package dev.mewdeko.mobile.feature.confessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import javax.inject.Inject

/** A single anonymous confession. */
@Serializable
data class ConfessionRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val confessNumber: String = "0",
    @SerialName("confession1") val text: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
) {
    /** The confession's public number, as a long. */
    val number: Long get() = confessNumber.toLongOrNull() ?: 0L
}

/** Aggregate confession counters. */
@Serializable
data class ConfessionStats(
    val totalConfessions: Int = 0,
    val confessionsThisMonth: Int = 0,
    val confessionsToday: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val lastConfessionNumber: String = "0",
    @Serializable(with = InstantSerializer::class) val lastConfessionDate: Instant? = null,
)

/** Confessions screen state. */
data class ConfessionsState(
    val confessions: List<ConfessionRecord> = emptyList(),
    val stats: ConfessionStats? = null,
    val channelId: Snowflake? = null,
    val logChannelId: Snowflake? = null,
    val loadedChannelId: Snowflake? = null,
    val loadedLogChannelId: Snowflake? = null,
    val blacklist: List<Snowflake> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val isSaving: Boolean = false,
) {
    /** Whether a channel edit is pending. */
    val hasUnsavedConfig: Boolean
        get() = channelId != loadedChannelId || logChannelId != loadedLogChannelId
}

/** Anonymous confession submissions. */
@HiltViewModel
class ConfessionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ConfessionsState())

    /** Observable screen state. */
    val state: StateFlow<ConfessionsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads confessions, stats, channels, and the role blacklist. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val confessions = async {
                runCatching {
                    api.send(
                        Endpoint("api/Confessions/$guildId"),
                        ListSerializer(ConfessionRecord.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val channel = async { scalar("channel") }
            val logChannel = async { scalar("logChannel") }
            val blacklist = async {
                runCatching {
                    (api.sendRaw(Endpoint("api/Confessions/$guildId/blacklist")) as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id != "0" } }
                        .orEmpty()
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/Confessions/$guildId/stats"),
                        ConfessionStats.serializer(),
                    )
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
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val loadedChannel = channel.await()
            val loadedLog = logChannel.await()

            _state.update {
                it.copy(
                    confessions = confessions.await().sortedByDescending { entry -> entry.number },
                    stats = stats.await(),
                    channelId = loadedChannel,
                    logChannelId = loadedLog,
                    loadedChannelId = loadedChannel,
                    loadedLogChannelId = loadedLog,
                    blacklist = blacklist.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Stages the channel confessions are posted to. */
    fun setChannel(id: Snowflake?) = _state.update { it.copy(channelId = id) }

    /** Stages the moderator-visible log channel. */
    fun setLogChannel(id: Snowflake?) = _state.update { it.copy(logChannelId = id) }

    /**
     * Writes whichever channel changed. Each has its own endpoint, so partial
     * success is reported rather than clearing the dirty flag outright.
     */
    fun saveConfig() = viewModelScope.launch {
        val current = _state.value
        if (!current.hasUnsavedConfig) {
            postError("No changes to save.")
            return@launch
        }
        _state.update { it.copy(isSaving = true) }
        var ok = true

        if (current.channelId != current.loadedChannelId) {
            ok = ok && runCatching {
                post("channel", jsonString(current.channelId ?: "0"))
            }.isSuccess
        }
        if (current.logChannelId != current.loadedLogChannelId) {
            ok = ok && runCatching {
                post("logChannel", jsonString(current.logChannelId ?: "0"))
            }.isSuccess
        }

        _state.update {
            if (ok) {
                it.copy(
                    isSaving = false,
                    loadedChannelId = it.channelId,
                    loadedLogChannelId = it.logChannelId,
                )
            } else {
                it.copy(isSaving = false)
            }
        }
        if (ok) postSuccess("Confession channels updated.") else postError("Failed to save channels.")
    }

    /** Adds or removes a role from the confession blacklist. */
    fun toggleBlacklist(roleId: Snowflake) = launchAction("Failed to toggle blacklist.") {
        api.sendIgnoringBody(
            Endpoint("api/Confessions/$guildId/blacklist/$roleId", HttpMethod.POST)
        )
        _state.update {
            it.copy(
                blacklist = if (roleId in it.blacklist) it.blacklist - roleId
                else it.blacklist + roleId,
            )
        }
    }

    /** Deletes one confession by its public number. */
    fun delete(number: Long) = launchAction("Failed to delete confession.") {
        api.sendIgnoringBody(
            Endpoint("api/Confessions/$guildId/$number", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(confessions = it.confessions.filterNot { entry -> entry.number == number })
        }
        postSuccess("Confession #$number deleted.")
    }

    private suspend fun post(tail: String, body: String) =
        api.sendIgnoringBody(Endpoint("api/Confessions/$guildId/$tail", HttpMethod.POST, body))

    private suspend fun scalar(tail: String): String? = runCatching {
        api.send(Endpoint("api/Confessions/$guildId/$tail"), ScalarString.serializer())
            .value
            ?.takeIf { it.isNotEmpty() && it != "0" }
    }.getOrNull()
}
