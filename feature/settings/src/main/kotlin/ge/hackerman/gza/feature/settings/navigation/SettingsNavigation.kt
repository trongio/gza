// One route key plus its graph functions, named after the feature like every XNavigation.kt.
@file:Suppress("MatchingDeclarationName")

package ge.hackerman.gza.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ge.hackerman.gza.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsDestination, navOptions)

/** [versionName] comes from the app's BuildConfig, which a library cannot read. */
fun NavGraphBuilder.settingsScreen(versionName: String) {
    composable<SettingsDestination> { SettingsScreen(versionName = versionName) }
}
