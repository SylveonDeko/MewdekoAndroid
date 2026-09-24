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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.doubleOrNull
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
 * A point-in-time copy of the whole staged card (template, custom layers,
 * built-in order, and background URL) used to undo or redo one edit.
 */
data class XpTemplateSnapshot(
    val template: XpTemplate,
    val customElements: List<XpCustomElement>,
    val builtInOrder: List<String>,
    val backgroundUrl: String,
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

    /** Whether the staged card background URL differs from the saved one. */
    val hasUnsavedBackground: Boolean
        get() = settings.customXpImageUrl != loadedSettings.customXpImageUrl

    /** Whether the rank card (template, layers, order, or background) differs from what the server has. */
    val hasUnsavedTemplate: Boolean
        get() = template != loadedTemplate ||
            customElements != loadedCustomElements ||
            builtInOrder != loadedBuiltInOrder ||
            hasUnsavedBackground

    /** The member the card previews with real data, when the viewer has an XP row. */
    fun realCardData(guildName: String, guildId: String): XpCardData? = viewerPreview?.let {
        XpCardData(
            username = it.username,
            displayName = it.username,
            nickname = it.username,
            avatarUrl = it.avatarUrl?.takeIf { url -> url.isNotBlank() },
            level = it.level,
            rank = it.rank,
            totalXp = it.totalXp,
            levelXp = it.levelXp,
            requiredXp = it.requiredXp,
            bonusXp = it.bonusXp,
            timeOnLevel = it.timeOnLevel.normalized(),
            clubName = "Elite Gamers",
            guildName = guildName,
            userId = it.userId,
            guildId = guildId,
        )
    }
}

/** Leveling, leaderboard, rewards, and rank card template. */
@HiltViewModel
class XpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(XpState())

    /**
     * Runs exclusion toggles one after another, so a multi-select change
     * that adds or removes several ids at once (Clear, for one) reads each
     * id's current state before it decides between add and remove.
     */
    private val exclusionLock = Mutex()

    /** Observable screen state. */
    val state: StateFlow<XpState> = _state.asStateFlow()

    /** View state of the rank card designer (zoom, pan, selection, toggles). */
    val designer = XpDesignerController()

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
                .sortedBy { element -> element.zIndex }
                .mapIndexed { index, element -> element.copy(zIndex = index) }
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
        val updated = postSettings(_state.value.settings)
        _state.update { it.copy(settings = updated, loadedSettings = updated) }
        postSuccess("XP settings saved.")
    }

    /** POSTs a full settings object and returns what the bot stored. */
    private suspend fun postSettings(current: XpSettings): XpSettings =
        api.send(
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
            exclusionLock.withLock {
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
        }

    /** Adds or removes a role from the XP exclusion list. */
    fun toggleExcludedRole(roleId: Snowflake) = launchAction("Failed to update excluded roles.") {
        exclusionLock.withLock {
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

    /**
     * Adds a new custom element of [type] on top of the stack, selects it,
     * and opens its properties.
     */
    fun addCustomElement(type: XpCustomElementType) {
        pushTemplateUndo()
        val element = newCustomElement(type, _state.value.customElements.size)
        _state.update {
            it.copy(customElements = it.customElements + element)
        }
        designer.selectedId = element.id
        designer.openSheet(XpSheetTab.PROPERTIES)
    }

    /** Applies an undoable edit to one custom element. */
    fun updateCustomElement(id: String, transform: (XpCustomElement) -> XpCustomElement) {
        pushTemplateUndo()
        stageCustomElement(id, transform)
    }

    /**
     * Applies an edit to one custom element without an undo entry, for live
     * slider drags whose single entry was pushed when the drag began.
     */
    fun stageCustomElement(id: String, transform: (XpCustomElement) -> XpCustomElement) {
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
            it.copy(
                customElements = it.customElements.filterNot { element -> element.id == id }
                    .mapIndexed { index, element -> element.copy(zIndex = index) },
            )
        }
        if (designer.selectedId == id) designer.clearSelection()
    }

    /**
     * Snaps a custom element against one edge or the centre of the card:
     * `left`, `center`, `right`, `top`, `middle`, or `bottom`.
     */
    fun alignCustomElement(id: String, edge: String) {
        val size = _state.value.template
        updateCustomElement(id) {
            when (edge) {
                "left" -> it.copy(x = 0.0)
                "center" -> it.copy(x = ((size.outputSizeX - it.width) / 2).roundToIntDouble())
                "right" -> it.copy(x = (size.outputSizeX - it.width).roundToIntDouble())
                "top" -> it.copy(y = 0.0)
                "middle" -> it.copy(y = ((size.outputSizeY - it.height) / 2).roundToIntDouble())
                "bottom" -> it.copy(y = (size.outputSizeY - it.height).roundToIntDouble())
                else -> it
            }
        }
    }

    /** Pushes one undo entry for an edit about to be staged in several steps, such as a slider drag. */
    fun beginTemplateEdit() = pushTemplateUndo()

    /** The drag origin of any element: its x/y, or point A for the bar. */
    fun elementOrigin(id: String): XpPoint? {
        val current = _state.value
        current.template.builtInOrigin(id)?.let { return it }
        return current.customElements.firstOrNull { it.id == id }?.let { XpPoint(it.x.toFloat(), it.y.toFloat()) }
    }

    /** Selects [id] and pushes the single undo entry for the drag that is starting. */
    fun beginDrag(id: String) {
        designer.selectedId = id
        pushTemplateUndo()
    }

    /**
     * Moves an element to card point ([x], [y]) without an undo entry,
     * snapped to the grid when snap is on and rounded to integers otherwise.
     */
    fun setElementPosition(id: String, x: Float, y: Float) {
        val nx = snapCoordinate(x, designer.snapToGrid, designer.gridSize)
        val ny = snapCoordinate(y, designer.snapToGrid, designer.gridSize)
        if (isBuiltInId(id)) {
            _state.update { it.copy(template = it.template.withBuiltInPosition(id, nx, ny)) }
        } else {
            stageCustomElement(id) { it.copy(x = nx.toDouble(), y = ny.toDouble()) }
        }
    }

    /** A committed X/Y field edit: one undo entry, then the same snapped update a drag uses. */
    fun commitElementPosition(id: String, x: Float, y: Float) {
        pushTemplateUndo()
        setElementPosition(id, x, y)
    }

    /** Shows or hides any element, built-in or custom, as one undo entry. */
    fun setElementVisible(id: String, visible: Boolean) {
        if (isBuiltInId(id)) {
            pushTemplateUndo()
            _state.update { it.copy(template = it.template.withBuiltInShown(id, visible)) }
        } else {
            updateCustomElement(id) { it.copy(visible = visible) }
        }
    }

    /**
     * Moves a layer one step in the draw order. A positive [direction] draws
     * it later (on top). Built-ins move within the seven-id built-in order
     * and custom layers within the custom stack; neither crosses the other,
     * and the dormant club layers do not move.
     */
    fun moveLayer(id: String, direction: Int) {
        if (id in ClubBuiltInIds) return
        if (isBuiltInId(id)) moveBuiltInElement(id, direction) else moveCustomElement(id, direction)
    }

    /** Stages a new card background URL (blank means the bot's default) as one undo entry. */
    fun setBackgroundUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed == _state.value.settings.customXpImageUrl) return
        pushTemplateUndo()
        _state.update { it.copy(settings = it.settings.copy(customXpImageUrl = trimmed)) }
    }

    /**
     * Matches the stored output size to the background's natural size, the
     * size the bot actually renders at. Marks the card dirty when it
     * changes, the way both web editors do on load; not an undo entry.
     */
    fun syncOutputSizeToBackground(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val template = _state.value.template
        if (template.outputSizeX == width && template.outputSizeY == height) return
        _state.update { it.copy(template = it.template.copy(outputSizeX = width, outputSizeY = height)) }
    }

    /** Sets the stored card size as one undo entry. */
    fun setCanvasSize(width: Int, height: Int) {
        val template = _state.value.template
        if (template.outputSizeX == width && template.outputSizeY == height) return
        pushTemplateUndo()
        editTemplate { it.copy(outputSizeX = width.coerceAtLeast(1), outputSizeY = height.coerceAtLeast(1)) }
    }

    /**
     * Replaces the staged card with the bot's defaults: every built-in at its
     * default placement, an 800 by 246 card, no custom layers, and the
     * default draw order.
     */
    fun resetToBotDefaults() {
        pushTemplateUndo()
        _state.update {
            it.copy(
                template = botDefaultTemplate(it.template),
                customElements = emptyList(),
                builtInOrder = DefaultBuiltInOrder,
            )
        }
        designer.clearSelection()
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
        designer.selectedId = copy.id
    }

    /**
     * Moves a custom element through the stack: `direction = 1` draws it
     * later (on top), `-1` earlier. Array order is z order, so every
     * `zIndex` is rewritten to its index.
     */
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

    /**
     * Moves a built-in element in the draw order: `direction = 1` draws it
     * later (on top), `-1` earlier.
     */
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
        val chosen = XpTemplatePresets[name] ?: return
        pushTemplateUndo()
        val withIds = chosen.mapIndexed { index, element ->
            element.copy(id = "custom-${java.util.UUID.randomUUID()}", zIndex = index)
        }
        _state.update { it.copy(customElements = withIds) }
        designer.clearSelection()
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
                sizeX = (root["outputSizeX"] as? JsonPrimitive)?.doubleOrNull
                    ?.takeIf { it.isFinite() && it >= 1 }?.let { Math.round(it).toInt() }
                sizeY = (root["outputSizeY"] as? JsonPrimitive)?.doubleOrNull
                    ?.takeIf { it.isFinite() && it >= 1 }?.let { Math.round(it).toInt() }
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
        designer.clearSelection()
        return true
    }

    /** Reverts the most recent template edit, staging it onto the redo stack. */
    fun undoTemplate() {
        val current = _state.value
        val last = current.templateUndoStack.lastOrNull() ?: return
        _state.update {
            restore(it, last).copy(
                templateUndoStack = it.templateUndoStack.dropLast(1),
                templateRedoStack = (it.templateRedoStack + snapshotOf(current))
                    .takeLast(TemplateUndoLimit),
            )
        }
        dropVanishedSelection()
    }

    /** Re-applies the most recently undone template edit. */
    fun redoTemplate() {
        val current = _state.value
        val next = current.templateRedoStack.lastOrNull() ?: return
        _state.update {
            restore(it, next).copy(
                templateRedoStack = it.templateRedoStack.dropLast(1),
                templateUndoStack = (it.templateUndoStack + snapshotOf(current))
                    .takeLast(TemplateUndoLimit),
            )
        }
        dropVanishedSelection()
    }

    /** Discards every staged card change (template, layers, order, background), reverting to the server's. */
    fun resetTemplateChanges() {
        _state.update {
            it.copy(
                template = it.loadedTemplate,
                customElements = it.loadedCustomElements,
                builtInOrder = it.loadedBuiltInOrder,
                settings = it.settings.copy(customXpImageUrl = it.loadedSettings.customXpImageUrl),
                templateUndoStack = emptyList(),
                templateRedoStack = emptyList(),
            )
        }
        dropVanishedSelection()
    }

    /**
     * Writes the staged rank card: the full template with the layers and
     * built-in order re-serialised, then the background URL through the
     * settings endpoint when it changed. Refuses to send a built-in colour
     * the bot's `SKColor.Parse` would throw on.
     */
    fun saveTemplate() = launchAction("Failed to save the rank card template.") {
        val current = _state.value
        val invalid = current.template.invalidColorFields()
        if (invalid.isNotEmpty()) {
            postError("Fix the colour of ${invalid.joinToString()} before saving (6 or 8 hex digits).")
            return@launchAction
        }
        val staged = current.template.copy(
            templateBar = current.template.templateBar.copy(
                barTransparency = current.template.templateBar.barTransparency.coerceIn(0, 255),
            ),
        )
        val toSend = staged.copy(
            customElementsJson = MewdekoJson.encodeToString(
                ListSerializer(XpCustomElement.serializer()),
                current.customElements,
            ),
            builtInOrderJson = MewdekoJson.encodeToString(
                ListSerializer(String.serializer()),
                sanitizeBuiltInOrder(current.builtInOrder),
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
                template = staged,
                loadedTemplate = staged,
                loadedCustomElements = current.customElements,
                loadedBuiltInOrder = current.builtInOrder,
            )
        }
        if (current.hasUnsavedBackground) {
            val backgroundUrl = current.settings.customXpImageUrl
            postSettings(current.loadedSettings.copy(customXpImageUrl = backgroundUrl))
            _state.update {
                it.copy(loadedSettings = it.loadedSettings.copy(customXpImageUrl = backgroundUrl))
            }
        }
    }

    /**
     * Snapshots the staged card so the change about to be made can be
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
        backgroundUrl = state.settings.customXpImageUrl,
    )

    private fun restore(state: XpState, snapshot: XpTemplateSnapshot) = state.copy(
        template = snapshot.template,
        customElements = snapshot.customElements,
        builtInOrder = snapshot.builtInOrder,
        settings = state.settings.copy(customXpImageUrl = snapshot.backgroundUrl),
    )

    private fun dropVanishedSelection() {
        val selected = designer.selectedId ?: return
        if (!isBuiltInId(selected) && _state.value.customElements.none { it.id == selected }) {
            designer.clearSelection()
        }
    }

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
        return sanitizeBuiltInOrder(saved)
    }

    private suspend fun idList(path: String): List<Snowflake> = runCatching {
        (api.sendRaw(Endpoint(path)) as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id != "0" } }
            .orEmpty()
    }.getOrDefault(emptyList())
}

private const val TemplateUndoLimit = 50

/** Rounds to the nearest integer, kept as a double for the custom element columns. */
private fun Double.roundToIntDouble(): Double = Math.round(this).toDouble()
