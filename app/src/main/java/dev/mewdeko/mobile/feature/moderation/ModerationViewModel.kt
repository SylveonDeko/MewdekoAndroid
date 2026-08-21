package dev.mewdeko.mobile.feature.moderation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.model.WarningRecord
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import javax.inject.Inject

/** One rung of the automatic warning-punishment ladder. */
@Serializable
data class WarningPunishment(
    val id: Int = 0,
    val count: Int = 0,
    val punishment: Int = 0,
    val time: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
) {
    /** Human-readable name for the punishment code the bot stores. */
    val actionLabel: String
        get() = when (punishment) {
            1 -> "Mute"
            2 -> "Kick"
            3 -> "Ban"
            4 -> "Soft-ban"
            5 -> "Add role"
            6 -> "Voice mute"
            7 -> "Chat mute"
            8 -> "Timeout"
            9 -> "Warn"
            10 -> "Remove roles"
            else -> "Action #$punishment"
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
    val warnings: List<WarningRecord> = emptyList(),
    val punishments: List<WarningPunishment> = emptyList(),
    val warnLogChannel: Snowflake? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableCategories: List<TextChannelLite> = emptyList(),
    val pruneActions: List<BanPruneActionInfo> = emptyList(),
    val pruneSettings: List<BanPruneSetting> = emptyList(),
    val section: String = "overview",
    val filterText: String = "",
    val activeOnly: Boolean = false,
) {
    /** Server-wide purge settings, keyed by action. An empty key covers every action. */
    val guildPruneDefaults: Map<String, BanPruneSetting>
        get() = pruneSettings
            .filter { it.scopeType == BanPruneScope.GUILD }
            .associateBy { it.actionKey }

    /** Purge settings attached to a single channel or category. */
    val pruneOverrides: List<BanPruneSetting>
        get() = pruneSettings.filter { it.scopeType != BanPruneScope.GUILD }

    /**
     * The purge an action uses at the server level: its own setting, the catch-all
     * setting covering every action, or the action's built in default.
     */
    fun guildPruneFor(action: BanPruneActionInfo): Int {
        val defaults = guildPruneDefaults
        return defaults[action.key]?.pruneDays
            ?: defaults[""]?.pruneDays
            ?: action.defaultDays
    }

    /** Where a purge value on an action comes from, for the caption under its row. */
    fun guildPruneSource(action: BanPruneActionInfo): String {
        val defaults = guildPruneDefaults
        return when {
            defaults.containsKey(action.key) -> "Set for this action"
            defaults.containsKey("") -> "From the all-actions default"
            else -> "Built in default"
        }
    }

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

    /** Warnings matching the current filter and active-only toggle. */
    val filteredWarnings: List<WarningRecord>
        get() {
            val query = filterText.trim().lowercase()
            return warnings
                .filter { !activeOnly || !it.forgiven }
                .filter { warning ->
                    query.isEmpty() ||
                        warning.userId?.contains(query) == true ||
                        warning.reason.orEmpty().lowercase().contains(query)
                }
        }
}

/** Loads the guild's warnings, punishment ladder, and warn-log destination. */
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
                        ListSerializer(WarningRecord.serializer()),
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
                    punishments = punishments.await().sortedBy { punishment -> punishment.count },
                    warnLogChannel = logChannel.await()?.channelId
                        ?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableCategories = categories.await()
                        .sortedBy { category -> category.name.lowercase() },
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

    /** Toggles hiding forgiven warnings. */
    fun setActiveOnly(value: Boolean) = _state.update { it.copy(activeOnly = value) }

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
    ) = launchAction("Failed to save purge setting.") {
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
    fun clearPrune(setting: BanPruneSetting) = launchAction("Failed to remove purge setting.") {
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

    /** Drops every purge setting, returning each action to its built in default. */
    fun resetPrune() = launchAction("Failed to reset purge settings.") {
        api.sendIgnoringBody(Endpoint("api/BanPrune/$guildId/all", HttpMethod.DELETE))
        refreshPruneSettings()
        postSuccess("Purge settings reset.")
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
