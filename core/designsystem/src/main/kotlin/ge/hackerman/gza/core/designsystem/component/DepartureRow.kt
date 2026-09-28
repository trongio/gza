package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** Not user-facing: lets tests find the time inside the row's cleared semantics. */
internal const val DEPARTURE_TIME_TAG = "departure_time"

/**
 * One departure: badge, then destination with the departure time on the end, the leave-by
 * time and the status chip under them. TalkBack reads the whole row as one sentence. With [onClick]
 * the row is a button that opens the map (PLAN 2.7: every row is a door into the map).
 */
@Composable
fun DepartureRow(
    routeNumber: String,
    mode: TransitMode,
    destination: String,
    departsAt: String,
    status: DepartureStatus,
    modifier: Modifier = Modifier,
    routeColor: Color = Color.Unspecified,
    leaveBy: String? = null,
    detail: String? = null,
    onClick: (() -> Unit)? = null
) {
    val sentence = departureSentence(routeNumber, mode, destination, departsAt, status, leaveBy, detail)
    val clickLabel = stringResource(R.string.departure_show_on_map)
    val interaction = if (onClick == null) {
        Modifier.clearAndSetSemantics { contentDescription = sentence }
    } else {
        Modifier
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            // Clearing drops the children's texts; the click action is set again here so it
            // survives whatever the modifier order does.
            .clearAndSetSemantics {
                contentDescription = sentence
                role = Role.Button
                onClick(label = clickLabel) {
                    onClick()
                    true
                }
            }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A fixed slot, so destinations line up whatever the badge width (metro "1" vs "326").
        Box(Modifier.widthIn(min = BadgeSlotWidth)) {
            RouteBadge(routeNumber, mode, color = routeColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = destination,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(departsAt, style = GzaTheme.typography.timeLarge, modifier = Modifier.testTag(DEPARTURE_TIME_TAG))
            }
            if (leaveBy != null) LeaveByText(leaveBy)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // The chip gets the full width under the destination, so long Georgian wraps once
            // instead of into a narrow column.
            StatusChip(status, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun LeaveByText(leaveBy: String) {
    val mono = GzaTheme.typography.timeSmall
    Text(
        text = withMonoSpan(
            stringResource(R.string.departure_leave_by, leaveBy),
            leaveBy,
            SpanStyle(fontFamily = mono.fontFamily, fontWeight = mono.fontWeight)
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private val BadgeSlotWidth = 60.dp

@Composable
private fun departureSentence(
    routeNumber: String,
    mode: TransitMode,
    destination: String,
    departsAt: String,
    status: DepartureStatus,
    leaveBy: String?,
    detail: String?
): String {
    val badge = routeBadgeDescription(mode, routeNumber)
    val statusText = status.description()
    val sentence = if (leaveBy != null) {
        stringResource(R.string.departure_a11y, badge, destination, departsAt, leaveBy, statusText)
    } else {
        stringResource(R.string.departure_a11y_no_leave_by, badge, destination, departsAt, statusText)
    }
    // The row's semantics are cleared, so anything visible that is not in this sentence is
    // never spoken: the detail line has to be carried over explicitly.
    return if (detail != null) stringResource(R.string.departure_a11y_with_detail, sentence, detail) else sentence
}

@PreviewLightDark
@Composable
private fun DepartureRowPreview() {
    GzaTheme {
        Surface {
            Column {
                DepartureRow(
                    "326",
                    TransitMode.Bus,
                    "Baratashvili St",
                    "17:13",
                    DepartureStatus.Waiting(
                        "17:13"
                    ),
                    leaveBy = "17:08",
                    onClick = {
                    }
                )
                DepartureRow(
                    "551",
                    TransitMode.Minibus,
                    "Tbilisi Mall",
                    "17:24",
                    DepartureStatus.Live(3),
                    leaveBy = "17:19"
                )
            }
        }
    }
}
