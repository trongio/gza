package ge.hackerman.gza.core.testing

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every node a user can act on (click, select, toggle, type into) has a non-blank
 * accessible name and a touch target of at least [minTouchTarget] in both directions.
 * The name may come from a content description or from visible text: items with a
 * visible label must not repeat it as a description, or TalkBack reads it twice.
 */
fun SemanticsNodeInteractionsProvider.assertInteractiveNodesAccessible(
    minTouchTarget: Dp = 48.dp,
    expectAtLeast: Int = 1
) {
    val nodes = onAllNodes(hasClickAction() or isSelectable() or isToggleable() or hasSetTextAction())
        .fetchSemanticsNodes()
    check(nodes.size >= expectAtLeast) {
        "Expected at least $expectAtLeast interactive nodes, found ${nodes.size}"
    }
    val failures = nodes.mapNotNull { node -> node.accessibilityProblem(minTouchTarget) }
    check(failures.isEmpty()) {
        "Inaccessible interactive nodes:\n" + failures.joinToString("\n")
    }
}

private fun SemanticsNode.accessibilityProblem(minTouchTarget: Dp): String? {
    val problems = buildList {
        if (accessibleName().isBlank()) add("no accessible name")
        val minPx = with(layoutInfo.density) { minTouchTarget.toPx() } - HALF_PIXEL
        // Layout bounds, not touchBoundsInRoot: Compose widens every pointer-input node's
        // touchBoundsInRoot to the 48dp minimum, so checking it could never fail. Material
        // components meet 48dp in layout through minimumInteractiveComponentSize.
        val bounds = boundsInRoot
        if (bounds.width < minPx || bounds.height < minPx) {
            val density = layoutInfo.density.density
            add("touch target ${bounds.width / density}dp x ${bounds.height / density}dp < $minTouchTarget")
        }
    }
    if (problems.isEmpty()) return null
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    return "node #$id (tag=$tag, name='${accessibleName()}'): ${problems.joinToString()}"
}

private fun SemanticsNode.accessibleName(): String = listOfNotNull(
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.EditableText)?.text
).joinToString(" ").trim()

private const val HALF_PIXEL = 0.5f
