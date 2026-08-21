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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
                    label = "Regex",
                    value = "${state.triggers.count { it.isRegex }}",
                    modifier = Modifier.weight(1f),
                )
            }
            SearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search triggers and responses",
            )
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
                        TagChip("${trigger.uses.compact()} fires")
                        if (trigger.isRegex) TagChip("Regex")
                        if (trigger.containsAnywhere) TagChip("Anywhere")
                        if (trigger.dmResponse) TagChip("DM")
                        if (trigger.ownerOnly) TagChip("Owner only")
                        if (trigger.autoDeleteTrigger) TagChip("Deletes trigger")
                        if (trigger.reactToTrigger) TagChip("Reacts")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTriggerEditor(
    initial: ChatTriggerModel,
    roleOptions: List<SelectorOption>,
    channelOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onSave: (ChatTriggerModel) -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }

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
