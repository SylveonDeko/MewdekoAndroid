package dev.mewdeko.mobile.feature.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("filters", "Filters", Icons.Default.FilterAlt),
    SectionTab("words", "Words", Icons.Default.Forum),
    SectionTab("autoban", "Auto-ban", Icons.Default.Block),
    SectionTab("channels", "Channels", Icons.Default.Tag),
)

/** Word filter list size past which a search box is shown, matching the dashboard. */
private const val SearchThreshold = 10

/** A word awaiting removal confirmation, tagged with the list it belongs to. */
private data class PendingWordRemoval(val word: String, val autoBan: Boolean)

/** Icon shown beside each filter kind. */
private val FilterKind.icon: ImageVector
    get() = when (this) {
        FilterKind.WORD -> Icons.Default.Forum
        FilterKind.INVITE -> Icons.Default.PersonAddAlt
        FilterKind.LINK -> Icons.Default.Link
    }

/** Message Filters: server-wide word, invite, and link filters, warnings, word lists, and channel overrides. */
@Composable
fun FilterScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: FilterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClear by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<PendingWordRemoval?>(null) }

    FeatureScaffold(
        title = "Message Filters",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load(refreshing = true) }, enabled = !loadState.isLoading && !loadState.isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "filters" -> FiltersSection(state, viewModel)
            "words" -> WordListSection(
                state = state,
                autoBan = false,
                onAdd = viewModel::addFilteredWord,
                onRemove = { pendingRemove = PendingWordRemoval(it, autoBan = false) },
                onClearAll = { pendingClear = true },
                onInputChange = viewModel::setNewWord,
                onSearchChange = viewModel::setWordSearch,
            )
            "autoban" -> WordListSection(
                state = state,
                autoBan = true,
                onAdd = viewModel::addAutoBanWord,
                onRemove = { pendingRemove = PendingWordRemoval(it, autoBan = true) },
                onClearAll = {},
                onInputChange = viewModel::setNewAutoBanWord,
                onSearchChange = {},
            )
            "channels" -> ChannelsSection(state, viewModel)
        }
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "Clear filtered words?",
            message = "All ${state.settings.filteredWords.size} filtered words are removed.",
            confirmLabel = "Clear all",
            onConfirm = {
                pendingClear = false
                viewModel.clearFilteredWords()
            },
            onDismiss = { pendingClear = false },
        )
    }

    pendingRemove?.let { removal ->
        ConfirmDialog(
            title = "Remove \"${removal.word}\"?",
            message = if (removal.autoBan) "Members posting it will no longer be banned automatically."
            else "Messages containing it will no longer be deleted by the word filter.",
            confirmLabel = "Remove",
            onConfirm = {
                pendingRemove = null
                viewModel.removeWord(removal.word, removal.autoBan)
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
private fun FiltersSection(state: FilterState, viewModel: FilterViewModel) {
    val settings = state.settings
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Filtered words", "${settings.filteredWords.size}", Modifier.weight(1f), icon = Icons.Default.Forum)
        StatTile(
            "Auto-ban words",
            "${settings.autoBanWords.size}",
            Modifier.weight(1f),
            tint = MaterialTheme.colorScheme.error,
            icon = Icons.Default.Block,
        )
        StatTile("Channel overrides", "${state.channelOverrideCount}", Modifier.weight(1f), icon = Icons.Default.Tag)
    }

    SectionCard {
        SectionCardHeader("Server-wide filters", Icons.Default.FilterAlt)
        Text(
            text = "These apply in every channel. Use channel overrides to enable a filter only in specific places.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterKind.entries.forEach { kind ->
            SwitchRow(
                title = kind.title,
                subtitle = kind.hint,
                checked = kind.serverEnabled(settings.serverSettings),
                enabled = !state.isSaving,
                onCheckedChange = { viewModel.toggleServerFilter(kind) },
            )
        }
    }

    SectionCard {
        SectionCardHeader("Warnings", Icons.Default.Warning)
        Text(
            text = "Give the member a warning in addition to deleting the message. Warning punishments on the " +
                "Moderation page still apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilterWarning.entries.forEach { warning ->
            SwitchRow(
                title = warning.title,
                checked = warning.value(settings.serverSettings),
                enabled = !state.isSaving,
                onCheckedChange = { viewModel.toggleWarning(warning) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordListSection(
    state: FilterState,
    autoBan: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onInputChange: (String) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val allWords = if (autoBan) state.settings.autoBanWords else state.settings.filteredWords
    val visible = if (autoBan) allWords else state.visibleFilteredWords
    val input = if (autoBan) state.newAutoBanWord else state.newWord
    val icon = if (autoBan) Icons.Default.Block else Icons.Default.Forum

    SectionCard {
        SectionCardHeader(
            title = "${if (autoBan) "Auto-ban words" else "Filtered words"} (${visible.size})",
            icon = icon,
            tint = if (autoBan) MaterialTheme.colorScheme.error else null,
            trailing = if (!autoBan && allWords.isNotEmpty()) {
                {
                    TextButton(
                        onClick = onClearAll,
                        enabled = !state.isSaving,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Clear all")
                    }
                }
            } else {
                null
            },
        )
        Text(
            text = if (autoBan) "Members who post any of these words are banned immediately. Use with care."
            else "Messages containing these words are deleted when the word filter is enabled server-wide or in a channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(
            value = input,
            onValueChange = onInputChange,
            label = if (autoBan) "New auto-ban word" else "New filtered word",
            placeholder = "Add a word or phrase",
        )
        Button(
            onClick = onAdd,
            enabled = !state.isSaving && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("  Add")
        }

        if (!autoBan && allWords.size > SearchThreshold) {
            SearchField(
                value = state.wordSearch,
                onValueChange = onSearchChange,
                placeholder = "Search words",
            )
        }

        if (visible.isEmpty()) {
            EmptyState(
                message = if (!autoBan && state.wordSearch.isNotBlank()) "No words match your search."
                else "No words added yet.",
                icon = icon,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                visible.forEach { word ->
                    InputChip(
                        selected = autoBan,
                        onClick = { onRemove(word) },
                        enabled = !state.isSaving,
                        label = { Text(word) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove $word",
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelsSection(state: FilterState, viewModel: FilterViewModel) {
    FilterKind.entries.forEach { kind ->
        val selected = kind.channels(state.settings.channelSettings)
        val serverWide = kind.serverEnabled(state.settings.serverSettings)
        SectionCard {
            SectionCardHeader("${kind.title} channels", kind.icon)
            Text(
                text = if (serverWide) "The ${kind.title.lowercase()} is on server-wide, so these channel entries " +
                    "have no extra effect."
                else "Enable the ${kind.title.lowercase()} only in these channels.",
                style = MaterialTheme.typography.bodySmall,
                color = if (serverWide) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.channels
                    .filterNot { channel -> selected.contains(channel.id) }
                    .map { SelectorOption(it.id, it.name) },
                placeholder = "Add a channel",
                label = "Add channel",
                selectedId = null,
                onSelect = { id -> viewModel.toggleChannel(kind, id) },
                enabled = !state.isSaving,
            )
            if (selected.isEmpty()) {
                EmptyState("No channel overrides.", icon = kind.icon)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.forEach { id ->
                        val name = state.channelName(id)
                        InputChip(
                            selected = true,
                            onClick = { viewModel.toggleChannel(kind, id) },
                            enabled = !state.isSaving,
                            label = { Text("#$name") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove #$name",
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.channels.isEmpty()) {
        Text(
            text = "No text channels could be loaded. Pull to refresh to try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
