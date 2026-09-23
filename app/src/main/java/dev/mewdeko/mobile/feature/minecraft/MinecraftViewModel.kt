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
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject

/** Which Minecraft edition a tracked server runs. */
enum class McServerType(val raw: Int, val label: String, val defaultPort: Int) {
    JAVA(0, "Java", 25565),
    BEDROCK(1, "Bedrock", 19132),
    GEYSER(2, "Geyser", 25565);

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
    val playerUuids: Map<String, String> = emptyMap(),
    val version: String = "",
    val latency: Int = 0,
    val map: String? = null,
    val gameMode: String? = null,
    val software: String? = null,
    val plugins: List<String> = emptyList(),
    val isQueryResponse: Boolean = false,
)

/** One historical snapshot of a server's status, used for the history charts. */
@Serializable
data class MinecraftSnapshot(
    val isOnline: Boolean = false,
    val playersOnline: Int = 0,
    val playersMax: Int = 0,
    val latency: Int = 0,
    val version: String? = null,
    @Serializable(with = InstantSerializer::class) val timestamp: Instant? = null,
)

/**
 * The bridge event message templates for a server, stored server-side as one
 * JSON string under [MinecraftServer.eventTemplates].
 *
 * The Discord-facing fields carry either plain text or a serialised
 * [dev.mewdeko.mobile.core.model.EmbedMessage]; [chatIngame] is always plain
 * text with Minecraft section-sign colour codes.
 */
@Serializable
data class McEventTemplates(
    val joinDiscord: String = "",
    val leaveDiscord: String = "",
    val chatDiscord: String = "",
    val chatIngame: String = "",
    val deathDiscord: String = "",
    val advancementDiscord: String = "",
) {
    /** Whether every field is blank, meaning the bot's defaults apply. */
    val isEmpty: Boolean
        get() = joinDiscord.isBlank() && leaveDiscord.isBlank() && chatDiscord.isBlank() &&
            chatIngame.isBlank() && deathDiscord.isBlank() && advancementDiscord.isBlank()

    companion object {
        /** Decodes the stored JSON, or blank templates if unset or malformed. */
        fun parse(raw: String?): McEventTemplates {
            if (raw.isNullOrBlank()) return McEventTemplates()
            return runCatching {
                MewdekoJson.decodeFromString(serializer(), raw)
            }.getOrDefault(McEventTemplates())
        }
    }
}

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

/** One command and its response in the RCON console history. */
data class ConsoleEntry(
    val command: String,
    val response: String?,
    val rawResponse: String?,
    val success: Boolean,
)

/** Minecraft screen state. */
data class MinecraftState(
    val servers: List<MinecraftServer> = emptyList(),
    val statuses: Map<String, MinecraftStatus> = emptyMap(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val pluginKey: String? = null,
    val pluginWsUrl: String? = null,
    val selectedServer: String? = null,
    val queryingAll: Boolean = false,
    val history: List<MinecraftSnapshot> = emptyList(),
    val historyServer: String? = null,
    val historyHours: Int = 24,
    val historyLoading: Boolean = false,
    val whitelist: List<String> = emptyList(),
    val whitelistLoading: Boolean = false,
    val consoleServer: String? = null,
    val consoleHistory: List<ConsoleEntry> = emptyList(),
    val consoleSending: Boolean = false,
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
            val previousSelection = state.value.selectedServer
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
            val currentSelection = state.value.selectedServer
            if (currentSelection != null && currentSelection != previousSelection) {
                syncWhitelistForSelection(currentSelection, loaded)
            }
            loaded.forEach { server -> refreshStatus(server.name, live = false) }
        }
    }

    /** Selects a server to show detail for, refreshing its whitelist when it has RCON. */
    fun selectServer(name: String) {
        _state.update { it.copy(selectedServer = name) }
        syncWhitelistForSelection(name, state.value.servers)
    }

    /**
     * Clears the previous server's whitelist and, when [name] has RCON enabled,
     * reloads it so the Whitelist card and Online Players icons reflect the
     * newly selected server instead of the one it replaced.
     */
    private fun syncWhitelistForSelection(name: String, servers: List<MinecraftServer>) {
        _state.update { it.copy(whitelist = emptyList()) }
        if (servers.firstOrNull { it.name == name }?.rconEnabled == true) {
            loadWhitelist(name)
        }
    }

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

    /** Pings every tracked server live, in parallel. */
    fun queryAll() = viewModelScope.launch {
        val servers = state.value.servers
        if (servers.isEmpty()) return@launch
        _state.update { it.copy(queryingAll = true) }
        try {
            coroutineScope {
                servers.map { server ->
                    async {
                        server.name to runCatching {
                            api.send(
                                Endpoint("api/Minecraft/$guildId/servers/${server.name.encoded()}/status"),
                                MinecraftStatus.serializer(),
                            )
                        }.getOrNull()
                    }
                }.awaitAll()
            }.forEach { (name, status) ->
                if (status != null) _state.update { it.copy(statuses = it.statuses + (name to status)) }
            }
        } finally {
            _state.update { it.copy(queryingAll = false) }
        }
    }

    /** Adds a server to track, optionally wiring up watching and a custom embed in the same action. */
    fun addServer(
        name: String,
        address: String,
        port: Int,
        type: McServerType,
        queryPort: Int,
        watchChannelId: Snowflake?,
        watchInterval: Int,
        watchMode: Int,
        customEmbedTemplate: String?,
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
        if (watchChannelId != null) {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/watch",
                    HttpMethod.PUT,
                    jsonBody(
                        "channelId" to watchChannelId.toLongOrNull(),
                        "interval" to watchInterval,
                        "watchMode" to watchMode,
                    ),
                ),
                MinecraftServer.serializer(),
            )
        }
        if (!customEmbedTemplate.isNullOrBlank()) {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/embed",
                    HttpMethod.PUT,
                    jsonBody("template" to customEmbedTemplate),
                ),
                MinecraftServer.serializer(),
            )
        }
        postSuccess("Server added.")
        load(refreshing = true)
    }

    /** Patches whichever server fields are non-null. Pass `"0"` for a channel id to clear it. */
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

    /**
     * Sets where and how often the bot posts the server's status.
     *
     * The controller always applies [channelId] verbatim, so callers that are
     * only changing [interval] or [watchMode] must pass the server's current
     * watch channel id to avoid silently clearing it.
     */
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

    /** Sets or clears the custom watch-embed template. */
    fun setCustomEmbed(name: String, template: String?) =
        launchAction("Failed to update the watch embed.") {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/embed",
                    HttpMethod.PUT,
                    jsonBody("template" to template),
                ),
                MinecraftServer.serializer(),
            )
            load(refreshing = true)
        }

    /** Sets or clears the custom online-alert message. */
    fun setOnlineMessage(name: String, template: String?) =
        launchAction("Failed to update the online alert.") {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/online-message",
                    HttpMethod.PUT,
                    jsonBody("template" to template),
                ),
                MinecraftServer.serializer(),
            )
            load(refreshing = true)
        }

    /** Sets or clears the custom offline-alert message. */
    fun setOfflineMessage(name: String, template: String?) =
        launchAction("Failed to update the offline alert.") {
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/offline-message",
                    HttpMethod.PUT,
                    jsonBody("template" to template),
                ),
                MinecraftServer.serializer(),
            )
            load(refreshing = true)
        }

    /** Sets or clears the bridge event templates. */
    fun setEventTemplates(name: String, templates: McEventTemplates) =
        launchAction("Failed to update event templates.") {
            val encoded = if (templates.isEmpty) {
                null
            } else {
                MewdekoJson.encodeToString(McEventTemplates.serializer(), templates)
            }
            api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/event-templates",
                    HttpMethod.PUT,
                    jsonBody("template" to encoded),
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

    /** Runs an RCON command and appends it to the console history. */
    fun sendRcon(name: String, command: String) = viewModelScope.launch {
        _state.update { it.copy(consoleSending = true) }
        try {
            val response = api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                    HttpMethod.POST,
                    jsonBody("command" to command),
                ),
                RconResponse.serializer(),
            )
            _state.update {
                it.copy(
                    consoleHistory = (
                        it.consoleHistory + ConsoleEntry(
                            command = command,
                            response = response.response,
                            rawResponse = response.rawResponse,
                            success = response.success,
                        )
                        ).takeLast(MaxConsoleHistory),
                )
            }
            if (!response.success) postError("Command failed.")
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    consoleHistory = (
                        it.consoleHistory + ConsoleEntry(command, null, null, success = false)
                        ).takeLast(MaxConsoleHistory),
                )
            }
            postError("Failed to run command.")
        } finally {
            _state.update { it.copy(consoleSending = false) }
        }
    }

    /** Selects the server shown in the RCON console tab, clearing its history. */
    fun selectConsoleServer(name: String?) =
        _state.update { it.copy(consoleServer = name, consoleHistory = emptyList()) }

    /** Selects the server shown in the history tab and loads its snapshots. */
    fun selectHistoryServer(name: String) {
        _state.update { it.copy(historyServer = name) }
        loadHistory(name, state.value.historyHours)
    }

    /** Loads historical snapshots for a server over the given period. */
    fun loadHistory(name: String, hours: Int) = viewModelScope.launch {
        _state.update { it.copy(historyLoading = true, historyHours = hours) }
        val snapshots = runCatching {
            api.send(
                Endpoint("api/Minecraft/$guildId/servers/${name.encoded()}/history?hours=$hours"),
                ListSerializer(MinecraftSnapshot.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(history = snapshots, historyLoading = false) }
    }

    /** Loads the RCON whitelist via `whitelist list`. */
    fun loadWhitelist(name: String) = viewModelScope.launch {
        _state.update { it.copy(whitelistLoading = true) }
        val players = runCatching {
            val response = api.send(
                Endpoint(
                    "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                    HttpMethod.POST,
                    jsonBody("command" to "whitelist list"),
                ),
                RconResponse.serializer(),
            )
            if (response.success) parseWhitelist(response.response) else emptyList()
        }.getOrDefault(emptyList())
        _state.update { it.copy(whitelist = players, whitelistLoading = false) }
    }

    /** Adds a player to the whitelist via RCON and reloads it. */
    fun whitelistAdd(name: String, player: String) = launchAction("Failed to add player.") {
        api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                HttpMethod.POST,
                jsonBody("command" to "whitelist add $player"),
            ),
            RconResponse.serializer(),
        )
        loadWhitelist(name)
    }

    /** Removes a player from the whitelist via RCON and reloads it. */
    fun whitelistRemove(name: String, player: String) = launchAction("Failed to remove player.") {
        api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/rcon",
                HttpMethod.POST,
                jsonBody("command" to "whitelist remove $player"),
            ),
            RconResponse.serializer(),
        )
        loadWhitelist(name)
    }

    /** Generates a fresh key for the companion server plugin. */
    fun generatePluginKey(name: String) = launchAction("Failed to generate plugin key.") {
        val response = api.send(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/plugin-key",
                HttpMethod.POST,
            ),
            PluginKeyResponse.serializer(),
        )
        val wsUrl = resolveBridgeWsUrl()
        _state.update { it.copy(pluginKey = response.key, pluginWsUrl = wsUrl) }
        load(refreshing = true)
    }

    /**
     * Derives the companion plugin's WebSocket bridge URL from the dashboard
     * this client is pointed at, mirroring the `api/mc-bridge/ws` path the
     * mobile gateway exposes alongside every other `api/...` endpoint.
     */
    private suspend fun resolveBridgeWsUrl(): String? {
        val base = api.currentBaseUrl() ?: return null
        val ws = base.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return "$ws/api/mc-bridge/ws"
    }

    /** Revokes the plugin API key for a server. */
    fun revokePluginKey(name: String) = launchAction("Failed to revoke plugin key.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Minecraft/$guildId/servers/${name.encoded()}/plugin-key",
                HttpMethod.DELETE,
            ),
        )
        _state.update { it.copy(pluginKey = null, pluginWsUrl = null) }
        load(refreshing = true)
    }

    /** Clears the displayed plugin key. */
    fun clearPluginKey() = _state.update { it.copy(pluginKey = null, pluginWsUrl = null) }

    /** Stops tracking a server. */
    fun removeServer(name: String) = launchAction("Failed to remove server.") {
        api.sendIgnoringBody(
            Endpoint("api/Minecraft/$guildId/servers/${name.encoded()}", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                servers = it.servers.filterNot { server -> server.name == name },
                statuses = it.statuses - name,
                selectedServer = it.selectedServer?.takeIf { it != name },
            )
        }
        postSuccess("Server removed.")
    }

    /** The dashboard uses the text after the first colon in `whitelist list`'s response. */
    private fun parseWhitelist(response: String?): List<String> {
        val text = response.orEmpty()
        val colon = text.indexOf(':')
        if (colon == -1) return emptyList()
        return text.substring(colon + 1)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private companion object {
        const val MaxConsoleHistory = 50
    }
}
