package dev.mewdeko.mobile.feature.featurerequests

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDateTime

/** Tabs every signed-in user sees. */
private val UserTabs = listOf(
    SectionTab(FeatureRequestSections.BROWSE, "Browse", Icons.Default.Lightbulb),
    SectionTab(FeatureRequestSections.SUGGEST, "Suggest", Icons.AutoMirrored.Filled.Send),
    SectionTab(FeatureRequestSections.MINE, "Mine", Icons.Default.Person),
)

/** Tabs the bot owner sees, adding the report channel settings. */
private val OwnerTabs = UserTabs + SectionTab(FeatureRequestSections.SETTINGS, "Settings", Icons.Default.Settings)

/** Status choices for the owner status editor. */
private val StatusOptions = FeatureRequestStatus.entries.map { SelectorOption(it.key, it.label) }

/** Category choices for the Suggest form, where a category is required. */
private val CategoryOptions = FeatureRequestCategory.entries.map { SelectorOption(it.key, it.label) }

/**
 * Browse status choices, with a sentinel "Any status" entry (empty id) so the
 * filter can be cleared back to its unfiltered state. [FeatureRequestsViewModel.setStatusFilter]
 * already treats an empty key as null.
 */
private val BrowseStatusOptions = listOf(SelectorOption("", "Any status")) + StatusOptions

/**
 * Browse category choices, with a sentinel "Any category" entry (empty id) so
 * the filter can be cleared back to its unfiltered state. [FeatureRequestsViewModel.setCategoryFilter]
 * already treats an empty key as null.
 */
private val BrowseCategoryOptions = listOf(SelectorOption("", "Any category")) + CategoryOptions

/**
 * The bot wide feature request board: browse, filter, and upvote requests,
 * suggest new ones, follow your own, and (for the bot owner) triage them and
 * pick where reports are posted.
 */
@Composable
fun FeaturerequestsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: FeatureRequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Feature Requests",
        subtitle = "Suggest features, report bugs, and upvote ideas",
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(
            tabs = if (state.isOwner) OwnerTabs else UserTabs,
            selectedId = state.section,
            onSelect = viewModel::setSection,
        )

        when (state.section) {
            FeatureRequestSections.SUGGEST -> SuggestSection(state, guild, viewModel)
            FeatureRequestSections.MINE -> MineSection(state, viewModel)
            FeatureRequestSections.SETTINGS -> if (state.isOwner) {
                SettingsSection(state, viewModel)
            } else {
                BrowseSection(state, viewModel)
            }
            else -> BrowseSection(state, viewModel)
        }
    }

    state.statusTarget?.let { target ->
        StatusDialog(
            target = target,
            choice = state.statusChoice,
            note = state.statusNote,
            saving = state.savingStatus,
            error = state.statusError,
            onChoice = viewModel::setStatusChoice,
            onNote = viewModel::setStatusNote,
            onSave = viewModel::saveStatus,
            onDismiss = viewModel::dismissStatus,
        )
    }

    state.deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete this request?",
            message = "\"${target.title}\" and its votes will be removed. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}

@Composable
private fun BrowseSection(state: FeatureRequestsState, viewModel: FeatureRequestsViewModel) {
    val stats = state.stats
    if (state.isOwner && stats != null) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Insights)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Total", "${stats.total}", Modifier.weight(1f), icon = Icons.Default.Lightbulb)
                StatTile(
                    "Open",
                    "${stats.byStatus[FeatureRequestStatus.OPEN.key] ?: 0}",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.secondary,
                    icon = Icons.Default.HourglassEmpty,
                )
                StatTile(
                    "Planned",
                    "${stats.byStatus[FeatureRequestStatus.PLANNED.key] ?: 0}",
                    Modifier.weight(1f),
                    icon = Icons.Default.AutoAwesome,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    "Done",
                    "${stats.byStatus[FeatureRequestStatus.DONE.key] ?: 0}",
                    Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle,
                )
                StatTile(
                    "Declined",
                    "${stats.byStatus[FeatureRequestStatus.DECLINED.key] ?: 0}",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.Cancel,
                )
                StatTile(
                    "Bugs",
                    "${stats.byCategory[FeatureRequestCategory.BUG.key] ?: 0}",
                    Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.error,
                    icon = Icons.Default.BugReport,
                )
            }
        }
    }

    SectionCard {
        SectionCardHeader("Filters", Icons.Default.FilterList)
        SearchBox(
            value = state.search,
            onValueChange = viewModel::setSearch,
            onSearch = viewModel::applyFilters,
            onClear = viewModel::clearSearch,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Flag),
            options = BrowseStatusOptions,
            placeholder = "Any status",
            label = "Status",
            selectedId = state.statusFilter,
            onSelect = viewModel::setStatusFilter,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Lightbulb),
            options = BrowseCategoryOptions,
            placeholder = "Any category",
            label = "Category",
            selectedId = state.categoryFilter,
            onSelect = viewModel::setCategoryFilter,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.sort == FeatureRequestSort.VOTES,
                onClick = { viewModel.setSort(FeatureRequestSort.VOTES) },
                label = { Text("Most votes") },
                leadingIcon = { Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
            FilterChip(
                selected = state.sort == FeatureRequestSort.NEWEST,
                onClick = { viewModel.setSort(FeatureRequestSort.NEWEST) },
                label = { Text("Newest") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }

    if (state.pageLoading && state.entries.isNotEmpty()) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    val pageError = state.pageError
    when {
        state.pageLoading && state.entries.isEmpty() -> InlineSpinner()
        pageError != null -> InlineError(pageError, onRetry = viewModel::retryPage)
        state.entries.isEmpty() -> SectionCard {
            EmptyState("Nothing here yet. Be the first to suggest something.", icon = Icons.Default.Lightbulb)
            TextButton(
                onClick = { viewModel.setSection(FeatureRequestSections.SUGGEST) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Suggest a feature") }
        }
        else -> {
            state.entries.forEach { entry ->
                RequestCard(
                    entry = entry,
                    expanded = state.expandedId == entry.id,
                    showOwnerActions = state.isOwner,
                    viewModel = viewModel,
                )
            }
            if (state.totalPages > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = { viewModel.changePage(state.page - 1) },
                        enabled = state.page > 1 && !state.pageLoading,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Previous", modifier = Modifier.padding(start = 6.dp))
                    }
                    Text(
                        text = "Page ${state.page} of ${state.totalPages}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { viewModel.changePage(state.page + 1) },
                        enabled = state.page < state.totalPages && !state.pageLoading,
                    ) {
                        Text("Next", modifier = Modifier.padding(end = 6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestSection(
    state: FeatureRequestsState,
    guild: GuildRouteArgs,
    viewModel: FeatureRequestsViewModel,
) {
    val form = state.form
    state.submitted?.let { submitted ->
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Thanks!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Your request \"${submitted.title}\" was sent to the developers. " +
                        "You can follow it under the Mine tab.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::dismissSubmitted) { Text("Submit another") }
                    TextButton(onClick = { viewModel.setSection(FeatureRequestSections.MINE) }) { Text("View mine") }
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("New request", Icons.AutoMirrored.Filled.Send)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(FeatureRequestCategory.of(form.category).icon),
            options = CategoryOptions,
            placeholder = "Pick a category",
            label = "What kind of request is this?",
            selectedId = form.category,
            onSelect = viewModel::setFormCategory,
            enabled = !state.submitting,
        )
        MewdekoTextField(
            value = form.title,
            onValueChange = viewModel::setFormTitle,
            label = "Title",
            placeholder = "One line that sums it up",
            enabled = !state.submitting,
            supportingText = counterText(form.title.trim().length, FeatureRequestDraft.TITLE_MIN, form.titleRemaining),
            isError = form.titleRemaining < 0,
        )
        MewdekoTextField(
            value = form.body,
            onValueChange = viewModel::setFormBody,
            label = "Details",
            placeholder = "What should it do? If it is a bug, what happened and what did you expect instead?",
            singleLine = false,
            minLines = 6,
            enabled = !state.submitting,
            supportingText = counterText(form.body.trim().length, FeatureRequestDraft.BODY_MIN, form.bodyRemaining),
            isError = form.bodyRemaining < 0,
        )
        if (guild.id.isNotEmpty()) {
            SwitchRow(
                title = "Attach this server",
                subtitle = "Mention that I was managing ${guild.name.ifEmpty { "this server" }}",
                checked = form.attachGuild,
                onCheckedChange = viewModel::setAttachGuild,
                enabled = !state.submitting,
            )
        }
        state.submitError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Text(
                text = if (state.submitting) "Sending..." else "Send to the developers",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = "Up to three requests an hour. Your Discord name is attached so we can follow up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MineSection(state: FeatureRequestsState, viewModel: FeatureRequestsViewModel) {
    val mineError = state.mineError
    when {
        state.mineLoading && state.mine.isEmpty() -> InlineSpinner()
        mineError != null && state.mine.isEmpty() -> InlineError(mineError, onRetry = viewModel::retryMine)
        state.mine.isEmpty() -> SectionCard {
            EmptyState("You have not submitted anything yet.", icon = Icons.Default.Lightbulb)
            TextButton(
                onClick = { viewModel.setSection(FeatureRequestSections.SUGGEST) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Suggest something") }
        }
        else -> state.mine.forEach { entry ->
            RequestCard(
                entry = entry,
                expanded = state.expandedId == entry.id,
                showOwnerActions = state.isOwner,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun SettingsSection(state: FeatureRequestsState, viewModel: FeatureRequestsViewModel) {
    val settings = state.settings
    val settingsError = state.settingsError
    if (settings == null) {
        when {
            state.settingsLoading -> InlineSpinner()
            else -> InlineError(settingsError ?: "Failed to load the settings.", onRetry = viewModel::retrySettings)
        }
        return
    }

    SectionCard {
        SectionCardHeader("Report channel", Icons.Default.Tag)
        Text(
            text = "Applies to the whole bot, not one server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(
            value = state.channelInput,
            onValueChange = viewModel::setChannelInput,
            label = "Channel ID new requests get posted to",
            placeholder = "Leave empty to use the join/leave channel",
            numeric = true,
            enabled = !state.savingSettings,
        )
        Button(
            onClick = viewModel::saveChannel,
            enabled = state.channelDirty && !state.savingSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.savingSettings) "Saving..." else "Save channel")
        }

        val destination: Pair<String, Color> = when {
            settingsError != null -> Pair(settingsError, MaterialTheme.colorScheme.error)
            settings.hasNoEffectiveChannel -> Pair(
                "No channel is set and there is no join/leave channel to fall back to, so requests are " +
                    "only stored, not posted.",
                MaterialTheme.colorScheme.error,
            )
            !settings.reachable -> Pair(
                "The bot cannot see channel ${settings.effectiveChannelId}, so requests will not be posted.",
                MaterialTheme.colorScheme.error,
            )
            else -> Pair(
                buildString {
                    append("Requests go to #")
                    append(settings.channelName.orEmpty())
                    settings.guildName?.takeIf { it.isNotBlank() }?.let { append(" in ").append(it) }
                    if (settings.usingFallback) append(" (from the join/leave channel, since no channel is set)")
                },
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = destination.first, style = MaterialTheme.typography.bodySmall, color = destination.second)
    }

    SectionCard {
        SectionCardHeader("Current destination", Icons.Default.Settings)
        InfoRow("Configured channel", settings.channelInputText.ifEmpty { "None (fallback)" })
        InfoRow(
            "Effective channel",
            if (settings.hasNoEffectiveChannel) "None" else settings.effectiveChannelId,
        )
        InfoRow("Channel name", settings.channelName?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: "Unknown")
        InfoRow("Server", settings.guildName?.takeIf { it.isNotBlank() } ?: "Unknown")
        InfoRow("Using join/leave fallback", if (settings.usingFallback) "Yes" else "No")
        InfoRow(
            "Reachable",
            if (settings.reachable) "Yes" else "No",
            valueColor = if (settings.reachable) null else MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestCard(
    entry: FeatureRequestEntry,
    expanded: Boolean,
    showOwnerActions: Boolean,
    viewModel: FeatureRequestsViewModel,
) {
    val category = FeatureRequestCategory.of(entry.category)
    SectionCard(contentPadding = 12) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VoteButton(votes = entry.votes, voted = entry.voted, onClick = { viewModel.toggleVote(entry) })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.toggleExpand(entry.id) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    StatusBadge(entry.status)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (entry.mine) {
                        Badge("Yours", MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(entry.userName.ifBlank { "Unknown" })
                        append(" · ")
                        append(entry.dateAdded?.shortDateTime() ?: "Unknown")
                        entry.guildName?.takeIf { it.isNotBlank() }?.let { append(" · from ").append(it) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!expanded) {
                    Text(
                        text = entry.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { viewModel.toggleExpand(entry.id) },
            )
        }

        if (expanded) {
            Text(text = entry.body, style = MaterialTheme.typography.bodyMedium)
            entry.ownerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Note from the developers",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(text = note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            entry.updatedAt?.let {
                Text(
                    text = "Updated ${it.shortDateTime()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showOwnerActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.openStatus(entry) }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Change status", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestDelete(entry) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Delete", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteButton(votes: Int, voted: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (voted) primary else primary.copy(alpha = 0.12f),
        contentColor = if (voted) MaterialTheme.colorScheme.onPrimary else primary,
        modifier = Modifier
            .width(56.dp)
            .semantics { contentDescription = if (voted) "Remove your vote" else "Upvote this request" },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("$votes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val tone = when (status) {
        FeatureRequestStatus.DECLINED.key -> MaterialTheme.colorScheme.error
        FeatureRequestStatus.OPEN.key -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Badge(FeatureRequestStatus.labelFor(status), tone)
}

@Composable
private fun Badge(label: String, tone: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tone.copy(alpha = 0.15f),
        contentColor = tone,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Search") },
        placeholder = { Text("Title, details, or submitter") },
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
            onSearch()
        }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusDialog(
    target: FeatureRequestEntry,
    choice: String,
    note: String,
    saving: Boolean,
    error: String?,
    onChoice: (String?) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Flag),
                    options = StatusOptions,
                    placeholder = "Pick a status",
                    label = "Status",
                    selectedId = choice,
                    onSelect = onChoice,
                    enabled = !saving,
                )
                MewdekoTextField(
                    value = note,
                    onValueChange = onNote,
                    label = "Note for the submitter (optional)",
                    singleLine = false,
                    minLines = 3,
                    enabled = !saving,
                    supportingText = "${FeatureRequestDraft.NOTE_MAX - note.length} left",
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !saving) {
                Text(if (saving) "Saving..." else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun InlineSpinner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    SectionCard {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Try again")
        }
    }
}

/** The length hint under a limited text field: a minimum reminder until met, then the characters left. */
private fun counterText(length: Int, minimum: Int, remaining: Int): String =
    if (length < minimum) "At least $minimum characters, $remaining left" else "$remaining left"
