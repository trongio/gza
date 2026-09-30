package ge.hackerman.gza.core.ttc.gateway.mapper

import ge.hackerman.gza.core.model.PatternSuffix
import ge.hackerman.gza.core.model.StopId
import ge.hackerman.gza.core.model.TransportKind
import ge.hackerman.gza.core.ttc.gateway.dto.BoardArrivalDto
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class BoardMappersTest {
    private val row = BoardArrivalDto("551", "0033B4", "Tbilisi Mall", "0:01", "BUS", true, 0, -90)

    @Test
    fun `terminus zero and negative scheduled minutes are copied raw`() {
        val arrival = requireNotNull(row.toBoardArrivalOrNull())
        assertEquals(0, arrival.realtimeMinutesHint)
        assertEquals(-90, arrival.scheduledMinutesHint)
        assertEquals(PatternSuffix("0:01"), arrival.pattern)
        assertEquals(TransportKind.BUS, arrival.kind, "the board cannot tell a minibus")
    }

    @Test
    fun `null minutes stay null and null realtime is false`() {
        val arrival = requireNotNull(
            row.copy(realtime = null, realtimeArrivalMinutes = null, scheduledArrivalMinutes = null)
                .toBoardArrivalOrNull()
        )
        assertFalse(arrival.realtime)
        assertNull(arrival.realtimeMinutesHint)
        assertNull(arrival.scheduledMinutesHint)
    }

    @Test
    fun `a row without a short name is dropped`() {
        assertNull(row.copy(shortName = " ").toBoardArrivalOrNull())
        assertNull(row.copy(shortName = null).toBoardArrivalOrNull())
    }

    @Test
    fun `a bad pattern or colour is null but keeps the row`() {
        val arrival = row.copy(patternSuffix = "x", color = "blue").toBoardArrivalOrNull()
        assertNull(arrival?.pattern)
        assertNull(arrival?.color)
    }

    @Test
    fun `the board is stamped with the fetch time`() {
        val at = Instant.parse("2026-09-28T16:43:32Z")
        val board = listOf(row, null, row.copy(shortName = null)).toStopBoard(StopId("1:970"), at)
        assertEquals(at, board.fetchedAt)
        assertEquals(1, board.arrivals.size)
    }
}
