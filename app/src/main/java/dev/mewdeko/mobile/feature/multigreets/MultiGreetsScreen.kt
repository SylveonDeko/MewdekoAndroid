package dev.mewdeko.mobile.feature.multigreets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Welcome messages posted when a member joins. */
@Composable
fun MultiGreetsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: MultiGreetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MultiGreetEntry?>(null) }

    FeatureScaffold(
        title = "Greets",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            NewItemFab(label = "Add greet", onClick = { showAdd = true })
        },
    ) {
        SectionCard {
            SectionCardHeader("Greet mode", Icons.Default.WavingHand)
            EnumPicker(
                label = "Greet mode",
                options = MultiGreetType.entries.map { EnumOption(it, title = it.label, description = it.blurb) },
                selected = state.greetType,
                onSelect = { viewModel.setType(it) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Greets", "${state.greets.size}", Modifier.weight(1f))
                StatTile("Active", "${state.activeCount}", Modifier.weight(1f))
            }
        }

        if (state.greets.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No welcome messages configured yet.",
                    icon = Icons.Default.WavingHand,
                    actionLabel = "Add greet",
                    onAction = { showAdd = true },
                )
            }
        } else {
            state.greets.forEach { greet ->
                key(greet.id) {
                    SectionCard {
                        SectionCardHeader(
                            title = "#${greet.channelName ?: state.channelName(greet.channelId)}",
                            icon = Icons.Default.Tag,
                            trailing = {
                                IconButton(onClick = { pendingDelete = greet }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove greet",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                        SwitchRow(
                            title = "Enabled",
                            checked = !greet.disabled,
                            onCheckedChange = { viewModel.setDisabled(greet.id, !it) },
                        )
                        SwitchRow(
                            title = "Greet bots",
                            subtitle = "Post this greeting for bot accounts too",
                            checked = greet.greetBots,
                            onCheckedChange = { viewModel.setGreetBots(greet.id, it) },
                        )

                        var editingDeleteTime by remember { mutableStateOf(false) }
                        var deleteTimeText by remember { mutableStateOf("") }
                        if (editingDeleteTime) {
                            MewdekoTextField(
                                value = deleteTimeText,
                                onValueChange = { deleteTimeText = it },
                                label = "Auto-delete after",
                                placeholder = "e.g. 1m30s",
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                listOf("Never" to "0s", "30s" to "30s", "60s" to "60s", "5m" to "300s")
                                    .forEach { (label, value) ->
                                        TextButton(onClick = { deleteTimeText = value }) {
                                            Text(label)
                                        }
                                    }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.updateDeleteTime(
                                            greet.id,
                                            deleteTimeText.ifBlank { "0s" },
                                        )
                                        editingDeleteTime = false
                                    },
                                ) { Text("Save") }
                                TextButton(onClick = { editingDeleteTime = false }) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Auto-delete", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = formatGreetDuration(greet.deleteTime),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        deleteTimeText = if (greet.deleteTime > 0) {
                                            formatGreetDuration(greet.deleteTime)
                                        } else {
                                            ""
                                        }
                                        editingDeleteTime = true
                                    },
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit auto-delete")
                                }
                            }
                        }

                        EmbedMessageEditor(
                            message = EmbedMessage.parse(greet.message),
                            onMessageChange = { viewModel.updateMessage(greet.id, it) },
                        )

                        var editingWebhook by remember { mutableStateOf(false) }
                        var webhookName by remember { mutableStateOf("") }
                        var webhookAvatarUrl by remember { mutableStateOf("") }
                        if (editingWebhook) {
                            MewdekoTextField(
                                value = webhookName,
                                onValueChange = { webhookName = it },
                                label = "Webhook name",
                                placeholder = "e.g. Welcome Bot",
                            )
                            MewdekoTextField(
                                value = webhookAvatarUrl,
                                onValueChange = { webhookAvatarUrl = it },
                                label = "Avatar URL (optional)",
                                placeholder = "https://...",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.setWebhook(greet.id, webhookName, webhookAvatarUrl)
                                        editingWebhook = false
                                    },
                                    enabled = webhookName.isNotBlank(),
                                ) { Text("Save") }
                                TextButton(onClick = { editingWebhook = false }) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text("Webhook", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (greet.webhookUrl.isNullOrBlank()) {
                                            "Not configured"
                                        } else {
                                            "Configured"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        webhookName = ""
                                        webhookAvatarUrl = ""
                                        editingWebhook = true
                                    },
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = "Edit webhook")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var channelId by remember { mutableStateOf<String?>(null) }
        FormSheet(
            title = "Add greet channel",
            confirmLabel = "Add",
            confirmEnabled = channelId != null,
            onConfirm = {
                channelId?.let { viewModel.add(it) }
                showAdd = false
            },
            onDismiss = { showAdd = false },
        ) {
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a channel",
                label = "Post welcomes in",
                selectedId = channelId,
                onSelect = { channelId = it },
            )
        }
    }

    pendingDelete?.let { greet ->
        ConfirmDialog(
            title = "Remove greet?",
            message = "Welcome messages stop posting in " +
                "#${greet.channelName ?: state.channelName(greet.channelId)}.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(greet.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Renders a second count as a compact duration, e.g. "1m30s", or "Never" for zero. */
private fun formatGreetDuration(seconds: Int): String {
    if (seconds <= 0) return "Never"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return buildString {
        if (hours > 0) append("${hours}h")
        if (minutes > 0) append("${minutes}m")
        if (remainingSeconds > 0 || isEmpty()) append("${remainingSeconds}s")
    }
}
