package ge.hackerman.gza.feature.now

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
class NowScreenScreenshotTest(
    // Only names the parameterised run in reports.
    @Suppress("unused") theme: String,
    private val darkTheme: Boolean
) {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nowScreen() = composeRule.captureThemed("now_screen", darkTheme, fullScreen = true) {
        NowScreen(onDepartureClick = {})
    }

    @Test
    @Config(qualifiers = "+ka")
    fun nowScreenGeorgian() = composeRule.captureThemed("now_screen_ka", darkTheme, fullScreen = true) {
        NowScreen(onDepartureClick = {})
    }

    @Test
    // The width of the user's phone (Galaxy S22 Ultra at default display size), where the
    // subtitle has to wrap at 1.5x.
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun nowScreenLargeFont() =
        composeRule.captureThemed("now_screen_font_scale_150", darkTheme, fontScale = 1.5f, fullScreen = true) {
            NowScreen(onDepartureClick = {})
        }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = ThemeParameters.both()
    }
}
