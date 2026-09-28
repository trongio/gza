package ge.hackerman.gza.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.R
import ge.hackerman.gza.core.designsystem.icon.icon
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.designsystem.theme.RouteBadgeShape

/**
 * A route number on its route colour, led by the vehicle glyph so the kind reads without
 * colour. Bus and minibus get a plate shape, metro and cable car a pill. [color] is the
 * gateway's route colour; unspecified falls back to TTC's colour for the kind. The height
 * is a minimum, so large system font sizes grow the badge instead of clipping the number.
 */
@Composable
fun RouteBadge(
    number: String,
    mode: TransitMode,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    size: RouteBadgeSize = RouteBadgeSize.Medium
) {
    val background = if (color.isSpecified) color else defaultRouteColor(mode, number)
    val content = badgeContentColor(background)
    val shape = when (mode) {
        TransitMode.Bus, TransitMode.Minibus -> RouteBadgeShape
        TransitMode.Metro, TransitMode.CableCar -> CircleShape
    }
    val description = routeBadgeDescription(mode, number)
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = description }
            .heightIn(min = size.height)
            .widthIn(min = size.minWidth)
            .background(background, shape)
            .padding(horizontal = if (size == RouteBadgeSize.Small) 5.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(mode.icon()),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size.iconSize)
        )
        Text(text = number, style = size.textStyle(), color = content, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** "Bus 326", "Metro line 1": the spoken name of a route, also used inside row sentences. */
@Composable
@ReadOnlyComposable
fun routeBadgeDescription(mode: TransitMode, number: String): String = stringResource(
    when (mode) {
        TransitMode.Bus -> R.string.route_badge_bus
        TransitMode.Minibus -> R.string.route_badge_minibus
        TransitMode.Metro -> R.string.route_badge_metro
        TransitMode.CableCar -> R.string.route_badge_cable_car
    },
    number
)

@Composable
@ReadOnlyComposable
private fun RouteBadgeSize.textStyle(): TextStyle = when (this) {
    RouteBadgeSize.Small -> GzaTheme.typography.routeNumberSmall
    RouteBadgeSize.Medium -> GzaTheme.typography.routeNumber
    RouteBadgeSize.Large -> GzaTheme.typography.routeNumberLarge
}

@PreviewLightDark
@Composable
private fun RouteBadgePreview() {
    GzaTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RouteBadge("326", TransitMode.Bus)
            RouteBadge("551", TransitMode.Minibus)
            RouteBadge("1", TransitMode.Metro, size = RouteBadgeSize.Large)
            RouteBadge("2", TransitMode.CableCar, size = RouteBadgeSize.Small)
        }
    }
}
