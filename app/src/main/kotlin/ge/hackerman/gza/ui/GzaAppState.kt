package ge.hackerman.gza.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import ge.hackerman.gza.feature.map.navigation.navigateToMap
import ge.hackerman.gza.feature.now.navigation.navigateToNow
import ge.hackerman.gza.feature.plan.navigation.navigateToPlan
import ge.hackerman.gza.feature.search.navigation.navigateToSearch
import ge.hackerman.gza.feature.settings.navigation.navigateToSettings
import ge.hackerman.gza.navigation.TopLevelDestination

@Composable
fun rememberGzaAppState(navController: NavHostController = rememberNavController()): GzaAppState =
    remember(navController) { GzaAppState(navController) }

/** App-level UI state: which tab is showing, and how to switch tabs. */
@Stable
class GzaAppState(val navController: NavHostController) {
    val currentDestination: NavDestination?
        @Composable get() {
            val entry by navController.currentBackStackEntryAsState()
            return entry?.destination
        }

    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            val destination = currentDestination
            return TopLevelDestination.entries.firstOrNull { destination?.hasRoute(it.route) == true }
        }

    /**
     * One copy of each tab: pop back to Now and save the tab being left, so switching back
     * restores its state (scroll, search text, plan mode). Back from any tab returns to Now.
     */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.NOW -> navController.navigateToNow(options)
            TopLevelDestination.SEARCH -> navController.navigateToSearch(options)
            TopLevelDestination.MAP -> navController.navigateToMap(options)
            TopLevelDestination.PLAN -> navController.navigateToPlan(options)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }
}
