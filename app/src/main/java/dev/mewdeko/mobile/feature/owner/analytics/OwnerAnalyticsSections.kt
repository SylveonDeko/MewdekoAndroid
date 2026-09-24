@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package dev.mewdeko.mobile.feature.owner.analytics

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.theme.Rgb
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.guildWash
import dev.mewdeko.mobile.core.ui.isDarkScheme
import dev.mewdeko.mobile.core.ui.rememberShellRoles
import dev.mewdeko.mobile.core.ui.toneWash
import dev.mewdeko.mobile.core.ui.washed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val TAG = "OwnerAnalytics"

/** The selector id standing for "any" in the tab level selects. */
private const val AnyOption = "__any__"

/** The glyph for each tab, the closest Material match to the dashboard's Font Awesome icon. */
val OwnerAnalyticsSection.icon: ImageVector
    get() = when (this) {
        OwnerAnalyticsSection.Overview -> Icons.Default.Speed
        OwnerAnalyticsSection.Commands -> Icons.Default.Terminal
        OwnerAnalyticsSection.Events -> Icons.Default.Bolt
        OwnerAnalyticsSection.Latency -> Icons.Default.Timer
        OwnerAnalyticsSection.Servers -> Icons.Default.Dns
        OwnerAnalyticsSection.Guilds -> Icons.Default.Groups
        OwnerAnalyticsSection.Features -> Icons.Default.Extension
        OwnerAnalyticsSection.Ai -> Icons.Default.SmartToy
        OwnerAnalyticsSection.Music -> Icons.Default.MusicNote
        OwnerAnalyticsSection.Website -> Icons.Default.Language
        OwnerAnalyticsSection.Alerts -> Icons.Default.Notifications
        OwnerAnalyticsSection.Pipeline -> Icons.Default.AccountTree
    }

/** The tab row entries, one per [OwnerAnalyticsSection]. */
val OwnerAnalyticsTabs: List<SectionTab> =
    OwnerAnalyticsSection.entries.map { SectionTab(it.id, it.label, it.icon) }

/**
 * What every analytics widget reads: the view model that performs the
 * requests, the global filters, the refresh tick, and the metric registry.
 */
@Immutable
data class AnalyticsEnv(
    /** Performs every analytics request against the selected bot. */
    val vm: OwnerAnalyticsViewModel,
    /** The global filter bar. */
    val filters: AnalyticsFilters,
    /** The refresh tick; widgets reload when it changes. */
    val tick: Int,
    /** The shared metric registry. */
    val registry: List<MetricDescriptor>,
)

/** The analytics environment the screen provides to its widgets. */
val LocalAnalytics = compositionLocalOf<AnalyticsEnv> {
    error("OwnerAnalyticsScreen provides the analytics environment")
}

/**
 * The body of the visible tab. Only the active tab is composed, so only its
 * widgets load and poll.
 */
@Composable
fun OwnerAnalyticsSectionBody(state: OwnerAnalyticsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state.section) {
            OwnerAnalyticsSection.Overview -> OverviewTab()
            OwnerAnalyticsSection.Commands -> CommandsTab()
            OwnerAnalyticsSection.Events -> EventsTab()
            OwnerAnalyticsSection.Latency -> LatencyTab()
            OwnerAnalyticsSection.Servers -> ServersTab()
            OwnerAnalyticsSection.Guilds -> GuildsTab()
            OwnerAnalyticsSection.Features -> FeaturesTab()
            OwnerAnalyticsSection.Ai -> AiTab()
            OwnerAnalyticsSection.Music -> MusicTab()
            OwnerAnalyticsSection.Website -> WebsiteTab()
            OwnerAnalyticsSection.Alerts -> AlertsTab()
            OwnerAnalyticsSection.Pipeline -> PipelineTab()
        }
    }
}

/** A widget's load: the last data, whether a load is running, and whether the last one failed. */
@Immutable
data class Loaded<T>(
    /** The data of the last successful load, or `null`. */
    val data: T?,
    /** Whether a load is in flight. */
    val loading: Boolean,
    /** Whether the last load failed. */
    val failed: Boolean,
)

/**
 * Runs [loader] whenever any of [keys] changes, keeping the previous data on
 * screen while the next load runs. A key change cancels the running load, so
 * an out of order response can never land, the same guarantee the
 * dashboard's per loader sequence counters give. Failures are logged and
 * reported through [Loaded.failed].
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, loader: suspend () -> T?): Loaded<T> {
    var result by remember { mutableStateOf(Loaded<T>(data = null, loading = true, failed = false)) }
    val latest by rememberUpdatedState(loader)
    LaunchedEffect(*keys) {
        result = result.copy(loading = true, failed = false)
        result = try {
            Loaded(latest(), loading = false, failed = false)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "analytics load failed: ${t.message}")
            Loaded(null, loading = false, failed = true)
        }
    }
    return result
}

/** The meaning of a pill or value color. */
enum class Tone {
    /** Healthy, the semantic green. */
    Ok,

    /** Worth a look, the semantic orange. */
    Warn,

    /** Broken, the semantic red. */
    Crit,

    /** Inactive or neutral, the muted ink. */
    Muted,
}

/**
 * The analytics colors, every one derived from the palette in scope: the
 * semantic tones from the shell roles and a categorical series scale that
 * starts at the palette primary and walks the hue wheel by the golden angle.
 */
@Immutable
data class AnalyticsPalette(
    /** The palette primary, for single series and bars. */
    val primary: Color,
    /** The semantic green. */
    val ok: Color,
    /** The semantic orange. */
    val warn: Color,
    /** The semantic red. */
    val crit: Color,
    /** The muted ink, also used for "Other" series. */
    val muted: Color,
    /** The info severity. */
    val info: Color,
    /** Chart grid lines and bar tracks, the primary at the `10` tint. */
    val grid: Color,
    /** Twelve categorical series colors. */
    val series: List<Color>,
) {
    /** The color of a named series; folded and unknown series are muted. */
    fun seriesColor(name: String, index: Int): Color =
        if (name.startsWith("Other") || name == "unknown") muted else series[index % series.size]

    /** The color of an alert severity. */
    fun severity(severity: String): Color = when (severity.lowercase()) {
        "critical", "crit" -> crit
        "warning", "warn" -> warn
        else -> info
    }

    /** The color of [tone]. */
    fun tone(tone: Tone): Color = when (tone) {
        Tone.Ok -> ok
        Tone.Warn -> warn
        Tone.Crit -> crit
        Tone.Muted -> muted
    }
}

/** The golden angle as a fraction of a turn, spacing categorical hues evenly. */
private const val GoldenTurn = 0.3819660112501051

/** Resolves [AnalyticsPalette] from the palette and scheme in scope. */
@Composable
fun rememberAnalyticsPalette(): AnalyticsPalette {
    val roles = rememberShellRoles()
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkScheme()
    return remember(roles, scheme.primary, dark) {
        val (hue, _, _) = Rgb.fromArgb(scheme.primary.toArgb()).hsl
        val series = List(12) { index ->
            if (index == 0) {
                scheme.primary
            } else {
                Rgb.fromHsl(
                    h = (hue + index * GoldenTurn) % 1.0,
                    s = if (dark) 0.66 else 0.6,
                    l = if (dark) 0.64 else 0.44,
                ).color
            }
        }
        AnalyticsPalette(
            primary = scheme.primary,
            ok = roles.positive,
            warn = roles.caution,
            crit = roles.negative,
            muted = roles.neutral,
            info = series[5],
            grid = scheme.primary.copy(alpha = DashAlpha.Hex10),
            series = series,
        )
    }
}

/** The pill tone of an alert severity. */
private fun severityTone(severity: String): Tone = when (severity) {
    "critical" -> Tone.Crit
    "warning" -> Tone.Warn
    else -> Tone.Muted
}

/** A state pill in an analytics tone. */
@Composable
fun TonePill(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    StatePill(text = text, tone = rememberAnalyticsPalette().tone(tone), modifier = modifier, icon = icon)
}

/** A pulsing placeholder bar shown while something loads with nothing to show yet. */
@Composable
fun Skeleton(modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val primary = MaterialTheme.colorScheme.primary
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { alpha = pulse }
            .background(primary.copy(alpha = DashAlpha.Hex20), RoundedCornerShape(6.dp)),
    )
}

/** A few skeleton rows for a list or table that is loading. */
@Composable
fun SkeletonRows(count: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) { Skeleton(Modifier.fillMaxWidth()) }
    }
}

/** Small muted text, the dashboard's empty and note line. */
@Composable
fun MutedText(
    text: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    center: Boolean = false,
    color: Color? = null,
) {
    Text(
        text = text,
        style = if (mono) MonospaceStyle else MaterialTheme.typography.labelMedium,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) modifier.fillMaxWidth().padding(vertical = 12.dp) else modifier,
    )
}

/** The small uppercase caption above a block. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.US),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** An analytics card: an optional title and note over the content, in the guild card dress. */
@Composable
fun AnCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(modifier = modifier) {
        if (title != null || note != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (note != null) MutedText(note)
            }
        }
        content()
    }
}

/** Lays [cards] out one per row on phones and two per row from 600dp. */
@Composable
fun CardColumns(vararg cards: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 600.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cards.toList().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card -> Box(modifier = Modifier.weight(1f)) { card() } }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Lays [tiles] out two per row on phones, three from 600dp and four from 840dp. */
@Composable
fun TileGrid(vararg tiles: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.toList().chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { tile -> Box(modifier = Modifier.weight(1f)) { tile() } }
                    repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The dress of a tile: the card wash over the low container with a hairline. */
@Composable
private fun TileSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = guildBorder(),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .washed(MaterialTheme.colorScheme.surfaceContainerLow, guildWash(), shape),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

/** Divides a range total down to a per minute or per second rate. */
enum class Per {
    /** Divided by the range length in minutes. */
    Minute,

    /** Divided by the range length in seconds. */
    Second,
}

/**
 * The dashboard's metric tile: a label, the aggregate over the range, a
 * sparkline of the same query, and a drill button that opens the breakdown
 * sheet. [per] divides by the nominal range length, as the dashboard does.
 */
@Composable
fun MetricTile(
    label: String,
    metric: String,
    agg: String = "sum",
    format: ValueFormat = ValueFormat.Whole,
    honours: Set<String> = HonoursBotShard,
    fixed: Map<String, String> = emptyMap(),
    per: Per? = null,
    spark: Boolean = true,
    critSpark: Boolean = false,
    drill: Boolean = true,
    unit: String = "",
    drillExtra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val env = LocalAnalytics.current
    val colors = rememberAnalyticsPalette()
    val filters = env.filters
    val value = rememberLoad(filters, env.tick, metric, agg, fixed, per, honours) {
        val raw = env.vm.aggregate(metric, agg, filters.queryParams(honours, fixed))?.value
        raw?.let { v ->
            when (per) {
                Per.Minute -> v / (filters.rangeSeconds() / 60.0)
                Per.Second -> v / filters.rangeSeconds()
                null -> v
            }
        }
    }
    val points = if (spark) {
        rememberLoad(filters, env.tick, metric, agg, fixed, honours) {
            env.vm.series(metric, agg, null, null, filters.queryParams(honours, fixed))
                ?.series?.firstOrNull()?.points?.map { it.value ?: 0.0 }
        }
    } else {
        null
    }
    var open by remember { mutableStateOf(false) }

    TileSurface {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (drill) {
                Icon(
                    Icons.Default.QueryStats,
                    contentDescription = "Open breakdown of $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { open = true }
                        .padding(5.dp),
                )
            }
        }
        if (value.loading && value.data == null) {
            Skeleton(Modifier.width(64.dp), height = 22.dp)
        } else {
            Text(
                text = fmt(value.data, format),
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (points != null) {
            SparkCanvas(
                values = points.data.orEmpty(),
                color = if (critSpark) colors.crit else colors.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
            )
        }
    }

    if (open) {
        DrillSheet(
            title = "$label · ${fmt(value.data, format)}",
            metric = metric,
            agg = agg,
            honours = honours,
            fixed = fixed,
            unit = unit,
            bytes = format == ValueFormat.Bytes,
            onDismiss = { open = false },
            extra = drillExtra,
        )
    }
}

/** The dashboard's plain stat tile: a label, a value in an optional tone, and a sub line. */
@Composable
fun StatBox(
    label: String,
    value: String,
    loading: Boolean = false,
    sub: String? = null,
    tone: Tone? = null,
) {
    val colors = rememberAnalyticsPalette()
    TileSurface {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (loading) {
            Skeleton(Modifier.width(64.dp), height = 22.dp)
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = tone?.let { colors.tone(it) } ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A tile sparkline: min to max normalized polyline with a soft fill. */
@Composable
fun SparkCanvas(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val low = values.min()
        val span = (values.max() - low).takeIf { it > 0 } ?: 1.0
        val points = values.mapIndexed { index, value ->
            Offset(
                x = size.width * index / (values.size - 1),
                y = (size.height - ((value - low) / span) * (size.height - 2) - 1).toFloat(),
            )
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            moveTo(0f, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.15f))
        drawPath(line, color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * The drill sheet: a chart of the metric, then a breakdown per label the
 * metric carries. Tapping a bar narrows everything to that value as a crumb;
 * tapping a crumb removes it. Crumbs reset when the sheet closes.
 */
@Composable
fun DrillSheet(
    title: String,
    metric: String?,
    onDismiss: () -> Unit,
    agg: String = "sum",
    honours: Set<String> = HonoursBotShard,
    fixed: Map<String, String> = emptyMap(),
    unit: String = "",
    bytes: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val env = LocalAnalytics.current
    var crumbs by remember { mutableStateOf(emptyMap<String, String>()) }
    val effective = fixed + crumbs

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (crumbs.isEmpty()) {
                if (metric != null) MutedText("Bar → narrow")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    crumbs.forEach { (label, value) ->
                        Surface(
                            onClick = { crumbs = crumbs - label },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex20),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex30)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("$label = $value", style = MonospaceStyle.copy(fontSize = 12.sp))
                                Icon(Icons.Default.Close, contentDescription = "Remove $label", modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            if (metric != null) {
                SeriesChart(
                    metric = metric,
                    agg = agg,
                    honours = honours,
                    fixed = effective,
                    unit = unit,
                    bytes = bytes,
                    fill = true,
                    noCompare = true,
                    height = 180.dp,
                )
                val blocks = rememberLoad(env.filters, env.tick, metric, effective, agg, honours) {
                    val labels = env.vm.metricLabels(metric).filter { it !in effective }
                    val base = env.filters.queryParams(honours, effective)
                    coroutineScope {
                        labels.map { label ->
                            async {
                                val rows = try {
                                    env.vm.breakdown(metric, label, agg, 12, base)
                                } catch (c: CancellationException) {
                                    throw c
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                                label to rows
                            }
                        }.awaitAll().filter { it.second.size > 1 }
                    }
                }
                val shown = blocks.data.orEmpty()
                when {
                    blocks.loading && shown.isEmpty() -> Skeleton(Modifier.fillMaxWidth())
                    shown.isEmpty() -> MutedText("No further labels")
                    else -> shown.forEach { (label, rows) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Overline(label)
                            BreakdownBars(rows = rows, top = 8, onSelect = { name -> crumbs = crumbs + (label to name) })
                        }
                    }
                }
            }

            extra?.invoke(this)
        }
    }
}

/**
 * Ranked bars: a mono name, a proportional bar with a two percent floor, and
 * a compact value. Tapping a row calls [onSelect] when given.
 */
@Composable
fun BreakdownBars(
    rows: List<BreakdownRow>,
    loading: Boolean = false,
    empty: String = "No data",
    top: Int = 12,
    color: Color? = null,
    format: (Double) -> String = { fmtCompact(it) },
    onSelect: ((String) -> Unit)? = null,
    selected: String? = null,
) {
    val colors = rememberAnalyticsPalette()
    val shown = remember(rows, top) { rows.sortedByDescending { it.value }.take(top) }
    when {
        loading && shown.isEmpty() -> SkeletonRows(3)
        shown.isEmpty() -> MutedText(empty)
        else -> {
            val peak = max(1.0, shown.maxOf { abs(it.value) })
            val bar = color ?: colors.primary
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.forEach { row ->
                    val fraction = max(0.02, abs(row.value) / peak).toFloat()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (row.name == selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex15)
                                } else {
                                    Color.Transparent
                                },
                            )
                            .then(if (onSelect != null) Modifier.clickable { onSelect(row.name) } else Modifier)
                            .padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = row.name,
                            style = MonospaceStyle.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.42f),
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.43f)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(colors.grid),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(bar, CircleShape),
                            )
                        }
                        Text(
                            text = format(row.value),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.weight(0.15f),
                        )
                    }
                }
            }
        }
    }
}

/** A breakdown of one label, loaded from the filters. */
@Composable
fun BreakdownPanel(
    metric: String,
    label: String,
    honours: Set<String> = HonoursBotShard,
    fixed: Map<String, String> = emptyMap(),
    agg: String = "sum",
    top: Int = 12,
    color: Color? = null,
    empty: String = "No data",
) {
    val env = LocalAnalytics.current
    val rows = rememberLoad(env.filters, env.tick, metric, label, honours, fixed, agg, top) {
        env.vm.breakdown(metric, label, agg, top, env.filters.queryParams(honours, fixed))
    }
    BreakdownBars(rows = rows.data.orEmpty(), loading = rows.loading, empty = empty, top = top, color = color)
}

/** How a chart draws its series. */
enum class ChartKind {
    /** Lines. */
    Line,

    /** Stacked filled areas. */
    Area,

    /** Bars, grouped or stacked. */
    Bar,
}

/** One drawn series. [dashed] marks the previous period overlay, which never stacks. */
@Immutable
data class ChartLine(
    val name: String,
    val color: Color,
    val values: List<Double?>,
    val dashed: Boolean = false,
)

/** A horizontal threshold line. */
@Immutable
data class ChartBandLine(val value: Double, val color: Color, val label: String)

/** Where the plot sits inside the canvas, written while drawing and read by touch handling. */
private class PlotArea {
    var left = 0f
    var right = 0f
    var count = 0
    var bars = false

    fun indexAt(x: Float): Int {
        if (count <= 1) return 0
        val width = (right - left).coerceAtLeast(1f)
        val index = if (bars) {
            floor((x - left) / (width / count)).toInt()
        } else {
            ((x - left) / width * (count - 1)).roundToInt()
        }
        return index.coerceIn(0, count - 1)
    }
}

/** A readable step for about four y axis intervals. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0 || raw.isNaN()) return 1.0
    val exponent = floor(log10(raw))
    val base = 10.0.pow(exponent)
    val fraction = raw / base
    val nice = when {
        fraction <= 1 -> 1.0
        fraction <= 2 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5 -> 5.0
        else -> 10.0
    }
    return nice * base
}

/** The y axis ticks for [lines], stacking solid series when [stacked]. */
private fun yTicks(lines: List<ChartLine>, count: Int, stacked: Boolean, beginAtZero: Boolean): List<Double> {
    val values = mutableListOf<Double>()
    if (stacked) {
        val solid = lines.filter { !it.dashed }
        for (i in 0 until count) {
            var positive = 0.0
            var negative = 0.0
            solid.forEach { line ->
                val v = line.values.getOrNull(i) ?: 0.0
                if (v >= 0) positive += v else negative += v
            }
            values += positive
            values += negative
        }
        lines.filter { it.dashed }.forEach { line -> values.addAll(line.values.filterNotNull()) }
    } else {
        lines.forEach { line -> values.addAll(line.values.filterNotNull()) }
    }
    var low = values.minOrNull() ?: 0.0
    var high = values.maxOrNull() ?: 1.0
    if (beginAtZero) {
        low = min(low, 0.0)
        high = max(high, 0.0)
    }
    if (high - low < 1e-9) high = low + 1.0
    val step = niceStep((high - low) / 4)
    val start = floor(low / step) * step
    val end = ceil(high / step) * step
    val ticks = mutableListOf<Double>()
    var tick = start
    while (tick <= end + step / 2 && ticks.size < 12) {
        ticks += tick
        tick += step
    }
    return ticks
}

/** Strokes the non null points of [values], joining across gaps. */
private fun DrawScope.strokeSeries(
    values: List<Double?>,
    color: Color,
    x: (Int) -> Float,
    y: (Double) -> Float,
    width: Float,
    effect: PathEffect? = null,
) {
    val path = Path()
    var started = false
    var single: Offset? = null
    values.forEachIndexed { index, value ->
        if (value == null) return@forEachIndexed
        if (!started) {
            path.moveTo(x(index), y(value))
            single = Offset(x(index), y(value))
            started = true
        } else {
            path.lineTo(x(index), y(value))
            single = null
        }
    }
    if (!started) return
    val lone = single
    if (lone != null) {
        drawCircle(color, radius = width * 1.5f, center = lone)
    } else {
        drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
    }
}

/**
 * The chart canvas every analytics chart draws with: grid and axis labels,
 * lines, stacked areas or bars, the dashed previous period, threshold bands,
 * and a touch readout. Tap or drag across the plot to read every series at a
 * bucket; tap the same bucket again to dismiss.
 */
@Composable
fun ChartCanvas(
    labels: List<String>,
    lines: List<ChartLine>,
    kind: ChartKind,
    height: Dp,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    fillSingle: Boolean = false,
    bands: List<ChartBandLine> = emptyList(),
    dimmed: Boolean = false,
    beginAtZero: Boolean = true,
    hidden: Set<Int> = emptySet(),
    yFormat: (Double) -> String = { fmtCompact(it) },
    tipFormat: (Double?) -> String = { fmtCompact(it) },
) {
    val colors = rememberAnalyticsPalette()
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val guide = MaterialTheme.colorScheme.onSurfaceVariant
    val plot = remember { PlotArea() }
    val count = labels.size
    var selected by remember(count) { mutableStateOf<Int?>(null) }
    val visible = remember(lines, hidden) { lines.filterIndexed { index, _ -> index !in hidden } }
    val ticks = remember(visible, count, stacked, beginAtZero) { yTicks(visible, count, stacked, beginAtZero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (dimmed) 0.35f else 1f }
                .pointerInput(count) {
                    detectTapGestures { offset ->
                        val index = plot.indexAt(offset.x)
                        selected = if (selected == index) null else index
                    }
                }
                .pointerInput(count) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> selected = plot.indexAt(offset.x) },
                        onHorizontalDrag = { change, _ -> selected = plot.indexAt(change.position.x) },
                    )
                },
        ) {
            if (count == 0 || ticks.size < 2) return@Canvas
            val tickLayouts = ticks.map { measurer.measure(yFormat(it), axisStyle) }
            val sample = measurer.measure("00:00", axisStyle)
            val left = (tickLayouts.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - sample.size.height - 6.dp.toPx()
            plot.left = left
            plot.right = right
            plot.count = count
            plot.bars = kind == ChartKind.Bar

            val low = ticks.first()
            val high = ticks.last()
            val slot = (right - left) / count
            val y: (Double) -> Float = { v -> (bottom - (v - low) / (high - low) * (bottom - top)).toFloat() }
            val x: (Int) -> Float = { i ->
                when {
                    kind == ChartKind.Bar -> left + slot * (i + 0.5f)
                    count <= 1 -> (left + right) / 2f
                    else -> left + (right - left) * i / (count - 1)
                }
            }

            ticks.forEachIndexed { index, tick ->
                val yy = y(tick)
                drawLine(colors.grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
                val layout = tickLayouts[index]
                drawText(layout, topLeft = Offset(left - 4.dp.toPx() - layout.size.width, yy - layout.size.height / 2f))
            }

            val maxLabels = max(2, ((right - left) / 56.dp.toPx()).toInt())
            val labelStep = max(1, ceil(count / maxLabels.toDouble()).toInt())
            var i = 0
            while (i < count) {
                val layout = measurer.measure(labels[i], axisStyle)
                val cx = (x(i) - layout.size.width / 2f).coerceIn(0f, max(0f, size.width - layout.size.width))
                drawText(layout, topLeft = Offset(cx, bottom + 4.dp.toPx()))
                i += labelStep
            }

            val solid = visible.filter { !it.dashed }
            val dashedLines = visible.filter { it.dashed }
            when (kind) {
                ChartKind.Bar -> {
                    val radius = CornerRadius(3.dp.toPx())
                    if (stacked) {
                        val barWidth = min(slot * 0.8f, 26.dp.toPx())
                        for (index in 0 until count) {
                            var positive = 0.0
                            var negative = 0.0
                            solid.forEach { line ->
                                val v = line.values.getOrNull(index) ?: return@forEach
                                if (v == 0.0) return@forEach
                                val base = if (v > 0) positive else negative
                                val end = base + v
                                if (v > 0) positive = end else negative = end
                                val y0 = y(base)
                                val y1 = y(end)
                                drawRect(
                                    color = line.color,
                                    topLeft = Offset(x(index) - barWidth / 2f, min(y0, y1)),
                                    size = Size(barWidth, abs(y1 - y0)),
                                )
                            }
                        }
                    } else if (solid.isNotEmpty()) {
                        val group = min(slot * 0.8f, 26.dp.toPx() * solid.size)
                        val each = group / solid.size
                        solid.forEachIndexed { k, line ->
                            for (index in 0 until count) {
                                val v = line.values.getOrNull(index) ?: continue
                                val y0 = y(0.0.coerceIn(low, high))
                                val y1 = y(v)
                                drawRoundRect(
                                    color = line.color,
                                    topLeft = Offset(x(index) - group / 2f + each * k + each * 0.05f, min(y0, y1)),
                                    size = Size(each * 0.9f, max(1f, abs(y1 - y0))),
                                    cornerRadius = radius,
                                )
                            }
                        }
                    }
                }

                ChartKind.Area -> {
                    var base = DoubleArray(count)
                    solid.forEach { line ->
                        val tops = DoubleArray(count) { index -> base[index] + (line.values.getOrNull(index) ?: 0.0) }
                        val area = Path().apply {
                            moveTo(x(0), y(tops[0]))
                            for (index in 1 until count) lineTo(x(index), y(tops[index]))
                            for (index in count - 1 downTo 0) lineTo(x(index), y(base[index]))
                            close()
                        }
                        drawPath(area, line.color.copy(alpha = 0.33f))
                        strokeSeries(tops.toList(), line.color, x, y, 1.dp.toPx())
                        base = tops
                    }
                }

                ChartKind.Line -> {
                    solid.forEach { line ->
                        if (fillSingle && solid.size == 1) {
                            val known = line.values.withIndex().filter { it.value != null }
                            if (known.size > 1) {
                                val fill = Path().apply {
                                    moveTo(x(known.first().index), bottom)
                                    known.forEach { lineTo(x(it.index), y(it.value!!)) }
                                    lineTo(x(known.last().index), bottom)
                                    close()
                                }
                                drawPath(fill, line.color.copy(alpha = DashAlpha.Hex20))
                            }
                        }
                        strokeSeries(line.values, line.color, x, y, 2.dp.toPx())
                    }
                }
            }

            dashedLines.forEach { line ->
                strokeSeries(
                    line.values,
                    line.color,
                    x,
                    y,
                    1.dp.toPx(),
                    PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }

            bands.forEach { band ->
                if (band.value < low || band.value > high) return@forEach
                val yy = y(band.value)
                drawLine(
                    color = band.color,
                    start = Offset(left, yy),
                    end = Offset(right, yy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
                val layout = measurer.measure(band.label, axisStyle.copy(color = band.color))
                drawText(layout, topLeft = Offset(right - layout.size.width - 4.dp.toPx(), yy - layout.size.height - 1.dp.toPx()))
            }

            val current = selected
            if (current != null && current in 0 until count) {
                val cx = x(current)
                drawLine(guide.copy(alpha = 0.5f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx())
                if (kind == ChartKind.Line) {
                    visible.forEach { line ->
                        val v = line.values.getOrNull(current) ?: return@forEach
                        drawCircle(line.color, radius = 3.5.dp.toPx(), center = Offset(cx, y(v)))
                    }
                }
            }
        }

        val current = selected
        if (current != null && current in 0 until count) {
            val rightSide = current < count / 2
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex30)),
                modifier = Modifier
                    .align(if (rightSide) Alignment.TopEnd else Alignment.TopStart)
                    .padding(6.dp)
                    .widthIn(max = 230.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(labels[current], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    visible.take(14).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(line.color, CircleShape),
                            )
                            Text(
                                text = "${line.name}: ${tipFormat(line.values.getOrNull(current))}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The empty, failed, or first load state of a chart, at the chart's height. */
@Composable
fun ChartPlaceholder(height: Dp, text: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        if (text == null) {
            Skeleton(Modifier.fillMaxSize(), height = height)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ShowChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                MutedText(text)
            }
        }
    }
}

/**
 * A chart legend. Tap an entry to hide or show it; long press to show it
 * alone, and long press again to bring every series back.
 */
@Composable
private fun ChartLegend(lines: List<ChartLine>, hidden: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = { onChange(if (index in hidden) hidden - index else hidden + index) },
                        onLongClick = {
                            val alone = lines.indices.all { (it in hidden) == (it != index) }
                            onChange(if (alone) emptySet() else lines.indices.filter { it != index }.toSet())
                        },
                    )
                    .alpha(if (index in hidden) 0.4f else 1f)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(line.color, RoundedCornerShape(2.dp)),
                )
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
    }
}

/** A series query result plus the previous window when compare is on. */
private class SeriesBundle(val results: List<SeriesResult?>, val previous: SeriesResult?)

/** A series result shaped for drawing. */
private class ChartModel(
    val labels: List<String>,
    val lines: List<ChartLine>,
    val single: Boolean,
    val otherCount: Int?,
    val otherNames: String,
    val seriesCount: Int,
)

/** Point values padded or cut to [size], as running totals when [total] is set. */
private fun pointValues(points: List<SeriesPoint>, size: Int, total: Boolean): List<Double?> {
    var run = 0.0
    return List(size) { index ->
        val value = points.getOrNull(index)?.value
        if (total) {
            run += value ?: 0.0
            run
        } else {
            value
        }
    }
}

private fun buildChart(
    bundle: SeriesBundle?,
    aggList: List<String>,
    groupBy: String?,
    total: Boolean,
    colors: AnalyticsPalette,
    rangeSecs: Double,
): ChartModel? {
    val primary = bundle?.results?.firstOrNull() ?: return null
    val first = primary.series.firstOrNull() ?: return null
    if (first.points.isEmpty()) return null
    val labels = first.points.map { bucketLabel(it.bucketUnix, primary.resolution, rangeSecs) }
    val single = groupBy.isNullOrEmpty() && aggList.size == 1
    val lines = mutableListOf<ChartLine>()
    aggList.forEachIndexed { aggIndex, agg ->
        val result = bundle.results.getOrNull(aggIndex) ?: return@forEachIndexed
        result.series.forEachIndexed { seriesIndex, series ->
            val count = series.labels["count"]
            val name = when {
                series.name == "Other" && count != null -> "Other ($count more)"
                aggList.size > 1 -> agg
                else -> series.name
            }
            val color = when {
                aggList.size > 1 -> colors.series[aggIndex % colors.series.size]
                single -> colors.primary
                else -> colors.seriesColor(series.name, seriesIndex)
            }
            lines += ChartLine(name, color, pointValues(series.points, labels.size, total))
        }
    }
    bundle.previous?.series?.firstOrNull()?.let { previous ->
        lines += ChartLine("Previous period", colors.muted, pointValues(previous.points, labels.size, total), dashed = true)
    }
    val other = primary.series.firstOrNull { it.name == "Other" && it.labels["count"] != null }
    return ChartModel(
        labels = labels,
        lines = lines,
        single = single,
        otherCount = other?.labels?.get("count")?.toIntOrNull(),
        otherNames = other?.labels?.get("names").orEmpty(),
        seriesCount = primary.series.size,
    )
}

/**
 * The dashboard's series chart. Loads `series` for the metric (one request
 * per aggregation in [aggs]) plus the previous window when compare is on,
 * and draws it with the legend, zoom sliders, the Σ running total switch,
 * alert bands, and the "Other" footnote with its show all toggle.
 */
@Composable
fun SeriesChart(
    metric: String,
    agg: String = "sum",
    aggs: List<String>? = null,
    groupBy: String? = null,
    maxSeries: Int? = null,
    honours: Set<String> = HonoursBotShard,
    fixed: Map<String, String> = emptyMap(),
    kind: ChartKind = ChartKind.Line,
    stack: Boolean = false,
    unit: String = "",
    bytes: Boolean = false,
    fill: Boolean = false,
    zoom: Boolean = false,
    bands: Boolean = false,
    extraBands: List<AlertBand> = emptyList(),
    totalToggle: Boolean = false,
    noCompare: Boolean = false,
    height: Dp = 220.dp,
) {
    val env = LocalAnalytics.current
    val colors = rememberAnalyticsPalette()
    val filters = env.filters
    var total by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val aggList = if (total) listOf("sum") else aggs ?: listOf(agg)
    val requestMax = maxSeries?.let { if (showAll) 100 else it }
    val compare = filters.compare && !noCompare

    val result = rememberLoad(filters, env.tick, metric, aggList, groupBy, requestMax, fixed, honours, compare) {
        coroutineScope {
            val base = filters.queryParams(honours, fixed)
            val primaries = aggList.map { a -> async { env.vm.series(metric, a, groupBy, requestMax, base) } }
            val previous = if (compare) {
                async {
                    env.vm.series(metric, aggList.first(), groupBy, requestMax, filters.queryParams(honours, fixed, shift = true))
                }
            } else {
                null
            }
            SeriesBundle(primaries.awaitAll(), previous?.await())
        }
    }
    val serverBands = if (bands) {
        rememberLoad(filters, env.tick, metric) {
            try {
                env.vm.alertBands(metric)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                emptyList()
            }
        }.data.orEmpty()
    } else {
        emptyList()
    }

    val rangeSecs = filters.rangeSeconds()
    val chart = remember(result.data, aggList, groupBy, total, colors, rangeSecs) {
        buildChart(result.data, aggList, groupBy, total, colors, rangeSecs)
    }
    val bandLines = remember(extraBands, serverBands, colors) {
        (extraBands + serverBands).flatMap { band ->
            listOfNotNull(band.threshold, band.thresholdHigh).map { value ->
                ChartBandLine(value, colors.severity(band.severity), "${band.comparator} ${fmtCompact(value)}")
            }
        }
    }
    val suffix = if (unit.isEmpty()) "" else " " + unit.replace(Regex("^/\\s*"), "/")
    val tip: (Double?) -> String = { v ->
        if (v == null || v.isNaN()) {
            Missing
        } else {
            val text = when {
                bytes -> fmtBytes(v)
                abs(v) >= 100 -> fmtInt(v)
                else -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
            }
            if (total || bytes) text else text + suffix
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (totalToggle) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    onClick = { total = !total },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = if (total) DashAlpha.Hex30 else DashAlpha.Hex10),
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Text(
                        text = "Σ",
                        style = MonospaceStyle.copy(fontSize = 13.sp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        if (chart == null) {
            ChartPlaceholder(
                height = height,
                text = when {
                    result.loading && !result.failed -> null
                    result.failed -> "Could not load"
                    else -> "No data"
                },
            )
            return@Column
        }

        val count = chart.labels.size
        var hidden by remember(chart.lines.size) { mutableStateOf(emptySet<Int>()) }
        var zoomRange by remember(count) { mutableStateOf(0f..(count - 1).coerceAtLeast(0).toFloat()) }
        val from = if (zoom) zoomRange.start.roundToInt().coerceIn(0, max(0, count - 1)) else 0
        val to = if (zoom) zoomRange.endInclusive.roundToInt().coerceIn(from, max(0, count - 1)) else count - 1
        val labels = chart.labels.subList(from, to + 1)
        val lines = chart.lines.map { it.copy(values = it.values.subList(from, to + 1)) }

        ChartCanvas(
            labels = labels,
            lines = lines,
            kind = kind,
            height = height,
            stacked = kind == ChartKind.Area || (kind == ChartKind.Bar && stack),
            fillSingle = fill && chart.single,
            bands = bandLines,
            dimmed = result.loading,
            beginAtZero = !bytes,
            hidden = hidden,
            yFormat = { if (bytes) fmtBytes(it) else fmtCompact(it) },
            tipFormat = tip,
        )

        if (zoom && count > 1) {
            RangeSlider(
                value = zoomRange,
                onValueChange = { zoomRange = it },
                valueRange = 0f..(count - 1).toFloat(),
            )
        }

        if (!chart.single) {
            ChartLegend(lines = chart.lines, hidden = hidden, onChange = { hidden = it })
        }

        if (chart.otherCount != null || showAll) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (chart.otherCount != null) {
                    MutedText("Other = ${chart.otherNames}")
                }
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        if (showAll) {
                            "Top ${maxSeries ?: 12} only"
                        } else {
                            "Show all ${chart.seriesCount - 1 + (chart.otherCount ?: 0)} series"
                        },
                    )
                }
            }
        }
    }
}

/**
 * A chart of data the tab already holds, the dashboard's `SimpleLineChart`:
 * snapshots, churn days, retention, histograms, and the census.
 */
@Composable
fun SimpleChart(
    labels: List<String>,
    lines: List<ChartLine>,
    loading: Boolean,
    kind: ChartKind = ChartKind.Line,
    empty: String = "No data",
    unit: String = "",
    beginAtZero: Boolean = false,
    height: Dp = 220.dp,
) {
    val hasData = labels.isNotEmpty() && lines.any { line -> line.values.any { it != null } }
    when {
        loading && !hasData -> ChartPlaceholder(height, null)
        !hasData -> ChartPlaceholder(height, empty)
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val suffix = if (unit.isEmpty()) "" else " $unit"
            ChartCanvas(
                labels = labels,
                lines = lines,
                kind = kind,
                height = height,
                fillSingle = lines.size == 1,
                dimmed = loading,
                beginAtZero = beginAtZero,
                tipFormat = { v -> if (v == null) Missing else fmtCompact(v) + suffix },
            )
            if (lines.size > 1) {
                var hidden by remember { mutableStateOf(emptySet<Int>()) }
                ChartLegend(lines = lines, hidden = hidden, onChange = { hidden = it })
            }
        }
    }
}

/** The size against features scatter: members on a log scale across, features up. */
@Composable
fun ScatterChart(points: List<DepthPoint>, loading: Boolean, height: Dp = 220.dp) {
    val colors = rememberAnalyticsPalette()
    val usable = remember(points) { points.filter { (it.memberCount ?: 0.0) > 0 } }
    if (usable.isEmpty()) {
        ChartPlaceholder(height, if (loading) null else "No data")
        return
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    var picked by remember(usable) { mutableStateOf<DepthPoint?>(null) }
    val lowExp = floor(log10(usable.minOf { it.memberCount ?: 1.0 })).toInt()
    val highExp = max(lowExp + 1, ceil(log10(usable.maxOf { it.memberCount ?: 1.0 })).toInt())
    val yTicks = remember(usable) { yTicks(listOf(ChartLine("", Color.Unspecified, usable.map { it.features.toDouble() })), usable.size, false, true) }
    val geometry = remember { floatArrayOf(0f, 0f, 0f, 0f) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { alpha = if (loading) 0.35f else 1f }
                .pointerInput(usable) {
                    detectTapGestures { tap ->
                        val (left, right, top, bottom) = geometry
                        picked = usable.minByOrNull { point ->
                            val px = left + ((log10(point.memberCount ?: 1.0) - lowExp) / (highExp - lowExp)).toFloat() * (right - left)
                            val py = bottom - ((point.features - yTicks.first()) / (yTicks.last() - yTicks.first())).toFloat() * (bottom - top)
                            (px - tap.x) * (px - tap.x) + (py - tap.y) * (py - tap.y)
                        }
                    }
                },
        ) {
            val tickLayouts = yTicks.map { measurer.measure(fmtCompact(it), axisStyle) }
            val sample = measurer.measure("1k", axisStyle)
            val left = (tickLayouts.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - sample.size.height - 6.dp.toPx()
            geometry[0] = left
            geometry[1] = right
            geometry[2] = top
            geometry[3] = bottom
            val yLow = yTicks.first()
            val yHigh = yTicks.last()
            val px: (Double) -> Float = { m -> left + ((log10(m) - lowExp) / (highExp - lowExp)).toFloat() * (right - left) }
            val py: (Double) -> Float = { f -> (bottom - (f - yLow) / (yHigh - yLow) * (bottom - top)).toFloat() }
            yTicks.forEachIndexed { index, tick ->
                val yy = py(tick)
                drawLine(colors.grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
                val layout = tickLayouts[index]
                drawText(layout, topLeft = Offset(left - 4.dp.toPx() - layout.size.width, yy - layout.size.height / 2f))
            }
            for (exp in lowExp..highExp) {
                val xx = px(10.0.pow(exp))
                drawLine(colors.grid, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(fmtCompact(10.0.pow(exp)), axisStyle)
                drawText(
                    layout,
                    topLeft = Offset((xx - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width), bottom + 4.dp.toPx()),
                )
            }
            usable.forEach { point ->
                drawCircle(
                    color = colors.primary.copy(alpha = 0.69f),
                    radius = if (point == picked) 6.dp.toPx() else 4.dp.toPx(),
                    center = Offset(px(point.memberCount ?: 1.0), py(point.features.toDouble())),
                )
            }
        }
        val current = picked
        MutedText(
            if (current == null) {
                "members (log) × features · tap a point"
            } else {
                "${current.guildId} · ${fmtInt(current.memberCount)} members · ${current.features} features"
            },
            mono = current != null,
        )
    }
}

/**
 * A heat matrix: [rows] down the side, [cols] across a horizontally
 * scrolling grid, each cell in the primary at an alpha scaled to the
 * largest value. Tap a cell to read it.
 */
@Composable
fun MatrixHeat(
    rows: List<String>,
    cols: List<String>,
    colLabel: (Int) -> String,
    value: (String, String) -> Double,
    unit: String,
    describe: (String, String, Double) -> String,
    cellWidth: Dp = 22.dp,
    cellHeight: Dp = 22.dp,
    rowLabelWidth: Dp = 84.dp,
    monoRows: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val peak = remember(rows, cols, value) {
        var best = 0.0
        rows.forEach { r -> cols.forEach { c -> best = max(best, value(r, c)) } }
        best
    }
    val labelEvery = max(1, ceil(cols.size / 12.0).toInt())
    var picked by remember(rows, cols) { mutableStateOf<Pair<Int, Int>?>(null) }
    val gap = 2.dp
    val header = 16.dp

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Column(
                modifier = Modifier.width(rowLabelWidth),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Spacer(Modifier.height(header - gap))
                rows.forEach { row ->
                    Box(modifier = Modifier.height(cellHeight), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = row,
                            style = if (monoRows) MonospaceStyle else MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Canvas(
                    modifier = Modifier
                        .width((cellWidth + gap) * cols.size)
                        .height(header + (cellHeight + gap) * rows.size)
                        .pointerInput(rows, cols) {
                            detectTapGestures { tap ->
                                val col = (tap.x / (cellWidth + gap).toPx()).toInt()
                                val row = ((tap.y - header.toPx()) / (cellHeight + gap).toPx()).toInt()
                                picked = if (tap.y >= header.toPx() && col in cols.indices && row in rows.indices) {
                                    row to col
                                } else {
                                    null
                                }
                            }
                        },
                ) {
                    val w = cellWidth.toPx()
                    val h = cellHeight.toPx()
                    val g = gap.toPx()
                    val top = header.toPx()
                    cols.indices.forEach { c ->
                        if (c % labelEvery == 0) {
                            val layout = measurer.measure(colLabel(c), axisStyle)
                            drawText(layout, topLeft = Offset(c * (w + g), 0f))
                        }
                    }
                    rows.forEachIndexed { r, row ->
                        cols.forEachIndexed { c, col ->
                            val v = value(row, col)
                            val alpha = if (v > 0 && peak > 0) (24 + (v / peak) * 200) / 255.0 else 8 / 255.0
                            val origin = Offset(c * (w + g), top + r * (h + g))
                            drawRoundRect(
                                color = primary.copy(alpha = alpha.toFloat()),
                                topLeft = origin,
                                size = Size(w, h),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                            if (picked == r to c) {
                                drawRoundRect(
                                    color = primary,
                                    topLeft = origin,
                                    size = Size(w, h),
                                    cornerRadius = CornerRadius(2.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
        }
        val current = picked
        if (current != null) {
            val row = rows[current.first]
            val col = cols[current.second]
            MutedText(describe(row, col, value(row, col)), mono = true)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MutedText("0")
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 24 / 255f), primary))),
            )
            MutedText("${fmtInt(peak)} $unit".trim())
        }
    }
}

/**
 * One column of a [DataTable]: the header, a fixed width, how the cell reads,
 * how it sorts, an optional pill tone, and the full text shown in the row
 * detail sheet when the cell is truncated.
 */
class TableColumn<T>(
    val label: String,
    val width: Dp = 96.dp,
    val numeric: Boolean = false,
    val mono: Boolean = false,
    val muted: Boolean = false,
    val sort: ((T) -> Any?)? = null,
    val tone: ((T) -> Tone?)? = null,
    val detail: ((T) -> String?)? = null,
    val text: (T) -> String,
)

/** A sort key: numbers and instants compare numerically, everything else as text. */
private fun sortKey(value: Any?): Any? = when (value) {
    null -> null
    is Number -> value.toDouble().takeUnless { it.isNaN() }
    is java.time.Instant -> value.toEpochMilli().toDouble()
    is Boolean -> if (value) 1.0 else 0.0
    else -> value.toString()
}

private fun compareKeys(a: Any?, b: Any?): Int =
    if (a is Double && b is Double) a.compareTo(b) else a.toString().compareTo(b.toString(), ignoreCase = true)

/**
 * The dashboard's analytics table: tap a header to sort descending, again
 * for ascending, with missing values last. Client paging shows "N rows" and
 * a pager when [pageSize] is set; server paging uses [serverPage] and
 * [serverTotal] with [onPage]. Tapping a row calls [onRowClick]; long press
 * opens every cell's full text. The table scrolls sideways on narrow screens.
 */
@Composable
fun <T> DataTable(
    columns: List<TableColumn<T>>,
    rows: List<T>,
    loading: Boolean,
    empty: String = "No data",
    sortIndex: Int? = null,
    sortDescending: Boolean = true,
    pageSize: Int? = null,
    serverPage: Int? = null,
    serverTotal: Long? = null,
    onPage: ((Int) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    isSelected: ((T) -> Boolean)? = null,
    rowAlpha: ((T) -> Float)? = null,
) {
    val colors = rememberAnalyticsPalette()
    var sortBy by remember { mutableStateOf(sortIndex) }
    var descending by remember { mutableStateOf(sortDescending) }
    var localPage by remember { mutableIntStateOf(1) }
    var details by remember { mutableStateOf<T?>(null) }

    val sorted = remember(rows, sortBy, descending) {
        val column = sortBy?.let { columns.getOrNull(it) }
        if (column == null) {
            rows
        } else {
            val key: (T) -> Any? = { row -> sortKey(column.sort?.invoke(row) ?: column.text(row).takeIf { it != Missing }) }
            rows.sortedWith { a, b ->
                val x = key(a)
                val y = key(b)
                when {
                    x == y -> 0
                    x == null -> 1
                    y == null -> -1
                    else -> if (descending) compareKeys(y, x) else compareKeys(x, y)
                }
            }
        }
    }
    val pager: ((Int) -> Unit)? = if (serverPage != null && serverTotal != null) onPage else null
    val serverPaged = pager != null
    val totalRows = if (pager != null) serverTotal ?: 0L else rows.size.toLong()
    val size = pageSize ?: 25
    val pageCount = when {
        serverPaged -> max(1, ceil(totalRows / size.toDouble()).toInt())
        pageSize != null -> max(1, ceil(rows.size / pageSize.toDouble()).toInt())
        else -> 1
    }
    val currentPage = if (pager != null) serverPage ?: 1 else min(localPage, pageCount)
    val visibleRows = if (!serverPaged && pageSize != null) {
        sorted.drop((currentPage - 1) * pageSize).take(pageSize)
    } else {
        sorted
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            loading && rows.isEmpty() -> SkeletonRows(3)
            visibleRows.isEmpty() -> MutedText(empty, center = true)
            else -> Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Row(modifier = Modifier.padding(bottom = 2.dp)) {
                    columns.forEachIndexed { index, column ->
                        Row(
                            modifier = Modifier
                                .width(column.width)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (sortBy == index) {
                                        descending = !descending
                                    } else {
                                        sortBy = index
                                        descending = true
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = if (column.numeric) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = column.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (sortBy == index) {
                                Icon(
                                    if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                    }
                }
                visibleRows.forEach { row ->
                    val selected = isSelected?.invoke(row) == true
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex15) else Color.Transparent,
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex30) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .combinedClickable(
                                onClick = { onRowClick?.invoke(row) },
                                onLongClick = { details = row },
                            )
                            .alpha(rowAlpha?.invoke(row) ?: 1f)
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        columns.forEach { column ->
                            val tone = column.tone?.invoke(row)
                            Box(
                                modifier = Modifier
                                    .width(column.width)
                                    .padding(horizontal = 6.dp),
                                contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart,
                            ) {
                                if (tone != null) {
                                    StatePill(text = column.text(row), tone = colors.tone(tone))
                                } else {
                                    Text(
                                        text = column.text(row),
                                        style = when {
                                            column.mono -> MonospaceStyle.copy(fontSize = 12.sp)
                                            column.numeric -> MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
                                            else -> MaterialTheme.typography.bodySmall
                                        },
                                        color = if (column.muted) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (pageCount > 1 || serverPaged) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MutedText(
                    text = "${fmtInt(totalRows.toDouble())} rows",
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        val next = max(1, currentPage - 1)
                        if (pager != null) pager(next) else localPage = next
                    },
                    enabled = currentPage > 1,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                }
                MutedText("$currentPage / $pageCount")
                IconButton(
                    onClick = {
                        val next = min(pageCount, currentPage + 1)
                        if (pager != null) pager(next) else localPage = next
                    },
                    enabled = currentPage < pageCount,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                }
            }
        }
    }

    details?.let { row ->
        MewdekoBottomSheet(onDismissRequest = { details = null }, title = "Row details") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                columns.forEach { column ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Overline(column.label)
                        Text(
                            text = column.detail?.invoke(row)?.takeIf { it.isNotEmpty() } ?: column.text(row),
                            style = if (column.mono) MonospaceStyle.copy(fontSize = 13.sp) else MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

/** A two column grid of label and value pairs. */
@Composable
private fun KeyGrid(items: List<Pair<String, String>>, monoKeys: Set<String> = emptySet()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        MutedText(label)
                        Text(
                            text = value,
                            style = if (label in monoKeys) {
                                MonospaceStyle.copy(fontSize = 12.sp)
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                            },
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A tab level picker over a small list, with an "any" entry mapping to the
 * empty value. Values not in [options] still show, as the dashboard keeps a
 * current value that dropped out of the list.
 */
@Composable
private fun ChoiceSelect(
    label: String,
    anyLabel: String?,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.FilterList,
) {
    val all = buildList {
        if (anyLabel != null) add(SelectorOption(AnyOption, anyLabel))
        options.forEach { (id, name) -> add(SelectorOption(id, name)) }
        if (selected.isNotEmpty() && options.none { it.first == selected }) add(SelectorOption(selected, selected))
    }
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(icon),
        options = all,
        placeholder = anyLabel ?: label,
        selectedId = selected.ifEmpty { if (anyLabel != null) AnyOption else null },
        onSelect = { id -> onSelect(if (id == null || id == AnyOption) "" else id) },
        label = label,
        modifier = modifier,
    )
}

/** Sample values the shared registry has seen for one label of one metric. */
private fun registryValues(registry: List<MetricDescriptor>, metric: String, label: String): List<String> =
    registry.firstOrNull { it.metric == metric }?.labels?.get(label).orEmpty().sortedWith(LabelValueOrder)

/** The firing alert cards, tinted by severity. */
@Composable
private fun FiringCards(firing: List<FiringAlert>, failed: Boolean, loading: Boolean, showMetric: Boolean) {
    val colors = rememberAnalyticsPalette()
    when {
        failed -> MutedText("Alert rules unavailable")
        loading && firing.isEmpty() -> SkeletonRows(2)
        firing.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.ok, modifier = Modifier.size(18.dp))
            Text("Nothing firing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            firing.forEach { alert ->
                val tint = colors.severity(alert.severity)
                val shape = RoundedCornerShape(10.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(toneWash(tint), shape)
                        .border(1.dp, tint.copy(alpha = DashAlpha.Hex30), shape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = alert.ruleName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TonePill(alert.severity, severityTone(alert.severity))
                    }
                    val parts = listOfNotNull(
                        alert.metric.takeIf { showMetric },
                        alert.groupKey.ifEmpty { "fleet" },
                        "${fmtCompact(alert.lastValue)} vs ${fmtCompact(alert.threshold)}",
                        ago(alert.since),
                    )
                    MutedText(parts.joinToString(" · "), mono = true)
                }
            }
        }
    }
}

/** The exception groups table; a row opens its stored hourly occurrences. */
@Composable
private fun ErrorsPanel(limit: Int) {
    val env = LocalAnalytics.current
    val filters = env.filters
    val rows = rememberLoad(filters, env.tick, limit) { env.vm.errors(filters.timeAndBot(), limit) }
    var selected by remember { mutableStateOf<ErrorGroupRow?>(null) }
    val columns = remember {
        listOf(
            TableColumn<ErrorGroupRow>("Type", 160.dp, mono = true) { it.type },
            TableColumn("Module", 110.dp, muted = true) { it.module ?: Missing },
            TableColumn("Message", 220.dp, detail = { it.lastMessage }) { truncate(it.lastMessage, 80) },
            TableColumn("Count", 72.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("First", 86.dp, numeric = true, muted = true, sort = { utcParse(it.firstSeen) }) { ago(it.firstSeen) },
            TableColumn("Last", 86.dp, numeric = true, muted = true, sort = { utcParse(it.lastSeen) }) { ago(it.lastSeen) },
            TableColumn("Location", 180.dp, mono = true, muted = true) { it.location ?: Missing },
        )
    }
    DataTable(
        columns = columns,
        rows = rows.data.orEmpty(),
        loading = rows.loading,
        empty = "No exceptions",
        sortIndex = 3,
        onRowClick = { row ->
            selected = if (selected?.hash == row.hash && selected?.type == row.type) null else row
        },
        isSelected = { it.hash == selected?.hash && it.type == selected?.type },
    )
    selected?.let { group ->
        val samples = rememberLoad(group, filters, env.tick) { env.vm.errorSamples(group, filters.timeAndBot(), 24) }
        val shape = RoundedCornerShape(10.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex05), shape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex20), shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MutedText("${group.type} · ${group.module ?: Missing} · ${fmtInt(group.count)} in range", mono = true)
            val sampleColumns = remember {
                listOf(
                    TableColumn<ErrorOccurrenceRow>("Hour (UTC)", 128.dp, muted = true, sort = { utcParse(it.hour) }) { stamp(it.hour) },
                    TableColumn("Bot", 150.dp, mono = true) { it.bot },
                    TableColumn("Shard", 60.dp, numeric = true, sort = { it.shard }) { it.shard?.toString() ?: Missing },
                    TableColumn("Count", 64.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
                    TableColumn("Guild", 160.dp, mono = true) { it.lastGuildId ?: Missing },
                    TableColumn("Last", 86.dp, numeric = true, muted = true, sort = { utcParse(it.lastSeen) }) { ago(it.lastSeen) },
                    TableColumn("Message", 240.dp, detail = { it.message }) { truncate(it.message, 90) },
                )
            }
            DataTable(
                columns = sampleColumns,
                rows = samples.data.orEmpty(),
                loading = samples.loading,
                empty = "No stored occurrences",
                sortIndex = 5,
            )
        }
    }
}

/** The "Exception groups" drill content of the unhandled errors tile. */
private val ExceptionGroupsDrill: @Composable ColumnScope.() -> Unit = {
    Overline("Exception groups")
    ErrorsPanel(limit = 15)
}

/** Everything known about one guild over the range. */
@Composable
private fun GuildCardView(guildId: String) {
    val env = LocalAnalytics.current
    val colors = rememberAnalyticsPalette()
    val card = rememberLoad(guildId, env.filters, env.tick) { env.vm.guildCard(guildId, env.filters.timeParams()) }
    val data = card.data
    when {
        card.loading && data == null -> SkeletonRows(3)
        data == null -> MutedText("No card for $guildId")
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(data.name ?: "unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(data.guildId, style = MonospaceStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TonePill(if (data.present) "present" else "gone", if (data.present) Tone.Ok else Tone.Muted)
                data.shard?.let { TonePill("shard $it", Tone.Muted) }
            }
            KeyGrid(
                listOf(
                    "Members" to fmtInt(data.memberCount),
                    "Joined" to (data.joinedAt?.let { ago(it) } ?: Missing),
                    "Commands in range" to fmtInt(data.commands),
                    "Events in range" to fmtInt(data.events),
                ),
            )
            data.shape?.let { shape ->
                val share = if (shape.memberCount > 0) shape.bots / shape.memberCount * 100 else 0.0
                KeyGrid(
                    listOf(
                        "Humans / bots" to "${fmtInt(shape.humans)} / ${fmtInt(shape.bots)} (${fmtPct(share, 0)} bots)",
                        "Online" to fmtInt(shape.online),
                        "Boosts" to "${fmtInt(shape.boosts)} · tier ${shape.boostTier}",
                        "Channels / roles" to "${fmtInt(shape.channels)} / ${fmtInt(shape.roles)}",
                        "Owner" to shape.ownerId,
                        "Created" to ago(shape.createdAt),
                    ),
                    monoKeys = setOf("Owner"),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Features configured · ${data.configuredFeatures.size} · ${data.enabledFeatures.size} enabled")
                if (data.configuredFeatures.isEmpty()) {
                    MutedText("Nothing configured")
                } else {
                    val enabled = data.enabledFeatures.toSet()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.configuredFeatures.forEach { feature ->
                            StatePill(feature, if (feature in enabled) colors.ok else colors.muted)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val uses = data.features.sumOf { it.count }
                val errors = data.features.sumOf { it.errors }
                Overline("Features used · ${fmtInt(uses)} uses · ${fmtPct(if (uses > 0) errors / uses * 100 else 0.0)} err")
                BreakdownBars(
                    rows = data.features.map { BreakdownRow(it.feature, it.count) },
                    top = 12,
                    empty = "No feature use in range",
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Overline("Top commands in range")
                BreakdownBars(
                    rows = data.topCommands.map { BreakdownRow(it.command, it.count) },
                    top = 10,
                    empty = "No commands in range",
                )
            }
        }
    }
}

/** A drill sheet holding one guild's card, opened from server rows. */
@Composable
private fun GuildCardSheet(guildId: String, onDismiss: () -> Unit) {
    DrillSheet(title = "Guild $guildId", metric = null, onDismiss = onDismiss) {
        GuildCardView(guildId)
    }
}

@Composable
private fun OverviewTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    TileGrid(
        { MetricTile("Servers", "guild.count", "last", ValueFormat.Whole) },
        { MetricTile("Users", "user.count", "last", ValueFormat.Compact, HonoursBot) },
        { MetricTile("Commands / min", "cmd.count", "sum", ValueFormat.Decimal, per = Per.Minute) },
        { MetricTile("Gateway events", "ev.count", "sum", ValueFormat.Compact) },
        { MetricTile("REST req / s", "rest.count", "sum", ValueFormat.Decimal, HonoursBot, per = Per.Second) },
        { MetricTile("Unhandled errors", "err.count", "sum", ValueFormat.Whole, drillExtra = ExceptionGroupsDrill) },
        { MetricTile("Worst shard latency", "shard.latency", "max", ValueFormat.Ms, critSpark = true, unit = "ms") },
        { MetricTile("Music players", "music.players", "last", ValueFormat.Whole, HonoursBot) },
    )

    val firing = rememberLoad(filters, env.tick) { env.vm.firingAlerts() }
    val snapshots = rememberLoad(filters, env.tick) { env.vm.serverSnapshots(30, filters.botParam()) }

    CardColumns(
        {
            AnCard(title = "Shard latency", note = "max per bucket · bands from alert rules") {
                SeriesChart(
                    metric = "shard.latency",
                    agg = "max",
                    groupBy = "shard",
                    maxSeries = 24,
                    unit = "ms",
                    zoom = true,
                    bands = true,
                    height = 300.dp,
                )
            }
        },
        {
            AnCard(title = "Firing alerts") {
                FiringCards(firing.data.orEmpty(), firing.failed, firing.loading, showMetric = false)
            }
        },
        {
            AnCard(title = "Commands / s by kind") {
                SeriesChart("cmd.count", "rate", groupBy = "kind", kind = ChartKind.Area, unit = "/ s", totalToggle = true)
            }
        },
        {
            AnCard(title = "Gateway events / s", note = "top types, rest folded") {
                SeriesChart(
                    "ev.count",
                    "rate",
                    groupBy = "type",
                    maxSeries = 6,
                    kind = ChartKind.Area,
                    unit = "/ s",
                    totalToggle = true,
                )
            }
        },
        {
            AnCard(title = "Servers · daily snapshots") {
                val rows = snapshots.data.orEmpty()
                SimpleChart(
                    labels = rows.map { it.day.take(10).drop(5) },
                    lines = listOf(ChartLine("Servers", rememberAnalyticsPalette().primary, rows.map { it.guilds })),
                    loading = snapshots.loading,
                    empty = "No snapshots yet",
                )
            }
        },
        {
            AnCard(title = "REST requests / s") {
                SeriesChart("rest.count", "rate", honours = HonoursBot, unit = "req/s", fill = true, height = 160.dp)
            }
        },
        {
            AnCard(title = "Process memory") {
                SeriesChart(
                    "proc.rss",
                    "avg",
                    groupBy = "bot",
                    honours = HonoursBot,
                    bytes = true,
                    fill = true,
                    height = 160.dp,
                )
            }
        },
    )
}

@Composable
private fun CommandsTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    var kind by rememberSaveable { mutableStateOf("") }
    var module by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf("") }
    val kinds = registryValues(env.registry, "cmd.count", "kind")
    val modules = registryValues(env.registry, "cmd.count", "module")
    val fixed = buildMap {
        if (kind.isNotEmpty()) put("kind", kind)
        if (module.isNotEmpty()) put("module", module)
    }
    val fixedFailed = fixed + ("ok" to "0")
    val commandQuery = buildList {
        addAll(filters.timeParams())
        if (filters.bot.isNotEmpty()) add("bot" to filters.bot)
        if (filters.shard.isNotEmpty()) add("shard" to filters.shard)
        if (filters.guild.isNotEmpty()) add("guild" to filters.guild)
        if (kind.isNotEmpty()) add("kind" to kind)
        if (module.isNotEmpty()) add("module" to module)
        if (outcome.isNotEmpty()) add("ok" to (outcome == "ok").toString())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceSelect("Kind", "any kind", kinds.map { it to it }, kind, { kind = it }, Modifier.weight(1f))
        ChoiceSelect("Module", "any module", modules.map { it to it }, module, { module = it }, Modifier.weight(1f))
        ChoiceSelect(
            "Outcome",
            "any outcome",
            listOf("ok" to "ok", "error" to "error"),
            outcome,
            { outcome = it },
            Modifier.weight(1f),
        )
    }

    TileGrid(
        { MetricTile("Invocations", "cmd.count", fixed = fixed) },
        { MetricTile("Errors", "cmd.count", fixed = fixedFailed) },
        { MetricTile("Duration p95", "cmd.duration", "p95", ValueFormat.Ms, HonoursBot, fixed, unit = "ms") },
        { MetricTile("Duration p50", "cmd.duration", "p50", ValueFormat.Ms, HonoursBot, fixed, unit = "ms") },
    )

    val heatmap = rememberLoad(commandQuery, env.tick) { env.vm.commandHeatmap(commandQuery) }

    CardColumns(
        {
            AnCard(title = "Invocations by module") {
                SeriesChart(
                    "cmd.count",
                    groupBy = "module",
                    maxSeries = 12,
                    fixed = fixed,
                    kind = ChartKind.Area,
                    unit = "per bucket",
                    zoom = true,
                    height = 300.dp,
                )
            }
        },
        {
            AnCard(title = "Errors by class") {
                SeriesChart(
                    "cmd.errors",
                    groupBy = "error",
                    maxSeries = 8,
                    honours = HonoursBot,
                    fixed = fixed,
                    kind = ChartKind.Area,
                    unit = "errors",
                    height = 300.dp,
                )
            }
        },
        {
            AnCard(title = "Execution duration", note = "p50 · p95 · p99") {
                SeriesChart(
                    "cmd.duration",
                    aggs = listOf("p50", "p95", "p99"),
                    honours = HonoursBot,
                    fixed = fixed,
                    unit = "ms",
                    noCompare = true,
                )
            }
        },
        {
            AnCard(title = "Invocations by kind") {
                BreakdownPanel("cmd.count", "kind", fixed = fixed)
            }
        },
    )

    AnCard(title = "Usage heatmap", note = "UTC hour × server size") {
        val data = heatmap.data
        when {
            heatmap.loading && data?.cells.isNullOrEmpty() -> Skeleton(Modifier.fillMaxWidth(), height = 120.dp)
            data == null || data.sizes.isEmpty() || data.cells.isEmpty() -> MutedText("No data", center = true)
            else -> {
                val cells = remember(data) { data.cells.associate { "${it.size}|${it.hour}" to it.count } }
                val hours = remember { (0 until 24).map { String.format(Locale.US, "%02d", it) } }
                MatrixHeat(
                    rows = data.sizes,
                    cols = hours,
                    colLabel = { hours[it] },
                    value = { size, hour -> cells["$size|${hour.toInt()}"] ?: 0.0 },
                    unit = "invocations",
                    describe = { size, hour, v -> "$size · $hour:00 UTC · ${fmtInt(v)} invocations" },
                    rowLabelWidth = 72.dp,
                )
            }
        }
    }

    CommandTables(commandQuery, filters.timeParams())
}

@Composable
private fun CommandTables(query: List<Pair<String, String>>, time: List<Pair<String, String>>) {
    val env = LocalAnalytics.current
    var page by remember(query) { mutableIntStateOf(1) }
    val top = rememberLoad(query, env.tick) { env.vm.topCommands(query, 15) }
    val invocations = rememberLoad(query, env.tick, page) { env.vm.invocations(query, page, 25) }
    val failing = rememberLoad(query, env.tick) { env.vm.failingCommands(query, 20) }
    var selected by remember { mutableStateOf<FailingCommandRow?>(null) }

    val topColumns = remember {
        listOf(
            TableColumn<TopCommandRow>("Command", 140.dp, mono = true) { it.command },
            TableColumn("Count", 72.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("Guilds", 64.dp, numeric = true, sort = { it.guilds }) { fmtInt(it.guilds) },
            TableColumn("p95", 80.dp, numeric = true, sort = { it.p95Ms ?: it.avgMs }) { fmtMs(it.p95Ms ?: it.avgMs) },
            TableColumn(
                "Err",
                72.dp,
                numeric = true,
                sort = { it.failureRate },
                tone = { if (it.failureRate >= 0.25) Tone.Crit else if (it.failureRate >= 0.05) Tone.Warn else null },
            ) { fmtPct(it.failureRate * 100) },
            TableColumn("Module", 110.dp, muted = true) { it.module ?: Missing },
        )
    }
    val invocationColumns = remember {
        listOf(
            TableColumn<InvocationRow>("When", 76.dp, muted = true, sort = { utcParse(it.at) }) { clock(it.at) },
            TableColumn("Bot", 150.dp, mono = true) { it.bot },
            TableColumn("Guild", 160.dp, mono = true) { it.guildId ?: Missing },
            TableColumn("Shard", 56.dp, numeric = true, sort = { it.shard }) { it.shard?.toString() ?: Missing },
            TableColumn("Command", 130.dp, mono = true) { it.command },
            TableColumn("Kind", 96.dp, tone = { Tone.Muted }) { it.kind },
            TableColumn("ms", 64.dp, numeric = true, sort = { it.durationMs }) { fmtInt(it.durationMs) },
            TableColumn("Ack", 72.dp, numeric = true, sort = { it.ackMs }) { it.ackMs?.let { v -> fmtMs(v) } ?: Missing },
            TableColumn(
                "Result",
                140.dp,
                tone = { if (it.ok) Tone.Ok else Tone.Crit },
                detail = { it.errorMessage },
            ) { if (it.ok) "ok" else it.errorClass ?: "error" },
        )
    }
    val failingColumns = remember {
        listOf(
            TableColumn<FailingCommandRow>("Command", 140.dp, mono = true) { it.command },
            TableColumn("Module", 110.dp, muted = true) { it.module ?: Missing },
            TableColumn("Error class", 170.dp, tone = { Tone.Crit }) { it.errorClass },
            TableColumn("Failures", 72.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("Last seen", 128.dp, muted = true, sort = { utcParse(it.lastSeen) }) { stamp(it.lastSeen) },
            TableColumn("Last message", 220.dp, muted = true, detail = { it.lastMessage }) {
                truncate(it.lastMessage, 70).ifEmpty { Missing }
            },
        )
    }

    CardColumns(
        {
            AnCard(title = "Top commands") {
                DataTable(topColumns, top.data.orEmpty(), top.loading, sortIndex = 1)
            }
        },
        {
            AnCard(title = "Invocation log", note = "no user ids") {
                val data = invocations.data
                DataTable(
                    columns = invocationColumns,
                    rows = data?.items.orEmpty(),
                    loading = invocations.loading,
                    sortIndex = 0,
                    pageSize = 25,
                    serverPage = data?.page ?: page,
                    serverTotal = data?.total ?: 0L,
                    onPage = { page = it },
                )
            }
        },
    )

    AnCard(title = "Top failing commands", note = "row → samples") {
        DataTable(
            columns = failingColumns,
            rows = failing.data.orEmpty(),
            loading = failing.loading,
            sortIndex = 3,
            onRowClick = { row ->
                selected = if (selected?.command == row.command && selected?.errorClass == row.errorClass) null else row
            },
            isSelected = { it.command == selected?.command && it.errorClass == selected?.errorClass },
        )
    }

    selected?.let { row ->
        val samples = rememberLoad(row, time, env.tick) { env.vm.commandErrorSamples(time, row.command, row.errorClass, 20) }
        val sampleColumns = remember {
            listOf(
                TableColumn<CommandErrorSample>("When", 128.dp, muted = true, sort = { utcParse(it.at) }) { stamp(it.at) },
                TableColumn("Guild", 160.dp, mono = true) { it.guildId ?: Missing },
                TableColumn("Kind", 96.dp, tone = { Tone.Muted }) { it.kind },
                TableColumn("Module", 110.dp, muted = true) { it.module ?: Missing },
                TableColumn("ms", 64.dp, numeric = true, sort = { it.durationMs }) { fmtInt(it.durationMs) },
                TableColumn("Message", 240.dp, detail = { it.message }) { truncate(it.message, 90).ifEmpty { Missing } },
            )
        }
        AnCard(title = "Samples", note = "exception messages only") {
            MutedText("${row.command} · ${row.errorClass}", mono = true)
            DataTable(sampleColumns, samples.data.orEmpty(), samples.loading, empty = "No samples", sortIndex = 0)
        }
    }
}

@Composable
private fun EventsTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val counts = rememberLoad(filters, env.tick) { env.vm.eventCounts(filters.queryParams(HonoursBotShard)) }
    val columns = remember {
        listOf(
            TableColumn<EventCountRow>("Type", 200.dp, mono = true) { it.type },
            TableColumn("Count", 90.dp, numeric = true, sort = { it.count }) { fmtCompact(it.count) },
        )
    }

    AnCard(title = "Gateway events by type", note = "top types, rest folded") {
        SeriesChart(
            "ev.count",
            "rate",
            groupBy = "type",
            maxSeries = 12,
            kind = ChartKind.Area,
            unit = "/ s",
            zoom = true,
            totalToggle = true,
            height = 320.dp,
        )
    }
    MetricTile("Count in range", "ev.count", format = ValueFormat.Compact, spark = false)
    AnCard {
        DataTable(columns, counts.data.orEmpty(), counts.loading, sortIndex = 1, pageSize = 14)
    }
    CardColumns(
        { AnCard(title = "Events per shard") { BreakdownPanel("ev.count", "shard", HonoursBot, top = 24) } },
        {
            AnCard(title = "Event handler p95 by type", note = "subscriber execution") {
                SeriesChart("ev.duration", "p95", groupBy = "type", maxSeries = 8, honours = HonoursBot, unit = "ms", noCompare = true)
            }
        },
        {
            AnCard(title = "Handler errors by module") {
                SeriesChart(
                    "ev.errors",
                    groupBy = "module",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Bar,
                    stack = true,
                    unit = "errors",
                )
            }
        },
        {
            AnCard(title = "Handler errors by type") {
                BreakdownPanel("ev.errors", "type", HonoursBot, top = 12, color = rememberAnalyticsPalette().crit)
            }
        },
    )
}

@Composable
private fun LatencyTab() {
    TileGrid(
        { MetricTile("REST p95", "rest.duration", "p95", ValueFormat.Ms, HonoursBot, unit = "ms") },
        { MetricTile("REST 5xx", "rest.count", honours = HonoursBot, fixed = mapOf("status" to "5xx"), critSpark = true) },
        { MetricTile("REST 429", "rest.count", honours = HonoursBot, fixed = mapOf("status" to "429"), critSpark = true) },
        { MetricTile("Ratelimit hits", "ratelimit.hit", honours = HonoursBot) },
        { MetricTile("DB p95", "db.duration", "p95", ValueFormat.Ms, HonoursBot, unit = "ms") },
        { MetricTile("DB errors", "db.errors", honours = HonoursBot, critSpark = true) },
        { MetricTile("Shard reconnects", "shard.reconnects") },
        { MetricTile("Unhandled errors", "err.count", drillExtra = ExceptionGroupsDrill) },
    )
    CardColumns(
        {
            AnCard(title = "REST latency", note = "p50 · p95 · p99") {
                SeriesChart("rest.duration", aggs = listOf("p50", "p95", "p99"), honours = HonoursBot, unit = "ms", noCompare = true)
            }
        },
        {
            AnCard(title = "REST requests / s by route", note = "snowflakes templated") {
                SeriesChart(
                    "rest.count",
                    "rate",
                    groupBy = "route",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Area,
                    unit = "req/s",
                    totalToggle = true,
                )
            }
        },
        {
            AnCard(title = "Rate limit hits by route") {
                SeriesChart(
                    "ratelimit.hit",
                    groupBy = "route",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Bar,
                    stack = true,
                    unit = "hits",
                )
            }
        },
        {
            AnCard(title = "Database p95 by op") {
                SeriesChart("db.duration", "p95", groupBy = "op", honours = HonoursBot, unit = "ms", noCompare = true)
            }
        },
        {
            AnCard(title = "Database errors by op") {
                SeriesChart("db.errors", groupBy = "op", honours = HonoursBot, kind = ChartKind.Bar, stack = true, unit = "errors")
            }
        },
    )
    TileGrid(
        {
            AnCard(title = "CPU") {
                SeriesChart("proc.cpu", "avg", groupBy = "bot", honours = HonoursBot, unit = "%", height = 150.dp, noCompare = true)
            }
        },
        {
            AnCard(title = "RSS") {
                SeriesChart("proc.rss", "avg", groupBy = "bot", honours = HonoursBot, bytes = true, height = 150.dp, noCompare = true)
            }
        },
        {
            AnCard(title = "Threads") {
                SeriesChart("proc.threads", "max", groupBy = "bot", honours = HonoursBot, height = 150.dp, noCompare = true)
            }
        },
        {
            AnCard(title = "Gen 2 GCs") {
                SeriesChart("proc.gc2", "last", groupBy = "bot", honours = HonoursBot, height = 150.dp, noCompare = true)
            }
        },
    )
    AnCard(title = "Shards", note = "latest state · peak in range") { ShardTable() }
    AnCard(title = "Shard latency") {
        SeriesChart("shard.latency", "max", groupBy = "shard", maxSeries = 24, unit = "ms", bands = true, height = 260.dp)
    }
    AnCard(title = "Unhandled exceptions", note = "row → occurrences") { ErrorsPanel(limit = 25) }
    AnCard(title = "Errors by type") {
        BreakdownPanel("err.count", "type", top = 10, color = rememberAnalyticsPalette().crit)
    }
}

/** One shard's merged state for the shards table. */
private data class ShardRow(
    val shard: Int,
    val connected: Boolean?,
    val latency: Double?,
    val peak: Double?,
    val guilds: Double?,
    val reconnects: Double?,
)

@Composable
private fun ShardTable() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val rows = rememberLoad(filters, env.tick) {
        val base = filters.queryParams(HonoursBotShard)
        coroutineScope {
            val queries = listOf(
                "shard.state" to "last",
                "shard.latency" to "last",
                "shard.latency" to "max",
                "guild.count" to "last",
                "shard.reconnects" to "sum",
            ).map { (metric, agg) ->
                async {
                    try {
                        env.vm.breakdown(metric, "shard", agg, 500, base).associate { it.name to it.value }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Log.w(TAG, "shard table query failed: ${t.message}")
                        emptyMap()
                    }
                }
            }.awaitAll()
            val (state, latency, peak, guilds, reconnects) = queries
            (state.keys + latency.keys + peak.keys + guilds.keys).distinct().mapNotNull { id ->
                val shard = id.toIntOrNull() ?: return@mapNotNull null
                ShardRow(
                    shard = shard,
                    connected = state[id]?.let { it >= 1 },
                    latency = latency[id],
                    peak = peak[id],
                    guilds = guilds[id],
                    reconnects = reconnects[id],
                )
            }
        }
    }
    val columns = remember {
        listOf(
            TableColumn<ShardRow>("Shard", 60.dp, numeric = true, sort = { it.shard }) { it.shard.toString() },
            TableColumn(
                "State",
                110.dp,
                sort = { row -> row.connected?.let { if (it) 1 else 0 } ?: -1 },
                tone = { row -> row.connected?.let { if (it) Tone.Ok else Tone.Crit } ?: Tone.Muted },
            ) { row -> row.connected?.let { if (it) "connected" else "down" } ?: "unknown" },
            TableColumn("Latency", 80.dp, numeric = true, sort = { it.latency }) { fmtMs(it.latency) },
            TableColumn(
                "Peak",
                90.dp,
                numeric = true,
                sort = { it.peak },
                tone = { row ->
                    val peak = row.peak
                    when {
                        peak == null -> null
                        peak >= 1000 -> Tone.Crit
                        peak >= 400 -> Tone.Warn
                        else -> null
                    }
                },
            ) { fmtMs(it.peak) },
            TableColumn("Guilds", 72.dp, numeric = true, sort = { it.guilds }) { fmtInt(it.guilds) },
            TableColumn("Reconnects", 90.dp, numeric = true, sort = { it.reconnects }) { fmtInt(it.reconnects) },
        )
    }
    DataTable(
        columns = columns,
        rows = rows.data.orEmpty(),
        loading = rows.loading,
        empty = "No shards reporting",
        sortIndex = 0,
        sortDescending = false,
        pageSize = 24,
    )
}

@Composable
private fun ServersTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val colors = rememberAnalyticsPalette()
    val churn = rememberLoad(filters, env.tick) { env.vm.growthChurn(filters.queryParams(HonoursBotShard)) }
    val snapshots = rememberLoad(filters, env.tick) { env.vm.serverSnapshots(30, filters.botParam()) }
    val retention = rememberLoad(filters, env.tick) {
        val params = filters.botParam() + listOfNotNull(filters.shard.takeIf { it.isNotEmpty() }?.let { "shard" to it })
        env.vm.growthRetention(30, params)
    }
    val bounced = rememberLoad(filters, env.tick) { env.vm.growthBounced(filters.timeAndBot(), 50) }
    val silent = rememberLoad(filters, env.tick) { env.vm.growthSilent(filters.botParam(), 50) }
    val overview = rememberLoad(filters, env.tick) { env.vm.serverOverview(filters.timeAndBot()) }
    var search by rememberSaveable { mutableStateOf("") }
    var drillGuild by remember { mutableStateOf<String?>(null) }

    val churnData = churn.data
    TileGrid(
        { MetricTile("Servers", "guild.count", "last") },
        { MetricTile("Users", "user.count", "last", ValueFormat.Compact, HonoursBot) },
        { StatBox("Joined", fmtInt(churnData?.joins), churn.loading) },
        { StatBox("Left", fmtInt(churnData?.leaves), churn.loading) },
        {
            StatBox(
                label = "Net",
                value = churnData?.let { (if (it.net >= 0) "+" else "") + fmtInt(it.net) } ?: Missing,
                loading = churn.loading,
                tone = churnData?.let { if (it.net >= 0) Tone.Ok else Tone.Crit },
            )
        },
    )

    val byDay = remember(snapshots.data) {
        snapshots.data.orEmpty()
            .groupBy { it.day.take(10) }
            .map { (day, rows) -> Triple(day, rows.sumOf { it.guilds }, rows.sumOf { it.users }) }
            .sortedBy { it.first }
    }
    CardColumns(
        {
            AnCard(title = "Servers · daily snapshots", note = "30d") {
                SimpleChart(
                    labels = byDay.map { it.first.drop(5) },
                    lines = listOf(ChartLine("Servers", colors.primary, byDay.map { it.second })),
                    loading = snapshots.loading,
                    empty = "No snapshots yet",
                    height = 200.dp,
                )
            }
        },
        {
            AnCard(title = "Users · daily snapshots", note = "30d") {
                SimpleChart(
                    labels = byDay.map { it.first.drop(5) },
                    lines = listOf(ChartLine("Users", colors.primary, byDay.map { it.third })),
                    loading = snapshots.loading,
                    empty = "No snapshots yet",
                    height = 200.dp,
                )
            }
        },
        {
            AnCard(title = "Joins and leaves per day") {
                val days = churnData?.days.orEmpty()
                SimpleChart(
                    labels = days.map { dayLabel(it.day) },
                    lines = listOf(
                        ChartLine("Joins", colors.ok, days.map { it.joins }),
                        ChartLine("Leaves", colors.crit, days.map { it.leaves }),
                    ),
                    loading = churn.loading,
                    kind = ChartKind.Bar,
                    beginAtZero = true,
                )
            }
        },
        {
            AnCard(title = "Churn") {
                when {
                    churn.loading && churnData == null -> Skeleton(Modifier.fillMaxWidth())
                    churnData == null -> MutedText("No data")
                    else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MutedText(
                            "bounced ${fmtInt(churnData.bounced)} · bounce rate ${fmtPct((churnData.bounceRate ?: 0.0) * 100)}",
                            mono = true,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Overline("Joins by size")
                            BreakdownBars(churnData.joinsBySize, top = 6, color = colors.ok)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Overline("Leaves by size")
                            BreakdownBars(churnData.leavesBySize, top = 6, color = colors.crit)
                        }
                    }
                }
            }
        },
        {
            AnCard(title = "Retention by join day", note = "still present · 30d") {
                val points = retention.data.orEmpty()
                SimpleChart(
                    labels = points.map { dayLabel(it.day) },
                    lines = listOf(ChartLine("Retained %", colors.primary, points.map { p -> p.rate?.let { it * 100 } })),
                    loading = retention.loading,
                    unit = "%",
                    beginAtZero = true,
                    height = 200.dp,
                )
            }
        },
        {
            AnCard(title = "Joins by size") {
                SeriesChart("guild.join", groupBy = "size", kind = ChartKind.Bar, stack = true, unit = "guilds", height = 200.dp)
            }
        },
    )

    val allServers = overview.data.orEmpty()
    val shownServers = remember(allServers, search) {
        val q = search.trim().lowercase(Locale.US)
        if (q.isEmpty()) allServers else allServers.filter { it.name.lowercase(Locale.US).contains(q) || it.guildId.contains(q) }
    }
    val humans = allServers.sumOf { it.shape.humans }
    val bots = allServers.sumOf { it.shape.bots }
    val configured = allServers.count { it.featuresConfigured > 0 }
    val overviewColumns = remember {
        listOf(
            TableColumn<GuildOverviewRow>("Server", 170.dp, detail = { "${it.name} (${it.guildId})" }) { it.name },
            TableColumn("Members", 80.dp, numeric = true, sort = { it.shape.memberCount }) { fmtInt(it.shape.memberCount) },
            TableColumn("Humans", 80.dp, numeric = true, sort = { it.shape.humans }) { fmtInt(it.shape.humans) },
            TableColumn(
                "Bots",
                110.dp,
                numeric = true,
                sort = { it.shape.bots },
                tone = { row ->
                    val ratio = if (row.shape.memberCount > 0) row.shape.bots / row.shape.memberCount else 0.0
                    if (ratio >= 0.5) Tone.Crit else if (ratio >= 0.25) Tone.Warn else null
                },
            ) { row ->
                val share = if (row.shape.memberCount > 0) row.shape.bots / row.shape.memberCount * 100 else 0.0
                "${fmtInt(row.shape.bots)} · ${fmtPct(share, 0)}"
            },
            TableColumn("Online", 72.dp, numeric = true, sort = { it.shape.online }) { fmtInt(it.shape.online) },
            TableColumn("Boosts", 80.dp, numeric = true, sort = { it.shape.boosts }) { row ->
                if (row.shape.boosts > 0) "${fmtInt(row.shape.boosts)} · T${row.shape.boostTier}" else Missing
            },
            TableColumn("Commands", 84.dp, numeric = true, sort = { it.commands }) { fmtCompact(it.commands) },
            TableColumn("Events", 76.dp, numeric = true, sort = { it.events }) { fmtCompact(it.events) },
            TableColumn(
                "Features",
                140.dp,
                numeric = true,
                sort = { it.featuresUsed },
                detail = { row -> row.features.joinToString(", ").ifEmpty { "none used in range" } },
            ) { "${it.featuresUsed} used · ${it.featuresEnabled}/${it.featuresConfigured} on" },
            TableColumn("Shard", 56.dp, numeric = true, muted = true, sort = { it.shard }) { it.shard.toString() },
            TableColumn("Joined", 90.dp, muted = true, sort = { utcParse(it.joinedAt) }) { row ->
                row.joinedAt?.let { ago(it) } ?: Missing
            },
        )
    }
    AnCard(title = "Server overview", note = "${fmtInt(shownServers.size.toDouble())} of ${fmtInt(allServers.size.toDouble())} · ${fmtCompact(humans)} humans · " +
            "${fmtCompact(bots)} bots · ${fmtInt(configured.toDouble())} configured · row → card") {
        dev.mewdeko.mobile.core.ui.SearchField(value = search, onValueChange = { search = it }, placeholder = "name or id")
        DataTable(
            columns = overviewColumns,
            rows = shownServers,
            loading = overview.loading,
            empty = "No servers",
            sortIndex = 1,
            pageSize = 25,
            onRowClick = { drillGuild = it.guildId },
        )
    }

    val bouncedColumns = remember {
        listOf(
            TableColumn<BouncedGuildRow>("Guild", 160.dp, mono = true) { it.guildId },
            TableColumn("First seen", 128.dp, muted = true, sort = { utcParse(it.firstSeen) }) { stamp(it.firstSeen) },
            TableColumn("Last seen", 128.dp, muted = true, sort = { utcParse(it.lastSeen) }) { stamp(it.lastSeen) },
            TableColumn("Stayed", 72.dp, numeric = true, sort = { stayedSeconds(it) }) { fmtDuration(stayedSeconds(it)) },
            TableColumn("Events", 64.dp, numeric = true, sort = { it.events }) { fmtInt(it.events) },
        )
    }
    val silentColumns = remember {
        listOf(
            TableColumn<SilentGuildRow>("Guild", 160.dp, mono = true) { it.guildId },
            TableColumn("Name", 150.dp) { it.name ?: Missing },
            TableColumn("Members", 76.dp, numeric = true, sort = { it.memberCount }) { fmtInt(it.memberCount) },
            TableColumn("Last active", 90.dp, muted = true, sort = { utcParse(it.lastActive) }) { ago(it.lastActive) },
            TableColumn("Prior events", 90.dp, numeric = true, sort = { it.events }) { fmtInt(it.events) },
        )
    }
    CardColumns(
        {
            AnCard(title = "Bounced guilds", note = "row → card") {
                DataTable(
                    columns = bouncedColumns,
                    rows = bounced.data.orEmpty(),
                    loading = bounced.loading,
                    empty = "No bounces",
                    sortIndex = 2,
                    pageSize = 10,
                    onRowClick = { drillGuild = it.guildId },
                )
            }
        },
        {
            AnCard(title = "Went silent", note = "row → card") {
                DataTable(
                    columns = silentColumns,
                    rows = silent.data.orEmpty(),
                    loading = silent.loading,
                    empty = "Nothing went quiet",
                    sortIndex = 4,
                    pageSize = 10,
                    onRowClick = { drillGuild = it.guildId },
                )
            }
        },
    )

    drillGuild?.let { id -> GuildCardSheet(id) { drillGuild = null } }
}

/** How long a bounced guild kept the bot, in seconds. */
private fun stayedSeconds(row: BouncedGuildRow): Double? {
    val first = utcParse(row.firstSeen) ?: return null
    val last = utcParse(row.lastSeen) ?: return null
    return (last.toEpochMilli() - first.toEpochMilli()) / 1000.0
}

@Composable
private fun GuildsTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val selected = filters.guild
    var eventType by rememberSaveable { mutableStateOf("") }
    var minCountText by rememberSaveable { mutableStateOf("50") }
    val minCount = max(1, minCountText.toIntOrNull() ?: 50)
    var guildInput by remember(selected) { mutableStateOf(selected) }
    val eventTypes = registryValues(env.registry, "ev.count", "type")
    val detailRequester = remember { BringIntoViewRequester() }

    val top = rememberLoad(filters, env.tick, eventType) { env.vm.topGuilds(filters.timeAndBot(), eventType, 25) }
    val anomalies = rememberLoad(filters, env.tick, minCount) { env.vm.guildAnomalies(filters.timeAndBot(), minCount, 50) }

    fun select(id: String) = env.vm.updateFilters { it.copy(guild = id) }

    LaunchedEffect(selected) {
        if (selected.isNotEmpty()) detailRequester.bringIntoView()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        ChoiceSelect(
            "Event type",
            "any type",
            eventTypes.map { it to it },
            eventType,
            { eventType = it },
            Modifier.weight(1f),
            Icons.Default.Bolt,
        )
        MewdekoTextField(
            value = minCountText,
            onValueChange = { minCountText = it.filter(Char::isDigit).take(6) },
            label = "min / h",
            numeric = true,
            modifier = Modifier.width(110.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = guildInput,
            onValueChange = { guildInput = it.filter(Char::isDigit) },
            label = { Text("Guild id") },
            singleLine = true,
            textStyle = MonospaceStyle.copy(fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                val id = guildInput.trim()
                if (id.isEmpty() || isGuildId(id)) select(id)
            }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = {
                val id = guildInput.trim()
                if (id.isEmpty() || isGuildId(id)) select(id)
            },
        ) { Text("Open") }
        if (selected.isNotEmpty()) {
            IconButton(onClick = { select("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear guild")
            }
        }
    }

    if (selected.isNotEmpty()) {
        Column(
            modifier = Modifier.bringIntoViewRequester(detailRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GuildDetail(selected)
        }
    }

    val topColumns = remember {
        listOf(
            TableColumn<TopGuildRow>("Guild", 160.dp, mono = true) { it.guildId },
            TableColumn("Name", 150.dp) { it.name ?: Missing },
            TableColumn("Members", 76.dp, numeric = true, sort = { it.memberCount }) { fmtInt(it.memberCount) },
            TableColumn("Events", 72.dp, numeric = true, sort = { it.events }) { fmtCompact(it.events) },
            TableColumn(
                "Top types",
                240.dp,
                muted = true,
                sort = { null },
                detail = { row -> row.topTypes.joinToString("\n") { "${it.type} ${fmtCompact(it.count)}" } },
            ) { row -> row.topTypes.joinToString(" · ") { "${it.type} ${fmtCompact(it.count)}" }.ifEmpty { Missing } },
        )
    }
    val anomalyColumns = remember {
        listOf(
            TableColumn<GuildAnomalyRow>("Guild", 160.dp, mono = true) { it.guildId },
            TableColumn("Name", 140.dp) { it.name ?: Missing },
            TableColumn("Type", 150.dp, mono = true) { it.eventType },
            TableColumn("Hour (UTC)", 128.dp, muted = true, sort = { utcParse(it.hour) }) { stamp(it.hour) },
            TableColumn("Count", 64.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("Usual", 64.dp, numeric = true, sort = { it.mean }) { fmtCompact(it.mean) },
            TableColumn(
                "z",
                64.dp,
                numeric = true,
                sort = { it.z },
                tone = { if (it.z >= 6) Tone.Crit else if (it.z >= 3) Tone.Warn else null },
            ) { String.format(Locale.US, "%.1f", it.z) },
        )
    }
    AnCard(title = "Top guilds by events", note = "row → select") {
        DataTable(
            columns = topColumns,
            rows = top.data.orEmpty(),
            loading = top.loading,
            sortIndex = 3,
            pageSize = 15,
            onRowClick = { select(it.guildId) },
            isSelected = { it.guildId == selected },
        )
    }
    AnCard(title = "Unusual activity", note = "vs same hour, previous 14 days · row → select") {
        DataTable(
            columns = anomalyColumns,
            rows = anomalies.data.orEmpty(),
            loading = anomalies.loading,
            empty = "Nothing unusual",
            sortIndex = 6,
            pageSize = 15,
            onRowClick = { select(it.guildId) },
            isSelected = { it.guildId == selected },
        )
    }
}

@Composable
private fun GuildDetail(guildId: String) {
    val env = LocalAnalytics.current
    val filters = env.filters
    val timeline = rememberLoad(guildId, filters, env.tick) { env.vm.guildTimeline(guildId, filters.timeAndBot()) }
    var eventType by remember(guildId) { mutableStateOf("") }
    var eventPage by remember(guildId, eventType, filters) { mutableIntStateOf(1) }
    val events = rememberLoad(guildId, filters, env.tick, eventPage, eventType) {
        env.vm.guildEvents(guildId, filters.timeParams(), eventType, eventPage, 25)
    }
    val data = timeline.data
    val hours = remember(data) {
        data?.cells.orEmpty().map { it.hour }.distinct().sortedBy { utcParse(it)?.toEpochMilli() ?: 0L }
    }
    val cells = remember(data) { data?.cells.orEmpty().associate { "${it.type}|${it.hour}" to it.count } }

    AnCard(title = "Guild card") { GuildCardView(guildId) }
    AnCard(title = "Activity by type and hour", note = "UTC") {
        when {
            timeline.loading && data?.cells.isNullOrEmpty() -> Skeleton(Modifier.fillMaxWidth(), height = 120.dp)
            data == null || data.types.isEmpty() || hours.isEmpty() -> MutedText("No activity in range", center = true)
            else -> MatrixHeat(
                rows = data.types,
                cols = hours,
                colLabel = { hourLabel(hours[it]) },
                value = { type, hour -> cells["$type|$hour"] ?: 0.0 },
                unit = "events",
                describe = { type, hour, v -> "$type · ${hourLabel(hour)} · ${fmtInt(v)} events" },
                cellWidth = 16.dp,
                cellHeight = 18.dp,
                rowLabelWidth = 120.dp,
                monoRows = true,
            )
        }
    }
    val eventColumns = remember {
        listOf(
            TableColumn<GuildEventRow>("When (UTC)", 128.dp, muted = true, sort = { utcParse(it.at) }) { stamp(it.at) },
            TableColumn("Type", 170.dp, mono = true) { it.eventType },
            TableColumn("Bot", 150.dp, mono = true, muted = true) { it.bot },
            TableColumn("Shard", 56.dp, numeric = true, sort = { it.shard }) { it.shard?.toString() ?: Missing },
        )
    }
    AnCard(title = "Security events") {
        ChoiceSelect(
            "Event type",
            "any type",
            data?.types.orEmpty().distinct().map { it to it },
            eventType,
            { eventType = it },
            icon = Icons.Default.Bolt,
        )
        val page = events.data
        DataTable(
            columns = eventColumns,
            rows = page?.items.orEmpty(),
            loading = events.loading,
            empty = "No logged events",
            sortIndex = 0,
            pageSize = 25,
            serverPage = page?.page ?: eventPage,
            serverTotal = page?.total ?: 0L,
            onPage = { eventPage = it },
        )
    }
}

/** `M/D HHh` for a timeline hour. */
private fun hourLabel(hour: String): String {
    val instant = utcParse(hour) ?: return hour
    val date = instant.atZone(java.time.ZoneOffset.UTC)
    return String.format(Locale.US, "%d/%d %02dh", date.monthValue, date.dayOfMonth, date.hour)
}

@Composable
private fun FeaturesTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val colors = rememberAnalyticsPalette()
    val adoption = rememberLoad(filters, env.tick) { env.vm.featureAdoption(filters.timeAndBot()) }
    val depth = rememberLoad(filters, env.tick) { env.vm.featureDepth(filters.timeAndBot()) }
    var feature by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(adoption.data) {
        if (feature.isEmpty()) adoption.data?.maxByOrNull { it.activity }?.let { feature = it.feature }
    }

    val columns = remember {
        listOf(
            TableColumn<FeatureAdoptionRow>("Feature", 150.dp, mono = true) { it.feature },
            TableColumn("Configured", 84.dp, numeric = true, sort = { it.configured }) { fmtInt(it.configured) },
            TableColumn("Enabled", 72.dp, numeric = true, sort = { it.enabled }) { fmtInt(it.enabled) },
            TableColumn("Enabled %", 80.dp, numeric = true, muted = true, sort = { ratio(it.enabled, it.configured) }) {
                fmtPct(ratio(it.enabled, it.configured))
            },
            TableColumn("Active", 64.dp, numeric = true, sort = { it.activeGuilds }) { fmtInt(it.activeGuilds) },
            TableColumn("Active %", 76.dp, numeric = true, muted = true, sort = { ratio(it.activeGuilds, it.enabled ?: it.configured) }) {
                fmtPct(ratio(it.activeGuilds, it.enabled ?: it.configured))
            },
            TableColumn("Activity", 76.dp, numeric = true, sort = { it.activity }) { fmtCompact(it.activity) },
            TableColumn("Errors", 64.dp, numeric = true, sort = { it.errors }) { fmtInt(it.errors) },
            TableColumn(
                "Err %",
                76.dp,
                numeric = true,
                sort = { if (it.activity > 0) it.errors / it.activity else 0.0 },
                tone = { row ->
                    val rate = if (row.activity > 0) row.errors / row.activity else 0.0
                    if (rate >= 0.1) Tone.Crit else if (rate >= 0.02) Tone.Warn else null
                },
            ) { row -> fmtPct(if (row.activity > 0) row.errors / row.activity * 100 else 0.0) },
        )
    }

    AnCard(title = "Feature adoption", note = "configured · enabled from nightly census · active in range · row → settings") {
        DataTable(
            columns = columns,
            rows = adoption.data.orEmpty(),
            loading = adoption.loading,
            sortIndex = 4,
            onRowClick = { feature = it.feature },
            isSelected = { it.feature == feature },
        )
    }

    val histogram = depth.data?.histogram.orEmpty()
    CardColumns(
        {
            AnCard(title = "Features per guild", note = "distinct features used in range") {
                SimpleChart(
                    labels = histogram.map { it.features.toString() },
                    lines = listOf(ChartLine("Guilds", colors.primary, histogram.map { it.guilds })),
                    loading = depth.loading,
                    kind = ChartKind.Bar,
                    unit = "guilds",
                    beginAtZero = true,
                )
            }
        },
        {
            AnCard(title = "Size vs features", note = "members (log) × features used") {
                ScatterChart(depth.data?.points.orEmpty(), depth.loading)
            }
        },
    )

    FeatureCensus(feature, adoption.data.orEmpty().map { it.feature }.sorted()) { feature = it }
}

/** `a / b` as a percentage, or `null` when either is missing or `b` is zero. */
private fun ratio(a: Double?, b: Double?): Double? =
    if (a == null || b == null || b == 0.0) null else a / b * 100

@Composable
private fun FeatureCensus(feature: String, features: List<String>, onFeature: (String) -> Unit) {
    val env = LocalAnalytics.current
    val colors = rememberAnalyticsPalette()
    var kind by rememberSaveable { mutableStateOf("configured") }
    var days by rememberSaveable { mutableIntStateOf(90) }
    val censusMetric = if (feature.isEmpty()) "" else "feature.$feature.$kind"
    val settings = rememberLoad(feature, env.tick) {
        if (feature.isEmpty()) emptyList() else env.vm.featureSettings(feature)
    }
    val census = rememberLoad(censusMetric, days, env.tick) {
        if (censusMetric.isEmpty()) emptyList() else env.vm.featureCensus(censusMetric, days)
    }
    val groups = remember(settings.data) {
        settings.data.orEmpty()
            .groupBy { "${it.table}.${it.column}" }
            .map { (key, rows) -> key to rows.map { BreakdownRow(it.value, it.count) } }
    }

    CardColumns(
        {
            AnCard(title = "Settings usage", note = "latest census") {
                ChoiceSelect(
                    "Feature",
                    null,
                    features.map { it to it },
                    feature,
                    onFeature,
                    icon = Icons.Default.Extension,
                )
                when {
                    feature.isEmpty() -> MutedText("Pick a feature", center = true)
                    settings.loading && groups.isEmpty() -> Skeleton(Modifier.fillMaxWidth())
                    groups.isEmpty() -> MutedText("No settings census for $feature", center = true)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        groups.forEach { (key, rows) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MutedText(key, mono = true)
                                BreakdownBars(rows, top = 8)
                            }
                        }
                    }
                }
            }
        },
        {
            AnCard(title = "Census", note = censusMetric.ifEmpty { "pick a feature" }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceSelect(
                        "Kind",
                        null,
                        listOf("configured" to "configured", "enabled" to "enabled"),
                        kind,
                        { if (it.isNotEmpty()) kind = it },
                        Modifier.weight(1f),
                    )
                    ChoiceSelect(
                        "Days",
                        null,
                        listOf("90" to "90d", "180" to "180d", "365" to "365d"),
                        days.toString(),
                        { value -> value.toIntOrNull()?.let { days = it } },
                        Modifier.weight(1f),
                    )
                }
                val rows = census.data.orEmpty()
                SimpleChart(
                    labels = rows.map { dayLabel(it.day) },
                    lines = listOf(ChartLine(censusMetric, colors.primary, rows.map { it.value })),
                    loading = census.loading,
                    empty = "No census rows",
                    unit = "guilds",
                    beginAtZero = true,
                )
            }
        },
    )
}

@Composable
private fun AiTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val summary = rememberLoad(filters, env.tick) { env.vm.aiSummary(filters.queryParams(HonoursBot)) }
    val guilds = rememberLoad(filters, env.tick) { env.vm.aiGuilds(filters.timeAndBot(), 25) }
    val data = summary.data

    TileGrid(
        { StatBox("Requests", fmtInt(data?.requests), summary.loading) },
        { StatBox("Tokens in", fmtCompact(data?.tokensIn), summary.loading) },
        { StatBox("Tokens out", fmtCompact(data?.tokensOut), summary.loading) },
        { StatBox("Est. cost", fmtUsd(data?.costUsd), summary.loading, sub = "list prices · priced models only") },
    )

    val modelColumns = remember {
        listOf(
            TableColumn<AiModelRow>("Model", 170.dp, mono = true) { it.model },
            TableColumn("Provider", 100.dp, muted = true) { it.provider ?: Missing },
            TableColumn("Requests", 80.dp, numeric = true, sort = { it.requests }) { fmtInt(it.requests) },
            TableColumn(
                "Failed",
                72.dp,
                numeric = true,
                sort = { if (it.requests > 0) it.failures / it.requests else 0.0 },
                tone = { row ->
                    val rate = if (row.requests > 0) row.failures / row.requests else 0.0
                    if (rate >= 0.1) Tone.Crit else if (row.failures > 0) Tone.Warn else null
                },
            ) { row -> fmtPct(if (row.requests > 0) row.failures / row.requests * 100 else 0.0) },
            TableColumn("Tokens in", 80.dp, numeric = true, sort = { it.tokensIn }) { fmtCompact(it.tokensIn) },
            TableColumn("Tokens out", 84.dp, numeric = true, sort = { it.tokensOut }) { fmtCompact(it.tokensOut) },
            TableColumn("p95", 80.dp, numeric = true, sort = { it.p95Ms }) { fmtMs(it.p95Ms) },
            TableColumn("Cost", 72.dp, numeric = true, sort = { it.costUsd }) { fmtUsd(it.costUsd) },
        )
    }
    AnCard(title = "By model") {
        DataTable(modelColumns, data?.models.orEmpty(), summary.loading, empty = "No AI requests", sortIndex = 2)
    }
    CardColumns(
        {
            AnCard(title = "Requests by model") {
                SeriesChart(
                    "ai.requests",
                    groupBy = "model",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Bar,
                    stack = true,
                    unit = "requests",
                )
            }
        },
        {
            AnCard(title = "Tokens by model", note = "in + out") {
                SeriesChart(
                    "ai.tokens",
                    groupBy = "model",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Area,
                    unit = "tokens",
                    totalToggle = true,
                )
            }
        },
        {
            AnCard(title = "Duration p95 by model") {
                SeriesChart("ai.duration", "p95", groupBy = "model", maxSeries = 8, honours = HonoursBot, unit = "ms", noCompare = true)
            }
        },
    )
    val guildColumns = remember {
        listOf(
            TableColumn<AiGuildRow>("Guild", 160.dp, mono = true) { it.guildId },
            TableColumn("Name", 150.dp) { it.name ?: Missing },
            TableColumn("Uses", 64.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("Errors", 64.dp, numeric = true, sort = { it.errors }) { fmtInt(it.errors) },
            TableColumn("Err %", 70.dp, numeric = true, muted = true, sort = { if (it.count > 0) it.errors / it.count else 0.0 }) { row ->
                fmtPct(if (row.count > 0) row.errors / row.count * 100 else 0.0)
            },
        )
    }
    AnCard(title = "Top guilds", note = "ai_chat feature use") {
        DataTable(guildColumns, guilds.data.orEmpty(), guilds.loading, empty = "No guild use", sortIndex = 2, pageSize = 15)
    }
}

@Composable
private fun MusicTab() {
    TileGrid(
        { MetricTile("Players", "music.players", "last", honours = HonoursBot) },
        { MetricTile("Tracks started", "music.track_start", format = ValueFormat.Compact, honours = HonoursBot) },
        { MetricTile("Errors", "music.errors", honours = HonoursBot, critSpark = true) },
    )
    CardColumns(
        {
            AnCard(title = "Players by node") {
                SeriesChart("music.players", "last", groupBy = "node", honours = HonoursBot, kind = ChartKind.Area, unit = "players")
            }
        },
        {
            AnCard(title = "Tracks started", note = "per bucket · by source") {
                SeriesChart(
                    "music.track_start",
                    groupBy = "source",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Area,
                    unit = "tracks",
                    totalToggle = true,
                )
            }
        },
        {
            AnCard(title = "Errors by kind") {
                SeriesChart(
                    "music.errors",
                    groupBy = "kind",
                    maxSeries = 8,
                    honours = HonoursBot,
                    kind = ChartKind.Bar,
                    stack = true,
                    unit = "errors",
                )
            }
        },
        {
            AnCard(title = "Node state", note = "1 connected · 0 down") {
                SeriesChart("music.node_state", "last", groupBy = "node", honours = HonoursBot, noCompare = true)
            }
        },
        {
            AnCard(title = "Tracks ended by reason") {
                BreakdownPanel("music.track_end", "reason", HonoursBot, top = 10)
            }
        },
    )
}

@Composable
private fun WebsiteTab() {
    val env = LocalAnalytics.current
    val filters = env.filters
    val colors = rememberAnalyticsPalette()
    val routes = rememberLoad(filters, env.tick) { env.vm.websiteRoutes(filters.timeParams(), 50) }
    val errors = rememberLoad(filters, env.tick) { env.vm.websiteErrors(filters.timeParams(), 50) }
    val funnel = rememberLoad(filters, env.tick) { env.vm.websiteFunnel(filters.timeParams()) }
    val visitors = routes.data.orEmpty().sumOf { it.visitors }

    TileGrid(
        { MetricTile("Page views", "page.view", honours = HonoursNothing) },
        { StatBox("Visitors", fmtCompact(visitors), routes.loading, sub = "sum over routes") },
        { MetricTile("Request p95", "page.duration", "p95", ValueFormat.Ms, HonoursNothing, unit = "ms") },
        { MetricTile("5xx", "page.view", honours = HonoursNothing, fixed = mapOf("status" to "5xx"), critSpark = true) },
    )
    CardColumns(
        {
            AnCard(title = "Views by route", note = "top routes, rest folded") {
                SeriesChart(
                    "page.view",
                    groupBy = "route",
                    maxSeries = 8,
                    honours = HonoursNothing,
                    kind = ChartKind.Area,
                    unit = "views",
                    totalToggle = true,
                )
            }
        },
        {
            AnCard(title = "Request duration", note = "p50 · p95 · p99") {
                SeriesChart("page.duration", aggs = listOf("p50", "p95", "p99"), honours = HonoursNothing, unit = "ms", noCompare = true)
            }
        },
    )
    val routeColumns = remember {
        listOf(
            TableColumn<RouteRow>("Route", 200.dp, mono = true) { it.route },
            TableColumn("Views", 72.dp, numeric = true, sort = { it.views }) { fmtCompact(it.views) },
            TableColumn("Visitors", 76.dp, numeric = true, sort = { it.visitors }) { fmtCompact(it.visitors) },
            TableColumn("p95", 80.dp, numeric = true, sort = { it.p95Ms }) { fmtMs(it.p95Ms) },
            TableColumn("5xx", 64.dp, numeric = true, sort = { it.errors }, tone = { if (it.errors > 0) Tone.Crit else null }) {
                fmtInt(it.errors)
            },
        )
    }
    val errorColumns = remember {
        listOf(
            TableColumn<RouteErrorRow>("Route", 200.dp, mono = true) { it.route },
            TableColumn("Status", 72.dp, sort = { it.status }, tone = { if (it.status >= 500) Tone.Crit else Tone.Warn }) {
                it.status.toString()
            },
            TableColumn("Count", 64.dp, numeric = true, sort = { it.count }) { fmtInt(it.count) },
            TableColumn("Last", 86.dp, numeric = true, muted = true, sort = { utcParse(it.lastSeen) }) { ago(it.lastSeen) },
        )
    }
    CardColumns(
        {
            AnCard(title = "Routes") {
                DataTable(routeColumns, routes.data.orEmpty(), routes.loading, empty = "No page views", sortIndex = 1, pageSize = 15)
            }
        },
        {
            AnCard(title = "Errors", note = "5xx responses") {
                DataTable(errorColumns, errors.data.orEmpty(), errors.loading, empty = "No errors", sortIndex = 2, pageSize = 15)
            }
        },
    )
    AnCard(title = "Login funnel", note = "distinct visitors · views in brackets") {
        val data = funnel.data
        when {
            funnel.loading && data == null -> Skeleton(Modifier.fillMaxWidth())
            data == null -> MutedText("No data")
            else -> {
                val steps = listOf(
                    Triple("Login", data.loginVisitors, data.loginViews),
                    Triple("OAuth callback", data.callbackVisitors, data.callbackViews),
                    Triple("Dashboard", data.dashboardVisitors, data.dashboardViews),
                )
                val peak = max(1.0, steps.maxOf { it.second })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.forEachIndexed { index, (label, stepVisitors, views) ->
                        val previous = steps.getOrNull(index - 1)?.second
                        val drop = if (index == 0 || previous == null || previous == 0.0) null else (1 - stepVisitors / previous) * 100
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(92.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.grid),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(max(0.02, stepVisitors / peak).toFloat())
                                        .fillMaxHeight()
                                        .background(colors.primary, RoundedCornerShape(6.dp)),
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 64.dp)) {
                                Text(
                                    "${fmtInt(stepVisitors)} (${fmtCompact(views)})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                )
                                if (drop != null) {
                                    Text(
                                        "-${fmtPct(drop, 0)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (drop > 50) colors.crit else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertsTab() {
    val env = LocalAnalytics.current
    val alerts by env.vm.alerts.collectAsStateWithLifecycle()
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AlertRule?>(null) }
    var pendingDelete by remember { mutableStateOf<AlertRule?>(null) }
    var eventRule by rememberSaveable { mutableStateOf("") }
    var eventPage by remember(eventRule) { mutableIntStateOf(1) }
    val ruleId = eventRule.toIntOrNull()
    val events = rememberLoad(env.tick, eventPage, ruleId) { env.vm.alertEvents(eventPage, 25, ruleId) }

    LaunchedEffect(env.filters, env.tick) { env.vm.loadAlerts() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = {
                editing = null
                editorOpen = true
            },
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("New rule")
        }
        OutlinedButton(onClick = env.vm::sendDigest, enabled = !alerts.digestBusy) {
            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (alerts.digestBusy) "Sending…" else "Send digest now")
        }
    }
    MutedText("${alerts.rules.size} rules · ${alerts.rules.count { it.enabled }} enabled")

    AnCard(title = "Firing") {
        FiringCards(alerts.firing, failed = false, loading = alerts.rulesLoading, showMetric = true)
    }

    AnCard(title = "Rules") {
        when {
            alerts.rulesLoading && alerts.rules.isEmpty() -> SkeletonRows(3)
            alerts.rules.isEmpty() -> MutedText("No rules yet", center = true)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                alerts.rules.forEach { rule ->
                    RuleRow(
                        rule = rule,
                        busy = alerts.busy == rule.id,
                        onToggle = { env.vm.toggleRule(rule) },
                        onEdit = {
                            editing = rule
                            editorOpen = true
                        },
                        onTest = { env.vm.testRule(rule) },
                        onMute = { env.vm.muteRule(rule) },
                        onDelete = { pendingDelete = rule },
                    )
                }
            }
        }
    }

    val eventColumns = remember {
        listOf(
            TableColumn<AlertEvent>("When (UTC)", 128.dp, muted = true, sort = { utcParse(it.at) }) { stamp(it.at) },
            TableColumn("Rule", 150.dp) { it.ruleName },
            TableColumn("Severity", 90.dp, tone = { severityTone(it.severity) }) { it.severity },
            TableColumn("Group", 120.dp, mono = true) { it.groupKey.ifEmpty { "fleet" } },
            TableColumn(
                "Transition",
                150.dp,
                tone = { row ->
                    when (row.toState) {
                        "firing" -> Tone.Crit
                        "pending" -> Tone.Warn
                        "resolved", "ok" -> Tone.Ok
                        else -> Tone.Muted
                    }
                },
            ) { "${it.fromState} → ${it.toState}" },
            TableColumn("Value", 120.dp, numeric = true, sort = { it.value }) { "${fmtCompact(it.value)} vs ${fmtCompact(it.threshold)}" },
            TableColumn("Notified", 72.dp, tone = { if (it.notified) Tone.Ok else Tone.Muted }) { if (it.notified) "yes" else "no" },
        )
    }
    AnCard(title = "Events", note = "newest first") {
        ChoiceSelect(
            "Rule",
            "all rules",
            alerts.rules.map { it.id.toString() to it.name },
            eventRule,
            { eventRule = it },
            icon = Icons.Default.Notifications,
        )
        val page = events.data
        DataTable(
            columns = eventColumns,
            rows = page?.items.orEmpty(),
            loading = events.loading,
            empty = "No transitions yet",
            sortIndex = 0,
            pageSize = 25,
            serverPage = page?.page ?: eventPage,
            serverTotal = page?.total ?: 0L,
            onPage = { eventPage = it },
        )
    }

    if (editorOpen) {
        AlertRuleEditor(
            rule = editing,
            registry = env.registry,
            onDismiss = { editorOpen = false },
            onSave = { input, done -> env.vm.saveRule(editing, input, done) },
        )
    }

    pendingDelete?.let { rule ->
        ConfirmDialog(
            title = "Delete rule",
            message = "Delete \"${rule.name}\" and its state history?",
            confirmLabel = "Delete",
            onConfirm = { env.vm.deleteRule(rule) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/**
 * One alert rule: the on switch, name with the muted pill and description,
 * the condition, severity and state pills with the firing groups, last fired
 * and the 30 day count, then edit, test, mute and delete.
 */
@Composable
private fun RuleRow(
    rule: AlertRule,
    busy: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onMute: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = rememberAnalyticsPalette()
    val shape = RoundedCornerShape(12.dp)
    val state = if (rule.enabled) rule.worstState() else "off"
    val stateTone = when {
        !rule.enabled -> Tone.Muted
        state == "firing" -> Tone.Crit
        state == "pending" -> Tone.Warn
        else -> Tone.Ok
    }
    val firingGroups = rule.states.filter { it.state == "firing" }.map { it.groupKey.ifEmpty { "fleet" } }
    val muted = rule.isMuted()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (rule.enabled) 1f else 0.6f)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08), shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex20), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() }, enabled = !busy)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(rule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (muted) TonePill("muted", Tone.Muted, icon = Icons.Default.NotificationsOff)
                }
                rule.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = describeRule(rule.toInput()) + (rule.groupBy?.let { " per $it" } ?: ""),
            style = MonospaceStyle.copy(fontSize = 12.sp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TonePill(rule.severity, severityTone(rule.severity))
            TonePill(state, stateTone)
            if (firingGroups.isNotEmpty()) MutedText(firingGroups.joinToString(", "), mono = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                MutedText("Last fired ${rule.lastFired()?.let { ago(it.toString()) } ?: Missing}")
                MutedText("${fmtInt(rule.firedLast30Days.toDouble())} fired in 30d")
            }
            IconButton(onClick = onEdit, enabled = !busy) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onTest, enabled = !busy) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send test")
            }
            IconButton(onClick = onMute, enabled = !busy) {
                Icon(
                    if (muted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = if (muted) "Unmute" else "Mute 1h",
                )
            }
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.crit)
            }
        }
    }
}

/** A decimal text field for rule thresholds. */
@Composable
private fun DecimalField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, placeholder: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == '.' || it == '-' }) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Renders a threshold for editing: whole numbers without a decimal point. */
private fun numberText(value: Double?): String = when {
    value == null -> ""
    value == floor(value) && abs(value) < 1e15 -> value.toLong().toString()
    else -> value.toString()
}

/** Duration choices for the rule editor pickers. */
private fun durationOptions(values: List<Int>, zero: String): List<EnumOption<Int>> =
    values.map { EnumOption(it, if (it == 0) zero else fmtDuration(it.toDouble())) }

/**
 * The full screen rule editor. The left column of the dashboard's editor
 * comes first (condition), then the live preview with the draft threshold,
 * then delivery. Closing never asks, as on the dashboard; a save failure
 * stays inline with the server's message.
 */
@Composable
private fun AlertRuleEditor(
    rule: AlertRule?,
    registry: List<MetricDescriptor>,
    onDismiss: () -> Unit,
    onSave: (AlertRuleInput, (String?) -> Unit) -> Unit,
) {
    val colors = rememberAnalyticsPalette()
    val initial = remember(rule) { rule?.toInput() ?: AlertRuleInput(metric = registry.firstOrNull()?.metric.orEmpty()) }
    var draft by remember(rule) { mutableStateOf(initial) }
    var filterRows by remember(rule) { mutableStateOf(initial.filters.map { it.key to it.value }) }
    var thresholdText by remember(rule) { mutableStateOf(numberText(initial.threshold)) }
    var highText by remember(rule) { mutableStateOf(numberText(initial.thresholdHigh)) }
    var minSamplesText by remember(rule) { mutableStateOf(initial.minSamples?.toString().orEmpty()) }
    var roleText by remember(rule) { mutableStateOf(initial.mentionRoleId.orEmpty()) }
    var threadText by remember(rule) { mutableStateOf(initial.threadId.orEmpty()) }
    var quietStart by remember(rule) { mutableStateOf(minutesToClock(initial.quietStartMinute)) }
    var quietEnd by remember(rule) { mutableStateOf(minutesToClock(initial.quietEndMinute)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val descriptor = registry.firstOrNull { it.metric == draft.metric }
    val labels = descriptor?.labels?.keys?.toList().orEmpty()
    val baseline = isBaselineComparator(draft.comparator)
    val absolute = !baseline && draft.comparator != "nodata"
    val outside = draft.comparator == "outside"
    val filterMap = filterRows.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()
    val previewBands = if (absolute && draft.metric.isNotEmpty()) {
        listOf(
            AlertBand(
                name = draft.name.ifEmpty { "draft" },
                threshold = thresholdText.toDoubleOrNull() ?: 0.0,
                thresholdHigh = if (outside) highText.toDoubleOrNull() else null,
                comparator = ComparatorSymbol[draft.comparator] ?: draft.comparator,
                severity = draft.severity,
            ),
        )
    } else {
        emptyList()
    }

    fun save() {
        val body = draft.copy(
            name = draft.name.trim(),
            description = draft.description?.trim()?.ifEmpty { null },
            filters = filterMap,
            groupBy = draft.groupBy?.ifEmpty { null },
            threshold = thresholdText.trim().toDoubleOrNull() ?: 0.0,
            thresholdHigh = if (outside) highText.trim().toDoubleOrNull() else null,
            baselineDays = if (baseline) draft.baselineDays ?: 7 else null,
            direction = if (baseline) draft.direction ?: "both" else null,
            repeatSeconds = draft.repeatSeconds?.takeIf { it > 0 },
            webhookUrl = draft.webhookUrl.trim(),
            mentionRoleId = roleText.trim().ifEmpty { null },
            threadId = threadText.trim().ifEmpty { null },
            quietStartMinute = quietStart.takeIf { it.isNotBlank() }?.let { clockToMinutes(it) },
            quietEndMinute = quietEnd.takeIf { it.isNotBlank() }?.let { clockToMinutes(it) },
            minSamples = minSamplesText.trim().toIntOrNull(),
        )
        error = when {
            body.name.isEmpty() -> "Name required"
            body.metric.isEmpty() -> "Metric required"
            body.webhookUrl.isEmpty() -> "Webhook required"
            else -> ""
        }
        if (error.isNotEmpty()) return
        saving = true
        onSave(body) { message ->
            saving = false
            if (message == null) onDismiss() else error = message
        }
    }

    FullScreenEditor(
        title = if (rule != null) "Edit · ${rule.name}" else "New rule",
        onClose = onDismiss,
        confirmLabel = when {
            saving -> "Saving…"
            rule != null -> "Save"
            else -> "Create"
        },
        confirmEnabled = !saving,
        onConfirm = ::save,
    ) {
        if (error.isNotEmpty()) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = colors.crit)
        }
        MewdekoTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, label = "Name")
        MewdekoTextField(
            value = draft.description.orEmpty(),
            onValueChange = { draft = draft.copy(description = it) },
            label = "Description",
        )
        EnumPicker(
            label = "Severity",
            options = listOf("info", "warning", "critical").map { EnumOption(it, it) },
            selected = draft.severity,
            onSelect = { draft = draft.copy(severity = it) },
        )
        SwitchRow(title = "Enabled", checked = draft.enabled, onCheckedChange = { draft = draft.copy(enabled = it) })

        val metricOptions = buildList {
            if (draft.metric.isNotEmpty() && registry.none { it.metric == draft.metric }) {
                add(SelectorOption(draft.metric, draft.metric))
            }
            registry.forEach { add(SelectorOption(it.metric, "${it.metric} · ${it.kind}")) }
        }
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ShowChart),
            options = metricOptions,
            placeholder = "Pick a metric",
            selectedId = draft.metric.ifEmpty { null },
            onSelect = { id ->
                val next = id.orEmpty()
                if (next != draft.metric) {
                    draft = draft.copy(metric = next, groupBy = null)
                    filterRows = emptyList()
                }
            },
            label = "Metric",
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { filterRows = filterRows + (labels.firstOrNull().orEmpty() to "") }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("add")
                }
            }
            filterRows.forEachIndexed { index, (key, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceSelect(
                            "Label",
                            null,
                            labels.map { it to it },
                            key,
                            { picked -> filterRows = filterRows.mapIndexed { i, row -> if (i == index) picked to row.second else row } },
                            Modifier.weight(0.45f),
                        )
                        Text("=", style = MaterialTheme.typography.titleMedium)
                        MewdekoTextField(
                            value = value,
                            onValueChange = { text ->
                                filterRows = filterRows.mapIndexed { i, row -> if (i == index) row.first to text else row }
                            },
                            label = "Value",
                            modifier = Modifier.weight(0.55f),
                        )
                        IconButton(onClick = { filterRows = filterRows.filterIndexed { i, _ -> i != index } }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove filter")
                        }
                    }
                    val samples = descriptor?.labels?.get(key).orEmpty().take(16)
                    if (samples.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            samples.forEach { sample ->
                                Surface(
                                    onClick = {
                                        filterRows = filterRows.mapIndexed { i, row -> if (i == index) row.first to sample else row }
                                    },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(
                                        alpha = if (sample == value) DashAlpha.Hex30 else DashAlpha.Hex10,
                                    ),
                                ) {
                                    Text(
                                        sample,
                                        style = MonospaceStyle.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ChoiceSelect(
            "Group by",
            "none",
            labels.map { it to it },
            draft.groupBy.orEmpty(),
            { draft = draft.copy(groupBy = it.ifEmpty { null }) },
        )
        EnumPicker(
            label = "Agg",
            options = AlertAggregations.map { EnumOption(it, it) },
            selected = draft.aggregation,
            onSelect = { draft = draft.copy(aggregation = it) },
        )
        EnumPicker(
            label = "Window",
            options = durationOptions(AlertWindows, "none"),
            selected = draft.windowSeconds,
            onSelect = { draft = draft.copy(windowSeconds = it) },
        )
        EnumPicker(
            label = "Comparator",
            options = AlertComparators.map { EnumOption(it, it) },
            selected = draft.comparator,
            onSelect = { draft = draft.copy(comparator = it) },
        )
        if (draft.comparator != "nodata") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = if (baseline) "Threshold %" else "Threshold",
                    modifier = Modifier.weight(1f),
                )
                if (outside) {
                    DecimalField(value = highText, onValueChange = { highText = it }, label = "High", modifier = Modifier.weight(1f))
                }
            }
        }
        if (baseline) {
            EnumPicker(
                label = "Baseline",
                options = listOf(EnumOption(1, "yesterday"), EnumOption(7, "last week")),
                selected = draft.baselineDays ?: 7,
                onSelect = { draft = draft.copy(baselineDays = it) },
            )
            EnumPicker(
                label = "Direction",
                options = listOf("both", "up", "down").map { EnumOption(it, it) },
                selected = draft.direction ?: "both",
                onSelect = { draft = draft.copy(direction = it) },
            )
        }
        EnumPicker(
            label = "For",
            options = durationOptions(AlertForSeconds, "immediately"),
            selected = draft.forSeconds,
            onSelect = { draft = draft.copy(forSeconds = it) },
        )
        EnumPicker(
            label = "Cooldown",
            options = durationOptions(AlertCooldowns, "none"),
            selected = draft.cooldownSeconds,
            onSelect = { draft = draft.copy(cooldownSeconds = it) },
        )
        EnumPicker(
            label = "Repeat",
            options = durationOptions(AlertRepeats, "once"),
            selected = draft.repeatSeconds ?: 0,
            onSelect = { draft = draft.copy(repeatSeconds = it.takeIf { seconds -> seconds > 0 }) },
        )
        MewdekoTextField(
            value = minSamplesText,
            onValueChange = { minSamplesText = it.filter(Char::isDigit) },
            label = "Min samples",
            placeholder = "none",
            numeric = true,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MutedText(if (absolute) "Preview · dashed = draft threshold" else "Preview · no fixed line for this comparator")
            if (draft.metric.isNotEmpty()) {
                androidx.compose.runtime.key(draft.metric) {
                    SeriesChart(
                        metric = draft.metric,
                        agg = draft.aggregation,
                        groupBy = draft.groupBy,
                        maxSeries = 8,
                        honours = HonoursNothing,
                        fixed = filterMap,
                        bands = true,
                        extraBands = previewBands,
                        noCompare = true,
                        height = 200.dp,
                    )
                }
            } else {
                MutedText("Pick a metric", center = true)
            }
        }

        MewdekoTextField(
            value = draft.webhookUrl,
            onValueChange = { draft = draft.copy(webhookUrl = it) },
            label = "Webhook URL",
            placeholder = "https://discord.com/api/webhooks/…",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = roleText,
                onValueChange = { roleText = it.filter(Char::isDigit) },
                label = "Mention role id",
                numeric = true,
                modifier = Modifier.weight(1f),
            )
            MewdekoTextField(
                value = threadText,
                onValueChange = { threadText = it.filter(Char::isDigit) },
                label = "Thread id",
                numeric = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = quietStart,
                onValueChange = { quietStart = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                label = "Quiet from (UTC)",
                placeholder = "HH:MM",
                modifier = Modifier.weight(1f),
            )
            MewdekoTextField(
                value = quietEnd,
                onValueChange = { quietEnd = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                label = "Quiet to (UTC)",
                placeholder = "HH:MM",
                modifier = Modifier.weight(1f),
            )
        }
        SwitchRow(
            title = "Notify on resolve",
            checked = draft.notifyOnResolve,
            onCheckedChange = { draft = draft.copy(notifyOnResolve = it) },
        )
    }
}

/** A pipeline health read and when it was read, for ages. */
private class HealthRead(val health: PipelineHealth?, val now: Long)

@Composable
private fun PipelineTab() {
    val env = LocalAnalytics.current
    val colors = rememberAnalyticsPalette()
    val read = rememberLoad(env.filters, env.tick) { HealthRead(env.vm.health(), System.currentTimeMillis()) }
    val health = read.data?.health
    val now = read.data?.now ?: System.currentTimeMillis()
    val flushAge = ageSeconds(health?.lastFlushAt, now)
    val rollupAge = ageSeconds(health?.lastRollupAt, now)
    val status: Pair<String, Tone> = when {
        health == null -> if (read.failed) "unreachable" to Tone.Crit else "loading" to Tone.Muted
        !health.enabled -> "disabled" to Tone.Muted
        !health.lastError.isNullOrBlank() -> "error" to Tone.Crit
        flushAge == null || flushAge > 60 -> "flush stale" to Tone.Crit
        rollupAge == null || rollupAge > 600 -> "rollup stale" to Tone.Warn
        else -> "healthy" to Tone.Ok
    }
    fun ageText(value: String?): String = ageSeconds(value, now)?.let { "${fmtDuration(it)} ago" } ?: "never"
    val loading = read.loading && health == null

    AnCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TonePill(
                status.first,
                status.second,
                icon = when (status.second) {
                    Tone.Ok -> Icons.Default.Check
                    Tone.Crit -> Icons.Default.Warning
                    else -> Icons.Default.Info
                },
            )
            MutedText("healthy = flush ≤ 60s · rollup ≤ 10m · no flush error", mono = true)
        }
    }

    TileGrid(
        {
            StatBox(
                "Last flush",
                ageText(health?.lastFlushAt),
                loading,
                tone = if (flushAge != null && flushAge > 60) Tone.Crit else null,
            )
        },
        {
            StatBox(
                "Last rollup",
                ageText(health?.lastRollupAt),
                loading,
                tone = if (rollupAge != null && rollupAge > 600) Tone.Warn else null,
            )
        },
        { StatBox("Last maintenance", ageText(health?.lastMaintenanceAt), loading) },
        { StatBox("Pending series", fmtInt(health?.pendingSeries?.toDouble()), loading) },
        { StatBox("Pending rows", fmtInt(health?.pendingRows?.toDouble()), loading) },
    )

    health?.lastError?.takeIf { it.isNotBlank() }?.let { message ->
        val shape = RoundedCornerShape(12.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(toneWash(colors.crit), shape)
                .border(1.dp, colors.crit.copy(alpha = DashAlpha.Hex30), shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "LAST FLUSH ERROR",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                color = colors.crit,
            )
            Text(message, style = MonospaceStyle.copy(fontSize = 12.sp))
        }
    }

    val tableColumns = remember {
        listOf(
            TableColumn<PipelineTable>("Table", 180.dp, mono = true) { it.table },
            TableColumn("Rows", 100.dp, numeric = true, sort = { it.rows }) { fmtInt(it.rows) },
            TableColumn("Newest", 90.dp, numeric = true, muted = true, sort = { utcParse(it.newest) }) { row ->
                row.newest?.let { ago(it) } ?: Missing
            },
        )
    }
    val instanceColumns = remember(now) {
        listOf(
            TableColumn<PipelineInstance>("Bot", 130.dp) { it.botName },
            TableColumn("Id", 160.dp, mono = true, muted = true) { it.botId },
            TableColumn("Host", 150.dp, mono = true, muted = true) { "${it.host}:${it.port}" },
            TableColumn("State", 90.dp, tone = { if (it.isActive) Tone.Ok else Tone.Muted }) {
                if (it.isActive) "active" else "inactive"
            },
            TableColumn("Heartbeat", 90.dp, numeric = true, muted = true, sort = { utcParse(it.lastStatusUpdate) }) {
                ago(it.lastStatusUpdate)
            },
            TableColumn(
                "Last guild count",
                120.dp,
                numeric = true,
                sort = { utcParse(it.lastGuildCountAt) },
                tone = { row ->
                    val age = ageSeconds(row.lastGuildCountAt, now)
                    when {
                        age == null || age > 600 -> Tone.Crit
                        age > 180 -> Tone.Warn
                        else -> null
                    }
                },
            ) { row -> row.lastGuildCountAt?.let { ago(it) } ?: "never" },
        )
    }
    CardColumns(
        {
            AnCard(title = "Tables") {
                DataTable(tableColumns, health?.tables.orEmpty(), read.loading, empty = "No tables reported", sortIndex = 1)
            }
        },
        {
            AnCard(title = "Instances") {
                DataTable(
                    instanceColumns,
                    health?.instances.orEmpty(),
                    read.loading,
                    empty = "No instances registered",
                    sortIndex = 4,
                )
            }
        },
        {
            AnCard(title = "Flush duration", note = "an.flush · ms") {
                SeriesChart("an.flush", "max", groupBy = "bot", honours = HonoursBot, unit = "ms", noCompare = true)
            }
        },
        {
            AnCard(title = "Rows per flush", note = "an.rows") {
                SeriesChart(
                    "an.rows",
                    groupBy = "bot",
                    honours = HonoursBot,
                    kind = ChartKind.Bar,
                    stack = true,
                    unit = "rows",
                    noCompare = true,
                )
            }
        },
    )
}
