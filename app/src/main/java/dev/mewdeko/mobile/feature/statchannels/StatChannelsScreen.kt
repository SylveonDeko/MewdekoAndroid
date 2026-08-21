package dev.mewdeko.mobile.feature.statchannels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Voice channels whose names carry live server statistics. */
@Composable
fun StatChannelsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StatChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StatChannel?>(null) }
    var editingTemplate by remember { mutableStateOf<StatChannel?>(null) }

    FeatureScaffold(
        title = "Stat Channels",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add channel") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Equalizer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Stat channels", "${state.channels.size}", Modifier.weight(1f))
                StatTile(
                    label = "Voice channels",
                    value = "${state.availableVoiceChannels.size}",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "The bot renames each channel on a schedule. Discord rate-limits channel " +
                    "renames, so updates can lag by a few minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.channels.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No stat channels configured yet.",
                    icon = Icons.Default.Equalizer,
                )
            }
        } else {
            state.channels.forEach { channel ->
                SectionCard {
                    SectionCardHeader(
                        title = channel.channelName,
                        icon = channel.type.icon,
                        trailing = {
                            IconButton(onClick = { pendingDelete = channel }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove stat channel",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip(channel.typeName ?: channel.type.label)
                        channel.currentValue?.let { TagChip("Now: $it") }
                        channel.roleName?.let { TagChip("@$it") }
                        channel.goalTarget?.let { TagChip("Goal $it") }
                        channel.countdownDate?.let { TagChip(it.shortDate()) }
                    }
                    channel.template?.takeIf { it.isNotBlank() }?.let { template ->
                        Text(
                            text = template,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { editingTemplate = channel }) { Text("Edit template") }
                }
            }
        }
    }

    if (showAdd) {
        AddStatChannelDialog(
            voiceOptions = state.availableVoiceChannels.map { SelectorOption(it.id, it.name) },
            roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) },
            onDismiss = { showAdd = false },
            onAdd = { channelId, type, template, roleId, days, goal ->
                viewModel.add(
                    channelId = channelId,
                    type = type,
                    template = template,
                    roleId = roleId,
                    countdownDate = if (type.requiresCountdown) {
                        Instant.now().plus(days.toLong(), ChronoUnit.DAYS)
                    } else {
                        null
                    },
                    goalTarget = goal,
                )
                showAdd = false
            },
        )
    }

    editingTemplate?.let { channel ->
        var draft by remember(channel.channelId) { mutableStateOf(channel.template.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            title = { Text("Channel name template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = "Template",
                        placeholder = "Members: %count%",
                    )
                    Text(
                        text = "Use %count% for the current value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTemplate(channel.channelId, draft)
                        editingTemplate = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplate = null }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { channel ->
        ConfirmDialog(
            title = "Remove stat channel?",
            message = "${channel.channelName} stops being renamed. The channel itself is kept.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(channel.channelId) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun AddStatChannelDialog(
    voiceOptions: List<SelectorOption>,
    roleOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onAdd: (
        channelId: String,
        type: StatChannelType,
        template: String,
        roleId: String?,
        days: Int,
        goal: Int,
    ) -> Unit,
) {
    var channelId by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf(StatChannelType.TOTAL_MEMBERS) }
    var template by remember { mutableStateOf("Members: %count%") }
    var roleId by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableIntStateOf(30) }
    var goal by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stat channel") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.VolumeUp),
                    options = voiceOptions,
                    placeholder = "Pick a voice channel",
                    label = "Channel",
                    selectedId = channelId,
                    onSelect = { channelId = it },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Equalizer),
                    options = StatChannelType.entries.map {
                        SelectorOption(it.raw.toString(), it.label)
                    },
                    placeholder = "Pick a statistic",
                    label = "Displays",
                    selectedId = type.raw.toString(),
                    onSelect = { raw ->
                        type = StatChannelType.from(raw?.toIntOrNull() ?: 0)
                    },
                )
                MewdekoTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = "Name template",
                    supportingText = "Use %count% for the current value.",
                )
                if (type.requiresRole) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "Pick a role",
                        label = "Count members with role",
                        selectedId = roleId,
                        onSelect = { roleId = it },
                    )
                }
                if (type.requiresCountdown) {
                    SliderRow(
                        label = "Counts down over",
                        value = days.toFloat(),
                        onValueChange = { days = it.toInt().coerceAtLeast(1) },
                        valueRange = 1f..365f,
                        valueLabel = "${days}d",
                    )
                }
                if (type.requiresGoal) {
                    MewdekoTextField(
                        value = goal,
                        onValueChange = { goal = it.filter(Char::isDigit) },
                        label = "Member goal",
                        numeric = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    channelId?.let {
                        onAdd(it, type, template, roleId, days, goal.toIntOrNull() ?: 0)
                    }
                },
                enabled = channelId != null &&
                    template.isNotBlank() &&
                    (!type.requiresRole || roleId != null),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
