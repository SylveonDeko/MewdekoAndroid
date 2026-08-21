package dev.mewdeko.mobile.feature.chatsaver

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** Summary row for one saved chat log. */
@Serializable
data class ChatLogSummary(
    val id: String = "0",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
    val name: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val timestamp: String? = null,
    val messageCount: Int = 0,
) {
    /** The label to show, preferring the user-assigned name. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: channelName?.let { "#$it" }
            ?: "Log $id"
}

/** The author block inside an archived message. */
@Serializable
data class ChatLogAuthor(
    val username: String = "Unknown",
    val avatarUrl: String? = null,
)

/** One archived message within a chat log. */
@Serializable
data class ChatLogMessage(
    val id: String = "",
    val content: String? = null,
    val author: ChatLogAuthor = ChatLogAuthor(),
    val timestamp: String? = null,
)

/** A chat log with its full message body. */
@Serializable
data class ChatLogDetail(
    val name: String? = null,
    val channelName: String? = null,
    val messageCount: Int = 0,
    val messages: List<ChatLogMessage> = emptyList(),
)

/** Chat saver screen state. */
data class ChatSaverState(
    val logs: List<ChatLogSummary> = emptyList(),
    val openLog: ChatLogDetail? = null,
    val openLogId: String? = null,
    val isLoadingDetail: Boolean = false,
) {
    /** Messages archived across every log. */
    val totalMessages: Int get() = logs.sumOf { it.messageCount }
}

/** Saved chat archives for a guild. */
@HiltViewModel
class ChatSaverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ChatSaverState())

    /** Observable screen state. */
    val state: StateFlow<ChatSaverState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the list of saved logs. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val logs = api.send(
            Endpoint("api/Chat/$guildId/logs"),
            ListSerializer(ChatLogSummary.serializer()),
        )
        _state.update { it.copy(logs = logs) }
    }

    /** Fetches and opens one log's full message body. */
    fun openLog(id: String) = viewModelScope.launch {
        _state.update { it.copy(isLoadingDetail = true, openLogId = id) }
        val detail = runCatching {
            api.send(Endpoint("api/Chat/$guildId/logs/$id"), ChatLogDetail.serializer())
        }.getOrNull()
        if (detail == null) postError("Failed to open log.")
        _state.update { it.copy(openLog = detail, isLoadingDetail = false) }
    }

    /** Closes the open log viewer. */
    fun closeLog() = _state.update { it.copy(openLog = null, openLogId = null) }

    /** Deletes a saved log. */
    fun delete(log: ChatLogSummary) = launchAction("Failed to delete log.") {
        api.sendIgnoringBody(
            Endpoint("api/Chat/$guildId/logs/${log.id}", HttpMethod.DELETE)
        )
        _state.update { it.copy(logs = it.logs.filterNot { entry -> entry.id == log.id }) }
        postSuccess("Log deleted.")
    }

    /** Renames a saved log. */
    fun rename(log: ChatLogSummary, name: String) = launchAction("Failed to rename log.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Chat/$guildId/logs/${log.id}",
                HttpMethod.PATCH,
                jsonBody("name" to name),
            )
        )
        _state.update { current ->
            current.copy(
                logs = current.logs.map { if (it.id == log.id) it.copy(name = name) else it },
            )
        }
        postSuccess("Log renamed.")
    }
}
