package dev.mewdeko.mobile.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/** Material's emphasized decelerate curve, used for chart draw-ins. */
val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * A small smoothed trend line with a soft area fill and an end dot.
 *
 * Values are normalized between their own minimum and maximum, so the shape
 * shows direction rather than absolute scale; a flat series draws a middle
 * line. The line draws in from the left once, unless [animate] is false.
 * Fewer than two points draw nothing. The parent supplies the spoken text.
 */
@Composable
fun Sparkline(
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    var drawn by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (drawn || !animate) 1f else 0f) }
    LaunchedEffect(animate) {
        if (animate && progress.value < 1f) {
            progress.animateTo(1f, tween(600, easing = EmphasizedDecelerateEasing))
        } else {
            progress.snapTo(1f)
        }
        drawn = true
    }

    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        if (values.size < 2) return@Canvas
        val pad = 3.dp.toPx()
        val minValue = values.min()
        val range = (values.max() - minValue).toFloat()
        val usableW = size.width - pad * 2f
        val usableH = size.height - pad * 2f
        val points = values.mapIndexed { index, value ->
            val x = pad + usableW * index / (values.size - 1).toFloat()
            val y = if (range == 0f) {
                size.height / 2f
            } else {
                pad + usableH * (1f - (value - minValue) / range)
            }
            Offset(x, y)
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (index in 1 until points.size) {
                val prev = points[index - 1]
                val cur = points[index]
                val midX = (prev.x + cur.x) / 2f
                cubicTo(midX, prev.y, midX, cur.y, cur.x, cur.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        clipRect(right = size.width * progress.value) {
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)),
                ),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(color = color, radius = 3.dp.toPx(), center = points.last())
        }
    }
}

/**
 * A row of small bottom-aligned bars.
 *
 * Heights scale to the largest value with a two dp floor for any nonzero
 * value, so a quiet bucket is still visible. The [highlight] bar draws at full
 * strength and the rest at [dimAlpha].
 */
@Composable
fun Sparkbars(
    values: List<Number>,
    color: Color,
    highlight: Int?,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        if (values.isEmpty()) return@Canvas
        val numbers = values.map { it.toFloat() }
        val peak = numbers.max()
        val slot = size.width / numbers.size
        val barW = slot * 0.62f
        val radius = CornerRadius(2.dp.toPx())
        val floor = 2.dp.toPx()
        numbers.forEachIndexed { index, value ->
            if (peak <= 0f || value <= 0f) return@forEachIndexed
            val h = max(floor, size.height * value / peak)
            drawRoundRect(
                color = color,
                topLeft = Offset(slot * index + (slot - barW) / 2f, size.height - h),
                size = Size(barW, h),
                cornerRadius = radius,
                alpha = if (index == highlight) 1f else dimAlpha,
            )
        }
    }
}

/**
 * A circular progress ring.
 *
 * The fill springs from zero on first show and to each new value after that,
 * unless [animate] is false.
 */
@Composable
fun RingGauge(
    fraction: Float,
    color: Color,
    track: Color,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val target = fraction.coerceIn(0f, 1f)
    val diameter = size
    var shown by rememberSaveable { mutableStateOf(false) }
    val sweep = remember { Animatable(if (shown || !animate) target else 0f) }
    LaunchedEffect(target, animate) {
        if (animate) sweep.animateTo(target, spring(stiffness = 200f)) else sweep.snapTo(target)
        shown = true
    }

    Canvas(modifier = modifier.size(diameter).clearAndSetSemantics {}) {
        val width = stroke.toPx()
        val inset = width / 2f
        val arcSize = Size(this.size.width - width, this.size.height - width)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * sweep.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width, cap = StrokeCap.Round),
        )
    }
}

/**
 * A labelled horizontal bar for ranked lists: the name and value on one line,
 * then a rounded track filled to [fraction].
 *
 * The fill springs from zero on first show, unless [animate] is false.
 */
@Composable
fun RankedBar(
    name: String,
    value: String,
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val target = fraction.coerceIn(0f, 1f)
    var shown by rememberSaveable { mutableStateOf(false) }
    val fill = remember { Animatable(if (shown || !animate) target else 0f) }
    LaunchedEffect(target, animate) {
        if (animate) fill.animateTo(target, spring(stiffness = 200f)) else fill.snapTo(target)
        shown = true
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clearAndSetSemantics {},
        ) {
            val h = size.height
            val y = h / 2f
            drawLine(
                color = track,
                start = Offset(h / 2f, y),
                end = Offset(size.width - h / 2f, y),
                strokeWidth = h,
                cap = StrokeCap.Round,
            )
            if (fill.value > 0f) {
                drawLine(
                    color = color,
                    start = Offset(h / 2f, y),
                    end = Offset(h / 2f + (size.width - h) * fill.value, y),
                    strokeWidth = h,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
