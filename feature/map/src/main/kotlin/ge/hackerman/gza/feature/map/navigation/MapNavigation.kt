// One route key plus its graph functions, named after the feature like every XNavigation.kt.
@file:Suppress("MatchingDeclarationName")

package ge.hackerman.gza.feature.map.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ge.hackerman.gza.feature.map.MapScreen
import kotlinx.serialization.Serializable

@Serializable
data object MapDestination

fun NavController.navigateToMap(navOptions: NavOptions? = null) = navigate(MapDestination, navOptions)

fun NavGraphBuilder.mapScreen() {
    composable<MapDestination> { MapScreen() }
}
