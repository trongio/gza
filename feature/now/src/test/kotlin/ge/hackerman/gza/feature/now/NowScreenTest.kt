package ge.hackerman.gza.feature.now

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NowScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var clicks = 0

    private fun show(fontScale: Float = 1f) = composeRule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            GzaTheme { Surface { NowScreen(onDepartureClick = { clicks++ }) } }
        }
    }

    private fun description(tag: String): String = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString()

    @Test
    fun saysItIsSampleData() {
        show()
        composeRule.onNodeWithText("Sample data. Live departures arrive in a later update.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun subtitleWrapsInsteadOfClippingAtLargeFont() {
        show(fontScale = 1.5f)
        val subtitle = composeRule.onNodeWithText("Ana Politkovskaia Street · $WALK").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        subtitle.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.lineCount > 1, "expected the subtitle to wrap at 1.5x, got ${layout.lineCount} line")
        assertFalse(layout.isLineEllipsized(layout.lineCount - 1), "subtitle is ellipsized")
        assertFalse(layout.didOverflowHeight, "subtitle is cut off")
        val walk = layout.layoutInput.text.indexOf(WALK)
        assertEquals(
            layout.getLineForOffset(walk),
            layout.getLineForOffset(walk + WALK.length - 1),
            "\"$WALK\" is split across lines"
        )
        // The bar grew to fit: the node is as tall as its text, not clipped to 64dp.
        assertEquals(layout.size.height.toFloat(), subtitle.fetchSemanticsNode().size.height.toFloat())
    }

    @Test
    fun showsFourDepartures() {
        show()
        repeat(4) { composeRule.onNodeWithTag(NowTestTags.departureRow(it)).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun parkedBusAtTheTerminusIsWaitingNeverZeroMinutes() {
        show()
        val row = description(NowTestTags.departureRow(0))
        assertTrue("Bus 326" in row, row)
        assertTrue("Waiting at terminus, leaves 17:13" in row, row)
        assertFalse("0 min" in row, row)
    }

    @Test
    fun rowsOpenTheMap() {
        show()
        composeRule.onNodeWithTag(NowTestTags.departureRow(2)).performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun heroCountdownReadsAsASentence() {
        show()
        composeRule.onNodeWithContentDescription("Leave in 7 minutes").assertIsDisplayed()
    }

    @Test
    fun everyInteractiveElementIsNamedAndBigEnough() {
        show()
        composeRule.onNodeWithTag(NowTestTags.departureRow(3)).performScrollTo()
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 4)
    }

    @Test
    @Config(qualifiers = "+ka")
    fun georgianHero() {
        show()
        composeRule.onNodeWithContentDescription("გამოდით 7 წუთში").assertIsDisplayed()
    }

    private companion object {
        const val WALK = "4\u00A0min\u00A0walk"
    }
}
