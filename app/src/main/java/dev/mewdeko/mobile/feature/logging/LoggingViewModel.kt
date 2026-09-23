package dev.mewdeko.mobile.feature.logging

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * Grouping used to filter the log-type list, mirroring the dashboard's
 * `LOG_TYPE_MAPPINGS` categories.
 */
enum class LogCategory(val id: String, val label: String) {
    MESSAGES("messages", "Messages"),
    THREADS("threads", "Threads"),
    USERS("users", "Users"),
    MODERATION("moderation", "Moderation"),
    VOICE("voice", "Voice"),
    SERVER("server", "Server"),
    ROLES("roles", "Roles"),
    CHANNELS("channels", "Channels"),
    OTHER("other", "Other"),
}

/**
 * Discord log types exposed by the bot.
 *
 * Raw values match the bot's `LogCommandService.LogType` enum and are sent
 * verbatim in the route, so they must not be renamed. Order, category, and
 * description mirror `LOG_TYPE_MAPPINGS` in the dashboard's `Logging.ts`.
 */
enum class LogType(
    val raw: String,
    val label: String,
    val category: LogCategory,
    val description: String,
    val icon: ImageVector,
) {
    MESSAGE_UPDATED(
        "MessageUpdated", "Message edited", LogCategory.MESSAGES,
        "Logs when a message is edited", Icons.Default.Edit,
    ),
    MESSAGE_DELETED(
        "MessageDeleted", "Message deleted", LogCategory.MESSAGES,
        "Logs when a message is deleted", Icons.Default.Delete,
    ),
    MESSAGES_BULK_DELETED(
        "MessagesBulkDeleted", "Messages bulk deleted", LogCategory.MESSAGES,
        "Logs when multiple messages are deleted at once", Icons.Default.Delete,
    ),
    REACTION_EVENTS(
        "ReactionEvents", "Reaction events", LogCategory.MESSAGES,
        "Logs when reactions are added or removed", Icons.Default.Add,
    ),
    THREAD_CREATED(
        "ThreadCreated", "Thread created", LogCategory.THREADS,
        "Logs when a new thread is created", Icons.Default.Add,
    ),
    THREAD_DELETED(
        "ThreadDeleted", "Thread deleted", LogCategory.THREADS,
        "Logs when a thread is deleted", Icons.Default.Delete,
    ),
    THREAD_UPDATED(
        "ThreadUpdated", "Thread updated", LogCategory.THREADS,
        "Logs when thread properties are updated", Icons.Default.Edit,
    ),
    USERNAME_UPDATED(
        "UsernameUpdated", "Username changed", LogCategory.USERS,
        "Logs when a user changes their username", Icons.Default.Edit,
    ),
    NICKNAME_UPDATED(
        "NicknameUpdated", "Nickname changed", LogCategory.USERS,
        "Logs when a user's nickname changes", Icons.Default.Edit,
    ),
    AVATAR_UPDATED(
        "AvatarUpdated", "Avatar changed", LogCategory.USERS,
        "Logs when a user updates their avatar", Icons.Default.Edit,
    ),
    USER_LEFT(
        "UserLeft", "User left", LogCategory.USERS,
        "Logs when a user leaves the server", Icons.Default.PersonRemove,
    ),
    USER_JOINED(
        "UserJoined", "User joined", LogCategory.USERS,
        "Logs when a user joins the server", Icons.Default.PersonAddAlt,
    ),
    USER_UPDATED(
        "UserUpdated", "User updated", LogCategory.USERS,
        "Logs when a user's profile is updated", Icons.Default.CheckCircle,
    ),
    USER_ROLE_ADDED(
        "UserRoleAdded", "Role assigned", LogCategory.USERS,
        "Logs when roles are added to a user", Icons.Default.Add,
    ),
    USER_ROLE_REMOVED(
        "UserRoleRemoved", "Role removed", LogCategory.USERS,
        "Logs when roles are removed from a user", Icons.Default.RemoveCircle,
    ),
    USER_BANNED(
        "UserBanned", "User banned", LogCategory.MODERATION,
        "Logs when a user is banned", Icons.Default.Block,
    ),
    USER_UNBANNED(
        "UserUnbanned", "User unbanned", LogCategory.MODERATION,
        "Logs when a user is unbanned", Icons.Default.CheckCircle,
    ),
    USER_MUTED(
        "UserMuted", "User muted", LogCategory.MODERATION,
        "Logs when a user is muted", Icons.Default.VolumeOff,
    ),
    VOICE_PRESENCE(
        "VoicePresence", "Voice presence", LogCategory.VOICE,
        "Logs voice channel activity", Icons.Default.Mic,
    ),
    VOICE_PRESENCE_TTS(
        "VoicePresenceTts", "Voice presence TTS", LogCategory.VOICE,
        "Logs TTS usage in voice channels", Icons.Default.VolumeUp,
    ),
    SERVER_UPDATED(
        "ServerUpdated", "Server updated", LogCategory.SERVER,
        "Logs when server settings change", Icons.Default.Settings,
    ),
    EVENT_CREATED(
        "EventCreated", "Event created", LogCategory.SERVER,
        "Logs when a server event is created", Icons.Default.Add,
    ),
    INVITE_CREATED(
        "InviteCreated", "Invite created", LogCategory.SERVER,
        "Logs when an invite is created", Icons.Default.Add,
    ),
    INVITE_DELETED(
        "InviteDeleted", "Invite deleted", LogCategory.SERVER,
        "Logs when an invite is deleted", Icons.Default.Delete,
    ),
    ROLE_UPDATED(
        "RoleUpdated", "Role updated", LogCategory.ROLES,
        "Logs when role properties are updated", Icons.Default.Edit,
    ),
    ROLE_DELETED(
        "RoleDeleted", "Role deleted", LogCategory.ROLES,
        "Logs when a role is deleted", Icons.Default.Delete,
    ),
    ROLE_CREATED(
        "RoleCreated", "Role created", LogCategory.ROLES,
        "Logs when a new role is created", Icons.Default.Add,
    ),
    CHANNEL_CREATED(
        "ChannelCreated", "Channel created", LogCategory.CHANNELS,
        "Logs when a new channel is created", Icons.Default.Tag,
    ),
    CHANNEL_DESTROYED(
        "ChannelDestroyed", "Channel destroyed", LogCategory.CHANNELS,
        "Logs when a channel is deleted", Icons.Default.Delete,
    ),
    CHANNEL_UPDATED(
        "ChannelUpdated", "Channel updated", LogCategory.CHANNELS,
        "Logs when channel properties are updated", Icons.Default.Edit,
    ),
    OTHER(
        "Other", "Other", LogCategory.OTHER,
        "Logs miscellaneous events", Icons.Default.WarningAmber,
    ),
    ;

    companion object {
        /** Raw values highlighted under the "Popular" filter, matching the dashboard. */
        val POPULAR_RAW = setOf(
            "UserJoined", "UserLeft", "MessageDeleted", "MessageUpdated",
            "UserBanned", "UserUnbanned", "ChannelCreated", "ChannelDestroyed",
        )
    }
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

    /** Unassigns every log type in [category] at once via the bulk-update route. */
    fun clearCategory(category: LogCategory) = launchAction("Failed to clear ${category.label}.") {
        val types = LogType.entries.filter { it.category == category }
        val mappings = buildJsonArray {
            types.forEach { type ->
                add(
                    buildJsonObject {
                        put("logType", type.raw)
                        put("channelId", JsonNull)
                    }
                )
            }
        }
        api.sendIgnoringBody(
            Endpoint(
                "api/Logging/$guildId/bulk-update",
                HttpMethod.PUT,
                jsonBody("logTypeMappings" to mappings),
            )
        )
        _state.update {
            it.copy(logTypeChannels = it.logTypeChannels - types.map { type -> type.raw }.toSet())
        }
    }
}
