package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Green "road" palette. Roles not fixed by hand come from the Material Theme Builder
// (2021 spec, tonal spot) for seed #1B6B50. T06 revisits the palette.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF1B6B50),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA6F2D0),
    onPrimaryContainer = Color(0xFF00513A),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF354B41),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FC),
    onTertiaryContainer = Color(0xFF254B5B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6FBF6),
    onBackground = Color(0xFF171D1A),
    surface = Color(0xFFF6FBF6),
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2ED),
    inversePrimary = Color(0xFF8BD6B4),
    surfaceDim = Color(0xFFD6DBD6),
    surfaceBright = Color(0xFFF6FBF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5EF),
    surfaceContainer = Color(0xFFEAEFEA),
    surfaceContainerHigh = Color(0xFFE4EAE4),
    surfaceContainerHighest = Color(0xFFDEE4DF)
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD6B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA6F2D0),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFA6CCDF),
    onTertiary = Color(0xFF093544),
    tertiaryContainer = Color(0xFF254B5B),
    onTertiaryContainer = Color(0xFFC1E8FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DF),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDEE4DF),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF1B6B50),
    surfaceDim = Color(0xFF0F1512),
    surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633)
)
