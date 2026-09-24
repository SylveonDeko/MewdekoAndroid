package dev.mewdeko.mobile.feature.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.BarChart),
    SectionTab("warnings", "Warnings", Icons.Default.Warning),
    SectionTab("warnactions", "Warning actions", Icons.Default.Gavel),
    SectionTab("activity", "Activity", Icons.Default.Schedule),
    SectionTab("purge", "Ban cleanup", Icons.Default.DeleteSweep),
)

private const val AllActionsId = "*"
private const val MaxPruneDays = 7

/** The "Default for every ban" choice meaning no server-wide value, so each action keeps its built in value. */
private const val BuiltInDays = -1

/** Warnings, the automatic warning actions, the warn-log destination, and ban cleanup settings. */
@Composable
fun ModerationScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ModerationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var showAddAction by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Moderation",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.section == "warnactions") {
                NewItemFab(label = "New action", onClick = { showAddAction = true })
            }
        },
    ) {
        SectionTabs(
            tabs = Tabs,
            selectedId = state.section,
            onSelect = viewModel::setSection,
        )

        when (state.section) {
            "warnings" -> WarningsSection(state, viewModel)
            "warnactions" -> WarningActionsSection(
                state = state,
                viewModel = viewModel,
                showAddAction = showAddAction,
                onRequestAddAction = { showAddAction = true },
                onDismissAddAction = { showAddAction = false },
            )
            "activity" -> ActivitySection(state)
            "purge" -> BanCleanupSection(state, viewModel)
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
            SectionCardHeader("Warning actions", Icons.Default.Shield)
            state.punishments.take(5).forEach {
                WarningActionRow(it, compact = true, roleName = state.roleName(it.roleId))
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

/**
 * The automatic actions triggered when a member reaches a warning count, plus the
 * channel warnings are logged to. New actions are added through the [NewItemFab] and
 * [FormSheet] in [ModerationScreen], following the app-wide "new" convention.
 */
@Composable
private fun WarningActionsSection(
    state: ModerationState,
    viewModel: ModerationViewModel,
    showAddAction: Boolean,
    onRequestAddAction: () -> Unit,
    onDismissAddAction: () -> Unit,
) {
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

    if (state.punishments.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No warning actions yet. Warnings are recorded, but nothing " +
                    "happens automatically.",
                icon = Icons.Default.Gavel,
                actionLabel = "Add a warning action",
                onAction = onRequestAddAction,
            )
        }
    } else {
        state.punishments.forEach { action ->
            key(action.id) {
                SectionCard(contentPadding = 12) {
                    WarningActionRow(action, compact = false, roleName = state.roleName(action.roleId))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { pendingRemoval = action },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "Remove warning action at ${action.count} warnings"
                            },
                        ) { Text("Remove") }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { action ->
        ConfirmDialog(
            title = "Remove this warning action?",
            message = action.removalMessage(state.roleName(action.roleId)),
            confirmLabel = "Remove",
            onConfirm = { viewModel.removePunishment(action.count) },
            onDismiss = { pendingRemoval = null },
        )
    }

    if (showAddAction) {
        AddWarningActionSheet(state = state, viewModel = viewModel, onDismiss = onDismissAddAction)
    }
}

@Composable
private fun AddWarningActionSheet(
    state: ModerationState,
    viewModel: ModerationViewModel,
    onDismiss: () -> Unit,
) {
    var count by remember { mutableStateOf("3") }
    var punishment by remember { mutableIntStateOf(PunishmentActions.MUTE) }
    var timeMinutes by remember { mutableStateOf("") }
    var roleId by remember { mutableStateOf<String?>(null) }

    val countValue = count.toIntOrNull()
    val canConfirm = countValue != null && countValue in 1..100 &&
        (punishment != PunishmentActions.ADD_ROLE || roleId != null)

    FormSheet(
        title = "Add a warning action",
        confirmLabel = "Add",
        confirmEnabled = canConfirm,
        onConfirm = {
            countValue?.let { value ->
                viewModel.addPunishment(value, punishment, timeMinutes.toIntOrNull(), roleId)
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    ) {
        MewdekoTextField(
            value = count,
            onValueChange = { count = it.filter(Char::isDigit) },
            label = "Warnings",
            placeholder = "1-100",
            numeric = true,
        )

        EnumPicker(
            label = "Action",
            options = PunishmentActions.Selectable.map { (code, name) -> EnumOption(code, name) },
            selected = punishment,
            onSelect = { punishment = it },
            showDescription = false,
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
    }
}

/** A warning action's threshold and effect, read as a sentence such as "When a member reaches 3 warnings: Ban". */
@Composable
private fun WarningActionRow(action: WarningPunishment, compact: Boolean, roleName: String? = null) {
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
                text = action.count.toString(),
                style = if (compact) MaterialTheme.typography.labelLarge
                else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = action.sentence(roleName),
            style = if (compact) MaterialTheme.typography.bodySmall
            else MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Which rule sheet is open, and the stored setting it edits, or null when adding a new one. */
private data class RuleSheetTarget(val existing: BanPruneSetting?)

/** "Don't delete", or how many days of messages a ban removes. */
private fun pruneDaysLabel(days: Int): String = when {
    days <= 0 -> "Don't delete"
    days == 1 -> "1 day"
    else -> "$days days"
}

/** The 0 through 7 day choices Discord accepts when banning. */
private val PruneDayOptions: List<EnumOption<Int>> =
    (0..MaxPruneDays).map { EnumOption(it, pruneDaysLabel(it)) }

/** Joins names as a sentence list, such as "Ban, Softban and Mass ban". */
private fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.first()
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

/**
 * Describes what each action deletes when no server-wide default is set, such as
 * "Ban and Softban delete 7 days. Everything else keeps messages."
 */
private fun builtInSummary(actions: List<BanPruneActionInfo>): String {
    if (actions.isEmpty()) return "Each kind of ban uses its own built in value."
    val deleting = actions.filter { it.defaultDays > 0 }.groupBy { it.defaultDays }
    if (deleting.isEmpty()) return "No ban deletes messages unless you choose otherwise."
    val sentences = deleting.entries.sortedByDescending { it.key }.map { (days, group) ->
        val verb = if (group.size == 1) "deletes" else "delete"
        "${joinNames(group.map { it.displayName })} $verb ${pruneDaysLabel(days)}."
    }
    val coveredCount = deleting.values.sumOf { it.size }
    val rest = if (coveredCount < actions.size) " Everything else keeps messages." else ""
    return sentences.joinToString(" ") + rest
}

/**
 * How many days of messages the bot deletes when it bans someone, as one compact card:
 * a server-wide default, a short list of actions that differ from it, and a short list
 * of channel or category overrides. Every value is a 0 to 7 day dropdown, and rules are
 * added or edited in a [FormSheet]. Resetting everything asks first.
 */
@Composable
private fun BanCleanupSection(state: ModerationState, viewModel: ModerationViewModel) {
    var pendingRemoval by remember { mutableStateOf<BanPruneSetting?>(null) }
    var showReset by remember { mutableStateOf(false) }
    var actionRuleSheet by remember { mutableStateOf<RuleSheetTarget?>(null) }
    var overrideSheet by remember { mutableStateOf<RuleSheetTarget?>(null) }

    val guildDefaults = state.guildPruneDefaults
    val allActionsSetting = guildDefaults[""]
    val actionRules = guildDefaults.values
        .filter { it.actionKey.isNotEmpty() }
        .sortedBy { state.pruneActionName(it.actionKey).lowercase() }
    val unconfiguredActions = state.pruneActions.filter { it.key !in guildDefaults }

    SectionCard {
        SectionCardHeader("Ban cleanup", Icons.Default.DeleteSweep)
        Text(
            text = "When the bot bans someone, it can also delete their recent messages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        EnumPicker(
            label = "Default for every ban",
            options = listOf(
                EnumOption(BuiltInDays, "Built in", builtInSummary(state.pruneActions)),
            ) + PruneDayOptions,
            selected = allActionsSetting?.pruneDays ?: BuiltInDays,
            onSelect = { days ->
                when {
                    days == BuiltInDays -> allActionsSetting?.let { viewModel.clearPrune(it) }
                    days != allActionsSetting?.pruneDays ->
                        viewModel.setPrune(BanPruneScope.GUILD, "0", null, days)
                }
            },
        )

        HorizontalDivider()
        RuleGroupHeading(
            title = "Different for specific actions",
            caption = "Actions not listed here use the default above.",
        )
        actionRules.forEach { setting ->
            key(setting.id) {
                PruneRuleRow(
                    icon = Icons.Default.Gavel,
                    title = state.pruneActionName(setting.actionKey),
                    subtitle = null,
                    days = setting.pruneDays,
                    onEdit = { actionRuleSheet = RuleSheetTarget(setting) },
                    onRemove = { pendingRemoval = setting },
                )
            }
        }
        if (unconfiguredActions.isNotEmpty()) {
            AddRuleButton("Add action rule") { actionRuleSheet = RuleSheetTarget(null) }
        }

        HorizontalDivider()
        RuleGroupHeading(
            title = "Channel and category overrides",
            caption = "A channel beats its category, which beats the server default.",
        )
        state.pruneOverrides.forEach { setting ->
            key(setting.id) {
                PruneRuleRow(
                    icon = if (setting.scopeType == BanPruneScope.CATEGORY) {
                        Icons.Default.Layers
                    } else {
                        Icons.Default.Tag
                    },
                    title = state.pruneScopeName(setting),
                    subtitle = state.pruneActionName(setting.actionKey),
                    days = setting.pruneDays,
                    onEdit = { overrideSheet = RuleSheetTarget(setting) },
                    onRemove = { pendingRemoval = setting },
                )
            }
        }
        AddRuleButton("Add override") { overrideSheet = RuleSheetTarget(null) }

        if (state.pruneSettings.isNotEmpty()) {
            HorizontalDivider()
            TextButton(
                onClick = { showReset = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Reset everything") }
        }
    }

    actionRuleSheet?.let { target ->
        ActionRuleSheet(
            state = state,
            existing = target.existing,
            onSave = { actionKey, days ->
                viewModel.setPrune(BanPruneScope.GUILD, "0", actionKey, days)
                actionRuleSheet = null
            },
            onDismiss = { actionRuleSheet = null },
        )
    }

    overrideSheet?.let { target ->
        OverrideSheet(
            state = state,
            existing = target.existing,
            onSave = { scopeType, scopeId, actionKey, days ->
                viewModel.setPrune(scopeType, scopeId, actionKey, days)
                overrideSheet = null
            },
            onDismiss = { overrideSheet = null },
        )
    }

    pendingRemoval?.let { setting ->
        val isActionRule = setting.scopeType == BanPruneScope.GUILD
        ConfirmDialog(
            title = if (isActionRule) "Remove action rule?" else "Remove override?",
            message = if (isActionRule) {
                "${state.pruneActionName(setting.actionKey)} goes back to the default for every ban."
            } else {
                "Bans in ${state.pruneScopeName(setting)} fall back to the next broadest setting."
            },
            confirmLabel = "Remove",
            onConfirm = { viewModel.clearPrune(setting) },
            onDismiss = { pendingRemoval = null },
        )
    }

    if (showReset) {
        ConfirmDialog(
            title = "Reset ban cleanup?",
            message = "Every rule and override is removed, and each kind of ban goes back " +
                "to its built in value.",
            confirmLabel = "Reset",
            onConfirm = { viewModel.resetPrune() },
            onDismiss = { showReset = false },
        )
    }
}

/** A group title inside the ban cleanup card, with a one-line explanation under it. */
@Composable
private fun RuleGroupHeading(title: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The inline "add" action at the foot of a rule list. */
@Composable
private fun AddRuleButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * One stored ban cleanup rule: what it covers, how many days it deletes, and a remove
 * button. Tapping the row opens it for editing.
 */
@Composable
private fun PruneRuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    days: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Edit", onClick = onEdit)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TagChip(pruneDaysLabel(days))
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove $title",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Adds or edits a rule giving one action its own ban cleanup value. When editing, the
 * action is fixed and only the number of days changes.
 */
@Composable
private fun ActionRuleSheet(
    state: ModerationState,
    existing: BanPruneSetting?,
    onSave: (actionKey: String, days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = if (existing != null) {
        state.pruneActions.filter { it.key == existing.actionKey }.ifEmpty {
            listOf(BanPruneActionInfo(existing.actionKey, state.pruneActionName(existing.actionKey)))
        }
    } else {
        state.pruneActions.filter { it.key !in state.guildPruneDefaults }
    }
    var actionKey by remember { mutableStateOf(existing?.actionKey ?: choices.firstOrNull()?.key.orEmpty()) }
    var days by remember { mutableIntStateOf(existing?.pruneDays ?: 0) }

    FormSheet(
        title = if (existing == null) "Add action rule" else "Edit action rule",
        confirmLabel = if (existing == null) "Add" else "Save",
        confirmEnabled = actionKey.isNotEmpty(),
        onConfirm = { onSave(actionKey, days) },
        onDismiss = onDismiss,
    ) {
        EnumPicker(
            label = "Action",
            options = choices.map { action ->
                EnumOption(
                    value = action.key,
                    title = action.displayName,
                    description = if (action.defaultDays > 0) {
                        "Normally deletes ${pruneDaysLabel(action.defaultDays)}"
                    } else {
                        "Normally keeps messages"
                    },
                )
            },
            selected = actionKey,
            onSelect = { actionKey = it },
            enabled = existing == null,
        )
        EnumPicker(
            label = "Messages to delete",
            options = PruneDayOptions,
            selected = days,
            onSelect = { days = it },
            showDescription = false,
        )
    }
}

/**
 * Adds or edits a channel or category override. When editing, what the override covers
 * is fixed and only the number of days changes.
 */
@Composable
private fun OverrideSheet(
    state: ModerationState,
    existing: BanPruneSetting?,
    onSave: (scopeType: Int, scopeId: String, actionKey: String?, days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = existing != null
    var scopeType by remember { mutableIntStateOf(existing?.scopeType ?: BanPruneScope.CHANNEL) }
    var targetId by remember { mutableStateOf(existing?.scopeId) }
    var actionId by remember {
        mutableStateOf(existing?.actionKey?.takeIf { it.isNotEmpty() } ?: AllActionsId)
    }
    var days by remember { mutableIntStateOf(existing?.pruneDays ?: 0) }

    val isCategory = scopeType == BanPruneScope.CATEGORY
    val targets = if (isCategory) state.availableCategories else state.availableChannels

    FormSheet(
        title = if (editing) "Edit override" else "Add override",
        confirmLabel = if (editing) "Save" else "Add",
        confirmEnabled = targetId != null,
        onConfirm = {
            targetId?.let { target ->
                onSave(scopeType, target, actionId.takeIf { it != AllActionsId }, days)
            }
        },
        onDismiss = onDismiss,
    ) {
        EnumPicker(
            label = "Applies to",
            options = listOf(
                EnumOption(BanPruneScope.CHANNEL, "One channel", icon = Icons.Default.Tag),
                EnumOption(
                    BanPruneScope.CATEGORY,
                    "Every channel in a category",
                    icon = Icons.Default.Layers,
                ),
            ),
            selected = scopeType,
            onSelect = { selected ->
                if (selected != scopeType) {
                    scopeType = selected
                    targetId = null
                }
            },
            enabled = !editing,
            showDescription = false,
        )
        DiscordSelectorSingle(
            kind = if (isCategory) SelectorKind.Custom(Icons.Default.Layers) else SelectorKind.Channel,
            options = targets.map { SelectorOption(it.id, it.name) },
            placeholder = if (isCategory) "Pick a category" else "Pick a channel",
            label = if (isCategory) "Category" else "Channel",
            selectedId = targetId,
            onSelect = { targetId = it },
            enabled = !editing,
        )
        EnumPicker(
            label = "Action",
            options = listOf(EnumOption(AllActionsId, "All actions")) +
                state.pruneActions.map { EnumOption(it.key, it.displayName) },
            selected = actionId,
            onSelect = { actionId = it },
            enabled = !editing,
            showDescription = false,
        )
        EnumPicker(
            label = "Messages to delete",
            options = PruneDayOptions,
            selected = days,
            onSelect = { days = it },
            showDescription = false,
        )
    }
}
