package ge.hackerman.gza.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.GzaBanner
import ge.hackerman.gza.core.designsystem.component.GzaTopAppBar
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** Trip planning: from, to, and when. Shows one sample itinerary until T14. */
@Composable
fun PlanScreen(mode: PlanMode, onModeSelected: (PlanMode) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag(PlanTestTags.SCREEN)) {
        GzaTopAppBar(title = stringResource(R.string.plan_title))
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlaceField(R.string.plan_from, DesignR.string.sample_stop_ana_politkovskaia, PlanTestTags.FROM)
            PlaceField(R.string.plan_to, DesignR.string.sample_stop_freedom_square, PlanTestTags.TO)
            ModeSelector(mode, onModeSelected)
            GzaBanner(stringResource(DesignR.string.sample_data_banner))
            ItineraryCard()
        }
    }
}

/** Read-only until T14 adds place search, but still focusable, so the label names it. */
@Composable
private fun PlaceField(label: Int, value: Int, tag: String) {
    OutlinedTextField(
        value = stringResource(value),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
}

@Composable
private fun ModeSelector(mode: PlanMode, onModeSelected: (PlanMode) -> Unit) {
    val labels = mapOf(
        PlanMode.LeaveNow to R.string.plan_mode_leave_now,
        PlanMode.DepartAt to R.string.plan_mode_depart_at,
        PlanMode.ArriveBy to R.string.plan_mode_arrive_by
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        PlanMode.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = entry == mode,
                onClick = { onModeSelected(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, PlanMode.entries.size),
                // Material draws these 40dp tall; the whole button should be a 48dp target.
                modifier = Modifier.heightIn(min = 48.dp).testTag(PlanTestTags.mode(entry)),
                icon = {},
                label = {
                    Text(stringResource(labels.getValue(entry)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlanScreenPreview() {
    GzaTheme {
        Surface { PlanScreen(mode = PlanMode.ArriveBy, onModeSelected = {}) }
    }
}
