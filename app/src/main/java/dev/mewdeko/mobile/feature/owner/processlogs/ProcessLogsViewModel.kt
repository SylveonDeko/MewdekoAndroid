package dev.mewdeko.mobile.feature.owner.processlogs

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads and follows the pm2 logs on the bot's host, mirroring the
 * dashboard's `/owner/process-logs`.
 *
 * Following is plain HTTP polling with a byte cursor: the tail call returns
 * the last lines and an `end` offset, and every poll asks for what was
 * appended after it. Both timers only run while the screen is started, see
 * [setActive].
 */
@HiltViewModel
class ProcessLogsViewModel @Inject constructor(
    api: ApiClient,
    session: SessionHolder,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(ProcessLogsState())

    /** Observable screen state. */
    val state: StateFlow<ProcessLogsState> = _state.asStateFlow()

    /** Whether the log view currently rests at its newest line, reported by the screen. */
    private var atBottom = true

    /** Bumped by every tail load, so a poll answered after a reload is dropped. */
    private var generation = 0

    private var tailJob: Job? = null
    private var pollJob: Job? = null
    private var processTimerJob: Job? = null
    private var polling = false

    init {
        markLoaded()
        viewModelScope.launch { loadProcesses(initial = true) }
        viewModelScope.launch {
            session.instance
                .map { it?.botId }
                .distinctUntilChanged()
                .drop(1)
                .collect { resetForInstance() }
        }
    }

    /** Pull to refresh: re-reads the process list and the selected log's tail. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        loadProcesses(initial = !refreshing && _state.value.processList == null)
        if (refreshing && _state.value.selected != null) loadTail().join()
    }

    /** The section's Refresh button: re-reads the process list without a spinner. */
    fun refreshProcesses() {
        viewModelScope.launch { loadProcesses(initial = false) }
    }

    /**
     * Starts or stops both timers, following the screen's lifecycle so
     * nothing polls while the app is in the background or the page is gone.
     */
    fun setActive(active: Boolean) {
        processTimerJob?.cancel()
        pollJob?.cancel()
        processTimerJob = null
        pollJob = null
        if (!active) return
        processTimerJob = viewModelScope.launch {
            while (true) {
                delay(PROCESS_REFRESH_MS.milliseconds)
                loadProcesses(initial = false)
            }
        }
        pollJob = viewModelScope.launch {
            while (true) {
                delay(POLL_MS.milliseconds)
                poll()
            }
        }
    }

    /** Records whether the log view is at its newest line; reaching it clears the new line count. */
    fun setAtBottom(value: Boolean) {
        atBottom = value
        if (value && _state.value.pendingNew != 0) _state.update { it.copy(pendingNew = 0) }
    }

    /** Selects a process and loads its tail. Selecting the current one does nothing. */
    fun selectProcess(pmId: Int) {
        if (pmId == _state.value.selectedPmId) return
        _state.update {
            it.copy(selectedPmId = pmId, lines = emptyList(), chunk = null, pendingNew = 0, logError = null)
        }
        loadTail()
    }

    /** Switches between stdout and stderr, clearing the buffer. */
    fun setStream(stream: Pm2Stream) {
        if (stream == _state.value.stream) return
        _state.update { it.copy(stream = stream, lines = emptyList(), chunk = null, pendingNew = 0) }
        loadTail()
    }

    /** Changes how many lines a tail asks for and reloads it. */
    fun setLineCount(count: Int) {
        if (count == _state.value.lineCount) return
        _state.update { it.copy(lineCount = count) }
        loadTail()
    }

    /** Pauses or resumes following; resuming continues from the stored cursor. */
    fun toggleFollow() = _state.update { it.copy(follow = !it.follow) }

    /** Toggles line wrapping. */
    fun toggleWrap() = _state.update { it.copy(wrap = !it.wrap) }

    /** Updates the line filter. */
    fun setSearch(text: String) = _state.update { it.copy(search = text) }

    /** Hides or shows one level. */
    fun toggleLevel(level: LogLevel) = _state.update {
        it.copy(hiddenLevels = if (level in it.hiddenLevels) it.hiddenLevels - level else it.hiddenLevels + level)
    }

    /** Shows every level again. */
    fun showAllLevels() = _state.update { it.copy(hiddenLevels = emptySet()) }

    /** Clears the new line count once the view has jumped to the bottom. */
    fun jumpedToBottom() = setAtBottom(true)

    /** Shows [message] in the log's error banner, for failures the screen detects itself. */
    fun showLogError(message: String) = _state.update { it.copy(logError = message) }

    /** Re-runs the tail fetch for the selected process and stream. */
    fun reloadTail() {
        loadTail()
    }

    /** Clears everything tied to the previous bot and loads the new one's processes. */
    private fun resetForInstance() {
        tailJob?.cancel()
        generation++
        _state.update {
            it.copy(
                processList = null,
                processesError = null,
                selectedPmId = null,
                lines = emptyList(),
                chunk = null,
                pendingNew = 0,
                logError = null,
                logLoading = false,
                lastUpdated = null,
            )
        }
        viewModelScope.launch { loadProcesses(initial = true) }
    }

    /**
     * Reads the process list. A selection that disappeared is cleared, and
     * with nothing selected the bot's own process, else the first, is picked.
     */
    private suspend fun loadProcesses(initial: Boolean) {
        if (initial) _state.update { it.copy(processesLoading = true) }
        _state.update { it.copy(processesError = null) }
        try {
            val list = fetch("api/Pm2/processes", Pm2ProcessList.serializer(), "processes")
            _state.update { current ->
                val keep = current.selectedPmId?.takeIf { id -> list.processes.any { it.pmId == id } }
                val dropped = current.selectedPmId != null && keep == null
                current.copy(
                    processList = list,
                    processesAt = System.currentTimeMillis(),
                    processesLoading = false,
                    selectedPmId = keep,
                    lines = if (dropped) emptyList() else current.lines,
                    chunk = if (dropped) null else current.chunk,
                    pendingNew = if (dropped) 0 else current.pendingNew,
                )
            }
            if (_state.value.selectedPmId == null && list.processes.isNotEmpty()) {
                val preferred = list.processes.firstOrNull { it.isSelf } ?: list.processes.first()
                selectProcess(preferred.pmId)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    processesLoading = false,
                    processesError = describePm2Error(t, "Failed to load the process list"),
                )
            }
        }
    }

    /**
     * Loads the tail of the selected log, replacing the buffer. An answer for
     * a selection or stream that changed in the meantime is dropped.
     */
    private fun loadTail(): Job {
        tailJob?.cancel()
        generation++
        val token = generation
        val job = viewModelScope.launch {
            val current = _state.value
            val process = current.selected ?: return@launch
            val pmId = process.pmId
            val stream = current.stream
            _state.update { it.copy(logLoading = true, logError = null) }
            try {
                val chunk = fetch(
                    "api/Pm2/logs/$pmId?stream=${stream.query}&lines=${current.lineCount}",
                    Pm2LogChunk.serializer(),
                    "end",
                )
                val lines = withContext(Dispatchers.Default) { Ansi.toViewLines(chunk.lines, null) }
                if (!matches(pmId, stream, token)) return@launch
                _state.update {
                    it.copy(
                        lines = lines,
                        chunk = chunk.copy(lines = emptyList()),
                        lastUpdated = System.currentTimeMillis(),
                        pendingNew = 0,
                        logLoading = false,
                        scrollToken = it.scrollToken + 1,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (!matches(pmId, stream, token)) return@launch
                _state.update {
                    it.copy(
                        logError = describePm2Error(t, "Failed to read the log"),
                        lines = emptyList(),
                        chunk = null,
                        logLoading = false,
                    )
                }
            }
        }
        tailJob = job
        return job
    }

    /**
     * Fetches whatever was appended since the stored cursor. Runs one at a
     * time; a rotated file replaces the buffer, anything else is appended and
     * the buffer is trimmed to [ProcessLogsState.MAX_BUFFER] lines.
     */
    private suspend fun poll() {
        val current = _state.value
        val process = current.selected
        val chunk = current.chunk
        if (polling || !current.follow || process == null || chunk == null) return
        polling = true
        val pmId = process.pmId
        val stream = current.stream
        val token = generation
        try {
            val result = fetch(
                "api/Pm2/logs/$pmId/updates?stream=${stream.query}&offset=${chunk.end}&lines=${current.lineCount}",
                Pm2LogChunk.serializer(),
                "end",
            )
            if (!matches(pmId, stream, token)) return

            if (result.rotated) {
                val lines = withContext(Dispatchers.Default) { Ansi.toViewLines(result.lines, null) }
                if (!matches(pmId, stream, token)) return
                _state.update {
                    it.copy(
                        lines = lines,
                        chunk = result.copy(lines = emptyList()),
                        lastUpdated = System.currentTimeMillis(),
                        pendingNew = 0,
                        logError = null,
                        scrollToken = it.scrollToken + 1,
                    )
                }
                return
            }

            if (result.lines.isEmpty()) {
                _state.update { state ->
                    state.copy(
                        chunk = state.chunk?.copy(
                            end = result.end,
                            fileSize = result.fileSize,
                            truncated = state.chunk.truncated || result.truncated,
                        ),
                        logError = null,
                    )
                }
                return
            }

            val previous = _state.value.lines.lastOrNull()?.effectiveLevel
            val appended = withContext(Dispatchers.Default) { Ansi.toViewLines(result.lines, previous) }
            if (!matches(pmId, stream, token)) return
            val follow = atBottom
            _state.update { state ->
                val merged = state.lines + appended
                state.copy(
                    lines = if (merged.size > ProcessLogsState.MAX_BUFFER) {
                        merged.takeLast(ProcessLogsState.MAX_BUFFER)
                    } else {
                        merged
                    },
                    chunk = state.chunk?.copy(
                        end = result.end,
                        fileSize = result.fileSize,
                        truncated = state.chunk.truncated || result.truncated,
                    ),
                    lastUpdated = System.currentTimeMillis(),
                    pendingNew = if (follow) 0 else state.pendingNew + appended.size,
                    scrollToken = if (follow) state.scrollToken + 1 else state.scrollToken,
                    logError = null,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (matches(pmId, stream, token)) {
                _state.update { it.copy(logError = describePm2Error(t, "Lost contact with the log; retrying")) }
            }
        } finally {
            polling = false
        }
    }

    /** Whether a response for [pmId] and [stream] still belongs on screen. */
    private fun matches(pmId: Int, stream: Pm2Stream, token: Int): Boolean {
        val current = _state.value
        return current.selectedPmId == pmId && current.stream == stream && generation == token
    }

    /**
     * Fetches [path] and decodes it with [strategy]. The proxy turns an empty
     * bot response into a JSON `null` with a 200, so any body that is not an
     * object carrying [requiredKey] counts as a failure.
     */
    private suspend fun <T> fetch(path: String, strategy: DeserializationStrategy<T>, requiredKey: String): T {
        val raw = api.sendRaw(Endpoint(path))
        val normalized = raw.normalizeKeys()
        if (normalized !is JsonObject || requiredKey !in normalized) {
            throw ApiError.Decoding(IllegalStateException("Unexpected response from $path"))
        }
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.decodeFromJsonElement(strategy, normalized)
            } catch (t: Throwable) {
                throw ApiError.Decoding(t)
            }
        }
    }

    private companion object {
        /** How often a followed log is polled. */
        const val POLL_MS = 2_000L

        /** How often the process list refreshes. */
        const val PROCESS_REFRESH_MS = 15_000L
    }
}
