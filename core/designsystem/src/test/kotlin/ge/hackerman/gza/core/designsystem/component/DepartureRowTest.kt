package ge.hackerman.gza.core.designsystem.component

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DepartureRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val waitingSentence =
        "Bus 326 to Baratashvili St. Departs 17:13. Leave by 17:08. Waiting at terminus, leaves 17:13"

    private fun row(
        destination: String = "Baratashvili St",
        leaveBy: String? = "17:08",
        detail: String? = null,
        fontScale: Float = 1f,
        onClick: (() -> Unit)? = null
    ) = composeRule.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            GzaTheme {
                Surface {
                    DepartureRow(
                        routeNumber = "326",
                        mode = TransitMode.Bus,
                        destination = destination,
                        departsAt = "17:13",
                        status = DepartureStatus.Waiting("17:13"),
                        modifier = Modifier.testTag(ROW),
                        leaveBy = leaveBy,
                        detail = detail,
                        onClick = onClick
                    )
                }
            }
        }
    }

    @Test
    fun clickableRowIsOneButtonSentence() {
        var clicks = 0
        row(onClick = { clicks++ })
        composeRule.onNodeWithTag(ROW)
            .assertHasClickAction()
            .assertContentDescriptionEquals(waitingSentence)
            .assertHeightIsAtLeast(72.dp)
            .performClick()
        assertEquals(1, clicks)
        composeRule.assertInteractiveNodesAccessible()
    }

    @Test
    fun rowWithoutClickIsNotAButtonButStillOneSentence() {
        row()
        composeRule.onNodeWithTag(ROW)
            .assertHasNoClickAction()
            .assertContentDescriptionEquals(waitingSentence)
    }

    @Test
    fun rowWithoutLeaveByUsesTheShortSentence() {
        row(leaveBy = null)
        composeRule.onNodeWithTag(ROW).assertContentDescriptionEquals(
            "Bus 326 to Baratashvili St. Departs 17:13. Waiting at terminus, leaves 17:13"
        )
    }

    @Test
    fun detailLineIsPartOfTheSpokenSentence() {
        row(detail = "4 min walk", onClick = {})
        composeRule.onNodeWithTag(ROW).assertContentDescriptionEquals("$waitingSentence. 4 min walk")
        composeRule.onNodeWithTag(ROW).assert(hasContentDescription("4 min walk", substring = true))
    }

    @Test
    fun longDestinationDoesNotPushTheTimeOffScreen() {
        row(destination = "A".repeat(10) + " very long destination name that goes on and on and on, 80ch")
        composeRule.onNodeWithTag(DEPARTURE_TIME_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun doubleFontSizeStillShowsTheTimeAndTheChip() {
        row(fontScale = 2f)
        composeRule.onNodeWithTag(DEPARTURE_TIME_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Waiting at terminus, leaves 17:13", useUnmergedTree = true).assertIsDisplayed()
    }

    private companion object {
        const val ROW = "row"
    }
}
