package dev.mewdeko.mobile.feature.messagestats

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** Message volume for one member. */
@Serializable
data class MessageStatsUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
)

/** Message volume for one channel. */
@Serializable
data class MessageStatsChannel(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
)

/** Guild-wide message counters plus the top members and channels. */
@Serializable
data class MessageStatsDetail(
    val enabled: Boolean = false,
    val topUsers: List<MessageStatsUser> = emptyList(),
    val topChannels: List<MessageStatsChannel> = emptyList(),
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
)

/** The full member leaderboard. */
@Serializable
data class MessageStatsLeaderboard(
    val enabled: Boolean = false,
    val leaderboard: List<MessageStatsUser> = emptyList(),
)

/** Whether message counting is switched on for the guild. */
@Serializable
data class MessageCountStatus(val enabled: Boolean = false)

/** Message stats screen state. */
data class MessageStatsState(
    val stats: MessageStatsDetail? = null,
    val leaderboard: List<MessageStatsUser> = emptyList(),
    val enabled: Boolean = false,
    val section: String = "overview",
)

/** Per-channel and per-member message activity. */
@HiltViewModel
class MessageStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(MessageStatsState())

    /** Observable screen state. */
    val state: StateFlow<MessageStatsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads counters, the leaderboard, and the enablement flag. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/stats"),
                        MessageStatsDetail.serializer(),
                    )
                }.getOrNull()
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/leaderboard?limit=25"),
                        MessageStatsLeaderboard.serializer(),
                    )
                }.getOrNull()
            }
            val status = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/status"),
                        MessageCountStatus.serializer(),
                    )
                }.getOrNull()
            }

            _state.update {
                it.copy(
                    stats = stats.await(),
                    leaderboard = leaderboard.await()?.leaderboard.orEmpty(),
                    enabled = status.await()?.enabled ?: stats.await()?.enabled ?: false,
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Turns message counting on or off. */
    fun toggleCounting() = launchAction("Failed to toggle message counting.") {
        val response = api.send(
            Endpoint("api/MessageCount/$guildId/toggle", HttpMethod.POST),
            MessageCountStatus.serializer(),
        )
        _state.update { it.copy(enabled = response.enabled) }
        postSuccess(if (response.enabled) "Counting enabled." else "Counting disabled.")
    }

    /**
     * Resets stored counts. Passing neither id clears the whole guild; passing
     * one scopes the reset to that member or channel.
     */
    fun reset(userId: Snowflake? = null, channelId: Snowflake? = null) =
        launchAction("Failed to reset counts.") {
            val query = buildList {
                userId?.toLongOrNull()?.let { add("userId=$it") }
                channelId?.toLongOrNull()?.let { add("channelId=$it") }
            }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }

            api.sendIgnoringBody(
                Endpoint("api/MessageCount/$guildId/reset$query", HttpMethod.POST)
            )
            postSuccess("Counts reset.")
            load(refreshing = true)
        }
}
