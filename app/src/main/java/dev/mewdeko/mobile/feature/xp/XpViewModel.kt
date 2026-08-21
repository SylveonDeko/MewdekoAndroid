package dev.mewdeko.mobile.feature.xp

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.model.XpLeaderboardEntry
import dev.mewdeko.mobile.core.model.XpServerStats
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** How steeply the XP required per level rises. */
enum class XpCurveType(val raw: Int, val label: String) {
    STANDARD(0, "Standard"),
    FAST(1, "Fast"),
    SLOW(2, "Slow"),
    EXPONENTIAL(3, "Exponential");

    companion object {
        /** Maps a wire value onto a curve, defaulting to [STANDARD]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: STANDARD
    }
}

/** Guild-wide XP configuration. */
@Serializable
data class XpSettings(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val xpMultiplier: Double = 1.0,
    val xpPerMessage: Int = 5,
    val messageXpCooldown: Int = 60,
    val voiceXpPerMinute: Int = 2,
    val voiceXpTimeout: Int = 0,
    val firstMessageBonus: Int = 0,
    val xpCurveType: Int = 0,
    val xpGainDisabled: Boolean = false,
    val customXpImageUrl: String = "",
    val levelUpMessage: String = "",
    @Serializable(with = SnowflakeSerializer::class) val levelUpChannel: Snowflake = "0",
    val exclusiveRoleRewards: Boolean = false,
    val enableXpDecay: Boolean = false,
    val inactivityDaysBeforeDecay: Int = 30,
    val dailyDecayPercentage: Double = 0.0,
) {
    /** The typed form of [xpCurveType]. */
    val curve: XpCurveType get() = XpCurveType.from(xpCurveType)
}

/** A role granted at a level threshold. */
@Serializable
data class XpRoleRewardModel(
    val id: Int = 0,
    val level: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val roleName: String? = null,
)

/** A currency payout at a level threshold. */
@Serializable
data class XpCurrencyRewardModel(
    val id: Int = 0,
    val level: Int = 0,
    val amount: Long = 0L,
)

/** XP screen state. */
data class XpState(
    val settings: XpSettings = XpSettings(),
    val loadedSettings: XpSettings = XpSettings(),
    val leaderboard: List<XpLeaderboardEntry> = emptyList(),
    val leaderboardPage: Int = 1,
    val roleRewards: List<XpRoleRewardModel> = emptyList(),
    val currencyRewards: List<XpCurrencyRewardModel> = emptyList(),
    val excludedChannels: List<Snowflake> = emptyList(),
    val excludedRoles: List<Snowflake> = emptyList(),
    val serverStats: XpServerStats? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "leaderboard",
) {
    /** Whether the configuration differs from what the server has. */
    val hasUnsavedSettings: Boolean get() = settings != loadedSettings
}

/** Leveling, leaderboard, and rewards. */
@HiltViewModel
class XpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(XpState())

    /** Observable screen state. */
    val state: StateFlow<XpState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads settings, leaderboard, rewards, and exclusions. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val page = _state.value.leaderboardPage
        coroutineScope {
            val settings = async {
                runCatching {
                    api.send(Endpoint("api/Xp/$guildId/settings"), XpSettings.serializer())
                }.getOrDefault(XpSettings())
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/xp/$guildId/leaderboard?page=$page&pageSize=25"),
                        ListSerializer(XpLeaderboardEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val roleRewards = async {
                runCatching {
                    api.send(
                        Endpoint("api/Xp/$guildId/rewards/roles"),
                        ListSerializer(XpRoleRewardModel.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val currencyRewards = async {
                runCatching {
                    api.send(
                        Endpoint("api/Xp/$guildId/rewards/currency"),
                        ListSerializer(XpCurrencyRewardModel.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val excludedChannels = async { idList("api/Xp/$guildId/excluded/channels") }
            val excludedRoles = async { idList("api/Xp/$guildId/excluded/roles") }
            val stats = async {
                runCatching {
                    api.send(Endpoint("api/xp/$guildId/stats"), XpServerStats.serializer())
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

            val loaded = settings.await()
            _state.update {
                it.copy(
                    settings = loaded,
                    loadedSettings = loaded,
                    leaderboard = leaderboard.await().sortedBy { entry -> entry.rank },
                    roleRewards = roleRewards.await().sortedBy { reward -> reward.level },
                    currencyRewards = currencyRewards.await().sortedBy { reward -> reward.level },
                    excludedChannels = excludedChannels.await(),
                    excludedRoles = excludedRoles.await(),
                    serverStats = stats.await(),
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

    /** Applies an edit to the staged settings. */
    fun edit(transform: (XpSettings) -> XpSettings) =
        _state.update { it.copy(settings = transform(it.settings)) }

    /** Sets the level-up announcement template. */
    fun setLevelUpMessage(message: EmbedMessage) =
        edit { it.copy(levelUpMessage = message.serialize()) }

    /** Loads another page of the leaderboard. */
    fun setPage(page: Int) = viewModelScope.launch {
        val safePage = page.coerceAtLeast(1)
        _state.update { it.copy(leaderboardPage = safePage) }
        val entries = runCatching {
            api.send(
                Endpoint("api/xp/$guildId/leaderboard?page=$safePage&pageSize=25"),
                ListSerializer(XpLeaderboardEntry.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(leaderboard = entries.sortedBy { entry -> entry.rank }) }
    }

    /** Writes the staged settings. */
    fun saveSettings() = launchAction("Failed to save settings.") {
        val current = _state.value.settings
        val updated = api.send(
            Endpoint(
                "api/Xp/$guildId/settings",
                HttpMethod.POST,
                jsonBody(
                    "id" to current.id,
                    "guildId" to (guildId.toLongOrNull() ?: 0L),
                    "xpMultiplier" to current.xpMultiplier,
                    "xpPerMessage" to current.xpPerMessage,
                    "messageXpCooldown" to current.messageXpCooldown,
                    "voiceXpPerMinute" to current.voiceXpPerMinute,
                    "voiceXpTimeout" to current.voiceXpTimeout,
                    "firstMessageBonus" to current.firstMessageBonus,
                    "xpCurveType" to current.xpCurveType,
                    "xpGainDisabled" to current.xpGainDisabled,
                    "customXpImageUrl" to current.customXpImageUrl,
                    "levelUpMessage" to current.levelUpMessage,
                    "levelUpChannel" to (current.levelUpChannel.toLongOrNull() ?: 0L),
                    "exclusiveRoleRewards" to current.exclusiveRoleRewards,
                    "enableXpDecay" to current.enableXpDecay,
                    "inactivityDaysBeforeDecay" to current.inactivityDaysBeforeDecay,
                    "dailyDecayPercentage" to current.dailyDecayPercentage,
                ),
            ),
            XpSettings.serializer(),
        )
        _state.update { it.copy(settings = updated, loadedSettings = updated) }
        postSuccess("XP settings saved.")
    }

    /** Grants a role at a level. */
    fun addRoleReward(level: Int, roleId: Snowflake) = launchAction("Failed to add role reward.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Xp/$guildId/rewards/roles",
                HttpMethod.POST,
                jsonBody(
                    "guildId" to (guildId.toLongOrNull() ?: 0L),
                    "level" to level,
                    "roleId" to (roleId.toLongOrNull() ?: 0L),
                ),
            )
        )
        postSuccess("Role reward added.")
        load(refreshing = true)
    }

    /** Removes a role reward. */
    fun removeRoleReward(rewardId: Int) = launchAction("Failed to remove role reward.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/rewards/roles/$rewardId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(roleRewards = it.roleRewards.filterNot { reward -> reward.id == rewardId })
        }
    }

    /** Pays currency at a level. */
    fun addCurrencyReward(level: Int, amount: Int) =
        launchAction("Failed to add currency reward.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Xp/$guildId/rewards/currency",
                    HttpMethod.POST,
                    jsonBody(
                        "guildId" to (guildId.toLongOrNull() ?: 0L),
                        "level" to level,
                        "amount" to amount,
                    ),
                )
            )
            postSuccess("Currency reward added.")
            load(refreshing = true)
        }

    /** Removes a currency reward. */
    fun removeCurrencyReward(rewardId: Int) = launchAction("Failed to remove currency reward.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/rewards/currency/$rewardId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                currencyRewards = it.currencyRewards.filterNot { reward -> reward.id == rewardId },
            )
        }
    }

    /** Adds or removes a channel from the XP exclusion list. */
    fun toggleExcludedChannel(channelId: Snowflake) =
        launchAction("Failed to update excluded channels.") {
            val excluded = channelId in _state.value.excludedChannels
            if (excluded) {
                api.sendIgnoringBody(
                    Endpoint("api/Xp/$guildId/excluded/channels/$channelId", HttpMethod.DELETE)
                )
            } else {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/Xp/$guildId/excluded/channels",
                        HttpMethod.POST,
                        (channelId.toLongOrNull() ?: 0L).toString(),
                    )
                )
            }
            _state.update {
                it.copy(
                    excludedChannels = if (excluded) it.excludedChannels - channelId
                    else it.excludedChannels + channelId,
                )
            }
        }

    /** Adds or removes a role from the XP exclusion list. */
    fun toggleExcludedRole(roleId: Snowflake) = launchAction("Failed to update excluded roles.") {
        val excluded = roleId in _state.value.excludedRoles
        if (excluded) {
            api.sendIgnoringBody(
                Endpoint("api/Xp/$guildId/excluded/roles/$roleId", HttpMethod.DELETE)
            )
        } else {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Xp/$guildId/excluded/roles",
                    HttpMethod.POST,
                    (roleId.toLongOrNull() ?: 0L).toString(),
                )
            )
        }
        _state.update {
            it.copy(
                excludedRoles = if (excluded) it.excludedRoles - roleId
                else it.excludedRoles + roleId,
            )
        }
    }

    /** Overwrites one member's XP total. */
    fun setUserXp(memberId: Snowflake, amount: Long) = launchAction("Failed to set XP.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/user/$memberId/set", HttpMethod.POST, amount.toString())
        )
        postSuccess("XP set.")
        load(refreshing = true)
    }

    /** Adds to one member's XP total. */
    fun addUserXp(memberId: Snowflake, amount: Int) = launchAction("Failed to add XP.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/user/$memberId/add", HttpMethod.POST, amount.toString())
        )
        postSuccess("XP added.")
        load(refreshing = true)
    }

    /** Clears one member's XP, optionally including bonus XP. */
    fun resetUserXp(memberId: Snowflake, resetBonus: Boolean) = launchAction("Failed to reset XP.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Xp/$guildId/user/$memberId/reset",
                HttpMethod.POST,
                jsonBool(resetBonus),
            )
        )
        postSuccess("XP reset.")
        load(refreshing = true)
    }

    private suspend fun idList(path: String): List<Snowflake> = runCatching {
        (api.sendRaw(Endpoint(path)) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id != "0" } }
            .orEmpty()
    }.getOrDefault(emptyList())
}
