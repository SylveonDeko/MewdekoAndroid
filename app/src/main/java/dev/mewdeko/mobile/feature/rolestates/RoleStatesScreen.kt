package dev.mewdeko.mobile.feature.rolestates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.MultiSelectDropdown
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * Saved member roles that survive a leave and rejoin.
 *
 * The page is a single [LazyColumn]: the settings cards come first, then one
 * compact row per saved member, composed only as it scrolls into view. A
 * guild snapshot can hold one saved state per member, so the list shows
 * [RoleStatesViewModel.PAGE_SIZE] rows at a time with a button to load more,
 * and the search narrows it. All parsing, filtering, and option building
 * happens in the view model, off the main thread.
 */
@Composable
fun RoleStatesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RoleStatesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClear by remember { mutableStateOf<RoleStateRow?>(null) }
    var editingRoles by remember { mutableStateOf<RoleStateRow?>(null) }
    var pendingSaveAll by remember { mutableStateOf(false) }
    var pendingCopy by remember { mutableStateOf(false) }

    var manageMemberId by remember { mutableStateOf<String?>(null) }
    var manageRoleIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var copySourceId by remember { mutableStateOf<String?>(null) }
    var copyTargetId by remember { mutableStateOf<String?>(null) }

    val visible = remember(state.filtered, state.visibleLimit) {
        state.filtered.take(state.visibleLimit)
    }
    val listState = rememberLazyListState()

    FeatureScaffold(
        title = "Role States",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        scrollable = false,
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = ListPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "overview", contentType = "card") {
                OverviewCard(
                    savedMembers = state.rows.size,
                    savedRoles = state.totalSavedRoles,
                    enabled = state.settings.enabled,
                    onSnapshot = { pendingSaveAll = true },
                )
            }

            item(key = "settings", contentType = "card") {
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
                    MultiSelectDropdown(
                        kind = SelectorKind.Role,
                        options = state.roleOptions,
                        selection = state.deniedRoleIds,
                        onSelectionChange = viewModel::setDeniedRoles,
                        label = "Never save these roles",
                        placeholder = "No roles excluded",
                        enabled = state.settings.enabled,
                    )
                    MultiSelectDropdown(
                        kind = SelectorKind.User,
                        options = state.memberOptions,
                        selection = state.deniedUserIds,
                        onSelectionChange = viewModel::setDeniedUsers,
                        label = "Never save roles for these members",
                        placeholder = "No members excluded",
                        enabled = state.settings.enabled,
                    )
                }
            }

            item(key = "manage", contentType = "card") {
                SectionCard {
                    SectionCardHeader("Manage user roles", Icons.Default.ManageAccounts)
                    DiscordSelectorSingle(
                        kind = SelectorKind.User,
                        options = state.memberOptions,
                        placeholder = "Choose a member",
                        label = "Member",
                        selectedId = manageMemberId,
                        onSelect = { manageMemberId = it },
                    )
                    MultiSelectDropdown(
                        kind = SelectorKind.Role,
                        options = state.roleOptions,
                        selection = manageRoleIds,
                        onSelectionChange = { manageRoleIds = it },
                        label = "Roles",
                        placeholder = "No roles selected",
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
            }

            item(key = "copy", contentType = "card") {
                SectionCard {
                    SectionCardHeader("Copy role state", Icons.Default.ContentCopy)
                    if (state.savedStateOptions.isEmpty()) {
                        EmptyState(
                            message = "No saved role states to copy from yet.",
                            icon = Icons.Default.ContentCopy,
                        )
                    } else {
                        DiscordSelectorSingle(
                            kind = SelectorKind.User,
                            options = state.savedStateOptions,
                            placeholder = "Choose a source member",
                            label = "Source (has a saved state)",
                            selectedId = copySourceId,
                            onSelect = { copySourceId = it },
                        )
                        DiscordSelectorSingle(
                            kind = SelectorKind.User,
                            options = state.memberOptions,
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
            }

            item(key = "saved-header", contentType = "card") {
                SectionCard {
                    SectionCardHeader(
                        title = "Saved members",
                        icon = Icons.Default.Sync,
                        trailing = {
                            if (state.rows.isNotEmpty()) {
                                Text(
                                    text = "${state.rows.size}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    SearchField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Search by name or ID",
                    )
                    if (state.filtered.isEmpty()) {
                        EmptyState(
                            message = if (state.appliedQuery.isBlank()) "No saved role states yet."
                            else "No members match \"${state.appliedQuery}\".",
                            icon = Icons.Default.Sync,
                        )
                    } else if (state.appliedQuery.isNotBlank()) {
                        Text(
                            text = "${state.filtered.size} of ${state.rows.size} members match.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(visible, key = { it.userId }, contentType = { "member" }) { row ->
                SavedMemberRow(
                    row = row,
                    onEdit = { editingRoles = row },
                    onClear = { pendingClear = row },
                )
            }

            if (state.filtered.size > visible.size) {
                item(key = "more", contentType = "footer") {
                    MoreFooter(
                        shown = visible.size,
                        total = state.filtered.size,
                        onShowMore = viewModel::showMore,
                    )
                }
            }
        }
    }

    editingRoles?.let { row ->
        var selection by remember(row.userId) { mutableStateOf(row.roleIds) }
        FormSheet(
            title = "Saved roles for ${row.name}",
            confirmLabel = "Save",
            confirmEnabled = selection != row.roleIds,
            onConfirm = {
                viewModel.setRoles(row.userId, selection)
                editingRoles = null
            },
            onDismiss = { editingRoles = null },
        ) {
            MultiSelectDropdown(
                kind = SelectorKind.Role,
                options = state.roleOptions,
                selection = selection,
                onSelectionChange = { selection = it },
                label = "Roles restored on rejoin",
                placeholder = "No roles",
            )
        }
    }

    pendingClear?.let { row ->
        ConfirmDialog(
            title = "Clear role state?",
            message = "Saved roles for ${row.name} are discarded.",
            confirmLabel = "Clear",
            onConfirm = { viewModel.clearUser(row.userId) },
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
        val sourceName = state.savedStateOptions.firstOrNull { it.id == sourceId }?.name
            ?: sourceId.orEmpty()
        val targetName = state.memberOptions.firstOrNull { it.id == targetId }?.name
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

/** Counts of saved members and roles, plus the snapshot action. */
@Composable
private fun OverviewCard(
    savedMembers: Int,
    savedRoles: Int,
    enabled: Boolean,
    onSnapshot: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("Overview", Icons.Default.Sync)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Saved members", "$savedMembers", Modifier.weight(1f))
            StatTile("Saved roles", "$savedRoles", Modifier.weight(1f))
            StatTile(
                label = "Status",
                value = if (enabled) "On" else "Off",
                tint = if (enabled) MaterialTheme.colorScheme.primary else null,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = onSnapshot,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Snapshot every member's roles now") }
    }
}

/**
 * One saved member: name, role count, and a text preview of the first few
 * roles. Tapping the row edits its roles; the overflow menu offers Edit and
 * Clear.
 */
@Composable
private fun SavedMemberRow(
    row: RoleStateRow,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    GuildCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (row.roleCount) {
                        0 -> "No roles saved"
                        1 -> "1 role"
                        else -> "${row.roleCount} roles"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (row.rolePreview.isNotEmpty()) {
                    Text(
                        text = row.rolePreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SavedMemberMenu(onEdit = onEdit, onClear = onClear)
        }
    }
}

/** The overflow menu on a saved member row. */
@Composable
private fun SavedMemberMenu(onEdit: () -> Unit, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Member actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit saved roles") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = { open = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Clear role state", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { open = false; onClear() },
            )
        }
    }
}

/** Tells the user the list is paged and offers the next page. */
@Composable
private fun MoreFooter(shown: Int, total: Int, onShowMore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Showing $shown of $total. Search to narrow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onShowMore) {
            Text("Load ${minOf(RoleStatesViewModel.PAGE_SIZE, total - shown)} more")
        }
    }
}

/**
 * The list's insets: the standard feature padding plus room at the bottom so
 * the Save settings button never covers the last row.
 */
private val ListPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp)
