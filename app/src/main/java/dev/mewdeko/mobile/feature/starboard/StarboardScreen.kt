package dev.mewdeko.mobile.feature.starboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("boards", "Boards", Icons.Default.Star),
    SectionTab("highlights", "Top posts", Icons.Default.Star),
)

/** Star-pinned message boards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarboardScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StarboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StarboardConfig?>(null) }
    var editingChannelsId by remember { mutableStateOf<Int?>(null) }
    var addingEmote by remember { mutableStateOf<StarboardConfig?>(null) }

    FeatureScaffold(
        title = "Starboard",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New starboard") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Star)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Boards", "${state.boards.size}", Modifier.weight(1f))
                StatTile(
                    label = "Starred posts",
                    value = "${state.stats?.totalStarredMessages ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Total stars",
                    value = "${state.stats?.totalStars ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
            state.stats?.let { stats ->
                stats.mostStarredUser?.userId?.let {
                    Text(
                        text = "Most starred: $it (${stats.mostStarredUser.totalStars} stars)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                stats.mostActiveStarrer?.userId?.let {
                    Text(
                        text = "Most generous: $it " +
                            "(${stats.mostActiveStarrer.starsGiven} given)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        if (state.section == "highlights") {
            if (state.highlights.isEmpty()) {
                SectionCard {
                    EmptyState("No starred posts yet.", icon = Icons.Default.Star)
                }
            } else {
                state.highlights.forEach { highlight ->
                    SectionCard(contentPadding = 12) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Avatar(
                                url = highlight.authorAvatarUrl,
                                contentDescription = highlight.authorName,
                                size = 32,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = highlight.authorName,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                highlight.createdAt?.let {
                                    Text(
                                        text = it.relativeToNow(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TagChip(
                                label = "${highlight.starEmote ?: "⭐"} ${highlight.starCount}",
                            )
                        }
                        highlight.content?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        highlight.imageUrl?.takeIf { it.isNotBlank() }?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                        }
                    }
                }
            }
            return@FeatureScaffold
        }

        if (state.boards.isEmpty()) {
            SectionCard {
                EmptyState("No starboards configured yet.", icon = Icons.Default.Star)
            }
        } else {
            state.boards.forEach { board ->
                SectionCard {
                    SectionCardHeader(
                        title = "#${state.channelName(board.starboardChannelId)}",
                        icon = Icons.Default.Star,
                        trailing = {
                            IconButton(onClick = { pendingDelete = board }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete starboard",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val canRemoveEmote = board.emotes.size > 1
                        board.emotes.forEach { emote ->
                            InputChip(
                                selected = true,
                                enabled = canRemoveEmote,
                                onClick = { viewModel.removeEmote(board.id, emote) },
                                label = { Text(emote) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove emote")
                                },
                            )
                        }
                        AssistChip(
                            onClick = { addingEmote = board },
                            label = { Text("Add emote") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        )
                    }
                    var starThresholdDraft by remember(board.id, board.threshold) {
                        mutableStateOf(board.threshold.toString())
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MewdekoTextField(
                            value = starThresholdDraft,
                            onValueChange = { starThresholdDraft = it.filter { c -> c.isDigit() } },
                            label = "Stars needed",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        val starThresholdValue = starThresholdDraft.toIntOrNull()
                        Button(
                            onClick = {
                                starThresholdValue?.let { viewModel.setThreshold(board.id, it) }
                            },
                            enabled = starThresholdValue != null && starThresholdValue >= 1 &&
                                starThresholdValue != board.threshold,
                        ) { Text("Save") }
                    }
                    var repostThresholdDraft by remember(board.id, board.repostThreshold) {
                        mutableStateOf(board.repostThreshold.toString())
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MewdekoTextField(
                            value = repostThresholdDraft,
                            onValueChange = {
                                repostThresholdDraft = it.filter { c -> c.isDigit() }
                            },
                            label = "Repost after (0 to disable)",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        val repostThresholdValue = repostThresholdDraft.toIntOrNull()
                        Button(
                            onClick = {
                                repostThresholdValue?.let {
                                    viewModel.setRepostThreshold(board.id, it)
                                }
                            },
                            enabled = repostThresholdValue != null &&
                                repostThresholdValue >= 0 &&
                                repostThresholdValue != board.repostThreshold,
                        ) { Text("Save") }
                    }
                    SwitchRow(
                        title = "Allow bot messages",
                        checked = board.allowBots,
                        onCheckedChange = { viewModel.setAllowBots(board.id, it) },
                    )
                    SwitchRow(
                        title = "Remove when original deleted",
                        checked = board.removeOnDelete,
                        onCheckedChange = { viewModel.setRemoveOnDelete(board.id, it) },
                    )
                    SwitchRow(
                        title = "Remove when reactions cleared",
                        checked = board.removeOnReactionsClear,
                        onCheckedChange = { viewModel.setRemoveOnClear(board.id, it) },
                    )
                    SwitchRow(
                        title = "Remove when below threshold",
                        checked = board.removeOnBelowThreshold,
                        onCheckedChange = { viewModel.setRemoveBelowThreshold(board.id, it) },
                    )
                    SwitchRow(
                        title = "Channel list is a block-list",
                        subtitle = if (board.useBlacklist) {
                            "Listed channels are ignored"
                        } else {
                            "Only listed channels are watched"
                        },
                        checked = board.useBlacklist,
                        onCheckedChange = { viewModel.setUseBlacklist(board.id, it) },
                    )
                    TextButton(onClick = { editingChannelsId = board.id }) {
                        Text("Channels (${board.checkedChannelIds.size})")
                    }
                }
            }
        }
    }

    if (showCreate) {
        var channelId by remember { mutableStateOf<String?>(null) }
        var emote by remember { mutableStateOf("⭐") }
        var thresholdText by remember { mutableStateOf("3") }
        val threshold = thresholdText.toIntOrNull()
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New starboard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a channel",
                        label = "Post starred messages in",
                        selectedId = channelId,
                        onSelect = { channelId = it },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("⭐", "🌟", "😲", "✨").forEach { preset ->
                            AssistChip(
                                onClick = { emote = preset },
                                label = { Text(preset) },
                            )
                        }
                    }
                    MewdekoTextField(
                        value = emote,
                        onValueChange = { emote = it },
                        label = "Star emote",
                        placeholder = "⭐ or <:name:id>",
                    )
                    MewdekoTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it.filter { c -> c.isDigit() } },
                        label = "Threshold (reactions needed)",
                        numeric = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = channelId
                        val value = threshold
                        if (id != null && value != null) {
                            viewModel.create(id, emote, value)
                            showCreate = false
                        }
                    },
                    enabled = channelId != null && emote.isNotBlank() &&
                        threshold != null && threshold >= 1,
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }

    addingEmote?.let { board ->
        var emote by remember(board.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingEmote = null },
            title = { Text("Add star emote") },
            text = {
                MewdekoTextField(
                    value = emote,
                    onValueChange = { emote = it },
                    label = "Emote",
                    placeholder = "⭐",
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addEmote(board.id, emote.trim()); addingEmote = null },
                    enabled = emote.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingEmote = null }) { Text("Cancel") } },
        )
    }

    val editingChannelsBoard = editingChannelsId?.let { id ->
        state.boards.firstOrNull { it.id == id }
    }
    editingChannelsBoard?.let { board ->
        val staleChannelIds = board.checkedChannelIds.filter { checkedId ->
            state.availableChannels.none { it.id == checkedId }
        }
        AlertDialog(
            onDismissRequest = { editingChannelsId = null },
            title = { Text(if (board.useBlacklist) "Ignored channels" else "Watched channels") },
            text = {
                Column {
                    state.availableChannels.forEach { channel ->
                        ListItem(
                            headlineContent = { Text("#${channel.name}") },
                            trailingContent = {
                                Checkbox(
                                    checked = channel.id in board.checkedChannelIds,
                                    onCheckedChange = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow { viewModel.toggleChannel(board.id, channel.id) },
                        )
                    }
                    if (staleChannelIds.isNotEmpty()) {
                        Text(
                            text = "Listed channels no longer visible",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        staleChannelIds.forEach { staleId ->
                            ListItem(
                                headlineContent = { Text("Channel ID: $staleId") },
                                trailingContent = {
                                    IconButton(
                                        onClick = { viewModel.toggleChannel(board.id, staleId) },
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove channel",
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { editingChannelsId = null }) { Text("Done") }
            },
        )
    }

    pendingDelete?.let { board ->
        ConfirmDialog(
            title = "Delete starboard?",
            message = "The board in #${state.channelName(board.starboardChannelId)} stops " +
                "collecting starred messages.",
            onConfirm = { viewModel.delete(board.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}
