package dev.mewdeko.mobile.feature.counting

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
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

/** The number sequence a counting channel expects. */
enum class CountingPattern(val raw: Int, val label: String) {
    SEQUENTIAL(0, "Sequential"),
    SKIP_MULTIPLES(1, "Skip Multiples of 5"),
    FIBONACCI(2, "Fibonacci"),
    PRIMES(3, "Primes"),
    POWERS_OF_TWO(4, "Powers of Two");

    companion object {
        /** Maps a wire value onto a pattern, defaulting to [SEQUENTIAL]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: SEQUENTIAL
    }
}

/** Which metric the counting leaderboard ranks by. */
enum class LeaderboardType(val raw: Int, val label: String) {
    CONTRIBUTIONS(0, "Contributions"),
    STREAK(1, "Streak"),
    ACCURACY(2, "Accuracy"),
}

/** A channel running the counting game. */
@Serializable
data class CountingChannelDetail(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    val currentNumber: Int = 0,
    val startNumber: Int = 0,
    val increment: Int = 1,
    @Serializable(with = SnowflakeSerializer::class) val lastUserId: Snowflake? = null,
    val lastUsername: String? = null,
    val isActive: Boolean = false,
    val highestNumber: Int = 0,
    val totalCounts: Int = 0,
)

/** Per-channel counting rules. */
@Serializable
data class CountingConfig(
    val allowRepeatedUsers: Boolean = true,
    val cooldown: Int = 0,
    val requiredRoles: String? = null,
    val bannedRoles: String? = null,
    val maxNumber: Int = 0,
    val resetOnError: Boolean = false,
    val deleteWrongMessages: Boolean = false,
    val pattern: Int = 0,
    val numberBase: Int = 10,
    val successEmote: String? = null,
    val errorEmote: String? = null,
    val enableAchievements: Boolean = false,
    val enableCompetitions: Boolean = false,
) {
    /** The typed form of [pattern]. */
    val patternType: CountingPattern get() = CountingPattern.from(pattern)

    /** Roles required to count, parsed from the delimited field. */
    val requiredRoleIds: List<Snowflake>
        get() = requiredRoles.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Roles forbidden from counting. */
    val bannedRoleIds: List<Snowflake>
        get() = bannedRoles.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** One member's counting record. */
@Serializable
data class CountingUserStats(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val avatarUrl: String? = null,
    val contributionsCount: Int = 0,
    val highestStreak: Int = 0,
    val currentStreak: Int = 0,
    val totalNumbersCounted: Int = 0,
    val errorsCount: Int = 0,
    val accuracy: Double = 0.0,
    val rank: Int? = null,
)

/** Aggregate stats for one counting channel. */
@Serializable
data class CountingChannelStats(
    val totalParticipants: Int = 0,
    val totalErrors: Int = 0,
    val milestonesReached: Int = 0,
    val averageAccuracy: Double = 0.0,
    @Serializable(with = InstantSerializer::class) val lastActivity: Instant? = null,
)

/** A page of the counting leaderboard. */
@Serializable
data class CountingLeaderboard(
    val users: List<CountingUserStats> = emptyList(),
    val totalUsers: Int = 0,
)

/** A restorable snapshot of a channel's count. */
@Serializable
data class CountingSavePoint(
    val id: Int = 0,
    val savedNumber: Int = 0,
    @Serializable(with = InstantSerializer::class) val savedAt: Instant? = null,
    val savedByUsername: String? = null,
    val reason: String? = null,
    val isActive: Boolean = false,
)

/** Counting screen state. */
data class CountingState(
    val channels: List<CountingChannelDetail> = emptyList(),
    val selectedChannelId: Snowflake? = null,
    val config: CountingConfig? = null,
    val stats: CountingChannelStats? = null,
    val leaderboard: List<CountingUserStats> = emptyList(),
    val leaderboardType: LeaderboardType = LeaderboardType.CONTRIBUTIONS,
    val savePoints: List<CountingSavePoint> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "channels",
) {
    /** The channel currently being inspected. */
    val selected: CountingChannelDetail?
        get() = channels.firstOrNull { it.channelId == selectedChannelId }
}

/** Counting game channels. */
@HiltViewModel
class CountingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(CountingState())

    /** Observable screen state. */
    val state: StateFlow<CountingState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the channel list, then the selected channel's detail. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/Counting/$guildId/channels"),
                        ListSerializer(CountingChannelDetail.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
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

            val loaded = channels.await()
            _state.update {
                it.copy(
                    channels = loaded,
                    selectedChannelId = it.selectedChannelId?.takeIf { id ->
                        loaded.any { channel -> channel.channelId == id }
                    } ?: loaded.firstOrNull()?.channelId,
                    availableChannels = textChannels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
        _state.value.selectedChannelId?.let { loadChannelDetail(it) }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Selects a counting channel and loads its detail. */
    fun selectChannel(channelId: Snowflake) = viewModelScope.launch {
        _state.update { it.copy(selectedChannelId = channelId) }
        loadChannelDetail(channelId)
    }

    /** Changes the leaderboard metric and reloads it. */
    fun setLeaderboardType(type: LeaderboardType) = viewModelScope.launch {
        _state.update { it.copy(leaderboardType = type) }
        _state.value.selectedChannelId?.let { loadLeaderboard(it, type) }
    }

    /** Turns a channel into a counting channel. */
    fun setup(channelId: Snowflake, startNumber: Int, increment: Int) =
        launchAction("Failed to set up counting.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/setup",
                    HttpMethod.POST,
                    jsonBody("startNumber" to startNumber, "increment" to increment),
                )
            )
            postSuccess("Counting channel created.")
            load(refreshing = true)
        }

    /** Resets a channel's count to a new number. */
    fun reset(channelId: Snowflake, newNumber: Int, reason: String?) =
        launchAction("Failed to reset counting.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/reset",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to userId,
                        "newNumber" to newNumber,
                        "reason" to reason,
                    ),
                )
            )
            postSuccess("Count reset to $newNumber.")
            load(refreshing = true)
        }

    /** Stops counting in a channel. */
    fun remove(channelId: Snowflake) = launchAction("Failed to remove counting channel.") {
        api.sendIgnoringBody(
            Endpoint("api/Counting/$guildId/channels/$channelId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(channels = it.channels.filterNot { channel -> channel.channelId == channelId })
        }
        postSuccess("Counting channel removed.")
    }

    /** Applies a configuration change to the selected channel. */
    fun updateConfig(channelId: Snowflake, transform: (CountingConfig) -> CountingConfig) =
        launchAction("Failed to update configuration.") {
            val current = _state.value.config ?: CountingConfig()
            val updated = transform(current)
            api.sendIgnoringBody(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/config",
                    HttpMethod.PUT,
                    jsonBody(
                        "allowRepeatedUsers" to updated.allowRepeatedUsers,
                        "cooldown" to updated.cooldown,
                        "requiredRoles" to updated.requiredRoles,
                        "bannedRoles" to updated.bannedRoles,
                        "maxNumber" to updated.maxNumber,
                        "resetOnError" to updated.resetOnError,
                        "deleteWrongMessages" to updated.deleteWrongMessages,
                        "pattern" to updated.pattern,
                        "numberBase" to updated.numberBase,
                        "successEmote" to updated.successEmote,
                        "errorEmote" to updated.errorEmote,
                        "enableAchievements" to updated.enableAchievements,
                        "enableCompetitions" to updated.enableCompetitions,
                    ),
                )
            )
            _state.update { it.copy(config = updated) }
        }

    /** Records a restorable snapshot of the current count. */
    fun createSavePoint(channelId: Snowflake, reason: String?) =
        launchAction("Failed to create save point.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/savepoints",
                    HttpMethod.POST,
                    jsonBody("userId" to userId, "reason" to reason),
                )
            )
            postSuccess("Save point created.")
            loadChannelDetail(channelId)
        }

    /** Restores the count from a save point. */
    fun restoreSavePoint(channelId: Snowflake, saveId: Int) =
        launchAction("Failed to restore save point.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/savepoints/restore",
                    HttpMethod.POST,
                    jsonBody("saveId" to saveId, "userId" to userId),
                )
            )
            postSuccess("Save point restored.")
            load(refreshing = true)
        }

    private suspend fun loadChannelDetail(channelId: Snowflake) = coroutineScope {
        val config = async {
            runCatching {
                api.send(
                    Endpoint("api/Counting/$guildId/channels/$channelId/config"),
                    CountingConfig.serializer(),
                )
            }.getOrNull()
        }
        val stats = async {
            runCatching {
                api.send(
                    Endpoint("api/Counting/$guildId/channels/$channelId/stats"),
                    CountingChannelStats.serializer(),
                )
            }.getOrNull()
        }
        val savePoints = async {
            runCatching {
                api.send(
                    Endpoint("api/Counting/$guildId/channels/$channelId/savepoints"),
                    ListSerializer(CountingSavePoint.serializer()),
                )
            }.getOrDefault(emptyList())
        }

        _state.update {
            it.copy(config = config.await(), stats = stats.await(), savePoints = savePoints.await())
        }
        loadLeaderboard(channelId, _state.value.leaderboardType)
    }

    private suspend fun loadLeaderboard(channelId: Snowflake, type: LeaderboardType) {
        val board = runCatching {
            api.send(
                Endpoint(
                    "api/Counting/$guildId/channels/$channelId/leaderboard" +
                        "?type=${type.raw}&page=1&pageSize=25"
                ),
                CountingLeaderboard.serializer(),
            )
        }.getOrNull()
        _state.update { it.copy(leaderboard = board?.users.orEmpty()) }
    }
}
