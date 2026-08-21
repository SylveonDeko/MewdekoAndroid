package dev.mewdeko.mobile.feature.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MusicSocket
import dev.mewdeko.mobile.core.net.MusicSocketEvent
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.URLEncoder
import javax.inject.Inject

/** One search hit from the music source. */
@Serializable
data class MusicSearchResult(
    val title: String = "",
    val author: String? = null,
    val duration: String? = null,
    val uri: String? = null,
    val artworkUri: String? = null,
    val sourceName: String? = null,
)

/** A page of search hits. */
@Serializable
data class MusicSearchResponse(val tracks: List<MusicSearchResult> = emptyList())

/** Persisted player preferences for a guild. */
@Serializable
data class MusicPlayerSettingModel(
    @SerialName("volume") val volume: Int = 100,
    @SerialName("playerRepeat") val playerRepeat: Int = 0,
    @SerialName("autoPlay") val autoPlay: Int = 0,
    @SerialName("autoDisconnect") val autoDisconnect: Int = 0,
)

/** One voice channel wired up for text-to-speech. */
@Serializable
data class TtsVoiceChannelEntry(
    @Serializable(with = SnowflakeSerializer::class) val voiceChannelId: Snowflake? = null,
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val linkedTextChannelId: Snowflake? = null,
    val announceJoinLeave: Boolean = false,
    val joinFormat: String? = null,
    val leaveFormat: String? = null,
)

/** Guild-wide text-to-speech settings. */
@Serializable
data class TtsSettingsResponse(
    val ttsVolume: Int = 100,
    val ttsSpeed: Double = 1.0,
    val ttsDefaultVoice: String = "",
    val ttsReplyContext: Boolean = false,
    val ttsAttachmentNarration: Boolean = false,
    val ttsConsecutiveGrouping: Boolean = false,
    val ttsMaxQueueSize: Int = 10,
    @Serializable(with = SnowflakeSerializer::class) val ttsRoleId: Snowflake? = null,
    val voiceChannels: List<TtsVoiceChannelEntry> = emptyList(),
)

/** An available text-to-speech voice. */
@Serializable
data class TtsVoice(
    val name: String = "?",
    val gender: String? = null,
    val source: String? = null,
    val language: String? = null,
    val languageCode: String? = null,
)

/** A member blocked from using text-to-speech. */
@Serializable
data class TtsBlockedUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val voice: String? = null,
    val isBlocked: Boolean = false,
)

/** Music screen state. */
data class MusicState(
    val player: MusicStatus? = null,
    val settings: MusicPlayerSettingModel = MusicPlayerSettingModel(),
    val searchQuery: String = "",
    val searchResults: List<MusicSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val tts: TtsSettingsResponse = TtsSettingsResponse(),
    val ttsVoices: List<TtsVoice> = emptyList(),
    val ttsBlocked: List<TtsBlockedUser> = emptyList(),
    val voiceChannels: List<TextChannelLite> = emptyList(),
    val textChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val isLive: Boolean = false,
    val section: String = "player",
) {
    /** The queue behind the current track. */
    val queue: List<dev.mewdeko.mobile.core.model.QueueTrack> get() = player?.queue.orEmpty()
}

/** The Lavalink-backed music player and its text-to-speech settings. */
@HiltViewModel
class MusicViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
    private val socket: MusicSocket,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(MusicState())

    /** Observable screen state. */
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private var socketJob: Job? = null

    init {
        load()
        connectLiveUpdates()
    }

    /** Reloads the player snapshot, settings, and TTS configuration. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val player = async {
                runCatching {
                    api.send(
                        Endpoint("api/Music/$guildId/status?userId=$userId"),
                        MusicStatus.serializer(),
                    )
                }.getOrNull()
            }
            val settings = async {
                runCatching {
                    api.send(
                        Endpoint("api/Music/$guildId/settings"),
                        MusicPlayerSettingModel.serializer(),
                    )
                }.getOrDefault(MusicPlayerSettingModel())
            }
            val tts = async {
                runCatching {
                    api.send(
                        Endpoint("api/Music/$guildId/tts"),
                        TtsSettingsResponse.serializer(),
                    )
                }.getOrDefault(TtsSettingsResponse())
            }
            val voices = async {
                runCatching {
                    api.send(
                        Endpoint("api/Music/$guildId/tts/voices?search="),
                        ListSerializer(TtsVoice.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val blocked = async {
                runCatching {
                    api.send(
                        Endpoint("api/Music/$guildId/tts/blocked"),
                        ListSerializer(TtsBlockedUser.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val voiceChannels = async { channelsOfType(2) }
            val textChannels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    player = player.await(),
                    settings = settings.await(),
                    tts = tts.await(),
                    ttsVoices = voices.await(),
                    ttsBlocked = blocked.await(),
                    voiceChannels = voiceChannels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    textChannels = textChannels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Toggles play and pause. */
    fun togglePlayPause() = command("pause", "Failed to pause.")

    /** Skips to the next track. */
    fun skip() = command("skip", "Failed to skip.")

    /** Returns to the previous track. */
    fun previous() = command("previous", "Failed to go back.")

    /** Shuffles the queue. */
    fun shuffle() = command("shuffle", "Failed to shuffle.")

    /** Sets the player volume, 0 to 100. */
    fun setVolume(volume: Int) = launchAction("Failed to set volume.") {
        api.sendIgnoringBody(
            Endpoint("api/Music/$guildId/volume/${volume.coerceIn(0, 100)}", HttpMethod.POST)
        )
        _state.update { it.copy(settings = it.settings.copy(volume = volume)) }
    }

    /** Seeks the current track to [seconds]. */
    fun seek(seconds: Int) = launchAction("Failed to seek.") {
        api.sendIgnoringBody(
            Endpoint("api/Music/$guildId/seek", HttpMethod.POST, jsonBody("position" to seconds))
        )
    }

    /** Sets the repeat mode: 0 off, 1 track, 2 queue. */
    fun setRepeat(mode: Int) = launchAction("Failed to set repeat mode.") {
        api.sendIgnoringBody(Endpoint("api/Music/$guildId/repeat/$mode", HttpMethod.POST))
        _state.update { it.copy(settings = it.settings.copy(playerRepeat = mode)) }
    }

    /** Queues a track or playlist by search term or URL. */
    fun play(query: String) = launchAction("Failed to queue track.") {
        api.sendIgnoringBody(
            Endpoint("api/Music/$guildId/play", HttpMethod.POST, jsonBody("query" to query))
        )
        postSuccess("Queued.")
        refreshPlayer()
    }

    /** Jumps to a queued track by index. */
    fun playTrack(index: Int) = launchAction("Failed to play track.") {
        api.sendIgnoringBody(Endpoint("api/Music/$guildId/play-track/$index", HttpMethod.POST))
        refreshPlayer()
    }

    /** Removes a track from the queue. */
    fun removeFromQueue(index: Int) = launchAction("Failed to remove track.") {
        api.sendIgnoringBody(Endpoint("api/Music/$guildId/queue/$index", HttpMethod.DELETE))
        refreshPlayer()
    }

    /** Empties the queue. */
    fun clearQueue() = launchAction("Failed to clear queue.") {
        api.sendIgnoringBody(Endpoint("api/Music/$guildId/queue", HttpMethod.DELETE))
        postSuccess("Queue cleared.")
        refreshPlayer()
    }

    /** Turns an audio filter on or off. */
    fun setFilter(name: String, enable: Boolean) = launchAction("Failed to set filter.") {
        api.sendIgnoringBody(
            Endpoint("api/Music/$guildId/filter/$name", HttpMethod.POST, jsonBool(enable))
        )
    }

    /** Runs a search against the music source. */
    fun search(query: String) = viewModelScope.launch {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return@launch
        }
        _state.update { it.copy(isSearching = true) }
        val encoded = URLEncoder.encode(query, "UTF-8")
        val response = runCatching {
            api.send(
                Endpoint("api/Music/0/search?query=$encoded&mode=YouTube&limit=15"),
                MusicSearchResponse.serializer(),
            )
        }.getOrNull()
        _state.update { it.copy(searchResults = response?.tracks.orEmpty(), isSearching = false) }
    }

    /** Writes the persisted player preferences. */
    fun saveSettings(settings: MusicPlayerSettingModel) =
        launchAction("Failed to save music settings.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Music/$guildId/settings",
                    HttpMethod.POST,
                    jsonBody(
                        "Volume" to settings.volume,
                        "PlayerRepeat" to settings.playerRepeat,
                        "AutoPlay" to settings.autoPlay,
                        "AutoDisconnect" to settings.autoDisconnect,
                    ),
                )
            )
            _state.update { it.copy(settings = settings) }
            postSuccess("Settings saved.")
        }

    /** Writes the guild's text-to-speech settings. */
    fun saveTtsSettings(settings: TtsSettingsResponse) =
        launchAction("Failed to save TTS settings.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Music/$guildId/tts/settings",
                    HttpMethod.POST,
                    jsonBody(
                        "ttsVolume" to settings.ttsVolume,
                        "ttsSpeed" to settings.ttsSpeed,
                        "ttsDefaultVoice" to settings.ttsDefaultVoice,
                        "ttsReplyContext" to settings.ttsReplyContext,
                        "ttsAttachmentNarration" to settings.ttsAttachmentNarration,
                        "ttsConsecutiveGrouping" to settings.ttsConsecutiveGrouping,
                        "ttsMaxQueueSize" to settings.ttsMaxQueueSize,
                        "ttsRoleId" to settings.ttsRoleId?.toLongOrNull(),
                    ),
                )
            )
            _state.update { it.copy(tts = settings) }
            postSuccess("TTS settings saved.")
        }

    /** Enables text-to-speech in a voice channel. */
    fun addTtsChannel(voiceChannelId: Snowflake, linkedTextChannelId: Snowflake?) =
        launchAction("Failed to add TTS channel.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Music/$guildId/tts/vc",
                    HttpMethod.POST,
                    jsonBody(
                        "voiceChannelId" to (voiceChannelId.toLongOrNull() ?: 0L),
                        "linkedTextChannelId" to linkedTextChannelId?.toLongOrNull(),
                        "enabled" to true,
                    ),
                )
            )
            postSuccess("TTS channel added.")
            load(refreshing = true)
        }

    /** Disables text-to-speech in a voice channel. */
    fun removeTtsChannel(voiceChannelId: Snowflake) =
        launchAction("Failed to remove TTS channel.") {
            api.sendIgnoringBody(
                Endpoint("api/Music/$guildId/tts/vc/$voiceChannelId", HttpMethod.DELETE)
            )
            _state.update {
                it.copy(
                    tts = it.tts.copy(
                        voiceChannels = it.tts.voiceChannels
                            .filterNot { entry -> entry.voiceChannelId == voiceChannelId },
                    ),
                )
            }
        }

    /** Blocks or unblocks a member from using text-to-speech. */
    fun setTtsBlocked(memberId: Snowflake, blocked: Boolean) =
        launchAction("Failed to update TTS block.") {
            api.sendIgnoringBody(
                Endpoint("api/Music/$guildId/tts/user/$memberId/block/$blocked", HttpMethod.POST)
            )
            load(refreshing = true)
        }

    /** Re-fetches the player snapshot without touching the rest of the screen. */
    fun refreshPlayer() = viewModelScope.launch {
        val player = runCatching {
            api.send(
                Endpoint("api/Music/$guildId/status?userId=$userId"),
                MusicStatus.serializer(),
            )
        }.getOrNull() ?: return@launch
        _state.update { it.copy(player = player) }
    }

    /**
     * Subscribes to the live player feed.
     *
     * The socket pushes a full snapshot on every change, so its frames simply
     * replace the polled state; if it drops, the screen still works through
     * pull to refresh.
     */
    private fun connectLiveUpdates() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            val baseUrl = api.currentBaseUrl() ?: return@launch
            val instance = api.currentInstance()
            socket.connect(baseUrl, instance, guildId, userId).collect { event ->
                when (event) {
                    is MusicSocketEvent.Status ->
                        _state.update { it.copy(player = event.status, isLive = true) }

                    is MusicSocketEvent.Closed -> _state.update { it.copy(isLive = false) }
                    is MusicSocketEvent.Failed -> _state.update { it.copy(isLive = false) }
                    is MusicSocketEvent.Raw -> Unit
                }
            }
        }
    }

    private fun command(tail: String, failureMessage: String) = launchAction(failureMessage) {
        api.sendIgnoringBody(Endpoint("api/Music/$guildId/$tail", HttpMethod.POST))
        refreshPlayer()
    }

    private suspend fun channelsOfType(type: Int): List<TextChannelLite> = runCatching {
        api.send(
            Endpoint("api/ClientOperations/channels/$guildId/$type"),
            ListSerializer(TextChannelLite.serializer()),
        )
    }.getOrDefault(emptyList())

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
    }
}
