package dev.mewdeko.mobile.feature.wordoftheday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
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
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Settings),
    SectionTab("schedule", "Schedule", Icons.Default.CalendarMonth),
    SectionTab("words", "Words", Icons.Default.MenuBook),
    SectionTab("history", "History", Icons.Default.History),
)

private val HourOptions = (0..23).map { SelectorOption(it.toString(), "%02d:00".format(it)) }
private val ModeOptions = WordSourceMode.entries.map { SelectorOption(it.value.toString(), it.label, it.blurb) }
private val PosOptions = WordPartOfSpeech.entries.map { SelectorOption(it.value.toString(), it.label) }
private val DifficultyOptions = WordDifficulty.entries.map { SelectorOption(it.value.toString(), it.label) }
private val RulePosOptions = listOf(SelectorOption("0", "Inherit")) + PosOptions.drop(1)
private val RuleDifficultyOptions = listOf(SelectorOption("0", "Inherit")) + DifficultyOptions.drop(1)

/** Daily vocabulary word: posting schedule, dictionary filters, weekday and month rules, custom words, and history. */
@Composable
fun WordOfTheDayScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: WordOfTheDayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<WordOfTheDayWord?>(null) }

    FeatureScaffold(
        title = "Word of the Day",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = viewModel::postNow, enabled = !state.isPosting) {
                Icon(Icons.Default.Send, contentDescription = "Post a word now")
            }
            IconButton(onClick = { pendingReset = true }) {
                Icon(Icons.Default.Restore, contentDescription = "Reset everything")
            }
        },
        floatingActionButton = {
            if (state.hasUnsavedChanges && state.section == "settings") {
                ExtendedFloatingActionButton(
                    onClick = viewModel::save,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save changes") },
                )
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "settings" -> SettingsSection(state, viewModel)
            "schedule" -> ScheduleSection(state, viewModel)
            "words" -> WordsSection(state, viewModel, onRemove = { pendingRemove = it })
            "history" -> HistorySection(state)
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "Reset Word of the Day?",
            message = "Removes the configuration, every custom word, all weekday and month rules, and the " +
                "posting history for this server.",
            confirmLabel = "Reset everything",
            onConfirm = {
                pendingReset = false
                viewModel.reset()
            },
            onDismiss = { pendingReset = false },
        )
    }

    pendingRemove?.let { word ->
        ConfirmDialog(
            title = "Remove \"${word.word}\"?",
            message = "It will no longer be picked from the custom list.",
            confirmLabel = "Remove",
            onConfirm = {
                pendingRemove = null
                viewModel.removeWord(word)
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
private fun SettingsSection(state: WordOfTheDayState, viewModel: WordOfTheDayViewModel) {
    if (state.enabled && state.channelId == null) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Posting is enabled but no channel is set. Pick a channel below or Word of the " +
                        "Day will never post.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    SectionCard {
        SectionCardHeader("Posting", Icons.Default.Send)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "No channel selected",
            label = "Channel",
            selectedId = state.channelId,
            onSelect = viewModel::setChannel,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Schedule),
            options = HourOptions,
            placeholder = "09:00",
            label = "Post at",
            selectedId = state.postHour.toString(),
            onSelect = { viewModel.setPostHour(it?.toIntOrNull() ?: 9) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Public),
            options = WordOfTheDayTimezone.presets.map { SelectorOption(it.id, it.label) },
            placeholder = "UTC",
            label = "Timezone",
            selectedId = state.timezone,
            onSelect = { viewModel.setTimezone(it ?: "UTC") },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
            placeholder = "No ping role",
            label = "Ping role",
            selectedId = state.pingRoleId,
            onSelect = viewModel::setPingRole,
        )
        SwitchRow(
            title = "Post a word every day",
            subtitle = if (state.channelId != null) "Runs at the hour above in the selected timezone"
            else "Pick a channel first",
            checked = state.enabled,
            enabled = state.channelId != null,
            onCheckedChange = viewModel::setEnabled,
        )
    }

    SectionCard {
        SectionCardHeader("Word source", Icons.Default.Tune)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.MenuBook),
            options = ModeOptions,
            placeholder = "Dictionary",
            label = "Source",
            selectedId = state.sourceMode.toString(),
            onSelect = { viewModel.setSourceMode(it?.toIntOrNull() ?: 0) },
        )
        MewdekoTextField(
            value = state.topic,
            onValueChange = viewModel::setTopic,
            label = "Topic",
            placeholder = "e.g. science, cooking, space",
            supportingText = "Up to five words the dictionary should lean toward. Leave empty for general vocabulary.",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Tag),
            options = PosOptions,
            placeholder = "Any",
            label = "Part of speech",
            selectedId = state.partOfSpeech.toString(),
            onSelect = { viewModel.setPartOfSpeech(it?.toIntOrNull() ?: 0) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Tune),
            options = DifficultyOptions,
            placeholder = "Any",
            label = "Difficulty",
            selectedId = state.difficulty.toString(),
            onSelect = { viewModel.setDifficulty(it?.toIntOrNull() ?: 0) },
        )
        Text(
            text = "Difficulty is based on how often a word appears in written English. Weekday and month " +
                "rules on the Schedule tab override these filters.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard {
        SectionCardHeader("Message template", Icons.Default.ChatBubble)
        EmbedMessageEditor(
            message = state.message,
            onMessageChange = viewModel::setMessage,
        )
        Text(
            text = "Leave empty for the built-in embed. Placeholders: %wotd.word%, %wotd.definition%, " +
                "%wotd.pos%, %wotd.example%, %wotd.phonetic%, %wotd.date%, %wotd.ping%, %server.name%.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard {
        SectionCardHeader("Current configuration", Icons.Default.Info)
        InfoRow(
            label = "Status",
            value = if (state.enabled) "Enabled" else "Disabled",
            valueColor = if (state.enabled) MaterialTheme.colorScheme.primary else null,
        )
        InfoRow(
            label = "Channel",
            value = state.availableChannels.firstOrNull { it.id == state.channelId }
                ?.let { "#${it.name}" } ?: "Not set",
        )
        InfoRow("Posts at", "%02d:00 %s".format(state.postHour, state.timezone))
        InfoRow(
            label = "Ping role",
            value = state.availableRoles.firstOrNull { it.id == state.pingRoleId }?.name ?: "None",
        )
        InfoRow("Source", WordSourceMode.from(state.sourceMode).label)
        InfoRow("Custom words", state.words.size.toString())
        InfoRow("Schedule rules", (state.dayDrafts + state.monthDrafts).count { it.exists }.toString())
        InfoRow("Last posted", state.lastPosted)
    }
}

@Composable
private fun ScheduleSection(state: WordOfTheDayState, viewModel: WordOfTheDayViewModel) {
    Text(
        text = "Each rule only overrides what it sets and inherits the rest from Settings. Weekday rules " +
            "win over month rules.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    SectionCard {
        SectionCardHeader("Weekdays", Icons.Default.CalendarMonth)
        state.dayDrafts.forEach { RuleEditor(it, viewModel) }
    }

    SectionCard {
        SectionCardHeader("Months", Icons.Default.CalendarMonth)
        state.monthDrafts.forEach { RuleEditor(it, viewModel) }
    }
}

@Composable
private fun RuleEditor(draft: RuleDraft, viewModel: WordOfTheDayViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(draft.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = draft.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (draft.exists) {
                TagChip("Active")
            }
        }
        MewdekoTextField(
            value = draft.topic,
            onValueChange = { value -> viewModel.updateDraft(draft.id) { it.copy(topic = value) } },
            label = "Topic",
            placeholder = "Optional",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Tag),
                options = RulePosOptions,
                placeholder = "Inherit",
                label = "Part of speech",
                selectedId = draft.partOfSpeech.toString(),
                onSelect = { value -> viewModel.updateDraft(draft.id) { it.copy(partOfSpeech = value?.toIntOrNull() ?: 0) } },
                modifier = Modifier.weight(1f),
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Tune),
                options = RuleDifficultyOptions,
                placeholder = "Inherit",
                label = "Difficulty",
                selectedId = draft.difficulty.toString(),
                onSelect = { value -> viewModel.updateDraft(draft.id) { it.copy(difficulty = value?.toIntOrNull() ?: 0) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveRule(draft) }, enabled = !draft.saving) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("  Save")
            }
            if (draft.exists) {
                OutlinedButton(onClick = { viewModel.clearRule(draft) }, enabled = !draft.saving) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text("  Clear")
                }
            }
        }
    }
}

@Composable
private fun WordsSection(
    state: WordOfTheDayState,
    viewModel: WordOfTheDayViewModel,
    onRemove: (WordOfTheDayWord) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Add a word", Icons.Default.Add)
        MewdekoTextField(
            value = state.newWord,
            onValueChange = viewModel::setNewWord,
            label = "Word",
        )
        MewdekoTextField(
            value = state.newDefinition,
            onValueChange = viewModel::setNewDefinition,
            label = "Definition",
            placeholder = "Optional, looked up if empty",
            singleLine = false,
            minLines = 2,
        )
        Button(
            onClick = viewModel::addWord,
            enabled = !state.isAddingWord && state.newWord.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isAddingWord) "Adding…" else "Add to list")
        }
    }

    SectionCard {
        SectionCardHeader("Custom words (${state.words.size})", Icons.Default.MenuBook)
        if (state.words.isEmpty()) {
            EmptyState(
                "No custom words yet. Add some above, then set the source to Custom or Mixed.",
                icon = Icons.Default.MenuBook,
            )
        } else {
            state.words.forEach { word ->
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(word.word, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            word.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            word.definition ?: "No definition, one will be looked up when posted",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TagChip("×${word.timesUsed}")
                            IconButton(onClick = { onRemove(word) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${word.word}")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun HistorySection(state: WordOfTheDayState) {
    SectionCard {
        SectionCardHeader("Recent words", Icons.Default.History)
        if (state.history.isEmpty()) {
            EmptyState("Nothing posted yet. Words appear here after the first post.", icon = Icons.Default.History)
        } else {
            state.history.forEach { entry ->
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.word, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            entry.phonetic?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            entry.partOfSpeech?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.definition, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            entry.example?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    "\"$it\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        entry.postedOn?.let { TagChip(it.shortDate()) }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
