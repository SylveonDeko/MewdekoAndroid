package dev.mewdeko.mobile.feature.chattriggers

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                    state.categories.forEach { category ->
                        TagChip(
                            label = category,
                            icon = if (state.category == category) Icons.Default.Check else null,
                            onClick = { viewModel.setCategory(category) },
                        )
                    }
                }

                state.category?.let { category ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTriggerEditor(
    initial: ChatTriggerModel,
    roleOptions: List<SelectorOption>,
    channelOptions: List<SelectorOption>,
    testResult: TriggerTestResult?,
    stats: TriggerStats?,
    onTest: (String) -> Unit,
    onLoadStats: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ChatTriggerModel) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var sample by remember(initial.id) { mutableStateOf("") }

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
                    onClick = { onSave(draft) },
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
                    SectionCardHeader("Matching", Icons.Default.Bolt)
                    SwitchRow(
                        title = "Regular expression",
                        subtitle = "Treat the trigger text as a regex pattern",
                        checked = draft.isRegex,
                        onCheckedChange = { draft = draft.copy(isRegex = it) },
                    )
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
                        checked = draft.autoDeleteTrigger,
                        onCheckedChange = { draft = draft.copy(autoDeleteTrigger = it) },
                    )
                    SwitchRow(
                        title = "React instead of replying",
                        checked = draft.reactToTrigger,
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
                        value = draft.additionalResponses.orEmpty(),
                        onValueChange = {
                            draft = draft.copy(additionalResponses = it.takeIf(String::isNotBlank))
                        },
                        label = "Extra responses",
                        supportingText = "Separate with @@@. Repeat one to make it more likely.",
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
                        value = draft.reactions.orEmpty(),
                        onValueChange = { draft = draft.copy(reactions = it) },
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
                    draft.activeWindow?.let { window ->
                        Text(
                            text = "Active ${window.summary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { draft = draft.copy(timeConditions = null) }) {
                            Text("Clear active hours")
                        }
                    }
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
                        NumberField(
                            value = draft.counterMin?.toInt() ?: 0,
                            onValueChange = { draft = draft.copy(counterMin = it.toLong()) },
                            label = "At least",
                        )
                        NumberField(
                            value = draft.counterMax?.toInt() ?: 0,
                            onValueChange = {
                                draft = draft.copy(counterMax = it.takeIf { v -> v > 0 }?.toLong())
                            },
                            label = "At most",
                            supportingText = "0 means no upper bound.",
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
