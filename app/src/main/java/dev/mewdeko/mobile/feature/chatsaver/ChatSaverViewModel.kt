package dev.mewdeko.mobile.feature.chatsaver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** Chat saver screen state: three sections mirroring the dashboard's Fetch, Saved, and View tabs. */
data class ChatSaverState(
    val section: String = "saved",
    val logs: List<ChatLogSummary> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val selectedChannelId: Snowflake? = null,
    val timeAmount: String = "1",
    val timeUnit: ChatTimeUnit = ChatTimeUnit.HOURS,
    val messages: List<ChatLogMessage> = emptyList(),
    val viewingChannelId: Snowflake? = null,
    val viewingChannelName: String? = null,
    val viewingLogId: String? = null,
    val isFetching: Boolean = false,
    val isSaving: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val pendingExport: ChatLogExport? = null,
) {
    /** Messages archived across every saved log. */
    val totalMessages: Int get() = logs.sumOf { it.messageCount }

    /** The channel currently picked in the Fetch section, if any. */
    val selectedChannelName: String?
        get() = availableChannels.firstOrNull { it.id == selectedChannelId }?.name
}

/** Saved chat archives for a guild, plus live channel fetch and export. */
@HiltViewModel
class ChatSaverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    private val session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ChatSaverState())

    /** Observable screen state. */
    val state: StateFlow<ChatSaverState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads saved logs, the channel list, and the member list. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val logs = async {
                runCatching {
                    api.send(Endpoint("api/Chat/$guildId/logs"), ListSerializer(ChatLogSummary.serializer()))
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
            val members = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            _state.update {
                it.copy(
                    logs = logs.await(),
                    availableChannels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    members = members.await(),
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Sets the channel to fetch live history from. */
    fun setChannel(id: Snowflake?) = _state.update { it.copy(selectedChannelId = id) }

    /** Updates the pending time amount text field. */
    fun setTimeAmount(value: String) = _state.update {
        it.copy(timeAmount = value.filter { c -> c.isDigit() }.take(5))
    }

    /** Sets the time unit, clamping the current amount to its maximum. */
    fun setTimeUnit(id: String) = _state.update {
        val unit = ChatTimeUnit.from(id)
        val amount = it.timeAmount.toIntOrNull()
        it.copy(timeUnit = unit, timeAmount = if (amount != null && amount > unit.maxAmount) unit.maxAmount.toString() else it.timeAmount)
    }

    /** Fetches live channel history for the selected channel and time window. */
    fun fetchMessages() = viewModelScope.launch {
        val current = _state.value
        val channelId = current.selectedChannelId
        if (channelId == null) {
            postError("Please select a channel.")
            return@launch
        }
        val amount = current.timeAmount.toIntOrNull()
        if (amount == null || amount <= 0) {
            postError("Time amount must be greater than 0.")
            return@launch
        }
        if (amount > current.timeUnit.maxAmount) {
            postError("Maximum time for ${current.timeUnit.label.lowercase()} is ${current.timeUnit.maxAmount}.")
            return@launch
        }

        _state.update { it.copy(isFetching = true) }
        val after = calculateAfter(current.timeUnit, amount)
        val encodedAfter = URLEncoder.encode(after, "UTF-8").replace("+", "%20")
        val result = runCatching {
            api.send(
                Endpoint("api/Chat/$guildId/$channelId/messages?after=$encodedAfter"),
                ListSerializer(ChatLogMessage.serializer()),
            )
        }
        _state.update { it.copy(isFetching = false) }
        result.onSuccess { fetched ->
            if (fetched.isEmpty()) {
                postError("No messages found in the selected time range.")
                return@onSuccess
            }
            _state.update {
                it.copy(
                    messages = fetched.sortedBy { m -> m.timestamp },
                    viewingChannelId = channelId,
                    viewingChannelName = current.selectedChannelName,
                    viewingLogId = null,
                    section = "view",
                )
            }
        }.onFailure {
            postError("Failed to fetch messages.")
        }
    }

    /** Opens a saved log's full message body in the View section. */
    fun openLog(id: String) = viewModelScope.launch {
        _state.update { it.copy(isLoadingDetail = true) }
        val detail = runCatching {
            api.send(Endpoint("api/Chat/$guildId/logs/$id"), ChatLogDetail.serializer())
        }.getOrNull()
        _state.update { it.copy(isLoadingDetail = false) }
        if (detail == null) {
            postError("Failed to open log.")
            return@launch
        }
        _state.update {
            it.copy(
                messages = detail.messages.sortedBy { m -> m.timestamp },
                viewingChannelId = detail.channelId,
                viewingChannelName = detail.channelName,
                viewingLogId = detail.id,
                section = "view",
            )
        }
    }

    /** Saves the currently fetched, not-yet-saved messages as a new chat log. */
    fun saveLog() = viewModelScope.launch {
        val current = _state.value
        if (current.messages.isEmpty() || current.viewingLogId != null) return@launch
        val channelId = current.viewingChannelId ?: return@launch

        _state.update { it.copy(isSaving = true) }
        val channelName = current.viewingChannelName ?: current.selectedChannelName.orEmpty()
        val logName = "$channelName - ${DateTimeFormatter.ofPattern("M/d/yyyy").format(LocalDate.now())}"
        val messagesJson = MewdekoJson.encodeToJsonElement(ListSerializer(ChatLogMessage.serializer()), current.messages)
        val body = buildJsonObject {
            put("channelId", JsonPrimitive(channelId.asSnowflakeNumber()))
            put("name", JsonPrimitive(logName))
            put("createdBy", JsonPrimitive(session.userId.asSnowflakeNumber()))
            put("messages", messagesJson)
        }
        val result = runCatching {
            api.send(
                Endpoint("api/Chat/$guildId/logs", HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
                SavedChatLogId.serializer(),
            )
        }
        _state.update { it.copy(isSaving = false) }
        result.onSuccess { saved ->
            _state.update { it.copy(viewingLogId = saved.id) }
            postSuccess("Log saved.")
            load(refreshing = true)
        }.onFailure {
            postError("Failed to save log.")
        }
    }

    /** Generates a standalone HTML transcript of the currently viewed messages. */
    fun exportHtml(guildName: String) {
        val current = _state.value
        if (current.messages.isEmpty()) return
        val channelName = current.viewingChannelName ?: current.selectedChannelName.orEmpty()
        val html = buildChatTranscriptHtml(guildName, channelName, current.messages, current.members, current.availableChannels)
        val filename = "$channelName-${DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDate.now())}.html"
        _state.update { it.copy(pendingExport = ChatLogExport(filename, html)) }
    }

    /** Clears the pending export once the share sheet has been launched. */
    fun clearPendingExport() = _state.update { it.copy(pendingExport = null) }

    /** Deletes a saved log. */
    fun delete(log: ChatLogSummary) = launchAction("Failed to delete log.") {
        api.sendIgnoringBody(Endpoint("api/Chat/$guildId/logs/${log.id}", HttpMethod.DELETE))
        _state.update {
            it.copy(
                logs = it.logs.filterNot { entry -> entry.id == log.id },
                viewingLogId = if (it.viewingLogId == log.id) null else it.viewingLogId,
                messages = if (it.viewingLogId == log.id) emptyList() else it.messages,
            )
        }
        postSuccess("Log deleted.")
    }

    /** Renames a saved log. */
    fun rename(log: ChatLogSummary, name: String) = launchAction("Failed to rename log.") {
        api.sendIgnoringBody(
            Endpoint("api/Chat/$guildId/logs/${log.id}", HttpMethod.PATCH, jsonBody("name" to name))
        )
        _state.update { current ->
            current.copy(
                logs = current.logs.map { if (it.id == log.id) it.copy(name = name) else it },
            )
        }
        postSuccess("Log renamed.")
    }

    private fun calculateAfter(unit: ChatTimeUnit, amount: Int): String {
        val now = Instant.now()
        val result = when (unit) {
            ChatTimeUnit.MINUTES -> now.minus(amount.toLong(), ChronoUnit.MINUTES)
            ChatTimeUnit.HOURS -> now.minus(amount.toLong(), ChronoUnit.HOURS)
            ChatTimeUnit.DAYS -> now.minus(amount.toLong(), ChronoUnit.DAYS)
        }
        return DateTimeFormatter.ISO_INSTANT.format(result)
    }
}
