package ge.hackerman.gza.feature.now

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.DepartureRow
import ge.hackerman.gza.core.designsystem.component.GzaBanner
import ge.hackerman.gza.core.designsystem.component.GzaTopAppBar
import ge.hackerman.gza.core.designsystem.component.LeaveCountdown
import ge.hackerman.gza.core.designsystem.component.RouteBadge
import ge.hackerman.gza.core.designsystem.component.TransitMode
import ge.hackerman.gza.core.designsystem.component.withMonoSpan
import ge.hackerman.gza.core.designsystem.theme.GzaMonoFamily
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import java.time.Duration

/**
 * "When do I leave?" for the sample place: a hero countdown for the next bus worth taking,
 * then the departures. Every row opens the map. Sample data only until T07.
 */
@Composable
fun NowScreen(onDepartureClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag(NowTestTags.SCREEN)) {
        GzaTopAppBar(
            title = stringResource(R.string.now_sample_place),
            subtitle = stringResource(
                R.string.now_subtitle,
                stringResource(DesignR.string.sample_stop_ana_politkovskaia),
                pluralStringResource(R.plurals.now_walk_minutes, WALK_MINUTES, WALK_MINUTES)
            )
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                GzaBanner(
                    text = stringResource(DesignR.string.sample_data_banner),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item { HeroCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.now_departures_header),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() }
                )
            }
            itemsIndexed(SampleDepartures) { index, departure ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                DepartureRow(
                    routeNumber = departure.route,
                    mode = departure.mode,
                    destination = stringResource(departure.destination),
                    departsAt = departure.departsAt,
                    status = departure.status,
                    leaveBy = departure.leaveBy,
                    onClick = onDepartureClick,
                    modifier = Modifier.testTag(NowTestTags.departureRow(index))
                )
            }
        }
    }
}

@Composable
private fun HeroCard(modifier: Modifier = Modifier) {
    val mono = SpanStyle(fontFamily = GzaMonoFamily)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LeaveCountdown(Duration.ofMinutes(HERO_MINUTES))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RouteBadge("326", TransitMode.Bus)
                val route = stringResource(
                    R.string.now_hero_route,
                    stringResource(DesignR.string.sample_headsign_326),
                    "17:13"
                )
                Text(withMonoSpan(route, "17:13", mono), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = withMonoSpan(stringResource(R.string.now_miss_it, "551", "17:24"), "17:24", mono),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private const val WALK_MINUTES = 4
private const val HERO_MINUTES = 7L

@PreviewLightDark
@Composable
private fun NowScreenPreview() {
    GzaTheme {
        Surface { NowScreen(onDepartureClick = {}) }
    }
}
