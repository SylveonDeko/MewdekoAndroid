package dev.mewdeko.mobile.feature.afk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
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
import dev.mewdeko.mobile.util.shortDateTime

/** AFK configuration and the guild's currently-AFK members. */
@Composable
fun AfkScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: AfkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClearAll by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<UserWithAfk?>(null) }
    var pendingClearSelected by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "AFK System",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsavedChanges) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::save,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save changes") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Auto-deletion", Icons.Default.Delete)
            MewdekoTextField(
                value = state.deletionSeconds.toString(),
                onValueChange = { raw ->
                    viewModel.setDeletionSeconds(raw.filter { it.isDigit() }.take(9).toIntOrNull() ?: 0)
                },
                label = "Delete after (seconds)",
                numeric = true,
                supportingText = if (state.deletionSeconds == 0) {
                    "Off"
                } else {
                    AfkTime.secondsToString(state.deletionSeconds)
                },
                isError = state.deletionSeconds < 0,
            )
            Text(
                text = "Time before AFK acknowledgement messages are deleted. " +
                    "Zero disables auto-deletion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Max message length", Icons.Default.Notes)
            MewdekoTextField(
                value = state.maxLength.toString(),
                onValueChange = { raw ->
                    viewModel.setMaxLength(raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0)
                },
                label = "Characters",
                numeric = true,
                supportingText = "1 to 4096",
                isError = state.maxLength !in 1..4096,
            )
            Text(
                text = "Maximum allowed length for member-set AFK messages (1 to 4096).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Removal trigger", Icons.Default.ToggleOn)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AfkRemovalType.entries.forEach { type ->
                    FilterChip(
                        selected = state.removalType == type,
                        onClick = { viewModel.setRemovalType(type) },
                        label = { Text(type.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Text(
                text = state.removalType.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("AFK timeout", Icons.Default.Timer)
            MewdekoTextField(
                value = state.timeoutString,
                onValueChange = viewModel::setTimeout,
                label = "Timeout",
                placeholder = "1h2m3s",
                supportingText = "${AfkTime.stringToSeconds(state.timeoutString)} seconds",
                isError = AfkTime.stringToSeconds(state.timeoutString) !in 1..7200,
            )
            Text(
                text = "Time before someone is considered AFK after their last activity. " +
                    "Range: 1 second to 2 hours. Format: 1h2m3s.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Disabled channels", Icons.Default.Tag)
            DiscordSelector(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "No channels disabled",
                multiple = true,
                selection = state.disabledChannelIds,
                onSelectionChange = viewModel::setDisabledChannels,
            )
            Text(
                text = "AFK return-from messages will not be posted in the selected channels.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Custom message", Icons.Default.ChatBubble)
            EmbedMessageEditor(
                message = state.customMessage,
                onMessageChange = viewModel::setCustomMessage,
            )
            Text(
                text = "Embed template used when an AFK member returns. Leave empty to reset to " +
                    "the bot's default. Visit mewdeko.tech/placeholders for variables.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader(
                title = "Currently AFK",
                icon = Icons.Default.Groups,
                trailing = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.selectedIds.isNotEmpty()) {
                            TextButton(onClick = { pendingClearSelected = true }) {
                                Text("Remove (${state.selectedIds.size})")
                            }
                        }
                        if (state.afkUsers.isNotEmpty()) {
                            TextButton(onClick = { pendingClearAll = true }) { Text("Clear all") }
                        }
                    }
                },
            )

            if (state.afkUsers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleSelectAll() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = state.allSelected, onCheckedChange = { viewModel.toggleSelectAll() })
                    Text(
                        text = "Select all (${state.afkUsers.size})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("AFK members", "${state.afkUsers.size}", Modifier.weight(1f))
                StatTile("Timed", "${state.timedAfkCount}", Modifier.weight(1f))
                StatTile(
                    "Permanent",
                    "${state.afkUsers.size - state.timedAfkCount}",
                    Modifier.weight(1f),
                )
                StatTile("Selected", "${state.selectedIds.size}", Modifier.weight(1f))
            }

            if (state.afkUsers.isEmpty()) {
                EmptyState("Nobody is currently AFK.", icon = Icons.Default.DarkMode)
            } else {
                state.afkUsers.forEach { user ->
                    AfkUserRow(
                        user = user,
                        selected = user.userId in state.selectedIds,
                        expanded = user.userId in state.expandedIds,
                        onToggleSelected = { viewModel.toggleSelected(user.userId) },
                        onToggleExpanded = { viewModel.toggleExpanded(user.userId) },
                        onClear = { pendingClear = user },
                    )
                }
            }
        }
    }

    if (pendingClearAll) {
        ConfirmDialog(
            title = "Clear every AFK status?",
            message = "This removes the AFK status of all ${state.afkUsers.size} members.",
            confirmLabel = "Clear all",
            onConfirm = viewModel::clearAll,
            onDismiss = { pendingClearAll = false },
        )
    }

    if (pendingClearSelected) {
        ConfirmDialog(
            title = "Remove AFK status?",
            message = "Remove AFK status from ${state.selectedIds.size} selected member" +
                "${if (state.selectedIds.size == 1) "" else "s"}. This cannot be undone.",
            confirmLabel = "Remove AFK status",
            onConfirm = viewModel::clearSelected,
            onDismiss = { pendingClearSelected = false },
        )
    }

    pendingClear?.let { user ->
        ConfirmDialog(
            title = "Clear AFK?",
            message = "Remove the AFK status for ${user.displayName}.",
            confirmLabel = "Clear",
            onConfirm = { viewModel.clearAfk(user.userId) },
            onDismiss = { pendingClear = null },
        )
    }
}

@Composable
private fun AfkUserRow(
    user: UserWithAfk,
    selected: Boolean,
    expanded: Boolean,
    onToggleSelected: () -> Unit,
    onToggleExpanded: () -> Unit,
    onClear: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = user.username,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                user.nickname?.takeIf { it.isNotBlank() }?.let { nickname ->
                    TagChip(nickname)
                }
                if (user.afkStatus?.wasTimed == true) {
                    Badge { Text("Timed") }
                }
            }
        },
        supportingContent = {
            Column(modifier = Modifier.clickable(onClick = onToggleExpanded)) {
                user.afkStatus?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(
                        text = message,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.afkStatus?.dateAdded?.let {
                        TagChip("Since ${it.relativeToNow()}", icon = Icons.Default.AccessTime)
                    }
                    if (user.afkStatus?.wasTimed == true) {
                        user.afkStatus.`when`?.let {
                            TagChip("Expires ${it.shortDateTime()}", icon = Icons.Default.Timer)
                        }
                    } else {
                        TagChip("Permanent", icon = Icons.Default.AllInclusive)
                    }
                }
                if (expanded) {
                    user.afkStatus?.dateAdded?.let {
                        Text(
                            text = "Since ${it.shortDateTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        leadingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                Avatar(url = user.avatarUrl, contentDescription = user.displayName)
            }
        },
        trailingContent = {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear AFK for ${user.displayName}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
