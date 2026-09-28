package ge.hackerman.gza.core.designsystem.testing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ge.hackerman.gza.core.testing.assertInteractiveNodesAccessible
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Proves the shared helper catches what it claims to, so a green screen test means something. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AccessibilityAssertionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun passesForANamedTargetOfAtLeast48dp() {
        composeRule.setContent {
            Box(Modifier.size(48.dp).semantics { contentDescription = "Open" }.clickable {})
        }
        composeRule.assertInteractiveNodesAccessible()
    }

    @Test
    fun acceptsVisibleTextAsTheName() {
        composeRule.setContent {
            Box(Modifier.size(56.dp).clickable {}) { Text("Map") }
        }
        composeRule.assertInteractiveNodesAccessible()
    }

    @Test
    fun failsForATargetSmallerThan48dp() {
        composeRule.setContent {
            Box(Modifier.size(40.dp).semantics { contentDescription = "Open" }.clickable {})
        }
        val error = assertFailsWith<IllegalStateException> { composeRule.assertInteractiveNodesAccessible() }
        assertTrue("touch target" in error.message.orEmpty())
    }

    @Test
    fun failsForATargetWithoutAName() {
        composeRule.setContent {
            Box(Modifier.size(48.dp).clickable {})
        }
        val error = assertFailsWith<IllegalStateException> { composeRule.assertInteractiveNodesAccessible() }
        assertTrue("no accessible name" in error.message.orEmpty())
    }

    @Test
    fun failsWhenFewerInteractiveNodesThanExpected() {
        composeRule.setContent { Text("Nothing to press") }
        assertFailsWith<IllegalStateException> { composeRule.assertInteractiveNodesAccessible() }
        composeRule.assertInteractiveNodesAccessible(expectAtLeast = 0)
    }
}
