package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaBottomSheetScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: BottomSheetScaffoldState

    @Before
    fun setUp() {
        composeRule.setContent {
            state = rememberBottomSheetScaffoldState()
            GzaTheme {
                GzaBottomSheetScaffold(
                    scaffoldState = state,
                    sheetContent = {
                        GzaSheetHeader("Departures")
                        Text("Row one")
                        Text("Row two")
                    },
                    sheetLabel = HANDLE
                ) { Box(Modifier.fillMaxSize()) }
            }
        }
    }

    private fun handle() = composeRule.onNode(hasClickAction() and hasContentDescription(HANDLE))

    @Test
    fun dragHandleIsANamedButtonTallEnoughToHit() {
        handle().assertHeightIsAtLeast(48.dp)
        composeRule.assertInteractiveNodesAccessible()
    }

    @Test
    fun handleExpandsAndCollapsesWithoutDragging() {
        handle().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(SheetValue.Expanded, state.bottomSheetState.currentValue)

        handle().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(SheetValue.PartiallyExpanded, state.bottomSheetState.currentValue)
    }

    @Test
    fun handleIsOneAccessibilityNode() {
        val bounds = handle().fetchSemanticsNode().boundsInRoot
        // Material's own handle slot stacked a long-clickable tooltip box on a clickable box,
        // both covering the handle: TalkBack stopped there twice.
        val actionable = composeRule.onAllNodes(hasAnyAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.boundsInRoot == bounds }
        assertEquals(1, actionable.size)
        composeRule.onAllNodes(hasContentDescription(HANDLE), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun handleOffersExpandAndThenCollapse() {
        handle().performSemanticsAction(SemanticsActions.Expand)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(SheetValue.Expanded, state.bottomSheetState.currentValue)

        handle().performSemanticsAction(SemanticsActions.Collapse)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(SheetValue.PartiallyExpanded, state.bottomSheetState.currentValue)
    }

    @Test
    fun handleSaysWhetherTheSheetIsExpanded() {
        handle().assert(hasStateDescription("Collapsed"))

        handle().performSemanticsAction(SemanticsActions.Expand)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        handle().assert(hasStateDescription("Expanded"))

        handle().performSemanticsAction(SemanticsActions.Collapse)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        handle().assert(hasStateDescription("Collapsed"))
    }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun hasAnyAction() = SemanticsMatcher("has a click or long click action") {
        SemanticsActions.OnClick in it.config || SemanticsActions.OnLongClick in it.config
    }

    private companion object {
        const val HANDLE = "Departures sheet"
    }
}
