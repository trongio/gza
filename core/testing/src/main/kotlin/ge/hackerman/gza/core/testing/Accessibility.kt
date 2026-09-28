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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Every node a user can act on (click, select, toggle, type into) has a non-blank
 * accessible name and a touch target of at least [minTouchTarget] in both directions.
 * The name may come from a content description or from visible text: items with a
 * visible label must not repeat it as a description, or TalkBack reads it twice, so a
 * description equal to or contained in the node's own text fails too.
 *
 * The touch target is the node's layout size, widened to the size a Material
 * `minimumInteractiveComponentSize()` on the same layout reserves (an `IconButton` draws
 * 40dp inside an enforced 48dp target).
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
    val layoutSize = touchTargetSize()
    val problems = buildList {
        if (accessibleName().isBlank()) add("no accessible name")
        repeatedDescription()?.let { add("content description '$it' repeats the visible text") }
        val minPx = with(layoutInfo.density) { minTouchTarget.toPx() } - HALF_PIXEL
        if (layoutSize.width < minPx || layoutSize.height < minPx) {
            val density = layoutInfo.density.density
            add("touch target ${layoutSize.width / density}dp x ${layoutSize.height / density}dp < $minTouchTarget")
        }
    }
    if (problems.isEmpty()) return null
    val tag = config.getOrNull(SemanticsProperties.TestTag)
    return "node #$id (tag=$tag, name='${accessibleName()}'): ${problems.joinToString()}"
}

/**
 * Not touchBoundsInRoot, which Compose widens to the 48dp minimum for every pointer-input
 * node (so it could never fail), and not boundsInRoot, which is clipped by scrolling (a row
 * half under the bottom bar is still a full-size target). Only a minimum size Material
 * enforces on purpose counts on top of the node's own size, so padding around a small
 * clickable does not pass for a target.
 */
private fun SemanticsNode.touchTargetSize(): IntSize {
    val enforced = layoutInfo.getModifierInfo()
        .filter { it.modifier::class.simpleName == MATERIAL_MIN_INTERACTIVE_MODIFIER && it.coordinates.isAttached }
        .map { it.coordinates.size }
    return (enforced + size).reduce { a, b -> IntSize(maxOf(a.width, b.width), maxOf(a.height, b.height)) }
}

private fun SemanticsNode.repeatedDescription(): String? {
    val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")?.trim().orEmpty()
    if (text.isEmpty()) return null
    return config.getOrNull(SemanticsProperties.ContentDescription)
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotEmpty() && text.contains(it, ignoreCase = true) }
}

private fun SemanticsNode.accessibleName(): String = listOfNotNull(
    config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.Text)?.joinToString(" "),
    config.getOrNull(SemanticsProperties.EditableText)?.text
).joinToString(" ").trim()

private const val HALF_PIXEL = 0.5f

/** Material 3's `minimumInteractiveComponentSize()` element; AccessibilityAssertionTest breaks if it is renamed. */
private const val MATERIAL_MIN_INTERACTIVE_MODIFIER = "MinimumInteractiveModifier"
