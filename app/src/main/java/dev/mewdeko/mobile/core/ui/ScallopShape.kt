package dev.mewdeko.mobile.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A rounded, lobed shape in the spirit of the Material 3 expressive shape set.
 *
 * The outline is built in polar form, `r(theta) = R * (1 - depth + depth * cos(lobes * theta))`,
 * sampled every two degrees and smoothed with quadratic segments between the
 * samples. Low [depth] gives a soft "cookie"; fewer [lobes] with more depth
 * gives a clover.
 *
 * material3 1.3 ships no `MaterialShapes`, so this is hand built.
 */
data class ScallopShape(val lobes: Int, val depth: Float) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val samples = 180
        val points = List(samples) { index ->
            val theta = (index * 2.0) * PI / 180.0 - PI / 2.0
            val r = radius * (1f - depth + depth * cos(lobes * (theta + PI / 2.0)).toFloat())
            Offset(cx + r * cos(theta).toFloat(), cy + r * sin(theta).toFloat())
        }
        val path = Path()
        val first = midpoint(points.last(), points.first())
        path.moveTo(first.x, first.y)
        points.forEachIndexed { index, point ->
            val next = points[(index + 1) % samples]
            val mid = midpoint(point, next)
            path.quadraticTo(point.x, point.y, mid.x, mid.y)
        }
        path.close()
        return Outline.Generic(path)
    }

    /** The point halfway between [a] and [b]. */
    private fun midpoint(a: Offset, b: Offset): Offset = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
}
