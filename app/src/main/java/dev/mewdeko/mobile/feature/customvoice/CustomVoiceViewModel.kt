package dev.mewdeko.mobile.feature.customvoice

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

/** Regex a Discord snowflake user id must match. */
private val UserIdPattern = Regex("\\d{15,22}")

/** Configuration for user-owned temporary voice channels. */
@Serializable
data class CustomVoiceConfig(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val hubVoiceChannelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val channelCategoryId: Snowflake? = null,
    val defaultNameFormat: String = "{username}'s Channel",
    val defaultUserLimit: Int = 0,
    val defaultBitrate: Int = 64,
    val deleteWhenEmpty: Boolean = true,
    val emptyChannelTimeout: Int = 1,
    val allowMultipleChannels: Boolean = false,
    val allowNameCustomization: Boolean = true,
    val allowUserLimitCustomization: Boolean = true,
    val allowBitrateCustomization: Boolean = false,
    val allowLocking: Boolean = true,
    val allowUserManagement: Boolean = true,
    val maxUserLimit: Int = 99,
    val maxBitrate: Int = 96,
    val persistUserPreferences: Boolean = true,
    val autoPermission: Boolean = true,
    @Serializable(with = SnowflakeSerializer::class) val customVoiceAdminRoleId: Snowflake? = null,
)

/** One live user-owned voice channel. */
@Serializable
data class CustomVoiceChannel(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val ownerId: Snowflake? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastActive: Instant? = null,
    val isLocked: Boolean = false,
    val keepAlive: Boolean = false,
    val allowedUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val deniedUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
)

/** Counters for the custom voice system. */
@Serializable
data class CustomVoiceStatistics(
    val enabled: Boolean = false,
    val totalChannels: Int = 0,
    val activeChannels: Int = 0,
    val lockedChannels: Int = 0,
    val keepAliveChannels: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val hubChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val categoryId: Snowflake? = null,
)

/** Result of a bulk cleanup of inactive channels. */
@Serializable
data class CustomVoiceCleanupResult(
    val success: Boolean = false,
    val deletedChannels: Int = 0,
    val message: String = "",
)

/** A member's saved custom voice channel preferences. */
@Serializable
data class CustomVoiceUserPreference(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val defaultName: String? = null,
    val defaultUserLimit: Int? = null,
    val defaultBitrate: Int? = null,
)

/** Custom voice screen state. */
data class CustomVoiceState(
    val config: CustomVoiceConfig = CustomVoiceConfig(),
    val loadedConfig: CustomVoiceConfig = CustomVoiceConfig(),
    val channels: List<CustomVoiceChannel> = emptyList(),
    val statistics: CustomVoiceStatistics? = null,
    val voiceChannels: List<TextChannelLite> = emptyList(),
    val categories: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "settings",
    val prefUserId: String = "",
    val prefLoading: Boolean = false,
    val prefError: String? = null,
    val userPrefs: CustomVoiceUserPreference? = null,
    val prefDraftName: String = "",
    val prefDraftUserLimit: String = "",
    val prefDraftBitrate: String = "",
) {
    /** Whether the configuration differs from what the server has. */
    val hasUnsavedConfig: Boolean get() = config != loadedConfig

    /** Whether custom voice is configured for this guild. */
    val isEnabled: Boolean get() = statistics?.enabled ?: config.enabled

    /** Resolves a channel id to its display name, falling back to the raw id. */
    fun channelName(channelId: Snowflake): String =
        voiceChannels.firstOrNull { it.id == channelId }?.name ?: channelId
}

/** User-owned temporary voice channels. */
@HiltViewModel
class CustomVoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(CustomVoiceState())

    /** Observable screen state. */
    val state: StateFlow<CustomVoiceState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the configuration, live channels, and statistics. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val config = async {
                runCatching {
                    api.send(
                        Endpoint("api/CustomVoice/$guildId/configuration"),
                        CustomVoiceConfig.serializer(),
                    )
                }.getOrDefault(CustomVoiceConfig())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/CustomVoice/$guildId/channels"),
                        ListSerializer(CustomVoiceChannel.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val statistics = async {
                runCatching {
                    api.send(
                        Endpoint("api/CustomVoice/$guildId/statistics"),
                        CustomVoiceStatistics.serializer(),
                    )
                }.getOrNull()
            }
            val voice = async { channelsOfType(1) }
            val categories = async { channelsOfType(2) }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val loaded = config.await()
            _state.update {
                it.copy(
                    config = loaded,
                    loadedConfig = loaded,
                    channels = channels.await(),
                    statistics = statistics.await(),
                    voiceChannels = voice.await().sortedBy { channel -> channel.name.lowercase() },
                    categories = categories.await().sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Applies an edit to the staged configuration. */
    fun edit(transform: (CustomVoiceConfig) -> CustomVoiceConfig) =
        _state.update { it.copy(config = transform(it.config)) }

    /** Writes the staged configuration. */
    fun save() = launchAction("Failed to save configuration.") {
        val current = _state.value.config
        api.sendIgnoringBody(
            Endpoint(
                "api/CustomVoice/$guildId/configuration",
                HttpMethod.PUT,
                jsonBody(
                    "hubVoiceChannelId" to (current.hubVoiceChannelId.toLongOrNull() ?: 0L),
                    "channelCategoryId" to current.channelCategoryId?.toLongOrNull(),
                    "defaultNameFormat" to current.defaultNameFormat,
                    "defaultUserLimit" to current.defaultUserLimit,
                    "defaultBitrate" to current.defaultBitrate,
                    "deleteWhenEmpty" to current.deleteWhenEmpty,
                    "emptyChannelTimeout" to current.emptyChannelTimeout,
                    "allowMultipleChannels" to current.allowMultipleChannels,
                    "allowNameCustomization" to current.allowNameCustomization,
                    "allowUserLimitCustomization" to current.allowUserLimitCustomization,
                    "allowBitrateCustomization" to current.allowBitrateCustomization,
                    "allowLocking" to current.allowLocking,
                    "allowUserManagement" to current.allowUserManagement,
                    "maxUserLimit" to current.maxUserLimit,
                    "maxBitrate" to current.maxBitrate,
                    "persistUserPreferences" to current.persistUserPreferences,
                    "autoPermission" to current.autoPermission,
                    "customVoiceAdminRoleId" to current.customVoiceAdminRoleId?.toLongOrNull(),
                ),
            ),
        )
        _state.update { it.copy(config = current, loadedConfig = current) }
        postSuccess("Configuration saved.")
        load(refreshing = true)
    }

    /** Turns the whole custom voice system off. */
    fun disable() = launchAction("Failed to disable custom voice.") {
        api.sendIgnoringBody(
            Endpoint("api/CustomVoice/$guildId/configuration", HttpMethod.DELETE)
        )
        postSuccess("Custom voice disabled.")
        load(refreshing = true)
    }

    /** Deletes one live channel. */
    fun deleteChannel(channelId: Snowflake) = launchAction("Failed to delete channel.") {
        api.sendIgnoringBody(
            Endpoint("api/CustomVoice/$guildId/channels/$channelId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(channels = it.channels.filterNot { entry -> entry.channelId == channelId })
        }
        postSuccess("Channel deleted.")
    }

    /** Deletes every channel idle for longer than [hoursInactive]. */
    fun cleanup(hoursInactive: Int) = launchAction("Failed to run cleanup.") {
        val result = api.send(
            Endpoint(
                "api/CustomVoice/$guildId/cleanup?hoursInactive=$hoursInactive",
                HttpMethod.DELETE,
            ),
            CustomVoiceCleanupResult.serializer(),
        )
        postSuccess("Cleaned up ${result.deletedChannels} inactive channel(s).")
        load(refreshing = true)
    }

    /** Locks, unlocks, or pins one live channel. */
    fun updateChannel(channelId: Snowflake, isLocked: Boolean? = null, keepAlive: Boolean? = null) =
        launchAction("Failed to update channel.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/CustomVoice/$guildId/channels/$channelId",
                    HttpMethod.PUT,
                    jsonBody("isLocked" to isLocked, "keepAlive" to keepAlive),
                )
            )
            _state.update { current ->
                current.copy(
                    channels = current.channels.map { channel ->
                        if (channel.channelId != channelId) channel else channel.copy(
                            isLocked = isLocked ?: channel.isLocked,
                            keepAlive = keepAlive ?: channel.keepAlive,
                        )
                    },
                )
            }
        }

    /** Updates the staged user id in the preferences lookup form. */
    fun setPrefUserId(value: String) =
        _state.update { it.copy(prefUserId = value.filter(Char::isDigit).take(22), prefError = null) }

    /** Updates the staged preferred channel name for the looked-up member. */
    fun setPrefDraftName(value: String) = _state.update { it.copy(prefDraftName = value) }

    /** Updates the staged preferred user limit for the looked-up member. */
    fun setPrefDraftUserLimit(value: String) =
        _state.update { it.copy(prefDraftUserLimit = value.filter(Char::isDigit).take(2)) }

    /** Updates the staged preferred bitrate for the looked-up member. */
    fun setPrefDraftBitrate(value: String) =
        _state.update { it.copy(prefDraftBitrate = value.filter(Char::isDigit).take(3)) }

    /** Looks up the currently entered member's saved custom voice preferences. */
    fun loadUserPreferences() = viewModelScope.launch {
        val id = _state.value.prefUserId.trim()
        if (!UserIdPattern.matches(id)) {
            _state.update { it.copy(prefError = "Enter a valid Discord user ID.", userPrefs = null) }
            return@launch
        }
        _state.update { it.copy(prefLoading = true, prefError = null) }
        runCatching {
            api.send(
                Endpoint("api/CustomVoice/$guildId/user-preferences/$id"),
                CustomVoiceUserPreference.serializer(),
            )
        }.onSuccess { prefs ->
            _state.update {
                it.copy(
                    prefLoading = false,
                    userPrefs = prefs,
                    prefDraftName = prefs.defaultName.orEmpty(),
                    prefDraftUserLimit = prefs.defaultUserLimit?.toString().orEmpty(),
                    prefDraftBitrate = prefs.defaultBitrate?.toString().orEmpty(),
                )
            }
        }.onFailure {
            _state.update {
                it.copy(
                    prefLoading = false,
                    userPrefs = null,
                    prefError = "No preferences found for that user, or the lookup failed.",
                )
            }
        }
    }

    /** Writes the staged preference edits for the looked-up member. */
    fun saveUserPreferences() = launchAction("Failed to save preferences.") {
        val prefs = _state.value.userPrefs ?: return@launchAction
        val current = _state.value
        val name = current.prefDraftName.trim().ifBlank { null }
        val userLimit = current.prefDraftUserLimit.toIntOrNull()?.coerceIn(0, 99)
        val bitrate = current.prefDraftBitrate.toIntOrNull()?.coerceIn(8, 384)
        api.sendIgnoringBody(
            Endpoint(
                "api/CustomVoice/$guildId/user-preferences/${prefs.userId}",
                HttpMethod.PUT,
                jsonBody(
                    "defaultName" to name,
                    "defaultUserLimit" to userLimit,
                    "defaultBitrate" to bitrate,
                ),
            ),
        )
        _state.update {
            it.copy(
                userPrefs = prefs.copy(
                    defaultName = name,
                    defaultUserLimit = userLimit,
                    defaultBitrate = bitrate,
                ),
            )
        }
        postSuccess("Preferences saved.")
    }

    private suspend fun channelsOfType(type: Int): List<TextChannelLite> = runCatching {
        api.send(
            Endpoint("api/ClientOperations/channels/$guildId/$type"),
            ListSerializer(TextChannelLite.serializer()),
        )
    }.getOrDefault(emptyList())
}
