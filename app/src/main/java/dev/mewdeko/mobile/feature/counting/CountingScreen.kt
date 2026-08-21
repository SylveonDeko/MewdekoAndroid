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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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

private val Tabs = listOf(
    SectionTab("channels", "Channels", Icons.Default.Numbers),
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
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
            ExtendedFloatingActionButton(
                onClick = { showSetup = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add channel") },
            )
        },
    ) {
        if (state.channels.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No counting channels yet. Add one to start the game.",
                    icon = Icons.Default.Numbers,
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

        when (state.section) {
            "settings" -> {
                val config = state.config
                val channelId = state.selectedChannelId
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
                        SliderRow(
                            label = "Cooldown",
                            value = config.cooldown.toFloat(),
                            onValueChange = { },
                            onValueChangeFinished = { },
                            valueRange = 0f..300f,
                            valueLabel = if (config.cooldown == 0) "None" else "${config.cooldown}s",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 5, 15, 60).forEach { seconds ->
                                TextButton(
                                    onClick = {
                                        viewModel.updateConfig(channelId) {
                                            it.copy(cooldown = seconds)
                                        }
                                    },
                                ) { Text(if (seconds == 0) "None" else "${seconds}s") }
                            }
                        }
                        SliderRow(
                            label = "Maximum number",
                            value = config.maxNumber.toFloat(),
                            onValueChange = { },
                            onValueChangeFinished = { },
                            valueRange = 0f..100000f,
                            valueLabel = if (config.maxNumber == 0) "Unlimited"
                            else "${config.maxNumber}",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(0, 1000, 10000, 100000).forEach { max ->
                                TextButton(
                                    onClick = {
                                        viewModel.updateConfig(channelId) { it.copy(maxNumber = max) }
                                    },
                                ) { Text(if (max == 0) "None" else "$max") }
                            }
                        }
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

                    if (state.savePoints.isNotEmpty()) {
                        SectionCard {
                            SectionCardHeader("Save points", Icons.Default.Save)
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
                                        TextButton(
                                            onClick = {
                                                viewModel.restoreSavePoint(channelId, save.id)
                                            },
                                        ) { Text("Restore") }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
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
                                        "streak ${user.currentStreak} · " +
                                        "%.0f%% accurate".format(user.accuracy)
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
        }
    }

    if (showSetup) {
        var channelId by remember { mutableStateOf<String?>(null) }
        var startNumber by remember { mutableStateOf("0") }
        var increment by remember { mutableIntStateOf(1) }
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Add counting channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a channel",
                        label = "Channel",
                        selectedId = channelId,
                        onSelect = { channelId = it },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        channelId?.let {
                            viewModel.setup(it, startNumber.toIntOrNull() ?: 0, increment)
                        }
                        showSetup = false
                    },
                    enabled = channelId != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showSetup = false }) { Text("Cancel") } },
        )
    }

    if (showReset) {
        var newNumber by remember { mutableStateOf("0") }
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Reset count") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.selectedChannelId?.let {
                            viewModel.reset(
                                it,
                                newNumber.toIntOrNull() ?: 0,
                                reason.takeIf { value -> value.isNotBlank() },
                            )
                        }
                        showReset = false
                    },
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showReset = false }) { Text("Cancel") } },
        )
    }

    if (showSavePoint) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePoint = false },
            title = { Text("Create save point") },
            text = {
                MewdekoTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason (optional)",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        state.selectedChannelId?.let {
                            viewModel.createSavePoint(
                                it,
                                reason.takeIf { value -> value.isNotBlank() },
                            )
                        }
                        showSavePoint = false
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSavePoint = false }) { Text("Cancel") } },
        )
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
}
