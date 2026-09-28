package ge.hackerman.gza.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.feature.plan.PlanTestTags
import ge.hackerman.gza.navigation.TopLevelDestination
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Landscape with the camera cutout on the left: content and the bottom bar must start after
 * it, on every screen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
class GzaAppInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentClearsASideCutout() {
        composeRule.setContent { GzaTheme { GzaApp() } }
        val root: View = composeRule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(CUTOUT_PX, 0, 0, 0))
            .build()
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(root, insets) }
        composeRule.waitForIdle()

        // The bottom bar is outside the content insets, so it has to clear the cutout itself.
        // Material's default bar insets include the cutout; this guards against losing that.
        val firstTab = composeRule.onNodeWithTag(TopLevelDestination.entries.first().testTag)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(firstTab.left >= CUTOUT_PX, "first tab starts at ${firstTab.left}px, under the cutout")

        composeRule.onNodeWithTag(TopLevelDestination.PLAN.testTag).performClick()
        val field = composeRule.onNodeWithTag(PlanTestTags.FROM).fetchSemanticsNode().boundsInRoot
        assertTrue(field.left >= CUTOUT_PX, "From field starts at ${field.left}px, under the cutout")
        // The top bar pads once, not twice.
        val title = composeRule.onNodeWithText("Plan a trip").fetchSemanticsNode().boundsInRoot
        assertTrue(title.left in CUTOUT_PX.toFloat()..<2f * CUTOUT_PX, "title starts at ${title.left}px")
    }

    private companion object {
        const val CUTOUT_PX = 120
    }
}
