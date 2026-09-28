package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * TTC's own route colours (live /v3/routes, see docs/TTC_API.md), so badges match the signs
 * and vehicles people see. The same in light and dark themes.
 */
object TransitColors {
    val Bus = Color(0xFF00B38B)
    val Minibus = Color(0xFF0033B4)
    val MetroLine1 = Color(0xFFFF505B)
    val MetroLine2 = Color(0xFF5CA330)
    val CableCar = Color(0xFFF5861F)

    val all: List<Color> = listOf(Bus, Minibus, MetroLine1, MetroLine2, CableCar)
}
