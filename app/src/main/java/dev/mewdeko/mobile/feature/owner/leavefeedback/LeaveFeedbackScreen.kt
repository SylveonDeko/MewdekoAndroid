package dev.mewdeko.mobile.feature.owner.leavefeedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.feature.guilddetail.home.skeleton
import dev.mewdeko.mobile.util.shortDateTime
import dev.mewdeko.mobile.util.withSeparators

/** The dashboard's two tabs. */
private val Tabs = listOf(
    SectionTab(LeaveFeedbackTabs.RESPONSES, "Responses", Icons.Default.Forum),
    SectionTab(LeaveFeedbackTabs.SETTINGS, "Settings", Icons.Default.Settings),
)

/**
 * Status filter choices, led by an "All statuses" sentinel (empty id) so the
 * single select sheet can clear the filter.
 */
private val StatusOptions = listOf(SelectorOption("", "All statuses")) +
    LeaveFeedbackStatusFilter.entries.map { SelectorOption(it.key, it.label) }

/** Skeleton rows shown while a page loads, as on the dashboard. */
private const val SkeletonRows = 6

/**
 * Why servers removed the bot, answered by their owners, mirroring the
 * dashboard's `/owner/leave-feedback`. Fleet level: acts on the selected bot
 * instance.
 */
@Composable
fun LeaveFeedbackScreen(
    onBack: () -> Unit,
    viewModel: LeaveFeedbackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Leave Feedback",
        subtitle = viewModel.botName,
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        Text(
            text = "Why servers removed the bot, straight from their owners",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionTabs(tabs = Tabs, selectedId = state.tab, onSelect = viewModel::setTab)

        when (state.tab) {
            LeaveFeedbackTabs.SETTINGS -> SettingsTab(state, viewModel)
            else -> ResponsesTab(state, viewModel)
        }
    }

    state.deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete feedback",
            message = "Permanently delete the feedback from ${target.guildName.ifEmpty { "this server" }}?",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun ResponsesTab(state: LeaveFeedbackState, viewModel: LeaveFeedbackViewModel) {
    StatGrid(state.stats)

    val stats = state.stats
    if (stats != null && stats.reasonsGiven.isNotEmpty()) {
        ReasonsCard(stats = stats, selected = state.reasonFilter, onToggle = viewModel::toggleReason)
    }

    FiltersCard(state, viewModel)

    when {
        state.listLoading -> repeat(SkeletonRows) { SkeletonRow() }
        state.listFailed -> ListMessage("Failed to load leave feedback", MaterialTheme.colorScheme.tertiary)
        state.entries.isEmpty() -> ListMessage(
            "No feedback matches the current filters.",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            state.entries.forEach { entry ->
                FeedbackCard(
                    entry = entry,
                    expanded = state.expandedId == entry.id,
                    onToggleComment = { viewModel.toggleComment(entry.id) },
                    onDelete = { viewModel.requestDelete(entry) },
                )
            }
            Pagination(state, viewModel::changePage)
        }
    }
}

/** The six stat tiles, two per row; zeros until the stats load. */
@Composable
private fun StatGrid(stats: LeaveFeedbackStats?) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val accent = MaterialTheme.colorScheme.tertiary
    val tiles = listOf(
        Tile("Prompts sent", (stats?.total ?: 0).withSeparators(), Icons.AutoMirrored.Filled.Send, secondary),
        Tile("Answered", (stats?.answered ?: 0).withSeparators(), Icons.Default.MarkChatRead, primary),
        Tile("With a comment", (stats?.withComment ?: 0).withSeparators(), Icons.Default.RateReview, primary),
        Tile("Dismissed", (stats?.dismissed ?: 0).withSeparators(), Icons.Default.Close, accent),
        Tile("No response", (stats?.pending ?: 0).withSeparators(), Icons.Default.HourglassEmpty, secondary),
        Tile("Response rate", "${stats?.responseRate ?: 0}%", Icons.Default.BarChart, primary),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        modifier = Modifier.weight(1f),
                        tint = tile.tone,
                        icon = tile.icon,
                    )
                }
            }
        }
    }
}

/** One stat tile's content. */
private data class Tile(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val tone: Color,
)

/**
 * Reasons at least one owner picked, most picked first. Tapping a row
 * filters the list to it, or clears the filter when it is already selected.
 */
@Composable
private fun ReasonsCard(
    stats: LeaveFeedbackStats,
    selected: String?,
    onToggle: (String) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    SectionCard {
        SectionCardHeader("Reasons given", Icons.Default.PieChart)
        stats.reasonsGiven.forEach { reason ->
            val isSelected = reason.key == selected
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) primary.copy(alpha = DashAlpha.Hex10) else Color.Transparent)
                    .clickable { onToggle(reason.key) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reason.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) readableInk(primary) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = reason.count.withSeparators(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ReasonBar(fraction = stats.barFraction(reason), color = primary)
            }
        }
    }
}

/** A rounded track filled to [fraction] in [color]. */
@Composable
private fun ReasonBar(fraction: Float, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(color.copy(alpha = DashAlpha.Hex15)),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

/**
 * The reason and status pickers, the search field, and Refresh. Any filter
 * change resets to page one and refetches the list and stats.
 */
@Composable
private fun FiltersCard(state: LeaveFeedbackState, viewModel: LeaveFeedbackViewModel) {
    val reasonOptions = remember(state.stats) {
        listOf(SelectorOption("", "All reasons")) +
            state.stats?.reasons.orEmpty().map { SelectorOption(it.key, "${it.label} (${it.count})") }
    }
    SectionCard {
        SectionCardHeader("Filters", Icons.Default.FilterList)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.FilterList),
            options = reasonOptions,
            placeholder = "All reasons",
            label = "Reason",
            selectedId = state.reasonFilter,
            onSelect = viewModel::setReasonFilter,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Checklist),
            options = StatusOptions,
            placeholder = "All statuses",
            label = "Status",
            selectedId = state.statusFilter,
            onSelect = viewModel::setStatusFilter,
        )
        SearchBox(
            value = state.searchInput,
            onValueChange = viewModel::setSearchInput,
            onApply = viewModel::applySearch,
            onClear = viewModel::clearSearch,
        )
        FilledTonalButton(
            onClick = viewModel::refreshList,
            enabled = !state.listLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Refresh", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * The search field. It applies on the keyboard's search action or when it
 * loses focus, never on each keystroke.
 */
@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Search") },
        placeholder = { Text("Server name or comment") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focus.clearFocus()
            onApply()
        }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) onApply()
                focused = it.isFocused
            },
    )
}

/** A placeholder card shaped like a feedback row while the page loads. */
@Composable
private fun SkeletonRow() {
    SectionCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "No response",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.skeleton(visible = true),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Sep 24, 2026, 12:00",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.skeleton(visible = true),
            )
        }
        Text(
            text = "A server name here",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.skeleton(visible = true),
        )
        Text(
            text = "000000000000000000 · 0 members",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.skeleton(visible = true),
        )
    }
}

/** A centered line in place of the list, for the empty and error states. */
@Composable
private fun ListMessage(text: String, color: Color) {
    SectionCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

/**
 * One record as a card: the status pill and date, the server and its
 * details, the reason, the comment behind a toggle, and Delete.
 */
@Composable
private fun FeedbackCard(
    entry: LeaveFeedbackEntry,
    expanded: Boolean,
    onToggleComment: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    val (statusLabel, statusTone) = when {
        entry.dismissed -> "Dismissed" to accent
        entry.answeredAt != null -> "Answered" to MaterialTheme.colorScheme.primary
        else -> "No response" to MaterialTheme.colorScheme.secondary
    }
    SectionCard(contentPadding = 12) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatePill(text = statusLabel, tone = statusTone)
            Spacer(Modifier.weight(1f))
            Text(
                text = entry.addedInstant?.shortDateTime() ?: "Unknown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                append(entry.guildId)
                append(" · ")
                append(entry.memberCount.withSeparators())
                append(" members")
                entry.tenure?.let { append(" · stayed ").append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = entry.reasonLabel?.takeIf { it.isNotBlank() } ?: "No reason selected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.reasonLabel.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        if (entry.hasComment && expanded) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex10),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = entry.comment.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = buildString {
                            append("Owner ").append(entry.ownerId)
                            entry.answeredInstant?.let { append(" · answered ").append(it.shortDateTime()) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.hasComment) {
                TextButton(onClick = onToggleComment) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (expanded) "Hide comment" else "Show comment",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = readableInk(accent)),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Delete record", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** The record count, page position, and Previous and Next. */
@Composable
private fun Pagination(state: LeaveFeedbackState, onPage: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${state.total.withSeparators()} records · page ${state.page} of ${state.totalPages}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(
                onClick = { onPage(state.page - 1) },
                enabled = state.page > 1 && !state.listLoading,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Previous", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = { onPage(state.page + 1) },
                enabled = state.page < state.totalPages && !state.listLoading,
            ) {
                Text("Next", modifier = Modifier.padding(end = 6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * The bot wide prompt toggle and report channel. Blank until the settings
 * load, with a retry if they fail.
 */
@Composable
private fun SettingsTab(state: LeaveFeedbackState, viewModel: LeaveFeedbackViewModel) {
    val settings = state.settings
    if (settings == null) {
        if (state.settingsLoadFailed && !state.settingsLoading) {
            SectionCard {
                Text(
                    text = "Failed to load the settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = viewModel::retrySettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Try again") }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val accent = MaterialTheme.colorScheme.tertiary
    val settingsError = state.settingsError
    SectionCard {
        SectionCardHeader(
            title = "Settings",
            icon = Icons.Default.Settings,
            trailing = {
                Text(
                    text = "Applies to the whole bot, not one server",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(140.dp),
                )
            },
        )
        SwitchRow(
            title = "Ask owners why the bot was removed",
            subtitle = "Sent right after the bot is removed, once per server every 30 days",
            checked = settings.enabled,
            onCheckedChange = viewModel::setEnabled,
            enabled = !state.savingSettings,
        )
        ChannelField(
            value = state.channelInput,
            onValueChange = viewModel::setChannelInput,
            enabled = !state.savingSettings,
        )
        Button(
            onClick = viewModel::saveChannel,
            enabled = state.channelDirty && !state.savingSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.savingSettings) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.savingSettings) "Saving..." else "Save channel")
        }

        val (line, tone) = when {
            settingsError != null -> settingsError to accent
            settings.hasNoEffectiveChannel ->
                "No channel is set and there is no join/leave channel to fall back to, so answers are " +
                    "not posted anywhere." to accent

            !settings.reachable ->
                "The bot cannot see channel ${settings.effectiveChannelId}, so answers will not be posted." to accent

            else -> buildString {
                append("Answers go to #")
                append(settings.channelName.orEmpty())
                settings.guildName?.takeIf { it.isNotBlank() }?.let { append(" in ").append(it) }
                if (settings.usingFallback) append(" (from the join/leave channel, since no channel is set)")
            } to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = if (tone == accent) readableInk(accent) else tone,
        )
    }
}

/** The report channel id field: digits only, monospaced, numeric keyboard. */
@Composable
private fun ChannelField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Report channel ID") },
        placeholder = { Text("Leave empty to use the join/leave channel") },
        leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}
