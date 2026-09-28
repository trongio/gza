package ge.hackerman.gza.core.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

/** Absorbs anti-aliasing noise between machines. */
val GzaRoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f)
)

const val SCREENSHOT_TAG = "screenshot"

/**
 * Renders [content] in GzaTheme on a background Surface and saves
 * `src/test/screenshots/<name>_<light|dark>.png`. Components are captured at their own
 * size to keep goldens small; screens pass [fullScreen] to capture the whole window.
 */
fun ComposeContentTestRule.captureThemed(
    name: String,
    darkTheme: Boolean,
    fontScale: Float = 1f,
    fullScreen: Boolean = false,
    content: @Composable () -> Unit
) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            GzaTheme(darkTheme = darkTheme) {
                // A Surface, like real screens, so components that inherit LocalContentColor
                // get onBackground instead of black.
                Surface(
                    modifier = Modifier.testTag(SCREENSHOT_TAG).then(
                        if (fullScreen) Modifier.fillMaxSize() else Modifier
                    ),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (fullScreen) content() else Box(Modifier.padding(16.dp)) { content() }
                }
            }
        }
    }
    val suffix = if (darkTheme) "dark" else "light"
    val node = if (fullScreen) onRoot() else onNodeWithTag(SCREENSHOT_TAG)
    node.captureRoboImage(
        filePath = "src/test/screenshots/${name}_$suffix.png",
        roborazziOptions = GzaRoborazziOptions
    )
}
