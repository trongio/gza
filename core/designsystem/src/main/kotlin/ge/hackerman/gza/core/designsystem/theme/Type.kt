package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ge.hackerman.gza.core.designsystem.R

/**
 * Noto Sans Georgian, condensed (wdth 75): reads like transit signage, fits long Georgian
 * headlines and keeps route numbers one width (its digits are tabular).
 */
internal val GzaDisplayFamily = FontFamily(
    Font(R.font.gza_display_semibold, FontWeight.SemiBold),
    Font(R.font.gza_display_extrabold, FontWeight.ExtraBold)
)

/** FiraGO: one humanist voice across Georgian, Latin and Cyrillic. */
internal val GzaBodyFamily = FontFamily(
    Font(R.font.gza_body_regular, FontWeight.Normal),
    Font(R.font.gza_body_medium, FontWeight.Medium),
    Font(R.font.gza_body_semibold, FontWeight.SemiBold)
)

/**
 * Martian Mono, condensed (wdth 75), for times only. The bundled subset has digits, `:`,
 * `+`, `-`, `±`, `h` and spaces but **no Georgian**: never give it words. If Georgian ever
 * reached it, Android would fall back to the system face per glyph (no tofu), not Martian.
 */
val GzaMonoFamily = FontFamily(
    Font(R.font.gza_mono_medium, FontWeight.Medium),
    Font(R.font.gza_mono_bold, FontWeight.Bold)
)

private val M3 = Typography()

private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) =
    copy(fontFamily = GzaDisplayFamily, fontWeight = weight)

private fun TextStyle.body(weight: FontWeight) = copy(fontFamily = GzaBodyFamily, fontWeight = weight)

// Material 3 default sizes and line heights: Georgian's tall ascenders fit in them.
internal val GzaTypography = Typography(
    displayLarge = M3.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = M3.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = M3.displaySmall.display(),
    headlineLarge = M3.headlineLarge.display(),
    headlineMedium = M3.headlineMedium.display(),
    headlineSmall = M3.headlineSmall.display(),
    titleLarge = M3.titleLarge.display(),
    titleMedium = M3.titleMedium.body(FontWeight.Medium),
    titleSmall = M3.titleSmall.body(FontWeight.Medium),
    bodyLarge = M3.bodyLarge.body(FontWeight.Normal),
    bodyMedium = M3.bodyMedium.body(FontWeight.Normal),
    bodySmall = M3.bodySmall.body(FontWeight.Normal),
    labelLarge = M3.labelLarge.body(FontWeight.Medium),
    labelMedium = M3.labelMedium.body(FontWeight.Medium),
    labelSmall = M3.labelSmall.body(FontWeight.Medium)
)
