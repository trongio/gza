package ge.hackerman.gza.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours Material has no role for. Departure status is a traffic light that needs no
 * legend: waiting is amber, live is green, late is red, timetable only is neutral.
 */
@Immutable
data class GzaColors(
    val waitingContainer: Color,
    val onWaitingContainer: Color,
    val waitingOutline: Color,
    val liveContainer: Color,
    val onLiveContainer: Color,
    val liveIndicator: Color,
    val lateContainer: Color,
    val onLateContainer: Color,
    val timetableOutline: Color,
    val onTimetable: Color,
    val mapLand: Color,
    val mapStreet: Color,
    val mapWater: Color
)

internal val LightGzaColors = GzaColors(
    waitingContainer = Color(0xFFFFC53D),
    onWaitingContainer = Color(0xFF3A2A00),
    // Amber on limestone alone is 1.5:1, so the pill needs an edge on the light background.
    waitingOutline = Color(0xFF9A6B00),
    liveContainer = Color(0xFFD5F5E3),
    onLiveContainer = Color(0xFF0B5B36),
    liveIndicator = Color(0xFF1B8A4E),
    lateContainer = LightColors.errorContainer,
    onLateContainer = LightColors.onErrorContainer,
    timetableOutline = LightColors.outlineVariant,
    onTimetable = LightColors.onSurfaceVariant,
    mapLand = Color(0xFFF3EDE6),
    mapStreet = Color(0xFFFFFFFF),
    mapWater = Color(0xFFB9DCE6)
)

internal val DarkGzaColors = GzaColors(
    waitingContainer = Color(0xFFFFC53D),
    onWaitingContainer = Color(0xFF3A2A00),
    waitingOutline = Color.Transparent,
    liveContainer = Color(0xFF0F3D27),
    onLiveContainer = Color(0xFF9BE8BD),
    liveIndicator = Color(0xFF5BD68F),
    lateContainer = DarkColors.errorContainer,
    onLateContainer = DarkColors.onErrorContainer,
    timetableOutline = DarkColors.outlineVariant,
    onTimetable = DarkColors.onSurfaceVariant,
    mapLand = Color(0xFF211A17),
    mapStreet = Color(0xFF3A302C),
    mapWater = Color(0xFF1D3640)
)

val LocalGzaColors = staticCompositionLocalOf { LightGzaColors }
