package ge.hackerman.gza.core.designsystem.component

import java.time.Duration
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CountdownDisplayTest {
    private fun display(seconds: Long) = countdownDisplay(Duration.ofSeconds(seconds))

    @Test
    fun pastOrZeroIsNow() {
        assertEquals(CountdownDisplay.Now, countdownDisplay(Duration.ofMinutes(-5)))
        assertEquals(CountdownDisplay.Now, countdownDisplay(Duration.ZERO))
        assertEquals(CountdownDisplay.Now, countdownDisplay(Duration.ofMillis(500)))
    }

    @Test
    fun lastMinuteCountsSeconds() {
        assertEquals(CountdownDisplay.Seconds("0:01"), display(1))
        assertEquals(CountdownDisplay.Seconds("0:42"), display(42))
        assertEquals(CountdownDisplay.Seconds("0:59"), display(59))
    }

    @Test
    fun minutesRoundDownNeverPromisingMoreTime() {
        assertEquals(CountdownDisplay.Minutes(1), display(60))
        assertEquals(CountdownDisplay.Minutes(7), display(7 * 60 + 59))
        assertEquals(CountdownDisplay.Minutes(59), display(59 * 60 + 59))
    }

    @Test
    fun anHourOrMoreShowsHoursAndMinutes() {
        assertEquals(CountdownDisplay.Hours("1:00"), display(60 * 60))
        assertEquals(CountdownDisplay.Hours("2:05"), display(125 * 60))
    }

    @Test
    fun pastMidnightDoesNotWrapAt24Hours() {
        assertEquals(CountdownDisplay.Hours("25:00"), display(25 * 60 * 60))
    }
}
