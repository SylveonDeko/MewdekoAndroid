package dev.mewdeko.mobile.feature.suggestions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.LabelledEmbedField
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("list", "Suggestions", Icons.Default.TipsAndUpdates),
    SectionTab("settings", "Settings", Icons.Default.Tune),
)

/** The member suggestion box. */
@Composable
fun SuggestionsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<SuggestionRecord?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var changingState by remember {
        mutableStateOf<Pair<SuggestionRecord, SuggestionState>?>(null)
    }

    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Suggestions",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            if (state.suggestions.isNotEmpty()) {
                IconButton(onClick = { pendingClearAll = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear all",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.hasUnsavedSettings) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveSettings,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save settings") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.TipsAndUpdates)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Total", "${state.suggestions.size}", Modifier.weight(1f))
                StatTile(
                    label = "Open",
                    value = "${state.countFor(SuggestionState.SUGGESTED)}",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Accepted",
                    value = "${state.countFor(SuggestionState.ACCEPTED)}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Denied",
                    value = "${state.countFor(SuggestionState.DENIED)}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        if (state.section == "settings") {
            SectionCard {
                SectionCardHeader("Channels", Icons.Default.Tune)
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "No channel",
                    label = "Submissions",
                    selectedId = state.settings.suggestChannel,
                    onSelect = { id -> viewModel.edit { it.copy(suggestChannel = id) } },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Same as submissions",
                    label = "Accepted",
                    selectedId = state.settings.acceptChannel,
                    onSelect = { id -> viewModel.edit { it.copy(acceptChannel = id) } },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Same as submissions",
                    label = "Denied",
                    selectedId = state.settings.denyChannel,
                    onSelect = { id -> viewModel.edit { it.copy(denyChannel = id) } },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Same as submissions",
                    label = "Considered",
                    selectedId = state.settings.considerChannel,
                    onSelect = { id -> viewModel.edit { it.copy(considerChannel = id) } },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Same as submissions",
                    label = "Implemented",
                    selectedId = state.settings.implementChannel,
                    onSelect = { id -> viewModel.edit { it.copy(implementChannel = id) } },
                )
            }

            SectionCard {
                SectionCardHeader("Length limits", Icons.Default.Tune)
                SliderRow(
                    label = "Minimum length",
                    value = state.settings.minLength.toFloat(),
                    onValueChange = { value ->
                        viewModel.edit { it.copy(minLength = value.toInt()) }
                    },
                    valueRange = 0f..500f,
                    valueLabel = "${state.settings.minLength}",
                )
                SliderRow(
                    label = "Maximum length",
                    value = state.settings.maxLength.toFloat(),
                    onValueChange = { value ->
                        viewModel.edit { it.copy(maxLength = value.toInt()) }
                    },
                    valueRange = 100f..4000f,
                    valueLabel = "${state.settings.maxLength}",
                )
            }

            SectionCard {
                SectionCardHeader("Reactions", Icons.Default.Tune)
                MewdekoTextField(
                    value = state.settings.emotes,
                    onValueChange = { value -> viewModel.edit { it.copy(emotes = value) } },
                    label = "Vote emotes",
                    placeholder = "👍 👎",
                    supportingText = "Up to five space-separated emoji added to each suggestion.",
                )
            }

            SectionCard {
                SectionCardHeader("Archiving", Icons.Default.Tune)
                SwitchRow(
                    title = "Archive on accept",
                    checked = state.settings.archiveOnAccept,
                    onCheckedChange = { value ->
                        viewModel.edit { it.copy(archiveOnAccept = value) }
                    },
                )
                SwitchRow(
                    title = "Archive on deny",
                    checked = state.settings.archiveOnDeny,
                    onCheckedChange = { value -> viewModel.edit { it.copy(archiveOnDeny = value) } },
                )
                SwitchRow(
                    title = "Archive on consider",
                    checked = state.settings.archiveOnConsider,
                    onCheckedChange = { value ->
                        viewModel.edit { it.copy(archiveOnConsider = value) }
                    },
                )
                SwitchRow(
                    title = "Archive on implement",
                    checked = state.settings.archiveOnImplement,
                    onCheckedChange = { value ->
                        viewModel.edit { it.copy(archiveOnImplement = value) }
                    },
                )
            }

            SectionCard {
                SectionCardHeader("Messages", Icons.Default.Tune)
                LabelledEmbedField(
                    label = "Submission template",
                    raw = state.settings.suggestionMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(suggestionMessage = value) }
                    },
                )
                LabelledEmbedField(
                    label = "Accepted template",
                    raw = state.settings.acceptMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(acceptMessage = value) }
                    },
                )
                LabelledEmbedField(
                    label = "Denied template",
                    raw = state.settings.denyMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(denyMessage = value) }
                    },
                )
                LabelledEmbedField(
                    label = "Considered template",
                    raw = state.settings.considerMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(considerMessage = value) }
                    },
                )
                LabelledEmbedField(
                    label = "Implemented template",
                    raw = state.settings.implementMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(implementMessage = value) }
                    },
                )
            }
            return@FeatureScaffold
        }

        SectionCard(contentPadding = 12) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.stateFilter == null,
                    onClick = { viewModel.setStateFilter(null) },
                    label = { Text("All") },
                )
                SuggestionState.entries.forEach { entry ->
                    FilterChip(
                        selected = state.stateFilter == entry,
                        onClick = {
                            viewModel.setStateFilter(if (state.stateFilter == entry) null else entry)
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        }

        if (state.visible.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No suggestions to show.",
                    icon = Icons.Default.TipsAndUpdates,
                )
            }
        } else {
            state.visible.forEach { suggestion ->
                SectionCard(contentPadding = 12) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Avatar(
                            url = suggestion.user?.avatarUrl,
                            contentDescription = suggestion.user?.username,
                            size = 32,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${suggestion.number} · " +
                                    (suggestion.user?.username ?: "Unknown"),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            suggestion.dateAdded?.let {
                                Text(
                                    text = it.relativeToNow(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TagChip(suggestion.state.label)
                        IconButton(onClick = { pendingDelete = suggestion }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete suggestion",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        text = suggestion.suggestion1,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    suggestion.emoteCounts?.let { counts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            counts.values.forEachIndexed { index, value ->
                                if (value > 0) TagChip("${index + 1}: $value")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                changingState = suggestion to SuggestionState.ACCEPTED
                            },
                        ) { Text("Accept") }
                        TextButton(
                            onClick = { changingState = suggestion to SuggestionState.DENIED },
                        ) { Text("Deny") }
                        TextButton(
                            onClick = { changingState = suggestion to SuggestionState.CONSIDERED },
                        ) { Text("Consider") }
                        TextButton(
                            onClick = { changingState = suggestion to SuggestionState.IMPLEMENTED },
                        ) { Text("Implement") }
                    }
                }
            }
        }
    }

    changingState?.let { (suggestion, target) ->
        var reason by remember(suggestion.id, target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changingState = null },
            title = { Text("Mark ${target.label.lowercase()}") },
            text = {
                MewdekoTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason (optional)",
                    singleLine = false,
                    minLines = 2,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setState(suggestion, target, reason.takeIf { it.isNotBlank() })
                        changingState = null
                    },
                ) { Text(target.label) }
            },
            dismissButton = {
                TextButton(onClick = { changingState = null }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { suggestion ->
        ConfirmDialog(
            title = "Delete suggestion?",
            message = "Suggestion #${suggestion.number} is removed.",
            onConfirm = { viewModel.delete(suggestion) },
            onDismiss = { pendingDelete = null },
        )
    }

    if (pendingClearAll) {
        ConfirmDialog(
            title = "Clear every suggestion?",
            message = "All ${state.suggestions.size} suggestions are deleted. " +
                "This cannot be undone.",
            confirmLabel = "Clear all",
            onConfirm = viewModel::clearAll,
            onDismiss = { pendingClearAll = false },
        )
    }
}
