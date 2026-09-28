package ge.hackerman.gza.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import ge.hackerman.gza.feature.map.MapTestTags
import ge.hackerman.gza.feature.now.NowTestTags
import ge.hackerman.gza.feature.plan.PlanMode
import ge.hackerman.gza.feature.plan.PlanTestTags
import ge.hackerman.gza.feature.search.SearchTestTags
import ge.hackerman.gza.feature.settings.SettingsTestTags
import ge.hackerman.gza.navigation.TopLevelDestination
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Shell behaviour beyond tab switching: saved state, leaving the app, large fonts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaAppStateRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun tab(destination: TopLevelDestination) = composeRule.onNodeWithTag(destination.testTag)

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun searchTextSurvivesSwitchingTabs() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        tab(TopLevelDestination.SEARCH).performClick()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("Rustaveli")
        tab(TopLevelDestination.NOW).performClick()
        tab(TopLevelDestination.SEARCH).performClick()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).assertTextContains("Rustaveli")
    }

    @Test
    fun selectedTabAndItsStateSurviveRecreation() {
        // Rotation and process death go through saved instance state.
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { GzaTheme { GzaApp() } }
        tab(TopLevelDestination.PLAN).performClick()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(PlanTestTags.SCREEN).assertIsDisplayed()
        tab(TopLevelDestination.PLAN).assertIsSelected()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).assertIsSelected()
    }

    @Test
    fun backOnNowLeavesTheApp() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        tab(TopLevelDestination.MAP).performClick()
        // Let the NavHost recompose first: until it does, its back callback is still disabled.
        composeRule.onNodeWithTag(MapTestTags.SCREEN).assertIsDisplayed()
        back()
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing, "first back only returns to Now")
        back()
        assertTrue(composeRule.activity.isFinishing, "back on Now leaves the app")
    }

    @Test
    fun departureRowToMapThenBackReturnsToNow() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        composeRule.onNodeWithTag(NowTestTags.departureRow(0)).performClick()
        composeRule.onNodeWithTag(MapTestTags.SCREEN).assertIsDisplayed()
        back()
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    @Config(fontScale = 2.0f)
    fun everyTabStaysAccessibleAtDoubleFontScale() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        listOf(
            TopLevelDestination.NOW to NowTestTags.SCREEN,
            TopLevelDestination.SEARCH to SearchTestTags.SCREEN,
            TopLevelDestination.MAP to MapTestTags.SCREEN,
            TopLevelDestination.PLAN to PlanTestTags.SCREEN,
            TopLevelDestination.SETTINGS to SettingsTestTags.SCREEN
        ).forEach { (destination, screen) ->
            tab(destination).performClick()
            composeRule.onNodeWithTag(screen).assertIsDisplayed()
            tab(destination).assertIsSelected()
            composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
        }
    }

    @Test
    @Config(qualifiers = "+ka")
    fun everyTabIsAccessibleInGeorgian() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        TopLevelDestination.entries.forEach { destination ->
            tab(destination).performClick()
            composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
        }
    }
}
