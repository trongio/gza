package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun backgroundIn(darkTheme: Boolean): Color {
        var background = Color.Unspecified
        composeRule.setContent {
            GzaTheme(darkTheme = darkTheme) {
                background = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()
        return background
    }

    @Test
    fun lightThemeUsesLightColors() {
        assertEquals(LightColors.background, backgroundIn(darkTheme = false))
    }

    @Test
    fun darkThemeUsesDarkColors() {
        assertEquals(DarkColors.background, backgroundIn(darkTheme = true))
    }

    @Test
    fun lightAndDarkBackgroundsDiffer() {
        assertNotEquals(LightColors.background, DarkColors.background)
    }
}
