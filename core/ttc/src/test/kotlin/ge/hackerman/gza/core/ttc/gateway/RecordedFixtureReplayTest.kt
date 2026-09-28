package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.BoundingBox
import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternStops
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.RouteDetail
import ge.hackerman.gza.core.model.RouteId
import ge.hackerman.gza.core.model.RoutePositions
import ge.hackerman.gza.core.model.RouteSchedule
import ge.hackerman.gza.core.model.Stop
import ge.hackerman.gza.core.model.StopBoard
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TBILISI_BOUNDS
import ge.hackerman.gza.core.model.TBILISI_ZONE
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.TripOptimize
import ge.hackerman.gza.core.model.TripPlan
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.TripTime
import ge.hackerman.gza.core.ttc.TtcJson
import ge.hackerman.gza.core.ttc.testing.FixtureGateway
import ge.hackerman.gza.core.ttc.testing.FixtureMeta
import ge.hackerman.gza.core.ttc.testing.Fixtures
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every recorded response, replayed through the public client from the request in its sidecar:
 * the client must ask for what the recorder asked for, map every real record (nothing real is
 * silently dropped), and turn every recorded error status into [TtcGatewayException.Http].
 * New recordings join automatically, so a re-recorded or newly odd response is tested too.
 */
class RecordedFixtureReplayTest {
    private val gateway = FixtureGateway()

    @AfterEach
    fun tearDown() = gateway.close()

    @ParameterizedTest
    @MethodSource("fixtures")
    fun `the client replays the recorded request and maps the recorded response`(path: String) {
        val meta = gateway.serve(path)
        val url = "http://recorded/pis-gateway/api${meta.request}".toHttpUrl()
        val client = gateway.client(clockAt = meta.recordedAt)
        if (meta.status != HTTP_OK) {
            val e = assertFailsWith<TtcGatewayException.Http> { runBlocking { call(client, url) } }
            assertEquals(meta.status, e.code, path)
            return
        }
        val result = runBlocking { call(client, url) }
        if (path !in NOT_REPRODUCIBLE) assertSameRequest(url, gateway.requests.first().url, path)
        assertNothingRealDropped(path, meta, TtcJson.parseToJsonElement(Fixtures.text(path)), result)
    }

    private suspend fun call(client: TtcGatewayClient, url: HttpUrl): Any {
        val segments = url.pathSegments.drop(API_PREFIX)
        val language = url.queryParameter("locale")?.let { code -> Language.entries.single { it.code == code } }
            ?: Language.EN
        val patterns = (url.queryParameter("patternSuffixes") ?: url.queryParameter("patternSuffix"))
            ?.split(',')?.map(::PatternSuffix)
            ?: listOf(PatternSuffix("0:01"))
        return when (segments.first()) {
            "v2" -> callV2(client, url, segments.drop(1), language)
            else -> callV3(client, segments.drop(1), language, patterns)
        }
    }

    private suspend fun callV2(client: TtcGatewayClient, url: HttpUrl, rest: List<String>, language: Language): Any =
        when {
            rest == listOf("stops") -> client.stops(language)

            rest.first() == "stops" && rest.size == 2 -> client.stop(StopId(rest[1]), language)

            rest.first() == "stops" && rest[2] == "routes" -> client.stopRoutes(StopId(rest[1]), language)

            rest.first() == "stops" && rest[2] == "arrival-times" -> client.arrivalBoard(StopId(rest[1]), language)

            rest == listOf("plan") -> client.plan(tripRequest(url), language)

            rest == listOf("geocode") -> client.geocode(
                url.queryParameter("query").orEmpty(),
                language,
                url.queryParameter("bbox")?.let(::bbox) ?: TBILISI_BOUNDS
            )

            rest == listOf("geocode", "reverse") -> client.reverseGeocode(
                LatLon(url.queryParameter("lat")!!.toDouble(), url.queryParameter("lon")!!.toDouble()),
                language
            )

            else -> error("no client call for ${url.encodedPath}")
        }

    private suspend fun callV3(
        client: TtcGatewayClient,
        rest: List<String>,
        language: Language,
        patterns: List<PatternSuffix>
    ): Any {
        if (rest.size == 1) return client.routes(language)
        val id = RouteId(rest[1])
        return when (rest.getOrNull(2)) {
            null -> client.route(id, language)
            "schedule" -> client.schedule(id, patterns.first(), language)
            "stops-of-patterns" -> client.patternStops(id, patterns.first(), language)
            "polylines" -> client.polylines(id, patterns)
            "positions" -> client.positions(id, patterns)
            else -> error("no client call for $rest")
        }
    }

    private fun tripRequest(url: HttpUrl): TripRequest {
        fun place(name: String) =
            url.queryParameter(name)!!.split(',').let { LatLon(it[0].toDouble(), it[1].toDouble()) }
        val at = url.queryParameter("date")?.let { date ->
            LocalDate.parse(date).atTime(LocalTime.parse(url.queryParameter("time"))).atZone(TBILISI_ZONE).toInstant()
        }
        val time = when (url.queryParameter("departMode")) {
            "arriveBy" -> TripTime.ArriveBy(at!!)
            "departAt" -> at?.let(TripTime::DepartAt) ?: TripTime.LeaveNow
            else -> TripTime.LeaveNow
        }
        val modes = url.queryParameter("modes")!!.split(',').toSet()
        val kinds = buildSet {
            if ("BUS" in modes) addAll(listOf(TransportKind.BUS, TransportKind.MINIBUS))
            if ("SUBWAY" in modes) add(TransportKind.METRO)
            if ("GONDOLA" in modes) add(TransportKind.CABLE_CAR)
        }
        val optimize = if (url.queryParameter("optimize") == "lessWalking") {
            TripOptimize.LESS_WALKING
        } else {
            TripOptimize.QUICK
        }
        return TripRequest(place("fromPlace"), place("toPlace"), time, kinds, optimize)
    }

    private fun bbox(raw: String): BoundingBox = raw.split(',').map(String::toDouble).let {
        BoundingBox(it[0], it[1], it[2], it[3])
    }

    /** Same path and parameters; numbers compared as numbers (`41.6934` and `41.693400` are one place). */
    private fun assertSameRequest(expected: HttpUrl, actual: HttpUrl, path: String) {
        assertEquals(expected.pathSegments, actual.pathSegments, path)
        assertEquals(expected.queryParameterNames, actual.queryParameterNames, path)
        expected.queryParameterNames.forEach { name ->
            val want = expected.queryParameter(name).orEmpty()
            val got = actual.queryParameter(name).orEmpty()
            val wantNumbers = want.split(',').map { it.toDoubleOrNull() }
            if (name in NUMERIC_PARAMS && null !in wantNumbers) {
                assertEquals(wantNumbers, got.split(',').map { it.toDoubleOrNull() }, "$path $name")
            } else {
                assertEquals(want, got, "$path $name")
            }
        }
    }

    private fun assertNothingRealDropped(path: String, meta: FixtureMeta, raw: JsonElement, result: Any) {
        when (result) {
            is RouteSchedule -> assertScheduleComplete(path, raw, result)
            is StopBoard -> assertEquals(meta.recordedAt, result.fetchedAt)
            is RoutePositions -> assertEquals(meta.recordedAt, result.fetchedAt)
            else -> Unit
        }
        val mapped = mappedCount(result) ?: return
        if (path !in NOT_REPRODUCIBLE) assertEquals(rawCount(raw, result), mapped, "$path: real records dropped")
    }

    /** Records the client returned, or null for single-object results. */
    private fun mappedCount(result: Any): Int? = when (result) {
        is List<*> -> result.size
        is StopBoard -> result.arrivals.size
        is RoutePositions -> result.vehicles.size
        is PatternStops -> result.stops.size
        is TripPlan -> result.itineraries.size
        else -> null.also { assertTrue(result is Stop || result is RouteDetail || result is RouteSchedule) }
    }

    /** Records in the raw response that the client should have returned. */
    private fun rawCount(raw: JsonElement, result: Any): Int = when {
        result is RoutePositions -> raw.jsonObject.values.sumOf { (it as? JsonArray)?.size ?: 0 }
        result is TripPlan -> raw.jsonObject["itineraries"]?.jsonArray?.size ?: 0
        raw is JsonArray -> raw.count { it != JsonNull }
        raw is JsonObject -> raw["features"]?.jsonArray?.size ?: raw.size
        else -> 0
    }

    private fun assertScheduleComplete(path: String, raw: JsonElement, result: RouteSchedule) {
        val periods = raw.jsonArray.map { it.jsonObject }
        assertEquals(periods.size, result.periods.size, "$path: periods dropped")
        periods.zip(result.periods).forEach { (period, mapped) ->
            val stops = period.getValue("stops").jsonArray
            assertEquals(stops.size, mapped.stops.size, "$path: stops dropped")
            val times = stops.sumOf { stop ->
                (stop.jsonObject["arrivalTimes"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    .split(',')
                    .count { it.isNotBlank() }
            }
            assertEquals(times, mapped.stops.sumOf { it.times.size }, "$path: times dropped")
            assertTrue(mapped.serviceDates.isNotEmpty(), "$path: service dates")
        }
    }

    companion object {
        private const val HTTP_OK = 200

        /** `pis-gateway`, `api`. */
        private const val API_PREFIX = 2

        private val NUMERIC_PARAMS = setOf("fromPlace", "toPlace", "bbox", "lat", "lon")

        /**
         * Requests the public client cannot send by design: several suffixes in one
         * stops-of-patterns call (no travel order), and the recorded error cases that omit a
         * required parameter.
         */
        private val NOT_REPRODUCIBLE = setOf(
            "stops-of-patterns/326-both-en.json",
            "errors/schedule-missing-pattern-400.json",
            "errors/geocode-missing-bbox-400.json",
            "errors/plan-departat-without-date-400.txt"
        )

        @JvmStatic
        fun fixtures(): List<String> = Fixtures.all()
    }
}
