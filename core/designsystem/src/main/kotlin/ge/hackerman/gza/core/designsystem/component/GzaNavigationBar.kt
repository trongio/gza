package ge.hackerman.gza.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.sp
import ge.hackerman.gza.core.designsystem.icon.GzaIcons
import ge.hackerman.gza.core.designsystem.theme.GzaTheme

@Composable
fun GzaNavigationBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        content = content
    )
}

/**
 * A bottom bar destination. The icon has no description because the always visible label
 * names the item; repeating it would make TalkBack read it twice. Material gives the item
 * a tab role, its selected state and a target well over 48dp.
 */
@Composable
fun RowScope.GzaNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    @DrawableRes selectedIcon: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(if (selected) selectedIcon else icon), contentDescription = null) },
        label = { NavigationLabel(label) },
        modifier = modifier,
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.onSecondaryContainer,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.secondaryContainer,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant
        )
    )
}

/**
 * Shrinks to fit rather than cutting the word: "პარამეტრები" (Settings) is wider than a
 * fifth of a phone at 12sp. Colour and style come from the item.
 */
@Composable
private fun NavigationLabel(label: String) {
    val style = LocalTextStyle.current.copy(color = LocalContentColor.current, letterSpacing = 0.sp)
    BasicText(
        text = label,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp)
    )
}

@PreviewLightDark
@Composable
private fun GzaNavigationBarPreview() {
    GzaTheme {
        GzaNavigationBar {
            GzaNavigationBarItem(true, {}, GzaIcons.Now, GzaIcons.NowSelected, "Now")
            GzaNavigationBarItem(false, {}, GzaIcons.Map, GzaIcons.MapSelected, "Map")
        }
    }
}
