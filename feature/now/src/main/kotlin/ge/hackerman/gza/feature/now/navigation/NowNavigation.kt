// One route key plus its graph functions, named after the feature like every XNavigation.kt.
@file:Suppress("MatchingDeclarationName")

package ge.hackerman.gza.feature.now.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ge.hackerman.gza.feature.now.NowScreen
import kotlinx.serialization.Serializable

/** The route key. Named Destination because `NowRoute` is reserved for the stateful screen (T07). */
@Serializable
data object NowDestination

fun NavController.navigateToNow(navOptions: NavOptions? = null) = navigate(NowDestination, navOptions)

fun NavGraphBuilder.nowScreen(onDepartureClick: () -> Unit) {
    composable<NowDestination> { NowScreen(onDepartureClick = onDepartureClick) }
}
