package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The Gza theme. No dynamic color on purpose: route colours and chips must look the same
 * on every device, and screenshots must be deterministic.
 */
@Composable
fun GzaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GzaTypography,
        content = content
    )
}
