package dev.mewdeko.mobile.feature.twitch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow

private val PermissionOptions = TwitchPermission.entries.map { SelectorOption(it.value, it.value) }

/** Custom command form, preview, list, and the command reference. */
@Composable
internal fun CustomCommandsSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val draft = state.commandDraft
    SectionCard {
        SectionCardHeader(if (draft.editing) "Edit command" else "New command", Icons.Default.Add)
        HelpText("Members type these in Twitch chat and the bot replies with your text.")
        MewdekoTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.updateCommandDraft { it.copy(name = value) } },
            label = "Name",
            placeholder = "hello",
            supportingText = "Runs as ${state.prefix}${draft.name.ifBlank { "name" }}",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Security),
            options = PermissionOptions,
            placeholder = TwitchPermission.EVERYONE.value,
            label = "Permission",
            selectedId = draft.permission,
            onSelect = { value ->
                viewModel.updateCommandDraft { it.copy(permission = value ?: TwitchPermission.EVERYONE.value) }
            },
        )
        MewdekoTextField(
            value = draft.cooldownSeconds,
            onValueChange = { value -> viewModel.updateCommandDraft { it.copy(cooldownSeconds = value.filter(Char::isDigit)) } },
            label = "Cooldown seconds",
            numeric = true,
            supportingText = "0 to 86400.",
        )
        MewdekoTextField(
            value = draft.response,
            onValueChange = { value -> viewModel.updateCommandDraft { it.copy(response = value) } },
            label = "Response",
            placeholder = "Hey %display%, welcome in.",
            singleLine = false,
            minLines = 3,
        )
        VariableChips(
            variables = state.variablesFor("custom_commands", TwitchReference.commandVariables),
            onInsert = { variable -> viewModel.updateCommandDraft { it.copy(response = it.response + variable) } },
        )
        SwitchRow(
            title = "Enabled",
            checked = draft.enabled,
            onCheckedChange = { value -> viewModel.updateCommandDraft { it.copy(enabled = value) } },
        )
        FormButtons(
            saveLabel = "Save command",
            saving = state.commandSaving,
            canSave = draft.name.isNotBlank() && draft.response.isNotBlank(),
            showCancel = draft.editing || draft.name.isNotEmpty() || draft.response.isNotEmpty(),
            onSave = viewModel::saveCommand,
            onCancel = viewModel::resetCommandDraft,
        )

        HorizontalDivider()
        Text("Test response", style = MaterialTheme.typography.titleSmall)
        HelpText("Renders the saved command named above without sending it to chat.")
        MewdekoTextField(
            value = draft.testArgs,
            onValueChange = { value -> viewModel.updateCommandDraft { it.copy(testArgs = value) } },
            label = "Test args",
            placeholder = "@target extra text",
        )
        OutlinedButton(
            onClick = viewModel::previewCommand,
            enabled = !state.commandPreviewing && draft.name.isNotBlank(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(if (state.commandPreviewing) "  Testing..." else "  Test response")
        }
        if (state.commandPreview.isNotEmpty()) {
            OutputBlock(state.commandPreview)
        }
    }

    SectionCard {
        SectionCardHeader("Custom commands (${state.customCommands.size})", Icons.Default.Terminal)
        when {
            TwitchList.CUSTOM_COMMANDS in state.failedLists -> ListError(
                "Could not load custom commands.",
                onRetry = { viewModel.load(refreshing = true) },
            )

            state.customCommands.isEmpty() -> EmptyState("No custom commands yet.", icon = Icons.Default.Terminal)

            else -> state.customCommands.forEach { command ->
                ListItem(
                    headlineContent = {
                        Text(
                            "${state.prefix}${command.name}",
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(command.response, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${command.permission} · ${command.cooldownSeconds}s · used ${command.useCount} times" +
                                    if (command.enabled) "" else " · disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.editCommand(command) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${command.name}")
                            }
                            IconButton(
                                onClick = {
                                    confirm(
                                        TwitchConfirmation(
                                            title = "Remove ${state.prefix}${command.name}?",
                                            message = "The command stops responding in Twitch chat.",
                                            confirmLabel = "Remove",
                                            onConfirm = { viewModel.removeCommand(command) },
                                        )
                                    )
                                },
                                enabled = !state.commandSaving,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${command.name}")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickableRow { viewModel.editCommand(command) }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }

    CommandReferenceCard(state, viewModel)
}

@Composable
private fun CommandReferenceCard(state: TwitchState, viewModel: TwitchViewModel) {
    var expanded by remember { mutableStateOf(false) }
    SectionCard {
        SectionCardHeader(
            "Command reference",
            Icons.Default.MenuBook,
            trailing = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            },
        )
        if (expanded) {
            CommandReferenceBody(state, viewModel)
        } else {
            HelpText("Discord slash commands and built-in Twitch chat commands.")
        }
    }
}

@Composable
private fun CommandReferenceBody(state: TwitchState, viewModel: TwitchViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Discord slash commands", style = MaterialTheme.typography.titleSmall)
        TwitchReference.slashCommands.forEach { command ->
            ListItem(
                headlineContent = {
                    Text(
                        listOf(command.name, command.usage).filter { it.isNotEmpty() }.joinToString(" "),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                supportingContent = { Text(command.description) },
                trailingContent = { TagChip(command.permission) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }

        HorizontalDivider()
        Text("Twitch chat commands", style = MaterialTheme.typography.titleSmall)
        when {
            TwitchList.CHAT_COMMANDS in state.failedLists -> ListError(
                "Could not load built-in chat commands.",
                onRetry = { viewModel.load(refreshing = true) },
            )

            state.chatCommands.isEmpty() -> EmptyState("No built-in chat commands reported.", icon = Icons.Default.ChatBubble)

            else -> state.chatCommands.forEach { command ->
                ListItem(
                    headlineContent = { Text("${state.prefix}${command.name}", fontFamily = FontFamily.Monospace) },
                    trailingContent = { TagChip(command.permission) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/** Timer form and list with enable, test, edit, and remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimersSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val draft = state.timerDraft
    SectionCard {
        SectionCardHeader(if (draft.editing) "Edit timer" else "New timer", Icons.Default.Add)
        HelpText("Timers post one of their messages to Twitch chat on a schedule.")
        MewdekoTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.updateTimerDraft { it.copy(name = value) } },
            label = "Name",
            placeholder = "socials",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = draft.intervalMinutes,
                onValueChange = { value -> viewModel.updateTimerDraft { it.copy(intervalMinutes = value.filter(Char::isDigit)) } },
                label = "Interval (min)",
                numeric = true,
                supportingText = "1 to 1440",
                modifier = Modifier.weight(1f),
            )
            MewdekoTextField(
                value = draft.minChatMessages,
                onValueChange = { value -> viewModel.updateTimerDraft { it.copy(minChatMessages = value.filter(Char::isDigit)) } },
                label = "Min chat messages",
                numeric = true,
                supportingText = "0 to 10000",
                modifier = Modifier.weight(1f),
            )
        }
        MewdekoTextField(
            value = draft.messages,
            onValueChange = { value -> viewModel.updateTimerDraft { it.copy(messages = value) } },
            label = "Messages",
            placeholder = "One message per line. Try: Follow the socials: %url%",
            singleLine = false,
            minLines = 4,
        )
        VariableChips(
            variables = state.variablesFor("timers", TwitchReference.timerVariables),
            onInsert = { variable -> viewModel.updateTimerDraft { it.copy(messages = it.messages + variable) } },
        )
        SwitchRow(
            title = "Online only",
            subtitle = "Only post while the stream is live",
            checked = draft.onlineOnly,
            onCheckedChange = { value -> viewModel.updateTimerDraft { it.copy(onlineOnly = value) } },
        )
        SwitchRow(
            title = "Randomize messages",
            subtitle = "Pick a random line instead of rotating in order",
            checked = draft.randomizeMessages,
            onCheckedChange = { value -> viewModel.updateTimerDraft { it.copy(randomizeMessages = value) } },
        )
        SwitchRow(
            title = "Enabled",
            checked = draft.enabled,
            onCheckedChange = { value -> viewModel.updateTimerDraft { it.copy(enabled = value) } },
        )
        FormButtons(
            saveLabel = "Save timer",
            saving = state.timerSaving,
            canSave = draft.name.isNotBlank() && draft.messages.isNotBlank(),
            showCancel = draft.editing || draft.name.isNotEmpty() || draft.messages.isNotEmpty(),
            onSave = viewModel::saveTimer,
            onCancel = viewModel::resetTimerDraft,
        )
    }

    SectionCard {
        SectionCardHeader("Timers (${state.timers.size})", Icons.Default.Timer)
        when {
            TwitchList.TIMERS in state.failedLists -> ListError(
                "Could not load timers.",
                onRetry = { viewModel.load(refreshing = true) },
            )

            state.timers.isEmpty() -> EmptyState("No timers yet.", icon = Icons.Default.Timer)

            else -> state.timers.forEachIndexed { index, timer ->
                if (index > 0) HorizontalDivider()
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(timer.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Every ${timer.intervalMinutes} min · ${timer.minChatMessages} chat messages · " +
                                    "${timer.messageLines.size} ${if (timer.messageLines.size == 1) "message" else "messages"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = timer.enabled,
                            onCheckedChange = { viewModel.setTimerEnabled(timer, it) },
                        )
                    }
                    timer.messageLines.firstOrNull()?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (timer.onlineOnly) TagChip("online only")
                        if (timer.randomizeMessages) TagChip("randomized")
                        TagChip("last sent ${timer.lastSentAt.twitchDate("never")}")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { viewModel.editTimer(timer) }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text("  Edit")
                        }
                        TextButton(
                            onClick = { viewModel.testTimer(timer) },
                            enabled = state.timerTesting == null,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                            Text(if (state.timerTesting == timer.name) "  Sending..." else "  Send now")
                        }
                        TextButton(
                            onClick = {
                                confirm(
                                    TwitchConfirmation(
                                        title = "Remove timer ${timer.name}?",
                                        message = "Its messages stop posting to Twitch chat.",
                                        confirmLabel = "Remove",
                                        onConfirm = { viewModel.removeTimer(timer) },
                                    )
                                )
                            },
                            enabled = !state.timerSaving,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text("  Remove")
                        }
                    }
                }
            }
        }
    }
}

/** Quote add form, search filter, and list with remove. */
@Composable
internal fun QuotesSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Add a quote", Icons.Default.Add)
        MewdekoTextField(
            value = state.quoteText,
            onValueChange = viewModel::setQuoteText,
            label = "Quote",
            placeholder = "The stream moment worth saving.",
            singleLine = false,
            minLines = 2,
        )
        MewdekoTextField(
            value = state.quoteAuthor,
            onValueChange = viewModel::setQuoteAuthor,
            label = "Author",
            placeholder = "optional username",
        )
        Button(
            onClick = viewModel::addQuote,
            enabled = !state.quoteSaving && state.quoteText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.quoteSaving) "Saving..." else "Add quote")
        }
    }

    SectionCard {
        SectionCardHeader(
            "Quotes (${state.quotes.size})",
            Icons.Default.FormatQuote,
            trailing = {
                IconButton(onClick = viewModel::refreshQuotes, enabled = !state.quotesLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh quotes")
                }
            },
        )
        SearchField(
            value = state.quoteSearch,
            onValueChange = viewModel::setQuoteSearch,
            placeholder = "Filter quotes",
        )
        OutlinedButton(
            onClick = viewModel::refreshQuotes,
            enabled = !state.quotesLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Search")
        }
        if (state.quotesLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when {
            TwitchList.QUOTES in state.failedLists -> ListError(
                "Could not load quotes.",
                onRetry = viewModel::refreshQuotes,
            )

            state.quotes.isEmpty() -> EmptyState(
                if (state.quoteSearch.isBlank()) "No quotes saved yet." else "No quotes match that filter.",
                icon = Icons.Default.FormatQuote,
            )

            else -> state.quotes.forEach { quote ->
                ListItem(
                    overlineContent = { Text("#${quote.id}") },
                    headlineContent = { Text(quote.text) },
                    supportingContent = {
                        val parts = listOfNotNull(
                            quote.author?.takeIf { it.isNotBlank() }?.let { "by $it" },
                            quote.addedBy?.takeIf { it.isNotBlank() }?.let { "added by $it" },
                            quote.dateAdded?.let { it.twitchDate("") }?.takeIf { it.isNotEmpty() },
                        )
                        if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                confirm(
                                    TwitchConfirmation(
                                        title = "Remove quote #${quote.id}?",
                                        message = "It can no longer be pulled up in chat.",
                                        confirmLabel = "Remove",
                                        onConfirm = { viewModel.removeQuote(quote) },
                                    )
                                )
                            },
                            enabled = !state.quoteSaving,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove quote ${quote.id}")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
