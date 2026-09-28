package ge.hackerman.gza.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.component.RouteBadge
import ge.hackerman.gza.core.designsystem.component.RouteBadgeSize
import ge.hackerman.gza.core.designsystem.component.TransitMode
import ge.hackerman.gza.core.designsystem.component.withMonoSpan
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.core.designsystem.theme.GzaMonoFamily
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

private const val LEAVE_AT = "8:12"
private const val ARRIVE_AT = "8:58"
private const val FIRST_WALK = 4
private const val TRANSFER_WALK = 2
private const val LAST_WALK = 3

/** One sample itinerary: walk, bus 326, walk, metro line 1, walk. */
@Composable
internal fun ItineraryCard(modifier: Modifier = Modifier) {
    val mono = SpanStyle(fontFamily = GzaMonoFamily)
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = withMonoSpan(stringResource(R.string.plan_leave_home, LEAVE_AT), LEAVE_AT, mono),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = withMonoSpan(stringResource(R.string.plan_arrive, ARRIVE_AT), ARRIVE_AT, mono),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Legs()
            SafeTransfer()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legs() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Walk(FIRST_WALK)
        RouteBadge("326", TransitMode.Bus, size = RouteBadgeSize.Small)
        Walk(TRANSFER_WALK)
        RouteBadge("1", TransitMode.Metro, size = RouteBadgeSize.Small)
        Walk(LAST_WALK)
    }
}

@Composable
private fun Walk(minutes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(painterResource(GzaIcons.Walk), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            text = pluralStringResource(R.plurals.plan_walk_minutes, minutes, minutes),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SafeTransfer() {
    val colors = GzaTheme.colors
    Surface(shape = RoundedCornerShape(14.dp), color = colors.liveContainer, contentColor = colors.onLiveContainer) {
        Text(
            text = stringResource(R.string.plan_safe_transfer),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
