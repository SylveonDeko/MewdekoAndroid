package dev.mewdeko.mobile.feature.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import dev.mewdeko.mobile.core.model.WarningRecord
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
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
    SectionTab("purge", "Purge", Icons.Default.DeleteSweep),
)

private const val AllActionsId = "*"
private const val MaxPruneDays = 7

/** Warnings, the auto-punishment ladder, and the warn-log destination. */
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

        state.warnLogChannelName?.let { name ->
            SectionCard(contentPadding = 12) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Warn log",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "#$name",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when (state.section) {
            "warnings" -> WarningsSection(state, viewModel)
            "punishments" -> PunishmentsSection(state)
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
            state.punishments.take(5).forEach { PunishmentRow(it, compact = true) }
        }
    }

    if (state.warnings.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Recent activity", Icons.Default.Schedule)
            state.warnings.take(5).forEach { warning ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = warning.userId ?: "Unknown user",
                            style = MonospaceStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        warning.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
            }
        }
    }
}

@Composable
private fun WarningsSection(state: ModerationState, viewModel: ModerationViewModel) {
    SectionCard {
        SearchField(
            value = state.filterText,
            onValueChange = viewModel::setFilter,
            placeholder = "Filter by user ID or reason",
        )
        SwitchRow(
            title = "Active only",
            subtitle = "Hide forgiven warnings",
            checked = state.activeOnly,
            onCheckedChange = viewModel::setActiveOnly,
        )
    }

    val filtered = state.filteredWarnings
    if (filtered.isEmpty()) {
        SectionCard { EmptyState("No warnings.", icon = Icons.Default.Warning) }
    } else {
        filtered.forEach { WarningCard(it) }
    }
}

@Composable
private fun WarningCard(warning: WarningRecord) {
    SectionCard(contentPadding = 12) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = warning.userId ?: "Unknown user",
                style = MonospaceStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (warning.forgiven) TagChip("Forgiven")
        }
        warning.reason?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            warning.moderator?.let {
                Text(
                    text = "by $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            warning.dateAdded?.let {
                Text(
                    text = it.relativeToNow(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PunishmentsSection(state: ModerationState) {
    if (state.punishments.isEmpty()) {
        SectionCard {
            EmptyState("No automatic punishments configured.", icon = Icons.Default.Gavel)
        }
        return
    }
    state.punishments.forEach { PunishmentRow(it, compact = false) }
}

@Composable
private fun PunishmentRow(punishment: WarningPunishment, compact: Boolean) {
    val content = @Composable {
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
                    punishment.roleId?.let {
                        Text(
                            text = "Role: $it",
                            style = MonospaceStyle,
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

    if (compact) content() else SectionCard(contentPadding = 12) { content() }
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
