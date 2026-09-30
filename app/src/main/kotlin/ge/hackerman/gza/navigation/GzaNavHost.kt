package ge.hackerman.gza.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

// NavHost defaults to a 700 ms crossfade, which made every tab switch feel sluggish. A
// shortened Material fade-through instead: the old tab fades out first, then the new one
// fades in, so the two never blend and the whole switch settles in under a quarter second.
internal const val TAB_FADE_OUT_MILLIS = 90
internal const val TAB_FADE_IN_MILLIS = 150
internal const val TAB_TRANSITION_TOTAL_MILLIS = TAB_FADE_OUT_MILLIS + TAB_FADE_IN_MILLIS

private val tabEnter: EnterTransition =
    fadeIn(animationSpec = tween(durationMillis = TAB_FADE_IN_MILLIS, delayMillis = TAB_FADE_OUT_MILLIS))
private val tabExit: ExitTransition = fadeOut(animationSpec = tween(durationMillis = TAB_FADE_OUT_MILLIS))

@Composable
fun GzaNavHost(appState: GzaAppState, modifier: Modifier = Modifier) {
    NavHost(
        navController = appState.navController,
        startDestination = NowDestination,
        modifier = modifier,
        enterTransition = { tabEnter },
        exitTransition = { tabExit },
        popEnterTransition = { tabEnter },
        popExitTransition = { tabExit }
    ) {
        // PLAN 2.7: every departure is a door into the map. T12 passes the chosen bus along.
        nowScreen(onDepartureClick = { appState.navigateToTopLevelDestination(TopLevelDestination.MAP) })
        searchScreen()
        mapScreen()
        planScreen()
        settingsScreen(versionName = BuildConfig.VERSION_NAME)
    }
}
