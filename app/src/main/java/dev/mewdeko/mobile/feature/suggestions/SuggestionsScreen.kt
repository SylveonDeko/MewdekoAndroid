package dev.mewdeko.mobile.feature.suggestions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapVert
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

private val ThreadTypeOptions = listOf(
    SelectorOption("0", "No threads"),
    SelectorOption("1", "Regular threads"),
    SelectorOption("2", "Private threads"),
)

private val EmoteModeOptions = listOf(
    SelectorOption("0", "Reactions"),
    SelectorOption("1", "Buttons"),
)

private val ButtonColorOptions = listOf(
    SelectorOption("1", "Blue"),
    SelectorOption("2", "Grey"),
    SelectorOption("3", "Green"),
    SelectorOption("4", "Red"),
)

private val SortOptions = listOf(
    SelectorOption(SuggestionSortBy.DATE.raw, SuggestionSortBy.DATE.label),
    SelectorOption(SuggestionSortBy.STATUS.raw, SuggestionSortBy.STATUS.label),
)

/** Placeholders the bot substitutes into every suggestion message template. */
private val SuggestionPlaceholders = listOf(
    "%suggest.user%" to "Full username of the suggester",
    "%suggest.user.id%" to "Id of the suggester",
    "%suggest.user.name%" to "Name of the suggester",
    "%suggest.user.avatar%" to "Avatar of the suggester",
    "%suggest.message%" to "The original suggestion text",
    "%suggest.number%" to "The suggestion's number",
    "%suggest.mod.user%" to "Full username of whoever updated it",
    "%suggest.mod.name%" to "Name of whoever updated it",
    "%suggest.mod.avatar%" to "Avatar of whoever updated it",
    "%suggest.mod.message%" to "The reason it was updated",
)

/** Expandable list of the placeholders available in suggestion templates. */
@Composable
private fun PlaceholderHint(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(
                if (expanded) "Hide placeholders" else "Show placeholders",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SuggestionPlaceholders.forEach { (name, description) ->
                    Text(
                        text = "$name: $description",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

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
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Same as submissions",
                    label = "Suggestion button posts to",
                    selectedId = state.settings.suggestButtonChannel,
                    onSelect = { id -> viewModel.edit { it.copy(suggestButtonChannel = id) } },
                )
            }

            SectionCard {
                SectionCardHeader("Behavior", Icons.Default.Forum)
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Forum),
                    options = ThreadTypeOptions,
                    placeholder = "No threads",
                    label = "Thread type",
                    selectedId = state.settings.threadsType.toString(),
                    onSelect = { id -> viewModel.edit { it.copy(threadsType = id?.toIntOrNull() ?: 0) } },
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
                    valueRange = 0f..2000f,
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
                SectionCardHeader("Emotes", Icons.Default.SmartButton)
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.SmartButton),
                    options = EmoteModeOptions,
                    placeholder = "Reactions",
                    label = "Display mode",
                    selectedId = state.settings.emoteMode.toString(),
                    onSelect = { id -> viewModel.edit { it.copy(emoteMode = id?.toIntOrNull() ?: 0) } },
                )
                SuggestionEmotePicker(
                    label = "Vote emotes",
                    selected = state.settings.emoteList,
                    guildEmotes = state.guildEmotes,
                    onSelectedChange = { values ->
                        viewModel.edit { it.copy(emotes = values.joinToString(",")) }
                    },
                    max = 5,
                    supportingText = "Up to five emotes added to each suggestion. Leave empty for the default " +
                        "👍/👎.",
                )
            }

            SectionCard {
                SectionCardHeader("Suggestion button", Icons.Default.SmartButton)
                MewdekoTextField(
                    value = state.settings.suggestButtonLabel,
                    onValueChange = { value -> viewModel.edit { it.copy(suggestButtonLabel = value) } },
                    label = "Button label",
                    placeholder = "Suggest",
                )
                SuggestionEmotePicker(
                    label = "Button emote",
                    selected = state.settings.suggestButtonEmote
                        .takeIf { it.isNotBlank() }
                        ?.let { listOf(it) }
                        ?: emptyList(),
                    guildEmotes = state.guildEmotes,
                    onSelectedChange = { values ->
                        viewModel.edit { it.copy(suggestButtonEmote = values.firstOrNull().orEmpty()) }
                    },
                    max = 1,
                    supportingText = "Shown on the suggest button. Leave empty for none.",
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Palette),
                    options = ButtonColorOptions,
                    placeholder = "Blue",
                    label = "Button color",
                    selectedId = state.settings.suggestButtonColor.toString(),
                    onSelect = { id ->
                        viewModel.edit { it.copy(suggestButtonColor = id?.toIntOrNull() ?: 1) }
                    },
                )
                LabelledEmbedField(
                    label = "Button message",
                    raw = state.settings.suggestButtonMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(suggestButtonMessage = value) }
                    },
                )
                PlaceholderHint()
            }

            SectionCard {
                SectionCardHeader("Emote button colors", Icons.Default.Palette)
                state.settings.emoteButtonStyles.forEachIndexed { index, style ->
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Palette),
                        options = ButtonColorOptions,
                        placeholder = "Blue",
                        label = "Emote ${index + 1} button",
                        selectedId = style.toString(),
                        onSelect = { id ->
                            viewModel.edit {
                                it.copy(
                                    emoteButtonStyles = it.emoteButtonStyles.toMutableList().apply {
                                        this[index] = id?.toIntOrNull() ?: 1
                                    },
                                )
                            }
                        },
                    )
                }
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
                PlaceholderHint()
                LabelledEmbedField(
                    label = "Accepted template",
                    raw = state.settings.acceptMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(acceptMessage = value) }
                    },
                )
                PlaceholderHint()
                LabelledEmbedField(
                    label = "Denied template",
                    raw = state.settings.denyMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(denyMessage = value) }
                    },
                )
                PlaceholderHint()
                LabelledEmbedField(
                    label = "Considered template",
                    raw = state.settings.considerMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(considerMessage = value) }
                    },
                )
                PlaceholderHint()
                LabelledEmbedField(
                    label = "Implemented template",
                    raw = state.settings.implementMessage,
                    onRawChange = { value ->
                        viewModel.edit { it.copy(implementMessage = value) }
                    },
                )
                PlaceholderHint()
            }
            return@FeatureScaffold
        }

        SectionCard(contentPadding = 12) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Sort),
                    options = SortOptions,
                    placeholder = "Date",
                    selectedId = state.sortBy.raw,
                    onSelect = { id ->
                        viewModel.setSortBy(
                            SuggestionSortBy.entries.firstOrNull { it.raw == id } ?: SuggestionSortBy.DATE,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::toggleSortDirection) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = if (state.sortDescending) "Descending" else "Ascending",
                    )
                }
            }
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TagChip("ID: ${suggestion.suggestionId}")
                        suggestion.stateChangeUser?.let { TagChip("Modified by: $it") }
                        if (suggestion.stateChangeCount > 0) {
                            TagChip("Changes: ${suggestion.stateChangeCount}")
                        }
                    }
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
