package dev.mewdeko.mobile.feature.streams

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.net.scalarText
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

/**
 * The streaming service a followed channel lives on.
 *
 * Raw values match the bot's `FType` enum (Searches/Common/StreamNotifications/Models/Enums.cs),
 * which is not contiguous: 1 and 2 are unused. The controller's `GetStreamTypeName` helper
 * (StreamNotificationsController.cs) now maps this same table server-side, so its `typeName`
 * field is trustworthy and preferred for display; this table remains as the icon lookup and as
 * a fallback for older servers or unset `typeName` values.
 */
enum class StreamPlatform(val raw: Int, val label: String, val icon: ImageVector) {
    TWITCH(0, "Twitch", Icons.Default.LiveTv),
    PICARTO(3, "Picarto", Icons.Default.Palette),
    YOUTUBE(4, "YouTube", Icons.Default.PlayCircle),
    FACEBOOK(5, "Facebook", Icons.Default.OndemandVideo),
    TROVO(6, "Trovo", Icons.Default.Videocam),
    KICK(7, "Kick", Icons.Default.Bolt),
    UNKNOWN(-1, "Unknown", Icons.AutoMirrored.Filled.HelpOutline);

    companion object {
        /** Maps a wire value onto a platform, falling back to [UNKNOWN] rather than guessing. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: UNKNOWN
    }
}

/** Resolves the display label for a stream type, preferring the server's [typeName] when present. */
private fun displayLabel(type: Int, typeName: String?): String =
    typeName?.takeIf { it.isNotBlank() } ?: StreamPlatform.from(type).label

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
    /** The typed form of [type], used for its icon. */
    val platform: StreamPlatform get() = StreamPlatform.from(type)

    /** Display name for the platform, preferring the server's [typeName]. */
    val platformLabel: String get() = displayLabel(type, typeName)
}

/** How many follows exist on one platform. */
@Serializable
data class StreamsByPlatformItem(
    val type: Int = 0,
    val typeName: String? = null,
    val count: Int = 0,
) {
    /** The typed form of [type], used for its icon. */
    val platform: StreamPlatform get() = StreamPlatform.from(type)

    /** Display name for the platform, preferring the server's [typeName]. */
    val platformLabel: String get() = displayLabel(type, typeName)
}

/** One streamer being followed, aggregated across every guild follow entry for them. */
@Serializable
data class UniqueStreamer(
    val username: String = "",
    val type: Int = 0,
    val typeName: String? = null,
    val followCount: Int = 0,
) {
    /** The typed form of [type], used for its icon. */
    val platform: StreamPlatform get() = StreamPlatform.from(type)

    /** Display name for the platform, preferring the server's [typeName]. */
    val platformLabel: String get() = displayLabel(type, typeName)
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
    val streamers: List<UniqueStreamer> = emptyList(),
    val customMessage: EmbedMessage = EmbedMessage(),
    val loadedCustomMessage: String = "",
    val offlineNotifications: Boolean = false,
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /**
     * Whether the shared template has an unsaved edit.
     *
     * Both sides are normalised through [normalizedMessage] first: an empty
     * editor serialises to `"-"`, but a never-set template loads as `""`, and
     * comparing those raw strings would flag every freshly loaded, unedited
     * template as dirty.
     */
    val hasUnsavedMessage: Boolean
        get() = normalizedMessage(customMessage.serialize()) != normalizedMessage(loadedCustomMessage)

    /** How many distinct platforms are currently in use, from the stats breakdown. */
    val platformsInUse: Int get() = stats?.streamsByType?.size ?: 0

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
}

/** Treats the empty-editor sentinel `"-"` the same as an actually empty string. */
private fun normalizedMessage(raw: String): String = if (raw == "-") "" else raw

/**
 * Serialises [message] the way the bot expects to see a reset: an empty
 * editor becomes `""`, which the bot's `IsNullOrWhiteSpace` checks treat as
 * "use the fallback", not the literal sentinel string `"-"` that
 * [EmbedMessage.serialize] uses for its own empty-editor placeholder.
 */
private fun messageBody(message: EmbedMessage): String = if (message.isEmpty) "" else message.serialize()

/** Twitch, Picarto, YouTube, Trovo, and Kick go-live notifications. */
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
            val streamers = async {
                runCatching {
                    api.send(
                        Endpoint("api/StreamNotifications/$guildId/streamers"),
                        ListSerializer(UniqueStreamer.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val custom = async {
                runCatching {
                    api.sendRaw(Endpoint("api/StreamNotifications/$guildId/customMessage")).scalarText()
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
                    streamers = streamers.await(),
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

    /** Sets the go-live message for one follow. An empty [message] resets it to the fallback template. */
    fun setOnlineMessage(index: Int, message: EmbedMessage) =
        launchAction("Failed to save online message.") {
            val body = messageBody(message)
            put(index, "onlineMessage", jsonString(body))
            _state.update { current ->
                current.copy(
                    streams = current.streams.map {
                        if (it.index == index) it.copy(onlineMessage = body) else it
                    },
                )
            }
            postSuccess("Online message saved.")
        }

    /** Sets the went-offline message for one follow. An empty [message] resets it to the fallback template. */
    fun setOfflineMessage(index: Int, message: EmbedMessage) =
        launchAction("Failed to save offline message.") {
            val body = messageBody(message)
            put(index, "offlineMessage", jsonString(body))
            _state.update { current ->
                current.copy(
                    streams = current.streams.map {
                        if (it.index == index) it.copy(offlineMessage = body) else it
                    },
                )
            }
            postSuccess("Offline message saved.")
        }

    /** Stages the guild-wide fallback template. */
    fun setCustomMessage(message: EmbedMessage) = _state.update { it.copy(customMessage = message) }

    /** Persists the guild-wide fallback template. An empty template resets it to the bot's default. */
    fun saveCustomMessage() = launchAction("Failed to save template.") {
        val body = messageBody(_state.value.customMessage)
        api.sendIgnoringBody(
            Endpoint(
                "api/StreamNotifications/$guildId/customMessage",
                HttpMethod.POST,
                jsonString(body),
            )
        )
        _state.update { it.copy(loadedCustomMessage = body) }
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
