package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a dashed rounded outline behind the content.
 *
 * The stroke is inset by half its width so the dashes sit fully inside the
 * bounds instead of being clipped by a parent.
 */
fun Modifier.dashedBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dash: Dp = 4.dp,
    gap: Dp = 3.dp,
): Modifier = drawBehind {
    val stroke = width.toPx()
    val half = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((cornerRadius.toPx() - half).coerceAtLeast(0f)),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        ),
    )
}

/**
 * Makes a row tappable while holding Material's minimum touch target.
 *
 * Several rows here are a single line of text, which would otherwise present a
 * target well under the 48dp floor.
 */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this
    .heightIn(min = 48.dp)
    .clickable(role = Role.Button, onClick = onClick)
