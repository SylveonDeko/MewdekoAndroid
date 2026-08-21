package dev.mewdeko.mobile.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** The Material icon standing in for each section. */
private val AdminSection.icon: ImageVector
    get() = when (this) {
        AdminSection.OVERVIEW -> Icons.Default.Insights
        AdminSection.PROTECTION -> Icons.Default.Shield
        AdminSection.ROLES -> Icons.Default.Groups
        AdminSection.AUTOMATION -> Icons.Default.Settings
        AdminSection.ADVANCED -> Icons.Default.Bolt
    }

/** Server administration: protection, role automation, and bulk moderation. */
@Composable
fun AdministrationScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: AdministrationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<ProtectionEditor?>(null) }

    FeatureScaffold(
        title = "Administration",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(
            tabs = AdminSection.entries.map { SectionTab(it.id, it.label, it.icon) },
            selectedId = state.section.id,
            onSelect = { id ->
                AdminSection.entries.firstOrNull { it.id == id }?.let(viewModel::setSection)
            },
        )

        when (state.section) {
            AdminSection.OVERVIEW -> OverviewSection(state)
            AdminSection.PROTECTION -> ProtectionSection(state, onEdit = { editor = it })
            AdminSection.ROLES -> RolesSection(
                state = state,
                onStaffRole = viewModel::setStaffRole,
                onMemberRole = viewModel::setMemberRole,
                onToggleAutoAssignNormal = viewModel::toggleAutoAssignNormal,
                onToggleAutoAssignBot = viewModel::toggleAutoAssignBot,
                onToggleSelfAssignable = viewModel::toggleSelfAssignable,
                onToggleAutoBan = viewModel::toggleAutoBanRole,
            )

            AdminSection.AUTOMATION -> AutomationSection(
                state = state,
                onTimezone = viewModel::setTimezone,
                onToggleGameVoice = viewModel::toggleGameVoiceChannel,
            )

            AdminSection.ADVANCED -> AdvancedSection(
                state = state,
                onSaveBanMessage = viewModel::saveBanMessage,
                onMassBan = viewModel::massBan,
                onPrune = viewModel::prune,
            )
        }
    }

    editor?.let { which ->
        ProtectionEditSheet(
            editor = which,
            protection = state.protection,
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            onDismiss = { editor = null },
            onSave = { config ->
                editor = null
                when (which) {
                    ProtectionEditor.RAID -> viewModel.saveAntiRaid(
                        enabled = config.enabled,
                        userThreshold = config.userThreshold,
                        seconds = config.seconds,
                        action = config.action.raw,
                        punishDuration = config.actionDuration,
                    )

                    ProtectionEditor.SPAM -> viewModel.saveAntiSpam(
                        enabled = config.enabled,
                        messageThreshold = config.messageThreshold,
                        action = config.action.raw,
                        muteTime = config.muteTime,
                        roleId = config.roleArgument,
                    )

                    ProtectionEditor.ALT -> viewModel.saveAntiAlt(
                        enabled = config.enabled,
                        minAgeMinutes = config.minAgeMinutes,
                        action = config.action.raw,
                        actionDurationMinutes = config.actionDuration,
                        roleId = config.roleArgument,
                    )

                    ProtectionEditor.MASS_MENTION -> viewModel.saveAntiMassMention(
                        enabled = config.enabled,
                        mentionThreshold = config.mentionThreshold,
                        timeWindowSeconds = config.timeWindowSeconds,
                        maxMentionsInTimeWindow = config.maxMentionsInWindow,
                        ignoreBots = config.ignoreBots,
                        action = config.action.raw,
                        muteTime = config.muteTime,
                        roleId = config.roleArgument,
                    )
                }
            },
        )
    }
}

@Composable
private fun OverviewSection(state: AdministrationState) {
    SectionCard {
        SectionCardHeader("Protection", Icons.Default.Shield)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Active", "${state.activeProtections}/5", Modifier.weight(1f))
            StatTile("Auto-ban roles", "${state.autoBanRoles.size}", Modifier.weight(1f))
        }
    }

    SectionCard {
        SectionCardHeader("Role automation", Icons.Default.Groups)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Self-assignable", "${state.selfAssignable.size}", Modifier.weight(1f))
            StatTile("Voice roles", "${state.voiceChannelRoles.size}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Auto-assign humans",
                value = "${state.autoAssign.normalRoles.size}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Auto-assign bots",
                value = "${state.autoAssign.botRoles.size}",
                modifier = Modifier.weight(1f),
            )
        }
    }

    SectionCard {
        SectionCardHeader("Server context", Icons.Default.Info)
        InfoRow("Timezone", state.timezoneId)
        InfoRow(
            "Game voice channel",
            if (state.gameVoiceChannelEnabled) "Enabled" else "Disabled",
        )
        InfoRow("Staff role", state.staffRoleId?.let { id ->
            state.availableRoles.firstOrNull { it.id == id }?.name?.let { "@$it" } ?: id
        } ?: "Not set")
        InfoRow("Member role", state.memberRoleId?.let { id ->
            state.availableRoles.firstOrNull { it.id == id }?.name?.let { "@$it" } ?: id
        } ?: "Not set")
    }
}

@Composable
private fun ProtectionSection(
    state: AdministrationState,
    onEdit: (ProtectionEditor) -> Unit,
) {
    val protection = state.protection
    if (protection == null) {
        SectionCard {
            EmptyState(message = "No protection data.", icon = Icons.Default.Shield)
        }
        return
    }

    ProtectionCard(
        title = "Anti-raid",
        enabled = protection.antiRaid.enabled,
        onEdit = { onEdit(ProtectionEditor.RAID) },
    ) {
        InfoRow(
            "Trigger",
            "${protection.antiRaid.userThreshold} joins / ${protection.antiRaid.seconds}s",
        )
        InfoRow("Action", AntiPunishmentAction.from(protection.antiRaid.action).label)
        InfoRow("Tracked users", "${protection.antiRaid.usersCount}")
    }

    ProtectionCard(
        title = "Anti-spam",
        enabled = protection.antiSpam.enabled,
        onEdit = { onEdit(ProtectionEditor.SPAM) },
    ) {
        InfoRow("Threshold", "${protection.antiSpam.messageThreshold} messages")
        InfoRow("Action", AntiPunishmentAction.from(protection.antiSpam.action).label)
        InfoRow("Mute time", "${protection.antiSpam.muteTime}m")
        InfoRow("Tracked users", "${protection.antiSpam.userCount}")
    }

    ProtectionCard(
        title = "Anti-alt",
        enabled = protection.antiAlt.enabled,
        onEdit = { onEdit(ProtectionEditor.ALT) },
    ) {
        InfoRow("Min account age", protection.antiAlt.minAge.ifEmpty { "Not set" })
        InfoRow("Action", AntiPunishmentAction.from(protection.antiAlt.action).label)
        InfoRow("Caught", "${protection.antiAlt.counter}")
    }

    ProtectionCard(
        title = "Anti-mass-mention",
        enabled = protection.antiMassMention.enabled,
        onEdit = { onEdit(ProtectionEditor.MASS_MENTION) },
    ) {
        InfoRow("Per message", "${protection.antiMassMention.mentionThreshold} mentions")
        InfoRow(
            "Window",
            "${protection.antiMassMention.maxMentionsInTimeWindow} in " +
                "${protection.antiMassMention.timeWindowSeconds}s",
        )
        InfoRow("Ignores bots", if (protection.antiMassMention.ignoreBots) "Yes" else "No")
        InfoRow("Action", AntiPunishmentAction.from(protection.antiMassMention.action).label)
    }

    ProtectionCard(
        title = "Anti-mass-post",
        enabled = protection.antiMassPost.enabled,
        onEdit = null,
    ) {
        InfoRow(
            "Trigger",
            "${protection.antiMassPost.channelThreshold} channels in " +
                "${protection.antiMassPost.timeWindowSeconds}s",
        )
        InfoRow("Action", AntiPunishmentAction.from(protection.antiMassPost.action).label)
        InfoRow("Caught", "${protection.antiMassPost.counter}")
    }

    Text(
        "Anti-mass-post and anti-pattern are configured from the web dashboard.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProtectionCard(
    title: String,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    SectionCard(
        modifier = if (onEdit != null) Modifier.clickableRow(onEdit) else Modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                if (enabled) Icons.Default.Shield else Icons.Default.Security,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            val color = if (enabled) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
                Text(
                    if (enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        if (enabled) {
            content()
        } else {
            Text(
                if (onEdit != null) {
                    "Tap to configure and switch on."
                } else {
                    "Disabled."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RolesSection(
    state: AdministrationState,
    onStaffRole: (Snowflake?) -> Unit,
    onMemberRole: (Snowflake?) -> Unit,
    onToggleAutoAssignNormal: (Snowflake) -> Unit,
    onToggleAutoAssignBot: (Snowflake) -> Unit,
    onToggleSelfAssignable: (Snowflake) -> Unit,
    onToggleAutoBan: (Snowflake) -> Unit,
) {
    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader("Special roles", Icons.Default.Groups)
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No staff role",
            selectedId = state.staffRoleId,
            onSelect = onStaffRole,
            label = "Staff role",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No member role",
            selectedId = state.memberRoleId,
            onSelect = onMemberRole,
            label = "Member role",
        )
    }

    RoleCheckList(
        title = "Auto-assigned to humans",
        icon = Icons.Default.Groups,
        blurb = "Applied automatically to non-bot members on join.",
        roles = state.availableRoles.map { it.id to it.name },
        selected = state.autoAssign.normalRoles,
        onToggle = onToggleAutoAssignNormal,
    )

    RoleCheckList(
        title = "Auto-assigned to bots",
        icon = Icons.Default.SmartToy,
        blurb = "Applied automatically to bot accounts on join.",
        roles = state.availableRoles.map { it.id to it.name },
        selected = state.autoAssign.botRoles,
        onToggle = onToggleAutoAssignBot,
    )

    RoleCheckList(
        title = "Self-assignable roles",
        icon = Icons.Default.PanTool,
        blurb = "Members can grant themselves these with /iam.",
        roles = state.availableRoles.map { it.id to it.name },
        selected = state.selfAssignable,
        onToggle = onToggleSelfAssignable,
    )

    RoleCheckList(
        title = "Auto-ban roles",
        icon = Icons.Default.Gavel,
        blurb = "Members who receive any of these roles are banned automatically.",
        roles = state.availableRoles.map { it.id to it.name },
        selected = state.autoBanRoles,
        onToggle = onToggleAutoBan,
        destructive = true,
    )
}

@Composable
private fun RoleCheckList(
    title: String,
    icon: ImageVector,
    blurb: String,
    roles: List<Pair<Snowflake, String>>,
    selected: List<Snowflake>,
    onToggle: (Snowflake) -> Unit,
    destructive: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val chosen = remember(selected) { selected.toSet() }

    SectionCard {
        SectionCardHeader(
            title = "$title (${chosen.size})",
            icon = icon,
            tint = if (destructive) MaterialTheme.colorScheme.error else null,
            trailing = {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Done" else "Edit")
                }
            },
        )
        Text(
            blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val visible = if (expanded) roles else roles.filter { it.first in chosen }
        if (visible.isEmpty()) {
            Text(
                "None selected. Tap Edit to pick roles.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEach { (id, name) ->
            ListItem(
                headlineContent = { Text("@$name") },
                leadingContent = {
                    Checkbox(checked = id in chosen, onCheckedChange = { onToggle(id) })
                },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickableRow { onToggle(id) },
            )
        }
    }
}

@Composable
private fun AutomationSection(
    state: AdministrationState,
    onTimezone: (String) -> Unit,
    onToggleGameVoice: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("Server timezone", Icons.Default.Public)
        if (state.availableTimezones.isEmpty()) {
            EmptyState(message = "Timezones not loaded.", icon = Icons.Default.Public)
        } else {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Public),
                options = state.availableTimezones.map {
                    SelectorOption(it.id, it.displayName.ifEmpty { it.id }, it.offset)
                },
                placeholder = "UTC",
                selectedId = state.timezoneId,
                onSelect = { it?.let(onTimezone) },
                label = "Timezone",
            )
        }
    }

    SectionCard {
        SectionCardHeader("Game voice channel", Icons.Default.SportsEsports)
        SwitchRow(
            title = "Assign roles by game played",
            subtitle = "Members joining a voice channel get the matching game role.",
            checked = state.gameVoiceChannelEnabled,
            onCheckedChange = { onToggleGameVoice() },
        )
    }

    if (state.voiceChannelRoles.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Voice channel roles", Icons.Default.Groups)
            state.voiceChannelRoles.forEach { entry ->
                InfoRow(
                    label = "Channel ${entry.voiceChannelId ?: "?"}",
                    value = entry.roleId?.let { id ->
                        state.availableRoles.firstOrNull { it.id == id }?.name?.let { "@$it" } ?: id
                    } ?: "None",
                )
            }
            Text(
                "Voice channel roles are added and removed with /voicerole.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    state: AdministrationState,
    onSaveBanMessage: (String) -> Unit,
    onMassBan: (List<Snowflake>, String?) -> Unit,
    onPrune: (Snowflake, Int) -> Unit,
) {
    var banMessage by remember(state.banMessage) {
        mutableStateOf(EmbedMessage.parse(state.banMessage))
    }
    var massBanIds by remember { mutableStateOf("") }
    var massBanReason by remember { mutableStateOf("") }
    var pruneChannelId by remember { mutableStateOf<String?>(null) }
    var pruneCount by remember { mutableStateOf(100f) }
    var confirmMassBan by remember { mutableStateOf(false) }
    var confirmPrune by remember { mutableStateOf(false) }

    val parsedIds = remember(massBanIds) {
        massBanIds.split(',', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.toLongOrNull() != null }
    }

    SectionCard {
        SectionCardHeader("Ban DM message", Icons.Default.Mail)
        Text(
            "Sent to a member when they are banned. Supports the standard placeholders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(message = banMessage, onMessageChange = { banMessage = it })
        Button(
            onClick = { onSaveBanMessage(banMessage.serialize()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save ban message") }
    }

    SectionCard {
        SectionCardHeader("Mass ban", Icons.Default.Gavel)
        MewdekoTextField(
            value = massBanIds,
            onValueChange = { massBanIds = it },
            label = "User IDs",
            placeholder = "Comma or space separated",
            singleLine = false,
            minLines = 3,
            supportingText = "${parsedIds.size} valid IDs",
        )
        MewdekoTextField(
            value = massBanReason,
            onValueChange = { massBanReason = it },
            label = "Reason",
            placeholder = "Optional",
        )
        Button(
            onClick = { confirmMassBan = true },
            enabled = parsedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mass ban ${parsedIds.size} users") }
    }

    SectionCard {
        SectionCardHeader("Prune channel", Icons.Default.ContentCut)
        Text(
            "Bulk deletes the most recent messages in a channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a channel",
            selectedId = pruneChannelId,
            onSelect = { pruneChannelId = it },
            label = "Channel",
        )
        SliderRow(
            label = "Messages",
            value = pruneCount,
            onValueChange = { pruneCount = it },
            valueRange = 1f..1000f,
        )
        Button(
            onClick = { confirmPrune = true },
            enabled = pruneChannelId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Prune ${pruneCount.toInt()} messages") }
    }

    if (confirmMassBan) {
        ConfirmDialog(
            title = "Ban ${parsedIds.size} users?",
            message = "This bans every listed ID immediately and cannot be undone in bulk.",
            confirmLabel = "Mass ban",
            onConfirm = {
                confirmMassBan = false
                onMassBan(parsedIds, massBanReason.trim().takeIf { it.isNotEmpty() })
                massBanIds = ""
                massBanReason = ""
            },
            onDismiss = { confirmMassBan = false },
        )
    }

    if (confirmPrune) {
        ConfirmDialog(
            title = "Prune ${pruneCount.toInt()} messages?",
            message = "The most recent messages in the selected channel will be deleted.",
            confirmLabel = "Prune",
            onConfirm = {
                confirmPrune = false
                pruneChannelId?.let { onPrune(it, pruneCount.toInt()) }
            },
            onDismiss = { confirmPrune = false },
        )
    }
}

/** The editable fields shared by every protection module. */
private data class ProtectionConfig(
    val enabled: Boolean = false,
    val userThreshold: Int = 5,
    val seconds: Int = 10,
    val messageThreshold: Int = 3,
    val muteTime: Int = 0,
    val minAgeMinutes: Int = 60,
    val actionDuration: Int = 0,
    val mentionThreshold: Int = 5,
    val timeWindowSeconds: Int = 10,
    val maxMentionsInWindow: Int = 5,
    val ignoreBots: Boolean = true,
    val action: AntiPunishmentAction = AntiPunishmentAction.MUTE,
    val roleId: Snowflake? = null,
) {
    /** The role id only matters when the punishment is "add role". */
    val roleArgument: Snowflake?
        get() = roleId.takeIf { action == AntiPunishmentAction.ADD_ROLE }
}

/** Reads a .NET timespan or second count into whole minutes. */
private fun parseMinutes(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toDoubleOrNull()?.let { return (it / 60).toInt() }
    val parts = trimmed.split(':')
    if (parts.size < 2) return null
    val hours = parts[0].toIntOrNull() ?: return null
    val minutes = parts[1].toIntOrNull() ?: return null
    return hours * 60 + minutes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionEditSheet(
    editor: ProtectionEditor,
    protection: ProtectionStatusDetail?,
    roles: List<SelectorOption>,
    onDismiss: () -> Unit,
    onSave: (ProtectionConfig) -> Unit,
) {
    var config by remember(editor, protection) {
        mutableStateOf(hydrate(editor, protection))
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(editor.label, style = MaterialTheme.typography.titleMedium)

            SwitchRow(
                title = "Enabled",
                checked = config.enabled,
                onCheckedChange = { config = config.copy(enabled = it) },
            )

            SectionCard {
                SectionCardHeader("Trigger", Icons.Default.Shield)
                when (editor) {
                    ProtectionEditor.RAID -> {
                        SliderRow(
                            label = "User threshold",
                            value = config.userThreshold.toFloat(),
                            onValueChange = { config = config.copy(userThreshold = it.toInt()) },
                            valueRange = 2f..30f,
                        )
                        SliderRow(
                            label = "Time window",
                            value = config.seconds.toFloat(),
                            onValueChange = { config = config.copy(seconds = it.toInt()) },
                            valueRange = 2f..300f,
                            valueLabel = "${config.seconds}s",
                        )
                        SliderRow(
                            label = "Punish duration",
                            value = config.actionDuration.toFloat(),
                            onValueChange = { config = config.copy(actionDuration = it.toInt()) },
                            valueRange = 0f..1440f,
                            valueLabel = "${config.actionDuration}m",
                        )
                    }

                    ProtectionEditor.SPAM -> {
                        SliderRow(
                            label = "Message threshold",
                            value = config.messageThreshold.toFloat(),
                            onValueChange = { config = config.copy(messageThreshold = it.toInt()) },
                            valueRange = 2f..100f,
                        )
                        SliderRow(
                            label = "Mute time",
                            value = config.muteTime.toFloat(),
                            onValueChange = { config = config.copy(muteTime = it.toInt()) },
                            valueRange = 0f..1440f,
                            valueLabel = "${config.muteTime}m",
                        )
                    }

                    ProtectionEditor.ALT -> {
                        SliderRow(
                            label = "Min account age",
                            value = config.minAgeMinutes.toFloat(),
                            onValueChange = { config = config.copy(minAgeMinutes = it.toInt()) },
                            valueRange = 1f..10080f,
                            valueLabel = "${config.minAgeMinutes}m",
                        )
                        SliderRow(
                            label = "Action duration",
                            value = config.actionDuration.toFloat(),
                            onValueChange = { config = config.copy(actionDuration = it.toInt()) },
                            valueRange = 0f..1440f,
                            valueLabel = "${config.actionDuration}m",
                        )
                    }

                    ProtectionEditor.MASS_MENTION -> {
                        SliderRow(
                            label = "Per-message threshold",
                            value = config.mentionThreshold.toFloat(),
                            onValueChange = { config = config.copy(mentionThreshold = it.toInt()) },
                            valueRange = 1f..50f,
                        )
                        SliderRow(
                            label = "Time window",
                            value = config.timeWindowSeconds.toFloat(),
                            onValueChange = {
                                config = config.copy(timeWindowSeconds = it.toInt())
                            },
                            valueRange = 5f..300f,
                            valueLabel = "${config.timeWindowSeconds}s",
                        )
                        SliderRow(
                            label = "Max mentions in window",
                            value = config.maxMentionsInWindow.toFloat(),
                            onValueChange = {
                                config = config.copy(maxMentionsInWindow = it.toInt())
                            },
                            valueRange = 1f..100f,
                        )
                        SwitchRow(
                            title = "Ignore bots",
                            checked = config.ignoreBots,
                            onCheckedChange = { config = config.copy(ignoreBots = it) },
                        )
                        SliderRow(
                            label = "Mute time",
                            value = config.muteTime.toFloat(),
                            onValueChange = { config = config.copy(muteTime = it.toInt()) },
                            valueRange = 0f..1440f,
                            valueLabel = "${config.muteTime}m",
                        )
                    }
                }
            }

            SectionCard {
                SectionCardHeader("Punishment", Icons.Default.Gavel)
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Gavel),
                    options = AntiPunishmentAction.entries.map {
                        SelectorOption(it.raw.toString(), it.label)
                    },
                    placeholder = "Mute",
                    selectedId = config.action.raw.toString(),
                    onSelect = { value ->
                        config = config.copy(
                            action = AntiPunishmentAction.from(value?.toIntOrNull() ?: 0)
                        )
                    },
                    label = "Action",
                )
                if (config.action == AntiPunishmentAction.ADD_ROLE) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = roles,
                        placeholder = "Pick a role",
                        selectedId = config.roleId,
                        onSelect = { config = config.copy(roleId = it) },
                        label = "Role to add",
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(onClick = { onSave(config) }, modifier = Modifier.weight(1f)) {
                    Text("Save")
                }
            }
        }
    }
}

/** Seeds the edit sheet from whatever the bot currently reports. */
private fun hydrate(
    editor: ProtectionEditor,
    protection: ProtectionStatusDetail?,
): ProtectionConfig {
    val base = ProtectionConfig()
    if (protection == null) return base
    return when (editor) {
        ProtectionEditor.RAID -> base.copy(
            enabled = protection.antiRaid.enabled,
            userThreshold = protection.antiRaid.userThreshold.coerceAtLeast(2),
            seconds = protection.antiRaid.seconds.coerceAtLeast(2),
            action = AntiPunishmentAction.from(protection.antiRaid.action),
        )

        ProtectionEditor.SPAM -> base.copy(
            enabled = protection.antiSpam.enabled,
            messageThreshold = protection.antiSpam.messageThreshold.coerceAtLeast(2),
            muteTime = protection.antiSpam.muteTime.coerceAtLeast(0),
            action = AntiPunishmentAction.from(protection.antiSpam.action),
        )

        ProtectionEditor.ALT -> base.copy(
            enabled = protection.antiAlt.enabled,
            minAgeMinutes = parseMinutes(protection.antiAlt.minAge)?.coerceAtLeast(1) ?: 60,
            action = AntiPunishmentAction.from(protection.antiAlt.action),
        )

        ProtectionEditor.MASS_MENTION -> base.copy(
            enabled = protection.antiMassMention.enabled,
            mentionThreshold = protection.antiMassMention.mentionThreshold.coerceAtLeast(1),
            timeWindowSeconds = protection.antiMassMention.timeWindowSeconds.coerceAtLeast(5),
            maxMentionsInWindow = protection.antiMassMention.maxMentionsInTimeWindow
                .coerceAtLeast(1),
            ignoreBots = protection.antiMassMention.ignoreBots,
            action = AntiPunishmentAction.from(protection.antiMassMention.action),
        )
    }
}
