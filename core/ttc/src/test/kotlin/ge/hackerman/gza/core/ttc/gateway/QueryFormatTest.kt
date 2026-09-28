package ge.hackerman.gza.core.ttc.gateway

import ge.hackerman.gza.core.model.Language
import ge.hackerman.gza.core.model.LatLon
import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.TBILISI_BOUNDS
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.model.TripOptimize
import ge.hackerman.gza.core.model.TripRequest
import ge.hackerman.gza.core.model.TripTime
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class QueryFormatTest {
    private val stop970 = LatLon(41.722055, 44.703114)
    private val freedomSquare = LatLon(41.694033, 44.801559)

    @Test
    fun `coordinates use a dot whatever the device locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ru"))
            assertEquals("41.722055,44.703114", QueryFormat.place(stop970))
            Locale.setDefault(Locale.forLanguageTag("ka"))
            assertEquals("44.600000,41.600000,45.000000,41.850000", QueryFormat.bbox(TBILISI_BOUNDS))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `plan modes`() {
        assertEquals("WALK,BUS", QueryFormat.planModes(setOf(TransportKind.BUS)))
        assertEquals("WALK,BUS", QueryFormat.planModes(setOf(TransportKind.MINIBUS)))
        assertEquals("WALK,SUBWAY,GONDOLA", QueryFormat.planModes(setOf(TransportKind.CABLE_CAR, TransportKind.METRO)))
        assertEquals("WALK", QueryFormat.planModes(emptySet()))
        assertEquals("WALK", QueryFormat.planModes(setOf(TransportKind.UNKNOWN)))
        assertEquals("WALK,SUBWAY,BUS,GONDOLA", QueryFormat.planModes(TripRequest(stop970, stop970).kinds))
    }

    @Test
    fun `arrive by is formatted in Tbilisi time`() {
        val time = TripTime.ArriveBy(Instant.parse("2026-09-29T05:00:00Z"))
        assertEquals("2026-09-29" to "09:00", QueryFormat.dateAndTime(time))
        assertEquals("arriveBy", QueryFormat.departMode(time))
    }

    @Test
    fun `a departure past midnight in Tbilisi is the next day`() {
        val time = TripTime.DepartAt(Instant.parse("2026-09-28T20:30:00Z"))
        assertEquals("2026-09-29" to "00:30", QueryFormat.dateAndTime(time))
        assertEquals("departAt", QueryFormat.departMode(time))
    }

    @Test
    fun `leave now has no date or time`() {
        assertNull(QueryFormat.dateAndTime(TripTime.LeaveNow))
        val query = QueryFormat.planQuery(TripRequest(stop970, freedomSquare), Language.EN)
        assertEquals(
            listOf("fromPlace", "toPlace", "departMode", "modes", "optimize", "locale"),
            query.keys.toList()
        )
        assertEquals("leaveNow", query["departMode"])
        assertEquals("41.694033,44.801559", query["toPlace"])
    }

    @Test
    fun `full plan query in the gateway's order`() {
        val request = TripRequest(
            stop970,
            freedomSquare,
            TripTime.ArriveBy(Instant.parse("2026-09-29T05:00:00Z")),
            setOf(TransportKind.BUS),
            TripOptimize.LESS_WALKING
        )
        assertEquals(
            mapOf(
                "fromPlace" to "41.722055,44.703114",
                "toPlace" to "41.694033,44.801559",
                "departMode" to "arriveBy",
                "date" to "2026-09-29",
                "time" to "09:00",
                "modes" to "WALK,BUS",
                "optimize" to "lessWalking",
                "locale" to "ka"
            ).toList(),
            QueryFormat.planQuery(request, Language.KA).toList()
        )
    }

    @Test
    fun `pattern lists are comma joined`() {
        assertEquals("0:01,1:01", QueryFormat.patterns(listOf(PatternSuffix("0:01"), PatternSuffix("1:01"))))
    }
}
