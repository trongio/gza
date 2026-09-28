package ge.hackerman.gza.feature.settings

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent { GzaTheme { Surface { SettingsScreen(versionName = "0.1.0") } } }
    }

    @Test
    fun saysTheAppIsUnofficial() {
        composeRule.onNodeWithText(
            "Gza is an unofficial app. It is not affiliated with Tbilisi Transport Company."
        ).assertExists()
    }

    @Test
    fun showsTheVersion() {
        composeRule.onNodeWithText("Version 0.1.0").assertExists()
    }

    @Test
    fun nothingInteractiveIsUnnamed() {
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 0)
    }
}
