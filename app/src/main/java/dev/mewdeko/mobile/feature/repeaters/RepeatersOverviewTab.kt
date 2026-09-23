package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile

/** Statistics tab: aggregate stat tiles, trigger mode distribution, and the most active repeater. */
@Composable
fun RepeatersOverviewTab(state: RepeatersState) {
    val stats = state.stats

    SectionCard {
        SectionCardHeader("Overview", Icons.Default.BarChart)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Total", "${stats?.totalRepeaters ?: state.repeaters.size}", Modifier.weight(1f))
            StatTile("Active", "${stats?.activeRepeaters ?: state.repeaters.count { it.isEnabled }}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("Total Displays", "${stats?.totalDisplays ?: state.repeaters.sumOf { it.displayCount }}", Modifier.weight(1f))
            StatTile("Scheduled", "${stats?.timeScheduledRepeaters ?: 0}", Modifier.weight(1f))
        }
    }

    if (stats == null) return

    SectionCard {
        SectionCardHeader("Trigger Modes", Icons.Default.Tune)
        if (stats.triggerModeDistribution.isEmpty()) {
            EmptyState("No repeaters yet.", icon = Icons.Default.Tune)
        } else {
            stats.triggerModeDistribution.entries
                .sortedByDescending { it.value }
                .forEach { (mode, count) -> InfoRow(label = mode.toDisplayWords(), value = count.toString()) }
        }
    }

    val mostActive = stats.mostActiveRepeater
    if (mostActive != null) {
        SectionCard {
            SectionCardHeader("Most Active Repeater", Icons.Default.Star)
            InfoRow(label = "Channel", value = "#${state.channelName(mostActive.channelId)}")
            InfoRow(label = "Displays", value = mostActive.displayCount.toString())
            val preview = EmbedMessage.parse(mostActive.message).content
                .ifBlank { "Rich embed message" }
                .take(100)
            Text(preview)
        }
    }
}

/**
 * Splits a Pascal- or camelCase enum name like `TimeInterval` (or, after the
 * shared JSON codec's key normalisation, `timeInterval`) into `Time Interval`.
 */
private fun String.toDisplayWords(): String =
    fold(StringBuilder()) { acc, c ->
        if (acc.isNotEmpty() && c.isUpperCase()) acc.append(' ')
        acc.append(c)
    }.toString().replaceFirstChar { it.uppercaseChar() }
