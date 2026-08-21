package dev.mewdeko.mobile.feature.votes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import javax.inject.Inject

/** A role granted for a period of time after a member votes. */
@Serializable
data class VoteRoleEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "0",
    val timer: Int = 0,
)

/** A recorded vote. */
@Serializable
data class VoteRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val botId: Snowflake? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** One entry in the vote leaderboard. */
@Serializable
data class VoteLeaderboardEntry(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val voteCount: Int = 0,
)

/** Votes screen state. */
data class VotesState(
    val voteRoles: List<VoteRoleEntry> = emptyList(),
    val votes: List<VoteRecord> = emptyList(),
    val leaderboard: List<VoteLeaderboardEntry> = emptyList(),
    val message: EmbedMessage = EmbedMessage(),
    val loadedMessage: String = "",
    val password: String = "",
    val loadedPassword: String = "",
    val channelId: Snowflake? = null,
    val loadedChannelId: Snowflake? = null,
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val section: String = "settings",
) {
    /** Whether any configuration edit is pending. */
    val hasUnsavedConfig: Boolean
        get() = message.serialize() != loadedMessage ||
            password != loadedPassword ||
            channelId != loadedChannelId

    /** Resolves a role id to its name, falling back to the raw id. */
    fun roleName(id: Snowflake): String =
        availableRoles.firstOrNull { it.id == id }?.name ?: id
}

/** Vote tracking, reward roles, and the vote leaderboard. */
@HiltViewModel
class VotesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(VotesState())

    /** Observable screen state. */
    val state: StateFlow<VotesState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads roles, votes, the leaderboard, and configuration. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/Votes/$guildId/roles"),
                        ListSerializer(VoteRoleEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val votes = async {
                runCatching {
                    api.send(
                        Endpoint("api/Votes/$guildId/votes"),
                        ListSerializer(VoteRecord.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/Votes/$guildId/leaderboard?limit=25"),
                        ListSerializer(VoteLeaderboardEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val message = async { scalar("message") }
            val password = async { scalar("password") }
            val channel = async { scalar("channel") }
            val allRoles = async {
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

            val loadedMessage = message.await().orEmpty()
            val loadedPassword = password.await().orEmpty()
            val loadedChannel = channel.await()?.takeIf { it.isNotEmpty() && it != "0" }

            _state.update {
                it.copy(
                    voteRoles = roles.await(),
                    votes = votes.await().sortedByDescending { vote -> vote.dateAdded ?: Instant.EPOCH },
                    leaderboard = leaderboard.await().sortedByDescending { entry -> entry.voteCount },
                    message = EmbedMessage.parse(loadedMessage),
                    loadedMessage = loadedMessage,
                    password = loadedPassword,
                    loadedPassword = loadedPassword,
                    channelId = loadedChannel,
                    loadedChannelId = loadedChannel,
                    availableRoles = allRoles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { entry -> entry.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Stages a new vote announcement message. */
    fun setMessage(value: EmbedMessage) = _state.update { it.copy(message = value) }

    /** Stages a new webhook password. */
    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    /** Stages a new announcement channel. */
    fun setChannel(value: Snowflake?) = _state.update { it.copy(channelId = value) }

    /**
     * Writes whichever configuration fields changed.
     *
     * Each field has its own endpoint, so partial success is possible and is
     * reported as such rather than silently clearing the dirty flag.
     */
    fun saveConfig() = viewModelScope.launch {
        val current = _state.value
        var ok = true

        if (current.message.serialize() != current.loadedMessage) {
            ok = ok && runCatching {
                post("message", jsonString(current.message.serialize()))
            }.isSuccess
        }
        if (current.password != current.loadedPassword) {
            ok = ok && runCatching { post("password", jsonString(current.password)) }.isSuccess
        }
        if (current.channelId != current.loadedChannelId) {
            ok = ok && runCatching {
                post("channel", (current.channelId?.toLongOrNull() ?: 0L).toString())
            }.isSuccess
        }

        if (ok) {
            _state.update {
                it.copy(
                    loadedMessage = it.message.serialize(),
                    loadedPassword = it.password,
                    loadedChannelId = it.channelId,
                )
            }
            postSuccess("Settings saved.")
        } else {
            postError("Some settings failed to save.")
        }
    }

    /** Grants a role for [seconds] after each vote. */
    fun addVoteRole(roleId: Snowflake, seconds: Int) = launchAction("Failed to add vote role.") {
        api.sendIgnoringBody(
            Endpoint("api/Votes/$guildId/roles/$roleId", HttpMethod.POST, jsonInt(seconds))
        )
        postSuccess("Vote role added.")
        load(refreshing = true)
    }

    /** Stops granting a role for votes. */
    fun removeVoteRole(roleId: Snowflake) = launchAction("Failed to remove vote role.") {
        api.sendIgnoringBody(
            Endpoint("api/Votes/$guildId/roles/$roleId", HttpMethod.DELETE)
        )
        _state.update { it.copy(voteRoles = it.voteRoles.filterNot { role -> role.roleId == roleId }) }
        postSuccess("Vote role removed.")
    }

    /** Changes how long a vote reward role lasts. */
    fun updateTimer(roleId: Snowflake, seconds: Int) = launchAction("Failed to update timer.") {
        api.sendIgnoringBody(
            Endpoint("api/Votes/$guildId/roles/$roleId", HttpMethod.PATCH, jsonInt(seconds))
        )
        _state.update { current ->
            current.copy(
                voteRoles = current.voteRoles.map {
                    if (it.roleId == roleId) it.copy(timer = seconds) else it
                },
            )
        }
    }

    /** Removes every vote reward role. */
    fun clearAllRoles() = launchAction("Failed to clear vote roles.") {
        api.sendIgnoringBody(Endpoint("api/Votes/$guildId/roles", HttpMethod.DELETE))
        _state.update { it.copy(voteRoles = emptyList()) }
        postSuccess("Vote roles cleared.")
    }

    private suspend fun post(tail: String, body: String) =
        api.sendIgnoringBody(Endpoint("api/Votes/$guildId/$tail", HttpMethod.POST, body))

    private suspend fun scalar(tail: String): String? = runCatching {
        api.send(Endpoint("api/Votes/$guildId/$tail"), ScalarString.serializer()).value
    }.getOrNull()
}
