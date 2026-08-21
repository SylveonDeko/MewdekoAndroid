package dev.mewdeko.mobile.feature.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("members", "Members", Icons.Default.Person),
    SectionTab("search", "Search", Icons.Default.Search),
    SectionTab("stats", "Stats", Icons.Default.Insights),
)

/** Guild-wide view of members' highlight words. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HighlightsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: HighlightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<GuildHighlight?>(null) }
    var pendingClearUser by remember { mutableStateOf<HighlightGroup?>(null) }

    FeatureScaffold(
        title = "Highlights",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.NotificationsActive)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Words",
                    value = "${state.stats?.totalHighlights ?: state.all.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Members",
                    value = "${state.stats?.totalUsers ?: state.grouped.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Muted",
                    value = "${state.disabled.size}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "search" -> {
                SectionCard {
                    SearchField(
                        value = state.query,
                        onValueChange = viewModel::search,
                        placeholder = "Search highlight words",
                    )
                }
                SectionCard {
                    SectionCardHeader("Results", Icons.Default.Search)
                    if (state.searchResults.isEmpty()) {
                        EmptyState(
                            message = if (state.query.isBlank()) "Type to search."
                            else "No highlights match \"${state.query}\".",
                            icon = Icons.Default.Search,
                        )
                    } else {
                        state.searchResults.forEach { highlight ->
                            HighlightRow(highlight, onDelete = { pendingDelete = highlight })
                        }
                    }
                }
            }

            "stats" -> {
                SectionCard {
                    SectionCardHeader("Most-used words", Icons.Default.Insights)
                    val words = state.stats?.topWords.orEmpty()
                    if (words.isEmpty()) {
                        EmptyState("No word statistics yet.")
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            words.forEach { TagChip("${it.word} · ${it.count}") }
                        }
                    }
                }
                SectionCard {
                    SectionCardHeader("Most-watchful members", Icons.Default.Person)
                    val users = state.stats?.topUsers.orEmpty()
                    if (users.isEmpty()) {
                        EmptyState("No member statistics yet.")
                    } else {
                        users.forEach { user ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = user.userId,
                                        style = MonospaceStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = { Text("${user.highlightCount} words") },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                SectionCard {
                    SectionCardHeader("Recently added", Icons.Default.NotificationsActive)
                    val recent = state.stats?.recentHighlights.orEmpty()
                    if (recent.isEmpty()) {
                        EmptyState("Nothing added recently.")
                    } else {
                        recent.forEach { entry ->
                            ListItem(
                                headlineContent = { Text(entry.word) },
                                supportingContent = {
                                    Text(
                                        text = buildString {
                                            append(entry.userId)
                                            entry.dateAdded?.let { append(" · ${it.relativeToNow()}") }
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                if (state.disabled.isNotEmpty()) {
                    SectionCard {
                        SectionCardHeader("Muted or scoped", Icons.Default.Block)
                        state.disabled.forEach { user ->
                            ListItem(
                                headlineContent = { Text(user.username) },
                                supportingContent = {
                                    Text(
                                        "${user.ignoredChannelsCount} channels, " +
                                            "${user.ignoredUsersCount} users ignored"
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            else -> {
                if (state.grouped.isEmpty()) {
                    SectionCard {
                        EmptyState(
                            message = "Nobody has registered a highlight word yet.",
                            icon = Icons.Default.NotificationsActive,
                        )
                    }
                } else {
                    state.grouped.forEach { group ->
                        SectionCard {
                            SectionCardHeader(
                                title = group.username,
                                icon = Icons.Default.Person,
                                trailing = {
                                    IconButton(onClick = { pendingClearUser = group }) {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            contentDescription = "Clear all for ${group.username}",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                group.words.forEach { highlight ->
                                    InputChip(
                                        selected = false,
                                        onClick = { pendingDelete = highlight },
                                        label = { Text(highlight.word) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { highlight ->
        ConfirmDialog(
            title = "Delete highlight?",
            message = "\"${highlight.word}\" stops notifying ${highlight.username}.",
            onConfirm = { viewModel.delete(highlight) },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingClearUser?.let { group ->
        ConfirmDialog(
            title = "Clear all highlights?",
            message = "All ${group.words.size} words registered by ${group.username} are removed.",
            confirmLabel = "Clear all",
            onConfirm = { viewModel.deleteAllForUser(group.userId) },
            onDismiss = { pendingClearUser = null },
        )
    }
}

@Composable
private fun HighlightRow(highlight: GuildHighlight, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(highlight.word) },
        supportingContent = {
            Text(
                text = buildString {
                    append(highlight.username)
                    highlight.dateAdded?.let { append(" · ${it.relativeToNow()}") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete ${highlight.word}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
