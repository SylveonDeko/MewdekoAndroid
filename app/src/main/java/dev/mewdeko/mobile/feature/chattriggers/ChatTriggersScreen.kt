package dev.mewdeko.mobile.feature.chattriggers

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedFooter
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.UrlBox
import dev.mewdeko.mobile.core.net.InstantParser
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.LabelledEmbedField
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.compact
import dev.mewdeko.mobile.util.relativeToNow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Custom keyword reactions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatTriggersScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ChatTriggersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<ChatTriggerModel?>(null) }
    var pendingDelete by remember { mutableStateOf<ChatTriggerModel?>(null) }
    var editingCounter by remember { mutableStateOf<TriggerCounter?>(null) }
    var pendingCounterDelete by remember { mutableStateOf<TriggerCounter?>(null) }

    FeatureScaffold(
        title = "Chat Triggers",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ChatTriggerModel.blank(guild.id) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New trigger") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Bolt)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Triggers", "${state.triggers.size}", Modifier.weight(1f))
                StatTile("Total fires", state.totalUses.compact(), Modifier.weight(1f))
                StatTile(
                    label = "Paused",
                    value = "${state.pausedCount}",
                    modifier = Modifier.weight(1f),
                )
            }
            SearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search triggers and responses",
            )
            if (state.categories.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(
                        label = "All",
                        icon = if (state.category == null) Icons.Default.Check else null,
                        onClick = { viewModel.setCategory(null) },
                    )
                    if (state.hasUngrouped) {
                        TagChip(
                            label = "Ungrouped",
                            icon = if (state.category == ChatTriggersState.UNGROUPED) Icons.Default.Check else null,
                            onClick = { viewModel.setCategory(ChatTriggersState.UNGROUPED) },
                        )
                    }
                    state.categories.forEach { category ->
                        TagChip(
                            label = category,
                            icon = if (state.category == category) Icons.Default.Check else null,
                            onClick = { viewModel.setCategory(category) },
                        )
                    }
                }

                state.category
                    ?.takeIf { it != ChatTriggersState.UNGROUPED }
                    ?.let { category ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.setCategoryPaused(category, true) }) {
                                Text("Pause all")
                            }
                            TextButton(onClick = { viewModel.setCategoryPaused(category, false) }) {
                                Text("Resume all")
                            }
                        }
                    }
            }
        }

        SectionCard {
            SectionCardHeader("Counters", Icons.Default.Bolt)
            Text(
                text = "Shared across the server. A response reads one with %counter:name%, " +
                    "or adds to it with %counter:name+%.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.counters.forEach { counter ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = counter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = counter.value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { editingCounter = counter }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${counter.name}")
                    }
                    IconButton(onClick = { pendingCounterDelete = counter }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${counter.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            TextButton(onClick = { editingCounter = TriggerCounter() }) {
                Text("Add a counter")
            }
        }

        if (state.placeholders.isNotEmpty()) {
            SectionCard {
                SectionCardHeader("Placeholders", Icons.Default.Bolt)
                Text(
                    text = "Drop any of these into a response and the bot fills them in when the " +
                        "trigger fires. Regex triggers can also use %regex.1% for capture groups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.placeholders.forEach { placeholder -> TagChip(placeholder) }
                }
            }
        }

        if (state.filtered.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = if (state.query.isBlank()) "No chat triggers configured yet."
                    else "No triggers match \"${state.query}\".",
                    icon = Icons.Default.Bolt,
                )
            }
        } else {
            state.filtered.forEach { trigger ->
                SectionCard(contentPadding = 12) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = trigger.trigger,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { viewModel.setPaused(trigger, !trigger.isDisabled) },
                        ) {
                            Icon(
                                imageVector = if (trigger.isDisabled) Icons.Default.PlayArrow
                                else Icons.Default.Pause,
                                contentDescription = if (trigger.isDisabled) "Resume trigger"
                                else "Pause trigger",
                            )
                        }
                        IconButton(onClick = { editing = trigger }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit trigger")
                        }
                        IconButton(onClick = { pendingDelete = trigger }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete trigger",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        text = trigger.response,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (trigger.isDisabled) TagChip("Paused")
                        trigger.category?.takeIf { it.isNotBlank() }?.let { TagChip(it) }
                        TagChip("${trigger.uses.compact()} fires")
                        if (trigger.event != ChatTriggerEventType.NONE) TagChip(trigger.event.label)
                        if (trigger.isRegex) TagChip("Regex")
                        if (trigger.containsAnywhere) TagChip("Anywhere")
                        if (trigger.dmResponse) TagChip("DM")
                        if (trigger.ownerOnly) TagChip("Owner only")
                        if (trigger.autoDeleteTrigger) TagChip("Deletes trigger")
                        if (trigger.reactToTrigger) TagChip("Reacts")
                        if (trigger.replyToTrigger) TagChip("Replies")
                        if (trigger.cooldownSeconds > 0) TagChip("${trigger.cooldownSeconds}s cooldown")
                        if (trigger.currencyCost > 0) TagChip("Costs ${trigger.currencyCost}")
                        if (trigger.requiredXpLevel > 0) TagChip("Level ${trigger.requiredXpLevel}+")
                        trigger.maxUses?.let { TagChip("${trigger.uses}/$it uses") }
                        if (trigger.allowBots) TagChip("Bots only")
                        if (trigger.hasActiveHours) TagChip("Timed")
                        TagChip(trigger.prefix.label)
                    }
                }
            }
        }
    }

    editing?.let { trigger ->
        ChatTriggerEditor(
            initial = trigger,
            roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) },
            channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) },
            categoryOptions = state.categories,
            testResult = state.testResults[trigger.id],
            stats = state.stats[trigger.id],
            onTest = { sample -> viewModel.testTrigger(trigger, sample) },
            onLoadStats = { viewModel.loadStats(trigger) },
            onDismiss = { editing = null },
            onSave = { updated ->
                if (updated.id == 0) viewModel.add(updated) else viewModel.update(updated)
                editing = null
            },
        )
    }

    pendingDelete?.let { trigger ->
        ConfirmDialog(
            title = "Delete trigger?",
            message = "\"${trigger.trigger}\" stops responding.",
            onConfirm = { viewModel.remove(trigger.id) },
            onDismiss = { pendingDelete = null },
        )
    }

    editingCounter?.let { counter ->
        CounterEditor(
            initial = counter,
            onDismiss = { editingCounter = null },
            onSave = { name, value ->
                viewModel.setCounter(name, value)
                editingCounter = null
            },
        )
    }

    pendingCounterDelete?.let { counter ->
        ConfirmDialog(
            title = "Delete counter?",
            message = "\"${counter.name}\" and every per-member value under it are removed.",
            onConfirm = { viewModel.deleteCounter(counter.name) },
            onDismiss = { pendingCounterDelete = null },
        )
    }
}

/**
 * Dialog for creating a counter or changing an existing one's value.
 */
@Composable
private fun CounterEditor(
    initial: TriggerCounter,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var value by remember(initial.id) { mutableStateOf(initial.value.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.name.isEmpty()) "New counter" else initial.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial.name.isEmpty()) {
                    MewdekoTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                    )
                }
                MewdekoTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() || char == '-' } },
                    label = "Value",
                    numeric = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), value.toLongOrNull() ?: 0L) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatTriggerEditor(
    initial: ChatTriggerModel,
    roleOptions: List<SelectorOption>,
    channelOptions: List<SelectorOption>,
    categoryOptions: List<String>,
    testResult: TriggerTestResult?,
    stats: TriggerStats?,
    onTest: (String) -> Unit,
    onLoadStats: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ChatTriggerModel) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var sample by remember(initial.id) { mutableStateOf("") }
    var regexSample by remember(initial.id) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (initial.id == 0) "New trigger" else "Edit trigger",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onSave(draft.validated()) },
                    enabled = draft.trigger.isNotBlank(),
                ) { Text("Save") }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard {
                    SectionCardHeader("Trigger", Icons.Default.Bolt)
                    if (initial.id == 0) {
                        Text(
                            text = "Or start from a template:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            QuickTemplate.entries.forEach { template ->
                                TagChip(
                                    label = template.label,
                                    onClick = { draft = template.instantiate(initial.guildId.orEmpty()) },
                                )
                            }
                        }
                    }
                    MewdekoTextField(
                        value = draft.trigger,
                        onValueChange = { draft = draft.copy(trigger = it) },
                        label = "Trigger text",
                    )
                    LabelledEmbedField(
                        label = "Response",
                        raw = draft.response,
                        onRawChange = { draft = draft.copy(response = it) },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Bolt),
                        options = ChatTriggerPrefixType.entries.map {
                            SelectorOption(it.raw.toString(), it.label)
                        },
                        placeholder = "Guild prefix",
                        label = "Prefix mode",
                        selectedId = draft.prefixType.toString(),
                        onSelect = { draft = draft.copy(prefixType = it?.toIntOrNull() ?: 0) },
                    )
                    if (draft.prefix == ChatTriggerPrefixType.CUSTOM) {
                        MewdekoTextField(
                            value = draft.customPrefix.orEmpty(),
                            onValueChange = { draft = draft.copy(customPrefix = it) },
                            label = "Custom prefix",
                        )
                    }
                }

                SectionCard {
                    SectionCardHeader("How it fires", Icons.Default.Bolt)
                    Text(
                        text = "At least one must stay on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ChatTriggerFireType.entries.forEach { type ->
                        SwitchRow(
                            title = type.label,
                            checked = draft.hasFireType(type),
                            onCheckedChange = { draft = draft.withFireType(type, it) },
                        )
                    }
                    if (draft.hasFireType(ChatTriggerFireType.INTERACTION)) {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Bolt),
                            options = ChatTriggerApplicationCommandType.entries.map {
                                SelectorOption(it.raw.toString(), it.label)
                            },
                            placeholder = "Not a command",
                            label = "Register as a command",
                            selectedId = draft.applicationCommandType.toString(),
                            onSelect = { draft = draft.copy(applicationCommandType = it?.toIntOrNull() ?: 0) },
                        )
                        if (draft.commandType != ChatTriggerApplicationCommandType.NONE) {
                            MewdekoTextField(
                                value = draft.applicationCommandName.orEmpty(),
                                onValueChange = {
                                    draft = draft.copy(applicationCommandName = it.takeIf(String::isNotBlank))
                                },
                                label = "Command name",
                            )
                            if (draft.commandType == ChatTriggerApplicationCommandType.SLASH) {
                                MewdekoTextField(
                                    value = draft.applicationCommandDescription.orEmpty(),
                                    onValueChange = {
                                        draft = draft.copy(
                                            applicationCommandDescription = it.takeIf(String::isNotBlank),
                                        )
                                    },
                                    label = "Command description",
                                )
                            }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Matching", Icons.Default.Bolt)
                    SwitchRow(
                        title = "Regular expression",
                        subtitle = "Treat the trigger text as a regex pattern",
                        checked = draft.isRegex,
                        onCheckedChange = { draft = draft.copy(isRegex = it) },
                    )
                    if (draft.isRegex) {
                        RegexTester(
                            pattern = draft.trigger,
                            sample = regexSample,
                            onSampleChange = { regexSample = it },
                        )
                    }
                    SwitchRow(
                        title = "Match anywhere",
                        subtitle = "Fire when the trigger appears anywhere in a message",
                        checked = draft.containsAnywhere,
                        onCheckedChange = { draft = draft.copy(containsAnywhere = it) },
                    )
                    SwitchRow(
                        title = "Owner only",
                        subtitle = "Only the bot owner can invoke this trigger",
                        checked = draft.ownerOnly,
                        onCheckedChange = { draft = draft.copy(ownerOnly = it) },
                    )
                    SwitchRow(
                        title = "Allow targeting",
                        subtitle = "Let the invoker mention someone to target them",
                        checked = draft.allowTarget,
                        onCheckedChange = { draft = draft.copy(allowTarget = it) },
                    )
                }

                SectionCard {
                    SectionCardHeader("Response behaviour", Icons.Default.Bolt)
                    SwitchRow(
                        title = "Reply in DM",
                        checked = draft.dmResponse,
                        onCheckedChange = { draft = draft.copy(dmResponse = it) },
                    )
                    SwitchRow(
                        title = "Delete the triggering message",
                        subtitle = if (draft.reactToTrigger) {
                            "Unavailable while reacting to the message, since there would be " +
                                "nothing left to react to."
                        } else null,
                        checked = draft.autoDeleteTrigger,
                        enabled = !draft.reactToTrigger,
                        onCheckedChange = { draft = draft.copy(autoDeleteTrigger = it) },
                    )
                    SwitchRow(
                        title = "React instead of replying",
                        subtitle = if (draft.autoDeleteTrigger) {
                            "Unavailable while deleting the triggering message."
                        } else null,
                        checked = draft.reactToTrigger,
                        enabled = !draft.autoDeleteTrigger,
                        onCheckedChange = { draft = draft.copy(reactToTrigger = it) },
                    )
                    SwitchRow(
                        title = "Send no message",
                        subtitle = "Apply role changes without posting a response",
                        checked = draft.noRespond,
                        onCheckedChange = { draft = draft.copy(noRespond = it) },
                    )
                    SwitchRow(
                        title = "Ephemeral slash response",
                        checked = draft.ephemeralResponse,
                        onCheckedChange = { draft = draft.copy(ephemeralResponse = it) },
                    )
                    SwitchRow(
                        title = "Reply to the message",
                        subtitle = "Shows the response as a reply so it is clear what it answered",
                        checked = draft.replyToTrigger,
                        onCheckedChange = { draft = draft.copy(replyToTrigger = it) },
                    )
                    NumberField(
                        value = draft.deleteResponseAfter,
                        onValueChange = { draft = draft.copy(deleteResponseAfter = it) },
                        label = "Delete the response after (seconds)",
                        supportingText = "0 keeps the response.",
                    )
                    MewdekoTextField(
                        value = draft.additionalResponses.orEmpty().replace("@@@", "\n"),
                        onValueChange = {
                            draft = draft.copy(
                                additionalResponses = it.replace("\n", "@@@").takeIf(String::isNotBlank),
                            )
                        },
                        label = "Extra responses",
                        singleLine = false,
                        minLines = 2,
                        supportingText = "One per line. Repeat one to make it more likely.",
                    )
                    if (draft.extraResponses.isNotEmpty()) {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Bolt),
                            options = ChatTriggerResponseMode.entries.map {
                                SelectorOption(it.raw.toString(), it.label)
                            },
                            placeholder = "Always the first response",
                            label = "When there are several responses",
                            selectedId = draft.responseMode.toString(),
                            onSelect = { draft = draft.copy(responseMode = it?.toIntOrNull() ?: 0) },
                        )
                    }
                    MewdekoTextField(
                        value = draft.reactions.orEmpty().replace("@@@", " "),
                        onValueChange = {
                            draft = draft.copy(
                                reactions = it.replace(Regex("\\s+"), "@@@").takeIf(String::isNotEmpty),
                            )
                        },
                        label = "Reactions",
                        placeholder = "🎉 👍",
                        supportingText = "Space-separated emoji added to the triggering message.",
                    )
                }

                SectionCard {
                    SectionCardHeader("Roles", Icons.Default.Bolt)
                    DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "No roles granted",
                        label = "Grant roles",
                        multiple = true,
                        selection = draft.grantedRoleIds,
                        onSelectionChange = {
                            draft = draft.copy(grantedRoles = it.joinToString("@@@"))
                        },
                    )
                    DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "No roles removed",
                        label = "Remove roles",
                        multiple = true,
                        selection = draft.removedRoleIds,
                        onSelectionChange = {
                            draft = draft.copy(removedRoles = it.joinToString("@@@"))
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Bolt),
                        options = ChatTriggerRoleGrantType.entries.map {
                            SelectorOption(it.raw.toString(), it.label)
                        },
                        placeholder = "Sender",
                        label = "Apply roles to",
                        selectedId = draft.roleGrantType.toString(),
                        onSelect = { draft = draft.copy(roleGrantType = it?.toIntOrNull() ?: 0) },
                    )
                }

                SectionCard {
                    SectionCardHeader("Limits", Icons.Default.Bolt)
                    NumberField(
                        value = draft.cooldownSeconds,
                        onValueChange = { draft = draft.copy(cooldownSeconds = it) },
                        label = "Cooldown (seconds)",
                        supportingText = "0 means no cooldown of its own.",
                    )
                    if (draft.cooldownSeconds > 0) {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Bolt),
                            options = ChatTriggerCooldownScope.entries.map {
                                SelectorOption(it.raw.toString(), it.label)
                            },
                            placeholder = "Each member separately",
                            label = "Cooldown applies to",
                            selectedId = draft.cooldownScope.toString(),
                            onSelect = { draft = draft.copy(cooldownScope = it?.toIntOrNull() ?: 0) },
                        )
                    }
                    NumberField(
                        value = draft.maxUses ?: 0,
                        onValueChange = { draft = draft.copy(maxUses = it.takeIf { v -> v > 0 }) },
                        label = "Stop after this many uses",
                        supportingText = "Used ${draft.uses} times so far. 0 means no limit.",
                    )
                    ExpiryField(
                        value = draft.expiresAt,
                        onChange = { draft = draft.copy(expiresAt = it) },
                    )
                    NumberField(
                        value = draft.minAccountAgeMinutes,
                        onValueChange = { draft = draft.copy(minAccountAgeMinutes = it) },
                        label = "Minimum account age (minutes)",
                    )
                    NumberField(
                        value = draft.minServerMembershipMinutes,
                        onValueChange = { draft = draft.copy(minServerMembershipMinutes = it) },
                        label = "Minimum time in server (minutes)",
                        supportingText = "These keep brand new accounts from using the trigger.",
                    )
                    ActiveHoursSection(
                        window = draft.activeWindow,
                        onChange = { window ->
                            draft = draft.copy(
                                timeConditions = window?.let {
                                    MewdekoJson.encodeToString(
                                        ListSerializer(ActiveWindow.serializer()),
                                        listOf(it),
                                    )
                                },
                            )
                        },
                    )
                }

                SectionCard {
                    SectionCardHeader("Costs and rewards", Icons.Default.Bolt)
                    NumberField(
                        value = draft.currencyCost.toInt(),
                        onValueChange = { draft = draft.copy(currencyCost = it.toLong()) },
                        label = "Costs the user",
                    )
                    NumberField(
                        value = draft.currencyReward.toInt(),
                        onValueChange = { draft = draft.copy(currencyReward = it.toLong()) },
                        label = "Pays the user",
                    )
                    NumberField(
                        value = draft.xpReward,
                        onValueChange = { draft = draft.copy(xpReward = it) },
                        label = "Grants XP",
                    )
                    NumberField(
                        value = draft.requiredXpLevel,
                        onValueChange = { draft = draft.copy(requiredXpLevel = it) },
                        label = "Requires level",
                    )
                    MewdekoTextField(
                        value = draft.requirementFailMessage.orEmpty(),
                        onValueChange = {
                            draft = draft.copy(requirementFailMessage = it.takeIf(String::isNotBlank))
                        },
                        label = "Message when they cannot use it",
                        supportingText = "Leave empty to say nothing.",
                    )
                }

                SectionCard {
                    SectionCardHeader("Counter requirement", Icons.Default.Bolt)
                    MewdekoTextField(
                        value = draft.counterName.orEmpty(),
                        onValueChange = { draft = draft.copy(counterName = it.takeIf(String::isNotBlank)) },
                        label = "Counter name",
                        supportingText = "Read or change one from a response with %counter:name%.",
                    )
                    if (!draft.counterName.isNullOrBlank()) {
                        NullableNumberField(
                            value = draft.counterMin,
                            onValueChange = { draft = draft.copy(counterMin = it) },
                            label = "At least",
                            supportingText = "Leave blank for no lower bound. Negative values are allowed.",
                        )
                        NullableNumberField(
                            value = draft.counterMax,
                            onValueChange = { draft = draft.copy(counterMax = it) },
                            label = "At most",
                            supportingText = "Leave blank for no upper bound. Negative values are allowed.",
                        )
                    }
                }

                SectionCard {
                    SectionCardHeader("Fire on an event", Icons.Default.Bolt)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Bolt),
                        options = ChatTriggerEventType.entries.map {
                            SelectorOption(it.raw.toString(), it.label)
                        },
                        placeholder = "Not an event trigger",
                        label = "Fire when",
                        selectedId = draft.eventType.toString(),
                        onSelect = { draft = draft.copy(eventType = it?.toIntOrNull() ?: 0) },
                    )
                    if (draft.event != ChatTriggerEventType.NONE) {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Channel,
                            options = channelOptions,
                            placeholder = "Where the event happened",
                            label = "Respond in",
                            selectedId = draft.eventChannelId,
                            onSelect = { draft = draft.copy(eventChannelId = it) },
                        )
                        Text(
                            text = "Joins and boosts have no channel of their own, so pick one here " +
                                "or the trigger will not respond.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SectionCard {
                    SectionCardHeader("Organisation", Icons.Default.Bolt)
                    MewdekoTextField(
                        value = draft.category.orEmpty(),
                        onValueChange = { draft = draft.copy(category = it.takeIf(String::isNotBlank)) },
                        label = "Category",
                        supportingText = "Group related triggers so you can pause them together.",
                    )
                    if (categoryOptions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            categoryOptions.forEach { category ->
                                TagChip(
                                    label = category,
                                    icon = if (draft.category == category) Icons.Default.Check else null,
                                    onClick = { draft = draft.copy(category = category) },
                                )
                            }
                        }
                    }
                    NumberField(
                        value = draft.nextTriggerId ?: 0,
                        onValueChange = { draft = draft.copy(nextTriggerId = it.takeIf { v -> v > 0 }) },
                        label = "Then run trigger",
                        supportingText = "Runs a second trigger by ID afterwards. It still checks its own rules.",
                    )
                    SwitchRow(
                        title = "Respond to bots instead of people",
                        subtitle = "Only matches messages from other bots and webhooks. Never its own.",
                        checked = draft.allowBots,
                        onCheckedChange = { draft = draft.copy(allowBots = it) },
                    )
                    SwitchRow(
                        title = "Paused",
                        subtitle = "Keeps the trigger without letting it fire",
                        checked = draft.isDisabled,
                        onCheckedChange = { draft = draft.copy(isDisabled = it) },
                    )
                }

                if (draft.id != 0) {
                    SectionCard {
                        SectionCardHeader("Test this trigger", Icons.Default.Bolt)
                        Text(
                            text = "Checks whether a message would fire this trigger, as you. " +
                                "Nothing is sent, charged or counted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MewdekoTextField(
                            value = sample,
                            onValueChange = { sample = it },
                            label = "Sample message",
                        )
                        TextButton(
                            onClick = { onTest(sample) },
                            enabled = sample.isNotBlank(),
                        ) { Text("Run test") }

                        testResult?.let { result ->
                            Text(
                                text = when {
                                    result.wouldFire -> "This message would fire the trigger."
                                    !result.matched ->
                                        "The message does not match this trigger's text, prefix or pattern."
                                    else -> "Matched, but blocked: ${result.blocker.orEmpty()}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (result.wouldFire) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                            )
                        }

                        TextButton(onClick = onLoadStats) { Text("Show recent activity") }

                        stats?.let { history ->
                            Text(
                                text = "Fired ${history.total} time(s) in total.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            history.recent.forEach { fire ->
                                Text(
                                    text = "<@${fire.userId.orEmpty()}> in <#${fire.channelId.orEmpty()}> " +
                                        fire.dateAdded.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Crossposting", Icons.Default.Bolt)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = channelOptions,
                        placeholder = "No crossposting",
                        label = "Also post to",
                        selectedId = draft.crosspostingChannelId,
                        onSelect = { draft = draft.copy(crosspostingChannelId = it) },
                    )
                    MewdekoTextField(
                        value = draft.crosspostingWebhookUrl.orEmpty(),
                        onValueChange = { draft = draft.copy(crosspostingWebhookUrl = it) },
                        label = "Webhook URL",
                    )
                }
            }
        }
    }
}

/**
 * A whole-number field that keeps an empty box readable as zero rather than rejecting it.
 */
@Composable
private fun NumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    supportingText: String? = null,
) {
    MewdekoTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { onValueChange(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
        label = label,
        placeholder = "0",
        supportingText = supportingText,
        numeric = true,
    )
}

/**
 * A whole-number field that allows a blank box to mean "no bound" and allows a leading minus
 * sign, unlike [NumberField]. Used for counter bounds, which the bot accepts as null or negative.
 */
@Composable
private fun NullableNumberField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    label: String,
    supportingText: String? = null,
) {
    MewdekoTextField(
        value = value?.toString().orEmpty(),
        onValueChange = { raw ->
            val filtered = raw.filterIndexed { index, char -> char.isDigit() || (char == '-' && index == 0) }
            onValueChange(if (filtered.isEmpty() || filtered == "-") null else filtered.toLongOrNull())
        },
        label = label,
        placeholder = "No limit",
        supportingText = supportingText,
    )
}

/** Ensures the trigger about to be saved matches the invariants the bot and dashboard enforce. */
private fun ChatTriggerModel.validated(): ChatTriggerModel {
    var next = this

    if (next.validTriggerTypes == 0) {
        next = next.copy(validTriggerTypes = ChatTriggerFireType.MESSAGE.raw)
    }

    if (next.commandType == ChatTriggerApplicationCommandType.SLASH &&
        !next.hasFireType(ChatTriggerFireType.INTERACTION)
    ) {
        next = next.withFireType(ChatTriggerFireType.INTERACTION, true)
    }

    if (next.autoDeleteTrigger && next.reactToTrigger) {
        next = next.copy(reactToTrigger = false)
    }

    return next
}

/** A starting point for a new trigger, applied by [instantiate]. */
private enum class QuickTemplate(val label: String) {
    SIMPLE("Simple hello"),
    ROLE("Role grant"),
    SLASH("Slash command"),
    EMBED("Rich embed welcome"),
}

/** Prefills a blank trigger for [guildId] with this template's fields. */
private fun QuickTemplate.instantiate(guildId: Snowflake): ChatTriggerModel {
    val blank = ChatTriggerModel.blank(guildId)
    return when (this) {
        QuickTemplate.SIMPLE -> blank.copy(
            trigger = "hello",
            response = EmbedMessage(content = "Hello there! 👋").serialize(),
        )

        QuickTemplate.ROLE -> blank.copy(
            trigger = "getrole",
            response = EmbedMessage(content = "Role assigned!").serialize(),
        )

        QuickTemplate.SLASH -> blank.copy(
            trigger = "info",
            response = EmbedMessage(content = "Server information: %server.name%").serialize(),
            validTriggerTypes = ChatTriggerFireType.INTERACTION.raw,
            applicationCommandType = ChatTriggerApplicationCommandType.SLASH.raw,
            applicationCommandName = "info",
            applicationCommandDescription = "Get server information",
        )

        QuickTemplate.EMBED -> blank.copy(
            trigger = "welcome",
            response = EmbedMessage(
                content = "Welcome to the server!",
                embeds = listOf(
                    EmbedSpec(
                        title = "Welcome!",
                        description = "Thanks for joining %server.name%!",
                        color = "0x5865F2",
                        thumbnail = UrlBox("%user.avatar%"),
                        footer = EmbedFooter(text = "Enjoy your stay!"),
                    ),
                ),
            ).serialize(),
        )
    }
}

/**
 * Checks a regex pattern for validity and, once a sample is typed, highlights every match in it.
 */
@Composable
private fun RegexTester(
    pattern: String,
    sample: String,
    onSampleChange: (String) -> Unit,
) {
    val result = remember(pattern) { runCatching { Regex(pattern) } }
    val highlight = MaterialTheme.colorScheme.primaryContainer

    result.exceptionOrNull()?.let { error ->
        Text(
            text = "Invalid pattern: ${error.message.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    MewdekoTextField(
        value = sample,
        onValueChange = onSampleChange,
        label = "Test text",
        supportingText = "Matches highlight below.",
    )

    val regex = result.getOrNull()
    if (regex != null && sample.isNotBlank()) {
        val matches = regex.findAll(sample).toList()
        Text(
            text = buildAnnotatedString {
                var index = 0
                matches.forEach { match ->
                    append(sample.substring(index, match.range.first))
                    withStyle(SpanStyle(background = highlight)) {
                        append(match.value)
                    }
                    index = match.range.last + 1
                }
                if (index <= sample.length) append(sample.substring(index))
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = if (matches.isEmpty()) "No match." else "${matches.size} match(es).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Parses a "HH:mm" string into hour/minute, defaulting to 09:00 when it does not parse. */
private fun String.toHourMinute(): Pair<Int, Int> {
    val parts = split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return hour to minute
}

/**
 * Turns a trigger's active window on or off and edits its start, end and weekday selection.
 *
 * Only the first stored condition is edited, matching [ChatTriggerModel.activeWindow]; any extra
 * conditions the bot might hold are left untouched by round-tripping through the same shape.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveHoursSection(
    window: ActiveWindow?,
    onChange: (ActiveWindow?) -> Unit,
) {
    val context = LocalContext.current
    val current = window ?: ActiveWindow(startTime = "09:00", endTime = "17:00", enabled = true)

    SwitchRow(
        title = "Only active certain hours",
        subtitle = "Uses the device's local time zone. An end time before the start time runs overnight.",
        checked = window != null,
        onCheckedChange = { onChange(if (it) current else null) },
    )

    if (window != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val (hour, minute) = current.startTime.orEmpty().toHourMinute()
                    TimePickerDialog(
                        context,
                        { _, h, m -> onChange(current.copy(startTime = "%02d:%02d".format(h, m))) },
                        hour,
                        minute,
                        true,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text("From ${current.startTime.orEmpty().ifEmpty { "09:00" }}") }
            OutlinedButton(
                onClick = {
                    val (hour, minute) = current.endTime.orEmpty().toHourMinute()
                    TimePickerDialog(
                        context,
                        { _, h, m -> onChange(current.copy(endTime = "%02d:%02d".format(h, m))) },
                        hour,
                        minute,
                        true,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Until ${current.endTime.orEmpty().ifEmpty { "17:00" }}") }
        }

        Text(
            text = "On these days",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActiveWindow.DAY_NAMES.forEachIndexed { index, name ->
                val days = current.daysOfWeek.orEmpty()
                val selected = index in days
                TagChip(
                    label = name,
                    icon = if (selected) Icons.Default.Check else null,
                    onClick = {
                        val next = if (selected) days - index else (days + index).sorted()
                        onChange(current.copy(daysOfWeek = next.takeIf { it.isNotEmpty() }))
                    },
                )
            }
        }
        Text(
            text = "No days selected means every day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A date and time picker for [ChatTriggerModel.expiresAt], stored as an ISO instant string. */
@Composable
private fun ExpiryField(
    value: String?,
    onChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val parsed = value?.let { InstantParser.parse(it) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Stop firing after",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val base = parsed?.let { ZonedDateTime.ofInstant(it, ZoneId.systemDefault()) }
                        ?: ZonedDateTime.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val zoned = ZonedDateTime.of(
                                        year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault(),
                                    )
                                    onChange(DateTimeFormatter.ISO_INSTANT.format(zoned.toInstant()))
                                },
                                base.hour,
                                base.minute,
                                true,
                            ).show()
                        },
                        base.year,
                        base.monthValue - 1,
                        base.dayOfMonth,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) { Text(parsed?.relativeToNow() ?: "Never expires") }
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear expiry")
                }
            }
        }
    }
}
