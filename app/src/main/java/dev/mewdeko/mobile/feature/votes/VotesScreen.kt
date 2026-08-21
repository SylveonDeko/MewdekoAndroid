package dev.mewdeko.mobile.feature.votes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MonospaceStyle
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
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.formatDuration
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("history", "History", Icons.Default.History),
)

/** Vote tracking, reward roles, and the vote leaderboard. */
@Composable
fun VotesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: VotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAddRole by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Votes",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsavedConfig) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveConfig,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save settings") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.ThumbUp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Votes", "${state.votes.size}", Modifier.weight(1f))
                StatTile("Voters", "${state.leaderboard.size}", Modifier.weight(1f))
                StatTile("Reward roles", "${state.voteRoles.size}", Modifier.weight(1f))
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "leaderboard" -> SectionCard {
                SectionCardHeader("Top voters", Icons.Default.Leaderboard)
                if (state.leaderboard.isEmpty()) {
                    EmptyState("No votes recorded yet.", icon = Icons.Default.ThumbUp)
                } else {
                    state.leaderboard.forEachIndexed { index, entry ->
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = entry.userId ?: "Unknown",
                                    style = MonospaceStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = "${entry.voteCount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            "history" -> SectionCard {
                SectionCardHeader("Recent votes", Icons.Default.History)
                if (state.votes.isEmpty()) {
                    EmptyState("No votes recorded yet.", icon = Icons.Default.History)
                } else {
                    state.votes.take(50).forEach { vote ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = vote.userId ?: "Unknown",
                                    style = MonospaceStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = vote.dateAdded?.let {
                                { Text(it.relativeToNow()) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            else -> {
                SectionCard {
                    SectionCardHeader("Announcement", Icons.Default.Tune)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No announcement channel",
                        label = "Announce votes in",
                        selectedId = state.channelId,
                        onSelect = viewModel::setChannel,
                    )
                    EmbedMessageEditor(
                        message = state.message,
                        onMessageChange = viewModel::setMessage,
                    )
                    MewdekoTextField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = "Webhook password",
                        supportingText = "Shared secret the vote site sends back to the bot.",
                    )
                }

                SectionCard {
                    SectionCardHeader(
                        title = "Reward roles",
                        icon = Icons.Default.ThumbUp,
                        trailing = {
                            if (state.voteRoles.isNotEmpty()) {
                                IconButton(onClick = { pendingClear = true }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Clear all",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    if (state.voteRoles.isEmpty()) {
                        EmptyState("No reward roles configured.")
                    } else {
                        state.voteRoles.forEach { entry ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "@${state.roleName(entry.roleId)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (entry.timer <= 0) "Permanent"
                                        else formatDuration(entry.timer.toLong()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeVoteRole(entry.roleId) },
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Remove role",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(0, 3600, 21600, 86400).forEach { seconds ->
                                        TextButton(
                                            onClick = {
                                                viewModel.updateTimer(entry.roleId, seconds)
                                            },
                                        ) {
                                            Text(
                                                text = if (seconds == 0) "Permanent"
                                                else formatDuration(seconds.toLong()),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAddRole = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add reward role", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (showAddRole) {
        var roleId by remember { mutableStateOf<String?>(null) }
        var hours by remember { mutableIntStateOf(12) }
        AlertDialog(
            onDismissRequest = { showAddRole = false },
            title = { Text("Add reward role") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a role",
                        label = "Role",
                        selectedId = roleId,
                        onSelect = { roleId = it },
                    )
                    SliderRow(
                        label = "Keep for",
                        value = hours.toFloat(),
                        onValueChange = { hours = it.toInt() },
                        valueRange = 0f..168f,
                        valueLabel = if (hours == 0) "Permanent" else "${hours}h",
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roleId?.let { viewModel.addVoteRole(it, hours * 3600) }
                        showAddRole = false
                    },
                    enabled = roleId != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddRole = false }) { Text("Cancel") } },
        )
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "Clear all reward roles?",
            message = "Every configured vote reward role is removed.",
            confirmLabel = "Clear all",
            onConfirm = viewModel::clearAllRoles,
            onDismiss = { pendingClear = false },
        )
    }
}
