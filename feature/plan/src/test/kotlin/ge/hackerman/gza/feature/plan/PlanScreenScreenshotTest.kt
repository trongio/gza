package ge.hackerman.gza.feature.plan

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
class PlanScreenScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun planScreen() = composeRule.captureThemed("plan_screen", darkTheme, fullScreen = true) {
        PlanScreen(mode = PlanMode.ArriveBy, onModeSelected = {})
    }

    @Test
    @Config(qualifiers = "+ka")
    fun planScreenGeorgian() = composeRule.captureThemed("plan_screen_ka", darkTheme, fullScreen = true) {
        PlanScreen(mode = PlanMode.ArriveBy, onModeSelected = {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}
