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
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import javax.inject.Inject

/**
 * How steeply the XP required per level rises. Wire values and labels mirror
 * the dashboard's curve selector, which uses the bot's `XpCurveType` enum
 * values (0, 1, 2, 3, 5; 4 is a formula-driven curve the dashboard does not
 * expose either).
 */
enum class XpCurveType(val raw: Int, val label: String) {
    STANDARD(0, "Default"),
    LINEAR(1, "Linear"),
    ACCELERATED(2, "Quadratic"),
    DECELERATED(3, "Exponential"),
    LEGACY(5, "Legacy");

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

/** Aggregate XP statistics for the whole guild, including recent activity. */
@Serializable
data class XpServerStatsFull(
    val totalUsers: Int = 0,
    val totalXp: Long = 0L,
    val averageLevel: Double = 0.0,
    val highestLevel: Int = 0,
    val recentActivity: List<XpRecentActivity> = emptyList(),
)

/** One row in the recent XP activity feed. */
@Serializable
data class XpRecentActivity(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val avatarUrl: String? = null,
    @Serializable(with = InstantSerializer::class) val timestamp: Instant = Instant.EPOCH,
)

/** How long a member has held their current level. */
@Serializable
data class XpTimeOnLevel(
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
)

/** One member's own XP standing, used to render the rank card preview. */
@Serializable
data class XpUserPreview(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val totalXp: Long = 0L,
    val level: Int = 0,
    val levelXp: Long = 0L,
    val requiredXp: Long = 0L,
    val rank: Int = 0,
    val bonusXp: Long = 0L,
    val username: String = "You",
    val avatarUrl: String? = null,
    val timeOnLevel: XpTimeOnLevel = XpTimeOnLevel(),
)

/**
 * A point-in-time copy of the rank card template used to undo or redo a
 * property edit, a layer add/remove, or a built-in element change.
 */
data class XpTemplateSnapshot(
    val template: XpTemplate,
    val customElements: List<XpCustomElement>,
    val builtInOrder: List<String>,
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
    val serverStats: XpServerStatsFull? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "leaderboard",
    val template: XpTemplate = XpTemplate(),
    val loadedTemplate: XpTemplate = XpTemplate(),
    val customElements: List<XpCustomElement> = emptyList(),
    val loadedCustomElements: List<XpCustomElement> = emptyList(),
    val builtInOrder: List<String> = DefaultBuiltInOrder,
    val loadedBuiltInOrder: List<String> = DefaultBuiltInOrder,
    val viewerPreview: XpUserPreview? = null,
    val templateUndoStack: List<XpTemplateSnapshot> = emptyList(),
    val templateRedoStack: List<XpTemplateSnapshot> = emptyList(),
) {
    /** Whether the configuration differs from what the server has. */
    val hasUnsavedSettings: Boolean get() = settings != loadedSettings

    /** Whether the rank card template differs from what the server has. */
    val hasUnsavedTemplate: Boolean
        get() = template != loadedTemplate ||
            customElements != loadedCustomElements ||
            builtInOrder != loadedBuiltInOrder
}

/** Leveling, leaderboard, rewards, and rank card template. */
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

    /** Reloads settings, leaderboard, rewards, exclusions, and the rank card template. */
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
                    api.send(Endpoint("api/xp/$guildId/stats"), XpServerStatsFull.serializer())
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
            val template = async {
                runCatching {
                    api.send(Endpoint("api/Xp/$guildId/template"), XpTemplate.serializer())
                }.getOrNull()
            }
            val viewerPreview = async {
                runCatching {
                    api.send(Endpoint("api/Xp/$guildId/user/$userId"), XpUserPreview.serializer())
                }.getOrNull()
            }

            val loaded = settings.await()
            val loadedTemplate = template.await() ?: XpTemplate(guildId = guildId)
            val loadedCustom = decodeCustomElements(loadedTemplate.customElementsJson)
            val loadedOrder = decodeBuiltInOrder(loadedTemplate.builtInOrderJson)
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
                    template = loadedTemplate,
                    loadedTemplate = loadedTemplate,
                    customElements = loadedCustom,
                    loadedCustomElements = loadedCustom,
                    builtInOrder = loadedOrder,
                    loadedBuiltInOrder = loadedOrder,
                    viewerPreview = viewerPreview.await(),
                    templateUndoStack = emptyList(),
                    templateRedoStack = emptyList(),
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

    /** Removes a role reward. The bot keys removal on the level, not the row id. */
    fun removeRoleReward(level: Int) = launchAction("Failed to remove role reward.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/rewards/roles/$level", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(roleRewards = it.roleRewards.filterNot { reward -> reward.level == level })
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

    /** Removes a currency reward. The bot keys removal on the level, not the row id. */
    fun removeCurrencyReward(level: Int) = launchAction("Failed to remove currency reward.") {
        api.sendIgnoringBody(
            Endpoint("api/Xp/$guildId/rewards/currency/$level", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                currencyRewards = it.currencyRewards.filterNot { reward -> reward.level == level },
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

    /** Applies an edit to the staged template's top-level fields. */
    fun editTemplate(transform: (XpTemplate) -> XpTemplate) =
        _state.update { it.copy(template = transform(it.template)) }

    /** Applies an undoable edit to the staged avatar/username element. */
    fun editTemplateUser(transform: (XpTemplateUser) -> XpTemplateUser) {
        pushTemplateUndo()
        editTemplate { it.copy(templateUser = transform(it.templateUser)) }
    }

    /** Applies an undoable edit to the staged XP progress bar. */
    fun editTemplateBar(transform: (XpTemplateBar) -> XpTemplateBar) {
        pushTemplateUndo()
        editTemplate { it.copy(templateBar = transform(it.templateBar)) }
    }

    /** Applies an undoable edit to the staged guild rank/level text. */
    fun editTemplateGuild(transform: (XpTemplateGuild) -> XpTemplateGuild) {
        pushTemplateUndo()
        editTemplate { it.copy(templateGuild = transform(it.templateGuild)) }
    }

    /**
     * Applies an undoable edit to a top-level template field (time on level,
     * awarded XP). Callers that mutate per keystroke, such as the card size
     * fields, should call [editTemplate] directly instead so every character
     * typed does not become its own undo step.
     */
    fun editTemplateFieldUndoable(transform: (XpTemplate) -> XpTemplate) {
        pushTemplateUndo()
        editTemplate(transform)
    }

    /** Adds a new custom element of [type] on top of the stack. */
    fun addCustomElement(type: XpCustomElementType) {
        pushTemplateUndo()
        val id = "custom-${java.util.UUID.randomUUID()}"
        val isLine = type == XpCustomElementType.LINE
        val element = XpCustomElement(
            id = id,
            type = type.raw,
            label = type.label,
            zIndex = _state.value.customElements.size,
            width = if (isLine) 180.0 else 140.0,
            height = if (isLine) 0.0 else 64.0,
            cornerRadius = if (type == XpCustomElementType.RECTANGLE) 12.0 else 0.0,
            fill = if (type == XpCustomElementType.TEXT) "#FFFFFF" else "#5865F2",
            strokeWidth = if (isLine) 4.0 else 0.0,
            text = if (type == XpCustomElementType.TEXT) {
                "Level %xp.level.current% • Rank #%xp.rank%"
            } else {
                ""
            },
        )
        _state.update {
            it.copy(customElements = it.customElements + element)
        }
    }

    /** Applies an undoable edit to one custom element. */
    fun updateCustomElement(id: String, transform: (XpCustomElement) -> XpCustomElement) {
        pushTemplateUndo()
        _state.update {
            it.copy(
                customElements = it.customElements.map { element ->
                    if (element.id == id) transform(element) else element
                },
            )
        }
    }

    /** Removes a custom element. */
    fun removeCustomElement(id: String) {
        pushTemplateUndo()
        _state.update {
            it.copy(customElements = it.customElements.filterNot { element -> element.id == id })
        }
    }

    /** Duplicates a custom element, offsetting the copy slightly. */
    fun duplicateCustomElement(id: String) {
        val source = _state.value.customElements.firstOrNull { it.id == id } ?: return
        pushTemplateUndo()
        val copy = source.copy(
            id = "custom-${java.util.UUID.randomUUID()}",
            label = "${source.label} copy",
            x = source.x + 20,
            y = source.y + 20,
            zIndex = _state.value.customElements.size,
        )
        _state.update { it.copy(customElements = it.customElements + copy) }
    }

    /** Moves a custom element up (`direction = -1`) or down (`direction = 1`) the stack. */
    fun moveCustomElement(id: String, direction: Int) {
        val items = _state.value.customElements.toMutableList()
        val index = items.indexOfFirst { it.id == id }
        val target = index + direction
        if (index < 0 || target < 0 || target >= items.size) return
        pushTemplateUndo()
        val moved = items.removeAt(index)
        items.add(target, moved)
        _state.update {
            it.copy(customElements = items.mapIndexed { zIndex, element -> element.copy(zIndex = zIndex) })
        }
    }

    /** Moves a built-in element's position in the draw order. */
    fun moveBuiltInElement(id: String, direction: Int) {
        val items = _state.value.builtInOrder.toMutableList()
        val index = items.indexOf(id)
        val target = index + direction
        if (index < 0 || target < 0 || target >= items.size) return
        pushTemplateUndo()
        val moved = items.removeAt(index)
        items.add(target, moved)
        _state.update { it.copy(builtInOrder = items) }
    }

    /** Replaces the custom elements with one of the built-in starter layouts. */
    fun applyTemplatePreset(name: String) {
        pushTemplateUndo()
        val presets: Map<String, List<XpCustomElement>> = mapOf(
            "minimal" to listOf(
                XpCustomElement(
                    type = "text", label = "Level and rank", x = 130.0, y = 90.0, width = 360.0,
                    height = 36.0, fill = "#FFFFFF",
                    text = "Level %xp.level.current%  •  Rank #%xp.rank%", fontSize = 26.0,
                ),
                XpCustomElement(
                    type = "progress", label = "XP progress", x = 130.0, y = 140.0, width = 540.0,
                    height = 18.0, fill = "#5865F2", trackFill = "#FFFFFF30", cornerRadius = 9.0,
                ),
            ),
            "glass" to listOf(
                XpCustomElement(
                    type = "rectangle", label = "Glass panel", x = 110.0, y = 45.0, width = 610.0,
                    height = 190.0, fill = "#111827B8", stroke = "#FFFFFF30", strokeWidth = 1.0,
                    cornerRadius = 24.0, shadowBlur = 16.0, shadowY = 8.0,
                ),
                XpCustomElement(
                    type = "text", label = "Profile heading", x = 145.0, y = 76.0, width = 480.0,
                    height = 40.0, fill = "#FFFFFF", text = "%xp.user.displayname%", fontSize = 30.0,
                ),
                XpCustomElement(
                    type = "progress", label = "XP progress", x = 145.0, y = 160.0, width = 520.0,
                    height = 20.0, fill = "#7C3AED", gradientEnd = "#22D3EE", trackFill = "#FFFFFF25",
                    cornerRadius = 10.0,
                ),
            ),
            "gaming" to listOf(
                XpCustomElement(
                    type = "rectangle", label = "Rank plate", x = 485.0, y = 38.0, width = 250.0,
                    height = 72.0, fill = "#EF4444", gradientEnd = "#F59E0B", cornerRadius = 8.0,
                    rotation = -2.0,
                ),
                XpCustomElement(
                    type = "text", label = "Rank", x = 505.0, y = 52.0, width = 210.0, height = 42.0,
                    fill = "#FFFFFF", text = "RANK  #%xp.rank%", fontSize = 30.0, textAlign = "center",
                ),
                XpCustomElement(
                    type = "progress", label = "Segmented XP", x = 130.0, y = 205.0, width = 590.0,
                    height = 22.0, fill = "#F59E0B", trackFill = "#FFFFFF25", progressStyle = "segmented",
                    segments = 12, cornerRadius = 3.0,
                ),
            ),
        )
        val chosen = presets[name] ?: return
        val withIds = chosen.mapIndexed { index, element ->
            element.copy(id = "custom-${java.util.UUID.randomUUID()}", zIndex = index)
        }
        _state.update { it.copy(customElements = withIds) }
    }

    /**
     * Serialises the staged card as `{version, outputSizeX, outputSizeY,
     * customElements}`, the same shape the dashboard's template editor
     * exports and imports.
     */
    fun exportTemplateJson(): String {
        val current = _state.value
        val elements = MewdekoJson.encodeToJsonElement(
            ListSerializer(XpCustomElement.serializer()),
            current.customElements,
        )
        val payload = JsonObject(
            mapOf(
                "version" to JsonPrimitive(1),
                "outputSizeX" to JsonPrimitive(current.template.outputSizeX),
                "outputSizeY" to JsonPrimitive(current.template.outputSizeY),
                "customElements" to elements,
            ),
        )
        return MewdekoJson.encodeToString(JsonObject.serializer(), payload)
    }

    /**
     * Replaces the staged custom elements (and card size, if present) from
     * exported JSON. Accepts the dashboard's `{version, outputSizeX,
     * outputSizeY, customElements}` object as well as a bare layer array, for
     * backward compatibility with older exports from this app. Returns
     * whether the JSON was understood.
     */
    fun importTemplateJson(json: String): Boolean {
        val root = runCatching { MewdekoJson.parseToJsonElement(json) }.getOrNull() ?: return false
        val elementsJson: JsonArray
        var sizeX: Int? = null
        var sizeY: Int? = null
        when (root) {
            is JsonArray -> elementsJson = root
            is JsonObject -> {
                elementsJson = root["customElements"] as? JsonArray ?: return false
                sizeX = (root["outputSizeX"] as? JsonPrimitive)?.intOrNull
                sizeY = (root["outputSizeY"] as? JsonPrimitive)?.intOrNull
            }
            else -> return false
        }
        val parsed = runCatching {
            MewdekoJson.decodeFromJsonElement(ListSerializer(XpCustomElement.serializer()), elementsJson)
        }.getOrNull() ?: return false
        pushTemplateUndo()
        val withIds = parsed.mapIndexed { index, element ->
            element.copy(id = "custom-${java.util.UUID.randomUUID()}", zIndex = index)
        }
        _state.update {
            it.copy(
                customElements = withIds,
                template = it.template.copy(
                    outputSizeX = sizeX ?: it.template.outputSizeX,
                    outputSizeY = sizeY ?: it.template.outputSizeY,
                ),
            )
        }
        return true
    }

    /** Reverts the most recent template edit, staging it onto the redo stack. */
    fun undoTemplate() {
        val current = _state.value
        val last = current.templateUndoStack.lastOrNull() ?: return
        _state.update {
            it.copy(
                template = last.template,
                customElements = last.customElements,
                builtInOrder = last.builtInOrder,
                templateUndoStack = it.templateUndoStack.dropLast(1),
                templateRedoStack = (it.templateRedoStack + snapshotOf(current))
                    .takeLast(TemplateUndoLimit),
            )
        }
    }

    /** Re-applies the most recently undone template edit. */
    fun redoTemplate() {
        val current = _state.value
        val next = current.templateRedoStack.lastOrNull() ?: return
        _state.update {
            it.copy(
                template = next.template,
                customElements = next.customElements,
                builtInOrder = next.builtInOrder,
                templateRedoStack = it.templateRedoStack.dropLast(1),
                templateUndoStack = (it.templateUndoStack + snapshotOf(current))
                    .takeLast(TemplateUndoLimit),
            )
        }
    }

    /** Discards every staged template change, reverting to what the server has. */
    fun resetTemplateChanges() {
        _state.update {
            it.copy(
                template = it.loadedTemplate,
                customElements = it.loadedCustomElements,
                builtInOrder = it.loadedBuiltInOrder,
                templateUndoStack = emptyList(),
                templateRedoStack = emptyList(),
            )
        }
    }

    /** Writes the staged rank card template. */
    fun saveTemplate() = launchAction("Failed to save the rank card template.") {
        val current = _state.value
        val toSend = current.template.copy(
            customElementsJson = MewdekoJson.encodeToString(
                ListSerializer(XpCustomElement.serializer()),
                current.customElements,
            ),
            builtInOrderJson = MewdekoJson.encodeToString(
                ListSerializer(String.serializer()),
                current.builtInOrder,
            ),
        )
        val encoded = MewdekoJson.encodeToJsonElement(XpTemplate.serializer(), toSend)
        val patched = JsonObject(
            (encoded as JsonObject).toMutableMap().apply {
                this["guildId"] = JsonPrimitive(guildId.toLongOrNull() ?: 0L)
            },
        )
        api.sendIgnoringBody(
            Endpoint(
                "api/Xp/$guildId/template",
                HttpMethod.POST,
                MewdekoJson.encodeToString(JsonObject.serializer(), patched),
            )
        )
        _state.update {
            it.copy(
                template = toSend,
                loadedTemplate = toSend,
                loadedCustomElements = current.customElements,
                loadedBuiltInOrder = current.builtInOrder,
                templateUndoStack = emptyList(),
                templateRedoStack = emptyList(),
            )
        }
        postSuccess("Rank card template saved.")
    }

    /**
     * Snapshots the staged template so the change about to be made can be
     * undone, and clears the redo stack, since it now describes a future
     * that no longer follows from the current state.
     */
    private fun pushTemplateUndo() {
        val current = _state.value
        _state.update {
            it.copy(
                templateUndoStack = (it.templateUndoStack + snapshotOf(current))
                    .takeLast(TemplateUndoLimit),
                templateRedoStack = emptyList(),
            )
        }
    }

    private fun snapshotOf(state: XpState) = XpTemplateSnapshot(
        template = state.template,
        customElements = state.customElements,
        builtInOrder = state.builtInOrder,
    )

    private fun decodeCustomElements(json: String?): List<XpCustomElement> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            MewdekoJson.decodeFromString(ListSerializer(XpCustomElement.serializer()), json)
        }.getOrDefault(emptyList())
    }

    private fun decodeBuiltInOrder(json: String?): List<String> {
        if (json.isNullOrBlank()) return DefaultBuiltInOrder
        val saved = runCatching {
            MewdekoJson.decodeFromString(ListSerializer(String.serializer()), json)
        }.getOrDefault(emptyList())
        val known = saved.filter { it in DefaultBuiltInOrder }
        return known + DefaultBuiltInOrder.filterNot { it in known }
    }

    private suspend fun idList(path: String): List<Snowflake> = runCatching {
        (api.sendRaw(Endpoint(path)) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id != "0" } }
            .orEmpty()
    }.getOrDefault(emptyList())
}

private const val TemplateUndoLimit = 20
