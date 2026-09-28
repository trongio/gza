package ge.hackerman.gza.feature.now

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

    private fun show() = composeRule.setContent {
        GzaTheme { Surface { NowScreen(onDepartureClick = { clicks++ }) } }
    }

    private fun description(tag: String): String = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        .config[SemanticsProperties.ContentDescription].joinToString()

    @Test
    fun saysItIsSampleData() {
        show()
        composeRule.onNodeWithText("Sample data. Live departures arrive in a later update.").assertIsDisplayed()
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
}
