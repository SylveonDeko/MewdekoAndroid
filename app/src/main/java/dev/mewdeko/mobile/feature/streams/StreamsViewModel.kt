package dev.mewdeko.mobile.feature.streams

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.Instant
import javax.inject.Inject

/** The streaming service a followed channel lives on. */
enum class StreamPlatform(val raw: Int, val label: String, val icon: ImageVector) {
    TWITCH(0, "Twitch", Icons.Default.LiveTv),
    YOUTUBE(1, "YouTube", Icons.Default.PlayCircle),
    TROVO(2, "Trovo", Icons.Default.Videocam),
    FACEBOOK(3, "Facebook", Icons.Default.OndemandVideo);

    companion object {
        /** Maps a wire value onto a platform, defaulting to [TWITCH]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: TWITCH
    }
}

/** A followed streamer and where their notifications post. */
@Serializable
data class FollowedStream(
    val index: Int = 0,
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val username: String = "",
    val type: Int = 0,
    val typeName: String? = null,
    val onlineMessage: String? = null,
    val offlineMessage: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val channelName: String? = null,
) {
    /** The typed form of [type]. */
    val platform: StreamPlatform get() = StreamPlatform.from(type)
}

/** How many follows exist on one platform. */
@Serializable
data class StreamsByPlatformItem(
    val type: Int = 0,
    val typeName: String? = null,
    val count: Int = 0,
) {
    /** The typed form of [type]. */
    val platform: StreamPlatform get() = StreamPlatform.from(type)
}

/** Follow counters broken down by platform. */
@Serializable
data class StreamStats(
    val totalStreams: Int = 0,
    val streamsByType: List<StreamsByPlatformItem> = emptyList(),
)

/** The response from toggling offline notifications. */
@Serializable
data class OfflineNotificationToggleResponse(val offlineNotificationsEnabled: Boolean = false)

/** Streams screen state. */
data class StreamsState(
    val streams: List<FollowedStream> = emptyList(),
    val stats: StreamStats? = null,
    val customMessage: EmbedMessage = EmbedMessage(),
    val loadedCustomMessage: String = "",
    val offlineNotifications: Boolean = false,
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** Whether the shared template has an unsaved edit. */
    val hasUnsavedMessage: Boolean get() = customMessage.serialize() != loadedCustomMessage

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
}

/** Twitch, YouTube, Trovo, and Facebook go-live notifications. */
@HiltViewModel
class StreamsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StreamsState())

    /** Observable screen state. */
    val state: StateFlow<StreamsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads follows, stats, and notification settings. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val streams = async {
                runCatching {
                    api.send(
                        Endpoint("api/StreamNotifications/$guildId"),
                        ListSerializer(FollowedStream.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/StreamNotifications/$guildId/stats"),
                        StreamStats.serializer(),
                    )
                }.getOrNull()
            }
            val custom = async {
                runCatching {
                    api.send(
                        Endpoint("api/StreamNotifications/$guildId/customMessage"),
                        ScalarString.serializer(),
                    ).value
                }.getOrNull()
            }
            val offline = async {
                runCatching {
                    api.send(
                        Endpoint("api/StreamNotifications/$guildId/offlineNotifications"),
                        Boolean.serializer(),
                    )
                }.getOrDefault(false)
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val loadedCustom = custom.await().orEmpty()
            _state.update {
                it.copy(
                    streams = streams.await(),
                    stats = stats.await(),
                    customMessage = EmbedMessage.parse(loadedCustom),
                    loadedCustomMessage = loadedCustom,
                    offlineNotifications = offline.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Follows a streamer, posting notifications into a channel. */
    fun follow(channelId: Snowflake, url: String) = launchAction("Failed to follow stream.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/StreamNotifications/$guildId",
                HttpMethod.POST,
                jsonBody("channelId" to (channelId.toLongOrNull() ?: 0L), "url" to url),
            )
        )
        postSuccess("Stream followed.")
        load(refreshing = true)
    }

    /** Stops following a streamer. */
    fun unfollow(index: Int) = launchAction("Failed to unfollow stream.") {
        api.sendIgnoringBody(
            Endpoint("api/StreamNotifications/$guildId/$index", HttpMethod.DELETE)
        )
        _state.update { it.copy(streams = it.streams.filterNot { entry -> entry.index == index }) }
        postSuccess("Stream unfollowed.")
    }

    /** Removes every follow. */
    fun clearAll() = launchAction("Failed to clear streams.") {
        api.sendIgnoringBody(
            Endpoint("api/StreamNotifications/$guildId", HttpMethod.DELETE)
        )
        _state.update { it.copy(streams = emptyList()) }
        postSuccess("All streams cleared.")
    }

    /** Sets the go-live message for one follow. */
    fun setOnlineMessage(index: Int, message: EmbedMessage) =
        launchAction("Failed to save online message.") {
            put(index, "onlineMessage", jsonString(message.serialize()))
            _state.update { current ->
                current.copy(
                    streams = current.streams.map {
                        if (it.index == index) it.copy(onlineMessage = message.serialize()) else it
                    },
                )
            }
            postSuccess("Online message saved.")
        }

    /** Sets the went-offline message for one follow. */
    fun setOfflineMessage(index: Int, message: EmbedMessage) =
        launchAction("Failed to save offline message.") {
            put(index, "offlineMessage", jsonString(message.serialize()))
            _state.update { current ->
                current.copy(
                    streams = current.streams.map {
                        if (it.index == index) it.copy(offlineMessage = message.serialize()) else it
                    },
                )
            }
            postSuccess("Offline message saved.")
        }

    /** Stages the guild-wide fallback template. */
    fun setCustomMessage(message: EmbedMessage) = _state.update { it.copy(customMessage = message) }

    /** Persists the guild-wide fallback template. */
    fun saveCustomMessage() = launchAction("Failed to save template.") {
        val serialized = _state.value.customMessage.serialize()
        api.sendIgnoringBody(
            Endpoint(
                "api/StreamNotifications/$guildId/customMessage",
                HttpMethod.POST,
                jsonString(serialized),
            )
        )
        _state.update { it.copy(loadedCustomMessage = serialized) }
        postSuccess("Template saved.")
    }

    /** Turns went-offline notifications on or off. */
    fun toggleOfflineNotifications() = launchAction("Failed to toggle notifications.") {
        val response = api.send(
            Endpoint(
                "api/StreamNotifications/$guildId/offlineNotifications/toggle",
                HttpMethod.POST,
            ),
            OfflineNotificationToggleResponse.serializer(),
        )
        _state.update { it.copy(offlineNotifications = response.offlineNotificationsEnabled) }
    }

    private suspend fun put(index: Int, tail: String, body: String) =
        api.sendIgnoringBody(
            Endpoint("api/StreamNotifications/$guildId/$index/$tail", HttpMethod.PUT, body)
        )
}
