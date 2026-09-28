package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The Gza theme. No dynamic colour on purpose: route badges and status chips carry meaning
 * through colour, and a wallpaper-derived primary could land on amber, green or red and
 * collide with them. The brick identity is the point, and screenshots stay deterministic.
 */
@Composable
fun GzaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGzaColors provides if (darkTheme) DarkGzaColors else LightGzaColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = GzaTypography,
            shapes = GzaShapes,
            content = content
        )
    }
}

/** Gza's own tokens next to [MaterialTheme]'s: `GzaTheme.colors.waitingContainer`. */
object GzaTheme {
    val colors: GzaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGzaColors.current
}
