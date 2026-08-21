package dev.mewdeko.mobile.feature.multigreets

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
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject

/** How the bot picks among the configured greet channels. */
enum class MultiGreetType(val raw: Int, val label: String, val blurb: String) {
    ALL(0, "All", "Post the greeting in every configured channel"),
    RANDOM(1, "Random", "Post in one randomly-chosen channel"),
    OFF(2, "Off", "Do not post greetings");

    companion object {
        /** Maps a wire value onto a mode, defaulting to [ALL]. */
        fun from(raw: Int): MultiGreetType = entries.firstOrNull { it.raw == raw } ?: ALL
    }
}

/** One configured welcome greeting. */
@Serializable
data class MultiGreetEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String? = null,
    val message: String? = null,
    val deleteTime: Int = 0,
    val webhookUrl: String? = null,
    val greetBots: Boolean = false,
    val disabled: Boolean = false,
)

/** Greets screen state. */
data class MultiGreetsState(
    val greets: List<MultiGreetEntry> = emptyList(),
    val greetType: MultiGreetType = MultiGreetType.ALL,
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** How many greets are currently active. */
    val activeCount: Int get() = greets.count { !it.disabled }

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
}

/** Welcome messages posted when a member joins. */
@HiltViewModel
class MultiGreetsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(MultiGreetsState())

    /** Observable screen state. */
    val state: StateFlow<MultiGreetsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the greet list, mode, and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val greets = async {
                runCatching {
                    api.send(
                        Endpoint("api/MultiGreet/$guildId"),
                        ListSerializer(MultiGreetEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val type = async {
                runCatching {
                    api.send(Endpoint("api/MultiGreet/$guildId/type"), Int.serializer())
                }.getOrDefault(0)
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
                    greetType = MultiGreetType.from(type.await()),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Sets how the bot picks among configured greet channels. */
    fun setType(type: MultiGreetType) = launchAction("Failed to set greet mode.") {
        api.sendIgnoringBody(
            Endpoint("api/MultiGreet/$guildId/type", HttpMethod.PUT, jsonInt(type.raw))
        )
        _state.update { it.copy(greetType = type) }
        postSuccess("Greet mode: ${type.label}.")
    }

    /** Adds a greet for a channel. */
    fun add(channelId: Snowflake) = launchAction("Failed to add greet.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/MultiGreet/$guildId",
                HttpMethod.POST,
                (channelId.toLongOrNull() ?: 0L).toString(),
            )
        )
        postSuccess("Greet added.")
        load(refreshing = true)
    }

    /** Removes a greet. */
    fun remove(id: Int) = launchAction("Failed to remove greet.") {
        api.sendIgnoringBody(Endpoint("api/MultiGreet/$guildId/$id", HttpMethod.DELETE))
        _state.update { it.copy(greets = it.greets.filterNot { entry -> entry.id == id }) }
        postSuccess("Greet removed.")
    }

    /** Sets the greet's message template. */
    fun updateMessage(id: Int, message: EmbedMessage) = launchAction("Failed to save message.") {
        put(id, "message", jsonString(message.serialize()))
        _state.update { current ->
            current.copy(
                greets = current.greets.map {
                    if (it.id == id) it.copy(message = message.serialize()) else it
                },
            )
        }
        postSuccess("Message saved.")
    }

    /**
     * Sets how long the greeting survives before deletion.
     *
     * This endpoint expects an `hh:mm:ss` string rather than a raw count,
     * unlike the equivalent role-greet route.
     */
    fun updateDeleteTime(id: Int, seconds: Int) = launchAction("Failed to update delete time.") {
        val formatted = "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        put(id, "delete-time", jsonString(formatted))
        _state.update { current ->
            current.copy(
                greets = current.greets.map { if (it.id == id) it.copy(deleteTime = seconds) else it },
            )
        }
        postSuccess("Delete time updated.")
    }

    /** Sets whether bots also receive this greeting. */
    fun setGreetBots(id: Int, value: Boolean) = launchAction("Failed to update setting.") {
        put(id, "greet-bots", jsonBool(value))
        _state.update { current ->
            current.copy(
                greets = current.greets.map { if (it.id == id) it.copy(greetBots = value) else it },
            )
        }
    }

    /** Enables or disables the greeting. */
    fun setDisabled(id: Int, value: Boolean) = launchAction("Failed to update setting.") {
        put(id, "disable", jsonBool(value))
        _state.update { current ->
            current.copy(
                greets = current.greets.map { if (it.id == id) it.copy(disabled = value) else it },
            )
        }
    }

    private suspend fun put(id: Int, tail: String, body: String) =
        api.sendIgnoringBody(Endpoint("api/MultiGreet/$guildId/$id/$tail", HttpMethod.PUT, body))
}
