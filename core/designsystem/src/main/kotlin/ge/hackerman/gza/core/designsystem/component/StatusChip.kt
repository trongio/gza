package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.core.designsystem.theme.GzaMonoFamily
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

private val ChipShape = RoundedCornerShape(14.dp)

/**
 * A departure's status as a small non-interactive pill. Waiting is the only solid chip
 * (amber, with its own glyph), so a parked bus at the terminus is unmistakable at a glance.
 * Long Georgian text wraps to two lines rather than being cut off.
 */
@Composable
fun StatusChip(status: DepartureStatus, modifier: Modifier = Modifier) {
    val colors = GzaTheme.colors
    val style = when (status) {
        is DepartureStatus.Waiting -> ChipStyle(
            colors.waitingContainer,
            colors.onWaitingContainer,
            colors.waitingOutline
        )

        is DepartureStatus.Live -> ChipStyle(colors.liveContainer, colors.onLiveContainer)

        is DepartureStatus.Late -> ChipStyle(colors.lateContainer, colors.onLateContainer)

        DepartureStatus.TimetableOnly -> ChipStyle(Color.Transparent, colors.onTimetable, colors.timetableOutline)
    }
    val description = status.description()
    val text = if (status is DepartureStatus.Waiting) {
        withMonoSpan(description, status.leavesAt, SpanStyle(fontFamily = GzaMonoFamily, fontWeight = FontWeight.Bold))
    } else {
        AnnotatedString(description)
    }
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 28.dp)
            .background(style.container, ChipShape)
            .border(1.dp, style.outline, ChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusLeading(status, style.content)
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = style.content)
    }
}

private data class ChipStyle(val container: Color, val content: Color, val outline: Color = Color.Transparent)

@Composable
private fun StatusLeading(status: DepartureStatus, tint: Color) {
    when (status) {
        // A dark disk with the pause bars cut out in amber: reads as "paused", not "coming".
        is DepartureStatus.Waiting -> StatusIcon(GzaIcons.Waiting, tint, size = 20)

        is DepartureStatus.Live -> Box(
            Modifier.size(8.dp).background(GzaTheme.colors.liveIndicator, CircleShape)
        )

        is DepartureStatus.Late -> StatusIcon(GzaIcons.Late, tint)

        DepartureStatus.TimetableOnly -> StatusIcon(GzaIcons.Timetable, tint)
    }
}

@Composable
private fun StatusIcon(icon: Int, tint: Color, size: Int = 16) {
    Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size.dp))
}

@Composable
private fun ChipSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(DepartureStatus.Waiting("17:13"))
        StatusChip(DepartureStatus.Live(3))
        StatusChip(DepartureStatus.Late(6))
        StatusChip(DepartureStatus.TimetableOnly)
    }
}

@PreviewLightDark
@Composable
private fun StatusChipPreview() {
    GzaTheme { ChipSamples() }
}

@Preview(locale = "ka")
@Composable
private fun StatusChipGeorgianPreview() {
    GzaTheme { ChipSamples() }
}
