package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A minimal line chart for one numeric series over time.
 *
 * Drawn directly on a [Canvas] rather than pulled from a charting library,
 * since the series here are short and the shape is fixed; the stroke colour
 * stays bound to the live Material scheme.
 */
@Composable
fun MinecraftHistoryChart(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val peak = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(160.dp)) {
        repeat(4) { step ->
            val y = size.height * step / 3f
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
        }
        if (values.size < 2) return@Canvas

        val stepX = size.width / (values.size - 1).toFloat()
        val path = Path()
        val fill = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height * (1f - (value / peak).coerceIn(0f, 1f))
            if (index == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()

        drawPath(fill, color = color.copy(alpha = 0.12f))
        drawPath(path, color = color, style = Stroke(width = 4f))
    }
}
