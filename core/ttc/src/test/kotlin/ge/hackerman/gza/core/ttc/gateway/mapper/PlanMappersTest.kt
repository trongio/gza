package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.TransitLeg
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.WalkLeg
import ge.hackerman.gza.core.ttc.gateway.dto.ItineraryDto
import ge.hackerman.gza.core.ttc.gateway.dto.LegDto
import ge.hackerman.gza.core.ttc.gateway.dto.LegRouteDto
import ge.hackerman.gza.core.ttc.gateway.dto.PlaceDto
import ge.hackerman.gza.core.ttc.gateway.dto.PlanResponseDto
import ge.hackerman.gza.core.ttc.gateway.dto.PolylineDto
import ge.hackerman.gza.core.ttc.gateway.dto.StepDto
import ge.hackerman.gza.core.ttc.gateway.dto.StopDto
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

class PlanMappersTest {
    private val start = JsonPrimitive("2026-09-28T16:43:46.000+00:00")
    private val end = JsonPrimitive("2026-09-28T17:05:46Z")
    private val here = PlaceDto(41.722055, 44.703114, "Ana Politkovskaia Street")
    private val there = PlaceDto(41.714769, 44.784136, "Heroes Square")
    private val bus = LegDto(
        mode = "BUS",
        from = here,
        to = there,
        startTime = start,
        endTime = end,
        realTime = true,
        arrivalDelay = 3676,
        distance = 7913.77,
        duration = 1320,
        route = LegRouteDto(null, "551", "long", "0033B4", null),
        intermediateStops = listOf(StopDto("1:969", "969", "Sokhumi", 41.721692, 44.704641, "BUS"), null),
        legPolyline = PolylineDto(null, "_p~iF~ps|U"),
        steps = null
    )
    private val walk = LegDto(
        mode = "WALK",
        from = there,
        to = there,
        startTime = end,
        endTime = end,
        route = null,
        steps = listOf(StepDto("DEPART", 95.64, "Queen Tamar Avenue", 41.7147605, 44.7841481), null)
    )
    private val itinerary = ItineraryDto(start, end, 1320, 0, 0.0, listOf(bus, walk))
    private val request = TripRequest(LatLon(41.722055, 44.703114), LatLon(41.694033, 44.801559))

    @Test
    fun `offset and Z times both parse`() {
        assertEquals(Instant.parse("2026-09-28T16:43:46Z"), start.toInstantOrNull())
        assertEquals(Instant.parse("2026-09-28T17:05:46Z"), end.toInstantOrNull())
    }

    @Test
    fun `epoch milliseconds parse too`() {
        assertEquals(Instant.ofEpochMilli(1_790_000_000_000), JsonPrimitive(1_790_000_000_000).toInstantOrNull())
    }

    @Test
    fun `unreadable times are null`() {
        assertNull(JsonPrimitive("yesterday").toInstantOrNull())
        assertNull(JsonNull.toInstantOrNull())
        assertNull(null.toInstantOrNull())
        assertNull(JsonPrimitive(true).toInstantOrNull())
    }

    @Test
    fun `an unparseable leg time drops only its itinerary`() {
        val broken = itinerary.copy(legs = listOf(bus.copy(endTime = JsonPrimitive("soon")), walk))
        val plan = PlanResponseDto(here, there, listOf(broken, itinerary)).toTripPlan(request)
        assertEquals(1, plan.itineraries.size)
    }

    @Test
    fun `an itinerary without legs is dropped`() {
        assertNull(itinerary.copy(legs = emptyList()).toItineraryOrNull())
        assertNull(itinerary.copy(legs = null).toItineraryOrNull())
        assertNull(itinerary.copy(legs = listOf(null)).toItineraryOrNull())
    }

    @Test
    fun `an itinerary that ends before it starts is dropped`() {
        assertNull(itinerary.copy(startTime = end, endTime = start).toItineraryOrNull())
        val plan = PlanResponseDto(here, there, listOf(itinerary.copy(startTime = end, endTime = start), itinerary))
            .toTripPlan(request)
        assertEquals(listOf(Instant.parse("2026-09-28T16:43:46Z")), plan.itineraries.map { it.start })
    }

    @Test
    fun `an itinerary that starts and ends at once is kept`() {
        val instant = itinerary.copy(endTime = start, legs = listOf(walk.copy(startTime = start, endTime = start)))
        assertEquals(Duration.ZERO, requireNotNull(instant.copy(duration = null).toItineraryOrNull()).duration)
    }

    @Test
    fun `a leg that ends before it starts is invalid and drops its itinerary`() {
        val backwards = bus.copy(startTime = end, endTime = start)
        assertNull(backwards.toLegOrNull())
        assertNull(itinerary.copy(legs = listOf(backwards, walk)).toItineraryOrNull())
    }

    @Test
    fun `a walk leg has no route and keeps its steps`() {
        val leg = assertIs<WalkLeg>(walk.toLegOrNull())
        assertEquals("Queen Tamar Avenue", leg.steps.single().streetName)
    }

    @Test
    fun `a bus leg with a null route id is a bus`() {
        val leg = assertIs<TransitLeg>(bus.toLegOrNull())
        assertEquals(TransportKind.BUS, leg.kind)
        assertNull(leg.route?.id)
        assertEquals("551", leg.route?.shortName)
        assertEquals(listOf("1:969"), leg.intermediateStops.map { it.id.value })
    }

    @Test
    fun `a minibus route id in a leg makes a minibus and subway is metro`() {
        val minibus = bus.copy(route = LegRouteDto(id = "1:minibusR24579")).toLegOrNull()
        assertEquals(TransportKind.MINIBUS, assertIs<TransitLeg>(minibus).kind)
        assertEquals(TransportKind.METRO, assertIs<TransitLeg>(bus.copy(mode = "SUBWAY").toLegOrNull()).kind)
    }

    @Test
    fun `arrival delay is copied raw whatever its value`() {
        assertEquals(Duration.ofSeconds(3676), assertIs<TransitLeg>(bus.toLegOrNull()).arrivalDelayHint)
        assertEquals(
            Duration.ofSeconds(-3631),
            assertIs<TransitLeg>(bus.copy(arrivalDelay = -3631).toLegOrNull()).arrivalDelayHint
        )
        assertNull(assertIs<TransitLeg>(bus.copy(arrivalDelay = null).toLegOrNull()).arrivalDelayHint)
    }

    @Test
    fun `missing itinerary numbers are computed or zero`() {
        val mapped = requireNotNull(
            itinerary.copy(duration = null, walkTime = null, walkDistance = null).toItineraryOrNull()
        )
        assertEquals(Duration.ofMinutes(22), mapped.duration)
        assertEquals(Duration.ZERO, mapped.walkTime)
        assertEquals(0.0, mapped.walkDistanceMeters)
    }

    @Test
    fun `a leg without a valid place is invalid`() {
        assertNull(bus.copy(from = PlaceDto(null, 44.7, "x")).toLegOrNull())
        assertNull(bus.copy(to = null).toLegOrNull())
    }

    @Test
    fun `plan endpoints fall back to the request`() {
        val plan = PlanResponseDto(null, null, null).toTripPlan(request)
        assertEquals(request.from, plan.from.location)
        assertEquals(request.to, plan.to.location)
        assertEquals(emptyList(), plan.itineraries)
    }
}
