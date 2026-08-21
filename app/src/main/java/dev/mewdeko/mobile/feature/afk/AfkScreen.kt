package dev.mewdeko.mobile.feature.afk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
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
import dev.mewdeko.mobile.core.ui.SliderRow
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
            SliderRow(
                label = "Delete after",
                value = state.deletionSeconds.toFloat(),
                onValueChange = { viewModel.setDeletionSeconds(it.toInt()) },
                valueRange = 0f..300f,
                valueLabel = if (state.deletionSeconds == 0) "Off" else "${state.deletionSeconds}s",
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
            SliderRow(
                label = "Characters",
                value = state.maxLength.toFloat(),
                onValueChange = { viewModel.setMaxLength(it.toInt()) },
                valueRange = 1f..4096f,
                valueLabel = "${state.maxLength}",
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
                    if (state.afkUsers.isNotEmpty()) {
                        TextButton(onClick = { pendingClearAll = true }) { Text("Clear all") }
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("AFK members", "${state.afkUsers.size}", Modifier.weight(1f))
                StatTile("Timed", "${state.timedAfkCount}", Modifier.weight(1f))
                StatTile(
                    "Permanent",
                    "${state.afkUsers.size - state.timedAfkCount}",
                    Modifier.weight(1f),
                )
            }

            if (state.afkUsers.isEmpty()) {
                EmptyState("Nobody is currently AFK.", icon = Icons.Default.DarkMode)
            } else {
                state.afkUsers.forEach { user ->
                    AfkUserRow(user = user, onClear = { pendingClear = user })
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
private fun AfkUserRow(user: UserWithAfk, onClear: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    text = user.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.afkStatus?.wasTimed == true) {
                    Badge { Text("Timed") }
                }
            }
        },
        supportingContent = {
            Column {
                user.afkStatus?.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.afkStatus?.dateAdded?.let {
                        TagChip("Since ${it.relativeToNow()}", icon = Icons.Default.AccessTime)
                    }
                    user.afkStatus?.`when`?.let {
                        TagChip("Expires ${it.shortDateTime()}", icon = Icons.Default.Timer)
                    }
                }
            }
        },
        leadingContent = {
            Avatar(url = user.avatarUrl, contentDescription = user.displayName)
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
