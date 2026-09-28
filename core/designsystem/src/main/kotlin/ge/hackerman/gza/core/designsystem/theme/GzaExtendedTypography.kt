package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Styles Material has no role for: times in the mono face and route numbers. All in sp. */
@Immutable
data class GzaExtendedTypography(
    val timeHero: TextStyle = mono(FontWeight.Bold, size = 64, lineHeight = 64).copy(letterSpacing = (-1).sp),
    val timeLarge: TextStyle = mono(FontWeight.Bold, size = 24, lineHeight = 28),
    val timeMedium: TextStyle = mono(FontWeight.Medium, size = 16, lineHeight = 20),
    val timeSmall: TextStyle = mono(FontWeight.Medium, size = 13, lineHeight = 16),
    val routeNumber: TextStyle = routeNumber(size = 16, lineHeight = 20),
    val routeNumberLarge: TextStyle = routeNumber(size = 22, lineHeight = 26),
    val routeNumberSmall: TextStyle = routeNumber(size = 13, lineHeight = 16)
)

private fun mono(weight: FontWeight, size: Int, lineHeight: Int) =
    TextStyle(fontFamily = GzaMonoFamily, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

private fun routeNumber(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = GzaDisplayFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp
)

val LocalGzaTypography = staticCompositionLocalOf { GzaExtendedTypography() }
