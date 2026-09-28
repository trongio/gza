package ge.hackerman.gza.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.core.designsystem.component.GzaBanner
import ge.hackerman.gza.core.designsystem.component.GzaTopAppBar
import ge.hackerman.gza.core.designsystem.component.RouteBadge
import ge.hackerman.gza.core.designsystem.component.RouteBadgeSize
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** Find a stop by name or code. Typing works; nothing is searched until T08. */
@Composable
fun SearchScreen(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag(SearchTestTags.SCREEN)) {
        GzaTopAppBar(title = stringResource(R.string.search_title))
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.search_field_label)) },
                    leadingIcon = { Icon(painterResource(GzaIcons.Search), contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag(SearchTestTags.FIELD)
                )
            }
            item {
                GzaBanner(
                    text = stringResource(DesignR.string.sample_data_banner),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Text(
                    text = stringResource(R.string.search_recent_header),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() }
                )
            }
            items(SampleStops) { stop ->
                if (stop != SampleStops.first()) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                StopRow(stop)
            }
        }
    }
}

@Composable
private fun StopRow(stop: SampleStop) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(stop.name), style = MaterialTheme.typography.titleMedium)
            if (stop.code != null) {
                Text(
                    text = stringResource(R.string.search_stop_code, stop.code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stop.routes.forEach { (number, mode) -> RouteBadge(number, mode, size = RouteBadgeSize.Small) }
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenPreview() {
    GzaTheme {
        Surface { SearchScreen(query = "", onQueryChange = {}) }
    }
}
