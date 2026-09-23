package dev.mewdeko.mobile.feature.serverstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Series colours matching the dashboard charts. */
internal object StatsColors {
    val Voice = Color(0xFFB67FF0)
    val Members = Color(0xFF4ADE80)
    val Online = Color(0xFF43B581)
    val Idle = Color(0xFFFAA61A)
    val Dnd = Color(0xFFF04747)
    val Joins = Color(0xFF4ADE80)
    val Leaves = Color(0xFFF87171)
}

private val DayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

private val DayTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault())

/** Formats an instant as a short local date such as `Mar 12`. */
internal fun Instant.statsDay(): String = DayFormatter.format(this)

/** Formats an instant as a short local date and time such as `Mar 12 14:00`. */
internal fun Instant.statsDayTime(): String = DayTimeFormatter.format(this)

/** Formats seconds as a compact duration such as `3d 4h`, `5h 12m` or `42m`, like the dashboard. */
internal fun statsDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Formats a count with thousands separators. */
internal fun statsNumber(value: Long): String = "%,d".format(value)

/** Formats a count with thousands separators. */
internal fun statsNumber(value: Int): String = "%,d".format(value)

/** Formats a signed change such as `+12` or `-3`. */
internal fun statsSigned(value: Int): String = if (value > 0) "+${statsNumber(value)}" else statsNumber(value)

/** Formats a ranking value: a count for messages, a duration for voice and games. */
internal fun statsValue(kind: StatKind, value: Long): String =
    if (kind == StatKind.MESSAGES) statsNumber(value) else statsDuration(value)

/** The shared lookback window picker, shown above every windowed section. */
@Composable
internal fun LookbackPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = LookbackOption.describe(selected),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LookbackOption.all.forEach { option ->
                FilterChip(
                    selected = selected == option.days,
                    onClick = { onSelect(option.days) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}

/** A stat tile with an optional secondary line, since the shared tile has no sub text. */
@Composable
internal fun StatsTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = tint ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One row of a ranking: rank, optional avatar, name, optional detail, and value. */
@Composable
internal fun RankRow(
    rank: Int,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    avatarUrl: String? = null,
    showAvatar: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        if (showAvatar) {
            Avatar(url = avatarUrl, contentDescription = title, size = 32)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** A label and value line used in the lookup breakdowns. */
@Composable
internal fun ValueLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** A small heading inside a card. */
@Composable
internal fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = 6.dp),
    )
}

/** Inline spinner for a section that is loading. */
@Composable
internal fun InlineLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

/** Inline failure message for a section, with a retry. */
@Composable
internal fun InlineError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

/** One series on a [StatsLineChart]. */
internal data class ChartLine(
    val name: String,
    val values: List<Double>,
    val color: Color,
)

/**
 * A compact multi-series line chart drawn on a [Canvas], with a legend, the
 * peak value, and the first and last bucket labels.
 */
@Composable
internal fun StatsLineChart(
    lines: List<ChartLine>,
    startLabel: String?,
    endLabel: String?,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    beginAtZero: Boolean = true,
    valueFormat: (Double) -> String = { "%,.0f".format(it) },
) {
    val populated = lines.filter { it.values.isNotEmpty() }
    if (populated.isEmpty()) {
        EmptyState(emptyMessage, modifier = modifier)
        return
    }

    val all = populated.flatMap { it.values }
    val peak = all.maxOrNull() ?: 0.0
    val floor = if (beginAtZero) 0.0 else (all.minOrNull() ?: 0.0)
    val top = if (peak <= floor) floor + 1.0 else peak
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        ) {
            repeat(4) { step ->
                val y = size.height * step / 3f
                drawLine(
                    color = gridColor.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
            populated.forEachIndexed { index, line ->
                drawStatsSeries(line.values, floor, top, line.color, fill = index == 0 && populated.size == 1)
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = startLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = endLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            populated.forEach { line ->
                LegendSwatch(
                    label = "${line.name} ${valueFormat(line.values.last())}",
                    color = line.color,
                )
            }
            Text(
                text = "Peak ${valueFormat(peak)}",
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

private fun DrawScope.drawStatsSeries(
    values: List<Double>,
    floor: Double,
    top: Double,
    color: Color,
    fill: Boolean,
) {
    val range = (top - floor).takeIf { it > 0 } ?: 1.0

    fun pointAt(index: Int): Offset {
        val x = if (values.size == 1) size.width / 2f else index * size.width / (values.size - 1).toFloat()
        val ratio = ((values[index] - floor) / range).toFloat().coerceIn(0f, 1f)
        return Offset(x, size.height - ratio * size.height)
    }

    if (values.size == 1) {
        drawCircle(color = color, radius = 4f, center = pointAt(0))
        return
    }

    val line = Path().apply {
        val first = pointAt(0)
        moveTo(first.x, first.y)
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
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))),
        )
    }

    drawPath(path = line, color = color, style = Stroke(width = 2.5f))
}
