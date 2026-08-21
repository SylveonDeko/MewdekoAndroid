package dev.mewdeko.mobile.feature.rolestates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Saved member roles that survive a leave and rejoin. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleStatesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RoleStatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClear by remember { mutableStateOf<UserRoleStateRecord?>(null) }
    var editingRoles by remember { mutableStateOf<UserRoleStateRecord?>(null) }
    var pendingSaveAll by remember { mutableStateOf(false) }

    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Role States",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsavedSettings) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveSettings,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save settings") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Sync)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Saved members", "${state.users.size}", Modifier.weight(1f))
                StatTile(
                    label = "Saved roles",
                    value = "${state.users.sumOf { it.roleIds.size }}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Status",
                    value = if (state.settings.enabled) "On" else "Off",
                    tint = if (state.settings.enabled) MaterialTheme.colorScheme.primary else null,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedButton(
                onClick = { pendingSaveAll = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Snapshot every member's roles now") }
        }

        SectionCard {
            SectionCardHeader("Settings", Icons.Default.Tune)
            SwitchRow(
                title = "Save role states",
                subtitle = "Restore a member's roles when they rejoin",
                checked = state.settings.enabled,
                onCheckedChange = { viewModel.toggleEnabled() },
            )
            SwitchRow(
                title = "Clear on ban",
                subtitle = "Discard a member's saved roles when they are banned",
                checked = state.settings.clearOnBan,
                onCheckedChange = { viewModel.toggleClearOnBan() },
            )
            SwitchRow(
                title = "Ignore bots",
                subtitle = "Do not save role states for bot accounts",
                checked = state.settings.ignoreBots,
                onCheckedChange = { viewModel.toggleIgnoreBots() },
            )
            SwitchRow(
                title = "Skip auto-assign roles",
                subtitle = "Do not restore roles the bot would grant automatically",
                checked = state.settings.skipAutoAssignRoles,
                onCheckedChange = viewModel::setSkipAutoAssign,
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles excluded",
                label = "Never save these roles",
                multiple = true,
                selection = state.settings.deniedRoleIds,
                onSelectionChange = viewModel::setDeniedRoles,
            )
        }

        SectionCard {
            SectionCardHeader("Saved members", Icons.Default.Sync)
            SearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search by name or ID",
            )
            if (state.visibleUsers.isEmpty()) {
                EmptyState(
                    message = if (state.query.isBlank()) "No saved role states yet."
                    else "No members match \"${state.query}\".",
                    icon = Icons.Default.Sync,
                )
            } else {
                state.visibleUsers.forEach { record ->
                    SectionCard(contentPadding = 12) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                text = record.userName?.takeIf { it.isNotBlank() }
                                    ?: record.userId.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { pendingClear = record }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear role state",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (record.roleIds.isEmpty()) {
                            Text(
                                text = "No roles saved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                record.roleIds.forEach { id ->
                                    TagChip("@${state.roleName(id)}")
                                }
                            }
                        }
                        TextButton(onClick = { editingRoles = record }) { Text("Edit saved roles") }
                    }
                }
            }
        }
    }

    editingRoles?.let { record ->
        var selection by remember(record.id) { mutableStateOf(record.roleIds) }
        AlertDialog(
            onDismissRequest = { editingRoles = null },
            title = { Text("Saved roles") },
            text = {
                DiscordSelector(
                    kind = SelectorKind.Role,
                    options = roleOptions,
                    placeholder = "No roles",
                    multiple = true,
                    selection = selection,
                    onSelectionChange = { selection = it },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        record.userId?.let { viewModel.setRoles(it, selection) }
                        editingRoles = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingRoles = null }) { Text("Cancel") }
            },
        )
    }

    pendingClear?.let { record ->
        ConfirmDialog(
            title = "Clear role state?",
            message = "Saved roles for " +
                "${record.userName ?: record.userId.orEmpty()} are discarded.",
            confirmLabel = "Clear",
            onConfirm = { record.userId?.let { viewModel.clearUser(it) } },
            onDismiss = { pendingClear = null },
        )
    }

    if (pendingSaveAll) {
        ConfirmDialog(
            title = "Snapshot all roles?",
            message = "Every current member's roles are recorded, overwriting existing saved " +
                "states.",
            confirmLabel = "Snapshot",
            destructive = false,
            onConfirm = viewModel::saveAll,
            onDismiss = { pendingSaveAll = false },
        )
    }
}
