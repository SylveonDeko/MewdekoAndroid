package dev.mewdeko.mobile.feature.chatsaver

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.LoadingState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDateTime
import java.time.format.DateTimeFormatter

private val Tabs = listOf(
    SectionTab("fetch", "Fetch", Icons.Default.Search),
    SectionTab("saved", "Saved", Icons.Default.Folder),
    SectionTab("view", "View", Icons.Default.Forum),
)

private val TimeUnitOptions = ChatTimeUnit.entries.map { SelectorOption(it.id, it.label) }

/** Chat Saver: fetch live channel history, browse saved logs, and view a Discord-style transcript. */
@Composable
fun ChatSaverScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ChatSaverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingDelete by remember { mutableStateOf<ChatLogSummary?>(null) }
    var renaming by remember { mutableStateOf<ChatLogSummary?>(null) }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, export.filename)
            putExtra(Intent.EXTRA_TEXT, export.content)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${export.filename}"))
        viewModel.clearPendingExport()
    }

    FeatureScaffold(
        title = "Chat Saver",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "fetch" -> FetchSection(state, viewModel)
            "saved" -> SavedSection(
                state = state,
                onOpen = viewModel::openLog,
                onRename = { renaming = it },
                onDelete = { pendingDelete = it },
            )

            "view" -> ViewSection(
                state = state,
                onSave = viewModel::saveLog,
                onExport = { viewModel.exportHtml(guild.name.ifEmpty { "Server" }) },
            )
        }
    }

    pendingDelete?.let { log ->
        ConfirmDialog(
            title = "Delete log?",
            message = "\"${log.displayName}\" and its ${log.messageCount} messages are removed.",
            onConfirm = { viewModel.delete(log) },
            onDismiss = { pendingDelete = null },
        )
    }

    renaming?.let { log ->
        var draft by remember(log.id) { mutableStateOf(log.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename log") },
            text = {
                MewdekoTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = "Name",
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.rename(log, draft.trim()); renaming = null },
                    enabled = draft.isNotBlank(),
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FetchSection(state: ChatSaverState, viewModel: ChatSaverViewModel) {
    SectionCard {
        SectionCardHeader("Fetch live messages", Icons.Default.Search)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Select a channel",
            label = "Channel",
            selectedId = state.selectedChannelId,
            onSelect = viewModel::setChannel,
        )
        MewdekoTextField(
            value = state.timeAmount,
            onValueChange = viewModel::setTimeAmount,
            label = "Time amount",
            numeric = true,
            supportingText = "Up to ${state.timeUnit.maxAmount} ${state.timeUnit.label.lowercase()}",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.CalendarMonth),
            options = TimeUnitOptions,
            placeholder = "Hours",
            label = "Time unit",
            selectedId = state.timeUnit.id,
            onSelect = { viewModel.setTimeUnit(it ?: ChatTimeUnit.HOURS.id) },
        )
        Button(
            onClick = viewModel::fetchMessages,
            enabled = !state.isFetching,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isFetching) "Loading…" else "Load messages")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedSection(
    state: ChatSaverState,
    onOpen: (String) -> Unit,
    onRename: (ChatLogSummary) -> Unit,
    onDelete: (ChatLogSummary) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Overview", Icons.Default.Storage)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Logs", "${state.logs.size}", Modifier.weight(1f))
            StatTile("Messages", "${state.totalMessages}", Modifier.weight(1f))
        }
    }

    SectionCard {
        SectionCardHeader("Saved logs", Icons.Default.Folder)
        if (state.logs.isEmpty()) {
            EmptyState(
                message = "No saved logs. Fetch messages, then save the transcript, or use the bot's " +
                    "chat save command.",
                icon = Icons.Default.Folder,
            )
        } else {
            state.logs.forEach { log ->
                ListItem(
                    headlineContent = {
                        Text(log.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TagChip("${log.messageCount} messages")
                            log.channelName?.let { TagChip("#$it", icon = Icons.Default.Tag) }
                            log.timestamp?.let { TagChip(it) }
                        }
                    },
                    trailingContent = { SavedLogActions(log, onRename, onDelete) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onOpen(log.id) },
                )
            }
        }
    }
}

/** Overflow menu for a saved log row, so a long timestamp chip never squeezes the trailing icons. */
@Composable
private fun SavedLogActions(
    log: ChatLogSummary,
    onRename: (ChatLogSummary) -> Unit,
    onDelete: (ChatLogSummary) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${log.displayName}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { open = false; onRename(log) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { open = false; onDelete(log) },
            )
        }
    }
}

@Composable
private fun ViewSection(
    state: ChatSaverState,
    onSave: () -> Unit,
    onExport: () -> Unit,
) {
    if (state.isLoadingDetail) {
        LoadingState(modifier = Modifier.padding(48.dp))
        return
    }

    if (state.messages.isEmpty()) {
        EmptyState(
            message = "No messages to display. Fetch live messages or open a saved log.",
            icon = Icons.Default.Forum,
        )
        return
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.viewingChannelName?.let { "#$it" } ?: "Transcript",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${state.messages.size} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.viewingLogId == null) {
                IconButton(onClick = onSave, enabled = !state.isSaving) {
                    Icon(Icons.Default.Save, contentDescription = "Save log")
                }
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Download, contentDescription = "Export HTML")
            }
        }
    }

    if (state.messages.size >= 1000) {
        SectionCard {
            Text(
                text = "Showing ${state.messages.size} messages. There may be more messages that are not displayed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }

    for ((date, dayMessages) in groupMessagesByDay(state.messages)) {
        SectionCard {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            groupMessagesByAuthor(dayMessages).forEach { group ->
                MessageGroup(group, state.members, state.availableChannels)
            }
        }
    }
}

@Composable
private fun MessageGroup(
    group: List<ChatLogMessage>,
    members: List<GuildMember>,
    channels: List<TextChannelLite>,
) {
    val first = group.first()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(url = first.author.avatarUrl, contentDescription = first.author.username, size = 36)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(first.author.username, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = first.timestamp.shortDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            group.forEachIndexed { index, message ->
                if (index > 0) {
                    Text(
                        text = message.timestamp.shortDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                message.content?.takeIf { it.isNotBlank() }?.let { content ->
                    Text(
                        text = rememberChatMessageText(content, members, channels),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (message.attachments.isNotEmpty()) {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.attachments.forEach { attachment -> AttachmentRow(attachment) }
                    }
                }
                if (message.embeds.isNotEmpty()) {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        message.embeds.forEach { embed -> EmbedCard(embed, members, channels) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: ChatLogAttachment) {
    val uriHandler = LocalUriHandler.current
    if (isImageUrl(attachment.url)) {
        AsyncImage(
            model = attachment.proxyUrl.ifBlank { attachment.url },
            contentDescription = attachment.filename,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { uriHandler.openUri(attachment.url) },
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(attachment.url) }
                .padding(8.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "${attachment.filename} (${formatFileSize(attachment.fileSize)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmbedCard(
    embed: ChatLogEmbed,
    members: List<GuildMember>,
    channels: List<TextChannelLite>,
) {
    val uriHandler = LocalUriHandler.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                embed.author?.let { author ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        author.iconUrl?.let {
                            Avatar(url = it, contentDescription = author.name, size = 18)
                        }
                        Text(author.name, style = MaterialTheme.typography.labelMedium)
                    }
                }
                embed.title?.takeIf { it.isNotBlank() }?.let { title ->
                    val embedUrl = embed.url
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = if (embedUrl != null) {
                            Modifier.clickable { uriHandler.openUri(embedUrl) }
                        } else {
                            Modifier
                        },
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (embedUrl != null) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open link",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
                embed.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = rememberChatMessageText(it, members, channels),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            embed.thumbnail?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
    }
}
