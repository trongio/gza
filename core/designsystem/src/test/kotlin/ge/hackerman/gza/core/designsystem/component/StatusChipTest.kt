package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StatusChipTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun assertChipText(status: DepartureStatus, expected: String) {
        composeRule.setContent { GzaTheme { StatusChip(status) } }
        composeRule.onNodeWithText(expected).assertExists().assertHasNoClickAction()
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 0)
    }

    @Test
    fun waitingNamesTheTerminusAndTheLeaveTime() =
        assertChipText(DepartureStatus.Waiting("17:13"), "Waiting at terminus, leaves 17:13")

    @Test
    fun liveWithStopsAway() = assertChipText(DepartureStatus.Live(3), "Live, 3 stops away")

    @Test
    fun liveWithOneStopUsesTheSingular() = assertChipText(DepartureStatus.Live(1), "Live, 1 stop away")

    @Test
    fun liveWithoutACount() = assertChipText(DepartureStatus.Live(null), "Live")

    @Test
    fun late() = assertChipText(DepartureStatus.Late(6), "Running 6 min late")

    @Test
    fun timetableOnly() = assertChipText(DepartureStatus.TimetableOnly, "Timetable only")

    // A no-break space keeps the time on the line with its verb, "გადის 17:13-ზე".
    @Test
    @Config(qualifiers = "+ka")
    fun georgianWaiting() =
        assertChipText(DepartureStatus.Waiting("17:13"), "ელოდება ბოლო გაჩერებაზე, გადის\u00A017:13-ზე")
}
