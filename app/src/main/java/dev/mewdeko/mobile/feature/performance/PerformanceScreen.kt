package dev.mewdeko.mobile.feature.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.util.compact
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab(PerfSection.OVERVIEW.id, PerfSection.OVERVIEW.title, Icons.Default.Speed),
    SectionTab(PerfSection.METHODS.id, PerfSection.METHODS.title, Icons.Default.Schedule),
    SectionTab(PerfSection.EVENTS.id, PerfSection.EVENTS.title, Icons.Default.Notifications),
    SectionTab(PerfSection.MODULES.id, PerfSection.MODULES.title, Icons.Default.Widgets),
)

/**
 * Bot CPU, memory, method, event, and module telemetry: the owner panel's
 * Performance page. Fleet level: acts on the selected bot instance. Only the
 * visible tab polls, on the interval [PerformanceViewModel.poll] gives it.
 */
@Composable
fun PerformanceScreen(
    onBack: () -> Unit,
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffectPoll(lifecycleOwner, viewModel, state.section)

    val currentLoading = when (state.section) {
        PerfSection.OVERVIEW -> state.overviewLoading
        PerfSection.METHODS -> state.methodsLoading
        PerfSection.EVENTS -> state.eventsLoading
        PerfSection.MODULES -> state.modulesLoading
    }

    FeatureScaffold(
        title = "Performance",
        subtitle = viewModel.botName,
        onBack = onBack,
        loadState = LoadState(hasLoaded = true, isRefreshing = currentLoading),
        onRefresh = viewModel::refreshCurrent,
    ) {
        SectionTabs(
            tabs = Tabs,
            selectedId = state.section.id,
            onSelect = { viewModel.setSection(PerfSection.fromId(it)) },
        )

        when (state.section) {
            PerfSection.OVERVIEW -> OverviewTab(state, viewModel::refreshCurrent)
            PerfSection.METHODS -> MethodsTab(state, viewModel)
            PerfSection.EVENTS -> EventsTab(state, viewModel)
            PerfSection.MODULES -> ModulesTab(state, viewModel)
        }
    }
}

/** Starts [PerformanceViewModel.poll] for [section] whenever it or the lifecycle changes. */
@Composable
private fun LaunchedEffectPoll(
    lifecycleOwner: LifecycleOwner,
    viewModel: PerformanceViewModel,
    section: PerfSection,
) {
    LaunchedEffect(lifecycleOwner, section) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.poll(section) }
    }
}

/** System resources plus the slowest instrumented methods, from `api/SystemInfo`. */
@Composable
private fun OverviewTab(state: PerformanceState, onRefresh: () -> Unit) {
    SectionCard {
        SectionCardHeader(
            "System Resources",
            Icons.Default.Speed,
            trailing = { RefreshAction(state.overviewLoading, onRefresh) },
        )
        state.overviewError?.let { ErrorBanner(it) }

        val info = state.overview
        when {
            info == null && state.overviewLoading -> InlineSpinner()
            info == null -> EmptyMetrics(
                Icons.Default.Speed,
                "No system information available.",
                "System data will appear here once loaded.",
            )

            else -> {
                MetricBar(
                    label = "CPU Usage",
                    valueText = "%.1f%%".format(info.cpuUsage),
                    progress = (info.cpuUsage / 100.0).toFloat().coerceIn(0f, 1f),
                    color = cpuTone(info.cpuUsage),
                )
                val memoryFraction = if (info.totalMemoryMb <= 0.0) {
                    0f
                } else {
                    (info.memoryUsageMb / info.totalMemoryMb).toFloat().coerceIn(0f, 1f)
                }
                MetricBar(
                    label = "Memory Usage",
                    valueText = "${formatMb(info.memoryUsageMb)} / ${formatMb(info.totalMemoryMb)}",
                    progress = memoryFraction,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Uptime", info.uptime, Modifier.weight(1f), icon = Icons.Default.Timer)
                    StatTile(
                        "Threads",
                        info.threadCount.toString(),
                        Modifier.weight(1f),
                        icon = Icons.Default.Layers,
                    )
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Top CPU Intensive Methods", Icons.Default.Speed)
        val topMethods = state.overview?.topMethods.orEmpty()
        if (topMethods.isEmpty()) {
            EmptyState("No method timings yet.", icon = Icons.Default.Speed)
        } else {
            topMethods.forEach { method ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = method.name,
                        style = MonospaceStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatTime(method.avgTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Instrumented method timings, from `api/Performance/methods`, with Clear Data. */
@Composable
private fun MethodsTab(state: PerformanceState, viewModel: PerformanceViewModel) {
    var pendingClear by remember { mutableStateOf(false) }

    SectionCard {
        SectionCardHeader(
            "Method Performance",
            Icons.Default.Schedule,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RefreshAction(state.methodsLoading, viewModel::refreshCurrent)
                    IconButton(onClick = { pendingClear = true }, enabled = !state.clearing) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear performance data",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
        state.methodsError?.let { ErrorBanner(it) }

        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Sort),
            options = MethodSortField.entries.map { SelectorOption(it.name, it.label) },
            placeholder = "Server order (avg time)",
            selectedId = state.methodSort?.name,
            onSelect = { id -> id?.let { viewModel.setMethodSort(MethodSortField.valueOf(it)) } },
            label = "Sort by",
        )

        when {
            state.methods.isEmpty() && state.methodsLoading -> InlineSpinner()
            state.methods.isEmpty() -> EmptyMetrics(
                Icons.Default.Schedule,
                "No performance data available yet.",
                "Run some commands to generate performance metrics.",
            )

            else -> state.methods.forEachIndexed { index, method ->
                MethodRow(method, alternate = index % 2 == 1)
            }
        }
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "Clear performance data?",
            message = "Method timing samples are discarded. Event and module counters are not " +
                "affected and keep collecting.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearMethodData,
            onDismiss = { pendingClear = false },
        )
    }
}

/** Gateway event throughput and error rates, from `api/Performance/events`. */
@Composable
private fun EventsTab(state: PerformanceState, viewModel: PerformanceViewModel) {
    SectionCard {
        SectionCardHeader(
            "Event Metrics",
            Icons.Default.Notifications,
            trailing = { RefreshAction(state.eventsLoading, viewModel::refreshCurrent) },
        )
        state.eventsError?.let { ErrorBanner(it) }

        if (state.events.isNotEmpty()) {
            val totalErrors = state.events.sumOf { it.totalErrors }
            MetricSummary(
                totalLabel = "Total Events",
                total = state.events.sumOf { it.totalProcessed }.compact(),
                totalErrors = totalErrors,
                countLabel = "Event Types",
                count = state.events.size,
                avgErrorRate = state.events.map { it.errorRate }.average(),
            )
        }

        MetricSortRow(
            selected = state.eventSort,
            descending = state.eventSortDescending,
            onSelect = viewModel::setEventSort,
        )

        when {
            state.events.isEmpty() && state.eventsLoading -> InlineSpinner()
            state.events.isEmpty() -> EmptyMetrics(
                Icons.Default.Notifications,
                "No event metrics available yet.",
                "Event metrics will appear as your bot processes Discord events.",
            )

            else -> state.events.forEach { event ->
                MetricRow(
                    name = event.eventType.ifEmpty { "Unknown" },
                    processed = event.totalProcessed,
                    errors = event.totalErrors,
                    errorRate = event.errorRate,
                    avgTime = event.averageExecutionTime,
                    totalTime = event.totalExecutionTime.toDouble(),
                )
            }
        }
    }
}

/** Bot module throughput and error rates, from `api/Performance/modules`. */
@Composable
private fun ModulesTab(state: PerformanceState, viewModel: PerformanceViewModel) {
    SectionCard {
        SectionCardHeader(
            "Module Metrics",
            Icons.Default.Widgets,
            trailing = { RefreshAction(state.modulesLoading, viewModel::refreshCurrent) },
        )
        state.modulesError?.let { ErrorBanner(it) }

        if (state.modules.isNotEmpty()) {
            val totalErrors = state.modules.sumOf { it.errors }
            MetricSummary(
                totalLabel = "Total Events",
                total = state.modules.sumOf { it.eventsProcessed }.compact(),
                totalErrors = totalErrors,
                countLabel = "Active Modules",
                count = state.modules.size,
                avgErrorRate = state.modules.map { it.errorRate }.average(),
            )
        }

        MetricSortRow(
            selected = state.moduleSort,
            descending = state.moduleSortDescending,
            onSelect = viewModel::setModuleSort,
        )

        when {
            state.modules.isEmpty() && state.modulesLoading -> InlineSpinner()
            state.modules.isEmpty() -> EmptyMetrics(
                Icons.Default.Widgets,
                "No module metrics available yet.",
                "Module metrics will appear as your bot modules process events.",
            )

            else -> state.modules.forEach { module ->
                MetricRow(
                    name = module.moduleName,
                    processed = module.eventsProcessed,
                    errors = module.errors,
                    errorRate = module.errorRate,
                    avgTime = module.averageExecutionTime,
                    totalTime = module.totalExecutionTime.toDouble(),
                    namePill = true,
                )
            }
        }
    }
}

/** The four summary tiles shared by the Events and Modules tabs. */
@Composable
private fun MetricSummary(
    totalLabel: String,
    total: String,
    totalErrors: Long,
    countLabel: String,
    count: Int,
    avgErrorRate: Double,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(totalLabel, total, Modifier.weight(1f))
        StatTile(
            label = "Total Errors",
            value = totalErrors.compact(),
            tint = if (totalErrors > 0) MaterialTheme.colorScheme.error else null,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(countLabel, count.toString(), Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
        StatTile("Avg Error Rate", "%.2f%%".format(avgErrorRate), Modifier.weight(1f))
    }
}

/** The field picker and direction toggle shared by the Events and Modules tabs. */
@Composable
private fun MetricSortRow(
    selected: MetricSortField,
    descending: Boolean,
    onSelect: (MetricSortField) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Sort),
            options = MetricSortField.entries.map { SelectorOption(it.name, it.label) },
            placeholder = "Sort by",
            selectedId = selected.name,
            onSelect = { id -> id?.let { onSelect(MetricSortField.valueOf(it)) } },
            label = "Sort by",
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onSelect(selected) }) {
            Icon(
                if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                contentDescription = if (descending) "Sorted descending" else "Sorted ascending",
            )
        }
    }
}

/** One instrumented method row on the Methods tab. */
@Composable
private fun MethodRow(method: PerfMethod, alternate: Boolean) {
    val background = if (alternate) {
        MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08)
    } else {
        Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = method.methodName,
            style = MonospaceStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${method.callCount.withSeparators()} calls",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(method.avgExecutionTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "total ${formatTime(method.totalTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = method.lastExecuted.relativeToNow(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One event or module row, shared by the Events and Modules tabs. */
@Composable
private fun MetricRow(
    name: String,
    processed: Long,
    errors: Long,
    errorRate: Double,
    avgTime: Double,
    totalTime: Double,
    namePill: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        if (namePill) {
            StatePill(text = name, tone = moduleTone(name))
        } else {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${processed.withSeparators()} processed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "$errors errors",
                style = MaterialTheme.typography.labelSmall,
                color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "%.2f%% error rate".format(errorRate),
                style = MaterialTheme.typography.labelSmall,
                color = errorRateTone(errorRate),
            )
            Text(
                text = "avg ${formatTime(avgTime)} · total ${formatTime(totalTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A labelled progress bar, used for CPU and memory usage on the Overview tab. */
@Composable
private fun MetricBar(label: String, valueText: String, progress: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = color)
        }
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/** A small trailing refresh action: a spinner while loading, otherwise a refresh glyph. */
@Composable
private fun RefreshAction(loading: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = !loading) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
    }
}

/** A centered spinner for a tab that is loading its first page of data. */
@Composable
private fun InlineSpinner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** The empty state shared by every tab: a headline plus a muted explanatory subline. */
@Composable
private fun EmptyMetrics(icon: ImageVector, message: String, subline: String) {
    EmptyState("$message\n$subline", icon = icon)
}

/** The inline error banner every tab shows above its content, instead of a toast. */
@Composable
private fun ErrorBanner(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** CPU usage color: the dashboard's over 80 percent, over 50 percent, and normal tiers. */
@Composable
private fun cpuTone(cpu: Double): Color = when {
    cpu > 80 -> MaterialTheme.colorScheme.error
    cpu > 50 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

/** Error rate color: none, a watch band under 5 percent, and a bad band at or above it. */
@Composable
private fun errorRateTone(rate: Double): Color = when {
    rate <= 0.0 -> MaterialTheme.colorScheme.primary
    rate < 5.0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * Module name pill color: the dashboard's Legacy, Command, Event, and Service
 * bands, and a muted fallback for everything else.
 */
@Composable
private fun moduleTone(name: String): Color = when {
    name == "Legacy" -> MaterialTheme.colorScheme.tertiary
    name.contains("Command", ignoreCase = true) -> MaterialTheme.colorScheme.primary
    name.contains("Event", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
    name.contains("Service", ignoreCase = true) -> MaterialTheme.colorScheme.error
    else -> LocalGuildPalette.current.muted.color
}

/** `μs` under 1ms, `ms` under 1s, `s` otherwise, matching the dashboard's `formatTime`. */
private fun formatTime(ms: Double): String = when {
    ms < 1.0 -> "%.2fµs".format(ms * 1000)
    ms < 1000.0 -> "%.2fms".format(ms)
    else -> "%.2fs".format(ms / 1000)
}

/** `MB` with 2 decimals under 1024, `GB` otherwise, matching the dashboard's memory display. */
private fun formatMb(mb: Double): String = if (mb < 1024) {
    "%.2f MB".format(mb)
} else {
    "%.2f GB".format(mb / 1024)
}
