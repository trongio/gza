package ge.hackerman.gza.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.R
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

@Composable
fun PlaceholderScreen(modifier: Modifier = Modifier) {
    Scaffold(
        // Lets Maestro and UiAutomator find test tags as resource ids.
        modifier = modifier.semantics { testTagsAsResourceId = true }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.placeholder_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .testTag(PlaceholderTestTags.TITLE)
                    .semantics { heading() }
            )
            Text(
                text = stringResource(R.string.placeholder_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(PlaceholderTestTags.SUBTITLE)
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PlaceholderScreenPreview() {
    GzaTheme {
        PlaceholderScreen()
    }
}
