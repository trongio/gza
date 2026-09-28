package ge.hackerman.gza

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.ui.PlaceholderTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Launches the real Hilt app (GzaApplication from the manifest), so no Hilt test runner yet.
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesAndShowsThePlaceholder() {
        composeRule.onNodeWithTag(PlaceholderTestTags.TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(PlaceholderTestTags.SUBTITLE).assertIsDisplayed()
    }
}
