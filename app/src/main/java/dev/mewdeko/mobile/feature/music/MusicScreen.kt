package dev.mewdeko.mobile.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.PlayerState
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
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("player", "Player", Icons.Default.MusicNote),
    SectionTab("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    SectionTab("search", "Search", Icons.Default.Search),
    SectionTab("tts", "TTS", Icons.Default.RecordVoiceOver),
)

private val Filters = listOf("nightcore", "bassboost", "vaporwave", "karaoke", "8d", "tremolo")

/** The Lavalink-backed music player. */
@Composable
fun MusicScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAddTtsChannel by remember { mutableStateOf(false) }
    var pendingClearQueue by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Music",
        subtitle = state.player?.channelName?.let { "In $it" }
            ?: guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "queue" -> SectionCard {
                SectionCardHeader(
                    title = "Queue",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    trailing = {
                        if (state.queue.isNotEmpty()) {
                            IconButton(onClick = { pendingClearQueue = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = "Clear queue",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    },
                )
                if (state.queue.isEmpty()) {
                    EmptyState("The queue is empty.", icon = Icons.AutoMirrored.Filled.QueueMusic)
                } else {
                    state.queue.forEachIndexed { index, entry ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = entry.track.artworkUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                )
                            },
                            headlineContent = {
                                Text(
                                    text = entry.track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = listOfNotNull(
                                        entry.track.author,
                                        entry.track.duration,
                                        entry.requester?.username?.let { "by $it" },
                                    ).joinToString(" · "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(
                                        onClick = {
                                            viewModel.playTrack(entry.index ?: index)
                                        },
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.removeFromQueue(entry.index ?: index)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
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

            "search" -> {
                SectionCard {
                    SearchField(
                        value = state.searchQuery,
                        onValueChange = viewModel::search,
                        placeholder = "Search or paste a URL",
                    )
                    if (state.searchQuery.isNotBlank()) {
                        OutlinedButton(
                            onClick = { viewModel.play(state.searchQuery) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Queue \"${state.searchQuery.take(40)}\"") }
                    }
                }
                SectionCard {
                    SectionCardHeader("Results", Icons.Default.Search)
                    if (state.searchResults.isEmpty()) {
                        EmptyState(
                            message = if (state.isSearching) "Searching…"
                            else "Type to search for a track.",
                            icon = Icons.Default.Search,
                        )
                    } else {
                        state.searchResults.forEach { result ->
                            ListItem(
                                leadingContent = {
                                    AsyncImage(
                                        model = result.artworkUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = result.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = listOfNotNull(
                                            result.author,
                                            result.duration,
                                            result.sourceName,
                                        ).joinToString(" · "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = { result.uri?.let { viewModel.play(it) } },
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Queue")
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            "tts" -> {
                SectionCard {
                    SectionCardHeader("Text to speech", Icons.Default.RecordVoiceOver)
                    SliderRow(
                        label = "Volume",
                        value = state.tts.ttsVolume.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..100f,
                        valueLabel = "${state.tts.ttsVolume}%",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(25, 50, 75, 100).forEach { volume ->
                            TextButton(
                                onClick = {
                                    viewModel.saveTtsSettings(state.tts.copy(ttsVolume = volume))
                                },
                            ) { Text("$volume%") }
                        }
                    }
                    SliderRow(
                        label = "Speed",
                        value = state.tts.ttsSpeed.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0.5f..2f,
                        valueLabel = "%.1fx".format(state.tts.ttsSpeed),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.75, 1.0, 1.25, 1.5).forEach { speed ->
                            TextButton(
                                onClick = {
                                    viewModel.saveTtsSettings(state.tts.copy(ttsSpeed = speed))
                                },
                            ) { Text("%.2fx".format(speed)) }
                        }
                    }
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.RecordVoiceOver),
                        options = state.ttsVoices.map {
                            SelectorOption(it.name, it.name, it.language)
                        },
                        placeholder = "Default voice",
                        label = "Default voice",
                        selectedId = state.tts.ttsDefaultVoice.takeIf { it.isNotBlank() },
                        onSelect = { voice ->
                            viewModel.saveTtsSettings(
                                state.tts.copy(ttsDefaultVoice = voice.orEmpty())
                            )
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "Anyone can use TTS",
                        label = "Required role",
                        selectedId = state.tts.ttsRoleId,
                        onSelect = { role ->
                            viewModel.saveTtsSettings(state.tts.copy(ttsRoleId = role))
                        },
                    )
                    SwitchRow(
                        title = "Read reply context",
                        checked = state.tts.ttsReplyContext,
                        onCheckedChange = { value ->
                            viewModel.saveTtsSettings(state.tts.copy(ttsReplyContext = value))
                        },
                    )
                    SwitchRow(
                        title = "Narrate attachments",
                        checked = state.tts.ttsAttachmentNarration,
                        onCheckedChange = { value ->
                            viewModel.saveTtsSettings(
                                state.tts.copy(ttsAttachmentNarration = value)
                            )
                        },
                    )
                    SwitchRow(
                        title = "Group consecutive messages",
                        checked = state.tts.ttsConsecutiveGrouping,
                        onCheckedChange = { value ->
                            viewModel.saveTtsSettings(
                                state.tts.copy(ttsConsecutiveGrouping = value)
                            )
                        },
                    )
                    SliderRow(
                        label = "Max queue size",
                        value = state.tts.ttsMaxQueueSize.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 1f..50f,
                        valueLabel = "${state.tts.ttsMaxQueueSize}",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5, 10, 25, 50).forEach { size ->
                            TextButton(
                                onClick = {
                                    viewModel.saveTtsSettings(
                                        state.tts.copy(ttsMaxQueueSize = size)
                                    )
                                },
                            ) { Text("$size") }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader(
                        title = "TTS channels",
                        icon = Icons.Default.VolumeUp,
                        trailing = {
                            IconButton(onClick = { showAddTtsChannel = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add TTS channel")
                            }
                        },
                    )
                    if (state.tts.voiceChannels.isEmpty()) {
                        EmptyState("No voice channels wired up for TTS.")
                    } else {
                        state.tts.voiceChannels.forEach { entry ->
                            val name = state.voiceChannels
                                .firstOrNull { it.id == entry.voiceChannelId }
                                ?.name
                                ?: entry.voiceChannelId.orEmpty()
                            ListItem(
                                headlineContent = { Text(name) },
                                supportingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TagChip(if (entry.enabled) "Enabled" else "Disabled")
                                        if (entry.announceJoinLeave) TagChip("Announces joins")
                                    }
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            entry.voiceChannelId?.let {
                                                viewModel.removeTtsChannel(it)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                if (state.ttsBlocked.isNotEmpty()) {
                    SectionCard {
                        SectionCardHeader("Blocked members", Icons.Default.RecordVoiceOver)
                        state.ttsBlocked.forEach { blocked ->
                            ListItem(
                                headlineContent = { Text(blocked.userId.orEmpty()) },
                                supportingContent = blocked.voice?.let { { Text("Voice: $it") } },
                                trailingContent = {
                                    TextButton(
                                        onClick = {
                                            blocked.userId?.let {
                                                viewModel.setTtsBlocked(it, !blocked.isBlocked)
                                            }
                                        },
                                    ) { Text(if (blocked.isBlocked) "Unblock" else "Block") }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            else -> {
                val player = state.player
                val track = player?.currentTrack?.track

                SectionCard {
                    if (track == null) {
                        EmptyState("Nothing is playing.", icon = Icons.Default.MusicNote)
                    } else {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.large),
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.author.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = player.position?.displayValue.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = track.duration.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = viewModel::previous) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        FilledIconButton(
                            onClick = viewModel::togglePlayPause,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = if (player?.state == PlayerState.PLAYING) {
                                    Icons.Default.Pause
                                } else {
                                    Icons.Default.PlayArrow
                                },
                                contentDescription = "Play or pause",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        IconButton(onClick = viewModel::skip) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Skip",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = viewModel::shuffle) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle")
                        }
                        IconButton(
                            onClick = {
                                viewModel.setRepeat((state.settings.playerRepeat + 1) % 3)
                            },
                        ) {
                            Icon(
                                imageVector = if (state.settings.playerRepeat == 1) {
                                    Icons.Default.RepeatOne
                                } else {
                                    Icons.Default.Repeat
                                },
                                contentDescription = "Repeat mode",
                                tint = if (state.settings.playerRepeat == 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        if (state.isLive) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(8.dp),
                                ) {}
                                Text(
                                    text = "Live",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Volume", Icons.Default.VolumeUp)
                    SliderRow(
                        label = "Player volume",
                        value = (player?.volume?.toFloat() ?: state.settings.volume.toFloat()),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..100f,
                        valueLabel = "${player?.volume?.toInt() ?: state.settings.volume}%",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(10, 25, 50, 75, 100).forEach { volume ->
                            TextButton(onClick = { viewModel.setVolume(volume) }) {
                                Text("$volume%")
                            }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Filters", Icons.Default.Tune)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Filters.chunked(3).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { name ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { viewModel.setFilter(name, true) },
                                                label = { Text(name) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { Filters.forEach { viewModel.setFilter(it, false) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear all filters") }
                }

                SectionCard {
                    SectionCardHeader("Player defaults", Icons.Default.Tune)
                    SliderRow(
                        label = "Auto-disconnect after",
                        value = state.settings.autoDisconnect.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..3f,
                        valueLabel = when (state.settings.autoDisconnect) {
                            0 -> "Never"
                            1 -> "Queue end"
                            2 -> "Voice empty"
                            else -> "Either"
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            0 to "Never",
                            1 to "Queue end",
                            2 to "Voice empty",
                            3 to "Either",
                        ).forEach { (value, label) ->
                            TextButton(
                                onClick = {
                                    viewModel.saveSettings(
                                        state.settings.copy(autoDisconnect = value)
                                    )
                                },
                            ) { Text(label) }
                        }
                    }
                    SwitchRow(
                        title = "Autoplay",
                        subtitle = "Keep playing related tracks when the queue runs out",
                        checked = state.settings.autoPlay > 0,
                        onCheckedChange = { value ->
                            viewModel.saveSettings(
                                state.settings.copy(autoPlay = if (value) 1 else 0)
                            )
                        },
                    )
                }
            }
        }
    }

    if (showAddTtsChannel) {
        var voiceChannelId by remember { mutableStateOf<String?>(null) }
        var textChannelId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddTtsChannel = false },
            title = { Text("Add TTS channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.VolumeUp),
                        options = state.voiceChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a voice channel",
                        label = "Voice channel",
                        selectedId = voiceChannelId,
                        onSelect = { voiceChannelId = it },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.textChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No linked text channel",
                        label = "Read messages from",
                        selectedId = textChannelId,
                        onSelect = { textChannelId = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        voiceChannelId?.let { viewModel.addTtsChannel(it, textChannelId) }
                        showAddTtsChannel = false
                    },
                    enabled = voiceChannelId != null,
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTtsChannel = false }) { Text("Cancel") }
            },
        )
    }

    if (pendingClearQueue) {
        ConfirmDialog(
            title = "Clear the queue?",
            message = "Every queued track is removed. The current track keeps playing.",
            confirmLabel = "Clear",
            onConfirm = viewModel::clearQueue,
            onDismiss = { pendingClearQueue = false },
        )
    }
}
