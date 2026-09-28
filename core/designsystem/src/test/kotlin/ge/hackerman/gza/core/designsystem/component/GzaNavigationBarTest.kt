package ge.hackerman.gza.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GzaNavigationBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fiveLabelledItemsOnlyTheCurrentOneSelected() {
        composeRule.setContent { GzaTheme { SampleNavigationBar(selected = 0) } }
        SampleTabs.forEachIndexed { index, tab ->
            val item = composeRule.onNodeWithTag("tab_$index")
                .assertTextEquals(tab.label)
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
            if (index == 0) item.assertIsSelected() else item.assertIsNotSelected()
        }
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
    }

    @Test
    fun clickingAnItemReportsIt() {
        val clicked = mutableListOf<Int>()
        composeRule.setContent { GzaTheme { SampleNavigationBar(selected = 0, onClick = { clicked += it }) } }
        composeRule.onNodeWithTag("tab_2").performClick()
        composeRule.onNodeWithTag("tab_4").performClick()
        assertEquals(listOf(2, 4), clicked)
    }

    @Test
    fun ellipsizedLabelStillSpeaksTheWholeName() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                GzaTheme { SampleNavigationBar(selected = 0, labels = GeorgianLabels) }
            }
        }
        composeRule.onNodeWithTag("tab_4").assertTextEquals("პარამეტრები")
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 5)
    }

    @Test
    fun labelsStayInsideTheirItemAtLargeFont() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                GzaTheme { SampleNavigationBar(selected = 0) }
            }
        }
        SampleTabs.indices.forEach { index ->
            val item = composeRule.onNodeWithTag("tab_$index").fetchSemanticsNode().boundsInRoot
            val label = composeRule.onNode(
                hasText(SampleTabs[index].label) and hasAnyAncestor(hasTestTag("tab_$index")),
                useUnmergedTree = true
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(label.left >= item.left && label.right <= item.right, "tab $index label $label outside $item")
        }
    }
}
