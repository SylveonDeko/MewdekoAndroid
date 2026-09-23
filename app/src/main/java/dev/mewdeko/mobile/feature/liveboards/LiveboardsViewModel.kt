package dev.mewdeko.mobile.feature.liveboards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** The pending "Add a live board" form. */
data class LiveBoardDraft(
    val channelId: Snowflake? = null,
    val kind: LiveBoardKind = LiveBoardKind.INVITE_LEADERBOARD,
    val range: LiveBoardRange = LiveBoardRange.WEEKLY,
    val pin: Boolean = true,
    val entries: Int = 10,
    val intervalText: String = "15",
) {
    /** The interval as typed, or `null` when it is not a number. */
    val intervalMinutes: Int? get() = intervalText.trim().toIntOrNull()

    /** Whether the typed interval is inside the bot's accepted bounds. */
    val intervalValid: Boolean
        get() = intervalMinutes?.let { it in LiveBoardLimits.MIN_INTERVAL..LiveBoardLimits.MAX_INTERVAL } == true
}

/** Live boards screen state. */
data class LiveboardsState(
    val section: String = "boards",
    val channels: List<TextChannelLite> = emptyList(),
    val boards: List<LiveBoard> = emptyList(),
    val boardsError: String? = null,
    val report: ServerReportSettings? = null,
    val reportError: String? = null,
    val draft: LiveBoardDraft = LiveBoardDraft(),
    val isCreating: Boolean = false,
    val isRefreshingBoards: Boolean = false,
    val deletingIds: Set<Int> = emptySet(),
    val isSavingReport: Boolean = false,
    val isSendingReport: Boolean = false,
) {
    /** Whether the guild already has the most boards the bot allows. */
    val atLimit: Boolean get() = boards.size >= LiveBoardLimits.MAX_BOARDS

    /** The channel's display name, falling back to its id. */
    fun channelName(id: Snowflake?): String {
        if (id.isNullOrEmpty() || id == "0") return "-"
        return channels.firstOrNull { it.id == id }?.name ?: id
    }
}

/** Loads and edits a guild's live boards and server report schedule. */
@HiltViewModel
class LiveboardsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(LiveboardsState())

    /** Observable screen state. */
    val state: StateFlow<LiveboardsState> = _state.asStateFlow()

    private val base: String get() = "api/LiveBoards/$guildId"

    init {
        load()
    }

    /** Loads text channels, the board list, and the report settings together. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val boards = async { runCatching { fetchBoards() } }
            val report = async { runCatching { fetchReport() } }

            val boardsResult = boards.await()
            val reportResult = report.await()
            val boardsFailure = boardsResult.exceptionOrNull()
            val reportFailure = reportResult.exceptionOrNull()
            if (boardsFailure != null && reportFailure != null) throw boardsFailure

            _state.update { current ->
                current.copy(
                    channels = channels.await().sortedBy { it.name.lowercase() },
                    boards = boardsResult.getOrNull() ?: current.boards,
                    boardsError = boardsFailure?.userFacingMessage,
                    report = reportResult.getOrNull() ?: current.report,
                    reportError = reportFailure?.userFacingMessage,
                )
            }
        }
    }

    /** Switches between the boards and reports sections. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Reloads only the board list, for the section's retry button. */
    fun reloadBoards() = viewModelScope.launch {
        runCatching { fetchBoards() }
            .onSuccess { list -> _state.update { it.copy(boards = list, boardsError = null) } }
            .onFailure { t -> _state.update { it.copy(boardsError = t.userFacingMessage) } }
    }

    /** Reloads only the report settings, for the section's retry button. */
    fun reloadReport() = viewModelScope.launch {
        runCatching { fetchReport() }
            .onSuccess { report -> _state.update { it.copy(report = report, reportError = null) } }
            .onFailure { t -> _state.update { it.copy(reportError = t.userFacingMessage) } }
    }

    /** Sets the channel the new board is posted in. */
    fun setDraftChannel(id: Snowflake?) = editDraft { it.copy(channelId = id) }

    /** Sets what the new board shows. */
    fun setDraftKind(kind: LiveBoardKind) = editDraft { it.copy(kind = kind) }

    /** Sets the window the new board covers. */
    fun setDraftRange(range: LiveBoardRange) = editDraft { it.copy(range = range) }

    /** Sets whether the new board's message is pinned. */
    fun setDraftPin(pin: Boolean) = editDraft { it.copy(pin = pin) }

    /** Sets how many rows a new leaderboard shows. */
    fun setDraftEntries(entries: Int) = editDraft {
        it.copy(entries = entries.coerceIn(LiveBoardLimits.MIN_ENTRIES, LiveBoardLimits.MAX_ENTRIES))
    }

    /** Sets the refresh interval text for the new board. */
    fun setDraftInterval(text: String) = editDraft { it.copy(intervalText = text.filter(Char::isDigit).take(4)) }

    /** Posts a new live board with the drafted settings. */
    fun createBoard() {
        val current = _state.value
        val draft = current.draft
        val channelId = draft.channelId
        if (current.isCreating || current.atLimit) return
        if (channelId.isNullOrEmpty()) {
            postError("Pick the channel to post in.")
            return
        }
        val interval = draft.intervalMinutes
        if (interval == null || !draft.intervalValid) {
            postError(
                "Refresh interval must be between ${LiveBoardLimits.MIN_INTERVAL} and " +
                    "${LiveBoardLimits.MAX_INTERVAL} minutes."
            )
            return
        }
        _state.update { it.copy(isCreating = true) }
        viewModelScope.launch {
            val body = buildJsonObject {
                put("channelId", JsonPrimitive(channelId.asSnowflakeNumber()))
                put("kind", JsonPrimitive(draft.kind.value))
                put("range", JsonPrimitive(draft.range.value))
                put("pin", JsonPrimitive(draft.pin))
                put("entries", JsonPrimitive(draft.entries))
                put("intervalMinutes", JsonPrimitive(interval))
            }
            val result = runCatching {
                api.sendIgnoringBody(
                    Endpoint(base, HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body))
                )
            }
            result.onFailure { t -> postError(t.userFacingMessage) }
            if (result.isSuccess) {
                postSuccess("${draft.kind.label} posted in #${_state.value.channelName(channelId)}.")
                runCatching { fetchBoards() }.onSuccess { list ->
                    _state.update { it.copy(boards = list, boardsError = null) }
                }
            }
            _state.update { it.copy(isCreating = false) }
        }
    }

    /** Deletes a live board and its Discord message. */
    fun deleteBoard(board: LiveBoard) {
        if (board.id in _state.value.deletingIds) return
        _state.update { it.copy(deletingIds = it.deletingIds + board.id) }
        viewModelScope.launch {
            val ok = runCatching {
                api.sendIgnoringBody(Endpoint("$base/${board.id}", HttpMethod.DELETE))
            }.isSuccess
            _state.update {
                it.copy(
                    deletingIds = it.deletingIds - board.id,
                    boards = if (ok) it.boards.filterNot { b -> b.id == board.id } else it.boards,
                )
            }
            if (!ok) postError("Failed to delete the live board.")
        }
    }

    /** Asks the bot to redraw every board now, then reloads the list. */
    fun refreshAll() {
        if (_state.value.isRefreshingBoards) return
        _state.update { it.copy(isRefreshingBoards = true) }
        viewModelScope.launch {
            val ok = runCatching {
                api.sendIgnoringBody(Endpoint("$base/refresh", HttpMethod.POST))
            }.isSuccess
            if (ok) {
                runCatching { fetchBoards() }.onSuccess { list ->
                    _state.update { it.copy(boards = list, boardsError = null) }
                }
            } else {
                postError("Failed to refresh the live boards.")
            }
            _state.update { it.copy(isRefreshingBoards = false) }
        }
    }

    /** Sets the report channel, which also turns reports on. */
    fun setReportChannel(id: Snowflake) = updateReport(
        buildJsonObject {
            put("channelId", JsonPrimitive(id.asSnowflakeNumber()))
            put("enabled", JsonPrimitive(true))
        }
    )

    /** Clears the report channel, which disables reports. */
    fun clearReportChannel() = updateReport(
        buildJsonObject { put("clearChannel", JsonPrimitive(true)) }
    )

    /** Sets how often reports post. */
    fun setReportFrequency(frequency: ReportFrequency) = updateReport(
        buildJsonObject { put("frequency", JsonPrimitive(frequency.value)) }
    )

    /** Turns scheduled reports on or off without losing the channel. */
    fun setReportEnabled(enabled: Boolean) = updateReport(
        buildJsonObject { put("enabled", JsonPrimitive(enabled)) }
    )

    /** Posts a report to the configured channel now. */
    fun sendReportNow() {
        if (_state.value.isSendingReport) return
        _state.update { it.copy(isSendingReport = true) }
        viewModelScope.launch {
            val result = runCatching {
                api.send(Endpoint("$base/report/send", HttpMethod.POST), Boolean.serializer())
            }
            result.onSuccess { sent ->
                if (sent) {
                    val channel = _state.value.channelName(_state.value.report?.activeChannelId)
                    postSuccess("Report posted in #$channel.")
                } else {
                    postError("No report channel is set.")
                }
                runCatching { fetchReport() }.onSuccess { report ->
                    _state.update { it.copy(report = report, reportError = null) }
                }
            }
            result.onFailure { postError("Failed to send the report.") }
            _state.update { it.copy(isSendingReport = false) }
        }
    }

    private fun updateReport(body: JsonObject) {
        _state.update { it.copy(isSavingReport = true) }
        viewModelScope.launch {
            val result = runCatching {
                api.send(
                    Endpoint(
                        "$base/report",
                        HttpMethod.PUT,
                        MewdekoJson.encodeToString(JsonObject.serializer(), body),
                    ),
                    ServerReportSettings.serializer(),
                )
            }
            result.onSuccess { report -> _state.update { it.copy(report = report, reportError = null) } }
            result.onFailure { postError("Failed to save report settings.") }
            _state.update { it.copy(isSavingReport = false) }
        }
    }

    private suspend fun fetchBoards(): List<LiveBoard> =
        api.send(Endpoint(base), ListSerializer(LiveBoard.serializer()))

    private suspend fun fetchReport(): ServerReportSettings =
        api.send(Endpoint("$base/report"), ServerReportSettings.serializer())

    private fun editDraft(transform: (LiveBoardDraft) -> LiveBoardDraft) {
        _state.update { it.copy(draft = transform(it.draft)) }
    }
}
