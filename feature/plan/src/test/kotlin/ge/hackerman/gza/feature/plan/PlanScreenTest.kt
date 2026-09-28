package ge.hackerman.gza.feature.plan

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlanScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<PlanMode>()

    private fun show(mode: PlanMode = PlanMode.LeaveNow, fontScale: Float = 1f) = composeRule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            GzaTheme { Surface { PlanScreen(mode = mode, onModeSelected = { selected += it }) } }
        }
    }

    @Test
    fun onlyTheCurrentModeIsSelected() {
        show(PlanMode.DepartAt)
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.LeaveNow)).assertIsNotSelected()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.DepartAt)).assertIsSelected()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun modeLabelsAreWholeAtLargeFont() {
        show(fontScale = 1.5f)
        listOf("Leave now", "Depart at", "Arrive by").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNode(hasText(label), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "'$label' is cut off")
        }
        composeRule.assertInteractiveNodesAccessible()
    }

    @Test
    fun choosingAModeReportsIt() {
        show()
        composeRule.onNodeWithTag(PlanTestTags.mode(PlanMode.ArriveBy)).performClick()
        assertEquals(listOf(PlanMode.ArriveBy), selected)
    }

    @Test
    fun fromAndToAreNamedByTheirLabels() {
        show()
        composeRule.onNodeWithTag(PlanTestTags.FROM).assert(hasText("From"))
        composeRule.onNodeWithTag(PlanTestTags.TO).assert(hasText("To"))
    }

    @Test
    fun everyInteractiveElementIsNamedAndBigEnough() {
        show()
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
    }
}
