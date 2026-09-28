package ge.hackerman.gza

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ge.hackerman.gza.core.designsystem.R as DesignR
import ge.hackerman.gza.feature.map.MapTestTags
import ge.hackerman.gza.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What TalkBack really gets for the Map sheet handle. The Robolectric tests check the Compose
 * semantics tree; this reads the platform AccessibilityNodeInfo tree, which is what a screen
 * reader focuses and acts on, so it also catches a label or action that lands on a child
 * node the reader never stops on.
 */
@RunWith(AndroidJUnit4::class)
class MapSheetHandleAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val uiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Test
    fun handleIsOneFocusableNodeWithItsLabelAndExpandAction() {
        composeRule.onNodeWithTag(TopLevelDestination.MAP.testTag).performClick()
        composeRule.waitForIdle()
        val label = composeRule.activity.getString(DesignR.string.sheet_drag_handle)

        val handle = findHandle()
        val subtree = handle.subtree()
        val dump = subtree.joinToString("\n") { it.describe() }

        val focusStops = subtree.filter { it.isScreenReaderFocusable || (it.isFocusable && it.isVisibleToUser) }
        assertEquals("Exactly one focus stop in the handle:\n$dump", 1, focusStops.size)
        val stop = focusStops.single()
        assertTrue("The focus stop is the clickable handle:\n$dump", stop.isClickable)
        assertTrue(
            "The focused node offers expand while the sheet is collapsed:\n$dump",
            stop.actionList.any { it.id == AccessibilityAction.ACTION_EXPAND.id }
        )
        val spoken = subtree.mapNotNull { it.contentDescription?.toString() }
        assertTrue("The handle is announced as '$label':\n$dump", spoken.any { it == label })
    }

    private fun findHandle(): AccessibilityNodeInfo {
        repeat(RETRIES) {
            uiAutomation.rootInActiveWindow?.subtree()
                ?.firstOrNull { it.viewIdResourceName == MapTestTags.SHEET_HANDLE }
                ?.let { return it }
            Thread.sleep(RETRY_DELAY_MS)
        }
        error("No accessibility node with id ${MapTestTags.SHEET_HANDLE}")
    }

    private fun AccessibilityNodeInfo.subtree(): List<AccessibilityNodeInfo> =
        listOf(this) + (0 until childCount).mapNotNull { getChild(it) }.flatMap { it.subtree() }

    private fun AccessibilityNodeInfo.describe(): String =
        "class=$className id=$viewIdResourceName desc=$contentDescription text=$text " +
            "clickable=$isClickable focusable=$isFocusable srFocusable=$isScreenReaderFocusable " +
            "important=$isImportantForAccessibility actions=${actionList.map { it.label ?: it.id }}"

    private companion object {
        const val RETRIES = 20
        const val RETRY_DELAY_MS = 250L
    }
}
