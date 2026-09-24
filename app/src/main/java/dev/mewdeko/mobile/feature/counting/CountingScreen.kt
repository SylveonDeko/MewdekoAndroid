package dev.mewdeko.mobile.feature.counting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.NewItemFab
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

private val Tabs = listOf(
    SectionTab("channels", "Channels", Icons.Default.Numbers),
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("management", "Manage", Icons.Default.Shield),
)

/** Counting game channels. */
@Composable
fun CountingScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: CountingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showSetup by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showSavePoint by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<CountingChannelDetail?>(null) }
    var pendingRestoreSave by remember { mutableStateOf<CountingSavePoint?>(null) }
    var pendingDeleteSave by remember { mutableStateOf<CountingSavePoint?>(null) }
    var pendingPurge by remember { mutableStateOf(false) }
    var purgeReason by remember { mutableStateOf("") }

    val selected = state.selected

    FeatureScaffold(
        title = "Counting",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            NewItemFab(label = "Add channel", onClick = { showSetup = true })
        },
    ) {
        if (state.channels.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No counting channels yet. Add one to start the game.",
                    icon = Icons.Default.Numbers,
                    actionLabel = "Add channel",
                    onAction = { showSetup = true },
                )
            }
            return@FeatureScaffold
        }

        SectionCard(contentPadding = 12) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.channels.forEach { channel ->
                    FilterChip(
                        selected = channel.channelId == state.selectedChannelId,
                        onClick = { viewModel.selectChannel(channel.channelId) },
                        label = {
                            Text(
                                text = "#${channel.channelName ?: channel.channelId}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        selected?.let { channel ->
            SectionCard {
                SectionCardHeader(
                    title = "#${channel.channelName ?: channel.channelId}",
                    icon = Icons.Default.Numbers,
                    trailing = {
                        IconButton(onClick = { pendingRemove = channel }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove counting channel",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Current", "${channel.currentNumber}", Modifier.weight(1f))
                    StatTile("Highest", "${channel.highestNumber}", Modifier.weight(1f))
                    StatTile("Counts", "${channel.totalCounts}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip(if (channel.isActive) "Active" else "Paused")
                    TagChip("Step ${channel.increment}")
                    channel.lastUsername?.let { TagChip("Last: $it") }
                }
                state.stats?.let { stats ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Participants",
                            value = "${stats.totalParticipants}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Errors",
                            value = "${stats.totalErrors}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Accuracy",
                            value = "%.0f%%".format(stats.averageAccuracy),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Milestones",
                            value = "${stats.milestonesReached}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    stats.topContributor?.let { top ->
                        ListItem(
                            leadingContent = { Avatar(top.avatarUrl, top.username, size = 36) },
                            headlineContent = { Text(top.username ?: top.userId) },
                            supportingContent = {
                                Text(
                                    "${top.contributionsCount} contributions · " +
                                        "best streak ${top.highestStreak} · " +
                                        "%.0f%% accurate · ".format(top.accuracy) +
                                        "${top.totalNumbersCounted} numbers"
                                )
                            },
                            trailingContent = if (top.rank != null) {
                                { TagChip("Rank #${top.rank}") }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                    stats.lastActivity?.let {
                        Text(
                            text = "Last counted ${it.relativeToNow()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showReset = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Text("Reset", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(
                        onClick = { showSavePoint = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text("Save point", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        val channelId = state.selectedChannelId

        when (state.section) {
            "settings" -> {
                val config = state.config
                if (config == null || channelId == null) {
                    SectionCard { EmptyState("Select a channel to configure it.") }
                } else {
                    SectionCard {
                        SectionCardHeader("Rules", Icons.Default.Tune)
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Numbers),
                            options = CountingPattern.entries.map {
                                SelectorOption(it.raw.toString(), it.label)
                            },
                            placeholder = "Sequential",
                            label = "Number pattern",
                            selectedId = config.pattern.toString(),
                            onSelect = { raw ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(pattern = raw?.toIntOrNull() ?: 0)
                                }
                            },
                        )
                        SwitchRow(
                            title = "Allow repeated counters",
                            subtitle = "The same member may count twice in a row",
                            checked = config.allowRepeatedUsers,
                            onCheckedChange = { value ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(allowRepeatedUsers = value)
                                }
                            },
                        )
                        SwitchRow(
                            title = "Reset on error",
                            subtitle = "Start over when someone counts wrong",
                            checked = config.resetOnError,
                            onCheckedChange = { value ->
                                viewModel.updateConfig(channelId) { it.copy(resetOnError = value) }
                            },
                        )
                        SwitchRow(
                            title = "Delete wrong messages",
                            checked = config.deleteWrongMessages,
                            onCheckedChange = { value ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(deleteWrongMessages = value)
                                }
                            },
                        )
                        SwitchRow(
                            title = "Achievements",
                            checked = config.enableAchievements,
                            onCheckedChange = { value ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(enableAchievements = value)
                                }
                            },
                        )
                        SwitchRow(
                            title = "Competitions",
                            checked = config.enableCompetitions,
                            onCheckedChange = { value ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(enableCompetitions = value)
                                }
                            },
                        )
                    }

                    SectionCard {
                        SectionCardHeader("Limits", Icons.Default.Tune)
                        MewdekoTextField(
                            value = config.numberBase.toString(),
                            onValueChange = { raw ->
                                raw.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }?.let { value ->
                                    viewModel.updateConfig(channelId) { it.copy(numberBase = value) }
                                }
                            },
                            label = "Number base",
                            numeric = true,
                            supportingText = "2-36 (10 for decimal)",
                        )
                        MewdekoTextField(
                            value = config.cooldown.toString(),
                            onValueChange = { raw ->
                                val value = raw.filter(Char::isDigit).toIntOrNull() ?: 0
                                viewModel.updateConfig(channelId) {
                                    it.copy(cooldown = value.coerceAtLeast(0))
                                }
                            },
                            label = "Cooldown (seconds)",
                            numeric = true,
                            supportingText = "0 for no cooldown",
                        )
                        MewdekoTextField(
                            value = config.maxNumber.toString(),
                            onValueChange = { raw ->
                                val value = raw.filter(Char::isDigit).toIntOrNull() ?: 0
                                viewModel.updateConfig(channelId) {
                                    it.copy(maxNumber = value.coerceAtLeast(0))
                                }
                            },
                            label = "Maximum number",
                            numeric = true,
                            supportingText = "0 for unlimited",
                        )
                    }

                    SectionCard {
                        SectionCardHeader("Roles", Icons.Default.Tune)
                        DiscordSelector(
                            kind = SelectorKind.Role,
                            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                            placeholder = "Anyone can count",
                            label = "Required roles",
                            multiple = true,
                            selection = config.requiredRoleIds,
                            onSelectionChange = { ids ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(requiredRoles = ids.joinToString(","))
                                }
                            },
                        )
                        DiscordSelector(
                            kind = SelectorKind.Role,
                            options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                            placeholder = "Nobody is banned",
                            label = "Banned roles",
                            multiple = true,
                            selection = config.bannedRoleIds,
                            onSelectionChange = { ids ->
                                viewModel.updateConfig(channelId) {
                                    it.copy(bannedRoles = ids.joinToString(","))
                                }
                            },
                        )
                    }

                    SectionCard {
                        SectionCardHeader("Reactions", Icons.Default.Tune)
                        MewdekoTextField(
                            value = config.successEmote.orEmpty(),
                            onValueChange = { value ->
                                viewModel.updateConfig(channelId) { it.copy(successEmote = value) }
                            },
                            label = "Success emote",
                        )
                        MewdekoTextField(
                            value = config.errorEmote.orEmpty(),
                            onValueChange = { value ->
                                viewModel.updateConfig(channelId) { it.copy(errorEmote = value) }
                            },
                            label = "Error emote",
                        )
                    }
                }
            }

            "leaderboard" -> SectionCard {
                SectionCardHeader("Leaderboard", Icons.Default.Leaderboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LeaderboardType.entries.forEach { type ->
                        FilterChip(
                            selected = state.leaderboardType == type,
                            onClick = { viewModel.setLeaderboardType(type) },
                            label = { Text(type.label) },
                        )
                    }
                }
                MewdekoTextField(
                    value = state.leaderboardLimit.toString(),
                    onValueChange = { raw ->
                        raw.filter(Char::isDigit).toIntOrNull()?.let { viewModel.setLeaderboardLimit(it) }
                    },
                    label = "Limit",
                    numeric = true,
                    supportingText = "5-100 rows",
                )
                if (state.leaderboard.isEmpty()) {
                    EmptyState("Nobody has counted here yet.", icon = Icons.Default.Numbers)
                } else {
                    state.leaderboard.forEachIndexed { index, user ->
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${user.rank ?: index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = user.username ?: user.userId,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    "${user.contributionsCount} counts · " +
                                        "streak ${user.highestStreak} · " +
                                        "%.0f%% accurate · ".format(user.accuracy) +
                                        "${user.totalNumbersCounted} total"
                                )
                            },
                            trailingContent = {
                                Avatar(
                                    url = user.avatarUrl,
                                    contentDescription = user.username,
                                    size = 32,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            "management" -> {
                if (channelId == null) {
                    SectionCard { EmptyState("Select a channel to manage it.") }
                } else {
                    ManagementSection(
                        state = state,
                        channelId = channelId,
                        viewModel = viewModel,
                        onRestoreSave = { pendingRestoreSave = it },
                        onDeleteSave = { pendingDeleteSave = it },
                        onPurge = { pendingPurge = true },
                        purgeReason = purgeReason,
                        onPurgeReasonChange = { purgeReason = it },
                    )
                }
            }
        }
    }

    if (showSetup) {
        var channelPick by remember { mutableStateOf<String?>(null) }
        var startNumber by remember { mutableStateOf("0") }
        var increment by remember { mutableIntStateOf(1) }
        FormSheet(
            title = "Add counting channel",
            confirmLabel = "Add",
            confirmEnabled = channelPick != null,
            onConfirm = {
                channelPick?.let {
                    viewModel.setup(it, startNumber.toIntOrNull() ?: 0, increment)
                }
                showSetup = false
            },
            onDismiss = { showSetup = false },
        ) {
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a channel",
                label = "Channel",
                selectedId = channelPick,
                onSelect = { channelPick = it },
            )
            MewdekoTextField(
                value = startNumber,
                onValueChange = { startNumber = it.filter(Char::isDigit) },
                label = "Start at",
                numeric = true,
            )
            SliderRow(
                label = "Increment",
                value = increment.toFloat(),
                onValueChange = { increment = it.toInt().coerceAtLeast(1) },
                valueRange = 1f..10f,
                valueLabel = "$increment",
            )
        }
    }

    if (showReset) {
        var newNumber by remember { mutableStateOf("0") }
        var reason by remember { mutableStateOf("") }
        FormSheet(
            title = "Reset count",
            confirmLabel = "Reset",
            confirmEnabled = true,
            onConfirm = {
                state.selectedChannelId?.let {
                    viewModel.reset(
                        it,
                        newNumber.toIntOrNull() ?: 0,
                        reason.takeIf { value -> value.isNotBlank() },
                    )
                }
                showReset = false
            },
            onDismiss = { showReset = false },
        ) {
            MewdekoTextField(
                value = newNumber,
                onValueChange = { newNumber = it.filter(Char::isDigit) },
                label = "New number",
                numeric = true,
            )
            MewdekoTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Reason (optional)",
            )
        }
    }

    if (showSavePoint) {
        var reason by remember { mutableStateOf("") }
        FormSheet(
            title = "Create save point",
            confirmLabel = "Save",
            confirmEnabled = true,
            onConfirm = {
                state.selectedChannelId?.let {
                    viewModel.createSavePoint(
                        it,
                        reason.takeIf { value -> value.isNotBlank() },
                    )
                }
                showSavePoint = false
            },
            onDismiss = { showSavePoint = false },
        ) {
            MewdekoTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Reason (optional)",
            )
        }
    }

    pendingRemove?.let { channel ->
        ConfirmDialog(
            title = "Remove counting channel?",
            message = "Counting stops in #${channel.channelName ?: channel.channelId} and its " +
                "progress is discarded.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(channel.channelId) },
            onDismiss = { pendingRemove = null },
        )
    }

    pendingRestoreSave?.let { save ->
        val channelId = state.selectedChannelId
        ConfirmDialog(
            title = "Restore save point?",
            message = "Restores the count to ${save.savedNumber}.",
            confirmLabel = "Restore",
            destructive = false,
            onConfirm = { channelId?.let { viewModel.restoreSavePoint(it, save.id) } },
            onDismiss = { pendingRestoreSave = null },
        )
    }

    pendingDeleteSave?.let { save ->
        val channelId = state.selectedChannelId
        ConfirmDialog(
            title = "Delete save point?",
            message = "Removes the save at number ${save.savedNumber}. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { channelId?.let { viewModel.deleteSavePoint(it, save.id) } },
            onDismiss = { pendingDeleteSave = null },
        )
    }

    if (pendingPurge) {
        val channelId = state.selectedChannelId
        ConfirmDialog(
            title = "Purge counting data?",
            message = "Wipes every count, streak, statistic, ban, and save point for this channel. " +
                "It cannot be undone.",
            confirmLabel = "Purge everything",
            onConfirm = {
                channelId?.let { viewModel.purge(it, purgeReason.takeIf { r -> r.isNotBlank() }) }
                purgeReason = ""
            },
            onDismiss = { pendingPurge = false },
        )
    }
}

@Composable
private fun ManagementSection(
    state: CountingState,
    channelId: Snowflake,
    viewModel: CountingViewModel,
    onRestoreSave: (CountingSavePoint) -> Unit,
    onDeleteSave: (CountingSavePoint) -> Unit,
    onPurge: () -> Unit,
    purgeReason: String,
    onPurgeReasonChange: (String) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Save points", Icons.Default.Save)
        if (state.savePoints.isEmpty()) {
            EmptyState("No save points yet.", icon = Icons.Default.Save)
        } else {
            state.savePoints.forEach { save ->
                ListItem(
                    headlineContent = { Text("Number ${save.savedNumber}") },
                    supportingContent = {
                        Text(
                            buildString {
                                save.savedByUsername?.let { append("by $it") }
                                save.savedAt?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append(it.relativeToNow())
                                }
                                save.reason?.takeIf { it.isNotBlank() }?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append(it)
                                }
                            }
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onRestoreSave(save) }) { Text("Restore") }
                            IconButton(onClick = { onDeleteSave(save) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete save point",
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

    SectionCard {
        SectionCardHeader("Milestones", Icons.Default.Flag)
        Text(
            text = "Numbers that trigger a celebration message when reached.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.milestones.isEmpty()) {
            Text(
                text = "Using the default milestones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.milestones.forEach { milestone ->
                    AssistChip(
                        onClick = { viewModel.removeMilestone(channelId, milestone) },
                        label = { Text("$milestone") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove milestone $milestone",
                                modifier = Modifier.width(16.dp),
                            )
                        },
                    )
                }
            }
        }
        var newMilestone by remember { mutableStateOf("") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MewdekoTextField(
                value = newMilestone,
                onValueChange = { newMilestone = it.filter(Char::isDigit) },
                label = "Add a milestone",
                numeric = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    newMilestone.toIntOrNull()?.takeIf { it > 0 }?.let {
                        viewModel.addMilestone(channelId, it)
                        newMilestone = ""
                    }
                },
                enabled = newMilestone.toIntOrNull()?.let { it > 0 } == true,
            ) { Text("Add") }
        }

        var milestoneMessage by remember(state.config?.milestoneMessage) {
            mutableStateOf(state.config?.milestoneMessage.orEmpty())
        }
        MewdekoTextField(
            value = milestoneMessage,
            onValueChange = { milestoneMessage = it },
            label = "Milestone message",
            placeholder = "🎉 %user% reached %number%!",
            supportingText = "Supports %user%, %number%, and %channel%.",
        )
        Button(
            onClick = { viewModel.setMilestoneMessage(channelId, milestoneMessage) },
            enabled = milestoneMessage.isNotBlank(),
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Text("  Save message")
        }
    }

    SectionCard {
        SectionCardHeader("Counting bans", Icons.Default.Block)
        Text(
            text = "Banned members can still chat but their numbers are ignored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var banUserId by remember { mutableStateOf("") }
        var banReason by remember { mutableStateOf("") }
        var banDuration by remember { mutableStateOf("") }
        MewdekoTextField(
            value = banUserId,
            onValueChange = { banUserId = it.filter(Char::isDigit) },
            label = "User ID",
            numeric = true,
        )
        MewdekoTextField(
            value = banReason,
            onValueChange = { banReason = it },
            label = "Reason (optional)",
        )
        MewdekoTextField(
            value = banDuration,
            onValueChange = { banDuration = it.filter(Char::isDigit) },
            label = "Duration in minutes (optional)",
            numeric = true,
            supportingText = "Leave empty for a permanent ban",
        )
        Button(
            onClick = {
                viewModel.banUser(
                    channelId,
                    banUserId,
                    banReason.takeIf { it.isNotBlank() },
                    banDuration.toIntOrNull(),
                )
                banUserId = ""
                banReason = ""
                banDuration = ""
            },
            enabled = banUserId.length in 15..22,
        ) {
            Icon(Icons.Default.Block, contentDescription = null)
            Text("  Ban")
        }

        if (state.bans.isEmpty()) {
            EmptyState("Nobody is banned from counting here.", icon = Icons.Default.Block)
        } else {
            state.bans.forEach { ban ->
                ListItem(
                    leadingContent = { Avatar(ban.avatarUrl, ban.username, size = 36) },
                    headlineContent = { Text(ban.username ?: "User ${ban.userId}") },
                    supportingContent = {
                        Text(
                            buildString {
                                append(ban.reason?.takeIf { it.isNotBlank() } ?: "No reason provided")
                                append(" · by ")
                                append(ban.bannedByUsername ?: ban.bannedBy)
                                ban.expiresAt?.let {
                                    append(" · until ")
                                    append(it.relativeToNow())
                                } ?: append(" · permanent")
                            }
                        )
                    },
                    trailingContent = {
                        TextButton(onClick = { viewModel.unbanUser(channelId, ban.userId) }) {
                            Text("Unban")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    SectionCard {
        SectionCardHeader("Purge channel data", Icons.Default.DeleteForever, tint = MaterialTheme.colorScheme.error)
        Text(
            text = "Deletes every count, statistic, streak, ban, and save point for this channel. " +
                "The channel stays configured but starts from scratch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(
            value = purgeReason,
            onValueChange = onPurgeReasonChange,
            label = "Reason (optional)",
        )
        Button(
            onClick = onPurge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null)
            Text("  Purge everything")
        }
    }
}
