package dev.mewdeko.mobile.feature.messagestats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.compact

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.MarkEmailUnread),
    SectionTab("members", "Members", Icons.Default.Person),
    SectionTab("channels", "Channels", Icons.Default.Tag),
)

/** Per-channel and per-member message activity. */
@Composable
fun MessageStatsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: MessageStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Message Stats",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingReset = true }) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Reset counts",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        SectionCard {
            SwitchRow(
                title = "Count messages",
                subtitle = "When off, the bot stops recording new activity",
                checked = state.enabled,
                onCheckedChange = { viewModel.toggleCounting() },
            )
        }

        SectionCard {
            SectionCardHeader("Volume", Icons.Default.MarkEmailUnread)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Total",
                    value = state.stats?.totalMessages?.compact() ?: "-",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Today",
                    value = state.stats?.dailyMessages?.compact() ?: "-",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Tracked channels",
                    value = "${state.stats?.topChannels?.size ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "members" -> SectionCard {
                SectionCardHeader("Member leaderboard", Icons.Default.Leaderboard)
                val entries = state.leaderboard.ifEmpty { state.stats?.topUsers.orEmpty() }
                if (entries.isEmpty()) {
                    EmptyState("No member activity recorded.", icon = Icons.Default.Person)
                } else {
                    entries.forEachIndexed { index, user ->
                        ListItem(
                            leadingContent = { RankBadge(index + 1) },
                            headlineContent = {
                                Text(
                                    text = user.userId ?: "Unknown",
                                    style = MonospaceStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${user.dailyMessages.compact()} today")
                            },
                            trailingContent = {
                                Text(
                                    text = user.totalMessages.compact(),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            "channels" -> SectionCard {
                SectionCardHeader("Channel activity", Icons.Default.Tag)
                val channels = state.stats?.topChannels.orEmpty()
                if (channels.isEmpty()) {
                    EmptyState("No channel activity recorded.", icon = Icons.Default.Tag)
                } else {
                    channels.forEachIndexed { index, channel ->
                        ListItem(
                            leadingContent = { RankBadge(index + 1) },
                            headlineContent = {
                                Text(
                                    text = "#${channel.channelName ?: channel.channelId.orEmpty()}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text("${channel.dailyMessages.compact()} today")
                            },
                            trailingContent = {
                                Text(
                                    text = channel.totalMessages.compact(),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            else -> {
                SectionCard {
                    SectionCardHeader("Top members", Icons.Default.Person)
                    val users = state.stats?.topUsers.orEmpty().take(5)
                    if (users.isEmpty()) {
                        EmptyState("No member activity recorded.")
                    } else {
                        users.forEachIndexed { index, user ->
                            ListItem(
                                leadingContent = { RankBadge(index + 1) },
                                headlineContent = {
                                    Text(
                                        text = user.userId ?: "Unknown",
                                        style = MonospaceStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = { Text(user.totalMessages.compact()) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                SectionCard {
                    SectionCardHeader("Top channels", Icons.Default.Tag)
                    val channels = state.stats?.topChannels.orEmpty().take(5)
                    if (channels.isEmpty()) {
                        EmptyState("No channel activity recorded.")
                    } else {
                        channels.forEachIndexed { index, channel ->
                            ListItem(
                                leadingContent = { RankBadge(index + 1) },
                                headlineContent = {
                                    Text(
                                        text = "#${channel.channelName ?: channel.channelId.orEmpty()}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = { Text(channel.totalMessages.compact()) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "Reset all counts?",
            message = "Every recorded message count for this server is cleared. " +
                "This cannot be undone.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.reset() },
            onDismiss = { pendingReset = false },
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            color = when (rank) {
                1 -> MaterialTheme.colorScheme.primary
                2, 3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}
