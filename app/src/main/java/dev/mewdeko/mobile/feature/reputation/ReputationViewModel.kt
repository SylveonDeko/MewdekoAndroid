package dev.mewdeko.mobile.feature.reputation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
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

/** Reputation configuration for a guild. */
@Serializable
data class RepConfig(
    val enabled: Boolean = false,
    val defaultCooldownMinutes: Int = 60,
    val dailyLimit: Int = 0,
    val weeklyLimit: Int? = null,
    val minAccountAgeDays: Int = 0,
    val minServerMembershipHours: Int = 0,
    val minMessageCount: Int = 0,
    val enableNegativeRep: Boolean = false,
    val enableAnonymous: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val notificationChannel: Snowflake? = null,
)

/** One entry in the reputation leaderboard. */
@Serializable
data class RepLeaderboardEntry(
    val rank: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val reputation: Int = 0,
)

/** A role granted once a member reaches a reputation threshold. */
@Serializable
data class RepRoleReward(
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val roleName: String = "Unknown",
    val repRequired: Int = 0,
    val removeOnDrop: Boolean = false,
    val xpReward: Int = 0,
)

/** Aggregate reputation counters. */
@Serializable
data class RepStats(
    val totalUsers: Int = 0,
    val totalRepGiven: Int = 0,
    val totalTransactions: Int = 0,
    val averageRepPerUser: Int = 0,
)

/** Reputation screen state. */
data class ReputationState(
    val config: RepConfig = RepConfig(),
    val leaderboard: List<RepLeaderboardEntry> = emptyList(),
    val rewards: List<RepRoleReward> = emptyList(),
    val stats: RepStats? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "settings",
)

/** Member-to-member reputation. */
@HiltViewModel
class ReputationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ReputationState())

    /** Observable screen state. */
    val state: StateFlow<ReputationState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads configuration, leaderboard, rewards, and stats. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val config = async {
                runCatching {
                    api.send(Endpoint("api/Reputation/$guildId/config"), RepConfig.serializer())
                }.getOrDefault(RepConfig())
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/Reputation/$guildId/leaderboard?page=1&pageSize=50"),
                        ListSerializer(RepLeaderboardEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val rewards = async {
                runCatching {
                    api.send(
                        Endpoint("api/Reputation/$guildId/roleRewards"),
                        ListSerializer(RepRoleReward.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(Endpoint("api/Reputation/$guildId/stats"), RepStats.serializer())
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

            _state.update {
                it.copy(
                    config = config.await(),
                    leaderboard = leaderboard.await().sortedBy { entry -> entry.rank },
                    rewards = rewards.await().sortedBy { reward -> reward.repRequired },
                    stats = stats.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Turns the reputation system on or off. */
    fun setEnabled(value: Boolean) = setting("enabled", jsonBool(value)) { it.copy(enabled = value) }

    /** Sets the minimum gap between two reputation gifts from one member. */
    fun setCooldown(minutes: Int) =
        setting("cooldown", jsonInt(minutes)) { it.copy(defaultCooldownMinutes = minutes) }

    /** Sets how much reputation a member may give per day. */
    fun setDailyLimit(limit: Int) =
        setting("dailyLimit", jsonInt(limit)) { it.copy(dailyLimit = limit) }

    /** Sets how much reputation a member may give per week; null means unlimited. */
    fun setWeeklyLimit(limit: Int?) =
        setting("weeklyLimit", limit?.let { jsonInt(it) } ?: "null") { it.copy(weeklyLimit = limit) }

    /** Sets the minimum account age required to give reputation. */
    fun setMinAccountAge(days: Int) =
        setting("minAccountAge", jsonInt(days)) { it.copy(minAccountAgeDays = days) }

    /** Sets the minimum time in the server required to give reputation. */
    fun setMinServerMembership(hours: Int) =
        setting("minServerMembership", jsonInt(hours)) { it.copy(minServerMembershipHours = hours) }

    /** Sets the minimum message count required to give reputation. */
    fun setMinMessageCount(count: Int) =
        setting("minMessageCount", jsonInt(count)) { it.copy(minMessageCount = count) }

    /** Allows or forbids removing reputation. */
    fun setNegativeRep(value: Boolean) =
        setting("negativeRep", jsonBool(value)) { it.copy(enableNegativeRep = value) }

    /** Allows or forbids anonymous reputation gifts. */
    fun setAnonymous(value: Boolean) =
        setting("anonymousRep", jsonBool(value)) { it.copy(enableAnonymous = value) }

    /** Sets where reputation changes are announced. */
    fun setNotificationChannel(id: Snowflake?) =
        setting("notificationChannel", id?.let { jsonString(it) } ?: "null") {
            it.copy(notificationChannel = id)
        }

    /** Creates or updates a role reward. */
    fun upsertRoleReward(
        roleId: Snowflake,
        repRequired: Int,
        removeOnDrop: Boolean,
        announceChannel: Snowflake?,
        announceDm: Boolean,
        xpReward: Int,
    ) = launchAction("Failed to save role reward.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Reputation/$guildId/roleRewards",
                HttpMethod.POST,
                jsonBody(
                    "roleId" to roleId,
                    "repRequired" to repRequired,
                    "removeOnDrop" to removeOnDrop,
                    "announceDM" to announceDm,
                    "xpReward" to xpReward,
                    "announceChannelId" to announceChannel,
                ),
            )
        )
        postSuccess("Role reward saved.")
        load(refreshing = true)
    }

    /** Deletes a role reward. */
    fun removeRoleReward(roleId: Snowflake) = launchAction("Failed to remove role reward.") {
        api.sendIgnoringBody(
            Endpoint("api/Reputation/$guildId/roleRewards/$roleId", HttpMethod.DELETE)
        )
        _state.update { it.copy(rewards = it.rewards.filterNot { reward -> reward.roleId == roleId }) }
        postSuccess("Role reward removed.")
    }

    private fun setting(
        tail: String,
        body: String,
        transform: (RepConfig) -> RepConfig,
    ) = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/Reputation/$guildId/$tail", HttpMethod.POST, body)
        )
        _state.update { it.copy(config = transform(it.config)) }
    }
}
