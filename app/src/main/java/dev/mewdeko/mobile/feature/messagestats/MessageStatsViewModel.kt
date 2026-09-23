package dev.mewdeko.mobile.feature.messagestats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Message stats screen state. */
data class MessageStatsState(
    val stats: MessageStatsDetail? = null,
    val leaderboard: List<MessageStatsUser> = emptyList(),
    val members: Map<Snowflake, GuildMember> = emptyMap(),
    val enabled: Boolean = false,
    val minMessageLength: Int = 0,
    val settingsLoading: Boolean = false,
    val resetLoading: Boolean = false,
    val section: String = "overview",
    val exportFormat: MessageStatsExportFormat = MessageStatsExportFormat.CSV,
    val includeUsers: Boolean = true,
    val includeChannels: Boolean = true,
    val includeHourly: Boolean = false,
    val exportStartDate: LocalDate = LocalDate.now().minusDays(7),
    val exportEndDate: LocalDate = LocalDate.now(),
    val isExporting: Boolean = false,
)

/** Per-channel and per-member message activity. */
@HiltViewModel
class MessageStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(MessageStatsState())

    /** Observable screen state. */
    val state: StateFlow<MessageStatsState> = _state.asStateFlow()

    private var loadedGuildConfig: JsonObject = JsonObject(emptyMap())
    private var pendingExportFileName: String? = null

    init {
        load()
    }

    /** Reloads counters, the leaderboard, member lookups, and settings. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/stats"),
                        MessageStatsDetail.serializer(),
                    )
                }.getOrNull()
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/leaderboard?limit=25"),
                        MessageStatsLeaderboard.serializer(),
                    )
                }.getOrNull()
            }
            val status = async {
                runCatching {
                    api.send(
                        Endpoint("api/MessageCount/$guildId/status"),
                        MessageCountStatus.serializer(),
                    )
                }.getOrNull()
            }
            val members = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val config = async {
                runCatching {
                    api.sendRaw(Endpoint("api/GuildConfig/$guildId")) as? JsonObject
                }.getOrNull() ?: JsonObject(emptyMap())
            }

            val loadedStats = stats.await()
            loadedGuildConfig = config.await()

            _state.update {
                it.copy(
                    stats = loadedStats,
                    leaderboard = leaderboard.await()?.leaderboard.orEmpty(),
                    enabled = status.await()?.enabled ?: loadedStats?.enabled ?: false,
                    members = members.await().associateBy { member -> member.id },
                    minMessageLength = loadedGuildConfig.intAt("minMessageLength")
                        ?: loadedGuildConfig.intAt("MinMessageLength")
                        ?: 0,
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Turns message counting on or off. */
    fun toggleCounting() = launchAction("Failed to toggle message counting.") {
        val response = api.send(
            Endpoint("api/MessageCount/$guildId/toggle", HttpMethod.POST),
            MessageCountStatus.serializer(),
        )
        _state.update { it.copy(enabled = response.enabled) }
        load(refreshing = true)
    }

    /**
     * Resets stored counts. Passing neither id clears the whole guild; passing
     * one scopes the reset to that member or channel.
     */
    fun reset(userId: Snowflake? = null, channelId: Snowflake? = null) =
        launchAction("Failed to reset counts.") {
            _state.update { it.copy(resetLoading = true) }
            try {
                val query = buildList {
                    userId?.toLongOrNull()?.let { add("userId=$it") }
                    channelId?.toLongOrNull()?.let { add("channelId=$it") }
                }.joinToString("&").let { if (it.isEmpty()) "" else "?$it" }

                api.sendIgnoringBody(
                    Endpoint("api/MessageCount/$guildId/reset$query", HttpMethod.POST)
                )
                load(refreshing = true)
            } finally {
                _state.update { it.copy(resetLoading = false) }
            }
        }

    /**
     * Refetches the guild config, merges in the new minimum message length,
     * and posts the whole object back, mirroring the dashboard's read-modify-
     * write flow so unrelated fields are not clobbered.
     */
    fun saveMinMessageLength(value: Int) = launchAction("Failed to update setting.") {
        _state.update { it.copy(settingsLoading = true) }
        try {
            val fresh = runCatching {
                api.sendRaw(Endpoint("api/GuildConfig/$guildId")) as? JsonObject
            }.getOrNull() ?: loadedGuildConfig

            val merged = buildJsonObject {
                fresh.forEach { (key, v) -> put(key, v) }
                put("MinMessageLength", JsonPrimitive(value))
            }

            api.sendIgnoringBody(
                Endpoint(
                    "api/GuildConfig/$guildId",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(JsonObject.serializer(), merged),
                )
            )
            loadedGuildConfig = merged
            _state.update { it.copy(minMessageLength = value) }
        } finally {
            _state.update { it.copy(settingsLoading = false) }
        }
    }

    /** Sets the export file format. */
    fun setExportFormat(format: MessageStatsExportFormat) =
        _state.update { it.copy(exportFormat = format) }

    /** Toggles whether the export includes per-member rows. */
    fun setIncludeUsers(value: Boolean) = _state.update { it.copy(includeUsers = value) }

    /** Toggles whether the export includes per-channel rows. */
    fun setIncludeChannels(value: Boolean) = _state.update { it.copy(includeChannels = value) }

    /** Toggles whether the export includes the busiest hours and days breakdown. */
    fun setIncludeHourly(value: Boolean) = _state.update { it.copy(includeHourly = value) }

    /** Sets the export range's start date. */
    fun setExportStartDate(date: LocalDate) = _state.update { it.copy(exportStartDate = date) }

    /** Sets the export range's end date. */
    fun setExportEndDate(date: LocalDate) = _state.update { it.copy(exportEndDate = date) }

    /**
     * Records the export file for the current options and returns its
     * suggested name, so the screen can ask where to save it. Kept here
     * rather than in the composable so it survives a configuration change
     * while the system file picker is open.
     */
    fun prepareExport(): String {
        val format = _state.value.exportFormat
        val date = LocalDate.now()
        val name = "message-stats-$date.${format.extension}"
        pendingExportFileName = name
        return name
    }

    /** Forgets a prepared export after the user cancels the file picker. */
    fun cancelExport() {
        pendingExportFileName = null
    }

    /**
     * Builds the export from the already-loaded stats and member lookups (the
     * bot has no export endpoint) and hands the bytes to [write], which
     * persists them to the file the user picked.
     */
    fun export(write: suspend (ByteArray) -> Unit) = viewModelScope.launch {
        val fileName = pendingExportFileName ?: return@launch
        pendingExportFileName = null
        val current = _state.value
        _state.update { it.copy(isExporting = true) }
        try {
            val text = when (current.exportFormat) {
                MessageStatsExportFormat.CSV -> buildCsvExport(current)
                MessageStatsExportFormat.JSON -> buildJsonExport(current)
            }
            write(text.toByteArray(Charsets.UTF_8))
            postSuccess("Saved $fileName.")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError("Failed to export stats.")
        } finally {
            _state.update { it.copy(isExporting = false) }
        }
    }

    private fun usernameFor(state: MessageStatsState, userId: Snowflake?): String {
        val member = userId?.let { state.members[it] } ?: return "Unknown User"
        return member.displayName.ifBlank { member.username }.ifBlank { "Unknown User" }
    }

    private fun buildCsvExport(state: MessageStatsState): String {
        val stats = state.stats
        val summary = toCsv(
            listOf(
                linkedMapOf(
                    "guildId" to guildId,
                    "guildName" to guildName,
                    "enabled" to (stats?.enabled ?: false),
                    "totalMessages" to (stats?.totalMessages ?: 0L),
                    "dailyMessages" to (stats?.dailyMessages ?: 0L),
                    "lastUpdated" to stats?.lastUpdated,
                    "exportedAt" to Instant.now(),
                    "startDate" to state.exportStartDate,
                    "endDate" to state.exportEndDate,
                )
            )
        )

        val sections = buildList {
            add(summary)
            if (state.includeUsers) {
                val rows = (stats?.topUsers.orEmpty()).mapIndexed { index, user ->
                    linkedMapOf(
                        "rank" to (index + 1),
                        "userId" to user.userId,
                        "username" to usernameFor(state, user.userId),
                        "totalMessages" to user.totalMessages,
                        "dailyMessages" to user.dailyMessages,
                        "percentage" to user.percentage,
                    )
                }
                if (rows.isNotEmpty()) add("\nTop Users\n${toCsv(rows)}")
            }
            if (state.includeChannels) {
                val rows = (stats?.topChannels.orEmpty()).map { channel ->
                    linkedMapOf(
                        "channelId" to channel.channelId,
                        "channelName" to channel.channelName,
                        "totalMessages" to channel.totalMessages,
                        "dailyMessages" to channel.dailyMessages,
                        "percentage" to channel.percentage,
                    )
                }
                if (rows.isNotEmpty()) add("\nTop Channels\n${toCsv(rows)}")
            }
            if (state.includeHourly) {
                val hourRows = (stats?.busiestHours.orEmpty()).map {
                    linkedMapOf("hour" to it.hour, "messageCount" to it.messageCount)
                }
                if (hourRows.isNotEmpty()) add("\nBusiest Hours\n${toCsv(hourRows)}")
                val dayRows = (stats?.busiestDays.orEmpty()).map {
                    linkedMapOf("day" to it.day, "messageCount" to it.messageCount)
                }
                if (dayRows.isNotEmpty()) add("\nBusiest Days\n${toCsv(dayRows)}")
            }
        }

        return sections.joinToString("\n")
    }

    private fun buildJsonExport(state: MessageStatsState): String {
        val stats = state.stats
        val payload = buildJsonObject {
            put("guildId", guildId)
            put("guildName", guildName)
            put("exportedAt", Instant.now().toString())
            put("startDate", state.exportStartDate.toString())
            put("endDate", state.exportEndDate.toString())
            put("summary", if (stats == null) JsonNull else buildJsonObject {
                put("enabled", stats.enabled)
                put("totalMessages", stats.totalMessages)
                put("dailyMessages", stats.dailyMessages)
                put("lastUpdated", stats.lastUpdated?.toString())
            })
            put("users", if (!state.includeUsers) JsonArray(emptyList()) else JsonArray(
                (stats?.topUsers.orEmpty()).mapIndexed { index, user ->
                    buildJsonObject {
                        put("rank", index + 1)
                        put("userId", user.userId)
                        put("username", usernameFor(state, user.userId))
                        put("totalMessages", user.totalMessages)
                        put("dailyMessages", user.dailyMessages)
                        put("percentage", user.percentage)
                    }
                }
            ))
            put("channels", if (!state.includeChannels) JsonArray(emptyList()) else JsonArray(
                (stats?.topChannels.orEmpty()).map { channel ->
                    buildJsonObject {
                        put("channelId", channel.channelId)
                        put("channelName", channel.channelName)
                        put("totalMessages", channel.totalMessages)
                        put("dailyMessages", channel.dailyMessages)
                        put("percentage", channel.percentage)
                    }
                }
            ))
            put(
                "leastActiveUser",
                stats?.leastActiveUser?.let {
                    buildJsonObject {
                        put("userId", it.userId)
                        put("username", usernameFor(state, it.userId))
                        put("totalMessages", it.totalMessages)
                    }
                } ?: JsonNull,
            )
            put(
                "leastActiveChannel",
                stats?.leastActiveChannel?.let {
                    buildJsonObject {
                        put("channelId", it.channelId)
                        put("channelName", it.channelName)
                        put("totalMessages", it.totalMessages)
                    }
                } ?: JsonNull,
            )
            put("hourlyStats", if (!state.includeHourly) JsonArray(emptyList()) else JsonArray(
                (stats?.busiestHours.orEmpty()).map {
                    buildJsonObject { put("hour", it.hour); put("messageCount", it.messageCount) }
                }
            ))
            put("busiestDays", if (!state.includeHourly) JsonArray(emptyList()) else JsonArray(
                (stats?.busiestDays.orEmpty()).map {
                    buildJsonObject { put("day", it.day); put("messageCount", it.messageCount) }
                }
            ))
        }
        return MewdekoJson.encodeToString(JsonObject.serializer(), payload)
    }

    private fun toCsv(rows: List<Map<String, Any?>>): String {
        if (rows.isEmpty()) return ""
        val headers = rows.first().keys.toList()
        val lines = buildList {
            add(headers.joinToString(",") { csvEscape(it) })
            rows.forEach { row -> add(headers.joinToString(",") { csvEscape(row[it]) }) }
        }
        return lines.joinToString("\n")
    }

    private fun csvEscape(value: Any?): String {
        if (value == null) return ""
        val text = value.toString()
        return if (text.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
    }

    private fun JsonObject.intAt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }?.toInt()
}
