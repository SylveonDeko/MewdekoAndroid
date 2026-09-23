package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.GraphStats
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.GuildCard
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Day label format for the chart axis, the callout and the footer. */
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/** Peak dates arrive as instants on a UTC day boundary, so they format in UTC. */
private val PeakFormat: DateTimeFormatter = DayFormat.withZone(ZoneOffset.UTC)

/**
 * Joins and leaves as one dated, diverging bar chart with totals, averages
 * and peaks.
 *
 * The card itself is not clickable so the chart can take taps and scrubs;
 * the header row opens activity stats.
 */
@Composable
fun MemberFlowCard(
    joinStats: GraphStats?,
    leaveStats: GraphStats?,
    flow: List<HomeSeries.FlowDay>,
    loading: Boolean,
    roles: HomeRoles,
    reduced: Boolean,
    onOpenDetails: () -> Unit,
) {
    val joined = joinStats?.summary?.total ?: flow.sumOf { it.joins }
    val left = leaveStats?.summary?.total ?: flow.sumOf { it.leaves }
    val net = joined - left
    val hasChart = flow.isNotEmpty() && joined + left > 0
    val stacked = LocalDensity.current.fontScale >= 1.3f
    val clamp = fontScaleClamp()

    val canvas = MaterialTheme.colorScheme.surfaceContainerLow
    GuildCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Open activity stats",
                        onClick = onOpenDetails,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Member flow",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = HomeSeries.periodLabel(flow),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }

            val stats: @Composable () -> Unit = {
                FlowStat(label = "Joined", swatch = roles.community.color) {
                    FlowValue(if (loading) null else joined.toLong(), roles.community.readableOn(canvas))
                }
                FlowStat(label = "Left", swatch = roles.safety.color) {
                    FlowValue(if (loading) null else left.toLong(), roles.safety.readableOn(canvas))
                }
                FlowStat(label = "Net", swatch = roles.entertainment.color) {
                    Text(
                        text = if (loading) "+123" else HomeSeries.signed(net),
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = roles.entertainment.readableOn(canvas),
                        modifier = Modifier.skeleton(loading),
                    )
                }
            }
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { stats() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) { stats() }
            }

            when {
                loading -> FlowSkeletonChart(height = HomeDimens.flowChartHeight * clamp)
                hasChart -> FlowChartArea(
                    flow = flow,
                    joined = joined,
                    left = left,
                    roles = roles,
                    reduced = reduced,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeDimens.flowChartHeight * clamp),
                )
                else -> Row(
                    modifier = Modifier.height(96.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "No joins or leaves in the last ${flow.size.takeIf { it > 0 } ?: 10} days",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (loading) {
                Text(
                    text = "Avg 0.0 joins · 0.0 leaves per day",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.skeleton(true),
                )
            } else if (hasChart) {
                FlowFooter(joinStats, leaveStats, flow)
            }
        }
    }
}

/** A labelled summary number with a color swatch matching its series. */
@Composable
fun FlowStat(label: String, swatch: Color, value: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp, 4.dp)
                    .background(swatch, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        value()
    }
}

/** The number slot of a [FlowStat], drawn in its series color. */
@Composable
private fun FlowValue(value: Long?, color: Color) {
    AnimatedCount(
        value = value,
        style = MaterialTheme.typography.headlineMedium,
        color = color,
        compact = false,
        placeholder = "1,234",
    )
}

/** Averages and peaks under the chart. */
@Composable
private fun FlowFooter(joinStats: GraphStats?, leaveStats: GraphStats?, flow: List<HomeSeries.FlowDay>) {
    val joinAvg = joinStats?.summary?.average ?: flow.map { it.joins }.average()
    val leaveAvg = leaveStats?.summary?.average ?: flow.map { it.leaves }.average()
    val peaks = buildList {
        joinStats?.summary?.takeIf { it.peakCount > 0 }?.let {
            add("Peak joins ${it.peakCount}${peakDate(it.peakDate)}")
        }
        leaveStats?.summary?.takeIf { it.peakCount > 0 }?.let {
            add("Peak leaves ${it.peakCount}${peakDate(it.peakDate)}")
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Avg ${"%.1f".format(joinAvg)} joins · ${"%.1f".format(leaveAvg)} leaves per day",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (peaks.isNotEmpty()) {
            Text(
                text = peaks.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** " on Mar 4" for a real peak date, empty for the epoch placeholder. */
private fun peakDate(instant: Instant): String =
    if (instant == Instant.EPOCH) "" else " on ${PeakFormat.format(instant)}"

/** Ten flat placeholder bars on the baseline while the series load. */
@Composable
private fun FlowSkeletonChart(height: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(12.dp)
                        .skeleton(true, MaterialTheme.shapes.extraSmall),
                )
            }
        }
    }
}

/** Precomputed layout of the flow chart for one size and data set. */
private class FlowGeometry(
    val count: Int,
    val plotLeft: Float,
    val plotTop: Float,
    val plotRight: Float,
    val plotBottom: Float,
    val baselineY: Float,
    val unit: Float,
    val slot: Float,
    val maxJoins: Int,
    val maxLeaves: Int,
    val labelX: Float,
    val yLabels: List<Pair<Float, TextLayoutResult>>,
    val xLabels: List<Pair<Float, TextLayoutResult>>,
    val xLabelTop: Float,
) {
    /** Horizontal centre of bar [index]. */
    fun cx(index: Int): Float = plotLeft + slot * (index + 0.5f)

    /** The bar under horizontal position [x]. */
    fun indexAt(x: Float): Int = ((x - plotLeft) / slot).toInt().coerceIn(0, count - 1)
}

/** Lays out plot, gutters and labels for [flow] in a [width] by [height] canvas. */
private fun flowGeometry(
    flow: List<HomeSeries.FlowDay>,
    width: Float,
    height: Float,
    density: Density,
    measure: (String) -> TextLayoutResult,
): FlowGeometry {
    val n = flow.size
    val maxJoins = flow.maxOf { it.joins }
    val maxLeaves = flow.maxOf { it.leaves }
    val upMax = max(maxJoins, 1)
    val downMax = max(maxLeaves, 1)

    val zero = measure("0")
    val labelH = zero.size.height.toFloat()
    val top = if (maxJoins > 0) measure(maxJoins.toString()) else null
    val bottom = if (maxLeaves > 0) measure(maxLeaves.toString()) else null
    val gutterPad = with(density) { 8.dp.toPx() }
    val widest = listOfNotNull(zero, top, bottom).maxOf { it.size.width }.toFloat()
    val plotLeft = 0f
    val plotRight = width - widest - gutterPad
    val plotTop = labelH / 2f
    val plotBottom = height - labelH - with(density) { 6.dp.toPx() }
    val unit = (plotBottom - plotTop) / (upMax + downMax)
    val baselineY = plotTop + upMax * unit
    val slot = (plotRight - plotLeft) / n

    val yLabels = buildList {
        add(baselineY to zero)
        if (top != null) {
            val y = baselineY - maxJoins * unit
            if (baselineY - y >= labelH) add(y to top)
        }
        if (bottom != null) {
            val y = baselineY + maxLeaves * unit
            if (y - baselineY >= labelH) add(y to bottom)
        }
    }

    val xLabels = mutableListOf<Pair<Float, TextLayoutResult>>()
    var lastRight = Float.NEGATIVE_INFINITY
    listOf(0, n / 3, 2 * n / 3, n - 1).distinct().sorted().forEach { index ->
        val layout = measure(DayFormat.format(flow[index].date))
        val w = layout.size.width.toFloat()
        val x = (plotLeft + slot * (index + 0.5f) - w / 2f)
            .coerceIn(plotLeft, max(plotLeft, plotRight - w))
        if (x >= lastRight + gutterPad / 2f) {
            xLabels += x to layout
            lastRight = x + w
        }
    }

    return FlowGeometry(
        count = n,
        plotLeft = plotLeft,
        plotTop = plotTop,
        plotRight = plotRight,
        plotBottom = plotBottom,
        baselineY = baselineY,
        unit = unit,
        slot = slot,
        maxJoins = maxJoins,
        maxLeaves = maxLeaves,
        labelX = plotRight + gutterPad,
        yLabels = yLabels,
        xLabels = xLabels,
        xLabelTop = height - labelH,
    )
}

/** The chart plus its selection callout. */
@Composable
private fun FlowChartArea(
    flow: List<HomeSeries.FlowDay>,
    joined: Int,
    left: Int,
    roles: HomeRoles,
    reduced: Boolean,
    modifier: Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    var selected by remember(flow.size) { mutableStateOf<Int?>(null) }
    val shownIndex = remember { IntArray(1) }
    selected?.let { shownIndex[0] = it.coerceIn(0, flow.lastIndex) }
    var calloutWidth by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = modifier) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val geometry = remember(flow, width, height, labelStyle, density) {
            flowGeometry(flow, width, height, density) { text ->
                textMeasurer.measure(text, labelStyle)
            }
        }
        MemberFlowChart(
            flow = flow,
            geometry = geometry,
            joinColor = roles.community.color,
            leaveColor = roles.safety.color,
            baseline = MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex30),
            grid = MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex10),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selected = selected,
            onSelect = { selected = it },
            reduced = reduced,
            description = "Member flow chart. $joined joined, $left left, net " +
                "${HomeSeries.signed(joined - left)} over ${flow.size} days.",
            modifier = Modifier.fillMaxSize(),
        )
        val boxWidth = constraints.maxWidth
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(HomeMotion.effects()),
            exit = fadeOut(HomeMotion.effects()),
            modifier = Modifier.offset {
                val x = (geometry.cx(shownIndex[0]) - calloutWidth / 2f).roundToInt()
                IntOffset(x.coerceIn(0, max(0, boxWidth - calloutWidth)), 0)
            },
        ) {
            FlowCallout(
                day = flow[shownIndex[0].coerceIn(0, flow.lastIndex)],
                modifier = Modifier.onSizeChanged { calloutWidth = it.width },
            )
        }
    }
}

/**
 * Diverging daily bars: joins rise above the baseline, leaves hang below it.
 *
 * Bars draw in the solid role colors, the zero axis in [baseline] and the
 * extreme guide lines in [grid], the dashboard's `10` primary grid tint.
 * A tap selects a day and a long press scrubs across days with haptic ticks;
 * neither captures vertical scrolling. Bars grow in once with a short
 * per-bar stagger unless motion is reduced.
 */
@Composable
private fun MemberFlowChart(
    flow: List<HomeSeries.FlowDay>,
    geometry: FlowGeometry,
    joinColor: Color,
    leaveColor: Color,
    baseline: Color,
    grid: Color,
    labelColor: Color,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    reduced: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val n = flow.size
    val totalMs = 600 + 30 * (n - 1)
    var grown by rememberSaveable { mutableStateOf(false) }
    val growth = remember { Animatable(if (grown || reduced) 1f else 0f) }
    LaunchedEffect(n) {
        if (growth.value < 1f) {
            growth.animateTo(1f, tween(totalMs, easing = LinearEasing))
        }
        grown = true
    }

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(selected) {
        if (selected != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentGeometry by rememberUpdatedState(geometry)

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(n) {
                detectTapGestures(onTap = { offset ->
                    val index = currentGeometry.indexAt(offset.x)
                    currentOnSelect(if (index == currentSelected) null else index)
                })
            }
            .pointerInput(n) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> currentOnSelect(currentGeometry.indexAt(offset.x)) },
                    onDrag = { change, _ ->
                        currentOnSelect(currentGeometry.indexAt(change.position.x))
                        change.consume()
                    },
                )
            },
    ) {
        val g = geometry
        val barW = g.slot * 0.56f
        val corner = 4.dp.toPx()
        val t = growth.value

        fun grow(index: Int): Float {
            if (t >= 1f) return 1f
            val local = ((t * totalMs - 30f * index) / 600f).coerceIn(0f, 1f)
            return HomeMotion.EmphasizedDecelerate.transform(local)
        }

        if (g.maxJoins > 0) {
            val y = g.baselineY - g.maxJoins * g.unit
            drawLine(
                color = grid,
                start = Offset(g.plotLeft, y),
                end = Offset(g.plotRight, y),
                strokeWidth = 0.5.dp.toPx(),
            )
        }
        if (g.maxLeaves > 0) {
            val y = g.baselineY + g.maxLeaves * g.unit
            drawLine(
                color = grid,
                start = Offset(g.plotLeft, y),
                end = Offset(g.plotRight, y),
                strokeWidth = 0.5.dp.toPx(),
            )
        }

        flow.forEachIndexed { index, day ->
            val cx = g.cx(index)
            val left = cx - barW / 2f
            val right = cx + barW / 2f
            val dim = selected != null && selected != index
            val scale = grow(index)
            if (day.joins > 0) {
                val h = day.joins * g.unit * scale
                val r = CornerRadius(min(corner, min(barW / 2f, h)))
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = g.baselineY - h,
                            right = right,
                            bottom = g.baselineY,
                            topLeftCornerRadius = r,
                            topRightCornerRadius = r,
                        ),
                    )
                }
                drawPath(path, joinColor, alpha = if (dim) 0.35f else 1f)
            }
            if (day.leaves > 0) {
                val h = day.leaves * g.unit * scale
                val r = CornerRadius(min(corner, min(barW / 2f, h)))
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = g.baselineY,
                            right = right,
                            bottom = g.baselineY + h,
                            bottomRightCornerRadius = r,
                            bottomLeftCornerRadius = r,
                        ),
                    )
                }
                drawPath(path, leaveColor, alpha = if (dim) 0.35f else 1f)
            }
        }

        drawLine(
            color = baseline,
            start = Offset(g.plotLeft, g.baselineY),
            end = Offset(g.plotRight, g.baselineY),
            strokeWidth = 1.dp.toPx(),
        )

        if (selected != null && selected in 0 until g.count) {
            val x = g.cx(selected)
            drawLine(
                color = labelColor.copy(alpha = 0.5f),
                start = Offset(x, g.plotTop),
                end = Offset(x, g.plotBottom),
                strokeWidth = 1.dp.toPx(),
            )
        }

        g.yLabels.forEach { (y, layout) ->
            drawText(
                textLayoutResult = layout,
                color = labelColor,
                topLeft = Offset(g.labelX, y - layout.size.height / 2f),
            )
        }
        g.xLabels.forEach { (x, layout) ->
            drawText(
                textLayoutResult = layout,
                color = labelColor,
                topLeft = Offset(x, g.xLabelTop),
            )
        }
    }
}

/**
 * The inverse-surface callout for the selected day.
 *
 * Drawn as a plain box rather than a Surface so it never intercepts the taps
 * meant for the chart underneath.
 */
@Composable
private fun FlowCallout(day: HomeSeries.FlowDay, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.inverseOnSurface) {
        Column(
            modifier = modifier
                .shadow(3.dp, shape)
                .background(MaterialTheme.colorScheme.inverseSurface, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            val style = MaterialTheme.typography.labelMedium.tabular()
            Text(
                text = DayFormat.format(day.date),
                style = style.copy(fontWeight = FontWeight.Bold),
            )
            Text(text = "+${day.joins} joined", style = style)
            Text(text = "-${day.leaves} left", style = style)
            Text(text = "net ${HomeSeries.signed(day.net)}", style = style)
        }
    }
}
