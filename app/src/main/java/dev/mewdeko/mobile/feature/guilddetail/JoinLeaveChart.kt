package dev.mewdeko.mobile.feature.guilddetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.DailyStat

/**
 * Overlaid join and leave series.
 *
 * Drawn on a [Canvas] rather than pulled from a chart library: the series are
 * short, the shape is fixed, and this keeps the colours bound to the live
 * Material scheme so the chart re-tints with the rest of the guild theme.
 */
@Composable
fun JoinLeaveChart(
    joins: List<DailyStat>,
    leaves: List<DailyStat>,
    modifier: Modifier = Modifier,
) {
    val joinColor = MaterialTheme.colorScheme.primary
    val leaveColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val peak = maxOf(
        joins.maxOfOrNull { it.count } ?: 0,
        leaves.maxOfOrNull { it.count } ?: 0,
        1,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                repeat(4) { step ->
                    val y = size.height * step / 3f
                    drawLine(
                        color = gridColor.copy(alpha = 0.4f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
                drawSeries(joins.map { it.count }, peak, joinColor, fill = true)
                drawSeries(leaves.map { it.count }, peak, leaveColor, fill = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendSwatch("Joins", joinColor)
            LegendSwatch("Leaves", leaveColor)
            Text(
                text = "Peak $peak",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendSwatch(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun DrawScope.drawSeries(values: List<Int>, peak: Int, color: Color, fill: Boolean) {
    if (values.size < 2) return
    val stepX = size.width / (values.size - 1).toFloat()

    fun pointAt(index: Int): Offset {
        val ratio = values[index].toFloat() / peak.toFloat()
        return Offset(index * stepX, size.height - (ratio * size.height))
    }

    val line = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (index in 1 until values.size) {
            val point = pointAt(index)
            lineTo(point.x, point.y)
        }
    }

    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(
                listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)),
            ),
        )
    }

    drawPath(path = line, color = color, style = Stroke(width = 2.5f))
}
