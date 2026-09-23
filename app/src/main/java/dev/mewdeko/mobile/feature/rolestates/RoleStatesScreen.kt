package dev.mewdeko.mobile.feature.rolestates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ManageAccounts
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
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
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
    var pendingCopy by remember { mutableStateOf(false) }

    var manageMemberId by remember { mutableStateOf<String?>(null) }
    var manageRoleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var copySourceId by remember { mutableStateOf<String?>(null) }
    var copyTargetId by remember { mutableStateOf<String?>(null) }

    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }
    val memberOptions = state.members.map {
        SelectorOption(it.id, it.displayName.ifBlank { it.username }, subtitle = it.username)
    }
    val savedStateOptions = state.users.mapNotNull { record ->
        record.userId?.let {
            SelectorOption(it, record.userName?.takeIf { name -> name.isNotBlank() } ?: it)
        }
    }

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
                enabled = state.settings.enabled,
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
                enabled = state.settings.enabled,
            )
            SwitchRow(
                title = "Ignore bots",
                subtitle = "Do not save role states for bot accounts",
                checked = state.settings.ignoreBots,
                onCheckedChange = { viewModel.toggleIgnoreBots() },
                enabled = state.settings.enabled,
            )
            SwitchRow(
                title = "Skip auto-assign roles",
                subtitle = "Do not restore roles the bot would grant automatically",
                checked = state.settings.skipAutoAssignRoles,
                onCheckedChange = viewModel::setSkipAutoAssign,
                enabled = state.settings.enabled,
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles excluded",
                label = "Never save these roles",
                multiple = true,
                enabled = state.settings.enabled,
                selection = state.settings.deniedRoleIds,
                onSelectionChange = viewModel::setDeniedRoles,
            )
            DiscordSelector(
                kind = SelectorKind.User,
                options = memberOptions,
                placeholder = "No members excluded",
                label = "Never save roles for these members",
                multiple = true,
                enabled = state.settings.enabled,
                selection = state.settings.deniedUserIds,
                onSelectionChange = viewModel::setDeniedUsers,
            )
        }

        SectionCard {
            SectionCardHeader("Manage user roles", Icons.Default.ManageAccounts)
            DiscordSelectorSingle(
                kind = SelectorKind.User,
                options = memberOptions,
                placeholder = "Choose a member",
                label = "Member",
                selectedId = manageMemberId,
                onSelect = { manageMemberId = it },
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles selected",
                label = "Roles",
                multiple = true,
                selection = manageRoleIds,
                onSelectionChange = { manageRoleIds = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        manageMemberId?.let { viewModel.addRolesToUser(it, manageRoleIds) }
                    },
                    enabled = manageMemberId != null && manageRoleIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Add roles") }
                OutlinedButton(
                    onClick = {
                        manageMemberId?.let { viewModel.removeRolesFromUser(it, manageRoleIds) }
                    },
                    enabled = manageMemberId != null && manageRoleIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Remove roles") }
            }
        }

        SectionCard {
            SectionCardHeader("Copy role state", Icons.Default.ContentCopy)
            if (savedStateOptions.isEmpty()) {
                EmptyState(
                    message = "No saved role states to copy from yet.",
                    icon = Icons.Default.ContentCopy,
                )
            } else {
                DiscordSelectorSingle(
                    kind = SelectorKind.User,
                    options = savedStateOptions,
                    placeholder = "Choose a source member",
                    label = "Source (has a saved state)",
                    selectedId = copySourceId,
                    onSelect = { copySourceId = it },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.User,
                    options = memberOptions,
                    placeholder = "Choose a target member",
                    label = "Target",
                    selectedId = copyTargetId,
                    onSelect = { copyTargetId = it },
                )
                Button(
                    onClick = { pendingCopy = true },
                    enabled = copySourceId != null && copyTargetId != null &&
                        copySourceId != copyTargetId,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Apply role state") }
            }
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

    if (pendingCopy) {
        val sourceId = copySourceId
        val targetId = copyTargetId
        val sourceName = savedStateOptions.firstOrNull { it.id == sourceId }?.name
            ?: sourceId.orEmpty()
        val targetName = memberOptions.firstOrNull { it.id == targetId }?.name
            ?: targetId.orEmpty()
        ConfirmDialog(
            title = "Apply role state?",
            message = "$sourceName's saved roles are granted to $targetName.",
            confirmLabel = "Apply",
            destructive = false,
            onConfirm = {
                if (sourceId != null && targetId != null) {
                    viewModel.copyRoleState(sourceId, targetId)
                }
            },
            onDismiss = { pendingCopy = false },
        )
    }
}
