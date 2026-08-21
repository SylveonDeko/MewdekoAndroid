package dev.mewdeko.mobile.feature.minecraft

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
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
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject

/** Which Minecraft edition a tracked server runs. */
enum class McServerType(val raw: Int, val label: String, val defaultPort: Int) {
    JAVA(0, "Java", 25565),
    BEDROCK(1, "Bedrock", 19132);

    companion object {
        /** Maps a wire value onto a type, defaulting to [JAVA]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: JAVA
    }
}

/** How the bot surfaces a watched server's status. */
enum class McWatchMode(val raw: Int, val label: String) {
    EMBED(0, "Embed"),
    CHANNEL_TOPIC(1, "Topic"),
    BOTH(2, "Both");

    companion object {
        /** Maps a wire value onto a mode, defaulting to [EMBED]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: EMBED
    }
}

/** A tracked Minecraft server. */
@Serializable
data class MinecraftServer(
    val id: Int = 0,
    val name: String = "",
    val address: String = "",
    val port: Int = 25565,
    val serverType: Int = 0,
    val queryPort: Int = 0,
    val isDefault: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val watchChannelId: Snowflake? = null,
    val watchInterval: Int = 5,
    val watchMode: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val chatChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val joinLeaveChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val deathChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val advancementChannelId: Snowflake? = null,
    val customOnlineMessage: String? = null,
    val customOfflineMessage: String? = null,
    val customEmbedTemplate: String? = null,
    val lastOnline: Boolean? = null,
    val rconEnabled: Boolean = false,
    val rconPort: Int = 25575,
    val hasRconPassword: Boolean = false,
    val eventTemplates: String? = null,
    val hasPluginKey: Boolean = false,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The typed form of [serverType]. */
    val type: McServerType get() = McServerType.from(serverType)

    /** The typed form of [watchMode]. */
    val watch: McWatchMode get() = McWatchMode.from(watchMode)
}

/** A live status ping for a tracked server. */
@Serializable
data class MinecraftStatus(
    val isOnline: Boolean = false,
    val motd: String = "",
    val playersOnline: Int = 0,
    val playersMax: Int = 0,
    val playerList: List<String> = emptyList(),
    val version: String = "",
    val latency: Int = 0,
    val map: String? = null,
    val gameMode: String? = null,
    val software: String? = null,
    val plugins: List<String> = emptyList(),
    val isQueryResponse: Boolean = false,
)

/** The plugin key generated for a server. */
@Serializable
data class PluginKeyResponse(val key: String? = null)

/** The result of an RCON command. */
@Serializable
data class RconResponse(
    val success: Boolean = false,
    val response: String? = null,
    val rawResponse: String? = null,
)

/** Minecraft screen state. */
data class MinecraftState(
    val servers: List<MinecraftServer> = emptyList(),
    val statuses: Map<String, MinecraftStatus> = emptyMap(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val pluginKey: String? = null,
    val rconOutput: String? = null,
    val selectedServer: String? = null,
) {
    /** The live status for one server, when it has been pinged. */
    fun status(name: String): MinecraftStatus? = statuses[name]
}

/** Minecraft server status tracking, event relays, and RCON. */
@HiltViewModel
class MinecraftViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(MinecraftState())

    /** Observable screen state. */
    val state: StateFlow<MinecraftState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the server list plus a cached status ping for each. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val servers = async {
                runCatching {
                    api.send(
                        Endpoint("api/Minecraft/$guildId/servers"),
                        ListSerializer(MinecraftServer.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val loaded = servers.await()
            _state.update {
                it.copy(
                    servers = loaded,
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    selectedServer = it.selectedServer?.takeIf { name ->
                        loaded.any { server -> server.name == name }
                    } ?: loaded.firstOrNull()?.name,
                )
            }
            loaded.forEach { server -> refreshStatus(server.name, live = false) }
        }
    }

    /** Selects a server to show detail for. */
    fun selectServer(name: String) = _state.update { it.copy(selectedServer = name) }

    /**
     * Fetches a server's status.
     *
     * @param live When true, pings the server directly; otherwise the bot's
     *   cached result is used, which avoids a round trip on every refresh.
     */
    fun refreshStatus(name: String, live: Boolean = true) = viewModelScope.launch {
        val tail = if (live) "status" else "cached-status"
        val status = runCatching {
            api.send(
                Endpoint("api/Minecraft/$guildId/servers/${name.encoded()}/$tail"),
                MinecraftStatus.serializer(),
            )
        }.getOrNull() ?: return@launch
        _state.update { it.copy(statuses = it.statuses + (name to status)) }
    }

    /** Adds a server to track. */
    fun addServer(
        name: String,
        address: String,
        port: Int,
        type: McServerType,
        queryPort: Int,
    ) = launchAction("Failed to add server.") {
        api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers",
                HttpMethod.POST,
                jsonBody(
                    "name" to name,
                    "address" to address,
                    "port" to port,
                    "serverType" to type.raw,
                    "queryPort" to queryPort,
                ),
            ),
            MinecraftServer.serializer(),
        )
        postSuccess("Server added.")
        load(refreshing = true)
    }

    /** Patches whichever server fields are non-null. */
    fun updateServer(
        name: String,
        address: String? = null,
        port: Int? = null,
        type: Int? = null,
        queryPort: Int? = null,
        chatChannelId: Snowflake? = null,
        joinLeaveChannelId: Snowflake? = null,
        deathChannelId: Snowflake? = null,
        advancementChannelId: Snowflake? = null,
        isDefault: Boolean? = null,
    ) = launchAction("Failed to update server.") {
        api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}",
                HttpMethod.PUT,
                jsonBody(
                    "address" to address,
                    "port" to port,
                    "serverType" to type,
                    "queryPort" to queryPort,
                    "chatChannelId" to chatChannelId?.toLongOrNull(),
                    "joinLeaveChannelId" to joinLeaveChannelId?.toLongOrNull(),
                    "deathChannelId" to deathChannelId?.toLongOrNull(),
                    "advancementChannelId" to advancementChannelId?.toLongOrNull(),
                    "isDefault" to isDefault,
                ),
            ),
            MinecraftServer.serializer(),
        )
        load(refreshing = true)
    }

    /** Sets where and how often the bot posts the server's status. */
    fun setWatch(name: String, channelId: Snowflake?, interval: Int?, watchMode: Int?) =
        launchAction("Failed to update watch settings.") {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/watch",
                    HttpMethod.PUT,
                    jsonBody(
                        "channelId" to channelId?.toLongOrNull(),
                        "interval" to interval,
                        "watchMode" to watchMode,
                    ),
                ),
                MinecraftServer.serializer(),
            )
            load(refreshing = true)
        }

    /** Configures RCON access for a server. */
    fun setRcon(name: String, enabled: Boolean, port: Int, password: String?) =
        launchAction("Failed to update RCON settings.") {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                    HttpMethod.PUT,
                    jsonBody(
                        "enabled" to enabled,
                        "port" to port,
                        "password" to password.orEmpty(),
                    ),
                ),
                MinecraftServer.serializer(),
            )
            postSuccess("RCON settings saved.")
            load(refreshing = true)
        }

    /** Runs an RCON command and stores its output. */
    fun sendRcon(name: String, command: String) = launchAction("Failed to run command.") {
        val response = api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                HttpMethod.POST,
                jsonBody("command" to command),
            ),
            RconResponse.serializer(),
        )
        _state.update {
            it.copy(rconOutput = response.response ?: response.rawResponse ?: "(no output)")
        }
        if (!response.success) postError("Command failed.")
    }

    /** Clears the stored RCON output. */
    fun clearRconOutput() = _state.update { it.copy(rconOutput = null) }

    /** Generates a fresh key for the companion server plugin. */
    fun generatePluginKey(name: String) = launchAction("Failed to generate plugin key.") {
        val response = api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/plugin-key",
                HttpMethod.POST,
            ),
            PluginKeyResponse.serializer(),
        )
        _state.update { it.copy(pluginKey = response.key) }
        load(refreshing = true)
    }

    /** Clears the displayed plugin key. */
    fun clearPluginKey() = _state.update { it.copy(pluginKey = null) }

    /** Stops tracking a server. */
    fun removeServer(name: String) = launchAction("Failed to remove server.") {
        api.sendIgnoringBody(
            Endpoint("api/Minecraft/$guildId/servers/${name.encoded()}", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                servers = it.servers.filterNot { server -> server.name == name },
                statuses = it.statuses - name,
            )
        }
        postSuccess("Server removed.")
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
