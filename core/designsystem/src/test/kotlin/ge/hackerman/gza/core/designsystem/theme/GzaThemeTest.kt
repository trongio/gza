package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
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

    private fun themeIn(darkTheme: Boolean): Pair<ColorScheme, GzaColors> {
        lateinit var scheme: ColorScheme
        lateinit var colors: GzaColors
        composeRule.setContent {
            GzaTheme(darkTheme = darkTheme) {
                scheme = MaterialTheme.colorScheme
                colors = GzaTheme.colors
            }
        }
        composeRule.waitForIdle()
        return scheme to colors
    }

    @Test
    fun lightThemeUsesLightColors() {
        val (scheme, colors) = themeIn(darkTheme = false)
        assertEquals(LightColors.background, scheme.background)
        assertSame(LightGzaColors, colors)
    }

    @Test
    fun darkThemeUsesDarkColors() {
        val (scheme, colors) = themeIn(darkTheme = true)
        assertEquals(DarkColors.background, scheme.background)
        assertSame(DarkGzaColors, colors)
    }

    @Test
    fun lightAndDarkBackgroundsDiffer() {
        assertNotEquals(LightColors.background, DarkColors.background)
    }

    @Test
    fun waitingNeverLooksLikeABadgeOrTheBrand() {
        listOf(LightGzaColors to LightColors, DarkGzaColors to DarkColors).forEach { (gza, scheme) ->
            TransitColors.all.forEach { assertNotEquals(it, gza.waitingContainer) }
            assertNotEquals(scheme.primary, gza.waitingContainer)
            assertNotEquals(scheme.primaryContainer, gza.waitingContainer)
        }
    }

    @Test
    fun lateUsesTheSchemeErrorRoles() {
        assertEquals(LightColors.errorContainer, LightGzaColors.lateContainer)
        assertEquals(DarkColors.onErrorContainer, DarkGzaColors.onLateContainer)
    }
}
