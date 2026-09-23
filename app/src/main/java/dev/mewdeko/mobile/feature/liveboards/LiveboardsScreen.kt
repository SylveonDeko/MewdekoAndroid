package dev.mewdeko.mobile.feature.liveboards

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.shortDateTime

/** The two sections of the screen, matching the dashboard's tabs. */
private val Tabs = listOf(
    SectionTab("boards", "Live Boards", Icons.Default.PushPin),
    SectionTab("reports", "Server Reports", Icons.Default.Insights),
)

/** Board kinds offered in the kind picker. */
private val KindOptions = LiveBoardKind.entries.map { SelectorOption(it.value.toString(), it.label) }

/** One line of the static "what a report contains" card. */
private data class ReportLine(val icon: ImageVector, val text: String)

/** What a server report includes, as listed on the dashboard. */
private val ReportContents = listOf(
    ReportLine(Icons.Default.Groups, "Joins, leaves, net growth and retention for the period"),
    ReportLine(Icons.Default.RecordVoiceOver, "Messages, voice time and active members"),
    ReportLine(Icons.AutoMirrored.Filled.TrendingUp, "Member count and how it changed"),
    ReportLine(Icons.Default.EmojiEvents, "Top inviters, chatters, voice members and busiest channels"),
    ReportLine(Icons.Default.Link, "Top invite codes with their labels"),
    ReportLine(Icons.Default.SportsEsports, "Top games and apps, when game tracking is on"),
    ReportLine(Icons.Default.BarChart, "A joins and leaves chart"),
)

/** Self-refreshing leaderboards and charts, plus scheduled server reports. */
@Composable
fun LiveboardsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: LiveboardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<LiveBoard?>(null) }
    var pendingClearChannel by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Live Boards",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "boards" -> BoardsSection(state, viewModel, onDelete = { pendingDelete = it })
            "reports" -> ReportsSection(state, viewModel, onClearChannel = { pendingClearChannel = true })
        }
    }

    pendingDelete?.let { board ->
        ConfirmDialog(
            title = "Delete live board",
            message = "Delete the ${board.kindLabel} in #${state.channelName(board.channelId)}? " +
                "Its message is removed too.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteBoard(board) },
            onDismiss = { pendingDelete = null },
        )
    }

    if (pendingClearChannel) {
        ConfirmDialog(
            title = "Clear the report channel?",
            message = "Scheduled reports stop until a channel is set again.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearReportChannel,
            onDismiss = { pendingClearChannel = false },
        )
    }
}

/** The board list and the form for adding a new board. */
@Composable
private fun BoardsSection(
    state: LiveboardsState,
    viewModel: LiveboardsViewModel,
    onDelete: (LiveBoard) -> Unit,
) {
    SectionCard {
        SectionCardHeader(
            title = "Live Boards (${state.boards.size}/${LiveBoardLimits.MAX_BOARDS})",
            icon = Icons.Default.PushPin,
            trailing = {
                TextButton(
                    onClick = viewModel::refreshAll,
                    enabled = !state.isRefreshingBoards && state.boards.isNotEmpty(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (state.isRefreshingBoards) "Refreshing…" else "Refresh all",
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            },
        )
        Text(
            text = "Up to ${LiveBoardLimits.MAX_BOARDS} per server, each refreshed on its own interval.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val error = state.boardsError
        when {
            error != null && state.boards.isEmpty() -> ErrorState(error, onRetry = { viewModel.reloadBoards() })
            state.boards.isEmpty() -> EmptyState(
                "No live boards yet. Add one and the bot posts and pins it right away.",
                icon = Icons.Default.PushPin,
            )
            else -> state.boards.forEach { board ->
                BoardRow(
                    board = board,
                    channelName = state.channelName(board.channelId),
                    deleting = board.id in state.deletingIds,
                    onDelete = { onDelete(board) },
                )
            }
        }
    }

    AddBoardCard(state, viewModel)
}

/** One board in the list: its kind, window, pin state, channel, interval, and freshness. */
@Composable
private fun BoardRow(
    board: LiveBoard,
    channelName: String,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = board.kindEnum?.icon ?: Icons.Default.PushPin,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = board.kindLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagChip(board.rangeLabel, icon = Icons.Default.Schedule)
                if (board.pin) TagChip("Pinned", icon = Icons.Default.PushPin)
                if (board.showsRows) TagChip("${board.entries} rows")
            }
            Text(
                text = "#$channelName, every ${board.intervalMinutes}m, updated " +
                    (board.lastUpdateAt?.relativeToNow() ?: "never"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete, enabled = !deleting) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete live board",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The "Add a live board" form. */
@Composable
private fun AddBoardCard(state: LiveboardsState, viewModel: LiveboardsViewModel) {
    val draft = state.draft
    SectionCard {
        SectionCardHeader("Add a live board", Icons.Default.Add)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.channels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a channel",
            label = "Channel",
            selectedId = draft.channelId,
            onSelect = viewModel::setDraftChannel,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.PushPin),
            options = KindOptions,
            placeholder = "Pick what the board shows",
            label = "Board",
            selectedId = draft.kind.value.toString(),
            onSelect = { id ->
                id?.toIntOrNull()?.let { LiveBoardKind.of(it) }?.let { viewModel.setDraftKind(it) }
            },
        )
        Text(
            text = "Window",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveBoardRange.entries.forEach { range ->
                FilterChip(
                    selected = draft.range == range,
                    onClick = { viewModel.setDraftRange(range) },
                    label = { Text(range.shortLabel) },
                )
            }
        }
        Text(
            text = "Leaderboards use it directly; charts use up to 90 days for all time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft.kind.isLeaderboard) {
            SliderRow(
                label = "Rows",
                value = draft.entries.toFloat(),
                onValueChange = { viewModel.setDraftEntries(it.toInt()) },
                valueRange = LiveBoardLimits.MIN_ENTRIES.toFloat()..LiveBoardLimits.MAX_ENTRIES.toFloat(),
                steps = LiveBoardLimits.MAX_ENTRIES - LiveBoardLimits.MIN_ENTRIES - 1,
                valueLabel = draft.entries.toString(),
            )
        }
        MewdekoTextField(
            value = draft.intervalText,
            onValueChange = viewModel::setDraftInterval,
            label = "Refresh every (minutes)",
            numeric = true,
            isError = draft.intervalText.isNotEmpty() && !draft.intervalValid,
            supportingText = "Between ${LiveBoardLimits.MIN_INTERVAL} and ${LiveBoardLimits.MAX_INTERVAL} minutes",
        )
        SwitchRow(
            title = "Pin the message",
            subtitle = "Needs Manage Messages in the channel",
            checked = draft.pin,
            onCheckedChange = viewModel::setDraftPin,
        )
        Button(
            onClick = viewModel::createBoard,
            enabled = !state.isCreating && !state.atLimit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.isCreating -> "Posting…"
                    state.atLimit -> "Limit reached"
                    else -> "Add live board"
                }
            )
        }
    }
}

/** The report schedule and the static description of a report's contents. */
@Composable
private fun ReportsSection(
    state: LiveboardsState,
    viewModel: LiveboardsViewModel,
    onClearChannel: () -> Unit,
) {
    val report = state.report
    val error = state.reportError
    if (report == null) {
        SectionCard {
            SectionCardHeader("Server Reports", Icons.Default.Insights)
            if (error != null) {
                ErrorState(error, onRetry = { viewModel.reloadReport() })
            } else {
                EmptyState("Report settings are not available yet.", icon = Icons.Default.Insights)
            }
        }
        return
    }

    val channelId = report.activeChannelId
    val frequency = ReportFrequency.of(report.frequency)

    SectionCard {
        SectionCardHeader("Schedule", Icons.Default.Schedule)
        Text(
            text = "A digest of growth and activity posted daily, weekly or monthly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.channels.map { SelectorOption(it.id, it.name) },
            placeholder = "No channel",
            label = "Channel",
            selectedId = channelId,
            enabled = !state.isSavingReport,
            onSelect = { id ->
                if (id.isNullOrEmpty()) onClearChannel() else if (id != channelId) viewModel.setReportChannel(id)
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Where the report is posted. Clearing it disables reports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (channelId != null) {
                TextButton(onClick = onClearChannel, enabled = !state.isSavingReport) {
                    Text("Clear")
                }
            }
        }
        Text(
            text = "Frequency",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportFrequency.entries.forEach { option ->
                FilterChip(
                    selected = frequency == option,
                    onClick = { if (frequency != option) viewModel.setReportFrequency(option) },
                    enabled = !state.isSavingReport,
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            text = "Daily at 00:00 UTC, weekly on Mondays, or on the first of the month.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            title = "Reports enabled",
            subtitle = if (channelId == null) {
                "Set a channel first"
            } else {
                "Turn scheduled reports on or off without losing the channel"
            },
            checked = report.enabled,
            onCheckedChange = viewModel::setReportEnabled,
            enabled = channelId != null && !state.isSavingReport,
        )
        InfoRow(
            label = "Last sent",
            value = report.lastSentAt?.let { "${it.relativeToNow()} (${it.shortDateTime()})" } ?: "Never",
        )
        Button(
            onClick = viewModel::sendReportNow,
            enabled = !state.isSendingReport && channelId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = if (state.isSendingReport) "Sending…" else "Send a report now",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    SectionCard {
        SectionCardHeader("What a report contains", Icons.Default.Info)
        ReportContents.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = line.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
