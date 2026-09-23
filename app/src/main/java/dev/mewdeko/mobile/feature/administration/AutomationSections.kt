package dev.mewdeko.mobile.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.CommentBank
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow

/** The Discord permissions the dashboard offers for permission overrides and the permissions manager. */
private val DISCORD_PERMISSIONS = listOf(
    "Administrator", "ManageGuild", "ManageRoles", "ManageChannels", "ManageMessages",
    "KickMembers", "BanMembers", "ModerateMembers", "ViewChannel", "SendMessages",
    "EmbedLinks", "AttachFiles", "ReadMessageHistory", "MentionEveryone", "UseExternalEmojis",
    "Connect", "Speak", "MuteMembers", "DeafenMembers", "MoveMembers",
)

/** Flattens every command name out of the loaded module list. */
private fun AdministrationState.commandOptions(): List<SelectorOption> =
    modules.flatMap { it.commands }.map { SelectorOption(it.commandName, it.commandName) }
        .distinctBy { it.id }
        .sortedBy { it.name }

/** Game voice channel: pick a channel to set it, show the current one, and disable it. */
@Composable
fun GameVoiceChannelSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    SectionCard {
        SectionCardHeader("Game voice channel", Icons.Default.SportsEsports)
        Text(
            "Members joining this voice channel get the matching game role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoRow(
            "Current channel",
            state.gameVoiceChannelId?.let { id ->
                state.availableVoiceChannels.firstOrNull { it.id == id }?.name ?: id
            } ?: "Disabled",
        )
        var pick by remember { mutableStateOf<Snowflake?>(null) }
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.SportsEsports),
            options = state.availableVoiceChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a voice channel",
            selectedId = pick,
            onSelect = { pick = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { pick?.let(viewModel::toggleGameVoiceChannel) },
                enabled = pick != null,
            ) { Text("Set channel") }
            if (state.gameVoiceChannelId != null) {
                OutlinedButton(onClick = { viewModel.toggleGameVoiceChannel(state.gameVoiceChannelId!!) }) {
                    Text("Disable")
                }
            }
        }
    }
}

/** Delete message on command: global toggle plus per-channel enable/disable/inherit overrides. */
@Composable
fun DeleteMessageOnCommandSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    SectionCard {
        SectionCardHeader("Delete message on command", Icons.Default.CommentBank)
        SwitchRow(
            title = "Delete command messages",
            subtitle = "Removes the user's message once a command runs",
            checked = state.deleteMessageOnCommand.enabled,
            onCheckedChange = { viewModel.toggleDeleteMessageOnCommand() },
        )

        var channelId by remember { mutableStateOf<Snowflake?>(null) }
        var channelState by remember { mutableStateOf(DeleteMsgState.INHERIT) }
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a channel",
            selectedId = channelId,
            onSelect = { channelId = it },
            label = "Channel override",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.CommentBank),
            options = DeleteMsgState.entries.map { SelectorOption(it.raw.toString(), it.label) },
            placeholder = "Inherit",
            selectedId = channelState.raw.toString(),
            onSelect = { channelState = DeleteMsgState.from(it?.toIntOrNull() ?: 2) },
            label = "State",
        )
        Button(
            onClick = { channelId?.let { viewModel.setDeleteMessageOnCommandChannel(it, channelState) } },
            enabled = channelId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set override") }

        if (state.deleteMessageOnCommand.channels.isNotEmpty()) {
            state.deleteMessageOnCommand.channels.forEach { entry ->
                val name = state.availableChannels.firstOrNull { it.id == entry.channelId }?.name ?: entry.channelId
                InfoRow(name, DeleteMsgState.from(entry.state).label)
            }
        }
    }
}

/** Command cooldowns: list, add, remove. */
@Composable
fun CommandCooldownsSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var command by remember { mutableStateOf<String?>(null) }
    var seconds by remember { mutableStateOf(5) }

    SectionCard {
        SectionCardHeader("Command cooldowns", Icons.Default.Schedule)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Schedule),
            options = state.commandOptions(),
            placeholder = "Pick a command",
            selectedId = command,
            onSelect = { command = it },
            label = "Command",
        )
        MewdekoTextField(
            value = seconds.toString(),
            onValueChange = { seconds = it.toIntOrNull() ?: 0 },
            label = "Cooldown (seconds)",
            numeric = true,
            supportingText = "0 to 90000 seconds",
        )
        Button(
            onClick = { command?.let { viewModel.setCommandCooldown(it, seconds) } },
            enabled = command != null && seconds in 0..90000,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set cooldown") }

        if (state.commandCooldowns.isEmpty()) {
            EmptyState("No command cooldowns configured.", icon = Icons.Default.Schedule)
        }
        state.commandCooldowns.forEach { cooldown ->
            ListItem(
                headlineContent = { Text(cooldown.commandName ?: "Unknown") },
                supportingContent = { Text("${cooldown.seconds}s") },
                trailingContent = {
                    OutlinedButton(onClick = {
                        cooldown.commandName?.let(viewModel::removeCommandCooldown)
                    }) { Text("Remove") }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** Permission overrides: list, add, remove one, bulk delete, clear all. */
@Composable
fun PermissionOverridesSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var command by remember { mutableStateOf<String?>(null) }
    var permission by remember { mutableStateOf<String?>(null) }
    var selectedForDeletion by remember { mutableStateOf(setOf<String>()) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    SectionCard {
        SectionCardHeader(
            "Permission overrides",
            Icons.Default.Key,
            trailing = {
                if (state.permissionOverrides.isNotEmpty()) {
                    OutlinedButton(onClick = { confirmClearAll = true }) { Text("Clear all") }
                }
            },
        )
        Text(
            "Overrides the Discord permission required for a specific command.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Key),
            options = state.commandOptions(),
            placeholder = "Pick a command",
            selectedId = command,
            onSelect = { command = it },
            label = "Command",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Key),
            options = DISCORD_PERMISSIONS.map { SelectorOption(it, it) },
            placeholder = "Pick a permission",
            selectedId = permission,
            onSelect = { permission = it },
            label = "Required permission",
        )
        Button(
            onClick = {
                if (command != null && permission != null) {
                    viewModel.addPermissionOverride(command!!, permission!!)
                    command = null
                    permission = null
                }
            },
            enabled = command != null && permission != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add override") }

        if (selectedForDeletion.isNotEmpty()) {
            Button(
                onClick = { confirmDeleteSelected = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete selected (${selectedForDeletion.size})") }
        }

        if (state.permissionOverrides.isEmpty()) {
            EmptyState("No permission overrides configured.", icon = Icons.Default.Key)
        }
        state.permissionOverrides.forEach { override ->
            ListItem(
                headlineContent = { Text(override.command) },
                supportingContent = { Text("Requires ${override.permission}") },
                leadingContent = {
                    androidx.compose.material3.Checkbox(
                        checked = override.command in selectedForDeletion,
                        onCheckedChange = { checked ->
                            selectedForDeletion = if (checked) {
                                selectedForDeletion + override.command
                            } else {
                                selectedForDeletion - override.command
                            }
                        },
                    )
                },
                trailingContent = {
                    OutlinedButton(onClick = { viewModel.removePermissionOverride(override.command) }) {
                        Text("Remove")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear all overrides?",
            message = "Every command reverts to its default required permission.",
            confirmLabel = "Clear all",
            onConfirm = { viewModel.clearAllPermissionOverrides() },
            onDismiss = { confirmClearAll = false },
        )
    }
    if (confirmDeleteSelected) {
        ConfirmDialog(
            title = "Delete ${selectedForDeletion.size} overrides?",
            message = "This removes the selected permission overrides.",
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deletePermissionOverrides(selectedForDeletion.toList())
                selectedForDeletion = emptySet()
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
}

/** The permissions manager: ordered allow/deny rules, add, move, remove, reset, verbose, and role. */
@Composable
fun PermissionsManagerSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    val rules = state.permissions.permissions.sortedBy { it.index }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    SectionCard {
        SectionCardHeader(
            "Permissions manager",
            Icons.Default.ShieldMoon,
            trailing = {
                if (rules.size > 1) {
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Reset all") }
                }
            },
        )
        Text(
            "Checked top to bottom; the first matching rule wins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SwitchRow(
            title = "Verbose",
            subtitle = "Explain to the user when a rule blocks a command",
            checked = state.permissions.verbose,
            onCheckedChange = { viewModel.togglePermissionVerbose() },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
            placeholder = "No permission role",
            selectedId = state.permissions.permRole,
            onSelect = viewModel::setPermissionRole,
            label = "Permission role (can edit rules via commands)",
        )

        OutlinedButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add rule") }
        if (adding) {
            AddPermissionRuleForm(state = state, viewModel = viewModel, onDone = { adding = false })
        }

        rules.forEachIndexed { position, rule ->
            val scope = describePrimaryTarget(rule, state)
            val target = when (rule.secondaryTarget) {
                0 -> "module ${rule.secondaryTargetName}"
                1 -> "command ${rule.secondaryTargetName}"
                else -> "all modules"
            }
            ListItem(
                headlineContent = { Text("${if (rule.state) "Allow" else "Deny"} $target") },
                supportingContent = { Text("for $scope") },
                trailingContent = {
                    if (rule.index == 0) {
                        Text("Default", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Row {
                            OutlinedButton(
                                onClick = { viewModel.movePermissionRule(rule.index, rule.index - 1) },
                                enabled = rule.index > 1,
                            ) { Text("Up") }
                            OutlinedButton(
                                onClick = { viewModel.movePermissionRule(rule.index, rule.index + 1) },
                                enabled = position < rules.lastIndex,
                            ) { Text("Down") }
                            OutlinedButton(onClick = { confirmRemove = rule.index }) { Text("Remove") }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset all permission rules?",
            message = "Removes every custom rule and restores the default allow-all rule.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.resetPermissionRules() },
            onDismiss = { confirmReset = false },
        )
    }
    confirmRemove?.let { index ->
        ConfirmDialog(
            title = "Remove rule #$index?",
            message = "This permission rule will no longer apply.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removePermissionRule(index) },
            onDismiss = { confirmRemove = null },
        )
    }
}

private fun describePrimaryTarget(rule: PermissionRuleEntry, state: AdministrationState): String {
    val id = rule.primaryTargetId
    return when (rule.primaryTarget) {
        0 -> "user $id"
        1 -> "#" + (state.availableChannels.firstOrNull { it.id == id }?.name ?: id)
        2 -> "@" + (state.availableRoles.firstOrNull { it.id == id }?.name ?: id)
        4 -> "category " + (state.availableCategories.firstOrNull { it.id == id }?.name ?: id)
        else -> "everyone"
    }
}

@Composable
private fun AddPermissionRuleForm(
    state: AdministrationState,
    viewModel: AdministrationViewModel,
    onDone: () -> Unit,
) {
    var allow by remember { mutableStateOf(false) }
    var secondaryTarget by remember { mutableStateOf(2) }
    var secondaryName by remember { mutableStateOf<String?>(null) }
    var primaryTarget by remember { mutableStateOf(3) }
    var primaryTargetId by remember { mutableStateOf<String?>(null) }

    val primaryOptions = listOf(
        3 to "Whole server", 2 to "A role", 1 to "A channel", 4 to "A category", 0 to "A specific user",
    )
    val secondaryOptions = listOf(2 to "All modules", 0 to "One module", 1 to "One command")

    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
        options = listOf(SelectorOption("allow", "Allow"), SelectorOption("deny", "Deny")),
        placeholder = "Deny",
        selectedId = if (allow) "allow" else "deny",
        onSelect = { allow = it == "allow" },
        label = "Action",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
        options = secondaryOptions.map { (id, name) -> SelectorOption(id.toString(), name) },
        placeholder = "All modules",
        selectedId = secondaryTarget.toString(),
        onSelect = { secondaryTarget = it?.toIntOrNull() ?: 2; secondaryName = null },
        label = "What",
    )
    when (secondaryTarget) {
        0 -> DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
            options = state.modules.map { SelectorOption(it.name, it.name) },
            placeholder = "Pick a module",
            selectedId = secondaryName,
            onSelect = { secondaryName = it },
            label = "Module",
        )

        1 -> DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
            options = state.commandOptions(),
            placeholder = "Pick a command",
            selectedId = secondaryName,
            onSelect = { secondaryName = it },
            label = "Command",
        )
    }
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
        options = primaryOptions.map { (id, name) -> SelectorOption(id.toString(), name) },
        placeholder = "Whole server",
        selectedId = primaryTarget.toString(),
        onSelect = { primaryTarget = it?.toIntOrNull() ?: 3; primaryTargetId = null },
        label = "For",
    )
    when (primaryTarget) {
        2 -> DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a role",
            selectedId = primaryTargetId,
            onSelect = { primaryTargetId = it },
            label = "Role",
        )

        1 -> DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a channel",
            selectedId = primaryTargetId,
            onSelect = { primaryTargetId = it },
            label = "Channel",
        )

        4 -> DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.ShieldMoon),
            options = state.availableCategories.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a category",
            selectedId = primaryTargetId,
            onSelect = { primaryTargetId = it },
            label = "Category",
        )

        0 -> MewdekoTextField(
            value = primaryTargetId.orEmpty(),
            onValueChange = { primaryTargetId = it },
            label = "User ID",
            numeric = true,
        )
    }

    val secondaryValid = secondaryTarget == 2 || !secondaryName.isNullOrBlank()
    val primaryValid = primaryTarget == 3 || !primaryTargetId.isNullOrBlank()
    Button(
        onClick = {
            viewModel.addPermissionRule(
                primaryTarget = primaryTarget,
                primaryTargetId = if (primaryTarget == 3) null else primaryTargetId,
                secondaryTarget = secondaryTarget,
                secondaryTargetName = if (secondaryTarget == 2) "*" else secondaryName.orEmpty().lowercase(),
                state = allow,
            )
            onDone()
        },
        enabled = secondaryValid && primaryValid,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add rule") }
}

/** Statistics & privacy: opt-out toggle and delete all stats data. */
@Composable
fun StatsPrivacySection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }

    SectionCard {
        SectionCardHeader("Statistics & privacy", Icons.Default.InsertChart)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                state.statsOptOut?.let { if (it) "Opted out of stats" else "Collecting stats" }
                    ?: "Statistics collection",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { viewModel.toggleStatsOptOut() }) { Text("Toggle opt-out") }
        }
        OutlinedButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.InsertChart, contentDescription = null)
            Text("Delete all statistics data")
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete all statistics?",
            message = "This permanently removes every collected statistic for this server. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteStatsData() },
            onDismiss = { confirmDelete = false },
        )
    }
}
