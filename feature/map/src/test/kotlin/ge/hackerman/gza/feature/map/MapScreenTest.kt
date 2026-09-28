package ge.hackerman.gza.feature.map

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent { GzaTheme { Surface { MapScreen() } } }
    }

    @Test
    fun mapPreviewIsDescribed() {
        composeRule.onNodeWithTag(MapTestTags.CANVAS)
            .assertContentDescriptionEquals("Map preview with sample buses on route 326")
    }

    @Test
    fun sheetNamesTheStop() {
        composeRule.onNodeWithText("Departures from Ana Politkovskaia Street").assertExists()
    }

    @Test
    fun sheetHandleIsTallEnough() {
        composeRule.onNodeWithTag(MapTestTags.SHEET_HANDLE, useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun sheetHandleNamesTheSheetAndSaysItIsCollapsed() {
        composeRule.onNodeWithTag(MapTestTags.SHEET_HANDLE)
            .assertContentDescriptionEquals("Departures sheet")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
    }

    @Test
    fun sheetContentScrollsToTheLastItem() {
        composeRule.onNodeWithTag(MapTestTags.SHEET_CONTENT).assert(hasScrollAction())
        composeRule.onNodeWithContentDescription(HANDLE).performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag(MapTestTags.SHEET_CONTENT)
            .performScrollToNode(hasText("Sample data", substring = true))
        composeRule.onNode(hasText("Sample data", substring = true)).assertIsDisplayed()
    }

    @Test
    fun sheetHandleIsAnnouncedOnce() {
        composeRule.onAllNodes(hasContentDescription(HANDLE), useUnmergedTree = true).assertCountEquals(1)
        val bounds = composeRule.onNodeWithContentDescription(HANDLE).fetchSemanticsNode().boundsInRoot
        val focusable = composeRule.onAllNodes(hasClickAction() or hasLongClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot == bounds }
        assertEquals(1, focusable.size, "TalkBack would stop on the handle ${focusable.size} times")
    }

    private fun hasLongClickAction() = SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)

    @Test
    fun everyInteractiveElementIsNamedAndBigEnough() {
        // The sheet handle is the one control.
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 1)
    }

    private companion object {
        const val HANDLE = "Departures sheet"
    }
}
