package ge.hackerman.gza.core.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** [height] is a minimum: large system font sizes grow the badge rather than clip it. */
enum class RouteBadgeSize(val height: Dp, val iconSize: Dp, val minWidth: Dp) {
    Small(height = 22.dp, iconSize = 14.dp, minWidth = 36.dp),
    Medium(height = 28.dp, iconSize = 16.dp, minWidth = 48.dp),
    Large(height = 36.dp, iconSize = 20.dp, minWidth = 56.dp)
}
