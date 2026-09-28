package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ge.hackerman.gza.core.designsystem.theme.TransitColors

private val HexColor = Regex("^#?([0-9a-fA-F]{6})$")

/** Dark ink for text on light route colours; near black with a hint of the brand warmth. */
val BadgeInk = Color(0xFF0B0F0E)

/** Parses the gateway's `color` field (`"00B38B"`, `"#f5861f"`); anything else is null. */
fun parseRouteColor(hex: String?): Color? {
    val digits = hex?.let { HexColor.matchEntire(it.trim()) }?.groupValues?.get(1) ?: return null
    return Color(OPAQUE or digits.toLong(radix = HEX_RADIX))
}

/** TTC's colour for a route when the gateway sent none or a malformed one. */
fun defaultRouteColor(mode: TransitMode, number: String): Color = when (mode) {
    TransitMode.Bus -> TransitColors.Bus
    TransitMode.Minibus -> TransitColors.Minibus
    TransitMode.Metro -> if (number.trim() == "2") TransitColors.MetroLine2 else TransitColors.MetroLine1
    TransitMode.CableCar -> TransitColors.CableCar
}

/**
 * Text colour for a badge: ink or white, whichever contrasts more with [background].
 * Computed, never hand-picked: white on the bus teal would be only 2.7:1.
 */
fun badgeContentColor(background: Color): Color =
    if (contrastRatio(background, BadgeInk) >= contrastRatio(background, Color.White)) BadgeInk else Color.White

/** WCAG 2 contrast ratio between two opaque colours, from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + WCAG_FLARE) / (minOf(la, lb) + WCAG_FLARE)
}

private const val WCAG_FLARE = 0.05f
private const val OPAQUE = 0xFF000000
private const val HEX_RADIX = 16
