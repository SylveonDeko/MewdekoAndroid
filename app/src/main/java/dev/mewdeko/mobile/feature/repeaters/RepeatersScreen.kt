package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
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
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

/** Recurring and sticky messages. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatersScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RepeatersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RepeaterEntry?>(null) }

    FeatureScaffold(
        title = "Repeaters",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New repeater") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Repeat)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Repeaters", "${state.repeaters.size}", Modifier.weight(1f))
                StatTile("Running", "${state.activeCount}", Modifier.weight(1f))
                StatTile("Posts", "${state.totalPosts}", Modifier.weight(1f))
            }
        }

        if (state.repeaters.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No repeaters configured yet.",
                    icon = Icons.Default.Repeat,
                )
            }
        } else {
            state.repeaters.forEach { repeater ->
                key(repeater.id) {
                    SectionCard {
                        SectionCardHeader(
                            title = "#${state.channelName(repeater.channelId)}",
                            icon = Icons.Default.Tag,
                            trailing = {
                                Row {
                                    IconButton(onClick = { viewModel.triggerNow(repeater.id) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Post now")
                                    }
                                    IconButton(onClick = { pendingDelete = repeater }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete repeater",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TagChip(repeater.trigger.label)
                            TagChip("Every ${repeater.interval}")
                            TagChip("Priority ${repeater.priority}")
                            TagChip("${repeater.displayCount} posts")
                            repeater.nextExecution?.let { TagChip("Next ${it.relativeToNow()}") }
                            repeater.lastDisplayed?.let { TagChip("Last ${it.relativeToNow()}") }
                        }
                        SwitchRow(
                            title = "Enabled",
                            checked = repeater.isEnabled,
                            onCheckedChange = { viewModel.update(repeater.id, isEnabled = it) },
                        )
                        SwitchRow(
                            title = "Skip if unchanged",
                            subtitle = "Do not repost when the message is still the newest",
                            checked = repeater.noRedundant,
                            onCheckedChange = { viewModel.update(repeater.id, noRedundant = it) },
                        )
                        SwitchRow(
                            title = "Silent",
                            subtitle = "Post without triggering notifications",
                            checked = repeater.suppressNotifications,
                            onCheckedChange = {
                                viewModel.update(repeater.id, suppressNotifications = it)
                            },
                        )
                        SliderRow(
                            label = "Priority",
                            value = repeater.priority.toFloat(),
                            onValueChange = { },
                            onValueChangeFinished = { },
                            valueRange = 0f..100f,
                            valueLabel = "${repeater.priority}",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(10, 50, 90).forEach { value ->
                                TextButton(
                                    onClick = { viewModel.update(repeater.id, priority = value) },
                                ) { Text("$value") }
                            }
                        }
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Repeat),
                            options = StickyTriggerMode.entries.map {
                                SelectorOption(it.raw.toString(), it.label, it.blurb)
                            },
                            placeholder = "Interval",
                            label = "Trigger mode",
                            selectedId = repeater.triggerMode.toString(),
                            onSelect = {
                                viewModel.update(repeater.id, triggerMode = it?.toIntOrNull() ?: 0)
                            },
                        )
                        EmbedMessageEditor(
                            message = EmbedMessage.parse(repeater.message),
                            onMessageChange = { viewModel.setMessage(repeater.id, it) },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateRepeaterDialog(
            channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) },
            onDismiss = { showCreate = false },
            onCreate = { channelId, message, minutes, priority, mode, silent ->
                viewModel.create(
                    channelId = channelId,
                    message = message,
                    interval = "%02d:%02d:00".format(minutes / 60, minutes % 60),
                    priority = priority,
                    triggerMode = mode,
                    suppressNotifications = silent,
                )
                showCreate = false
            },
        )
    }

    pendingDelete?.let { repeater ->
        ConfirmDialog(
            title = "Delete repeater?",
            message = "The repeater in #${state.channelName(repeater.channelId)} stops posting.",
            onConfirm = { viewModel.remove(repeater.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun CreateRepeaterDialog(
    channelOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onCreate: (
        channelId: String,
        message: String,
        intervalMinutes: Int,
        priority: Int,
        mode: StickyTriggerMode,
        silent: Boolean,
    ) -> Unit,
) {
    var channelId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var minutes by remember { mutableIntStateOf(60) }
    var priority by remember { mutableIntStateOf(50) }
    var mode by remember { mutableStateOf(StickyTriggerMode.TIME_INTERVAL) }
    var silent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New repeater") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Pick a channel",
                    label = "Post in",
                    selectedId = channelId,
                    onSelect = { channelId = it },
                )
                MewdekoTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = "Message",
                    singleLine = false,
                    minLines = 3,
                )
                SliderRow(
                    label = "Interval",
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceAtLeast(1) },
                    valueRange = 1f..1440f,
                    valueLabel = if (minutes < 60) "${minutes}m"
                    else "${minutes / 60}h ${minutes % 60}m",
                )
                SliderRow(
                    label = "Priority",
                    value = priority.toFloat(),
                    onValueChange = { priority = it.toInt() },
                    valueRange = 0f..100f,
                    valueLabel = "$priority",
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Repeat),
                    options = StickyTriggerMode.entries.map {
                        SelectorOption(it.raw.toString(), it.label, it.blurb)
                    },
                    placeholder = "Interval",
                    label = "Trigger mode",
                    selectedId = mode.raw.toString(),
                    onSelect = { mode = StickyTriggerMode.from(it?.toIntOrNull() ?: 0) },
                )
                SwitchRow(
                    title = "Silent",
                    subtitle = "Post without triggering notifications",
                    checked = silent,
                    onCheckedChange = { silent = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    channelId?.let { onCreate(it, message, minutes, priority, mode, silent) }
                },
                enabled = channelId != null && message.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
