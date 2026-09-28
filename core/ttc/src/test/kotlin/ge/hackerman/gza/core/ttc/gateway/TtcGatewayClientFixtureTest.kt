package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransitLeg
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.TripOptimize
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.TripTime
import ge.hackerman.gza.core.model.WalkLeg
import ge.hackerman.gza.core.ttc.http.TtcGateway
import ge.hackerman.gza.core.ttc.testing.FirebaseFixtures
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.FixtureMeta
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * One test per endpoint: a recorded response through MockWebServer, the real auth
 * interceptor, Retrofit, the converter and the mappers.
 */
class TtcGatewayClientFixtureTest {
    private val gateway = FixtureGateway()
    private val route326 = RouteId("1:R97493")
    private val route551 = RouteId("1:minibusR24579")
    private val route472 = RouteId("1:minibusR25521")
    private val route301 = RouteId("1:R29981")
    private val stop970 = StopId("1:970")
    private val both = listOf(PatternSuffix("0:01"), PatternSuffix("1:01"))

    @AfterEach
    fun tearDown() = gateway.close()

    private fun <T> fixture(path: String, block: suspend (FixtureMeta) -> T): T {
        val meta = gateway.serve(path)
        return runBlocking { block(meta) }
    }

    /** The client asked for exactly what the recorder asked for, under the configured base, with the key. */
    private fun assertRequested(meta: FixtureMeta, request: RecordedRequest = gateway.requests.last()) {
        val expected = "http://recorded/pis-gateway/api${meta.request}".toHttpUrl()
        assertEquals(expected.pathSegments, request.url.pathSegments)
        assertEquals(expected.queryParameterNames, request.url.queryParameterNames)
        expected.queryParameterNames.forEach {
            assertEquals(expected.queryParameter(it), request.url.queryParameter(it), it)
        }
        assertEquals(FirebaseFixtures.GATEWAY_KEY, request.headers[TtcGateway.API_KEY_HEADER])
    }

    @Test
    fun stops() {
        val stops =
            fixture("stops/all-en.json") { gateway.client().stops(Language.EN).also { _ -> assertRequested(it) } }
        assertEquals(2753, stops.size)
        val stop = stops.single { it.id == stop970 }
        assertEquals("970", stop.code)
        assertEquals(TransportKind.BUS, stop.kind)
        val metro = stops.filter { it.kind == TransportKind.METRO }
        assertTrue(metro.isNotEmpty() && metro.all { it.code == null })
        assertEquals(29, stops.count { it.code == null })
    }

    @Test
    fun `stop in english and georgian`() {
        val en =
            fixture("stop/1-970-en.json") {
                gateway.client().stop(stop970, Language.EN).also { _ -> assertRequested(it) }
            }
        val ka =
            fixture("stop/1-970-ka.json") {
                gateway.client().stop(stop970, Language.KA).also { _ -> assertRequested(it) }
            }
        assertEquals("Ana Politkovskaia Street", en.name)
        assertEquals("ანა პოლიტკოვსკაიას ქუჩა", ka.name)
        assertEquals(LatLon(41.722055, 44.703114), en.location)
        assertEquals("/pis-gateway/api/v2/stops/1:970", gateway.requests.first().url.encodedPath)
    }

    @Test
    fun `stop routes`() {
        val routes = fixture("stop-routes/1-970-en.json") {
            gateway.client().stopRoutes(stop970, Language.EN).also { _ -> assertRequested(it) }
        }
        assertEquals(listOf("301", "326", "551"), routes.map { it.shortName })
        val minibus = routes.single { it.shortName == "551" }
        assertEquals(TransportKind.MINIBUS, minibus.kind)
        assertEquals("თბილისი მოლი - ანა პოლიტკოვსკაიას ქ.", minibus.longName)
        assertEquals(0x00B38B, routes.first().color?.rgb)
    }

    @Test
    fun `arrival board at the terminus`() {
        val (board, meta) = fixture("terminus/551-20260928T2043/arrival-times.json") {
            gateway.client(clockAt = it.recordedAt).arrivalBoard(stop970, Language.EN) to it
        }
        assertRequested(meta)
        assertEquals(meta.recordedAt, board.fetchedAt)
        assertEquals(stop970, board.stopId)
        assertEquals(listOf("551", "301", "326"), board.arrivals.map { it.routeShortName })
    }

    @Test
    fun `arrival board mid route and in georgian`() {
        val board = fixture("arrival-times/1-3569-en-20260928T2044.json") {
            gateway.client().arrivalBoard(StopId("1:3569"), Language.EN).also { _ -> assertRequested(it) }
        }
        assertTrue(board.arrivals.size > 3)
        val ka = fixture("arrival-times/1-970-ka-20260928T2101.json") {
            gateway.client().arrivalBoard(stop970, Language.KA).also { _ -> assertRequested(it) }
        }
        assertEquals("თბილისი მოლი", ka.arrivals.first().headsign)
    }

    @Test
    fun `metro board is not realtime and a stop without service is empty`() {
        val metro = fixture("arrival-times/metro-1-1-en-20260928T2101.json") {
            gateway.client().arrivalBoard(StopId("1:metro_1_1"), Language.EN)
        }
        val row = metro.arrivals.single()
        assertEquals(TransportKind.METRO, row.kind)
        assertEquals(false, row.realtime)
        val empty = fixture("arrival-times/empty-gondola-5.json") {
            gateway.client().arrivalBoard(StopId("1:gondola_5"), Language.EN).also { _ -> assertRequested(it) }
        }
        assertTrue(empty.arrivals.isEmpty())
    }

    @Test
    fun routes() {
        val routes =
            fixture("routes/all-en.json") { gateway.client().routes(Language.EN).also { _ -> assertRequested(it) } }
        assertEquals(280, routes.size)
        val kinds = routes.groupingBy { it.kind }.eachCount()
        assertEquals(275, (kinds[TransportKind.BUS] ?: 0) + (kinds[TransportKind.MINIBUS] ?: 0))
        assertEquals(2, kinds[TransportKind.METRO])
        assertEquals(3, kinds[TransportKind.CABLE_CAR])
        assertTrue((kinds[TransportKind.MINIBUS] ?: 0) > 0)
    }

    @Test
    fun `route details`() {
        mapOf("301" to route301, "326" to route326, "551" to route551).forEach { (name, id) ->
            val detail =
                fixture("route/$name-en.json") {
                    gateway.client().route(id, Language.EN).also { _ -> assertRequested(it) }
                }
            assertEquals(name, detail.shortName)
            assertEquals(both.toSet(), detail.patterns.map { it.suffix }.toSet())
        }
        val r472 = fixture("route/472-en.json") { gateway.client().route(route472, Language.EN) }
        assertEquals(listOf("0:03", "1:03"), r472.patterns.map { it.suffix.value })
        assertEquals(PatternSuffix("1:03"), r472.defaultPattern)
        assertEquals("უნივერსიტეტის მაღლივი კორპუსი", r472.patterns.first().headsign)
        assertEquals(TransportKind.MINIBUS, r472.kind)
        val metro = fixture("route/metro-1-en.json") { gateway.client().route(RouteId("1:Metro_Metro_1"), Language.EN) }
        assertEquals(TransportKind.METRO, metro.kind)
    }

    @Test
    fun `schedule of 326`() {
        val schedule = fixture("schedule/326-0-01-en.json") {
            gateway.client().schedule(route326, PatternSuffix("0:01"), Language.EN).also { _ -> assertRequested(it) }
        }
        assertEquals("0:01", gateway.single().url.queryParameter("patternSuffix"))
        val weekdays = schedule.periods.first()
        assertEquals(46, weekdays.stops.size)
        val first = weekdays.stops.first()
        assertEquals(stop970, first.stopId)
        assertEquals(1, first.position)
        assertEquals(475, first.times.first().minutes)
        assertTrue(weekdays.stops.drop(1).any { stop -> stop.times.any { it.minutes == 1445 } }, "has 24:05")
    }

    @Test
    fun `schedule of 551 has three periods`() {
        val schedule = fixture("schedule/551-0-01-en.json") {
            gateway.client().schedule(route551, PatternSuffix("0:01"), Language.EN).also { _ -> assertRequested(it) }
        }
        assertEquals(3, schedule.periods.size)
        val saturday = schedule.periods[1]
        assertEquals(saturday.fromDay, saturday.toDay)
        assertEquals(5, schedule.periods.first().serviceDates.size)
    }

    @Test
    fun `pattern stops of 326 in travel order`() {
        val stops = fixture("stops-of-patterns/326-0-01-en.json") {
            gateway.client().patternStops(route326, PatternSuffix("0:01"), Language.EN).also { _ ->
                assertRequested(it)
            }
        }
        assertEquals(46, stops.stops.size)
        assertEquals(stop970, stops.stops.first().id)
        assertEquals(StopId("1:824"), stops.stops.last().id)
        val schedule = fixture("schedule/326-0-01-en.json") {
            gateway.client().schedule(route326, PatternSuffix("0:01"), Language.EN)
        }
        assertEquals(
            schedule.periods.first().stops.sortedBy {
                it.position
            }.map { it.stopId },
            stops.stops.map { it.id }
        )
        val r472 = fixture("stops-of-patterns/472-0-03-en.json") {
            gateway.client().patternStops(route472, PatternSuffix("0:03"), Language.EN).also { _ ->
                assertRequested(it)
            }
        }
        assertEquals(43, r472.stops.size)
    }

    @Test
    fun `polylines of both 326 patterns`() {
        // The recorder asks in the route detail's order, which is 1:01 first for 326.
        val polylines =
            fixture("polylines/326.json") {
                gateway.client().polylines(route326, both.reversed()).also { _ -> assertRequested(it) }
            }
        assertEquals(both.toSet(), polylines.map { it.pattern }.toSet())
        assertTrue(polylines.all { it.encoded.decode().size > 100 })
    }

    @Test
    fun positions() {
        listOf(
            "positions/326-20260928T2043.json" to route326,
            "terminus/551-20260928T2043/positions.json" to route551,
            "positions/301-20260928T2044.json" to route301
        ).forEach { (path, id) ->
            val positions = fixture(path) { gateway.client(clockAt = it.recordedAt).positions(id, both) to it }
            assertRequested(positions.second)
            assertEquals("0:01,1:01", gateway.requests.last().url.queryParameter("patternSuffixes"))
            assertEquals(positions.second.recordedAt, positions.first.fetchedAt)
            assertTrue(positions.first.vehicles.isNotEmpty())
        }
    }

    @Test
    fun `plan leaving now`() {
        val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.6934, 44.8015))
        val plan = fixture("plan/leave-now-970-to-freedom-square-20260928T2043.json") {
            gateway.client().plan(request, Language.EN)
        }
        val url = gateway.single().url
        assertEquals("leaveNow", url.queryParameter("departMode"))
        assertNull(url.queryParameter("date"))
        assertEquals("41.722055,44.703114", url.queryParameter("fromPlace"))
        assertEquals("WALK,SUBWAY,BUS,GONDOLA", url.queryParameter("modes"))
        assertEquals(3, plan.itineraries.size)
        val first = plan.itineraries.first()
        assertEquals(Instant.parse("2026-09-28T16:43:46Z"), first.start)
        assertEquals(Instant.parse("2026-09-28T17:29:20Z"), first.end)
        assertIs<TransitLeg>(first.legs[0])
        assertIs<WalkLeg>(first.legs[1])
        val bus = first.legs[0] as TransitLeg
        assertEquals("551", bus.route?.shortName)
        assertEquals(StopId("1:969"), bus.intermediateStops.first().id)
        assertEquals("Origin", plan.from.name)
    }

    @Test
    fun `plan arriving by nine tomorrow`() {
        val request = TripRequest(
            LatLon(41.722055, 44.703114),
            LatLon(41.694033, 44.801559),
            TripTime.ArriveBy(Instant.parse("2026-09-29T05:00:00Z")),
            optimize = TripOptimize.LESS_WALKING
        )
        val plan = fixture("plan/arrive-by-970-to-freedom-square-20260928T2101.json") {
            gateway.client().plan(request, Language.EN).also { _ -> assertRequested(it) }
        }
        val url = gateway.single().url
        assertEquals("arriveBy", url.queryParameter("departMode"))
        assertEquals("2026-09-29", url.queryParameter("date"))
        assertEquals("09:00", url.queryParameter("time"))
        assertEquals("lessWalking", url.queryParameter("optimize"))
        assertEquals(3, plan.itineraries.size)
    }

    @Test
    fun `plan with no itineraries`() {
        val here = LatLon(41.722055, 44.703114)
        val plan = fixture("plan/no-itineraries.json") { gateway.client().plan(TripRequest(here, here), Language.EN) }
        assertTrue(plan.itineraries.isEmpty())
    }

    @Test
    fun geocode() {
        val en = fixture("geocode/rustaveli-en.json") { gateway.client().geocode("rustaveli", Language.EN) }
        assertEquals("44.600000,41.600000,45.000000,41.850000", gateway.requests.last().url.queryParameter("bbox"))
        assertEquals("Rustaveli", en.first().name)
        val ka = fixture("geocode/rustaveli-ka.json") { gateway.client().geocode("რუსთაველი", Language.KA) }
        assertEquals("რუსთაველი", gateway.requests.last().url.queryParameter("query"))
        assertEquals("რუსთაველი", ka.first().name)
        val none = fixture("geocode/no-results.json") { gateway.client().geocode("zzqxqzzqxq", Language.EN) }
        assertTrue(none.isEmpty())
    }

    @Test
    fun `reverse geocode`() {
        val at = LatLon(41.722055, 44.703114)
        val results = fixture("reverse-geocode/1-970-en.json") {
            gateway.client().reverseGeocode(at, Language.EN).also { _ -> assertRequested(it) }
        }
        val first = results.first()
        assertEquals("Politkovskaia Street #16", first.name)
        assertEquals(LatLon(41.7219641, 44.7032188), first.location)
        assertEquals("bus_stop", first.osmValue)
        val nowhere =
            fixture("reverse-geocode/nowhere.json") { gateway.client().reverseGeocode(LatLon(0.0, 0.0), Language.EN) }
        assertTrue(nowhere.isEmpty())
    }
}
