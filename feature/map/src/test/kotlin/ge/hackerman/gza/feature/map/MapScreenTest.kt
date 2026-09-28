package ge.hackerman.gza.feature.map

import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
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
class MapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent { GzaTheme { Surface { MapScreen() } } }
    }

    @Test
    fun mapPreviewIsDescribed() {
        composeRule.onNodeWithTag(MapTestTags.CANVAS)
            .assertContentDescriptionEquals("Map preview with sample buses on route 326")
    }

    @Test
    fun sheetNamesTheStop() {
        composeRule.onNodeWithText("Departures from Ana Politkovskaia Street").assertExists()
    }

    @Test
    fun sheetHandleIsTallEnough() {
        composeRule.onNodeWithTag(MapTestTags.SHEET_HANDLE, useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun everyInteractiveElementIsNamedAndBigEnough() {
        // The sheet handle is the one control.
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 1)
    }
}
