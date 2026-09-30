package ge.hackerman.gza.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import ge.hackerman.gza.feature.map.MapTestTags
import ge.hackerman.gza.feature.now.NowTestTags
import ge.hackerman.gza.feature.plan.PlanMode
import ge.hackerman.gza.feature.plan.PlanTestTags
import ge.hackerman.gza.feature.search.SearchTestTags
import ge.hackerman.gza.feature.settings.SettingsTestTags
import ge.hackerman.gza.navigation.TAB_TRANSITION_TOTAL_MILLIS
import ge.hackerman.gza.navigation.TopLevelDestination
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        composeRule.setContent { GzaTheme { GzaApp() } }
    }

    private fun tab(destination: TopLevelDestination) = composeRule.onNodeWithTag(destination.testTag)

    private fun assertOnly(selected: TopLevelDestination) {
        TopLevelDestination.entries.forEach {
            if (it == selected) tab(it).assertIsSelected() else tab(it).assertIsNotSelected()
        }
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun startsOnNow() {
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
        assertOnly(TopLevelDestination.NOW)
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
    }

    @Test
    fun bottomBarReachesEveryDestination() {
        listOf(
            TopLevelDestination.SEARCH to SearchTestTags.SCREEN,
            TopLevelDestination.MAP to MapTestTags.SCREEN,
            TopLevelDestination.PLAN to PlanTestTags.SCREEN,
            TopLevelDestination.SETTINGS to SettingsTestTags.SCREEN
        ).forEach { (destination, screen) ->
            tab(destination).performClick()
            composeRule.onNodeWithTag(screen).assertIsDisplayed()
            assertOnly(destination)
            composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
        }
    }

    @Test
    fun backFromAnyTabReturnsToNow() {
        tab(TopLevelDestination.SEARCH).performClick()
        tab(TopLevelDestination.SETTINGS).performClick()
        back()
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
        assertOnly(TopLevelDestination.NOW)
    }

    @Test
    fun tabStateSurvivesSwitchingTabs() {
        tab(TopLevelDestination.PLAN).performClick()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).performClick()
        tab(TopLevelDestination.MAP).performClick()
        tab(TopLevelDestination.PLAN).performClick()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).assertIsSelected()
    }

    @Test
    fun reselectingTheCurrentTabKeepsItsStateAndStacksNoCopy() {
        tab(TopLevelDestination.PLAN).performClick()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).performClick()
        tab(TopLevelDestination.PLAN).performClick()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).assertIsSelected()
        back()
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun departureRowOpensTheMap() {
        composeRule.onNodeWithTag(NowTestTags.departureRow(0)).performClick()
        composeRule.onNodeWithTag(MapTestTags.SCREEN).assertIsDisplayed()
        assertOnly(TopLevelDestination.MAP)
    }

    @Test
    fun tabSwitchSettlesQuickly() {
        // The user found the default 700 ms fade too slow; pin the switch to a short budget.
        assertTrue(TAB_TRANSITION_TOTAL_MILLIS <= TAB_SWITCH_BUDGET_MILLIS)
        composeRule.mainClock.autoAdvance = false
        tab(TopLevelDestination.SEARCH).performClick()
        composeRule.mainClock.advanceTimeBy(TAB_SWITCH_BUDGET_MILLIS.toLong())
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        assertOnly(TopLevelDestination.SEARCH)
    }

    @Test
    @Config(qualifiers = "+ka")
    fun bottomBarSpeaksGeorgian() {
        val labels = listOf("ახლა", "ძიება", "რუკა", "დაგეგმვა", "პარამეტრები")
        TopLevelDestination.entries.zip(labels).forEach { (destination, label) ->
            tab(destination).assertTextEquals(label)
        }
    }

    private companion object {
        const val TAB_SWITCH_BUDGET_MILLIS = 300
    }
}
