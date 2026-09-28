package ge.hackerman.gza.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ge.hackerman.gza.core.designsystem.R

/**
 * What the app knows about a departure, as the UI shows it. Times arrive already formatted
 * (`"17:13"`): formatting with a Clock belongs to the prediction and feature layers.
 */
sealed interface DepartureStatus {
    /** Parked at the terminus: waiting, not arriving, whatever the board says. */
    data class Waiting(val leavesAt: String) : DepartureStatus

    /** Seen on GPS; [stopsAway] when known. */
    data class Live(val stopsAway: Int?) : DepartureStatus

    data class Late(val minutes: Int) : DepartureStatus

    data object TimetableOnly : DepartureStatus
}

/** The status as one sentence, the same words the chip shows. */
@Composable
@ReadOnlyComposable
fun DepartureStatus.description(): String = when (this) {
    is DepartureStatus.Waiting -> stringResource(R.string.status_waiting, leavesAt)

    is DepartureStatus.Live -> if (stopsAway == null) {
        stringResource(R.string.status_live)
    } else {
        pluralStringResource(R.plurals.status_live_stops_away, stopsAway, stopsAway)
    }

    is DepartureStatus.Late -> pluralStringResource(R.plurals.status_late_minutes, minutes, minutes)

    DepartureStatus.TimetableOnly -> stringResource(R.string.status_timetable_only)
}
