package ge.hackerman.gza.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.component.GzaTopAppBar
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** Preferences, shown but not yet editable (T16), and the unofficial notice. */
@Composable
fun SettingsScreen(versionName: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag(SettingsTestTags.SCREEN)) {
        GzaTopAppBar(title = stringResource(R.string.settings_title))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Setting(stringResource(R.string.settings_language), stringResource(R.string.settings_language_system))
            Setting(stringResource(R.string.settings_theme), stringResource(R.string.settings_theme_system))
            Setting(
                stringResource(R.string.settings_buffer),
                pluralStringResource(R.plurals.settings_buffer_minutes, BUFFER_MINUTES, BUFFER_MINUTES)
            )
            AboutCard(versionName, Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun Setting(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun AboutCard(versionName: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_about_unofficial), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val BUFFER_MINUTES = 1

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    GzaTheme {
        Surface { SettingsScreen(versionName = "0.1.0") }
    }
}
