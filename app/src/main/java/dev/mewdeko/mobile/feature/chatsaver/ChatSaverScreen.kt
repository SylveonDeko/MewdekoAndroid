package dev.mewdeko.mobile.feature.chatsaver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.LoadingState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Archived chat logs, with an in-app viewer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSaverScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ChatSaverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ChatLogSummary?>(null) }
    var renaming by remember { mutableStateOf<ChatLogSummary?>(null) }

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
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Storage)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Logs", "${state.logs.size}", Modifier.weight(1f))
                StatTile("Messages", "${state.totalMessages}", Modifier.weight(1f))
            }
        }

        SectionCard {
            SectionCardHeader("Saved logs", Icons.Default.Storage)
            if (state.logs.isEmpty()) {
                EmptyState(
                    message = "No saved logs. Use the bot's chat save command to archive a channel.",
                    icon = Icons.Default.Storage,
                )
            } else {
                state.logs.forEach { log ->
                    ListItem(
                        headlineContent = {
                            Text(log.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TagChip("${log.messageCount} messages")
                                log.channelName?.let { TagChip("#$it", icon = Icons.Default.Tag) }
                                log.timestamp?.let { TagChip(it) }
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = log }) {
                                    Icon(
                                        Icons.Default.DriveFileRenameOutline,
                                        contentDescription = "Rename",
                                    )
                                }
                                IconButton(onClick = { pendingDelete = log }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableRow { viewModel.openLog(log.id) },
                    )
                }
            }
        }
    }

    if (state.openLogId != null) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeLog,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            val detail = state.openLog
            when {
                state.isLoadingDetail -> LoadingState(modifier = Modifier.padding(48.dp))
                detail == null -> EmptyState("Could not load this log.")
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = detail.name?.takeIf { it.isNotBlank() }
                                ?: detail.channelName?.let { "#$it" }
                                ?: "Chat log",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "${detail.messageCount} messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                    ) {
                        items(detail.messages, key = { it.id }) { message ->
                            ListItem(
                                headlineContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = message.author.username,
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                        message.timestamp?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                supportingContent = {
                                    Text(message.content.orEmpty())
                                },
                                leadingContent = {
                                    Avatar(
                                        url = message.author.avatarUrl,
                                        contentDescription = message.author.username,
                                        size = 32,
                                    )
                                },
                            )
                        }
                    }
                }
            }
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
