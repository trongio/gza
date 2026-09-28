package ge.hackerman.gza.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import ge.hackerman.gza.BuildConfig
import ge.hackerman.gza.feature.map.navigation.mapScreen
import ge.hackerman.gza.feature.now.navigation.NowDestination
import ge.hackerman.gza.feature.now.navigation.nowScreen
import ge.hackerman.gza.feature.plan.navigation.planScreen
import ge.hackerman.gza.feature.search.navigation.searchScreen
import ge.hackerman.gza.feature.settings.navigation.settingsScreen
import ge.hackerman.gza.ui.GzaAppState

@Composable
fun GzaNavHost(appState: GzaAppState, modifier: Modifier = Modifier) {
    NavHost(navController = appState.navController, startDestination = NowDestination, modifier = modifier) {
        // PLAN 2.7: every departure is a door into the map. T12 passes the chosen bus along.
        nowScreen(onDepartureClick = { appState.navigateToTopLevelDestination(TopLevelDestination.MAP) })
        searchScreen()
        mapScreen()
        planScreen()
        settingsScreen(versionName = BuildConfig.VERSION_NAME)
    }
}
