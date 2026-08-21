package dev.mewdeko.mobile.feature.logging

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import javax.inject.Inject

/**
 * Discord log types exposed by the bot.
 *
 * Raw values match the bot's `LogCommandService.LogType` enum and are sent
 * verbatim in the route, so they must not be renamed.
 */
enum class LogType(val raw: String, val label: String) {
    MESSAGE_UPDATED("MessageUpdated", "Message edited"),
    MESSAGE_DELETED("MessageDeleted", "Message deleted"),
    THREAD_CREATED("ThreadCreated", "Thread created"),
    THREAD_DELETED("ThreadDeleted", "Thread deleted"),
    THREAD_UPDATED("ThreadUpdated", "Thread updated"),
    USERNAME_UPDATED("UsernameUpdated", "Username changed"),
    NICKNAME_UPDATED("NicknameUpdated", "Nickname changed"),
    AVATAR_UPDATED("AvatarUpdated", "Avatar changed"),
    USER_JOINED("UserJoined", "User joined"),
    USER_LEFT("UserLeft", "User left"),
    USER_BANNED("UserBanned", "User banned"),
    USER_UNBANNED("UserUnbanned", "User unbanned"),
    USER_UPDATED("UserUpdated", "User updated"),
    USER_MUTED("UserMuted", "User muted"),
    USER_ROLE_ADDED("UserRoleAdded", "Role assigned"),
    USER_ROLE_REMOVED("UserRoleRemoved", "Role removed"),
    VOICE_PRESENCE("VoicePresence", "Voice presence"),
    VOICE_PRESENCE_TTS("VoicePresenceTts", "Voice presence TTS"),
    SERVER_UPDATED("ServerUpdated", "Server updated"),
    ROLE_CREATED("RoleCreated", "Role created"),
    ROLE_DELETED("RoleDeleted", "Role deleted"),
    ROLE_UPDATED("RoleUpdated", "Role updated"),
    EVENT_CREATED("EventCreated", "Event created"),
    CHANNEL_CREATED("ChannelCreated", "Channel created"),
    CHANNEL_DESTROYED("ChannelDestroyed", "Channel destroyed"),
    CHANNEL_UPDATED("ChannelUpdated", "Channel updated"),
    REACTION_EVENTS("ReactionEvents", "Reaction events"),
    OTHER("Other", "Other"),
}

/** Logging screen state. */
data class LoggingState(
    val enabled: Boolean = false,
    val logTypeChannels: Map<String, Snowflake> = emptyMap(),
    val ignoredChannels: List<Snowflake> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val section: String = "types",
) {
    /** How many log types have a destination channel set. */
    val configuredCount: Int get() = logTypeChannels.size

    /** The channel bound to [type], if any. */
    fun channelFor(type: LogType): Snowflake? = logTypeChannels[type.raw]
}

/** Loads and edits the guild's per-event logging destinations. */
@HiltViewModel
class LoggingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(LoggingState())

    /** Observable screen state. */
    val state: StateFlow<LoggingState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the logging configuration and channel list. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val config = async {
                runCatching {
                    api.sendRaw(Endpoint("api/Logging/$guildId/configuration")) as? JsonObject
                }.getOrNull()
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val cfg = config.await()
            val logTypes = (cfg?.get("logTypes") as? JsonObject)
                ?.mapNotNull { (key, value) ->
                    if (value is JsonNull) return@mapNotNull null
                    val id = (value as? JsonPrimitive)?.content ?: return@mapNotNull null
                    if (id.isEmpty() || id == "0") null else key to id
                }
                ?.toMap()
                .orEmpty()

            val ignored = (cfg?.get("ignoredChannels") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id != "0" } }
                .orEmpty()

            _state.update {
                it.copy(
                    enabled = (cfg?.get("enabled") as? JsonPrimitive)?.booleanOrNull == true,
                    logTypeChannels = logTypes,
                    ignoredChannels = ignored,
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Binds [type] to a channel, or clears it when [channelId] is null. */
    fun setChannel(type: LogType, channelId: Snowflake?) =
        launchAction("Failed to update ${type.label}.") {
            val numeric = channelId?.toLongOrNull()?.takeIf { it != 0L }
            api.sendIgnoringBody(
                Endpoint(
                    "api/Logging/$guildId/log-type/${type.raw}",
                    HttpMethod.PUT,
                    jsonBody("channelId" to numeric),
                )
            )
            _state.update {
                it.copy(
                    logTypeChannels = if (numeric == null) it.logTypeChannels - type.raw
                    else it.logTypeChannels + (type.raw to channelId!!),
                )
            }
        }

    /** Adds or removes a channel from the ignore list. */
    fun toggleIgnored(channelId: Snowflake) = launchAction("Failed to update ignored channels.") {
        api.sendIgnoringBody(
            Endpoint("api/Logging/$guildId/ignored-channels/$channelId", HttpMethod.POST)
        )
        _state.update {
            it.copy(
                ignoredChannels = if (channelId in it.ignoredChannels) {
                    it.ignoredChannels - channelId
                } else {
                    it.ignoredChannels + channelId
                },
            )
        }
    }

    /** Clears every log-type binding. */
    fun disableAll() = launchAction("Failed to disable logging.") {
        api.sendIgnoringBody(Endpoint("api/Logging/$guildId/disable-all", HttpMethod.DELETE))
        postSuccess("All logging disabled.")
        load(refreshing = true)
    }
}
