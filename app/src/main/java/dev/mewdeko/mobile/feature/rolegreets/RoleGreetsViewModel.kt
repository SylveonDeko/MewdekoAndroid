package dev.mewdeko.mobile.feature.rolegreets

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.net.jsonInt
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
import javax.inject.Inject

/** A greeting posted when a member gains a specific role. */
@Serializable
data class RoleGreetEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val message: String? = null,
    val greetBots: Boolean = false,
    val deleteTime: Int = 0,
    val webhookUrl: String? = null,
    val disabled: Boolean = false,
)

/** Role greets screen state. */
data class RoleGreetsState(
    val greets: List<RoleGreetEntry> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** How many greets are currently active. */
    val activeCount: Int get() = greets.count { !it.disabled }

    /** Resolves a role id to its name, falling back to the raw id. */
    fun roleName(id: Snowflake?): String =
        id?.let { raw -> availableRoles.firstOrNull { it.id == raw }?.name ?: raw } ?: "Unknown role"

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake?): String =
        id?.let { raw -> availableChannels.firstOrNull { it.id == raw }?.name ?: raw }
            ?: "Unknown channel"
}

/** Greetings posted when a member gains a role. */
@HiltViewModel
class RoleGreetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(RoleGreetsState())

    /** Observable screen state. */
    val state: StateFlow<RoleGreetsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the greet list plus role and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val greets = async {
                runCatching {
                    api.send(
                        Endpoint("api/RoleGreet/$guildId"),
                        ListSerializer(RoleGreetEntry.serializer()),
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
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    greets = greets.await(),
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Creates a greet binding a role to a channel. */
    fun add(roleId: Snowflake, channelId: Snowflake) = launchAction("Failed to add greet.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/RoleGreet/$guildId/role/$roleId",
                HttpMethod.POST,
                (channelId.toLongOrNull() ?: 0L).toString(),
            )
        )
        postSuccess("Greet added.")
        load(refreshing = true)
    }

    /** Sets the greet's message template. */
    fun updateMessage(greetId: Int, message: EmbedMessage) = launchAction("Failed to save message.") {
        put(greetId, "message", jsonString(message.serialize()))
        _state.update { current ->
            current.copy(
                greets = current.greets.map {
                    if (it.id == greetId) it.copy(message = message.serialize()) else it
                },
            )
        }
        postSuccess("Saved.")
    }

    /** Sets how long the greet stays before being deleted, in seconds. */
    fun updateDeleteTime(greetId: Int, seconds: Int) = launchAction("Failed to update delete time.") {
        put(greetId, "delete-time", jsonInt(seconds))
        _state.update { current ->
            current.copy(
                greets = current.greets.map {
                    if (it.id == greetId) it.copy(deleteTime = seconds) else it
                },
            )
        }
    }

    /** Sets whether bots also receive this greet. */
    fun updateGreetBots(greetId: Int, value: Boolean) = launchAction("Failed to update setting.") {
        put(greetId, "greet-bots", jsonBool(value))
        _state.update { current ->
            current.copy(
                greets = current.greets.map {
                    if (it.id == greetId) it.copy(greetBots = value) else it
                },
            )
        }
    }

    /** Enables or disables the greet. */
    fun updateDisabled(greetId: Int, value: Boolean) = launchAction("Failed to update setting.") {
        put(greetId, "disable", jsonBool(value))
        _state.update { current ->
            current.copy(
                greets = current.greets.map {
                    if (it.id == greetId) it.copy(disabled = value) else it
                },
            )
        }
    }

    private suspend fun put(greetId: Int, tail: String, body: String) =
        api.sendIgnoringBody(
            Endpoint("api/RoleGreet/$guildId/$greetId/$tail", HttpMethod.PUT, body)
        )
}
