package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import java.time.Duration

/**
 * "Leave in 7 min", big. Takes the colour of its container (LocalContentColor). TalkBack
 * reads one full sentence; there is no live region, because a per-second tick in the last
 * minute would flood the screen reader.
 */
@Composable
fun LeaveCountdown(remaining: Duration, modifier: Modifier = Modifier, size: CountdownSize = CountdownSize.Hero) {
    val display = countdownDisplay(remaining)
    val description = countdownDescription(display, remaining)
    Column(modifier = modifier.clearAndSetSemantics { contentDescription = description }) {
        if (display == CountdownDisplay.Now) {
            val style = if (size == CountdownSize.Hero) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.titleLarge
            }
            Text(stringResource(R.string.countdown_now), style = style)
            return@Column
        }
        Text(
            text = stringResource(R.string.countdown_leave_in),
            style = if (size == CountdownSize.Hero) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            }
        )
        CountdownValue(display, size)
    }
}

@Composable
private fun CountdownValue(display: CountdownDisplay, size: CountdownSize) {
    val (number, unit) = when (display) {
        is CountdownDisplay.Seconds -> display.text to null
        is CountdownDisplay.Minutes -> display.minutes.toString() to stringResource(R.string.countdown_unit_minutes)
        is CountdownDisplay.Hours -> display.text to stringResource(R.string.countdown_unit_hours)
        CountdownDisplay.Now -> return
    }
    val numberStyle: TextStyle
    val unitStyle: TextStyle
    if (size == CountdownSize.Hero) {
        numberStyle = GzaTheme.typography.timeHero
        unitStyle = MaterialTheme.typography.titleMedium
    } else {
        numberStyle = GzaTheme.typography.timeLarge
        unitStyle = MaterialTheme.typography.labelLarge
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(number, style = numberStyle, modifier = Modifier.alignByBaseline())
        if (unit != null) Text(unit, style = unitStyle, modifier = Modifier.alignByBaseline())
    }
}

@Composable
@ReadOnlyComposable
private fun countdownDescription(display: CountdownDisplay, remaining: Duration): String = when (display) {
    CountdownDisplay.Now -> stringResource(R.string.countdown_now)

    is CountdownDisplay.Seconds -> remaining.seconds.toInt().let {
        pluralStringResource(R.plurals.countdown_a11y_seconds, it, it)
    }

    is CountdownDisplay.Minutes -> display.minutes.toInt().let {
        pluralStringResource(R.plurals.countdown_a11y_minutes, it, it)
    }

    is CountdownDisplay.Hours -> stringResource(
        R.string.countdown_a11y_hours,
        remaining.toHours(),
        remaining.toMinutes() % MINUTES_PER_HOUR
    )
}

@PreviewLightDark
@Composable
private fun LeaveCountdownPreview() {
    GzaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LeaveCountdown(Duration.ofMinutes(7))
            LeaveCountdown(Duration.ofSeconds(42), size = CountdownSize.Compact)
        }
    }
}

private const val MINUTES_PER_HOUR = 60L
