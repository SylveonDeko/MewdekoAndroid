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
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MultiSelectDropdown
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
import dev.mewdeko.mobile.core.ui.diffSelection
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
            AdminSection.PROTECTION -> ProtectionSection(
                state = state,
                viewModel = viewModel,
                onEdit = { editor = it },
            )

            AdminSection.ROLES -> RolesSection(state = state, viewModel = viewModel)

            AdminSection.AUTOMATION -> AutomationSection(state = state, viewModel = viewModel)

            AdminSection.ADVANCED -> AdvancedSection(state = state, viewModel = viewModel)
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
            StatTile("Active", "${state.activeProtections}/8", Modifier.weight(1f))
            StatTile("Auto-ban roles", "${state.autoBanRoles.size}", Modifier.weight(1f))
        }
    }

    SectionCard {
        SectionCardHeader("Role automation", Icons.Default.Groups)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Self-assignable", "${state.selfAssignable.roles.size}", Modifier.weight(1f))
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
            state.gameVoiceChannelId?.let { id ->
                state.availableVoiceChannels.firstOrNull { it.id == id }?.name ?: id
            } ?: "Disabled",
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
    viewModel: AdministrationViewModel,
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
        onQuickToggle = { viewModel.quickToggleProtection(QuickProtectionModule.RAID) },
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
        onQuickToggle = { viewModel.quickToggleProtection(QuickProtectionModule.SPAM) },
    ) {
        InfoRow("Threshold", "${protection.antiSpam.messageThreshold} messages")
        InfoRow("Action", AntiPunishmentAction.from(protection.antiSpam.action).label)
        InfoRow("Mute time", "${protection.antiSpam.muteTime}m")
        InfoRow("Tracked users", "${protection.antiSpam.userCount}")
    }
    AntiSpamIgnoredChannelsCard(state = state, viewModel = viewModel)

    ProtectionCard(
        title = "Anti-alt",
        enabled = protection.antiAlt.enabled,
        onEdit = { onEdit(ProtectionEditor.ALT) },
        onQuickToggle = { viewModel.quickToggleProtection(QuickProtectionModule.ALT) },
    ) {
        InfoRow("Min account age", protection.antiAlt.minAge.ifEmpty { "Not set" })
        InfoRow("Action", AntiPunishmentAction.from(protection.antiAlt.action).label)
        InfoRow("Caught", "${protection.antiAlt.counter}")
    }

    ProtectionCard(
        title = "Anti-mass-mention",
        enabled = protection.antiMassMention.enabled,
        onEdit = { onEdit(ProtectionEditor.MASS_MENTION) },
        onQuickToggle = { viewModel.quickToggleProtection(QuickProtectionModule.MASS_MENTION) },
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

    AntiMassPostCard(state = state, viewModel = viewModel, onQuickToggle = {
        viewModel.quickToggleProtection(QuickProtectionModule.MASS_POST)
    })

    AntiPatternCard(state = state, viewModel = viewModel, onQuickToggle = {
        viewModel.quickToggleProtection(QuickProtectionModule.PATTERN)
    })

    AntiPostChannelCard(state = state, viewModel = viewModel, onQuickToggle = {
        viewModel.quickToggleProtection(QuickProtectionModule.POST_CHANNEL)
    })

    AntiImageHashCard(state = state, viewModel = viewModel, onQuickToggle = {
        viewModel.quickToggleProtection(QuickProtectionModule.IMAGE_HASH)
    })
}

/** Shared card shell for every protection module: header, enabled badge, and content or hint. */
@Composable
fun ProtectionCard(
    title: String,
    enabled: Boolean,
    onEdit: (() -> Unit)?,
    onQuickToggle: (() -> Unit)?,
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
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.16f),
                onClick = { onQuickToggle?.invoke() },
                enabled = onQuickToggle != null,
            ) {
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
                    "Tap to configure, or tap the badge to switch on with sensible defaults."
                } else {
                    "Tap the badge to switch on with sensible defaults."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RolesSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader("Special roles", Icons.Default.Groups)
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No staff role",
            selectedId = state.staffRoleId,
            onSelect = viewModel::setStaffRole,
            label = "Staff role",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No member role",
            selectedId = state.memberRoleId,
            onSelect = viewModel::setMemberRole,
            label = "Member role",
        )
    }

    SectionCard {
        SectionCardHeader("Auto-assigned roles", Icons.Default.SmartToy)
        Text(
            "Given automatically to new members the moment they join.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RoleToggleDropdown(
            label = "Humans",
            placeholder = "No roles for humans",
            options = roleOptions,
            selected = state.autoAssign.normalRoles,
            onToggle = viewModel::toggleAutoAssignNormal,
        )
        RoleToggleDropdown(
            label = "Bots",
            placeholder = "No roles for bots",
            options = roleOptions,
            selected = state.autoAssign.botRoles,
            onToggle = viewModel::toggleAutoAssignBot,
        )
    }

    SectionCard {
        SectionCardHeader("Auto-ban roles", Icons.Default.Gavel, tint = MaterialTheme.colorScheme.error)
        Text(
            "Members who receive any of these roles are banned automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RoleToggleDropdown(
            label = "Ban on role",
            placeholder = "No auto-ban roles",
            options = roleOptions,
            selected = state.autoBanRoles,
            onToggle = viewModel::toggleAutoBanRole,
            destructive = true,
        )
    }

    SelfAssignableRolesSection(state = state, viewModel = viewModel)
    VoiceChannelRolesSection(state = state, viewModel = viewModel)
    ReactionRolesSection(state = state, viewModel = viewModel)
}

/**
 * A role [MultiSelectDropdown] for settings whose view model saves one role
 * at a time: every role added or removed in the dropdown is passed to
 * [onToggle] once.
 */
@Composable
private fun RoleToggleDropdown(
    label: String,
    placeholder: String,
    options: List<SelectorOption>,
    selected: List<Snowflake>,
    onToggle: (Snowflake) -> Unit,
    destructive: Boolean = false,
) {
    MultiSelectDropdown(
        kind = SelectorKind.Role,
        options = options,
        selection = selected,
        onSelectionChange = { next ->
            val (added, removed) = diffSelection(selected, next)
            (removed + added).forEach(onToggle)
        },
        label = label,
        placeholder = placeholder,
        destructive = destructive,
    )
}

@Composable
private fun AutomationSection(state: AdministrationState, viewModel: AdministrationViewModel) {
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
                onSelect = { it?.let(viewModel::setTimezone) },
                label = "Timezone",
            )
        }
    }

    GameVoiceChannelSection(state = state, viewModel = viewModel)
    DeleteMessageOnCommandSection(state = state, viewModel = viewModel)
    CommandCooldownsSection(state = state, viewModel = viewModel)
    PermissionOverridesSection(state = state, viewModel = viewModel)
    PermissionsManagerSection(state = state, viewModel = viewModel)
    StatsPrivacySection(state = state, viewModel = viewModel)
}

@Composable
private fun AdvancedSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    BanMessageSection(state = state, viewModel = viewModel)
    ServerRecoverySection(state = state, viewModel = viewModel)
    MassOperationsSection(state = state, viewModel = viewModel)
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

/** Reads a bare minute count or a legacy `H:MM` timespan into whole minutes. */
private fun parseMinutes(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toDoubleOrNull()?.let { return it.toInt() }
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

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = editor.label) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                            valueRange = 2f..10f,
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
            actionDuration = protection.antiRaid.punishDuration,
        )

        ProtectionEditor.SPAM -> base.copy(
            enabled = protection.antiSpam.enabled,
            messageThreshold = protection.antiSpam.messageThreshold.coerceAtLeast(2),
            muteTime = protection.antiSpam.muteTime.coerceAtLeast(0),
            action = AntiPunishmentAction.from(protection.antiSpam.action),
            roleId = protection.antiSpam.roleId,
        )

        ProtectionEditor.ALT -> base.copy(
            enabled = protection.antiAlt.enabled,
            minAgeMinutes = parseMinutes(protection.antiAlt.minAge)?.coerceAtLeast(1) ?: 60,
            action = AntiPunishmentAction.from(protection.antiAlt.action),
            actionDuration = protection.antiAlt.actionDuration,
            roleId = protection.antiAlt.roleId,
        )

        ProtectionEditor.MASS_MENTION -> base.copy(
            enabled = protection.antiMassMention.enabled,
            mentionThreshold = protection.antiMassMention.mentionThreshold.coerceAtLeast(1),
            timeWindowSeconds = protection.antiMassMention.timeWindowSeconds.coerceAtLeast(5),
            maxMentionsInWindow = protection.antiMassMention.maxMentionsInTimeWindow
                .coerceAtLeast(1),
            ignoreBots = protection.antiMassMention.ignoreBots,
            action = AntiPunishmentAction.from(protection.antiMassMention.action),
            muteTime = protection.antiMassMention.muteTime,
            roleId = protection.antiMassMention.roleId,
        )
    }
}
