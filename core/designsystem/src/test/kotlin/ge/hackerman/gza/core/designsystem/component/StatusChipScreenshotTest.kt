package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.testing.ThemeParameters
import ge.hackerman.gza.core.testing.captureThemed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StatusChipScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun statusChips() = composeRule.captureThemed("status_chips", darkTheme) { Chips() }

    @Test
    @Config(qualifiers = "+ka")
    fun statusChipsGeorgian() = composeRule.captureThemed("status_chips_ka", darkTheme) { Chips() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

@Composable
private fun Chips() {
    // Narrow like the middle column of a departure row, so long Georgian has to wrap.
    Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(DepartureStatus.Waiting("17:13"))
        StatusChip(DepartureStatus.Live(3))
        StatusChip(DepartureStatus.Live(1))
        StatusChip(DepartureStatus.Live(null))
        StatusChip(DepartureStatus.Late(6))
        StatusChip(DepartureStatus.TimetableOnly)
    }
}
