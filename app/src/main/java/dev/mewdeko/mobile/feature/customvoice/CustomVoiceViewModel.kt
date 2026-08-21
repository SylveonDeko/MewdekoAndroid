package dev.mewdeko.mobile.feature.customvoice

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
import dev.mewdeko.mobile.core.net.jsonBody
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

/** Configuration for user-owned temporary voice channels. */
@Serializable
data class CustomVoiceConfig(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val hubVoiceChannelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val channelCategoryId: Snowflake? = null,
    val defaultNameFormat: String = "{username}'s Channel",
    val defaultUserLimit: Int = 0,
    val defaultBitrate: Int = 64000,
    val deleteWhenEmpty: Boolean = true,
    val emptyChannelTimeout: Int = 1,
    val allowMultipleChannels: Boolean = false,
    val allowNameCustomization: Boolean = true,
    val allowUserLimitCustomization: Boolean = true,
    val allowBitrateCustomization: Boolean = false,
    val allowLocking: Boolean = true,
    val allowUserManagement: Boolean = true,
    val maxUserLimit: Int = 99,
    val maxBitrate: Int = 96000,
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
) {
    /** Whether the configuration differs from what the server has. */
    val hasUnsavedConfig: Boolean get() = config != loadedConfig
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
            val voice = async { channelsOfType(2) }
            val categories = async { channelsOfType(4) }
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
        val updated = api.send(
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
            CustomVoiceConfig.serializer(),
        )
        _state.update { it.copy(config = updated, loadedConfig = updated) }
        postSuccess("Configuration saved.")
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
        api.sendIgnoringBody(
            Endpoint(
                "api/CustomVoice/$guildId/cleanup?hoursInactive=$hoursInactive",
                HttpMethod.DELETE,
            )
        )
        postSuccess("Cleanup complete.")
        load(refreshing = true)
    }

    /** Locks, unlocks, or pins one live channel. */
    fun updateChannel(channelId: Snowflake, isLocked: Boolean? = null, keepAlive: Boolean? = null) =
        launchAction("Failed to update channel.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/CustomVoice/$guildId/channels/$channelId",
                    HttpMethod.PATCH,
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

    private suspend fun channelsOfType(type: Int): List<TextChannelLite> = runCatching {
        api.send(
            Endpoint("api/ClientOperations/channels/$guildId/$type"),
            ListSerializer(TextChannelLite.serializer()),
        )
    }.getOrDefault(emptyList())
}
