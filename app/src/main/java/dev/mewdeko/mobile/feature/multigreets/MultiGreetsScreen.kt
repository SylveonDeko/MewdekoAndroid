package dev.mewdeko.mobile.feature.multigreets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.WavingHand
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
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
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add greet") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Greet mode", Icons.Default.WavingHand)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MultiGreetType.entries.forEach { type ->
                    FilterChip(
                        selected = state.greetType == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(type.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = state.greetType.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                )
            }
        } else {
            state.greets.forEach { greet ->
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Auto-delete",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        listOf(0, 30, 60, 300).forEach { seconds ->
                            TextButton(
                                onClick = { viewModel.updateDeleteTime(greet.id, seconds) },
                            ) {
                                Text(
                                    text = if (seconds == 0) "Never" else "${seconds}s",
                                    color = if (greet.deleteTime == seconds) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    EmbedMessageEditor(
                        message = EmbedMessage.parse(greet.message),
                        onMessageChange = { viewModel.updateMessage(greet.id, it) },
                    )
                    if (!greet.webhookUrl.isNullOrBlank()) {
                        TagChip("Webhook")
                    }
                }
            }
        }
    }

    if (showAdd) {
        var channelId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add greet channel") },
            text = {
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                    placeholder = "Pick a channel",
                    label = "Post welcomes in",
                    selectedId = channelId,
                    onSelect = { channelId = it },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        channelId?.let { viewModel.add(it) }
                        showAdd = false
                    },
                    enabled = channelId != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
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
