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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import dev.mewdeko.mobile.feature.embed.EmbedPreview
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate

/** One `%stream.*%` token the bot fills in when it sends a go-live or offline message. */
private data class StreamPlaceholder(val token: String, val description: String)

/** Mirrors the dashboard's stream-specific placeholder list (`CreateStreamReplacer` on the bot). */
private val StreamPlaceholders = listOf(
    StreamPlaceholder("%stream.name%", "Display name of the streamer"),
    StreamPlaceholder("%stream.username%", "Login name/username"),
    StreamPlaceholder("%stream.url%", "Direct URL to the stream"),
    StreamPlaceholder("%stream.title%", "Current stream title"),
    StreamPlaceholder("%stream.game%", "Game/category being streamed"),
    StreamPlaceholder("%stream.viewers%", "Current viewer count (- if offline)"),
    StreamPlaceholder("%stream.platform%", "Platform name (Twitch, YouTube, etc.)"),
    StreamPlaceholder("%stream.avatar%", "URL to streamer's avatar"),
    StreamPlaceholder("%stream.preview%", "URL to stream preview/thumbnail"),
    StreamPlaceholder("%stream.status%", "Online or offline status"),
    StreamPlaceholder("%stream.channelid%", "Platform-specific channel ID"),
)

/**
 * Reference card listing the `%stream.*%` placeholders a message editor accepts.
 *
 * The shared [EmbedMessageEditor] has no per-feature placeholder list, so this
 * sits next to it as a tappable, copyable reference instead.
 */
@Composable
private fun StreamPlaceholderReference(modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Tap a placeholder to copy it",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StreamPlaceholders.forEach { placeholder ->
                TagChip(
                    label = placeholder.token,
                    icon = Icons.Default.Code,
                    onClick = { clipboard.setText(AnnotatedString(placeholder.token)) },
                )
            }
        }
    }
}

/** Parses a stored online/offline message, or `null` if it is unset (blank, `"-"`, or empty). */
private fun parsedMessageOrNull(raw: String?): EmbedMessage? {
    if (raw.isNullOrBlank() || raw == "-") return null
    val parsed = EmbedMessage.parse(raw)
    return parsed.takeUnless { it.isEmpty }
}

/** Twitch, Picarto, YouTube, Trovo, and Kick go-live notifications. */
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Unique streamers",
                    value = "${state.streamers.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Platforms in use",
                    value = "${state.platformsInUse}",
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
                            label = "${entry.platformLabel}: ${entry.count}",
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
            StreamPlaceholderReference()
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
                                text = "${stream.platformLabel} · " +
                                    "#${stream.channelName ?: state.channelName(stream.channelId)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            stream.dateAdded?.let { added ->
                                Text(
                                    text = "Added ${added.shortDate()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { pendingUnfollow = stream }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Unfollow",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    val onlinePreview = remember(stream.onlineMessage) { parsedMessageOrNull(stream.onlineMessage) }
                    val offlinePreview = remember(stream.offlineMessage) { parsedMessageOrNull(stream.offlineMessage) }
                    Row {
                        TextButton(onClick = { editingOnline = stream }) {
                            if (onlinePreview != null) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Text("Online message")
                        }
                        TextButton(onClick = { editingOffline = stream }) {
                            if (offlinePreview != null) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                            Text("Offline message")
                        }
                    }
                    onlinePreview?.let { preview ->
                        Text(
                            text = "Online message preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EmbedPreview(
                            message = preview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                    offlinePreview?.let { preview ->
                        Text(
                            text = "Offline message preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        EmbedPreview(
                            message = preview,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
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
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EmbedMessageEditor(message = draft, onMessageChange = { draft = it })
                StreamPlaceholderReference()
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
