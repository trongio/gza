package ge.hackerman.gza.core.designsystem.component

import java.time.Duration
import java.util.Locale

enum class CountdownSize { Hero, Compact }

/** How a leave-by countdown reads at a given remaining time. */
sealed interface CountdownDisplay {
    data object Now : CountdownDisplay

    /** The last minute, down to the second: `"0:42"`. */
    data class Seconds(val text: String) : CountdownDisplay

    data class Minutes(val minutes: Long) : CountdownDisplay

    /** An hour or more: `"1:05"`. No wrap at 24 h; the caller decides whether to show it. */
    data class Hours(val text: String) : CountdownDisplay
}

/**
 * Digits are always ASCII (Locale.ROOT): the mono face has no other digits.
 * Always rounds down: 7 min 59 s shows 7, never promising more time than there is.
 * Under a second left is already "now".
 */
fun countdownDisplay(remaining: Duration): CountdownDisplay {
    val seconds = remaining.seconds
    return when {
        seconds <= 0 -> CountdownDisplay.Now

        seconds < SECONDS_PER_MINUTE -> CountdownDisplay.Seconds(String.format(Locale.ROOT, "0:%02d", seconds))

        seconds < SECONDS_PER_HOUR -> CountdownDisplay.Minutes(seconds / SECONDS_PER_MINUTE)

        else -> CountdownDisplay.Hours(
            String.format(
                Locale.ROOT,
                "%d:%02d",
                seconds / SECONDS_PER_HOUR,
                seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
            )
        )
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
