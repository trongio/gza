package ge.hackerman.gza.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.feature.now.navigation.NowDestination
import ge.hackerman.gza.navigation.GzaNavHost
import ge.hackerman.gza.navigation.TopLevelDestination
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaAppStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun topLevelNavigationKeepsOneCopyOfEachTabAboveNow() {
        var current: TopLevelDestination? = null
        val nav = TestNavHostController(ApplicationProvider.getApplicationContext()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }
        val state = GzaAppState(nav)
        composeRule.setContent {
            current = state.currentTopLevelDestination
            GzaTheme { GzaNavHost(state) }
        }

        composeRule.runOnUiThread {
            state.navigateToTopLevelDestination(TopLevelDestination.SEARCH)
            state.navigateToTopLevelDestination(TopLevelDestination.SEARCH)
            state.navigateToTopLevelDestination(TopLevelDestination.MAP)
        }
        composeRule.waitForIdle()

        assertEquals(TopLevelDestination.MAP, current)
        val screens = nav.currentBackStack.value.map { it.destination }.filter { d ->
            TopLevelDestination.entries.any { d.hasRoute(it.route) }
        }
        assertTrue(screens.first().hasRoute(NowDestination::class), "Now stays at the bottom")
        assertEquals(2, screens.size, "Now plus Map only, never a stacked Search: $screens")
        assertTrue(screens.last().hasRoute(TopLevelDestination.MAP.route))
    }
}
