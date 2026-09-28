package ge.hackerman.gza.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ge.hackerman.gza.core.designsystem.theme.GzaColors
import ge.hackerman.gza.core.designsystem.theme.GzaTheme
import ge.hackerman.gza.core.designsystem.theme.TransitColors

/**
 * A drawn schematic, not a real map (MapLibre arrives in T11): streets, the river, route
 * 326, the stop, two moving buses and the parked one at the terminus in the waiting colours.
 * Points are fractions of the canvas, so it fits any screen.
 */
@Composable
internal fun MapPreviewCanvas(stopColor: Color, modifier: Modifier = Modifier) {
    val colors = GzaTheme.colors
    Canvas(modifier) {
        drawRect(colors.mapLand)
        drawStreets(colors)
        drawRiver(colors)
        drawRoute()
        drawStop(stopColor)
        MovingBuses.forEach { drawBus(at(it)) }
        drawParkedBus(at(Terminus), colors)
    }
}

private class Street(val from: Offset, val to: Offset, val wide: Boolean)

private val Streets = listOf(
    Street(Offset(0f, 0.18f), Offset(1f, 0.10f), wide = true),
    Street(Offset(0f, 0.58f), Offset(1f, 0.46f), wide = true),
    Street(Offset(0.30f, 0f), Offset(0.36f, 1f), wide = false),
    Street(Offset(0.62f, 0f), Offset(0.84f, 1f), wide = false),
    Street(Offset(0f, 0.36f), Offset(0.62f, 0.30f), wide = false)
)

private val RiverStart = Offset(0.05f, 0f)

// Two control points and the end of the river's cubic curve.
private val RiverCurve = listOf(Offset(0.35f, 0.30f), Offset(-0.05f, 0.60f), Offset(0.20f, 1f))

private val Terminus = Offset(0.22f, 0.12f)
private val StopPoint = Offset(0.60f, 0.31f)
private val Route326 = listOf(
    Terminus,
    Offset(0.33f, 0.17f),
    Offset(0.35f, 0.33f),
    StopPoint,
    Offset(0.66f, 0.49f),
    Offset(0.90f, 0.46f)
)
private val MovingBuses = listOf(Offset(0.45f, 0.322f), Offset(0.78f, 0.475f))

private val WideStreet = 10.dp
private val NarrowStreet = 6.dp
private val RiverWidth = 26.dp
private val RouteWidth = 6.dp
private val StopRadius = 9.dp
private val StopRing = 3.dp
private val BusHalo = 11.dp
private val BusRadius = 8.dp
private val ParkedWidth = 34.dp
private val ParkedHeight = 20.dp
private val ParkedOutline = 1.5.dp
private val PauseBarWidth = 3.dp
private val PauseBarHeight = 9.dp
private val PauseBarOffset = 3.dp

private fun DrawScope.at(point: Offset) = Offset(size.width * point.x, size.height * point.y)

private fun DrawScope.drawStreets(colors: GzaColors) {
    Streets.forEach {
        val width = (if (it.wide) WideStreet else NarrowStreet).toPx()
        drawLine(colors.mapStreet, at(it.from), at(it.to), width, StrokeCap.Round)
    }
}

private fun DrawScope.drawRiver(colors: GzaColors) {
    val start = at(RiverStart)
    val (c1, c2, end) = RiverCurve.map { at(it) }
    val river = Path().apply {
        moveTo(start.x, start.y)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    }
    drawPath(river, colors.mapWater, style = Stroke(width = RiverWidth.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawRoute() {
    val points = Route326.map { at(it) }
    val route = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        route,
        TransitColors.Bus,
        style = Stroke(width = RouteWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawStop(color: Color) {
    val center = at(StopPoint)
    drawCircle(Color.White, radius = StopRadius.toPx(), center = center)
    drawCircle(color, radius = StopRadius.toPx(), center = center, style = Stroke(width = StopRing.toPx()))
}

private fun DrawScope.drawBus(center: Offset) {
    drawCircle(Color.White, radius = BusHalo.toPx(), center = center)
    drawCircle(TransitColors.Bus, radius = BusRadius.toPx(), center = center)
}

/** The parked bus: an amber pill with pause bars, the same language as the waiting chip. */
private fun DrawScope.drawParkedBus(center: Offset, colors: GzaColors) {
    val pill = Size(ParkedWidth.toPx(), ParkedHeight.toPx())
    val topLeft = Offset(center.x - pill.width / 2, center.y - pill.height / 2)
    val radius = CornerRadius(pill.height / 2)
    drawRoundRect(colors.waitingContainer, topLeft, pill, radius)
    drawRoundRect(colors.onWaitingContainer, topLeft, pill, radius, style = Stroke(width = ParkedOutline.toPx()))
    val bar = Size(PauseBarWidth.toPx(), PauseBarHeight.toPx())
    listOf(-PauseBarOffset.toPx(), PauseBarOffset.toPx()).forEach { dx ->
        drawRect(colors.onWaitingContainer, Offset(center.x + dx - bar.width / 2, center.y - bar.height / 2), bar)
    }
}
