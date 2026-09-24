package dev.mewdeko.mobile.feature.utility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab(UtilitySection.ALIASES, "Aliases", Icons.Default.Terminal),
    SectionTab(UtilitySection.QUOTES, "Quotes", Icons.Default.FormatQuote),
    SectionTab(UtilitySection.AUTO_PUBLISH, "Auto Publish", Icons.Default.Campaign),
    SectionTab(UtilitySection.STREAM_ROLE, "Stream Role", Icons.Default.VideoCameraFront),
    SectionTab(UtilitySection.AI, "AI Assistant", Icons.Default.SmartToy),
    SectionTab(UtilitySection.NSFW, "NSFW Filter", Icons.Default.VisibilityOff),
    SectionTab(UtilitySection.ROLE_MONITOR, "Role Monitor", Icons.Default.AdminPanelSettings),
)

private val ProviderOptions = AiProvider.entries.map { SelectorOption(it.value.toString(), it.label) }
private val ListTypeOptions = listOf(
    SelectorOption("whitelist", "Whitelist", "Always eligible, even without the role"),
    SelectorOption("blacklist", "Blacklist", "Never given the stream role"),
)
private val PunishmentOptions = UtilityPunishment.choices.map { (value, name) -> SelectorOption(value.toString(), name) }
private val OverrideOptions = listOf(SelectorOption("default", "Use default punishment")) + PunishmentOptions
private const val DefaultOverrideId = "default"

/**
 * Utilities: command aliases, quotes, auto publish, stream role, the AI
 * assistant, the NSFW tag filter, and role monitoring.
 */
@Composable
fun UtilityScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: UtilityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var confirmClearAliases by remember { mutableStateOf(false) }
    var pendingQuoteDelete by remember { mutableStateOf<GuildQuote?>(null) }
    var pendingPublishDisable by remember { mutableStateOf<AutoPublishChannel?>(null) }
    var confirmStopStream by remember { mutableStateOf(false) }
    var confirmClearKey by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Utilities",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load(refreshing = true) }, enabled = !state.busy) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        SectionGate(state, onRetry = { viewModel.reloadSection() }) {
            when (state.section) {
                UtilitySection.ALIASES -> AliasesSection(state, viewModel, onClearAll = { confirmClearAliases = true })
                UtilitySection.QUOTES -> QuotesSection(state, viewModel, onDelete = { pendingQuoteDelete = it })
                UtilitySection.AUTO_PUBLISH -> AutoPublishSection(state, viewModel, onDisable = { pendingPublishDisable = it })
                UtilitySection.STREAM_ROLE -> StreamRoleSection(state, viewModel, onStop = { confirmStopStream = true })
                UtilitySection.AI -> AiSection(state, viewModel, onClearKey = { confirmClearKey = true })
                UtilitySection.NSFW -> NsfwSection(state, viewModel)
                UtilitySection.ROLE_MONITOR -> RoleMonitorSection(state, viewModel)
            }
        }
    }

    if (confirmClearAliases) {
        ConfirmDialog(
            title = "Clear all aliases?",
            message = "Remove all ${state.aliases.size} aliases? Members will need the full commands again.",
            confirmLabel = "Clear all",
            onConfirm = viewModel::clearAliases,
            onDismiss = { confirmClearAliases = false },
        )
    }

    pendingQuoteDelete?.let { quote ->
        ConfirmDialog(
            title = "Delete quote?",
            message = "Delete quote #${quote.id} (${quote.keyword})?",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteQuote(quote) },
            onDismiss = { pendingQuoteDelete = null },
        )
    }

    pendingPublishDisable?.let { entry ->
        ConfirmDialog(
            title = "Disable auto publish?",
            message = "Messages in #${entry.channelName ?: entry.channelId} will no longer be published " +
                "automatically, and its skip lists are removed.",
            confirmLabel = "Disable",
            onConfirm = { viewModel.removeAutoPublish(entry.channelId) },
            onDismiss = { pendingPublishDisable = null },
        )
    }

    if (confirmStopStream) {
        ConfirmDialog(
            title = "Disable the stream role?",
            message = "Members keep the role until they stop streaming.",
            confirmLabel = "Disable",
            onConfirm = viewModel::stopStreamRole,
            onDismiss = { confirmStopStream = false },
        )
    }

    if (confirmClearKey) {
        ConfirmDialog(
            title = "Remove the API key?",
            message = "The assistant stops responding until a new key is set.",
            confirmLabel = "Remove key",
            onConfirm = viewModel::clearAiKey,
            onDismiss = { confirmClearKey = false },
        )
    }
}

/** Shows the active section once loaded, or its loading or failure state. */
@Composable
private fun SectionGate(
    state: UtilityState,
    onRetry: () -> Unit,
    content: @Composable () -> Unit,
) {
    val section = state.section
    val error = state.sectionErrors[section]
    when {
        section in state.loadedSections -> content()
        error != null -> SectionCard { ErrorState(message = error, onRetry = onRetry) }
        else -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

/** Small muted helper text used under inputs. */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A removable chip for list entries such as ids, words, and tags. */
@Composable
private fun RemovableChip(label: String, enabled: Boolean, contentDescription: String, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onRemove,
        enabled = enabled,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(Icons.Default.Close, contentDescription = contentDescription, modifier = Modifier.size(16.dp)) },
    )
}

/** Wraps chips across lines, or shows [emptyText] when there are none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(isEmpty: Boolean, emptyText: String, content: @Composable () -> Unit) {
    if (isEmpty) {
        Hint(emptyText)
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}

/** A text field with a trailing add button. */
@Composable
private fun AddRow(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    onAdd: () -> Unit,
    numeric: Boolean = false,
    placeholder: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MewdekoTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            numeric = numeric,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAdd, enabled = enabled && value.isNotBlank()) { Text("Add") }
    }
}

@Composable
private fun AliasesSection(state: UtilityState, viewModel: UtilityViewModel, onClearAll: () -> Unit) {
    SectionCard {
        SectionCardHeader("Add an alias", Icons.Default.Add)
        MewdekoTextField(
            value = state.aliasTrigger,
            onValueChange = { viewModel.setAliasTrigger(it.take(50)) },
            label = "Trigger",
            placeholder = "e.g. yt",
            supportingText = "One word, no spaces.",
        )
        MewdekoTextField(
            value = state.aliasMapping,
            onValueChange = { viewModel.setAliasMapping(it.take(500)) },
            label = "Runs this command",
            placeholder = "e.g. play youtube",
        )
        Button(
            onClick = viewModel::addAlias,
            enabled = !state.busy && state.aliasTrigger.isNotBlank() && state.aliasMapping.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add alias") }
    }

    SectionCard {
        SectionCardHeader("Command aliases (${state.aliases.size})", Icons.Default.Terminal)
        Hint("Shortcuts that expand into full commands when typed with the prefix.")
        if (state.aliases.isEmpty()) {
            EmptyState(
                "No aliases yet. Aliases let members type a short word that expands to a full command.",
                icon = Icons.Default.Terminal,
            )
        } else {
            if (state.aliases.size > 8) {
                SearchField(
                    value = state.aliasSearch,
                    onValueChange = viewModel::setAliasSearch,
                    placeholder = "Search aliases",
                )
            }
            val shown = state.filteredAliases
            if (shown.isEmpty()) {
                EmptyState("No aliases match.")
            }
            shown.forEachIndexed { index, alias ->
                if (index > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        alias.trigger,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        alias.mapping,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeAlias(alias.trigger) }, enabled = !state.busy) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove alias ${alias.trigger}")
                    }
                }
            }
            OutlinedButton(
                onClick = onClearAll,
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text("  Clear all aliases")
            }
        }
    }
}

@Composable
private fun QuotesSection(state: UtilityState, viewModel: UtilityViewModel, onDelete: (GuildQuote) -> Unit) {
    SectionCard {
        SectionCardHeader("Add a quote", Icons.Default.Add)
        MewdekoTextField(
            value = state.quoteKeyword,
            onValueChange = { viewModel.setQuoteKeyword(it.take(50)) },
            label = "Keyword",
        )
        MewdekoTextField(
            value = state.quoteText,
            onValueChange = { viewModel.setQuoteText(it.take(2000)) },
            label = "Quote text",
            singleLine = false,
            minLines = 2,
        )
        Button(
            onClick = viewModel::addQuote,
            enabled = !state.busy && state.quoteKeyword.isNotBlank() && state.quoteText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add quote") }
    }

    SectionCard {
        SectionCardHeader("Quotes (${state.quoteTotal})", Icons.Default.FormatQuote)
        Hint("Saved snippets members can recall by keyword.")
        SearchField(
            value = state.quoteSearch,
            onValueChange = viewModel::setQuoteSearch,
            placeholder = "Search keyword or text",
        )
        if (state.quotes.isEmpty()) {
            EmptyState(
                if (state.quoteSearch.isNotBlank()) "No quotes match." else "No quotes yet.",
                icon = Icons.Default.FormatQuote,
            )
        } else {
            state.quotes.forEachIndexed { index, quote ->
                if (index > 0) HorizontalDivider()
                if (state.editingQuoteId == quote.id) {
                    QuoteEditor(state, viewModel)
                } else {
                    QuoteRow(quote, busy = state.busy, onEdit = { viewModel.startEditQuote(quote) }, onDelete = { onDelete(quote) })
                }
            }
            if (state.quoteTotal > QuotePageSize) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { viewModel.goToQuotePage(state.quotePage - 1) },
                        enabled = !state.busy && state.quotePage > 1,
                    ) { Text("Previous") }
                    Text(
                        "Page ${state.quotePage} of ${state.quotePageCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(
                        onClick = { viewModel.goToQuotePage(state.quotePage + 1) },
                        enabled = !state.busy && state.quotePage * QuotePageSize < state.quoteTotal,
                    ) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(quote: GuildQuote, busy: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        quote.keyword,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(
                    "#${quote.id} · by ${quote.authorName} · used ${quote.useCount} times",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(quote.text, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = onEdit, enabled = !busy) {
            Icon(Icons.Default.Edit, contentDescription = "Edit quote")
        }
        IconButton(onClick = onDelete, enabled = !busy) {
            Icon(Icons.Default.Delete, contentDescription = "Delete quote")
        }
    }
}

@Composable
private fun QuoteEditor(state: UtilityState, viewModel: UtilityViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MewdekoTextField(
            value = state.editQuoteKeyword,
            onValueChange = viewModel::setEditQuoteKeyword,
            label = "Keyword",
        )
        MewdekoTextField(
            value = state.editQuoteText,
            onValueChange = viewModel::setEditQuoteText,
            label = "Text",
            singleLine = false,
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveQuoteEdit, enabled = !state.busy) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("  Save")
            }
            TextButton(onClick = viewModel::cancelEditQuote) { Text("Cancel") }
        }
    }
}

@Composable
private fun AutoPublishSection(
    state: UtilityState,
    viewModel: UtilityViewModel,
    onDisable: (AutoPublishChannel) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Auto publish", Icons.Default.Campaign)
        Hint("Automatically publish announcement channel posts to servers that follow them.")
        val enabledIds = state.autoPublish.map { it.channelId }.toSet()
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.newsChannels.filter { it.id !in enabledIds }.map { SelectorOption(it.id, it.name) },
            placeholder = if (state.newsChannels.isEmpty()) "No announcement channels in this server"
            else "Select announcement channel",
            label = "Add an announcement channel",
            selectedId = null,
            onSelect = viewModel::addAutoPublish,
            enabled = !state.busy && state.newsChannels.isNotEmpty(),
        )
    }

    if (state.autoPublish.isEmpty()) {
        SectionCard {
            EmptyState(
                "No channels are auto published. Messages posted in announcement channels you add here are " +
                    "published to followers automatically.",
                icon = Icons.Default.Campaign,
            )
        }
    }

    state.autoPublish.forEach { entry ->
        SectionCard {
            SectionCardHeader(
                title = "#${entry.channelName ?: entry.channelId}",
                icon = Icons.Default.Campaign,
                trailing = {
                    TextButton(
                        onClick = { onDisable(entry) },
                        enabled = !state.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Disable") }
                },
            )
            Text("Skip messages from these users", style = MaterialTheme.typography.labelLarge)
            AddRow(
                value = state.publishUserDrafts[entry.channelId].orEmpty(),
                onValueChange = { viewModel.setPublishUserDraft(entry.channelId, it.filter(Char::isDigit)) },
                label = "User ID",
                numeric = true,
                enabled = !state.busy,
                onAdd = { viewModel.addPublishUser(entry.channelId) },
            )
            ChipFlow(entry.blacklistedUsers.isEmpty(), "No skipped users.") {
                entry.blacklistedUsers.forEach { id ->
                    RemovableChip(id, !state.busy, "Stop skipping user $id") {
                        viewModel.removePublishUser(entry.channelId, id)
                    }
                }
            }
            HorizontalDivider()
            Text("Skip messages containing these words", style = MaterialTheme.typography.labelLarge)
            AddRow(
                value = state.publishWordDrafts[entry.channelId].orEmpty(),
                onValueChange = { viewModel.setPublishWordDraft(entry.channelId, it) },
                label = "Word",
                enabled = !state.busy,
                onAdd = { viewModel.addPublishWord(entry.channelId) },
            )
            ChipFlow(entry.blacklistedWords.isEmpty(), "No skipped words.") {
                entry.blacklistedWords.forEach { word ->
                    RemovableChip(word, !state.busy, "Stop skipping word $word") {
                        viewModel.removePublishWord(entry.channelId, word)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamRoleSection(state: UtilityState, viewModel: UtilityViewModel, onStop: () -> Unit) {
    val roleOptions = state.roles.map { SelectorOption(it.id, it.name) }
    val settings = state.streamRole
    val enabled = settings?.enabled == true

    SectionCard {
        SectionCardHeader(
            title = "Stream role",
            icon = Icons.Default.VideoCameraFront,
            trailing = { TagChip(if (enabled) "Enabled" else "Disabled") },
        )
        Hint(
            if (enabled) {
                "@${state.roleName(settings.fromRoleId)} streamers get @${state.roleName(settings.addRoleId)}"
            } else {
                "Give members a role automatically while they are live on Discord."
            }
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "Members with this role",
            label = "Eligible role",
            selectedId = state.streamFromRole,
            onSelect = viewModel::setStreamFromRole,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "Role to add",
            label = "Role while streaming",
            selectedId = state.streamAddRole,
            onSelect = viewModel::setStreamAddRole,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::saveStreamRole,
                enabled = !state.busy && state.streamFromRole != null && state.streamAddRole != null,
            ) { Text(if (enabled) "Update roles" else "Enable") }
            if (enabled) {
                OutlinedButton(
                    onClick = onStop,
                    enabled = !state.busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Text("  Disable")
                }
            }
        }
        HorizontalDivider()
        MewdekoTextField(
            value = state.streamKeyword,
            onValueChange = { viewModel.setStreamKeyword(it.take(100)) },
            label = "Stream title keyword",
            placeholder = "Any stream",
            supportingText = "Only streams whose title contains this word count. Leave empty to match any stream.",
        )
        OutlinedButton(onClick = viewModel::saveStreamKeyword, enabled = !state.busy) { Text("Save keyword") }
    }

    SectionCard {
        SectionCardHeader("Exceptions", Icons.Default.VerifiedUser)
        Hint("Fine-tune who can receive the stream role.")
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Person),
            options = ListTypeOptions,
            placeholder = "Whitelist",
            label = "List",
            selectedId = state.streamListType,
            onSelect = { viewModel.setStreamListType(it ?: "whitelist") },
        )
        AddRow(
            value = state.streamListUser,
            onValueChange = { viewModel.setStreamListUser(it.filter(Char::isDigit)) },
            label = "User ID",
            numeric = true,
            enabled = !state.busy,
            onAdd = viewModel::addStreamListUser,
        )
        StreamList("Whitelist", "Always eligible, even without the role", "whitelist", settings?.whitelist.orEmpty(), state.busy, viewModel)
        StreamList("Blacklist", "Never given the stream role", "blacklist", settings?.blacklist.orEmpty(), state.busy, viewModel)
    }
}

@Composable
private fun StreamList(
    title: String,
    hint: String,
    key: String,
    users: List<StreamRoleUser>,
    busy: Boolean,
    viewModel: UtilityViewModel,
) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.labelLarge)
    Hint(hint)
    ChipFlow(users.isEmpty(), "Empty") {
        users.forEach { entry ->
            val label = entry.username?.takeIf { it.isNotBlank() } ?: entry.userId
            RemovableChip(label, !busy, "Remove $label") { viewModel.removeStreamListUser(key, entry.userId) }
        }
    }
}

@Composable
private fun AiSection(state: UtilityState, viewModel: UtilityViewModel, onClearKey: () -> Unit) {
    val ai = state.ai
    SectionCard {
        SectionCardHeader("AI assistant", Icons.Default.SmartToy)
        Hint("Let the bot answer questions in one channel using your own provider key.")
        SwitchRow(
            title = "Assistant enabled",
            subtitle = buildString {
                append("Replies to messages in the chosen channel")
                if ((ai?.tokensUsed ?: 0L) > 0L) append(" · ${"%,d".format(ai?.tokensUsed ?: 0L)} tokens used")
            },
            checked = state.aiEnabled,
            onCheckedChange = viewModel::setAiEnabled,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.textChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Select channel",
            label = "Channel",
            selectedId = state.aiChannelId,
            onSelect = viewModel::setAiChannel,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.SmartToy),
            options = ProviderOptions,
            placeholder = "OpenAI",
            label = "Provider",
            selectedId = state.aiProvider.toString(),
            onSelect = { it?.toIntOrNull()?.let(viewModel::setAiProvider) },
        )
    }

    SectionCard {
        SectionCardHeader("API key and model", Icons.Default.Key)
        SecretField(
            value = state.aiApiKey,
            onValueChange = viewModel::setAiApiKey,
            label = "API key",
            placeholder = if (ai?.hasApiKey == true) "Stored (${ai.apiKeyHint ?: "hidden"}). Enter a new key to replace it."
            else "Paste your provider API key",
        )
        Hint("The key is stored by the bot and never shown again in full.")
        if (ai?.hasApiKey == true) {
            OutlinedButton(
                onClick = onClearKey,
                enabled = !state.busy,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Remove key") }
        }
        HorizontalDivider()
        if (state.aiModels.isNotEmpty()) {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.SmartToy),
                options = state.aiModels.map { SelectorOption(it.id, it.name.ifBlank { it.id }, it.id) },
                placeholder = "Select model",
                label = "Model",
                selectedId = state.aiModel.takeIf { it.isNotEmpty() },
                onSelect = { it?.let(viewModel::setAiModel) },
            )
            TextButton(onClick = viewModel::clearAiModels) { Text("Type a model id instead") }
        } else {
            MewdekoTextField(
                value = state.aiModel,
                onValueChange = viewModel::setAiModel,
                label = "Model",
                placeholder = "Model id, e.g. gpt-4o-mini",
            )
        }
        OutlinedButton(
            onClick = { viewModel.loadAiModels() },
            enabled = !state.aiModelsLoading && ai?.hasApiKey == true,
        ) {
            if (state.aiModelsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  Fetching models")
            } else {
                Text("Fetch models")
            }
        }
        if (ai?.hasApiKey != true) Hint("Save an API key to fetch the provider's model list.")
    }

    SectionCard {
        SectionCardHeader("Behaviour", Icons.Default.Edit)
        MewdekoTextField(
            value = state.aiSystemPrompt,
            onValueChange = viewModel::setAiSystemPrompt,
            label = "System prompt",
            placeholder = "You are a helpful assistant for this community...",
            singleLine = false,
            minLines = 4,
        )
        SwitchRow(
            title = "Allow web search",
            checked = state.aiWebSearch,
            onCheckedChange = viewModel::setAiWebSearch,
        )
        SwitchRow(
            title = "Hide \"searching the web\" notices",
            checked = state.aiHideWebSearch,
            onCheckedChange = viewModel::setAiHideWebSearch,
        )
        MewdekoTextField(
            value = state.aiWebhookUrl,
            onValueChange = viewModel::setAiWebhookUrl,
            label = if (ai?.hasWebhook == true) "Webhook URL (set)" else "Webhook URL (optional)",
            placeholder = if (ai?.hasWebhook == true) "Enter a new URL to replace the current webhook"
            else "Respond through a webhook for a custom name and avatar",
        )
        Button(
            onClick = viewModel::saveAi,
            enabled = !state.busy && state.aiDirty,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Text(if (state.aiDirty) "  Save AI settings" else "  Saved")
        }
    }
}

/** A single-line password field that never echoes the typed key. */
@Composable
private fun SecretField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NsfwSection(state: UtilityState, viewModel: UtilityViewModel) {
    SectionCard {
        SectionCardHeader("Blocked NSFW tags (${state.nsfwTags.size})", Icons.Default.VisibilityOff)
        Hint("Tags that NSFW image commands will never return in this server.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = state.nsfwDraft,
                onValueChange = { viewModel.setNsfwDraft(it.take(100)) },
                label = "Tag to block",
                placeholder = "e.g. gore",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = viewModel::addNsfwTag, enabled = !state.busy && state.nsfwDraft.isNotBlank()) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" Block")
            }
        }
        if (state.nsfwTags.isEmpty()) {
            EmptyState(
                "No tags are blocked. Blocked tags are excluded from every NSFW image search in this server.",
                icon = Icons.Default.VisibilityOff,
            )
        } else {
            ChipFlow(isEmpty = false, emptyText = "") {
                state.nsfwTags.forEach { tag ->
                    RemovableChip(tag, !state.busy, "Unblock $tag") { viewModel.removeNsfwTag(tag) }
                }
            }
        }
    }
}

@Composable
private fun RoleMonitorSection(state: UtilityState, viewModel: UtilityViewModel) {
    val config = state.roleMonitor ?: RoleMonitorConfig()

    SectionCard {
        SectionCardHeader("Role monitor", Icons.Default.Shield)
        Hint("Revert dangerous role or permission changes and punish the moderator who made them.")
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Gavel),
            options = PunishmentOptions,
            placeholder = "Only revert the change",
            label = "Default punishment",
            selectedId = config.defaultPunishment.toString(),
            onSelect = { value ->
                val parsed = value?.toIntOrNull()
                if (parsed != null && parsed != config.defaultPunishment) viewModel.setRoleMonitorDefault(parsed)
            },
            enabled = !state.busy,
        )
        Hint("Applied to whoever hands out a blacklisted role or permission, unless the entry has its own punishment.")
    }

    SectionCard {
        SectionCardHeader("Blacklisted roles", Icons.Default.Person)
        Hint("Nobody can be given these roles; the change is reverted and the moderator punished.")
        val blocked = config.blacklistedRoles.map { it.roleId }.toSet()
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.roles.filter { it.id !in blocked }.map { SelectorOption(it.id, it.name) },
            placeholder = "Role to protect",
            label = "Role",
            selectedId = state.rmRolePick,
            onSelect = viewModel::setRmRolePick,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Gavel),
            options = OverrideOptions,
            placeholder = "Use default punishment",
            label = "Punishment",
            selectedId = state.rmRolePunish?.toString() ?: DefaultOverrideId,
            onSelect = { viewModel.setRmRolePunish(it?.toIntOrNull()) },
        )
        Button(onClick = viewModel::addRmRole, enabled = !state.busy && state.rmRolePick != null) { Text("Add") }
        if (config.blacklistedRoles.isEmpty()) {
            Hint("No roles are blacklisted.")
        }
        config.blacklistedRoles.forEach { entry ->
            MonitorRow(
                title = "@${state.roleName(entry.roleId)}",
                punishment = UtilityPunishment.label(entry.punishment),
                busy = state.busy,
                onRemove = { viewModel.removeRmRole(entry.roleId) },
            )
        }
    }

    SectionCard {
        SectionCardHeader("Blacklisted permissions", Icons.Default.Key)
        Hint("Roles granting these permissions are reverted when created or edited.")
        val blocked = config.blacklistedPermissions.map { it.permission }.toSet()
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Key),
            options = MonitoredPermissions.filter { it.first !in blocked }.map { SelectorOption(it.first, it.second) },
            placeholder = "Permission to protect",
            label = "Permission",
            selectedId = state.rmPermPick,
            onSelect = viewModel::setRmPermPick,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Gavel),
            options = OverrideOptions,
            placeholder = "Use default punishment",
            label = "Punishment",
            selectedId = state.rmPermPunish?.toString() ?: DefaultOverrideId,
            onSelect = { viewModel.setRmPermPunish(it?.toIntOrNull()) },
        )
        Button(onClick = viewModel::addRmPermission, enabled = !state.busy && state.rmPermPick != null) { Text("Add") }
        if (config.blacklistedPermissions.isEmpty()) {
            Hint("No permissions are blacklisted.")
        }
        config.blacklistedPermissions.forEach { entry ->
            MonitorRow(
                title = entry.permissionName.ifBlank {
                    MonitoredPermissions.firstOrNull { it.first == entry.permission }?.second ?: entry.permission
                },
                punishment = UtilityPunishment.label(entry.punishment),
                busy = state.busy,
                onRemove = { viewModel.removeRmPermission(entry.permission) },
            )
        }
    }

    SectionCard {
        SectionCardHeader("Trusted roles and members", Icons.Default.VerifiedUser)
        Hint("Changes made by these roles or members are never reverted.")
        val trusted = config.whitelistedRoles.toSet()
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.roles.filter { it.id !in trusted }.map { SelectorOption(it.id, it.name) },
            placeholder = "Add a trusted role",
            label = "Trusted roles",
            selectedId = null,
            onSelect = viewModel::whitelistRole,
            enabled = !state.busy,
        )
        ChipFlow(config.whitelistedRoles.isEmpty(), "No trusted roles.") {
            config.whitelistedRoles.forEach { id ->
                val name = state.roleName(id)
                RemovableChip("@$name", !state.busy, "Remove trusted role $name") { viewModel.unwhitelistRole(id) }
            }
        }
        HorizontalDivider()
        Text("Trusted members", style = MaterialTheme.typography.labelLarge)
        AddRow(
            value = state.rmWhitelistUser,
            onValueChange = { viewModel.setRmWhitelistUser(it.filter(Char::isDigit)) },
            label = "User ID",
            numeric = true,
            enabled = !state.busy,
            onAdd = viewModel::addRmWhitelistUser,
        )
        ChipFlow(config.whitelistedUsers.isEmpty(), "No trusted members.") {
            config.whitelistedUsers.forEach { id: Snowflake ->
                RemovableChip(id, !state.busy, "Remove trusted member $id") { viewModel.unwhitelistUser(id) }
            }
        }
    }
}

@Composable
private fun MonitorRow(title: String, punishment: String, busy: Boolean, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TagChip(punishment)
        IconButton(onClick = onRemove, enabled = !busy) {
            Icon(Icons.Default.Delete, contentDescription = "Remove $title")
        }
    }
}
