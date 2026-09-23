package dev.mewdeko.mobile.feature.statroles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
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
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import java.text.NumberFormat
import kotlin.math.roundToInt

private val Tabs = listOf(
    SectionTab("roles", "Stat Roles", Icons.Default.EmojiEvents),
    SectionTab("editor", "Create / Edit", Icons.Default.Edit),
)

private val StatOptions = StatRoleStat.entries.map { SelectorOption(it.value.toString(), it.label, it.blurb) }
private val LimitOptions = StatRoleLimit.entries.map { SelectorOption(it.value.toString(), it.label, it.blurb) }

/** Selector id standing in for "no announcement channel". */
private const val NoChannel = "none"

/** How many members each ranked list in a result shows. */
private const val ResultListSize = 15

private fun formatNumber(value: Number): String = NumberFormat.getIntegerInstance().format(value)

/** Stat Roles: roles earned by activity and taken away again when it stops. */
@Composable
fun StatrolesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StatRolesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<StatRole?>(null) }
    var pendingRun by remember { mutableStateOf<StatRole?>(null) }

    FeatureScaffold(
        title = "Stat Roles",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = viewModel::startNew) {
                Icon(Icons.Default.Add, contentDescription = "New stat role")
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "roles" -> RolesSection(
                state = state,
                viewModel = viewModel,
                onRun = { pendingRun = it },
                onDelete = { pendingDelete = it },
            )

            "editor" -> EditorSection(state, viewModel)
        }
    }

    pendingDelete?.let { role ->
        ConfirmDialog(
            title = "Delete stat role",
            message = "Delete \"${role.name}\"? Members keep whatever they currently hold.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                viewModel.delete(role)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingRun?.let { role ->
        ConfirmDialog(
            title = "Run now",
            message = "Evaluate \"${role.name}\" and apply role changes right now?",
            confirmLabel = "Run",
            destructive = false,
            onConfirm = {
                pendingRun = null
                viewModel.run(role)
            },
            onDismiss = { pendingRun = null },
        )
    }
}

@Composable
private fun RolesSection(
    state: StatRolesState,
    viewModel: StatRolesViewModel,
    onRun: (StatRole) -> Unit,
    onDelete: (StatRole) -> Unit,
) {
    Text(
        text = "Each role is re-evaluated on its own schedule.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    if (state.roles.isEmpty()) {
        SectionCard {
            EmptyState(
                "No stat roles yet. Create one to reward active members automatically.",
                icon = Icons.Default.EmojiEvents,
            )
        }
        return
    }

    state.roles.forEach { role ->
        StatRoleCard(
            role = role,
            state = state,
            busy = state.busyId == role.id,
            onPreview = { viewModel.preview(role) },
            onRun = { onRun(role) },
            onToggle = { viewModel.toggleEnabled(role) },
            onEdit = { viewModel.edit(role) },
            onDelete = { onDelete(role) },
            onDismissResult = { viewModel.dismissResult(role.id) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatRoleCard(
    role: StatRole,
    state: StatRolesState,
    busy: Boolean,
    onPreview: () -> Unit,
    onRun: () -> Unit,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissResult: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "#${role.id} ${role.name}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${state.roleName(role.roleId)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = role.enabled, onCheckedChange = { onToggle() })
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TagChip(if (role.enabled) "enabled" else "disabled")
            if (role.permanent) TagChip("permanent")
            if (role.invert) TagChip("inverted")
            role.groupName?.takeIf { it.isNotBlank() }?.let { TagChip("group: $it") }
        }

        if (role.condition.isNotBlank()) {
            Text(role.condition, style = MaterialTheme.typography.bodyMedium)
        }

        Text(
            text = buildList {
                add("Every ${role.intervalMinutes}m")
                add("last run ${role.lastRun?.relativeToNow() ?: "never"}")
                role.announceChannelId?.let { add("announces in #${state.channelName(it) ?: "channel"}") }
                if (role.notifyDm) add("DMs members")
            }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalButton(onClick = onPreview, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Preview", maxLines = 1)
            }
            FilledTonalButton(onClick = onRun, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Run now", maxLines = 1)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${role.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${role.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        state.results[role.id]?.let { panel ->
            HorizontalDivider()
            ResultPanel(panel, onDismiss = onDismissResult)
        }
    }
}

@Composable
private fun ResultPanel(panel: StatRoleResultPanel, onDismiss: () -> Unit) {
    val result = panel.result
    val ok = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.tertiary
    val crit = MaterialTheme.colorScheme.error

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (panel.preview) "Preview" else "Run result",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Hide result")
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Qualify", formatNumber(result.qualifyingCount), Modifier.weight(1f))
        StatTile(
            if (panel.preview) "Would gain" else "To gain",
            formatNumber(result.toGrant.size),
            Modifier.weight(1f),
            tint = ok,
        )
        StatTile(
            if (panel.preview) "Would lose" else "To lose",
            formatNumber(result.toRemove.size),
            Modifier.weight(1f),
            tint = if (result.toRemove.isNotEmpty()) warn else null,
        )
    }
    if (!panel.preview) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Granted", formatNumber(result.granted), Modifier.weight(1f), tint = ok)
            StatTile("Removed", formatNumber(result.removed), Modifier.weight(1f))
            StatTile(
                "Failed",
                formatNumber(result.failed),
                Modifier.weight(1f),
                tint = if (result.failed > 0) crit else null,
            )
        }
    }

    RankedMembers("Gaining the role", result.toGrant, useServerRank = true)
    RankedMembers("Losing the role", result.toRemove, useServerRank = false)
}

@Composable
private fun RankedMembers(title: String, members: List<StatRoleMember>, useServerRank: Boolean) {
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    if (members.isEmpty()) {
        Text("Nobody.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    members.take(ResultListSize).forEachIndexed { index, member ->
        val rank = if (useServerRank) member.rank ?: (index + 1) else index + 1
        val name = member.username?.takeIf { it.isNotBlank() } ?: member.userId
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
            Avatar(url = member.avatarUrl, contentDescription = null, size = 28)
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatNumber(member.value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    if (members.size > ResultListSize) {
        Text(
            text = "and ${formatNumber(members.size - ResultListSize)} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditorSection(state: StatRolesState, viewModel: StatRolesViewModel) {
    val draft = state.draft
    val roleOptions = state.guildRoles.map { SelectorOption(it.id, it.name) }

    Text(
        text = if (draft.isNew) "New stat role" else "Edit stat role #${draft.id}",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Text(
        text = "Pick what to measure, how members qualify, and what happens when they do.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    ConditionCard(draft, roleOptions, viewModel)
    FiltersCard(state, draft, roleOptions, viewModel)
    ScheduleCard(state, draft, viewModel)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                when {
                    state.isSaving -> "Saving…"
                    draft.isNew -> "Create stat role"
                    else -> "Save changes"
                },
            )
        }
        OutlinedButton(onClick = viewModel::cancelEdit, enabled = !state.isSaving) {
            Text("Cancel")
        }
    }
}

@Composable
private fun ConditionCard(draft: StatRoleDraft, roleOptions: List<SelectorOption>, viewModel: StatRolesViewModel) {
    SectionCard {
        SectionCardHeader("Condition", Icons.Default.Tune)
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "Pick a role",
            label = "Role to manage (required)",
            selectedId = draft.roleId,
            onSelect = { id -> viewModel.updateDraft { it.copy(roleId = id) } },
        )
        Hint("Granted to members who qualify and removed from those who no longer do.")
        MewdekoTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.updateDraft { it.copy(name = value.take(StatRoleDraft.NAME_MAX)) } },
            label = "Name",
            supportingText = "Shown in lists; defaults to the role name.",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Insights),
            options = StatOptions,
            placeholder = "Messages",
            label = "Measure",
            selectedId = draft.statType.value.toString(),
            onSelect = { id ->
                val stat = StatRoleStat.from(id?.toIntOrNull() ?: 0)
                viewModel.updateDraft { it.copy(statType = stat) }
            },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.FilterList),
            options = LimitOptions,
            placeholder = "Threshold",
            label = "Qualify by",
            selectedId = draft.limitType.value.toString(),
            onSelect = { id -> viewModel.setLimit(StatRoleLimit.from(id?.toIntOrNull() ?: 0)) },
        )

        if (draft.statType == StatRoleStat.ACTIVITY_MINUTES) {
            MewdekoTextField(
                value = draft.activityName,
                onValueChange = { value ->
                    viewModel.updateDraft { it.copy(activityName = value.take(StatRoleDraft.ACTIVITY_MAX)) }
                },
                label = "Game or app",
                placeholder = "Any game",
                supportingText = "Leave empty to count time in any game.",
            )
        }

        when (draft.limitType) {
            StatRoleLimit.THRESHOLD -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    value = draft.minimum,
                    label = "Minimum",
                    hint = "The least a member needs",
                    modifier = Modifier.weight(1f),
                ) { value -> viewModel.updateDraft { it.copy(minimum = value) } }
                NumberField(
                    value = draft.maximum,
                    label = "Maximum",
                    hint = "Empty for no ceiling",
                    modifier = Modifier.weight(1f),
                ) { value -> viewModel.updateDraft { it.copy(maximum = value) } }
            }

            StatRoleLimit.TOP_RANK, StatRoleLimit.TOP_PERCENT -> {
                val rank = draft.limitType == StatRoleLimit.TOP_RANK
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = draft.topStart,
                        label = if (rank) "From rank" else "From percentile",
                        hint = "Best position, usually 1",
                        modifier = Modifier.weight(1f),
                    ) { value -> viewModel.updateDraft { it.copy(topStart = value) } }
                    NumberField(
                        value = draft.topEnd,
                        label = if (rank) "To rank" else "To percentile",
                        hint = if (rank) "Worst position that qualifies" else "Up to 100",
                        modifier = Modifier.weight(1f),
                    ) { value -> viewModel.updateDraft { it.copy(topEnd = value) } }
                }
            }

            StatRoleLimit.DAILY_STREAK -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = draft.minimum,
                        label = "Per day minimum",
                        hint = "Needed on a day for it to count",
                        modifier = Modifier.weight(1f),
                    ) { value -> viewModel.updateDraft { it.copy(minimum = value) } }
                    NumberField(
                        value = draft.requiredDays,
                        label = "Required days",
                        hint = "Days in the window that must meet it",
                        modifier = Modifier.weight(1f),
                    ) { value -> viewModel.updateDraft { it.copy(requiredDays = value) } }
                }
                if (draft.streakInvalid) {
                    Text(
                        text = "Daily streaks only work with messages, voice minutes or minutes in a game.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (draft.statType.usesWindow) {
            val streak = draft.limitType == StatRoleLimit.DAILY_STREAK
            val floor = if (streak) 1 else 0
            val days = draft.lookbackDays.coerceIn(floor, StatRoleDraft.MAX_LOOKBACK)
            SliderRow(
                label = "Window",
                value = days.toFloat(),
                onValueChange = { value -> viewModel.updateDraft { it.copy(lookbackDays = value.roundToInt()) } },
                valueRange = floor.toFloat()..StatRoleDraft.MAX_LOOKBACK.toFloat(),
                steps = StatRoleDraft.MAX_LOOKBACK - floor - 1,
                valueLabel = if (days == 0) "All time" else "$days days",
            )
            Hint(
                if (streak) "How far back to measure, 1 to 90 days."
                else "How far back to measure, 1 to 90 days. 0 means all time.",
            )
        }

        SwitchRow(
            title = "Permanent",
            subtitle = "Never removed once earned",
            checked = draft.permanent,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(permanent = value) } },
        )
        SwitchRow(
            title = "Invert",
            subtitle = "Members who do NOT qualify get it (inactivity roles)",
            checked = draft.invert,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(invert = value) } },
        )
        SwitchRow(
            title = "Include bots",
            subtitle = "Consider bot accounts too",
            checked = draft.applyToBots,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(applyToBots = value) } },
        )
    }
}

@Composable
private fun FiltersCard(
    state: StatRolesState,
    draft: StatRoleDraft,
    roleOptions: List<SelectorOption>,
    viewModel: StatRolesViewModel,
) {
    SectionCard {
        SectionCardHeader("Filters", Icons.Default.FilterList)
        Hint("Who is considered and where activity is counted.")

        if (draft.statType.supportsChannelFilter) {
            DiscordSelector(
                kind = SelectorKind.Channel,
                options = state.activityChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Any channel",
                label = "Only count in channels",
                multiple = true,
                selection = draft.channelFilter,
                onSelectionChange = { ids -> viewModel.updateDraft { it.copy(channelFilter = ids) } },
            )
            Hint("Empty counts every channel.")
        }

        DiscordSelector(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No requirement",
            label = "Required roles",
            multiple = true,
            selection = draft.roleWhitelist,
            onSelectionChange = { ids -> viewModel.updateDraft { it.copy(roleWhitelist = ids) } },
        )
        Hint("Members need at least one of these.")

        DiscordSelector(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "None",
            label = "Excluded roles",
            multiple = true,
            selection = draft.roleBlacklist,
            onSelectionChange = { ids -> viewModel.updateDraft { it.copy(roleBlacklist = ids) } },
        )
        Hint("Members holding any of these are never considered.")

        DiscordSelector(
            kind = SelectorKind.User,
            options = state.members.map { member ->
                SelectorOption(
                    id = member.id,
                    name = member.label,
                    subtitle = member.username.takeIf { it.isNotBlank() && it != member.label },
                )
            },
            placeholder = "None",
            label = "Ignored members",
            multiple = true,
            selection = draft.ignoredUsers,
            onSelectionChange = { ids -> viewModel.updateDraft { it.copy(ignoredUsers = ids) } },
        )
        Hint("The role is never granted to or removed from these members.")

        MewdekoTextField(
            value = draft.groupName,
            onValueChange = { value -> viewModel.updateDraft { it.copy(groupName = value.take(StatRoleDraft.GROUP_MAX)) } },
            label = "Group",
            placeholder = "e.g. activity-tiers",
            supportingText = "Stat roles sharing a group keep only the highest tier a member qualifies for.",
        )
    }
}

@Composable
private fun ScheduleCard(state: StatRolesState, draft: StatRoleDraft, viewModel: StatRolesViewModel) {
    val channelOptions = listOf(SelectorOption(NoChannel, "No announcements")) +
        state.textChannels.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader("Schedule and notifications", Icons.Default.Schedule)
        NumberField(
            value = draft.intervalMinutes,
            label = "Evaluate every (minutes)",
            hint = "At least ${StatRoleDraft.MIN_INTERVAL}. Default is every 3 hours.",
            isError = (draft.intervalMinutes.toIntOrNull() ?: 0) < StatRoleDraft.MIN_INTERVAL,
        ) { value -> viewModel.updateDraft { it.copy(intervalMinutes = value) } }
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No announcements",
            label = "Announce in",
            selectedId = draft.notifyChannelId ?: NoChannel,
            onSelect = { id ->
                viewModel.updateDraft { it.copy(notifyChannelId = id?.takeIf { value -> value != NoChannel }) }
            },
        )
        Hint("A message is posted here whenever the role is granted or removed.")
        SwitchRow(
            title = "DM the member",
            subtitle = "Send the same message directly to the member",
            checked = draft.notifyDm,
            onCheckedChange = { value -> viewModel.updateDraft { it.copy(notifyDm = value) } },
        )
        MewdekoTextField(
            value = draft.notifyMessage,
            onValueChange = { value -> viewModel.updateDraft { it.copy(notifyMessage = value) } },
            label = "Message",
            placeholder = "%user% %action% %role%",
            singleLine = false,
            minLines = 3,
            supportingText = "Placeholders: %user%, %role%, %action% (earned or lost), %value%, %stat%. " +
                "Empty uses the default.",
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    MewdekoTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(12)) },
        label = label,
        numeric = true,
        supportingText = hint,
        isError = isError,
        modifier = modifier,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
