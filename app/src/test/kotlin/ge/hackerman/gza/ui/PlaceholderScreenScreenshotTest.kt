package ge.hackerman.gza.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlaceholderScreenScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Absorbs anti-aliasing noise between machines.
    private val options = RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
    )

    private fun capture(name: String, darkTheme: Boolean) {
        composeRule.setContent {
            GzaTheme(darkTheme = darkTheme) {
                PlaceholderScreen()
            }
        }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziOptions = options
        )
    }

    @Test
    fun placeholderLight() = capture("placeholder_light", darkTheme = false)

    @Test
    fun placeholderDark() = capture("placeholder_dark", darkTheme = true)
}
