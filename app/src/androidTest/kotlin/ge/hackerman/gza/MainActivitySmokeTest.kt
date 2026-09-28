package ge.hackerman.gza

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.feature.now.NowTestTags
import ge.hackerman.gza.navigation.TopLevelDestination
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Launches the real Hilt app (GzaApplication from the manifest), so no Hilt test runner yet.
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesOnTheNowTab() {
        composeRule.onNodeWithTag(NowTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(TopLevelDestination.NOW.testTag).assertIsDisplayed().assertIsSelected()
    }
}
