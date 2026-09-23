package dev.mewdeko.mobile.feature.currency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.util.withSeparators
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats a signed amount with an explicit plus sign for gains. */
internal fun Long.signed(): String = if (this > 0) "+${withSeparators()}" else withSeparators()

/** Formats a 0 to 1 fraction as a percentage. */
internal fun Double.percent(digits: Int = 1): String = "%.${digits}f%%".format(this * 100)

/** Renders a cooldown in seconds the way the dashboard hints do. */
internal fun cooldownLabel(seconds: Int): String = when {
    seconds <= 0 -> "No cooldown"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${Math.round(seconds / 60.0)}m"
    seconds % 3600 == 0 -> "${seconds / 3600}h"
    else -> "%.1fh".format(seconds / 3600.0)
}

/**
 * Whole-number input for economy settings.
 *
 * Keeps its own text so an empty or partial entry is not immediately
 * overwritten, and only reports parsable values. Out-of-range values are
 * flagged but still reported, since the bot clamps them and echoes the
 * result back.
 */
@Composable
internal fun CurrencyNumberField(
    label: String,
    value: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    min: Long = 0,
    max: Long = Long.MAX_VALUE,
    enabled: Boolean = true,
    allowNegative: Boolean = false,
) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toLongOrNull() != value) text = value.toString()
    }
    val parsed = text.toLongOrNull()
    val invalid = parsed == null || parsed < min || parsed > max
    val support = when {
        invalid && max != Long.MAX_VALUE -> "Between $min and $max"
        invalid -> "At least $min"
        else -> hint
    }
    val focus = LocalFocusManager.current

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = if (allowNegative) {
                val negative = raw.trimStart().startsWith("-")
                val digits = raw.filter { it.isDigit() }.take(18)
                if (negative) "-$digits" else digits
            } else {
                raw.filter { it.isDigit() }.take(18)
            }
            text = filtered
            filtered.toLongOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        supportingText = support?.let { message -> { Text(message) } },
        isError = invalid,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Integer convenience over [CurrencyNumberField]. */
@Composable
internal fun CurrencyIntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    allowNegative: Boolean = false,
) {
    CurrencyNumberField(
        label = label,
        value = value.toLong(),
        onValueChange = { onValueChange(it.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()) },
        modifier = modifier,
        hint = hint,
        min = min.toLong(),
        max = if (max == Int.MAX_VALUE) Long.MAX_VALUE else max.toLong(),
        allowNegative = allowNegative,
    )
}

/** Decimal input for fractional settings such as the payout multiplier. */
@Composable
internal fun CurrencyDecimalField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    min: Double,
    max: Double,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var text by remember { mutableStateOf(value.plain()) }
    LaunchedEffect(value) {
        if (text.toDoubleOrNull() != value) text = value.plain()
    }
    val parsed = text.toDoubleOrNull()
    val invalid = parsed == null || parsed < min || parsed > max
    val support = if (invalid) "Between ${min.plain()} and ${max.plain()}" else hint
    val focus = LocalFocusManager.current

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val normalized = raw.replace(',', '.')
            val cleaned = buildString {
                var seenDot = false
                normalized.forEach { char ->
                    when {
                        char.isDigit() -> append(char)
                        char == '.' && !seenDot -> {
                            seenDot = true
                            append(char)
                        }
                    }
                }
            }.take(12)
            text = cleaned
            cleaned.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        supportingText = support?.let { message -> { Text(message) } },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun Double.plain(): String =
    if (this == Math.floor(this) && !isInfinite()) toLong().toString() else toString()

/** A stat tile with a secondary detail line, for the analytics overview. */
@Composable
internal fun EconomyStat(
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A horizontal bar filled to [fraction] of its width, used for flow buckets
 * and the wallet and bank split.
 */
@Composable
internal fun RatioBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** A coloured dot followed by a label, for chart legends. */
@Composable
internal fun LegendDot(label: String, color: Color) {
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

private val ChartDate: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).withZone(ZoneOffset.UTC)

/**
 * Money supply over time: the running total of daily net changes as a filled
 * line, with each day's own net drawn dashed on the same scale. Drawn on a
 * [Canvas] so it tints with the live theme, like the other in-app charts.
 */
@Composable
internal fun SupplyChart(points: List<SupplyPoint>, modifier: Modifier = Modifier) {
    val cumulativeColor = MaterialTheme.colorScheme.primary
    val dailyColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val daily = points.map { it.net }
    val cumulative = daily.runningReduce { acc, value -> acc + value }
    val high = maxOf(cumulative.maxOrNull() ?: 0L, daily.maxOrNull() ?: 0L, 0L)
    val low = minOf(cumulative.minOrNull() ?: 0L, daily.minOrNull() ?: 0L, 0L)
    val span = (high - low).coerceAtLeast(1L)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val zeroY = size.height - ((0L - low).toFloat() / span.toFloat()) * size.height
                repeat(4) { step ->
                    val y = size.height * step / 3f
                    drawLine(
                        color = gridColor.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                }
                drawLine(
                    color = gridColor,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f,
                )
                plotSeries(cumulative, low, span, zeroY, cumulativeColor, fill = true, dashed = false)
                plotSeries(daily, low, span, zeroY, dailyColor, fill = false, dashed = true)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = points.firstOrNull()?.let { ChartDate.format(it.date) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = points.lastOrNull()?.let { ChartDate.format(it.date) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("Cumulative change", cumulativeColor)
            LegendDot("Daily net", dailyColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "High ${high.signed()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Low ${low.signed()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "End ${(cumulative.lastOrNull() ?: 0L).signed()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DrawScope.plotSeries(
    values: List<Long>,
    low: Long,
    span: Long,
    zeroY: Float,
    color: Color,
    fill: Boolean,
    dashed: Boolean,
) {
    if (values.isEmpty()) return
    val stepX = if (values.size > 1) size.width / (values.size - 1).toFloat() else 0f

    fun pointAt(index: Int): Offset {
        val ratio = (values[index] - low).toFloat() / span.toFloat()
        val x = if (values.size > 1) index * stepX else size.width / 2f
        return Offset(x, size.height - ratio * size.height)
    }

    if (values.size < 3) {
        values.indices.forEach { index -> drawCircle(color = color, radius = 5f, center = pointAt(index)) }
        if (values.size < 2) return
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
            lineTo(pointAt(values.lastIndex).x, zeroY)
            lineTo(pointAt(0).x, zeroY)
            close()
        }
        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0.02f))),
        )
    }

    drawPath(
        path = line,
        color = color,
        style = Stroke(
            width = if (dashed) 2f else 3f,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null,
        ),
    )
}
