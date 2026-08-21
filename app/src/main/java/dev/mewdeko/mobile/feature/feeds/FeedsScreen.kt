package dev.mewdeko.mobile.feature.feeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Tag
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
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

/** RSS and social feed subscriptions. */
@Composable
fun FeedsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: FeedsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FeedSubscription?>(null) }
    var editingMessage by remember { mutableStateOf<FeedSubscription?>(null) }

    FeatureScaffold(
        title = "Feeds",
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
                text = { Text("Add feed") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.RssFeed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Feeds",
                    value = "${state.stats?.totalFeeds ?: state.feeds.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Channels",
                    value = "${state.feeds.map { it.channelId }.distinct().size}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.feeds.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No feeds yet. Add an RSS URL to mirror it into a channel.",
                    icon = Icons.Default.RssFeed,
                )
            }
        } else {
            state.feeds.forEach { feed ->
                SectionCard(contentPadding = 12) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = feed.url,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TagChip(
                                    label = "#${feed.channelName ?: state.channelName(feed.channelId)}",
                                    icon = Icons.Default.Tag,
                                )
                                feed.dateAdded?.let { TagChip(it.relativeToNow()) }
                            }
                        }
                        IconButton(onClick = { pendingDelete = feed }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove feed",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = { editingMessage = feed }) {
                        Text(
                            if (feed.message.isNullOrBlank()) "Set announcement message"
                            else "Edit announcement message"
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFeedDialog(
            channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) },
            onDismiss = { showAdd = false },
            onAdd = { channelId, url ->
                viewModel.add(channelId, url)
                showAdd = false
            },
        )
    }

    pendingDelete?.let { feed ->
        ConfirmDialog(
            title = "Remove feed?",
            message = feed.url,
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(feed) },
            onDismiss = { pendingDelete = null },
        )
    }

    editingMessage?.let { feed ->
        var draft by remember(feed.index) { mutableStateOf(EmbedMessage.parse(feed.message)) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Announcement message") },
            text = {
                EmbedMessageEditor(message = draft, onMessageChange = { draft = it })
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setMessage(feed, draft)
                        editingMessage = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AddFeedDialog(
    channelOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add feed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MewdekoTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Feed URL",
                    placeholder = "https://example.com/rss.xml",
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Pick a channel",
                    label = "Post to",
                    selectedId = channelId,
                    onSelect = { channelId = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { channelId?.let { onAdd(it, url.trim()) } },
                enabled = url.isNotBlank() && channelId != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
