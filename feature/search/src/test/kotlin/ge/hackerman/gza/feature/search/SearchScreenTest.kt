package ge.hackerman.gza.feature.search

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<String>()

    private fun show() = composeRule.setContent {
        var query by remember { mutableStateOf("") }
        GzaTheme {
            Surface {
                SearchScreen(query = query, onQueryChange = {
                    changes += it
                    query = it
                })
            }
        }
    }

    @Test
    fun typingReportsTheText() {
        show()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).performTextInput("Freedom")
        assertEquals("Freedom", changes.last())
    }

    @Test
    fun fieldIsNamedByItsLabel() {
        show()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).assert(hasText("Stop name or code"))
    }

    @Test
    fun showsRecentSampleStops() {
        show()
        composeRule.onNodeWithText("Ana Politkovskaia Street").assertExists()
        composeRule.onNodeWithText("Code 970", substring = true).assertExists()
    }

    @Test
    fun everyInteractiveElementIsNamedAndBigEnough() {
        show()
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 1)
    }
}
