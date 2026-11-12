package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dylanakp.abysstvlauncher.data.WeatherIconType
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherIcon(
    type: WeatherIconType,
    color: Color = Color.White,
    size: Dp = 15.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = (w * 0.10f).coerceAtLeast(1.5f)
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (type) {
            WeatherIconType.SUNNY -> drawSunny(w, h, color, strokeWidth)
            WeatherIconType.CLEAR_NIGHT -> drawClearNight(w, h, color)
            WeatherIconType.PARTLY_CLOUDY_DAY -> drawPartlyCloudyDay(w, h, color, stroke, strokeWidth)
            WeatherIconType.PARTLY_CLOUDY_NIGHT -> drawPartlyCloudyNight(w, h, color, stroke)
            WeatherIconType.CLOUDY -> drawCloud(w, h, color, stroke)
            WeatherIconType.FOG -> drawFog(w, h, color, strokeWidth)
            WeatherIconType.RAIN -> drawRain(w, h, color, stroke, strokeWidth)
            WeatherIconType.SNOW -> drawSnow(w, h, color, stroke, strokeWidth)
            WeatherIconType.THUNDERSTORM -> drawThunderstorm(w, h, color, stroke, strokeWidth)
        }
    }
}

private fun DrawScope.drawSunny(w: Float, h: Float, color: Color, strokeWidth: Float) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    drawCircle(color = color, radius = w * 0.22f, center = Offset(cx, cy), style = Stroke(strokeWidth))

    val angles = intArrayOf(0, 45, 90, 135, 180, 225, 270, 315)
    for (deg in angles) {
        val rad = Math.toRadians(deg.toDouble())
        val x1 = cx + cos(rad).toFloat() * w * 0.32f
        val y1 = cy + sin(rad).toFloat() * h * 0.32f
        val x2 = cx + cos(rad).toFloat() * w * 0.46f
        val y2 = cy + sin(rad).toFloat() * h * 0.46f
        drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawClearNight(w: Float, h: Float, color: Color) {
    val path = Path().apply {
        moveTo(w * 0.66f, h * 0.14f)
        cubicTo(w * 0.18f, h * 0.22f, w * 0.18f, h * 0.78f, w * 0.66f, h * 0.86f)
        cubicTo(w * 0.36f, h * 0.72f, w * 0.36f, h * 0.28f, w * 0.66f, h * 0.14f)
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

private fun DrawScope.drawCloud(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
    offsetY: Float = 0f,
    scale: Float = 1f
) {
    val cx = w * 0.5f
    val cy = h * 0.5f + offsetY
    val path = Path().apply {
        moveTo(cx - w * 0.24f * scale, cy + h * 0.18f * scale)
        lineTo(cx + w * 0.24f * scale, cy + h * 0.18f * scale)
        cubicTo(
            cx + w * 0.40f * scale, cy + h * 0.18f * scale,
            cx + w * 0.40f * scale, cy - h * 0.04f * scale,
            cx + w * 0.26f * scale, cy - h * 0.06f * scale
        )
        cubicTo(
            cx + w * 0.24f * scale, cy - h * 0.26f * scale,
            cx - w * 0.06f * scale, cy - h * 0.28f * scale,
            cx - w * 0.12f * scale, cy - h * 0.10f * scale
        )
        cubicTo(
            cx - w * 0.28f * scale, cy - h * 0.10f * scale,
            cx - w * 0.38f * scale, cy + h * 0.02f * scale,
            cx - w * 0.24f * scale, cy + h * 0.18f * scale
        )
        close()
    }
    drawPath(path = path, color = color, style = stroke)
}

private fun DrawScope.drawPartlyCloudyDay(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
    strokeWidth: Float
) {
    val sunCenter = Offset(w * 0.34f, h * 0.32f)
    drawCircle(color = color, radius = w * 0.15f, center = sunCenter, style = Stroke(strokeWidth))
    val rays = intArrayOf(135, 180, 225, 270)
    for (deg in rays) {
        val rad = Math.toRadians(deg.toDouble())
        val x1 = sunCenter.x + cos(rad).toFloat() * w * 0.20f
        val y1 = sunCenter.y + sin(rad).toFloat() * h * 0.20f
        val x2 = sunCenter.x + cos(rad).toFloat() * w * 0.29f
        val y2 = sunCenter.y + sin(rad).toFloat() * h * 0.29f
        drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
    drawCloud(w, h, color, stroke, offsetY = h * 0.08f, scale = 0.85f)
}

private fun DrawScope.drawPartlyCloudyNight(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke
) {
    val moonPath = Path().apply {
        moveTo(w * 0.46f, h * 0.14f)
        cubicTo(w * 0.14f, h * 0.18f, w * 0.14f, h * 0.52f, w * 0.44f, h * 0.56f)
        cubicTo(w * 0.26f, h * 0.48f, w * 0.26f, h * 0.22f, w * 0.46f, h * 0.14f)
        close()
    }
    drawPath(path = moonPath, color = color, style = Fill)
    drawCloud(w, h, color, stroke, offsetY = h * 0.08f, scale = 0.85f)
}

private fun DrawScope.drawFog(w: Float, h: Float, color: Color, strokeWidth: Float) {
    drawLine(color = color, start = Offset(w * 0.20f, h * 0.34f), end = Offset(w * 0.80f, h * 0.34f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w * 0.14f, h * 0.50f), end = Offset(w * 0.86f, h * 0.50f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w * 0.24f, h * 0.66f), end = Offset(w * 0.76f, h * 0.66f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

private fun DrawScope.drawRain(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
    strokeWidth: Float
) {
    drawCloud(w, h, color, stroke, offsetY = -h * 0.10f, scale = 0.82f)
    val rainWidth = strokeWidth * 0.85f
    drawLine(color = color, start = Offset(w * 0.35f, h * 0.68f), end = Offset(w * 0.29f, h * 0.86f), strokeWidth = rainWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w * 0.51f, h * 0.68f), end = Offset(w * 0.45f, h * 0.86f), strokeWidth = rainWidth, cap = StrokeCap.Round)
    drawLine(color = color, start = Offset(w * 0.67f, h * 0.68f), end = Offset(w * 0.61f, h * 0.86f), strokeWidth = rainWidth, cap = StrokeCap.Round)
}

private fun DrawScope.drawSnow(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
    strokeWidth: Float
) {
    drawCloud(w, h, color, stroke, offsetY = -h * 0.10f, scale = 0.82f)
    val dotRadius = strokeWidth * 0.75f
    drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.34f, h * 0.78f), style = Fill)
    drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.51f, h * 0.84f), style = Fill)
    drawCircle(color = color, radius = dotRadius, center = Offset(w * 0.68f, h * 0.78f), style = Fill)
}

private fun DrawScope.drawThunderstorm(
    w: Float,
    h: Float,
    color: Color,
    stroke: Stroke,
    strokeWidth: Float
) {
    drawCloud(w, h, color, stroke, offsetY = -h * 0.12f, scale = 0.82f)
    val lightning = Path().apply {
        moveTo(w * 0.53f, h * 0.58f)
        lineTo(w * 0.42f, h * 0.74f)
        lineTo(w * 0.54f, h * 0.74f)
        lineTo(w * 0.40f, h * 0.94f)
    }
    drawPath(path = lightning, color = color, style = Stroke(strokeWidth * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Miter))
}
