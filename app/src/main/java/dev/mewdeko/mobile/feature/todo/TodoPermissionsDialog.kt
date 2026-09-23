package dev.mewdeko.mobile.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow

/**
 * Manage-permissions modal for one todo list: current grants with their role label, a member
 * picker to grant view/edit/manage access, and per-user revoke.
 */
@Composable
fun TodoPermissionsDialog(
    list: TodoListModel,
    permissions: List<TodoListPermissionModel>,
    members: List<GuildMember>,
    onGrant: (targetUserId: Snowflake, canView: Boolean, canEdit: Boolean, canManage: Boolean) -> Unit,
    onRevoke: (targetUserId: Snowflake) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftUserId by remember(list.id) { mutableStateOf<String?>(null) }
    var draftCanView by remember(list.id) { mutableStateOf(true) }
    var draftCanEdit by remember(list.id) { mutableStateOf(false) }
    var draftCanManage by remember(list.id) { mutableStateOf(false) }
    var pendingRevoke by remember(list.id) { mutableStateOf<TodoListPermissionModel?>(null) }

    val grantedIds = permissions.map { it.userId }.toSet()
    val grantableMembers = members.filterNot { it.isBot || it.id in grantedIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions: ${list.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DiscordSelectorSingle(
                    kind = SelectorKind.User,
                    options = grantableMembers.map {
                        SelectorOption(it.id, it.displayName.ifBlank { it.username }, subtitle = it.username)
                    },
                    placeholder = "Select a member",
                    label = "Add someone",
                    selectedId = draftUserId,
                    onSelect = { draftUserId = it },
                )
                SwitchRow(title = "Can view", checked = draftCanView, onCheckedChange = { draftCanView = it })
                SwitchRow(title = "Can edit", checked = draftCanEdit, onCheckedChange = { draftCanEdit = it })
                SwitchRow(title = "Can manage", checked = draftCanManage, onCheckedChange = { draftCanManage = it })
                Button(
                    onClick = {
                        val target = draftUserId ?: return@Button
                        onGrant(target, draftCanView, draftCanEdit, draftCanManage)
                        draftUserId = null
                        draftCanView = true
                        draftCanEdit = false
                        draftCanManage = false
                    },
                    enabled = draftUserId != null && draftCanView,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Grant access") }

                HorizontalDivider()
                Text("Current access", style = MaterialTheme.typography.titleSmall)

                if (permissions.isEmpty()) {
                    EmptyState("No one else has access yet.", icon = Icons.Default.Shield)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(permissions, key = { it.id }) { permission ->
                            val member = members.firstOrNull { it.id == permission.userId }
                            val name = member?.displayName?.takeIf { it.isNotBlank() }
                                ?: member?.username?.takeIf { it.isNotBlank() }
                                ?: permission.userId
                            ListItem(
                                leadingContent = {
                                    Avatar(
                                        url = member?.avatarUrl,
                                        contentDescription = name,
                                        size = 32,
                                    )
                                },
                                headlineContent = { Text(name) },
                                supportingContent = { Text(permission.roleLabel) },
                                trailingContent = {
                                    IconButton(onClick = { pendingRevoke = permission }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Revoke access",
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    pendingRevoke?.let { permission ->
        val member = members.firstOrNull { it.id == permission.userId }
        val name = member?.displayName?.takeIf { it.isNotBlank() }
            ?: member?.username?.takeIf { it.isNotBlank() }
            ?: permission.userId
        ConfirmDialog(
            title = "Revoke access?",
            message = "$name will lose access to \"${list.name}\".",
            confirmLabel = "Revoke",
            onConfirm = { onRevoke(permission.userId) },
            onDismiss = { pendingRevoke = null },
        )
    }
}
