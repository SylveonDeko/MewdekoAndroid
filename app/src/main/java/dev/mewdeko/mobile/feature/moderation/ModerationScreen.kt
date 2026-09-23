package dev.mewdeko.mobile.feature.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.BarChart),
    SectionTab("warnings", "Warnings", Icons.Default.Warning),
    SectionTab("punishments", "Ladder", Icons.Default.Gavel),
    SectionTab("activity", "Activity", Icons.Default.Schedule),
    SectionTab("purge", "Purge", Icons.Default.DeleteSweep),
)

private const val AllActionsId = "*"
private const val MaxPruneDays = 7

/** Warnings, the auto-punishment ladder, the warn-log destination, and ban purge settings. */
@Composable
fun ModerationScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Moderation",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(
            tabs = Tabs,
            selectedId = state.section,
            onSelect = viewModel::setSection,
        )

        when (state.section) {
            "warnings" -> WarningsSection(state, viewModel)
            "punishments" -> PunishmentsSection(state, viewModel)
            "activity" -> ActivitySection(state)
            "purge" -> PurgeSection(state, viewModel)
            else -> OverviewSection(state)
        }
    }
}

@Composable
private fun OverviewSection(state: ModerationState) {
    SectionCard {
        SectionCardHeader("Warning totals", Icons.Default.BarChart)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Total", state.warnings.size.withSeparators(), Modifier.weight(1f))
            StatTile(
                label = "Active",
                value = state.activeCount.withSeparators(),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Forgiven",
                value = state.forgivenCount.withSeparators(),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (state.punishments.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Auto-punishment ladder", Icons.Default.Shield)
            state.punishments.take(5).forEach {
                PunishmentRow(it, compact = true, roleName = state.roleName(it.roleId))
            }
        }
    }

    if (state.recentActivity.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Recent activity", Icons.Default.Schedule)
            state.recentActivity.take(5).forEachIndexed { index, warning ->
                if (index > 0) HorizontalDivider()
                WarningEntry(warning, onForgive = null, onDelete = null)
            }
        }
    }
}

@Composable
private fun WarningsSection(state: ModerationState, viewModel: ModerationViewModel) {
    var pendingForgiveAllUser by remember { mutableStateOf<Snowflake?>(null) }
    var pendingDeleteWarning by remember { mutableStateOf<ModerationWarning?>(null) }

    SectionCard {
        SearchField(
            value = state.filterText,
            onValueChange = viewModel::setFilter,
            placeholder = "Filter by user ID, reason, or moderator",
        )
        SwitchRow(
            title = "Show forgiven warnings",
            checked = state.showForgiven,
            onCheckedChange = viewModel::setShowForgiven,
        )
    }

    SectionCard {
        SectionCardHeader("Warn a user", Icons.Default.Add)
        WarnUserForm(viewModel)
    }

    val groups = state.warningsByUser
    if (groups.isEmpty()) {
        SectionCard { EmptyState("No warnings.", icon = Icons.Default.Warning) }
    } else {
        groups.forEach { group ->
            key(group.userId) {
                SectionCard(contentPadding = 12) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = group.userId,
                            style = MonospaceStyle,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (group.activeCount > 0) TagChip("${group.activeCount} active")
                    }
                    if (group.activeCount > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { pendingForgiveAllUser = group.userId }) {
                                Text("Forgive all")
                            }
                        }
                    }
                    group.warnings.forEachIndexed { index, warning ->
                        if (index > 0) HorizontalDivider()
                        WarningEntry(
                            warning = warning,
                            onForgive = if (warning.forgiven) null else {
                                { viewModel.forgiveWarning(warning.id) }
                            },
                            onDelete = { pendingDeleteWarning = warning },
                        )
                    }
                }
            }
        }
    }

    pendingForgiveAllUser?.let { targetUserId ->
        ConfirmDialog(
            title = "Forgive all warnings?",
            message = "Every active warning for $targetUserId is marked as forgiven.",
            confirmLabel = "Forgive all",
            destructive = false,
            onConfirm = { viewModel.forgiveAllForUser(targetUserId) },
            onDismiss = { pendingForgiveAllUser = null },
        )
    }

    pendingDeleteWarning?.let { warning ->
        ConfirmDialog(
            title = "Delete warning?",
            message = "This permanently removes the warning for " +
                "${warning.userId ?: "this user"}.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteWarning(warning.id) },
            onDismiss = { pendingDeleteWarning = null },
        )
    }
}

@Composable
private fun WarnUserForm(viewModel: ModerationViewModel) {
    var targetUserId by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    MewdekoTextField(
        value = targetUserId,
        onValueChange = { targetUserId = it.filter(Char::isDigit); error = null },
        label = "User ID",
        placeholder = "Discord user ID",
        numeric = true,
    )
    MewdekoTextField(
        value = reason,
        onValueChange = { if (it.length <= 500) reason = it },
        label = "Reason",
        singleLine = false,
        minLines = 2,
        supportingText = "${reason.length}/500",
    )
    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(
        onClick = {
            val trimmedId = targetUserId.trim()
            if (!trimmedId.matches(Regex("\\d{15,22}"))) {
                error = "Enter a valid Discord user ID."
                return@Button
            }
            if (reason.isBlank()) {
                error = "A reason is required."
                return@Button
            }
            error = null
            viewModel.warnUser(trimmedId, reason.trim())
            targetUserId = ""
            reason = ""
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Send warning") }
}

/** A warning's reason, moderator, forgiveness state, and date, with optional actions. */
@Composable
private fun WarningEntry(
    warning: ModerationWarning,
    onForgive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = warning.reason?.takeIf { it.isNotBlank() } ?: "No reason given",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning.forgiven) TagChip("Forgiven")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                warning.moderator?.let {
                    Text(
                        text = "by $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (warning.forgiven && !warning.forgivenBy.isNullOrBlank()) {
                    Text(
                        text = "Forgiven by ${warning.forgivenBy}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            warning.dateAdded?.let {
                Text(
                    text = it.relativeToNow(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onForgive != null || onDelete != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                onForgive?.let { TextButton(onClick = it) { Text("Forgive") } }
                onDelete?.let {
                    TextButton(
                        onClick = it,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(state: ModerationState) {
    if (state.recentActivity.isEmpty()) {
        SectionCard { EmptyState("No recent moderation activity.", icon = Icons.Default.Schedule) }
        return
    }
    state.recentActivity.forEach { warning ->
        key(warning.id) {
            SectionCard(contentPadding = 12) {
                WarningEntry(warning, onForgive = null, onDelete = null)
            }
        }
    }
}

@Composable
private fun PunishmentsSection(state: ModerationState, viewModel: ModerationViewModel) {
    var pendingRemoval by remember { mutableStateOf<WarningPunishment?>(null) }

    SectionCard {
        SectionCardHeader("Warning log channel", Icons.Default.Description)
        Text(
            text = "Where forgiven and active warnings are posted for staff to review.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "No channel set",
            selectedId = state.warnLogChannel,
            onSelect = { channelId -> channelId?.let { viewModel.setWarnLogChannel(it) } },
        )
    }

    SectionCard {
        SectionCardHeader("Add a rung", Icons.Default.Add)
        AddPunishmentForm(state, viewModel)
    }

    if (state.punishments.isEmpty()) {
        SectionCard {
            EmptyState("No automatic punishments configured.", icon = Icons.Default.Gavel)
        }
    } else {
        state.punishments.forEach { punishment ->
            key(punishment.id) {
                SectionCard(contentPadding = 12) {
                    PunishmentRow(punishment, compact = false, roleName = state.roleName(punishment.roleId))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { pendingRemoval = punishment },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { punishment ->
        ConfirmDialog(
            title = "Remove rung?",
            message = "Warning count ${punishment.count} no longer triggers " +
                "${punishment.actionLabel}.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removePunishment(punishment.count) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun AddPunishmentForm(state: ModerationState, viewModel: ModerationViewModel) {
    var count by remember { mutableStateOf("3") }
    var punishment by remember { mutableIntStateOf(PunishmentActions.MUTE) }
    var timeMinutes by remember { mutableStateOf("") }
    var roleId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    MewdekoTextField(
        value = count,
        onValueChange = { count = it.filter(Char::isDigit) },
        label = "Warning count",
        placeholder = "1-100",
        numeric = true,
    )

    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Gavel),
        options = PunishmentActions.Selectable.map { (code, name) -> SelectorOption(code.toString(), name) },
        placeholder = "Punishment",
        label = "Punishment",
        selectedId = punishment.toString(),
        onSelect = { selected -> punishment = selected?.toIntOrNull() ?: PunishmentActions.MUTE },
    )

    if (punishment in PunishmentActions.Timed) {
        MewdekoTextField(
            value = timeMinutes,
            onValueChange = { timeMinutes = it.filter(Char::isDigit) },
            label = "Duration in minutes",
            placeholder = "Permanent",
            numeric = true,
        )
    }

    if (punishment == PunishmentActions.ADD_ROLE) {
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a role",
            label = "Role to add",
            selectedId = roleId,
            onSelect = { roleId = it },
        )
    }

    error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Button(
        onClick = {
            val countValue = count.toIntOrNull()
            if (countValue == null || countValue !in 1..100) {
                error = "Warning count must be between 1 and 100."
                return@Button
            }
            if (punishment == PunishmentActions.ADD_ROLE && roleId == null) {
                error = "Choose the role to add."
                return@Button
            }
            error = null
            viewModel.addPunishment(countValue, punishment, timeMinutes.toIntOrNull(), roleId)
            count = (countValue + 1).toString()
            timeMinutes = ""
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add rung") }
}

@Composable
private fun PunishmentRow(punishment: WarningPunishment, compact: Boolean, roleName: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(if (compact) 36.dp else 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${punishment.count}w",
                style = if (compact) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = punishment.actionLabel,
                style = if (compact) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.titleSmall,
            )
            if (!compact) {
                if (punishment.time > 0) {
                    Text(
                        text = "Duration: ${punishment.time}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                roleName?.let {
                    Text(
                        text = "Role: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (compact && punishment.time > 0) {
            Text(
                text = "${punishment.time}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PurgeSection(state: ModerationState, viewModel: ModerationViewModel) {
    var pendingRemoval by remember { mutableStateOf<BanPruneSetting?>(null) }
    var showReset by remember { mutableStateOf(false) }

    SectionCard {
        SectionCardHeader("Server defaults", Icons.Default.DeleteSweep)
        Text(
            text = "How many days of a member's messages each action deletes when it bans them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val allActionsSetting = state.guildPruneDefaults[""]
        PruneSliderRow(
            label = "All actions",
            subtitle = "Applies to any action below that has no value of its own.",
            days = allActionsSetting?.pruneDays ?: 0,
            onCommit = { days ->
                viewModel.setPrune(BanPruneScope.GUILD, "0", null, days)
            },
            onClear = allActionsSetting?.let { setting -> { viewModel.clearPrune(setting) } },
        )
    }

    if (state.pruneActions.isEmpty()) {
        SectionCard {
            EmptyState("No ban actions reported by the bot.", icon = Icons.Default.DeleteSweep)
        }
    } else {
        state.pruneActions.forEach { action ->
            key(action.key) {
                SectionCard(contentPadding = 12) {
                    PruneSliderRow(
                        label = action.displayName,
                        subtitle = state.guildPruneSource(action),
                        days = state.guildPruneFor(action),
                        onCommit = { days ->
                            viewModel.setPrune(BanPruneScope.GUILD, "0", action.key, days)
                        },
                        onClear = state.guildPruneDefaults[action.key]
                            ?.let { setting -> { viewModel.clearPrune(setting) } },
                    )
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Overrides", Icons.Default.Layers)
        Text(
            text = "A channel beats its category, which beats the server default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AddOverrideForm(state, viewModel)
    }

    if (state.pruneOverrides.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No overrides. Every channel uses the server defaults.",
                icon = Icons.Default.Layers,
            )
        }
    } else {
        state.pruneOverrides.forEach { setting ->
            key(setting.id) {
                SectionCard(contentPadding = 12) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            if (setting.scopeType == BanPruneScope.CATEGORY) {
                                Icons.Default.Layers
                            } else {
                                Icons.Default.Tag
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.pruneScopeName(setting),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.pruneActionName(setting.actionKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { pendingRemoval = setting }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove override",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    PruneSliderRow(
                        label = null,
                        subtitle = null,
                        days = setting.pruneDays,
                        onCommit = { days ->
                            viewModel.setPrune(
                                setting.scopeType,
                                setting.scopeId,
                                setting.actionKey.takeIf { it.isNotEmpty() },
                                days,
                            )
                        },
                        onClear = null,
                    )
                }
            }
        }
    }

    if (state.pruneSettings.isNotEmpty()) {
        SectionCard(contentPadding = 12) {
            TextButton(onClick = { showReset = true }) {
                Text(
                    text = "Reset everything to defaults",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    pendingRemoval?.let { setting ->
        ConfirmDialog(
            title = "Remove override?",
            message = "Bans in ${state.pruneScopeName(setting)} fall back to the next " +
                "broadest setting.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.clearPrune(setting) },
            onDismiss = { pendingRemoval = null },
        )
    }

    if (showReset) {
        ConfirmDialog(
            title = "Reset purge settings?",
            message = "Every server default and override is removed, and each action goes back " +
                "to its built in purge.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.resetPrune() },
            onDismiss = { showReset = false },
        )
    }
}

/**
 * A slider bound to a stored purge value. The slider tracks the drag locally and
 * only writes once the gesture ends, so a drag does not fire a request per frame.
 */
@Composable
private fun PruneSliderRow(
    label: String?,
    subtitle: String?,
    days: Int,
    onCommit: (Int) -> Unit,
    onClear: (() -> Unit)?,
) {
    var draft by remember(days) { mutableIntStateOf(days) }

    if (label != null || onClear != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                label?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onClear?.let {
                TextButton(onClick = it) { Text("Unset") }
            }
        }
    }

    SliderRow(
        label = "Days of messages",
        value = draft.toFloat(),
        onValueChange = { draft = it.toInt() },
        onValueChangeFinished = { onCommit(draft) },
        valueRange = 0f..MaxPruneDays.toFloat(),
        steps = MaxPruneDays - 1,
        valueLabel = if (draft <= 0) "None" else "$draft day${if (draft == 1) "" else "s"}",
    )
}

@Composable
private fun AddOverrideForm(state: ModerationState, viewModel: ModerationViewModel) {
    var scopeType by remember { mutableIntStateOf(BanPruneScope.CHANNEL) }
    var targetId by remember { mutableStateOf<String?>(null) }
    var actionId by remember { mutableStateOf(AllActionsId) }
    var days by remember { mutableIntStateOf(0) }

    val targets = if (scopeType == BanPruneScope.CATEGORY) {
        state.availableCategories
    } else {
        state.availableChannels
    }

    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Layers),
        options = listOf(
            SelectorOption(BanPruneScope.CHANNEL.toString(), "Channel"),
            SelectorOption(BanPruneScope.CATEGORY.toString(), "Category"),
        ),
        placeholder = "Scope",
        label = "Scope",
        selectedId = scopeType.toString(),
        onSelect = { selected ->
            scopeType = selected?.toIntOrNull() ?: BanPruneScope.CHANNEL
            targetId = null
        },
    )

    DiscordSelectorSingle(
        kind = if (scopeType == BanPruneScope.CATEGORY) {
            SelectorKind.Custom(Icons.Default.Layers)
        } else {
            SelectorKind.Channel
        },
        options = targets.map { SelectorOption(it.id, it.name) },
        placeholder = "Pick one",
        label = if (scopeType == BanPruneScope.CATEGORY) "Category" else "Channel",
        selectedId = targetId,
        onSelect = { targetId = it },
    )

    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Gavel),
        options = listOf(SelectorOption(AllActionsId, "All actions")) +
            state.pruneActions.map { SelectorOption(it.key, it.displayName) },
        placeholder = "Action",
        label = "Action",
        selectedId = actionId,
        onSelect = { actionId = it ?: AllActionsId },
    )

    SliderRow(
        label = "Purge",
        value = days.toFloat(),
        onValueChange = { days = it.toInt() },
        valueRange = 0f..MaxPruneDays.toFloat(),
        steps = MaxPruneDays - 1,
        valueLabel = if (days <= 0) "None" else "$days day${if (days == 1) "" else "s"}",
    )

    Button(
        onClick = {
            targetId?.let { target ->
                viewModel.setPrune(
                    scopeType,
                    target,
                    actionId.takeIf { it != AllActionsId },
                    days,
                )
                targetId = null
                actionId = AllActionsId
                days = 0
            }
        },
        enabled = targetId != null,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add override") }
}
