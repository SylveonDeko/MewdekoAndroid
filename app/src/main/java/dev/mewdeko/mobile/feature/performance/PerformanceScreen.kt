package dev.mewdeko.mobile.feature.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.compact

private val Tabs = listOf(
    SectionTab("methods", "Methods", Icons.Default.Functions),
    SectionTab("events", "Events", Icons.Default.Bolt),
    SectionTab("modules", "Modules", Icons.Default.Extension),
)

/** Bot CPU, throughput, and error telemetry. */
@Composable
fun PerformanceScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClear by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Performance",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingClear = true }) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear samples")
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Totals", Icons.Default.Speed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Calls", state.totalCalls.compact(), Modifier.weight(1f))
                StatTile("Events", state.totalEvents.compact(), Modifier.weight(1f))
                StatTile(
                    label = "Errors",
                    value = state.totalErrors.compact(),
                    tint = if (state.totalErrors > 0) MaterialTheme.colorScheme.error else null,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "events" -> SectionCard {
                SectionCardHeader("Gateway events", Icons.Default.Bolt)
                if (state.events.isEmpty()) {
                    EmptyState("No event samples collected.", icon = Icons.Default.Bolt)
                } else {
                    state.events.forEach { event ->
                        MetricRow(
                            name = event.eventType,
                            primary = "${event.totalProcessed.compact()} processed",
                            secondary = "avg ${"%.2f".format(event.averageExecutionTime)} ms",
                            errorRate = event.errorRate,
                            errorCount = event.totalErrors,
                        )
                    }
                }
            }

            "modules" -> SectionCard {
                SectionCardHeader("Modules", Icons.Default.Extension)
                if (state.modules.isEmpty()) {
                    EmptyState("No module samples collected.", icon = Icons.Default.Extension)
                } else {
                    state.modules.forEach { module ->
                        MetricRow(
                            name = module.moduleName,
                            primary = "${module.eventsProcessed.compact()} events",
                            secondary = "avg ${"%.2f".format(module.averageExecutionTime)} ms",
                            errorRate = module.errorRate,
                            errorCount = module.errors,
                        )
                    }
                }
            }

            else -> SectionCard {
                SectionCardHeader("Slowest methods", Icons.Default.Functions)
                if (state.methods.isEmpty()) {
                    EmptyState("No method samples collected.", icon = Icons.Default.Functions)
                } else {
                    val slowest = state.methods.firstOrNull()?.totalTime ?: 1.0
                    state.methods.forEach { method ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = method.methodName,
                                style = MonospaceStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${method.callCount.compact()} calls",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${"%.2f".format(method.totalTime)} ms total · " +
                                        "${"%.2f".format(method.avgExecutionTime)} ms avg",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    if (slowest <= 0) 0f
                                    else (method.totalTime / slowest).toFloat().coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "Clear performance data?",
            message = "Every collected sample on the bot is discarded. Collection continues.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearData,
            onDismiss = { pendingClear = false },
        )
    }
}

@Composable
private fun MetricRow(
    name: String,
    primary: String,
    secondary: String,
    errorRate: Double,
    errorCount: Long,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = primary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (errorCount > 0) {
            Text(
                text = "$errorCount errors (${"%.1f".format(errorRate)}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
