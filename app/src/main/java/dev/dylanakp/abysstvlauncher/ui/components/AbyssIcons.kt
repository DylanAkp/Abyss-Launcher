package dev.dylanakp.abysstvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class AbyssIconType {
    PLAY,
    PAUSE,
    SEEK_BACK_10,
    SEEK_FORWARD_10,
    SKIP_NEXT,
    AUDIO_SPEAKER,
    SUBTITLES_CC,
    TV_CHANNELS,
    CLOSE,
    CHECK,
    RESTART,
    EXTERNAL_LINK,
    SEARCH,
    SETTINGS,
    STAR_FILLED,
    STAR_OUTLINE,
    PIN,
    UNPIN,
    EYE,
    EYE_OFF,
    ARROW_LEFT,
    ARROW_RIGHT,
    INFO,
    WIFI,
    BLUETOOTH,
    DISPLAY,
    APPS_GRID,
    HOME,
    SERVER,
    GLOBE,
    REMOTE,
    DOWNLOAD,
    SPARKLE,
    COMPASS
}

val CompassExploreIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Filled.Explore",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 10.9f)
            curveToRelative(-0.61f, 0f, -1.1f, 0.49f, -1.1f, 1.1f)
            reflectiveCurveToRelative(0.49f, 1.1f, 1.1f, 1.1f)
            curveToRelative(0.61f, 0f, 1.1f, -0.49f, 1.1f, -1.1f)
            reflectiveCurveToRelative(-0.49f, -1.1f, -1.1f, -1.1f)
            close()
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(14.19f, 14.19f)
            lineTo(6f, 18f)
            lineToRelative(3.81f, -8.19f)
            lineTo(18f, 6f)
            lineToRelative(-3.81f, 8.19f)
            close()
        }
    }.build()
}

@Composable
fun AbyssIcon(
    type: AbyssIconType,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp
) {
    if (type == AbyssIconType.SETTINGS) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = color,
            modifier = modifier.size(size)
        )
        return
    }
    if (type == AbyssIconType.COMPASS) {
        Icon(
            imageVector = CompassExploreIcon,
            contentDescription = null,
            tint = color,
            modifier = modifier.size(size)
        )
        return
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = (w * 0.115f).coerceAtLeast(1.5f)
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (type) {
            AbyssIconType.PLAY -> drawPlay(w, h, color)
            AbyssIconType.PAUSE -> drawPause(w, h, color)
            AbyssIconType.SEEK_BACK_10 -> drawSeekBack(w, h, color, stroke)
            AbyssIconType.SEEK_FORWARD_10 -> drawSeekForward(w, h, color, stroke)
            AbyssIconType.SKIP_NEXT -> drawSkipNext(w, h, color, strokeWidth)
            AbyssIconType.AUDIO_SPEAKER -> drawAudioSpeaker(w, h, color, stroke)
            AbyssIconType.SUBTITLES_CC -> drawSubtitlesCc(w, h, color, stroke)
            AbyssIconType.TV_CHANNELS -> drawTvChannels(w, h, color, stroke)
            AbyssIconType.CLOSE -> drawClose(w, h, color, stroke)
            AbyssIconType.CHECK -> drawCheck(w, h, color, stroke)
            AbyssIconType.RESTART -> drawRestart(w, h, color, stroke)
            AbyssIconType.EXTERNAL_LINK -> drawExternalLink(w, h, color, stroke)
            AbyssIconType.SEARCH -> drawSearch(w, h, color, stroke)
            AbyssIconType.SETTINGS -> drawSettings(w, h, color, stroke)
            AbyssIconType.STAR_FILLED -> drawStar(w, h, color, filled = true, stroke = stroke)
            AbyssIconType.STAR_OUTLINE -> drawStar(w, h, color, filled = false, stroke = stroke)
            AbyssIconType.PIN -> drawPin(w, h, color, stroke, crossed = false)
            AbyssIconType.UNPIN -> drawPin(w, h, color, stroke, crossed = true)
            AbyssIconType.EYE -> drawEye(w, h, color, stroke, crossed = false)
            AbyssIconType.EYE_OFF -> drawEye(w, h, color, stroke, crossed = true)
            AbyssIconType.ARROW_LEFT -> drawArrowHorizontal(w, h, color, stroke, left = true)
            AbyssIconType.ARROW_RIGHT -> drawArrowHorizontal(w, h, color, stroke, left = false)
            AbyssIconType.INFO -> drawInfo(w, h, color, stroke)
            AbyssIconType.WIFI -> drawWifi(w, h, color, stroke)
            AbyssIconType.BLUETOOTH -> drawBluetooth(w, h, color, stroke)
            AbyssIconType.DISPLAY -> drawDisplay(w, h, color, stroke)
            AbyssIconType.APPS_GRID -> drawAppsGrid(w, h, color, stroke)
            AbyssIconType.HOME -> drawHome(w, h, color, stroke)
            AbyssIconType.SERVER -> drawServer(w, h, color, stroke)
            AbyssIconType.GLOBE -> drawGlobe(w, h, color, stroke)
            AbyssIconType.REMOTE -> drawRemote(w, h, color, stroke)
            AbyssIconType.DOWNLOAD -> drawDownload(w, h, color, stroke)
            AbyssIconType.SPARKLE -> drawSparkle(w, h, color)
        }
    }
}

private fun DrawScope.drawPlay(w: Float, h: Float, color: Color) {
    val path = Path().apply {
        moveTo(w * 0.24f, h * 0.16f)
        lineTo(w * 0.84f, h * 0.50f)
        lineTo(w * 0.24f, h * 0.84f)
        close()
    }
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawPause(w: Float, h: Float, color: Color) {
    val barW = w * 0.22f
    val barH = h * 0.68f
    val top = h * 0.16f
    val radius = CornerRadius(barW * 0.35f, barW * 0.35f)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.20f, top),
        size = Size(barW, barH),
        cornerRadius = radius
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.58f, top),
        size = Size(barW, barH),
        cornerRadius = radius
    )
}

private fun DrawScope.drawSeekBack(w: Float, h: Float, color: Color, stroke: Stroke) {
    // Double chevron pointing left (minimalist rewind)
    val path = Path().apply {
        moveTo(w * 0.52f, h * 0.22f)
        lineTo(w * 0.20f, h * 0.50f)
        lineTo(w * 0.52f, h * 0.78f)

        moveTo(w * 0.84f, h * 0.22f)
        lineTo(w * 0.52f, h * 0.50f)
        lineTo(w * 0.84f, h * 0.78f)
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawSeekForward(w: Float, h: Float, color: Color, stroke: Stroke) {
    // Double chevron pointing right (minimalist fast-forward)
    val path = Path().apply {
        moveTo(w * 0.16f, h * 0.22f)
        lineTo(w * 0.48f, h * 0.50f)
        lineTo(w * 0.16f, h * 0.78f)

        moveTo(w * 0.48f, h * 0.22f)
        lineTo(w * 0.80f, h * 0.50f)
        lineTo(w * 0.48f, h * 0.78f)
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawSkipNext(w: Float, h: Float, color: Color, strokeWidth: Float) {
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.20f)
        lineTo(w * 0.66f, h * 0.50f)
        lineTo(w * 0.18f, h * 0.80f)
        close()
    }
    drawPath(path, color = color, style = Fill)
    drawLine(
        color = color,
        start = Offset(w * 0.78f, h * 0.22f),
        end = Offset(w * 0.78f, h * 0.78f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawAudioSpeaker(w: Float, h: Float, color: Color, stroke: Stroke) {
    val speaker = Path().apply {
        moveTo(w * 0.14f, h * 0.38f)
        lineTo(w * 0.32f, h * 0.38f)
        lineTo(w * 0.54f, h * 0.18f)
        lineTo(w * 0.54f, h * 0.82f)
        lineTo(w * 0.32f, h * 0.62f)
        lineTo(w * 0.14f, h * 0.62f)
        close()
    }
    drawPath(speaker, color = color, style = stroke)

    // Sound waves
    drawArc(
        color = color,
        startAngle = -45f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(w * 0.48f, h * 0.30f),
        size = Size(w * 0.24f, h * 0.40f),
        style = stroke
    )
    drawArc(
        color = color,
        startAngle = -50f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(w * 0.46f, h * 0.16f),
        size = Size(w * 0.42f, h * 0.68f),
        style = stroke
    )
}

private fun DrawScope.drawSubtitlesCc(w: Float, h: Float, color: Color, stroke: Stroke) {
    // Rounded rectangle frame
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.10f, h * 0.18f),
        size = Size(w * 0.80f, h * 0.64f),
        cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
        style = stroke
    )
    // Two minimalist caption lines inside
    drawLine(
        color = color,
        start = Offset(w * 0.26f, h * 0.42f),
        end = Offset(w * 0.52f, h * 0.42f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.64f, h * 0.42f),
        end = Offset(w * 0.74f, h * 0.42f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.26f, h * 0.60f),
        end = Offset(w * 0.40f, h * 0.60f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.52f, h * 0.60f),
        end = Offset(w * 0.74f, h * 0.60f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawTvChannels(w: Float, h: Float, color: Color, stroke: Stroke) {
    val ys = listOf(h * 0.26f, h * 0.50f, h * 0.74f)
    ys.forEach { y ->
        drawCircle(
            color = color,
            radius = stroke.width * 0.65f,
            center = Offset(w * 0.20f, y)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.36f, y),
            end = Offset(w * 0.84f, y),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawClose(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawLine(
        color = color,
        start = Offset(w * 0.22f, h * 0.22f),
        end = Offset(w * 0.78f, h * 0.78f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.78f, h * 0.22f),
        end = Offset(w * 0.22f, h * 0.78f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawCheck(w: Float, h: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.52f)
        lineTo(w * 0.42f, h * 0.76f)
        lineTo(w * 0.84f, h * 0.24f)
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawRestart(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = -60f,
        sweepAngle = 285f,
        useCenter = false,
        topLeft = Offset(w * 0.16f, h * 0.16f),
        size = Size(w * 0.68f, h * 0.68f),
        style = stroke
    )
    val arrowHead = Path().apply {
        moveTo(w * 0.48f, h * 0.08f)
        lineTo(w * 0.66f, h * 0.22f)
        lineTo(w * 0.46f, h * 0.32f)
    }
    drawPath(arrowHead, color = color, style = stroke)
}

private fun DrawScope.drawExternalLink(w: Float, h: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(w * 0.45f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.22f)
        lineTo(w * 0.78f, h * 0.55f)

        moveTo(w * 0.76f, h * 0.24f)
        lineTo(w * 0.22f, h * 0.78f)
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawSearch(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawCircle(
        color = color,
        radius = w * 0.26f,
        center = Offset(w * 0.42f, h * 0.42f),
        style = stroke
    )
    drawLine(
        color = color,
        start = Offset(w * 0.61f, h * 0.61f),
        end = Offset(w * 0.84f, h * 0.84f),
        strokeWidth = stroke.width * 1.15f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawSettings(w: Float, h: Float, color: Color, stroke: Stroke) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val outerR = w * 0.38f
    val innerR = w * 0.26f
    val holeR = w * 0.12f

    // 6 mechanical gear teeth
    for (i in 0 until 6) {
        val angle = i * (Math.PI / 3.0)
        val cosA = cos(angle).toFloat()
        val sinA = sin(angle).toFloat()
        drawLine(
            color = color,
            start = Offset(cx + innerR * cosA, cy + innerR * sinA),
            end = Offset(cx + outerR * cosA, cy + outerR * sinA),
            strokeWidth = stroke.width * 1.6f,
            cap = StrokeCap.Round
        )
    }
    // Main ring
    drawCircle(
        color = color,
        radius = innerR,
        center = Offset(cx, cy),
        style = stroke
    )
    // Center axle hole
    drawCircle(
        color = color,
        radius = holeR,
        center = Offset(cx, cy),
        style = stroke
    )
}

private fun DrawScope.drawSparkle(w: Float, h: Float, color: Color) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val path = Path().apply {
        moveTo(cx, h * 0.12f)
        quadraticBezierTo(cx, cy, w * 0.88f, cy)
        quadraticBezierTo(cx, cy, cx, h * 0.88f)
        quadraticBezierTo(cx, cy, w * 0.12f, cy)
        quadraticBezierTo(cx, cy, cx, h * 0.12f)
        close()
    }
    drawPath(path, color = color, style = Fill)
}

private fun DrawScope.drawStar(w: Float, h: Float, color: Color, filled: Boolean, stroke: Stroke) {
    val cx = w * 0.5f
    val cy = h * 0.5f
    val outerR = w * 0.42f
    val innerR = w * 0.18f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outerR else innerR
        val angle = Math.toRadians((i * 36.0) - 90.0)
        val x = cx + (r * cos(angle)).toFloat()
        val y = cy + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = if (filled) Fill else stroke)
}

private fun DrawScope.drawPin(w: Float, h: Float, color: Color, stroke: Stroke, crossed: Boolean) {
    val path = Path().apply {
        moveTo(w * 0.30f, h * 0.18f)
        lineTo(w * 0.70f, h * 0.18f)
        lineTo(w * 0.64f, h * 0.48f)
        lineTo(w * 0.76f, h * 0.60f)
        lineTo(w * 0.24f, h * 0.60f)
        lineTo(w * 0.36f, h * 0.48f)
        close()
    }
    drawPath(path, color = color, style = stroke)
    drawLine(color, Offset(w * 0.50f, h * 0.60f), Offset(w * 0.50f, h * 0.88f), stroke.width, StrokeCap.Round)
    if (crossed) {
        drawLine(color, Offset(w * 0.16f, h * 0.16f), Offset(w * 0.84f, h * 0.84f), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawEye(w: Float, h: Float, color: Color, stroke: Stroke, crossed: Boolean) {
    val path = Path().apply {
        moveTo(w * 0.10f, h * 0.50f)
        quadraticTo(w * 0.50f, h * 0.14f, w * 0.90f, h * 0.50f)
        quadraticTo(w * 0.50f, h * 0.86f, w * 0.10f, h * 0.50f)
    }
    drawPath(path, color = color, style = stroke)
    drawCircle(color = color, radius = w * 0.12f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
    if (crossed) {
        drawLine(color, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.82f, h * 0.82f), stroke.width, StrokeCap.Round)
    }
}

private fun DrawScope.drawArrowHorizontal(w: Float, h: Float, color: Color, stroke: Stroke, left: Boolean) {
    drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), stroke.width, StrokeCap.Round)
    val head = Path().apply {
        if (left) {
            moveTo(w * 0.44f, h * 0.24f)
            lineTo(w * 0.18f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.76f)
        } else {
            moveTo(w * 0.56f, h * 0.24f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w * 0.56f, h * 0.76f)
        }
    }
    drawPath(head, color = color, style = stroke)
}

private fun DrawScope.drawInfo(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
    drawLine(color, Offset(w * 0.50f, h * 0.46f), Offset(w * 0.50f, h * 0.70f), stroke.width, StrokeCap.Round)
    drawCircle(color, radius = stroke.width * 0.65f, center = Offset(w * 0.50f, h * 0.31f))
}

private fun DrawScope.drawWifi(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawArc(
        color = color,
        startAngle = 215f,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = Offset(w * 0.08f, h * 0.20f),
        size = Size(w * 0.84f, h * 0.84f),
        style = stroke
    )
    drawArc(
        color = color,
        startAngle = 220f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(w * 0.24f, h * 0.40f),
        size = Size(w * 0.52f, h * 0.52f),
        style = stroke
    )
    drawCircle(
        color = color,
        radius = stroke.width * 0.75f,
        center = Offset(w * 0.50f, h * 0.78f)
    )
}

private fun DrawScope.drawBluetooth(w: Float, h: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(w * 0.28f, h * 0.30f)
        lineTo(w * 0.72f, h * 0.70f)
        lineTo(w * 0.50f, h * 0.88f)
        lineTo(w * 0.50f, h * 0.12f)
        lineTo(w * 0.72f, h * 0.30f)
        lineTo(w * 0.28f, h * 0.70f)
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawDisplay(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.12f, h * 0.16f),
        size = Size(w * 0.76f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = stroke
    )
    drawLine(color, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.50f, h * 0.84f), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(w * 0.32f, h * 0.84f), Offset(w * 0.68f, h * 0.84f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawAppsGrid(w: Float, h: Float, color: Color, stroke: Stroke) {
    val boxSize = Size(w * 0.32f, h * 0.32f)
    val cr = CornerRadius(w * 0.06f, w * 0.06f)
    drawRoundRect(color, Offset(w * 0.14f, h * 0.14f), boxSize, cr, style = stroke)
    drawRoundRect(color, Offset(w * 0.54f, h * 0.14f), boxSize, cr, style = stroke)
    drawRoundRect(color, Offset(w * 0.14f, h * 0.54f), boxSize, cr, style = stroke)
    drawRoundRect(color, Offset(w * 0.54f, h * 0.54f), boxSize, cr, style = stroke)
}

private fun DrawScope.drawHome(w: Float, h: Float, color: Color, stroke: Stroke) {
    val path = Path().apply {
        moveTo(w * 0.16f, h * 0.46f)
        lineTo(w * 0.50f, h * 0.18f)
        lineTo(w * 0.84f, h * 0.46f)
        lineTo(w * 0.84f, h * 0.82f)
        lineTo(w * 0.58f, h * 0.82f)
        lineTo(w * 0.58f, h * 0.56f)
        lineTo(w * 0.42f, h * 0.56f)
        lineTo(w * 0.42f, h * 0.82f)
        lineTo(w * 0.16f, h * 0.82f)
        close()
    }
    drawPath(path, color = color, style = stroke)
}

private fun DrawScope.drawServer(w: Float, h: Float, color: Color, stroke: Stroke) {
    val cr = CornerRadius(w * 0.08f, w * 0.08f)
    drawRoundRect(color, Offset(w * 0.14f, h * 0.18f), Size(w * 0.72f, h * 0.26f), cr, style = stroke)
    drawRoundRect(color, Offset(w * 0.14f, h * 0.56f), Size(w * 0.72f, h * 0.26f), cr, style = stroke)
    drawCircle(color, radius = stroke.width * 0.6f, center = Offset(w * 0.30f, h * 0.31f))
    drawCircle(color, radius = stroke.width * 0.6f, center = Offset(w * 0.30f, h * 0.69f))
}

private fun DrawScope.drawGlobe(w: Float, h: Float, color: Color, stroke: Stroke) {
    drawCircle(color, radius = w * 0.38f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
    drawLine(color, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.88f, h * 0.50f), stroke.width, StrokeCap.Round)
    drawOval(
        color = color,
        topLeft = Offset(w * 0.32f, h * 0.12f),
        size = Size(w * 0.36f, h * 0.76f),
        style = stroke
    )
}

private fun DrawScope.drawRemote(w: Float, h: Float, color: Color, stroke: Stroke) {
    val cr = CornerRadius(w * 0.12f, w * 0.12f)
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.28f, h * 0.10f),
        size = Size(w * 0.44f, h * 0.80f),
        cornerRadius = cr,
        style = stroke
    )
    drawCircle(
        color = color,
        radius = stroke.width * 0.55f,
        center = Offset(w * 0.50f, h * 0.22f)
    )
    drawCircle(
        color = color,
        radius = w * 0.10f,
        center = Offset(w * 0.50f, h * 0.42f),
        style = stroke
    )
    val dotOffset = w * 0.08f
    drawLine(
        color = color,
        start = Offset(w * 0.50f - dotOffset, h * 0.64f),
        end = Offset(w * 0.50f + dotOffset, h * 0.64f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.50f - dotOffset, h * 0.76f),
        end = Offset(w * 0.50f + dotOffset, h * 0.76f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawDownload(w: Float, h: Float, color: Color, stroke: Stroke) {
    // Arrow pointing down
    drawLine(color, Offset(w * 0.50f, h * 0.15f), Offset(w * 0.50f, h * 0.62f), stroke.width, StrokeCap.Round)
    val chevronPath = Path().apply {
        moveTo(w * 0.28f, h * 0.44f)
        lineTo(w * 0.50f, h * 0.65f)
        lineTo(w * 0.72f, h * 0.44f)
    }
    drawPath(chevronPath, color = color, style = stroke)

    // Tray at bottom
    val trayPath = Path().apply {
        moveTo(w * 0.20f, h * 0.72f)
        lineTo(w * 0.20f, h * 0.85f)
        lineTo(w * 0.80f, h * 0.85f)
        lineTo(w * 0.80f, h * 0.72f)
    }
    drawPath(trayPath, color = color, style = stroke)
}
