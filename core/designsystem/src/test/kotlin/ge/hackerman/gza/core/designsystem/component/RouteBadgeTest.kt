package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RouteBadgeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun badge(number: String, mode: TransitMode) = composeRule.setContent {
        GzaTheme { RouteBadge(number, mode) }
    }

    @Test
    fun busBadgeIsReadAsBusAndNumber() {
        badge("326", TransitMode.Bus)
        composeRule.onNodeWithContentDescription("Bus 326").assertHasNoClickAction()
    }

    @Test
    fun minibusBadge() {
        badge("551", TransitMode.Minibus)
        composeRule.onNodeWithContentDescription("Minibus 551").assertExists()
    }

    @Test
    fun metroBadgeNamesTheLine() {
        badge("1", TransitMode.Metro)
        composeRule.onNodeWithContentDescription("Metro line 1").assertExists()
    }

    @Test
    fun cableCarBadge() {
        badge("2", TransitMode.CableCar)
        composeRule.onNodeWithContentDescription("Cable car 2").assertExists()
    }

    @Test
    @Config(qualifiers = "+ka")
    fun georgianDescription() {
        badge("326", TransitMode.Bus)
        composeRule.onNodeWithContentDescription("ავტობუსი 326").assertExists()
    }

    @Test
    fun mediumBadgeIs28dpTall() {
        badge("326", TransitMode.Bus)
        composeRule.onNodeWithContentDescription("Bus 326").assertHeightIsEqualTo(28.dp)
    }
}
