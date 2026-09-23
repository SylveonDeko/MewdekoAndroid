package dev.mewdeko.mobile.feature.twitch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/** Go-live, sub, and raid routing with test buttons, plus channel point actions. */
@Composable
internal fun AlertsSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val settings = state.settings
    val channelOptions = state.channels.map { SelectorOption(it.id, it.name) }

    HelpText(
        "Alert changes are saved with the Save changes button. Test buttons use the saved settings.",
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    SectionCard {
        SectionCardHeader("Go-live", Icons.Default.SignalCellularAlt)
        HelpText("Post a message when the connected channel goes live (and goes offline again).")
        if (state.status?.hasChannelAuthorization == true) {
            HelpText(
                "This channel is connected via OAuth, so go-live notifications set up here replace anything " +
                    "configured for @${state.status?.channelUsername.orEmpty()} under Streams."
            )
        }
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No channel selected",
            label = "Go-live Discord channel",
            selectedId = settings.goLiveChannelId,
            onSelect = viewModel::setGoLiveChannel,
        )
        Text("Go-live message", style = MaterialTheme.typography.titleSmall)
        EmbedMessageEditor(
            message = settings.goLiveMessage,
            onMessageChange = viewModel::setGoLiveMessage,
        )
        HelpText(
            "Leave empty for a default embed. Placeholders: " +
                state.variablesFor("go_live", TwitchReference.goLiveVariables).joinToString(", ") + "."
        )
        TestButton(state, TwitchTestEvent.GO_LIVE, "Send test go-live", viewModel)
    }

    SectionCard {
        SectionCardHeader("Sub notifications", Icons.Default.Star)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No channel selected",
            label = "Sub notification channel",
            selectedId = settings.subChannelId,
            onSelect = viewModel::setSubChannel,
        )
        MewdekoTextField(
            value = settings.subMessage,
            onValueChange = viewModel::setSubMessage,
            label = "Sub message",
            placeholder = "%display% subscribed to %channel%!",
            singleLine = false,
            minLines = 2,
        )
        VariableChips(
            variables = state.variablesFor("sub", TwitchReference.subVariables),
            onInsert = { viewModel.appendAlertVariable(TwitchTestEvent.SUB, it) },
        )
        TestButton(state, TwitchTestEvent.SUB, "Test sub", viewModel)
    }

    SectionCard {
        SectionCardHeader("Raid notifications", Icons.Default.Groups)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No channel selected",
            label = "Raid notification channel",
            selectedId = settings.raidChannelId,
            onSelect = viewModel::setRaidChannel,
        )
        MewdekoTextField(
            value = settings.raidMessage,
            onValueChange = viewModel::setRaidMessage,
            label = "Raid message",
            placeholder = "%raider% raided %channel% with %viewers% viewers!",
            singleLine = false,
            minLines = 2,
        )
        VariableChips(
            variables = state.variablesFor("raid", TwitchReference.raidVariables),
            onInsert = { viewModel.appendAlertVariable(TwitchTestEvent.RAID, it) },
        )
        TestButton(state, TwitchTestEvent.RAID, "Test raid", viewModel)
    }

    RedemptionsCards(state, viewModel, channelOptions, confirm)
}

@Composable
private fun TestButton(state: TwitchState, event: TwitchTestEvent, label: String, viewModel: TwitchViewModel) {
    OutlinedButton(
        onClick = { viewModel.sendTestEvent(event) },
        enabled = state.testRunning == null,
    ) {
        Icon(Icons.Default.Science, contentDescription = null)
        Text(if (state.testRunning == event) "  Sending..." else "  $label")
    }
}

@Composable
private fun RedemptionsCards(
    state: TwitchState,
    viewModel: TwitchViewModel,
    channelOptions: List<SelectorOption>,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val draft = state.redemptionDraft
    SectionCard {
        SectionCardHeader(
            if (draft.editing) "Edit channel point action" else "New channel point action",
            Icons.Default.Add,
        )
        HelpText("Match a reward by its exact title on Twitch, then reply in chat, post to Discord, or both.")
        MewdekoTextField(
            value = draft.rewardTitle,
            onValueChange = { value -> viewModel.updateRedemptionDraft { it.copy(rewardTitle = value) } },
            label = "Reward title",
            placeholder = "Hydrate",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No Discord post",
            label = "Discord channel",
            selectedId = draft.discordChannelId,
            onSelect = { value -> viewModel.updateRedemptionDraft { it.copy(discordChannelId = value) } },
        )
        MewdekoTextField(
            value = draft.twitchResponse,
            onValueChange = { value -> viewModel.updateRedemptionDraft { it.copy(twitchResponse = value) } },
            label = "Twitch reply",
            placeholder = "Thanks %display%, hydrate time.",
            singleLine = false,
            minLines = 2,
        )
        MewdekoTextField(
            value = draft.discordMessage,
            onValueChange = { value -> viewModel.updateRedemptionDraft { it.copy(discordMessage = value) } },
            label = "Discord message",
            placeholder = "%display% redeemed %reward%: %input%",
            singleLine = false,
            minLines = 2,
        )
        VariableChips(
            variables = state.variablesFor("redemption", TwitchReference.redemptionVariables),
            onInsert = null,
        )
        FormButtons(
            saveLabel = "Save action",
            saving = state.redemptionSaving,
            canSave = draft.rewardTitle.isNotBlank(),
            showCancel = draft.editing || draft.rewardTitle.isNotEmpty(),
            onSave = viewModel::saveRedemption,
            onCancel = viewModel::resetRedemptionDraft,
        )
    }

    SectionCard {
        SectionCardHeader("Channel point actions (${state.redemptions.size})", Icons.Default.Redeem)
        when {
            TwitchList.REDEMPTIONS in state.failedLists -> ListError(
                "Could not load channel point actions.",
                onRetry = { viewModel.load(refreshing = true) },
            )

            state.redemptions.isEmpty() -> EmptyState("No channel point actions yet.", icon = Icons.Default.Redeem)

            else -> state.redemptions.forEach { action ->
                val channelName = action.discordChannelId
                    ?.takeIf { it.isNotEmpty() && it != "0" }
                    ?.let { id -> state.channels.firstOrNull { it.id == id }?.name?.let { "#$it" } ?: "#$id" }
                ListItem(
                    headlineContent = { Text(action.rewardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            action.twitchResponse?.takeIf { it.isNotBlank() }?.let {
                                Text("Twitch: $it", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            channelName?.let {
                                Text(
                                    "Discord: $it" + (action.discordMessage?.takeIf { msg -> msg.isNotBlank() }?.let { msg -> ", $msg" } ?: ""),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.editRedemption(action) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${action.rewardTitle}")
                            }
                            IconButton(
                                onClick = {
                                    confirm(
                                        TwitchConfirmation(
                                            title = "Remove action for ${action.rewardTitle}?",
                                            message = "Redemptions of this reward will no longer trigger anything.",
                                            confirmLabel = "Remove",
                                            onConfirm = { viewModel.removeRedemption(action) },
                                        )
                                    )
                                },
                                enabled = !state.redemptionSaving,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove ${action.rewardTitle}")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickableRow { viewModel.editRedemption(action) }
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

/** Chat, marker, clip, poll, moderation, and message deletion against the connected channel. */
@Composable
internal fun LiveToolsSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val live = state.live
    val running = state.liveRunning
    val uriHandler = LocalUriHandler.current

    SectionCard {
        SectionCardHeader("Chat", Icons.Default.ChatBubble)
        MewdekoTextField(
            value = live.chatMessage,
            onValueChange = { value -> viewModel.updateLive { it.copy(chatMessage = value) } },
            label = "Message",
            placeholder = "Message to send in Twitch chat",
            singleLine = false,
            minLines = 2,
        )
        LiveButton("Send", Icons.AutoMirrored.Filled.Send, TwitchLiveAction.CHAT, running, live.chatMessage.isNotBlank()) {
            viewModel.runLiveAction(TwitchLiveAction.CHAT)
        }
    }

    SectionCard {
        SectionCardHeader("Stream marker", Icons.Default.Bookmark)
        MewdekoTextField(
            value = live.markerDescription,
            onValueChange = { value -> viewModel.updateLive { it.copy(markerDescription = value) } },
            label = "Description",
            placeholder = "Marker description",
        )
        LiveButton("Create marker", Icons.Default.Bookmark, TwitchLiveAction.MARKER, running, true) {
            viewModel.runLiveAction(TwitchLiveAction.MARKER)
        }
    }

    SectionCard {
        SectionCardHeader("Clip", Icons.Default.ContentCut)
        HelpText("Captures a clip of the live stream.")
        LiveButton("Create clip", Icons.Default.ContentCut, TwitchLiveAction.CLIP, running, true) {
            viewModel.runLiveAction(TwitchLiveAction.CLIP)
        }
        state.lastClipUrl?.let { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text("  $url", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    SectionCard {
        SectionCardHeader("Poll", Icons.Default.Poll)
        MewdekoTextField(
            value = live.pollTitle,
            onValueChange = { value -> viewModel.updateLive { it.copy(pollTitle = value) } },
            label = "Title",
            placeholder = "Poll title",
        )
        MewdekoTextField(
            value = live.pollChoices,
            onValueChange = { value -> viewModel.updateLive { it.copy(pollChoices = value) } },
            label = "Choices",
            placeholder = "One choice per line",
            singleLine = false,
            minLines = 3,
        )
        MewdekoTextField(
            value = live.pollDurationSeconds,
            onValueChange = { value -> viewModel.updateLive { it.copy(pollDurationSeconds = value.filter(Char::isDigit)) } },
            label = "Duration (seconds)",
            numeric = true,
            supportingText = "15 to 1800.",
        )
        val choiceCount = live.pollChoices.lines().count { it.isNotBlank() }
        LiveButton(
            "Create poll",
            Icons.Default.Poll,
            TwitchLiveAction.POLL,
            running,
            live.pollTitle.isNotBlank() && choiceCount >= 2,
        ) {
            viewModel.runLiveAction(TwitchLiveAction.POLL)
        }
    }

    SectionCard {
        SectionCardHeader("Moderation", Icons.Default.Gavel)
        MewdekoTextField(
            value = live.moderationUsername,
            onValueChange = { value -> viewModel.updateLive { it.copy(moderationUsername = value) } },
            label = "Twitch username",
        )
        MewdekoTextField(
            value = live.moderationDurationSeconds,
            onValueChange = { value -> viewModel.updateLive { it.copy(moderationDurationSeconds = value.filter(Char::isDigit)) } },
            label = "Timeout duration (seconds)",
            numeric = true,
            supportingText = "1 to 1209600. Only used by Timeout.",
        )
        MewdekoTextField(
            value = live.moderationReason,
            onValueChange = { value -> viewModel.updateLive { it.copy(moderationReason = value) } },
            label = "Reason",
            placeholder = "Optional",
        )
        val hasUser = live.moderationUsername.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LiveButton("Timeout", Icons.Default.Timer, TwitchLiveAction.TIMEOUT, running, hasUser, Modifier.weight(1f)) {
                confirm(
                    TwitchConfirmation(
                        title = "Time out ${live.moderationUsername.trim()}?",
                        message = "They cannot chat for ${live.moderationDurationSeconds.ifBlank { "600" }} seconds.",
                        confirmLabel = "Timeout",
                        onConfirm = { viewModel.runLiveAction(TwitchLiveAction.TIMEOUT) },
                    )
                )
            }
            LiveButton("Ban", Icons.Default.Block, TwitchLiveAction.BAN, running, hasUser, Modifier.weight(1f)) {
                confirm(
                    TwitchConfirmation(
                        title = "Ban ${live.moderationUsername.trim()}?",
                        message = "They are banned from the channel until unbanned.",
                        confirmLabel = "Ban",
                        onConfirm = { viewModel.runLiveAction(TwitchLiveAction.BAN) },
                    )
                )
            }
        }
        LiveButton("Unban", Icons.Default.LockOpen, TwitchLiveAction.UNBAN, running, hasUser, outlined = true) {
            viewModel.runLiveAction(TwitchLiveAction.UNBAN)
        }
    }

    SectionCard {
        SectionCardHeader("Delete a message", Icons.Default.DeleteSweep)
        MewdekoTextField(
            value = live.deleteMessageId,
            onValueChange = { value -> viewModel.updateLive { it.copy(deleteMessageId = value) } },
            label = "Message ID",
            placeholder = "Message ID to delete",
        )
        LiveButton(
            "Delete message",
            Icons.Default.Delete,
            TwitchLiveAction.DELETE,
            running,
            live.deleteMessageId.isNotBlank(),
        ) {
            confirm(
                TwitchConfirmation(
                    title = "Delete this chat message?",
                    message = "Removes message ${live.deleteMessageId.trim()} from Twitch chat.",
                    confirmLabel = "Delete",
                    onConfirm = { viewModel.runLiveAction(TwitchLiveAction.DELETE) },
                )
            )
        }
    }
}

@Composable
private fun LiveButton(
    label: String,
    icon: ImageVector,
    action: TwitchLiveAction,
    running: TwitchLiveAction?,
    ready: Boolean,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val enabled = running == null && ready
    val text = if (running == action) "  Working..." else "  $label"
    if (outlined) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = null)
            Text(text, maxLines = 1)
        }
    } else {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = null)
            Text(text, maxLines = 1)
        }
    }
}
