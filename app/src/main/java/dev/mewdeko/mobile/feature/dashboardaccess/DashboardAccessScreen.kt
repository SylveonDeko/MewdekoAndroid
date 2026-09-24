package dev.mewdeko.mobile.feature.dashboardaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate

/**
 * Restricted dashboard access: owner-only delegation and access managers,
 * plus per-feature View or Manage grants for users and roles.
 */
@Composable
fun DashboardaccessScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: DashboardAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingManager by remember { mutableStateOf<DashboardAccessManager?>(null) }
    var pendingGrant by remember { mutableStateOf<DashboardAccessGrant?>(null) }

    FeatureScaffold(
        title = "Dashboard Access",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.canManageAccess && state.page != AccessPage.DELEGATION) {
                NewItemFab(label = "New grant", onClick = viewModel::startNewGrant)
            }
        },
    ) {
        if (!state.canManageAccess) {
            LockoutCard()
        } else {
            AccessPages(
                state = state,
                viewModel = viewModel,
                onRemoveGrant = { pendingGrant = it },
                onRemoveManager = { pendingManager = it },
            )
        }
    }

    if (state.canManageAccess && state.page == AccessPage.EDITOR) {
        val draft = state.draft
        FullScreenEditor(
            title = if (draft.isEditing) "Edit grant" else "New grant",
            onClose = viewModel::cancelEdit,
            confirmLabel = when {
                state.isSavingGrant -> "Saving..."
                draft.isEditing -> "Save"
                else -> "Grant"
            },
            confirmEnabled = !state.isSavingGrant && !draft.targetId.isNullOrBlank(),
            onConfirm = viewModel::saveGrant,
            hasUnsavedChanges = !draft.isEditing &&
                (!draft.targetId.isNullOrBlank() || draft.grantedSections.isNotEmpty()),
        ) {
            GrantEditorPage(
                state = state,
                onTargetType = viewModel::setGrantTargetType,
                onTarget = viewModel::setGrantTarget,
                onGroupLevel = viewModel::setGroupLevel,
            )
        }
    }

    pendingManager?.let { manager ->
        ConfirmDialog(
            title = "Remove manager?",
            message = "Remove ${state.targetName(manager.type, manager.targetId)} from access managers? " +
                "They will no longer be able to manage dashboard access for this server.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeManager(manager) },
            onDismiss = { pendingManager = null },
        )
    }

    pendingGrant?.let { grant ->
        ConfirmDialog(
            title = "Remove access grant?",
            message = "Remove all restricted dashboard access for " +
                "${state.targetName(grant.type, grant.targetId)}?",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeGrant(grant) },
            onDismiss = { pendingGrant = null },
        )
    }
}

/** The page switcher and the selected page, for users who may manage access. */
@Composable
private fun AccessPages(
    state: DashboardAccessState,
    viewModel: DashboardAccessViewModel,
    onRemoveGrant: (DashboardAccessGrant) -> Unit,
    onRemoveManager: (DashboardAccessManager) -> Unit,
) {
    if (state.isGuildOwner) {
        SectionTabs(
            tabs = listOf(
                SectionTab(AccessPage.GRANTS.id, "Grants", Icons.Default.Key),
                SectionTab(AccessPage.DELEGATION.id, "Managers", Icons.Default.SupervisorAccount),
            ),
            selectedId = if (state.page == AccessPage.DELEGATION) AccessPage.DELEGATION.id else AccessPage.GRANTS.id,
            onSelect = { viewModel.selectPage(AccessPage.from(it)) },
        )
    }

    when (state.page) {
        AccessPage.GRANTS, AccessPage.EDITOR -> GrantsPage(
            state = state,
            onEdit = viewModel::editGrant,
            onRemove = onRemoveGrant,
            onRetry = { viewModel.load(refreshing = true) },
            onNew = viewModel::startNewGrant,
        )

        AccessPage.DELEGATION -> if (state.isGuildOwner) {
            DelegationPage(
                state = state,
                onToggle = viewModel::toggleAdminsCanManage,
                onTargetType = viewModel::setManagerTargetType,
                onTarget = viewModel::setManagerTarget,
                onAdd = viewModel::addManager,
                onRemove = onRemoveManager,
                onRetry = { viewModel.load(refreshing = true) },
            )
        }
    }
}

/** Shown instead of the editor when the signed-in user cannot manage access. */
@Composable
private fun LockoutCard() {
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = "You can't manage dashboard access here",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Only the server owner and users or roles appointed as access managers can " +
                    "grant or revoke restricted dashboard access for this server.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The list of existing grants with edit and remove actions. */
@Composable
private fun GrantsPage(
    state: DashboardAccessState,
    onEdit: (DashboardAccessGrant) -> Unit,
    onRemove: (DashboardAccessGrant) -> Unit,
    onRetry: () -> Unit,
    onNew: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("Access Grants (${state.grants.size})", Icons.Default.Key)
        Text(
            text = "Users and roles with View or Manage access to specific dashboard sections, " +
                "without full server admin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.grantsError != null && state.grants.isEmpty() ->
                ErrorState(message = state.grantsError, onRetry = onRetry)

            state.grants.isEmpty() ->
                EmptyState(
                    message = "No restricted access grants yet.",
                    icon = Icons.Default.Key,
                    actionLabel = "New grant",
                    onAction = onNew,
                )

            else -> state.grants.forEach { grant ->
                val count = grant.sections.size
                ListItem(
                    headlineContent = {
                        Text(
                            text = state.targetName(grant.type, grant.targetId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text("$count section${if (count == 1) "" else "s"}")
                    },
                    leadingContent = { TargetIcon(grant.type) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEdit(grant) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit grant")
                            }
                            IconButton(onClick = { onRemove(grant) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove grant",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/**
 * The body of the grant editor: target selection and per-feature levels
 * grouped by category. It is long, so it lives in a [FullScreenEditor] whose
 * top bar holds Save and Close.
 */
@Composable
private fun GrantEditorPage(
    state: DashboardAccessState,
    onTargetType: (AccessTargetType) -> Unit,
    onTarget: (String?) -> Unit,
    onGroupLevel: (DashboardAccessGroup, AccessLevel) -> Unit,
) {
    val draft = state.draft
    val selectedCount = draft.grantedSections.size

    SectionCard {
        SectionCardHeader(
            title = if (draft.isEditing) "Edit Access Grant" else "Grant Restricted Access",
            icon = Icons.Default.VerifiedUser,
        )
        Text(
            text = "Give a user or role View or Manage access to specific dashboard sections, " +
                "without giving them full server admin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TargetPicker(
            state = state,
            type = draft.targetType,
            targetId = draft.targetId,
            enabled = !draft.isEditing,
            onTargetType = onTargetType,
            onTarget = onTarget,
        )
        if (draft.isEditing) {
            Text(
                text = "The target cannot be changed while editing. Remove the grant to reassign it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TagChip("$selectedCount section${if (selectedCount == 1) "" else "s"} selected")
    }

    DashboardAccessSections.grouped.forEach { (category, groups) ->
        SectionCard {
            SectionCardHeader(category.label, category.icon())
            groups.forEach { group ->
                GroupLevelRow(
                    group = group,
                    level = draft.groupLevel(group.sections),
                    onLevel = { onGroupLevel(group, it) },
                )
            }
        }
    }
}

/** One dashboard feature with its None, View, and Manage choice. */
@Composable
private fun GroupLevelRow(
    group: DashboardAccessGroup,
    level: AccessLevel,
    onLevel: (AccessLevel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                group.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(group.label, style = MaterialTheme.typography.bodyMedium)
                if (group.sections.size > 1) {
                    Text(
                        text = "Includes ${group.sections.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        EnumPicker(
            label = "Access",
            options = AccessLevelOptions,
            selected = level,
            onSelect = onLevel,
            showDescription = false,
        )
    }
}

/** The None, View, and Manage choices, with what each lets the target do. */
private val AccessLevelOptions = listOf(
    EnumOption(AccessLevel.NONE, AccessLevel.NONE.label, "Hidden from them on the dashboard."),
    EnumOption(AccessLevel.VIEW, AccessLevel.VIEW.label, "They can see these settings but not change them."),
    EnumOption(AccessLevel.MANAGE, AccessLevel.MANAGE.label, "They can see and change these settings."),
)

/** Owner-only delegation toggle and access-manager list. */
@Composable
private fun DelegationPage(
    state: DashboardAccessState,
    onToggle: () -> Unit,
    onTargetType: (AccessTargetType) -> Unit,
    onTarget: (String?) -> Unit,
    onAdd: () -> Unit,
    onRemove: (DashboardAccessManager) -> Unit,
    onRetry: () -> Unit,
) {
    val settings = state.settings

    SectionCard {
        SectionCardHeader("Delegation Settings", Icons.Default.Security)
        Text(
            text = "Owner only. Controls who besides you may grant or revoke dashboard access " +
                "for other people.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow(
            title = "Allow Administrators and Manage Guild members to manage dashboard access",
            checked = settings?.adminsCanManageAccess == true,
            onCheckedChange = { onToggle() },
            enabled = !state.isUpdatingToggle,
        )
    }

    SectionCard {
        SectionCardHeader("Access Managers (${state.managers.size})", Icons.Default.SupervisorAccount)
        Text(
            text = "Appointed users and roles can grant or revoke restricted access, " +
                "whatever their Discord permissions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TargetPicker(
            state = state,
            type = state.managerTargetType,
            targetId = state.managerTargetId,
            enabled = !state.isAddingManager,
            onTargetType = onTargetType,
            onTarget = onTarget,
        )
        Button(
            onClick = onAdd,
            enabled = !state.isAddingManager && !state.managerTargetId.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isAddingManager) "Adding..." else "Add Manager")
        }

        when {
            state.managersError != null && state.managers.isEmpty() ->
                ErrorState(message = state.managersError, onRetry = onRetry)

            state.managers.isEmpty() ->
                EmptyState("No appointed managers yet.", icon = Icons.Default.SupervisorAccount)

            else -> state.managers.forEach { manager ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = state.targetName(manager.type, manager.targetId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = manager.dateAdded?.let { added ->
                        { Text("Added ${added.shortDate()}") }
                    },
                    leadingContent = { TargetIcon(manager.type) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(manager) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove manager",
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

/** A User or Role kind selector followed by the matching member or role selector. */
@Composable
private fun TargetPicker(
    state: DashboardAccessState,
    type: AccessTargetType,
    targetId: String?,
    enabled: Boolean,
    onTargetType: (AccessTargetType) -> Unit,
    onTarget: (String?) -> Unit,
) {
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Category),
        options = AccessTargetType.entries.map { SelectorOption(it.value.toString(), it.label) },
        placeholder = "Target type",
        label = "Target type",
        selectedId = type.value.toString(),
        onSelect = { id -> id?.toIntOrNull()?.let { onTargetType(AccessTargetType.from(it)) } },
        enabled = enabled,
    )

    val baseOptions = when (type) {
        AccessTargetType.ROLE -> state.roles.map { SelectorOption(it.id, it.name) }
        AccessTargetType.USER -> state.members.map { member ->
            SelectorOption(
                id = member.id,
                name = member.displayName.ifBlank { member.username },
                subtitle = member.username.takeIf { it.isNotBlank() && it != member.displayName },
            )
        }
    }
    val options = if (targetId != null && baseOptions.none { it.id == targetId }) {
        baseOptions + SelectorOption(targetId, state.targetName(type, targetId))
    } else {
        baseOptions
    }

    DiscordSelectorSingle(
        kind = if (type == AccessTargetType.ROLE) SelectorKind.Role else SelectorKind.User,
        options = options,
        placeholder = if (type == AccessTargetType.ROLE) "Select a role" else "Select a user",
        label = if (type == AccessTargetType.ROLE) "Role" else "User",
        selectedId = targetId,
        onSelect = onTarget,
        enabled = enabled,
    )
}

/** The leading glyph distinguishing user targets from role targets. */
@Composable
private fun TargetIcon(type: AccessTargetType) {
    Icon(
        imageVector = if (type == AccessTargetType.ROLE) Icons.Default.AlternateEmail else Icons.Default.Person,
        contentDescription = type.label,
        tint = MaterialTheme.colorScheme.primary,
    )
}

/** The header glyph for a feature category card. */
private fun AccessCategory.icon(): ImageVector = when (this) {
    AccessCategory.COMMUNITY -> Icons.Default.Groups
    AccessCategory.ENTERTAINMENT -> Icons.Default.SportsEsports
    AccessCategory.ACTIONS -> Icons.Default.Bolt
    AccessCategory.SECURITY -> Icons.Default.Shield
    AccessCategory.ANALYTICS -> Icons.Default.QueryStats
    AccessCategory.SETTINGS -> Icons.Default.Tune
}
