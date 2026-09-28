package ge.hackerman.gza.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlaceholderScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            GzaTheme {
                PlaceholderScreen()
            }
        }
    }

    @Test
    fun showsTitleAndSubtitleInEnglish() {
        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE)
            .assertIsDisplayed()
            .assertTextEquals("Gza")
        composeRule.onNodeWithTag(PlaceholderTestTags.SUBTITLE)
            .assertIsDisplayed()
            .assertTextEquals("Unofficial Tbilisi public transport app")
    }

    @Test
    fun titleIsAHeadingForTalkBack() {
        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE).assert(isHeading())
    }

    @Test
    @Config(qualifiers = "+ka")
    fun showsGeorgianStringsUnderKaLocale() {
        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE).assertTextEquals("გზა")
    }
}
