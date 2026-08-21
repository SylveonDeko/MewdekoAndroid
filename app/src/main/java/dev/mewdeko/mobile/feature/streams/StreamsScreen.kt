package dev.mewdeko.mobile.feature.streams

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Twitch, YouTube, Trovo, and Facebook go-live notifications. */
@Composable
fun StreamsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StreamsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingUnfollow by remember { mutableStateOf<FollowedStream?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }
    var editingOnline by remember { mutableStateOf<FollowedStream?>(null) }
    var editingOffline by remember { mutableStateOf<FollowedStream?>(null) }

    FeatureScaffold(
        title = "Streams",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            if (state.streams.isNotEmpty()) {
                IconButton(onClick = { pendingClearAll = true }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Unfollow all",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.hasUnsavedMessage) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveCustomMessage,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save template") },
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Follow stream") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.VideoCameraFront)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Following",
                    value = "${state.stats?.totalStreams ?: state.streams.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Channels",
                    value = "${state.streams.map { it.channelId }.distinct().size}",
                    modifier = Modifier.weight(1f),
                )
            }
            val byPlatform = state.stats?.streamsByType.orEmpty()
            if (byPlatform.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    byPlatform.forEach { entry ->
                        TagChip(
                            label = "${entry.typeName ?: entry.platform.label}: ${entry.count}",
                            icon = entry.platform.icon,
                        )
                    }
                }
            }
            SwitchRow(
                title = "Offline notifications",
                subtitle = "Also post when a followed streamer goes offline",
                checked = state.offlineNotifications,
                onCheckedChange = { viewModel.toggleOfflineNotifications() },
            )
        }

        SectionCard {
            SectionCardHeader("Default template", Icons.Default.VideoCameraFront)
            Text(
                text = "Used for any follow without its own message.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbedMessageEditor(
                message = state.customMessage,
                onMessageChange = viewModel::setCustomMessage,
            )
        }

        if (state.streams.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "Not following any streamers yet.",
                    icon = Icons.Default.VideoCameraFront,
                )
            }
        } else {
            state.streams.forEach { stream ->
                SectionCard(contentPadding = 12) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            stream.platform.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stream.username,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${stream.typeName ?: stream.platform.label} · " +
                                    "#${stream.channelName ?: state.channelName(stream.channelId)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { pendingUnfollow = stream }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Unfollow",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { editingOnline = stream }) { Text("Online message") }
                        TextButton(onClick = { editingOffline = stream }) { Text("Offline message") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var url by remember { mutableStateOf("") }
        var channelId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Follow stream") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = "Stream URL",
                        placeholder = "https://twitch.tv/example",
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a channel",
                        label = "Notify in",
                        selectedId = channelId,
                        onSelect = { channelId = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        channelId?.let { viewModel.follow(it, url.trim()) }
                        showAdd = false
                    },
                    enabled = url.isNotBlank() && channelId != null,
                ) { Text("Follow") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }

    editingOnline?.let { stream ->
        StreamMessageDialog(
            title = "Online message",
            initial = EmbedMessage.parse(stream.onlineMessage),
            onDismiss = { editingOnline = null },
            onSave = { viewModel.setOnlineMessage(stream.index, it); editingOnline = null },
        )
    }

    editingOffline?.let { stream ->
        StreamMessageDialog(
            title = "Offline message",
            initial = EmbedMessage.parse(stream.offlineMessage),
            onDismiss = { editingOffline = null },
            onSave = { viewModel.setOfflineMessage(stream.index, it); editingOffline = null },
        )
    }

    pendingUnfollow?.let { stream ->
        ConfirmDialog(
            title = "Unfollow ${stream.username}?",
            message = "Go-live notifications for this streamer stop.",
            confirmLabel = "Unfollow",
            onConfirm = { viewModel.unfollow(stream.index) },
            onDismiss = { pendingUnfollow = null },
        )
    }

    if (pendingClearAll) {
        ConfirmDialog(
            title = "Unfollow every stream?",
            message = "All ${state.streams.size} follows are removed.",
            confirmLabel = "Unfollow all",
            onConfirm = viewModel::clearAll,
            onDismiss = { pendingClearAll = false },
        )
    }
}

@Composable
private fun StreamMessageDialog(
    title: String,
    initial: EmbedMessage,
    onDismiss: () -> Unit,
    onSave: (EmbedMessage) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { EmbedMessageEditor(message = draft, onMessageChange = { draft = it }) },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
