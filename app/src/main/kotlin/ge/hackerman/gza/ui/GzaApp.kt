package ge.hackerman.gza.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import ge.hackerman.gza.core.designsystem.component.GzaNavigationBar
import ge.hackerman.gza.core.designsystem.component.GzaNavigationBarItem
import ge.hackerman.gza.navigation.GzaNavHost
import ge.hackerman.gza.navigation.TopLevelDestination

/**
 * The app shell: the five destinations over a bottom bar. Screens handle their own top
 * insets (the top bar does; the map draws behind the status bar on purpose).
 */
@Composable
fun GzaApp(modifier: Modifier = Modifier, appState: GzaAppState = rememberGzaAppState()) {
    val current = appState.currentTopLevelDestination
    Scaffold(
        // Lets Maestro and UiAutomator find test tags as resource ids.
        modifier = modifier.semantics { testTagsAsResourceId = true },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            GzaNavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    GzaNavigationBarItem(
                        selected = destination == current,
                        onClick = { appState.navigateToTopLevelDestination(destination) },
                        icon = destination.icon,
                        selectedIcon = destination.selectedIcon,
                        label = stringResource(destination.label),
                        modifier = Modifier.testTag(destination.testTag)
                    )
                }
            }
        }
    ) { padding ->
        GzaNavHost(appState, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}
