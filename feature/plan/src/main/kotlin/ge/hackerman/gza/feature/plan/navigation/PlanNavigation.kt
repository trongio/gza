// One route key plus its graph functions, named after the feature like every XNavigation.kt.
@file:Suppress("MatchingDeclarationName")

package ge.hackerman.gza.feature.plan.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ge.hackerman.gza.feature.plan.PlanMode
import ge.hackerman.gza.feature.plan.PlanScreen
import kotlinx.serialization.Serializable

@Serializable
data object PlanDestination

fun NavController.navigateToPlan(navOptions: NavOptions? = null) = navigate(PlanDestination, navOptions)

fun NavGraphBuilder.planScreen() {
    composable<PlanDestination> {
        // Saved with the tab's back stack entry, so the mode survives switching tabs.
        var mode by rememberSaveable { mutableStateOf(PlanMode.LeaveNow) }
        PlanScreen(mode = mode, onModeSelected = { mode = it })
    }
}
