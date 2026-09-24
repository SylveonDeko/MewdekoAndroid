package dev.mewdeko.mobile.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.ShellCallout
import dev.mewdeko.mobile.core.ui.ShellTone
import dev.mewdeko.mobile.core.ui.SwitchRow

/** Self-assignable roles: grouped list with per-role level requirement, exclusive/auto-delete toggles, and add flow. */
@Composable
fun SelfAssignableRolesSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    val payload = state.selfAssignable
    var adding by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<Int?>(null) }
    var groupNameDraft by remember { mutableStateOf("") }
    var editingLevelRole by remember { mutableStateOf<Snowflake?>(null) }
    var levelDraft by remember { mutableStateOf(0) }

    val grouped = remember(payload) {
        val ids = (payload.groups.keys + payload.roles.map { it.group }).toSortedSet()
        ids.map { id -> id to payload.roles.filter { it.group == id } }
            .filter { (id, roles) -> roles.isNotEmpty() || payload.groups.containsKey(id) }
    }

    SectionCard {
        SectionCardHeader(
            title = "Self-assignable roles",
            icon = Icons.Default.PanTool,
            trailing = {
                OutlinedButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add") }
            },
        )
        Text(
            "Members grant themselves these with /iam.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchRow(
            title = "Exclusive",
            subtitle = "One self-assignable role per group",
            checked = payload.exclusive,
            onCheckedChange = { viewModel.toggleSelfAssignableExclusive() },
        )
        SwitchRow(
            title = "Auto-delete confirmation replies",
            subtitle = "Removes the bot's iam/iamnot reply a few seconds after it posts",
            checked = state.autoDeleteSelfAssign == true,
            onCheckedChange = { viewModel.toggleSelfAssignableAutoDelete() },
        )

        if (adding) {
            var newRole by remember { mutableStateOf<Snowflake?>(null) }
            var newGroup by remember { mutableStateOf(0) }
            var newLevel by remember { mutableStateOf(0) }

            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a role",
                selectedId = newRole,
                onSelect = { newRole = it },
                label = "Role",
            )
            MewdekoTextField(
                value = newGroup.toString(),
                onValueChange = { newGroup = it.toIntOrNull() ?: 0 },
                label = "Group",
                numeric = true,
            )
            MewdekoTextField(
                value = newLevel.toString(),
                onValueChange = { newLevel = it.toIntOrNull() ?: 0 },
                label = "Level requirement",
                numeric = true,
                supportingText = "0 for no requirement",
            )
            Button(
                onClick = {
                    newRole?.let { viewModel.addSelfAssignableRole(it, newGroup, newLevel) }
                    adding = false
                },
                enabled = newRole != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add role") }
        }

        if (grouped.isEmpty()) {
            EmptyState("No self-assignable roles configured.", icon = Icons.Default.PanTool)
        }

        grouped.forEach { (groupId, roles) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (editingGroup == groupId) {
                    MewdekoTextField(
                        value = groupNameDraft,
                        onValueChange = { groupNameDraft = it },
                        label = "Group name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        if (groupId == 0) "Ungrouped" else {
                            "Group $groupId" + (payload.groups[groupId]?.let { ": $it" } ?: "")
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (editingGroup == groupId) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        viewModel.renameSelfAssignableGroup(groupId, groupNameDraft)
                        editingGroup = null
                    }) { Text("Save name") }
                    OutlinedButton(onClick = { editingGroup = null }) { Text("Cancel") }
                }
            } else if (groupId != 0) {
                OutlinedButton(onClick = {
                    editingGroup = groupId
                    groupNameDraft = payload.groups[groupId].orEmpty()
                }) { Text("Rename group") }
            }

            roles.forEach { role ->
                ListItem(
                    headlineContent = { Text("@${role.roleName}") },
                    supportingContent = {
                        if (editingLevelRole == role.roleId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MewdekoTextField(
                                    value = levelDraft.toString(),
                                    onValueChange = { levelDraft = it.toIntOrNull() ?: 0 },
                                    label = "Level",
                                    numeric = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Button(onClick = {
                                    viewModel.setSelfAssignableRoleLevel(role.roleId, levelDraft)
                                    editingLevelRole = null
                                }) { Text("Save") }
                            }
                        } else {
                            Text(
                                if (role.levelRequirement > 0) "Level ${role.levelRequirement}+" else "No level requirement",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(onClick = {
                                editingLevelRole = role.roleId
                                levelDraft = role.levelRequirement
                            }) { Text("Level") }
                            OutlinedButton(onClick = { viewModel.removeSelfAssignableRole(role.roleId) }) {
                                Text("Remove")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/** Voice channel roles: list with remove-with-confirm, plus an add flow. */
@Composable
fun VoiceChannelRolesSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var adding by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<VoiceChannelRoleEntry?>(null) }

    SectionCard {
        SectionCardHeader(
            title = "Voice channel roles",
            icon = Icons.Default.Groups,
            trailing = {
                OutlinedButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add") }
            },
        )
        Text(
            "Assigns a role automatically while a member sits in a voice channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (adding) {
            var channelId by remember { mutableStateOf<Snowflake?>(null) }
            var roleId by remember { mutableStateOf<Snowflake?>(null) }
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Tag),
                options = state.availableVoiceChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a voice channel",
                selectedId = channelId,
                onSelect = { channelId = it },
                label = "Voice channel",
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a role",
                selectedId = roleId,
                onSelect = { roleId = it },
                label = "Role to assign",
            )
            Button(
                onClick = {
                    if (channelId != null && roleId != null) {
                        viewModel.addVoiceChannelRole(channelId!!, roleId!!)
                        adding = false
                    }
                },
                enabled = channelId != null && roleId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add mapping") }
        }

        if (state.voiceChannelRoles.isEmpty()) {
            EmptyState("No voice channel roles configured.", icon = Icons.Default.Groups)
        }
        state.voiceChannelRoles.forEach { entry ->
            ListItem(
                headlineContent = { Text(entry.channelName) },
                supportingContent = { Text("@${entry.roleName}") },
                trailingContent = {
                    OutlinedButton(onClick = { confirmRemove = entry }) { Text("Remove") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    confirmRemove?.let { entry ->
        ConfirmDialog(
            title = "Remove voice channel role?",
            message = "Members in ${entry.channelName} will no longer receive @${entry.roleName}.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeVoiceChannelRole(entry.channelId) },
            onDismiss = { confirmRemove = null },
        )
    }
}

/** Reaction roles: list of setups with their emote-to-role pairs, plus an add flow and a guild emoji picker. */
@Composable
fun ReactionRolesSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var adding by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Int?>(null) }

    SectionCard {
        ShellCallout(
            text = "Role Menus does this with a dropdown or buttons. Open Role Menus and use Move older " +
                "setups to bring these over.",
            tone = ShellTone.Brand,
        )
        SectionCardHeader(
            title = "Reaction roles",
            icon = Icons.Default.AlternateEmail,
            trailing = {
                OutlinedButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add") }
            },
        )
        Text(
            "Reacting to a message grants the matching role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (adding) {
            AddReactionRoleForm(state = state, viewModel = viewModel, onDone = { adding = false })
        }

        if (state.reactionRoles.isEmpty()) {
            EmptyState("No reaction roles configured.", icon = Icons.Default.AlternateEmail)
        }
        state.reactionRoles.forEach { setup ->
            ListItem(
                headlineContent = {
                    val channelName = state.availableChannels.firstOrNull { it.id == setup.channelId }?.name
                    Text(channelName?.let { "#$it · message ${setup.messageId}" } ?: "Message ${setup.messageId}")
                },
                supportingContent = {
                    Text(
                        setup.reactionRoles.joinToString { pair ->
                            val roleName = state.availableRoles.firstOrNull { it.id == pair.roleId }?.name
                            "${pair.emoteName} -> @${roleName ?: pair.roleId}"
                        } + if (setup.exclusive) " (exclusive)" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingContent = {
                    OutlinedButton(onClick = { confirmRemove = setup.index }) { Text("Remove") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    confirmRemove?.let { index ->
        ConfirmDialog(
            title = "Remove reaction roles?",
            message = "This removes the whole setup for that message.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeReactionRoleSetup(index) },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun AddReactionRoleForm(
    state: AdministrationState,
    viewModel: AdministrationViewModel,
    onDone: () -> Unit,
) {
    var messageId by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf<Snowflake?>(null) }
    var exclusive by remember { mutableStateOf(false) }
    var pairs by remember { mutableStateOf(listOf<Pair<String, Snowflake?>>("" to null)) }
    var guildEmojis by remember { mutableStateOf<List<EmojiInfo>>(emptyList()) }

    LaunchedEffect(Unit) { guildEmojis = viewModel.loadGuildEmojis() }

    MewdekoTextField(
        value = messageId,
        onValueChange = { messageId = it },
        label = "Message ID",
        numeric = true,
        supportingText = "Right-click a message and Copy ID (needs Developer Mode)",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Channel,
        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
        placeholder = "Pick a channel",
        selectedId = channelId,
        onSelect = { channelId = it },
        label = "Channel",
    )
    SwitchRow(
        title = "Exclusive",
        subtitle = "Members can only hold one role from this message",
        checked = exclusive,
        onCheckedChange = { exclusive = it },
    )

    Text("Emoji to role pairs", style = MaterialTheme.typography.titleSmall)
    pairs.forEachIndexed { index, (emote, roleId) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val emojiOptions = guildEmojis.map { SelectorOption(it.toEmoteName(), it.name) }
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.AlternateEmail),
                options = emojiOptions,
                placeholder = "Emoji",
                selectedId = emote.takeIf { it.isNotBlank() },
                onSelect = { selectedEmote ->
                    pairs = pairs.toMutableList().also { it[index] = (selectedEmote ?: "") to roleId }
                },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Role",
                selectedId = roleId,
                onSelect = { newRole ->
                    pairs = pairs.toMutableList().also { it[index] = emote to newRole }
                },
            )
        }
        MewdekoTextField(
            value = emote,
            onValueChange = { text -> pairs = pairs.toMutableList().also { it[index] = text to roleId } },
            label = "...or paste a unicode emoji",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pairs = pairs + ("" to null) }) { Text("Add pair") }
        if (pairs.size > 1) {
            OutlinedButton(onClick = { pairs = pairs.dropLast(1) }) { Text("Remove last") }
        }
    }

    val validPairs = pairs.filter { (emote, role) -> emote.isNotBlank() && role != null }
    Button(
        onClick = {
            val cId = channelId
            val mId = messageId.toLongOrNull()
            if (cId != null && mId != null && validPairs.isNotEmpty()) {
                viewModel.addReactionRoleSetup(
                    messageId = mId.toString(),
                    channelId = cId,
                    exclusive = exclusive,
                    pairs = validPairs.map { (emote, role) -> emote to (role as Snowflake) },
                )
                onDone()
            }
        },
        enabled = channelId != null && messageId.toLongOrNull() != null && validPairs.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save reaction roles") }
}
