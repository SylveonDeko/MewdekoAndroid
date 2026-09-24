package dev.mewdeko.mobile.feature.messagestats

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.compact
import dev.mewdeko.mobile.util.shortDate
import java.time.LocalDate

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.MarkEmailUnread),
    SectionTab("members", "Members", Icons.Default.Person),
    SectionTab("channels", "Channels", Icons.Default.Tag),
    SectionTab("settings", "Settings", Icons.Default.Settings),
    SectionTab("export", "Export", Icons.Default.Download),
)

private val GoldColor = Color(0xFFFFD700)
private val SilverColor = Color(0xFFC0C0C0)
private val BronzeColor = Color(0xFFCD7F32)

/** Per-channel and per-member message activity, with settings and a client-built export. */
@Composable
fun MessageStatsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: MessageStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingReset by remember { mutableStateOf(false) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MessageStatsExportFormat.CSV.mimeType),
    ) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            val resolver = context.contentResolver
            viewModel.export { bytes ->
                val stream = resolver.openOutputStream(uri) ?: error("Could not open the file.")
                stream.use { it.write(bytes) }
            }
        }
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MessageStatsExportFormat.JSON.mimeType),
    ) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            val resolver = context.contentResolver
            viewModel.export { bytes ->
                val stream = resolver.openOutputStream(uri) ?: error("Could not open the file.")
                stream.use { it.write(bytes) }
            }
        }
    }

    FeatureScaffold(
        title = "Message Stats",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingReset = true }) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Reset counts",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "members" -> MembersSection(state)
            "channels" -> ChannelsSection(state)
            "settings" -> SettingsSection(
                state = state,
                viewModel = viewModel,
                onResetAll = { pendingReset = true },
            )
            "export" -> ExportSection(
                state = state,
                viewModel = viewModel,
                onExport = {
                    when (state.exportFormat) {
                        MessageStatsExportFormat.CSV -> csvExportLauncher.launch(viewModel.prepareExport())
                        MessageStatsExportFormat.JSON -> jsonExportLauncher.launch(viewModel.prepareExport())
                    }
                },
            )
            else -> OverviewSection(state)
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "Reset all counts?",
            message = "Every recorded message count for this server is cleared. " +
                "This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.reset() },
            onDismiss = { pendingReset = false },
        )
    }
}

@Composable
private fun OverviewSection(state: MessageStatsState) {
    val stats = state.stats

    SectionCard {
        SectionCardHeader("Volume", Icons.Default.MarkEmailUnread)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Total",
                value = stats?.totalMessages?.compact() ?: "-",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Today",
                value = stats?.dailyMessages?.compact() ?: "-",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Last updated",
                value = stats?.lastUpdated?.shortDate() ?: "-",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Status",
                value = if (state.enabled) "Enabled" else "Disabled",
                tint = if (state.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (stats != null && (stats.leastActiveUser != null || stats.leastActiveChannel != null)) {
        SectionCard {
            SectionCardHeader("Least active", Icons.AutoMirrored.Filled.TrendingDown)
            stats.leastActiveUser?.let { user ->
                InfoRowCompat(
                    label = "User",
                    value = "${displayNameFor(state, user.userId)} (${user.totalMessages.compact()})",
                )
            }
            stats.leastActiveChannel?.let { channel ->
                InfoRowCompat(
                    label = "Channel",
                    value = "#${channel.channelName ?: channel.channelId.orEmpty()} " +
                        "(${channel.totalMessages.compact()})",
                )
            }
        }
    }

    val hours = stats?.busiestHours.orEmpty().sortedBy { it.hour }
    if (hours.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Busiest hours", Icons.Default.Schedule)
            val total = hours.sumOf { it.messageCount }
            val average = if (hours.isNotEmpty()) total / hours.size else 0L
            val peak = hours.maxByOrNull { it.messageCount }
            ActivitySummaryRow(
                total = total,
                average = average,
                peakLabel = peak?.let { "${it.hour}:00" } ?: "-",
            )
            ActivityBarChart(entries = hours.map { "${it.hour}:00" to it.messageCount })
        }
    }

    val days = stats?.busiestDays.orEmpty()
    if (days.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Busiest days", Icons.Default.CalendarMonth)
            val total = days.sumOf { it.messageCount }
            val average = if (days.isNotEmpty()) total / days.size else 0L
            val peak = days.maxByOrNull { it.messageCount }
            ActivitySummaryRow(
                total = total,
                average = average,
                peakLabel = peak?.day?.take(3) ?: "-",
            )
            ActivityBarChart(entries = days.map { it.day.take(3) to it.messageCount })
        }
    }

    SectionCard {
        SectionCardHeader("Top members", Icons.Default.Person)
        val users = stats?.topUsers.orEmpty().take(5)
        if (users.isEmpty()) {
            EmptyState("No member activity recorded.")
        } else {
            users.forEachIndexed { index, user ->
                MemberRow(
                    rank = index + 1,
                    avatarUrl = memberFor(state, user.userId)?.avatarUrl,
                    name = displayNameFor(state, user.userId),
                    totalMessages = user.totalMessages,
                    dailyMessages = user.dailyMessages,
                    percentage = percentageOf(user.totalMessages, stats?.totalMessages ?: 0L),
                    compact = true,
                )
            }
        }
    }
    SectionCard {
        SectionCardHeader("Top channels", Icons.Default.Tag)
        val channels = stats?.topChannels.orEmpty().take(5)
        if (channels.isEmpty()) {
            EmptyState("No channel activity recorded.")
        } else {
            channels.forEachIndexed { index, channel ->
                ChannelRow(rank = index + 1, channel = channel, compact = true)
            }
        }
    }
}

@Composable
private fun MembersSection(state: MessageStatsState) {
    SectionCard {
        SectionCardHeader("Member leaderboard", Icons.Default.Leaderboard)
        val entries = state.leaderboard.ifEmpty { state.stats?.topUsers.orEmpty() }
        if (entries.isEmpty()) {
            EmptyState("No member activity recorded.", icon = Icons.Default.Person)
        } else {
            entries.forEachIndexed { index, user ->
                MemberRow(
                    rank = index + 1,
                    avatarUrl = memberFor(state, user.userId)?.avatarUrl,
                    name = displayNameFor(state, user.userId),
                    totalMessages = user.totalMessages,
                    dailyMessages = user.dailyMessages,
                    percentage = percentageOf(user.totalMessages, state.stats?.totalMessages ?: 0L),
                    compact = false,
                )
            }
        }
    }
}

@Composable
private fun ChannelsSection(state: MessageStatsState) {
    SectionCard {
        SectionCardHeader("Channel activity", Icons.Default.Tag)
        val channels = state.stats?.topChannels.orEmpty()
        if (channels.isEmpty()) {
            EmptyState("No channel activity recorded.", icon = Icons.Default.Tag)
        } else {
            channels.forEachIndexed { index, channel ->
                ChannelRow(rank = index + 1, channel = channel, compact = false)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    state: MessageStatsState,
    viewModel: MessageStatsViewModel,
    onResetAll: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("Message counting", Icons.Default.MarkEmailUnread)
        SwitchRow(
            title = "Count messages",
            subtitle = "When off, the bot stops recording new activity",
            checked = state.enabled,
            onCheckedChange = { viewModel.toggleCounting() },
        )
    }

    SectionCard {
        SectionCardHeader("Minimum message length", Icons.Default.Settings)
        Text(
            text = "Only count messages with at least this many characters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CommittingSliderRow(
            label = "Minimum length",
            value = state.minMessageLength.toFloat(),
            valueRange = 0f..4098f,
            valueLabel = { it.toInt().toString() },
            onCommit = { viewModel.saveMinMessageLength(it.toInt()) },
        )
    }

    SectionCard {
        SectionCardHeader("Reset message counts", Icons.Default.DeleteSweep, tint = MaterialTheme.colorScheme.error)
        Text(
            text = "Permanently delete message count data. This action cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onResetAll) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reset all server counts")
        }
    }
}

@Composable
private fun ExportSection(
    state: MessageStatsState,
    viewModel: MessageStatsViewModel,
    onExport: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("Export message data", Icons.Default.Download)
        Text(
            text = "Built from the statistics currently loaded on this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DatePickerField(
            label = "Start date",
            date = state.exportStartDate,
            onChange = viewModel::setExportStartDate,
        )
        DatePickerField(
            label = "End date",
            date = state.exportEndDate,
            onChange = viewModel::setExportEndDate,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Description),
            options = MessageStatsExportFormat.entries.map { SelectorOption(it.id, it.label) },
            placeholder = "Select export format",
            label = "Format",
            selectedId = state.exportFormat.id,
            onSelect = { id -> viewModel.setExportFormat(MessageStatsExportFormat.from(id)) },
        )

        SwitchRow(
            title = "Include user statistics",
            checked = state.includeUsers,
            onCheckedChange = viewModel::setIncludeUsers,
        )
        SwitchRow(
            title = "Include channel statistics",
            checked = state.includeChannels,
            onCheckedChange = viewModel::setIncludeChannels,
        )
        SwitchRow(
            title = "Include hourly and daily breakdown",
            checked = state.includeHourly,
            onCheckedChange = viewModel::setIncludeHourly,
        )

        OutlinedButton(onClick = onExport, enabled = !state.isExporting) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.isExporting) "Exporting…" else "Export data")
        }
    }
}

private fun memberFor(state: MessageStatsState, userId: String?) =
    userId?.let { state.members[it] }

/**
 * The share of [total] that [count] represents, as a percentage.
 *
 * Computed client-side rather than trusted from the payload, since the
 * `/leaderboard` endpoint (used for the longer member list) does not return
 * a percentage field the way `/stats` does for its top-ten summary.
 */
private fun percentageOf(count: Long, total: Long): Double =
    if (total <= 0L) 0.0 else count * 100.0 / total

private fun displayNameFor(state: MessageStatsState, userId: String?): String {
    val member = memberFor(state, userId) ?: return userId ?: "Unknown"
    return member.displayName.ifBlank { member.username }.ifBlank { userId ?: "Unknown" }
}

@Composable
private fun MemberRow(
    rank: Int,
    avatarUrl: String?,
    name: String,
    totalMessages: Long,
    dailyMessages: Long,
    percentage: Double,
    compact: Boolean,
) {
    ListItem(
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RankBadge(rank)
                Avatar(url = avatarUrl, contentDescription = name, size = 32)
            }
        },
        headlineContent = {
            Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = if (!compact) {
            { Text("${dailyMessages.compact()} today • ${"%.1f".format(percentage)}% of total") }
        } else {
            null
        },
        trailingContent = {
            Text(text = totalMessages.compact(), style = MaterialTheme.typography.titleSmall)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ChannelRow(rank: Int, channel: MessageStatsChannel, compact: Boolean) {
    ListItem(
        leadingContent = { RankBadge(rank) },
        headlineContent = {
            Text(
                text = "#${channel.channelName ?: channel.channelId.orEmpty()}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (!compact) {
            {
                Text(
                    "${channel.dailyMessages.compact()} today • " +
                        "${"%.1f".format(channel.percentage)}% of total",
                )
            }
        } else {
            null
        },
        trailingContent = {
            Text(text = channel.totalMessages.compact(), style = MaterialTheme.typography.titleSmall)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun InfoRowCompat(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ActivitySummaryRow(total: Long, average: Long, peakLabel: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SummaryStat("Total", total.compact())
        SummaryStat("Average", average.compact())
        SummaryStat("Peak", peakLabel)
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Compact horizontal bar chart used for the busiest-hours and busiest-days breakdowns. */
@Composable
private fun ActivityBarChart(entries: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    val maxCount = (entries.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { (label, count) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(40.dp),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / maxCount.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Text(
                    text = count.compact(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(44.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate,
    onChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onChange(LocalDate.of(year, month + 1, day)) },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(date.toString())
        }
    }
}

/** A [SliderRow] that tracks edits locally and only commits when the drag ends. */
@Composable
private fun CommittingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    var draft by remember(value) { mutableStateOf(value) }
    SliderRow(
        label = label,
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        valueRange = valueRange,
        valueLabel = valueLabel(draft),
        modifier = modifier,
    )
}

@Composable
private fun RankBadge(rank: Int) {
    Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            color = when (rank) {
                1 -> GoldColor
                2 -> SilverColor
                3 -> BronzeColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}
