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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.PlayerState
import dev.mewdeko.mobile.core.model.Snowflake
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
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("player", "Player", Icons.Default.MusicNote),
    SectionTab("queue", "Queue", Icons.AutoMirrored.Filled.QueueMusic),
    SectionTab("search", "Search", Icons.Default.Search),
    SectionTab("tts", "TTS", Icons.Default.RecordVoiceOver),
)

/** The eight Lavalink audio filters, keyed by their API name. */
private val Filters = listOf(
    "bassboost" to "Bass boost",
    "nightcore" to "Nightcore",
    "vaporwave" to "Vaporwave",
    "karaoke" to "Karaoke",
    "tremolo" to "Tremolo",
    "vibrato" to "Vibrato",
    "rotation" to "8D rotation",
    "distortion" to "Distortion",
)

/** The synthetic option id used to clear a channel or role selection. */
private const val NoneId = "0"

private val RepeatModeOptions = listOf(
    SelectorOption("0", "Off"),
    SelectorOption("1", "Single track"),
    SelectorOption("2", "Queue"),
)

private val AutoDisconnectOptions = listOf(
    SelectorOption("0", "Never"),
    SelectorOption("1", "When voice empty"),
    SelectorOption("2", "When queue empty"),
    SelectorOption("3", "Either"),
)

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
    var showAddLinkChannel by remember { mutableStateOf(false) }
    var pendingRemoveTtsChannel by remember { mutableStateOf<Snowflake?>(null) }
    var pendingRemoveLinkChannel by remember { mutableStateOf<Snowflake?>(null) }

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
                var voiceQuery by remember { mutableStateOf("") }

                SectionCard {
                    SectionCardHeader("Text to speech", Icons.Default.RecordVoiceOver)
                    CommittingSliderRow(
                        label = "Volume",
                        value = state.tts.ttsVolume.toFloat(),
                        valueRange = 0f..100f,
                        valueLabel = { "${it.toInt()}%" },
                        onCommit = {
                            viewModel.saveTtsSettings(state.tts.copy(ttsVolume = it.toInt()))
                        },
                    )
                    CommittingSliderRow(
                        label = "Speed",
                        value = state.tts.ttsSpeed.toFloat(),
                        valueRange = 0.5f..3f,
                        valueLabel = { "%.1fx".format(it) },
                        onCommit = {
                            viewModel.saveTtsSettings(state.tts.copy(ttsSpeed = it.toDouble()))
                        },
                    )
                    CommittingSliderRow(
                        label = "Max queue size",
                        value = state.tts.ttsMaxQueueSize.toFloat(),
                        valueRange = 1f..50f,
                        valueLabel = { "${it.toInt()}" },
                        onCommit = {
                            viewModel.saveTtsSettings(state.tts.copy(ttsMaxQueueSize = it.toInt()))
                        },
                    )
                    BlurCommitTextField(
                        value = state.tts.ttsDefaultVoice,
                        label = "Default voice",
                        placeholder = "e.g. Brian (leave empty for default)",
                        supportingText = "Use the voice search below to find available voices.",
                        onCommit = {
                            viewModel.saveTtsSettings(state.tts.copy(ttsDefaultVoice = it))
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = listOf(SelectorOption(NoneId, "Anyone can use TTS")) +
                            state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "Anyone can use TTS",
                        label = "Required role",
                        selectedId = state.tts.ttsRoleId ?: NoneId,
                        onSelect = { role ->
                            viewModel.saveTtsSettings(
                                state.tts.copy(ttsRoleId = role?.takeIf { it != NoneId })
                            )
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
                }

                SectionCard {
                    SectionCardHeader("Voice search", Icons.Default.Search)
                    SearchField(
                        value = voiceQuery,
                        onValueChange = { voiceQuery = it },
                        placeholder = "Search voices (e.g. Brian, English)",
                    )
                    if (voiceQuery.isNotBlank()) {
                        OutlinedButton(
                            onClick = { viewModel.searchTtsVoices(voiceQuery) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.isSearchingVoices) "Searching…" else "Search") }
                    }
                    if (state.ttsVoiceResults.isNotEmpty()) {
                        state.ttsVoiceResults.forEach { voice ->
                            ListItem(
                                headlineContent = { Text(voice.name) },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(voice.source, voice.language)
                                            .joinToString(" · "),
                                    )
                                },
                                trailingContent = {
                                    TextButton(
                                        onClick = {
                                            viewModel.saveTtsSettings(
                                                state.tts.copy(ttsDefaultVoice = voice.name)
                                            )
                                        },
                                    ) { Text("Use") }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
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
                            TtsChannelRow(
                                entry = entry,
                                voiceChannelName = state.voiceChannels
                                    .firstOrNull { it.id == entry.voiceChannelId }
                                    ?.name
                                    ?: entry.voiceChannelId.orEmpty(),
                                textChannels = state.textChannels,
                                onChange = { updated -> viewModel.upsertTtsChannel(updated) },
                                onRemove = { pendingRemoveTtsChannel = entry.voiceChannelId },
                            )
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Blocked TTS users", Icons.Default.Block)
                    var blockUserId by remember { mutableStateOf("") }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MewdekoTextField(
                                value = blockUserId,
                                onValueChange = { blockUserId = it },
                                label = "User ID",
                                placeholder = "Enter a Discord user ID",
                                numeric = true,
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.setTtsBlocked(blockUserId.trim(), true)
                                blockUserId = ""
                            },
                            enabled = blockUserId.isNotBlank(),
                        ) { Text("Block") }
                    }
                    if (state.ttsBlocked.isEmpty()) {
                        EmptyState("No members are blocked from TTS.")
                    } else {
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
                                viewModel.setRepeat((state.effectiveRepeatMode + 1) % 3)
                            },
                        ) {
                            Icon(
                                imageVector = if (state.effectiveRepeatMode == 1) {
                                    Icons.Default.RepeatOne
                                } else {
                                    Icons.Default.Repeat
                                },
                                contentDescription = "Repeat mode",
                                tint = if (state.effectiveRepeatMode == 0) {
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
                    CommittingSliderRow(
                        label = "Player volume",
                        value = player?.volume?.toFloat() ?: state.settings.volume.toFloat(),
                        valueRange = 0f..100f,
                        valueLabel = { "${it.toInt()}%" },
                        onCommit = { viewModel.setVolume(it.toInt()) },
                    )
                }

                SectionCard {
                    SectionCardHeader("Filters", Icons.Default.Tune)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Filters.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { (key, label) ->
                                            val active = state.filters.isActive(key)
                                            FilterChip(
                                                selected = active,
                                                onClick = { viewModel.setFilter(key, !active) },
                                                label = { Text(label) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { Filters.forEach { (key, _) -> viewModel.setFilter(key, false) } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear all filters") }
                }

                SectionCard {
                    SectionCardHeader("General settings", Icons.Default.Tune)
                    CommittingSliderRow(
                        label = "Default volume",
                        value = state.settings.volume.toFloat(),
                        valueRange = 0f..100f,
                        valueLabel = { "${it.toInt()}%" },
                        onCommit = {
                            viewModel.saveSettings(state.settings.copy(volume = it.toInt()))
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = listOf(SelectorOption(NoneId, "All channels")) +
                            state.textChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "All channels",
                        label = "Music channel",
                        selectedId = state.settings.musicChannelId ?: NoneId,
                        onSelect = { channel ->
                            viewModel.saveSettings(
                                state.settings.copy(musicChannelId = channel?.takeIf { it != NoneId })
                            )
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = listOf(SelectorOption(NoneId, "No DJ role")) +
                            state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "No DJ role",
                        label = "DJ role",
                        selectedId = state.settings.djRoleId ?: NoneId,
                        onSelect = { role ->
                            viewModel.saveSettings(
                                state.settings.copy(djRoleId = role?.takeIf { it != NoneId })
                            )
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Repeat),
                        options = RepeatModeOptions,
                        placeholder = "Select repeat mode",
                        label = "Default repeat mode",
                        selectedId = state.settings.playerRepeat.toString(),
                        onSelect = { mode ->
                            viewModel.saveSettings(
                                state.settings.copy(playerRepeat = mode?.toIntOrNull() ?: 0)
                            )
                        },
                    )
                }

                SectionCard {
                    SectionCardHeader("Advanced settings", Icons.Default.Tune)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Tune),
                        options = AutoDisconnectOptions,
                        placeholder = "Select auto disconnect",
                        label = "Auto disconnect",
                        selectedId = state.settings.autoDisconnect.toString(),
                        onSelect = { mode ->
                            viewModel.saveSettings(
                                state.settings.copy(autoDisconnect = mode?.toIntOrNull() ?: 0)
                            )
                        },
                    )
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
                    SwitchRow(
                        title = "Enable vote skip",
                        checked = state.settings.voteSkipEnabled,
                        onCheckedChange = { value ->
                            viewModel.saveSettings(state.settings.copy(voteSkipEnabled = value))
                        },
                    )
                    if (state.settings.voteSkipEnabled) {
                        CommittingSliderRow(
                            label = "Vote skip threshold",
                            value = state.settings.voteSkipThreshold.toFloat(),
                            valueRange = 1f..100f,
                            valueLabel = { "${it.toInt()}%" },
                            onCommit = {
                                viewModel.saveSettings(
                                    state.settings.copy(voteSkipThreshold = it.toInt())
                                )
                            },
                        )
                    }
                }

                SectionCard {
                    SectionCardHeader(
                        title = "Music link conversion",
                        icon = Icons.Default.Link,
                        trailing = {
                            IconButton(onClick = { showAddLinkChannel = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add channel")
                            }
                        },
                    )
                    Text(
                        text = "Apple Music, Spotify, and YouTube links posted in these channels " +
                            "are replaced with a cross-platform embed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.linkChannels.isEmpty()) {
                        EmptyState("No channels have music link conversion enabled.")
                    } else {
                        state.linkChannels.forEach { channelId ->
                            val name = state.textChannels.firstOrNull { it.id == channelId }?.name
                                ?: channelId
                            ListItem(
                                headlineContent = { Text(name) },
                                trailingContent = {
                                    IconButton(
                                        onClick = { pendingRemoveLinkChannel = channelId },
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
            }
        }
    }

    if (showAddTtsChannel) {
        var voiceChannelId by remember { mutableStateOf<String?>(null) }
        var textChannelId by remember { mutableStateOf<String?>(null) }
        var announceJoinLeave by remember { mutableStateOf(false) }
        var joinFormat by remember { mutableStateOf("") }
        var leaveFormat by remember { mutableStateOf("") }
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
                    SwitchRow(
                        title = "Announce join/leave",
                        checked = announceJoinLeave,
                        onCheckedChange = { announceJoinLeave = it },
                    )
                    if (announceJoinLeave) {
                        MewdekoTextField(
                            value = joinFormat,
                            onValueChange = { joinFormat = it },
                            label = "Join format",
                            placeholder = "%user.name% joined the channel",
                        )
                        MewdekoTextField(
                            value = leaveFormat,
                            onValueChange = { leaveFormat = it },
                            label = "Leave format",
                            placeholder = "%user.name% left the channel",
                        )
                        Text(
                            text = "Placeholders: %user.name% %user.mention% %user.id% " +
                                "%server.name% %server.members% %channel.name%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vc = voiceChannelId
                        if (vc != null) {
                            viewModel.upsertTtsChannel(
                                TtsVoiceChannelEntry(
                                    voiceChannelId = vc,
                                    enabled = true,
                                    linkedTextChannelId = textChannelId,
                                    announceJoinLeave = announceJoinLeave,
                                    joinFormat = joinFormat.takeIf { it.isNotBlank() },
                                    leaveFormat = leaveFormat.takeIf { it.isNotBlank() },
                                ),
                                reloadAfter = true,
                            )
                        }
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

    if (showAddLinkChannel) {
        var linkChannelId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddLinkChannel = false },
            title = { Text("Add link conversion channel") },
            text = {
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = state.textChannels.map { SelectorOption(it.id, it.name) },
                    placeholder = "Pick a channel",
                    label = "Channel",
                    selectedId = linkChannelId,
                    onSelect = { linkChannelId = it },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        linkChannelId?.let { viewModel.addLinkChannel(it) }
                        showAddLinkChannel = false
                    },
                    enabled = linkChannelId != null,
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLinkChannel = false }) { Text("Cancel") }
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

    pendingRemoveTtsChannel?.let { voiceChannelId ->
        ConfirmDialog(
            title = "Remove TTS channel?",
            message = "Text-to-speech will stop reading messages aloud in this voice channel.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeTtsChannel(voiceChannelId) },
            onDismiss = { pendingRemoveTtsChannel = null },
        )
    }

    pendingRemoveLinkChannel?.let { channelId ->
        ConfirmDialog(
            title = "Disable link conversion?",
            message = "Music links posted in this channel will no longer be converted.",
            confirmLabel = "Disable",
            onConfirm = { viewModel.removeLinkChannel(channelId) },
            onDismiss = { pendingRemoveLinkChannel = null },
        )
    }
}

/** One editable row in the TTS voice channel list. */
@Composable
private fun TtsChannelRow(
    entry: TtsVoiceChannelEntry,
    voiceChannelName: String,
    textChannels: List<dev.mewdeko.mobile.core.model.TextChannelLite>,
    onChange: (TtsVoiceChannelEntry) -> Unit,
    onRemove: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(voiceChannelName, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        SwitchRow(
            title = "Enabled",
            checked = entry.enabled,
            onCheckedChange = { onChange(entry.copy(enabled = it)) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = listOf(SelectorOption(NoneId, "VC text chat only")) +
                textChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "VC text chat only",
            label = "Linked text channel",
            selectedId = entry.linkedTextChannelId ?: NoneId,
            onSelect = { channel ->
                onChange(entry.copy(linkedTextChannelId = channel?.takeIf { it != NoneId }))
            },
        )
        SwitchRow(
            title = "Announce join/leave",
            checked = entry.announceJoinLeave,
            onCheckedChange = { onChange(entry.copy(announceJoinLeave = it)) },
        )
        if (entry.announceJoinLeave) {
            BlurCommitTextField(
                value = entry.joinFormat.orEmpty(),
                label = "Join format",
                placeholder = "%user.name% joined the channel",
                onCommit = { onChange(entry.copy(joinFormat = it.takeIf { f -> f.isNotBlank() })) },
            )
            BlurCommitTextField(
                value = entry.leaveFormat.orEmpty(),
                label = "Leave format",
                placeholder = "%user.name% left the channel",
                onCommit = { onChange(entry.copy(leaveFormat = it.takeIf { f -> f.isNotBlank() })) },
            )
            Text(
                text = "Placeholders: %user.name% %user.mention% %user.id% " +
                    "%server.name% %server.members% %channel.name%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A slider that tracks the drag locally and only commits once the gesture
 * ends, so dragging never fires a request per frame.
 */
@Composable
private fun CommittingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { it.toInt().toString() },
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    SliderRow(
        label = label,
        value = draft,
        onValueChange = { draft = it },
        onValueChangeFinished = { onCommit(draft) },
        valueRange = valueRange,
        valueLabel = valueLabel(draft),
        modifier = modifier,
    )
}

/** A text field that tracks edits locally and only commits when it loses focus. */
@Composable
private fun BlurCommitTextField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
) {
    var draft by remember(value) { mutableStateOf(value) }
    MewdekoTextField(
        value = draft,
        onValueChange = { draft = it },
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        modifier = modifier.onFocusChanged { focus ->
            if (!focus.isFocused && draft != value) onCommit(draft)
        },
    )
}
