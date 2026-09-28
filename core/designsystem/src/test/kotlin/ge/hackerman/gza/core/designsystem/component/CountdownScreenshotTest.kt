package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.testing.ThemeParameters
import ge.hackerman.gza.core.testing.captureThemed
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CountdownScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun countdown() = composeRule.captureThemed("countdown", darkTheme) { Countdowns() }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}

private val Samples = listOf(
    Duration.ofMinutes(7),
    Duration.ofSeconds(42),
    Duration.ZERO,
    Duration.ofMinutes(65)
)

@Composable
private fun Countdowns() {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Samples.forEach { LeaveCountdown(it) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Samples.forEach { LeaveCountdown(it, size = CountdownSize.Compact) }
        }
    }
}
