package dev.mewdeko.mobile.feature.moderation

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
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import javax.inject.Inject

/** A warning issued to a member, with its forgiveness state. */
@Serializable
data class ModerationWarning(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val reason: String? = null,
    val moderator: String? = null,
    val forgivenBy: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val forgiven: Boolean = false,
)

/** One user's warnings, grouped for the per-user "forgive all" action. */
data class WarningGroup(
    val userId: Snowflake,
    val warnings: List<ModerationWarning>,
    val activeCount: Int,
)

/** Result of issuing a warning, reporting whether a warning action fired. */
@Serializable
data class WarnUserResult(
    val punishmentApplied: Boolean = false,
    val punishment: String? = null,
)

/** Body for [ModerationViewModel.warnUser]. */
@Serializable
private data class WarnUserRequestBody(val moderatorId: Snowflake, val reason: String)

/** Body for endpoints that only need the acting dashboard moderator. */
@Serializable
private data class ModeratorRequestBody(val moderatorId: Snowflake)

/** Body for [ModerationViewModel.addPunishment]. */
@Serializable
private data class SetWarnPunishmentRequestBody(
    val count: Int,
    val punishment: Int,
    val timeMinutes: Int? = null,
    val roleId: Snowflake? = null,
)

/** Body for [ModerationViewModel.setWarnLogChannel]. */
@Serializable
private data class SetWarnLogChannelRequestBody(val channelId: Snowflake)

/**
 * The punishment codes a warning action can be configured with, mirroring
 * `Mewdeko.Modules.Administration.Common.PunishmentAction`.
 */
object PunishmentActions {
    const val MUTE = 0
    const val KICK = 1
    const val BAN = 2
    const val SOFTBAN = 3
    const val REMOVE_ROLES = 4
    const val CHAT_MUTE = 5
    const val VOICE_MUTE = 6
    const val ADD_ROLE = 7
    const val DELETE = 8
    const val WARN = 9
    const val TIMEOUT = 10
    const val NONE = 11

    /** The nine actions the warning action add form can choose from, in dashboard order. */
    val Selectable: List<Pair<Int, String>> = listOf(
        MUTE to "Mute",
        CHAT_MUTE to "Chat mute",
        VOICE_MUTE to "Voice mute",
        TIMEOUT to "Timeout",
        KICK to "Kick",
        SOFTBAN to "Softban",
        BAN to "Ban",
        ADD_ROLE to "Add role",
        REMOVE_ROLES to "Remove all roles",
    )

    /** Actions that accept an optional duration in minutes. */
    val Timed: Set<Int> = setOf(MUTE, CHAT_MUTE, VOICE_MUTE, TIMEOUT, BAN, ADD_ROLE)

    /** Human-readable label for any punishment code, including ones not in [Selectable]. */
    fun label(code: Int): String = Selectable.firstOrNull { it.first == code }?.second
        ?: when (code) {
            DELETE -> "Delete message"
            WARN -> "Warn"
            NONE -> "None"
            else -> "Action #$code"
        }

    /**
     * How a member is described once this action has fired, used in the warning
     * action removal confirmation, such as "will no longer be muted".
     */
    fun pastTense(code: Int): String = when (code) {
        MUTE -> "muted"
        CHAT_MUTE -> "chat muted"
        VOICE_MUTE -> "voice muted"
        TIMEOUT -> "timed out"
        KICK -> "kicked"
        SOFTBAN -> "softbanned"
        BAN -> "banned"
        ADD_ROLE -> "given the role"
        REMOVE_ROLES -> "stripped of every role"
        WARN -> "warned again"
        else -> "affected"
    }
}

/** Formats a duration in minutes as whole hours when it divides evenly, otherwise as minutes. */
private fun formatWarningDuration(minutes: Int): String = if (minutes % 60 == 0) {
    val hours = minutes / 60
    "$hours hour${if (hours == 1) "" else "s"}"
} else {
    "$minutes minute${if (minutes == 1) "" else "s"}"
}

/** One action that fires automatically when a member reaches a warning count. */
@Serializable
data class WarningPunishment(
    val id: Int = 0,
    val count: Int = 0,
    val punishment: Int = 0,
    val time: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
) {
    /** Human-readable name for the punishment code the bot stores. */
    val actionLabel: String get() = PunishmentActions.label(punishment)

    /**
     * The full sentence describing when this action fires, such as
     * "When a member reaches 3 warnings: Ban, for 30 minutes".
     */
    fun sentence(roleName: String?): String {
        val plural = if (count == 1) "warning" else "warnings"
        val suffix = when {
            time > 0 -> ", for ${formatWarningDuration(time)}"
            punishment == PunishmentActions.ADD_ROLE && roleName != null -> ", role @$roleName"
            else -> ""
        }
        return "When a member reaches $count $plural: $actionLabel$suffix"
    }

    /** The confirmation message shown before removing this warning action. */
    fun removalMessage(roleName: String?): String {
        val target = if (punishment == PunishmentActions.ADD_ROLE && roleName != null) {
            "${PunishmentActions.pastTense(punishment)} @$roleName"
        } else {
            PunishmentActions.pastTense(punishment)
        }
        return "Members reaching $count warnings will no longer be $target."
    }
}

/** Which part of a guild a ban purge setting applies to. */
object BanPruneScope {
    /** The guild-wide default, used when no override matches. */
    const val GUILD = 0

    /** An override covering every channel inside one category. */
    const val CATEGORY = 1

    /** An override covering a single channel. */
    const val CHANNEL = 2
}

/** A moderation action that bans, and so has a message purge attached to it. */
@Serializable
data class BanPruneActionInfo(
    val key: String = "",
    val displayName: String = "",
    val defaultDays: Int = 0,
)

/** One stored ban purge setting. */
@Serializable
data class BanPruneSetting(
    val id: Int = 0,
    val scopeType: Int = BanPruneScope.GUILD,
    @Serializable(with = SnowflakeSerializer::class) val scopeId: Snowflake = "0",
    val actionKey: String = "",
    val pruneDays: Int = 0,
)

/** Body for creating or updating a ban purge setting. */
@Serializable
private data class BanPruneSettingRequest(
    val scopeType: Int,
    val scopeId: String,
    val actionKey: String?,
    val pruneDays: Int,
)

/** The channel warnings are logged to, if configured. */
@Serializable
data class WarnLogChannelResponse(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
)

/** Moderation screen state. */
data class ModerationState(
    val warnings: List<ModerationWarning> = emptyList(),
    val recentActivity: List<ModerationWarning> = emptyList(),
    val punishments: List<WarningPunishment> = emptyList(),
    val warnLogChannel: Snowflake? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableCategories: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val pruneActions: List<BanPruneActionInfo> = emptyList(),
    val pruneSettings: List<BanPruneSetting> = emptyList(),
    val section: String = "overview",
    val filterText: String = "",
    val showForgiven: Boolean = true,
) {
    /** Server-wide purge settings, keyed by action. An empty key covers every action. */
    val guildPruneDefaults: Map<String, BanPruneSetting>
        get() = pruneSettings
            .filter { it.scopeType == BanPruneScope.GUILD }
            .associateBy { it.actionKey }

    /** Purge settings attached to a single channel or category. */
    val pruneOverrides: List<BanPruneSetting>
        get() = pruneSettings.filter { it.scopeType != BanPruneScope.GUILD }

    /** The name of the channel or category an override targets, falling back to its id. */
    fun pruneScopeName(setting: BanPruneSetting): String {
        val pool = if (setting.scopeType == BanPruneScope.CATEGORY) {
            availableCategories
        } else {
            availableChannels
        }
        val match = pool.firstOrNull { it.id == setting.scopeId }
        val prefix = if (setting.scopeType == BanPruneScope.CATEGORY) "" else "#"
        return prefix + (match?.name ?: setting.scopeId)
    }

    /** The display name for an action key, or "All actions" for the catch-all. */
    fun pruneActionName(key: String): String {
        if (key.isEmpty()) return "All actions"
        return pruneActions.firstOrNull { it.key == key }?.displayName ?: key
    }

    /** Warnings still counting against their member. */
    val activeCount: Int get() = warnings.count { !it.forgiven }

    /** Warnings that have been forgiven. */
    val forgivenCount: Int get() = warnings.count { it.forgiven }

    /** The warn-log channel's name, falling back to its raw id. */
    val warnLogChannelName: String?
        get() = warnLogChannel?.let { id ->
            availableChannels.firstOrNull { it.id == id }?.name ?: id
        }

    /** The role name for a warning action's role, falling back to its raw id. */
    fun roleName(roleId: Snowflake?): String? =
        roleId?.let { id -> availableRoles.firstOrNull { it.id == id }?.name ?: id }

    /** Warnings matching the current search and the "show forgiven" toggle. */
    val filteredWarnings: List<ModerationWarning>
        get() {
            val query = filterText.trim().lowercase()
            return warnings
                .filter { showForgiven || !it.forgiven }
                .filter { warning ->
                    query.isEmpty() ||
                        warning.userId?.contains(query) == true ||
                        warning.reason.orEmpty().lowercase().contains(query) ||
                        warning.moderator.orEmpty().lowercase().contains(query)
                }
        }

    /** [filteredWarnings] grouped by the user they were issued to. */
    val warningsByUser: List<WarningGroup>
        get() {
            val groups = LinkedHashMap<Snowflake, MutableList<ModerationWarning>>()
            filteredWarnings.forEach { warning ->
                val userId = warning.userId ?: return@forEach
                groups.getOrPut(userId) { mutableListOf() }.add(warning)
            }
            return groups.map { (userId, items) ->
                WarningGroup(userId, items, items.count { !it.forgiven })
            }
        }
}

/** Loads the guild's warnings, warning actions, and warn-log destination. */
@HiltViewModel
class ModerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ModerationState())

    /** Observable screen state. */
    val state: StateFlow<ModerationState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads warnings, punishments, and channel metadata. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val warnings = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/warnings"),
                        ListSerializer(ModerationWarning.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val recentActivity = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/recent?limit=10"),
                        ListSerializer(ModerationWarning.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val punishments = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/punishments"),
                        ListSerializer(WarningPunishment.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val logChannel = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/warnlog-channel"),
                        WarnLogChannelResponse.serializer(),
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
            val categories = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/categories/$guildId"),
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
            val pruneActions = async {
                runCatching {
                    api.send(
                        Endpoint("api/BanPrune/$guildId/actions"),
                        ListSerializer(BanPruneActionInfo.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val pruneSettings = async {
                runCatching {
                    api.send(
                        Endpoint("api/BanPrune/$guildId"),
                        ListSerializer(BanPruneSetting.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    warnings = warnings.await()
                        .sortedByDescending { warning -> warning.dateAdded ?: Instant.EPOCH },
                    recentActivity = recentActivity.await(),
                    punishments = punishments.await().sortedBy { punishment -> punishment.count },
                    warnLogChannel = logChannel.await()?.channelId
                        ?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableCategories = categories.await()
                        .sortedBy { category -> category.name.lowercase() },
                    availableRoles = roles.await()
                        .sortedBy { role -> role.name.lowercase() },
                    pruneActions = pruneActions.await(),
                    pruneSettings = pruneSettings.await(),
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Updates the warning search filter. */
    fun setFilter(text: String) = _state.update { it.copy(filterText = text) }

    /** Toggles whether forgiven warnings are shown in the list. */
    fun setShowForgiven(value: Boolean) = _state.update { it.copy(showForgiven = value) }

    /**
     * Warns a member. The bot may apply an automatic warning action as a side
     * effect, reported back so the screen can surface it since it happens on Discord.
     */
    fun warnUser(targetUserId: Snowflake, reason: String) =
        launchAction("Failed to warn user. Make sure the ID belongs to a member of this server.") {
            val payload = WarnUserRequestBody(moderatorId = userId, reason = reason.trim())
            val result = api.send(
                Endpoint(
                    "api/Moderation/$guildId/warnings/user/$targetUserId",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(WarnUserRequestBody.serializer(), payload),
                ),
                WarnUserResult.serializer(),
            )
            refreshWarnings()
            if (result.punishmentApplied && !result.punishment.isNullOrBlank()) {
                postSuccess("Auto-punishment applied: ${result.punishment}")
            }
        }

    /** Forgives a single warning. */
    fun forgiveWarning(warningId: Int) = launchAction("Failed to forgive warning.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Moderation/$guildId/warnings/$warningId/forgive",
                HttpMethod.POST,
                MewdekoJson.encodeToString(ModeratorRequestBody.serializer(), ModeratorRequestBody(userId)),
            ),
        )
        refreshWarnings()
    }

    /** Forgives every active warning for a user. */
    fun forgiveAllForUser(targetUserId: Snowflake) = launchAction("Failed to forgive warnings.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Moderation/$guildId/warnings/user/$targetUserId/forgive-all",
                HttpMethod.POST,
                MewdekoJson.encodeToString(ModeratorRequestBody.serializer(), ModeratorRequestBody(userId)),
            ),
        )
        refreshWarnings()
    }

    /** Permanently deletes a warning. */
    fun deleteWarning(warningId: Int) = launchAction("Failed to delete warning.") {
        api.sendIgnoringBody(Endpoint("api/Moderation/$guildId/warnings/$warningId", HttpMethod.DELETE))
        refreshWarnings()
    }

    private suspend fun refreshWarnings() {
        val warnings = runCatching {
            api.send(
                Endpoint("api/Moderation/$guildId/warnings"),
                ListSerializer(ModerationWarning.serializer()),
            )
        }.getOrDefault(emptyList())
        val recentActivity = runCatching {
            api.send(
                Endpoint("api/Moderation/$guildId/recent?limit=10"),
                ListSerializer(ModerationWarning.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                warnings = warnings.sortedByDescending { warning -> warning.dateAdded ?: Instant.EPOCH },
                recentActivity = recentActivity,
            )
        }
    }

    /** Adds or replaces the warning action fired at [count] warnings. */
    fun addPunishment(count: Int, punishment: Int, timeMinutes: Int?, roleId: Snowflake?) =
        launchAction("Failed to save warning action.") {
            val payload = SetWarnPunishmentRequestBody(
                count = count,
                punishment = punishment,
                timeMinutes = timeMinutes?.takeIf { it > 0 },
                roleId = roleId,
            )
            val updated = api.send(
                Endpoint(
                    "api/Moderation/$guildId/punishments",
                    HttpMethod.PUT,
                    MewdekoJson.encodeToString(SetWarnPunishmentRequestBody.serializer(), payload),
                ),
                ListSerializer(WarningPunishment.serializer()),
            )
            _state.update { it.copy(punishments = updated.sortedBy { action -> action.count }) }
        }

    /** Removes the warning action at [count] warnings. */
    fun removePunishment(count: Int) = launchAction("Failed to remove warning action.") {
        val updated = api.send(
            Endpoint("api/Moderation/$guildId/punishments/$count", HttpMethod.DELETE),
            ListSerializer(WarningPunishment.serializer()),
        )
        _state.update { it.copy(punishments = updated.sortedBy { action -> action.count }) }
    }

    /** Sets the channel warnings are logged to. */
    fun setWarnLogChannel(channelId: Snowflake) = launchAction("Failed to set warning log channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Moderation/$guildId/warnlog-channel",
                HttpMethod.POST,
                MewdekoJson.encodeToString(
                    SetWarnLogChannelRequestBody.serializer(),
                    SetWarnLogChannelRequestBody(channelId),
                ),
            ),
        )
        _state.update { it.copy(warnLogChannel = channelId) }
    }

    /**
     * Stores how many days of messages one action purges within one scope.
     *
     * @param actionKey the action to configure, or null to cover every action in the scope
     */
    fun setPrune(
        scopeType: Int,
        scopeId: Snowflake,
        actionKey: String?,
        pruneDays: Int,
    ) = launchAction("Failed to save ban cleanup setting.") {
        val payload = BanPruneSettingRequest(
            scopeType = scopeType,
            scopeId = if (scopeType == BanPruneScope.GUILD) "0" else scopeId,
            actionKey = actionKey?.takeIf { it.isNotEmpty() },
            pruneDays = pruneDays.coerceIn(0, MaxPruneDays),
        )
        api.sendIgnoringBody(
            Endpoint(
                "api/BanPrune/$guildId",
                HttpMethod.POST,
                MewdekoJson.encodeToString(BanPruneSettingRequest.serializer(), payload),
            ),
        )
        refreshPruneSettings()
    }

    /** Removes one purge setting so its scope falls back to a broader one. */
    fun clearPrune(setting: BanPruneSetting) = launchAction("Failed to remove ban cleanup setting.") {
        val actionQuery = setting.actionKey.takeIf { it.isNotEmpty() }
            ?.let { "&actionKey=$it" }
            .orEmpty()
        api.sendIgnoringBody(
            Endpoint(
                "api/BanPrune/$guildId?scopeType=${setting.scopeType}" +
                    "&scopeId=${setting.scopeId}$actionQuery",
                HttpMethod.DELETE,
            ),
        )
        refreshPruneSettings()
    }

    /** Drops every ban cleanup setting, returning each action to its built in default. */
    fun resetPrune() = launchAction("Failed to reset ban cleanup.") {
        api.sendIgnoringBody(Endpoint("api/BanPrune/$guildId/all", HttpMethod.DELETE))
        refreshPruneSettings()
    }

    private suspend fun refreshPruneSettings() {
        val settings = runCatching {
            api.send(
                Endpoint("api/BanPrune/$guildId"),
                ListSerializer(BanPruneSetting.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(pruneSettings = settings) }
    }

    private companion object {
        /** The largest purge Discord accepts. */
        const val MaxPruneDays = 7
    }
}
