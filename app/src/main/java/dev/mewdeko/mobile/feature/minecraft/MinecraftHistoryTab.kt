package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile

/** History period choices, expressed in hours to match the bot's `hours` query param. */
private val HistoryPeriods = listOf("24h" to 24, "7d" to 168, "30d" to 720)

/**
 * Player-count and latency history for a chosen server, with uptime and
 * average/peak summary stats.
 */
@Composable
fun MinecraftHistoryTab(
    servers: List<MinecraftServer>,
    state: MinecraftState,
    onSelectServer: (String) -> Unit,
    onSelectHours: (Int) -> Unit,
) {
    if (servers.isEmpty()) {
        SectionCard { EmptyState("No servers tracked yet.", icon = Icons.Default.QueryStats) }
        return
    }

    SectionCard(contentPadding = 12) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            servers.forEach { server ->
                FilterChip(
                    selected = server.name == state.historyServer,
                    onClick = { onSelectServer(server.name) },
                    label = { Text(server.name) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryPeriods.forEach { (label, hours) ->
                FilterChip(
                    selected = hours == state.historyHours,
                    onClick = { onSelectHours(hours) },
                    label = { Text(label) },
                )
            }
        }
    }

    val server = state.historyServer
    if (server == null) {
        SectionCard {
            EmptyState(
                "Select a server to view its history.",
                icon = Icons.Default.QueryStats,
            )
        }
        return
    }

    if (state.historyLoading) {
        SectionCard { EmptyState("Loading history...") }
        return
    }

    if (state.history.size < 2) {
        SectionCard {
            EmptyState(
                "Not enough data yet. Snapshots are recorded each time the watch timer runs.",
                icon = Icons.Default.QueryStats,
            )
        }
        return
    }

    val snapshots = state.history
    val onlineSnapshots = snapshots.filter { it.isOnline }
    val avgPlayers = if (onlineSnapshots.isNotEmpty()) {
        onlineSnapshots.sumOf { it.playersOnline } / onlineSnapshots.size
    } else {
        0
    }
    val peakPlayers = snapshots.maxOf { it.playersOnline }
    val avgLatency = if (onlineSnapshots.isNotEmpty()) {
        onlineSnapshots.sumOf { it.latency } / onlineSnapshots.size
    } else {
        0
    }
    val uptime = (onlineSnapshots.size * 100 / snapshots.size)

    SectionCard {
        SectionCardHeader("Players online ($server)", Icons.Default.ShowChart)
        MinecraftHistoryChart(
            values = snapshots.map { it.playersOnline.toFloat() },
            color = MaterialTheme.colorScheme.primary,
        )
    }

    SectionCard {
        SectionCardHeader("Latency ($server)", Icons.Default.Speed)
        MinecraftHistoryChart(
            values = snapshots.map { it.latency.toFloat() },
            color = MaterialTheme.colorScheme.tertiary,
        )
    }

    SectionCard(contentPadding = 12) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = "Avg players", value = "$avgPlayers", modifier = Modifier.weight(1f))
            StatTile(label = "Peak players", value = "$peakPlayers", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = "Avg latency", value = "${avgLatency}ms", modifier = Modifier.weight(1f))
            StatTile(label = "Uptime", value = "$uptime%", modifier = Modifier.weight(1f))
        }
    }
}
