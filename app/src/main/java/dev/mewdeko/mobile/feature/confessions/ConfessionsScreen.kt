package dev.mewdeko.mobile.feature.confessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

/** Anonymous confession submissions. */
@Composable
fun ConfessionsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ConfessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ConfessionRecord?>(null) }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Confessions",
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
                    text = { Text(if (state.isSaving) "Saving…" else "Save channels") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Lock)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Total",
                    value = "${state.stats?.totalConfessions ?: state.confessions.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "This month",
                    value = "${state.stats?.confessionsThisMonth ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Today",
                    value = "${state.stats?.confessionsToday ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
            state.stats?.lastConfessionDate?.let {
                Text(
                    text = "Last confession ${it.relativeToNow()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard {
            SectionCardHeader("Channels", Icons.Default.Tag)
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "No confession channel",
                label = "Post confessions in",
                selectedId = state.channelId,
                onSelect = viewModel::setChannel,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "No log channel",
                label = "Moderator log",
                selectedId = state.logChannelId,
                onSelect = viewModel::setLogChannel,
            )
            Text(
                text = "The log channel records which member submitted each confession. Leave it " +
                    "unset to keep submissions fully anonymous.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Blacklisted roles", Icons.Default.Block)
            Text(
                text = "Members holding a blacklisted role cannot submit confessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.availableRoles.isEmpty()) {
                EmptyState("No roles found.")
            } else {
                state.availableRoles.forEach { role ->
                    ListItem(
                        headlineContent = { Text("@${role.name}") },
                        trailingContent = {
                            Checkbox(checked = role.id in state.blacklist, onCheckedChange = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickableRow { viewModel.toggleBlacklist(role.id) },
                    )
                }
            }
        }

        SectionCard {
            SectionCardHeader("Recent confessions", Icons.Default.Lock)
            if (state.confessions.isEmpty()) {
                EmptyState("No confessions submitted yet.", icon = Icons.Default.Lock)
            } else {
                state.confessions.forEach { confession ->
                    ListItem(
                        overlineContent = { Text("#${confession.number}") },
                        headlineContent = {
                            Text(
                                text = confession.text,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = confession.dateAdded?.let {
                            { Text(it.relativeToNow()) }
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = confession }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete confession",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    pendingDelete?.let { confession ->
        ConfirmDialog(
            title = "Delete confession?",
            message = "Confession #${confession.number} is removed from the channel and the log.",
            onConfirm = { viewModel.delete(confession.number) },
            onDismiss = { pendingDelete = null },
        )
    }
}
