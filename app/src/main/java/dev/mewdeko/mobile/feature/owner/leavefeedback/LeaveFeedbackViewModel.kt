package dev.mewdeko.mobile.feature.owner.leavefeedback

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder
import javax.inject.Inject

/**
 * Loads the feedback server owners left when they removed the bot, and the
 * bot wide settings for the prompt, mirroring the dashboard's
 * `/owner/leave-feedback`.
 *
 * Stats are read on first load, pull to refresh, the Refresh button, filter
 * changes, and deletes, but not on page changes, since the bot computes them
 * over every record on each call.
 */
@HiltViewModel
class LeaveFeedbackViewModel @Inject constructor(
    api: ApiClient,
    session: SessionHolder,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(LeaveFeedbackState())

    /** Observable screen state. */
    val state: StateFlow<LeaveFeedbackState> = _state.asStateFlow()

    private var listJob: Job? = null

    private var settingsJob: Job? = null

    init {
        markLoaded()
        load()
    }

    /**
     * Reloads the list, stats, and settings. The screen stays up throughout,
     * with the list drawn as skeletons, so each section reports its own
     * failure instead of replacing the page.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val list = reloadList(withStats = true)
        val settings = reloadSettings(keepError = false)
        list.join()
        settings.join()
    }

    /** Switches between the Responses and Settings tabs. */
    fun setTab(id: String) = _state.update { it.copy(tab = id) }

    /** Refetches the list and stats at the current page and filters. */
    fun refreshList() {
        reloadList(withStats = true)
    }

    /** Sets or clears the reason filter. */
    fun setReasonFilter(key: String?) {
        val next = key?.takeIf { it.isNotEmpty() }
        if (next == _state.value.reasonFilter) return
        applyFilter { it.copy(reasonFilter = next) }
    }

    /** Filters to the tapped reason, or clears the filter when it is already selected. */
    fun toggleReason(key: String) {
        applyFilter { it.copy(reasonFilter = if (it.reasonFilter == key) null else key) }
    }

    /** Sets or clears the status filter. */
    fun setStatusFilter(key: String?) {
        val next = key?.takeIf { it.isNotEmpty() }
        if (next == _state.value.statusFilter) return
        applyFilter { it.copy(statusFilter = next) }
    }

    /** Edits the search text without applying it. */
    fun setSearchInput(value: String) = _state.update { it.copy(searchInput = value) }

    /** Applies the typed search, on submit or when the field loses focus. */
    fun applySearch() {
        val trimmed = _state.value.searchInput.trim()
        if (trimmed == _state.value.appliedSearch) return
        applyFilter { it.copy(appliedSearch = trimmed) }
    }

    /** Clears the search text and applies the empty search. */
    fun clearSearch() {
        _state.update { it.copy(searchInput = "") }
        applySearch()
    }

    /** Moves to [page], collapsing any expanded comment. */
    fun changePage(page: Int) {
        val current = _state.value
        val target = page.coerceIn(1, current.totalPages)
        if (target == current.page) return
        _state.update { it.copy(page = target, expandedId = null) }
        reloadList(withStats = false)
    }

    /** Expands [id]'s comment, collapsing any other, or collapses it when already open. */
    fun toggleComment(id: Int) = _state.update {
        it.copy(expandedId = if (it.expandedId == id) null else id)
    }

    /** Asks to delete [entry]; the screen confirms first. */
    fun requestDelete(entry: LeaveFeedbackEntry) = _state.update { it.copy(deleteTarget = entry) }

    /** Cancels a pending delete. */
    fun dismissDelete() = _state.update { it.copy(deleteTarget = null) }

    /**
     * Deletes the record awaiting confirmation, then refetches the list and
     * stats at the same page and filters. A failure leaves the list in place
     * and surfaces a transient error.
     */
    fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        _state.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            try {
                api.sendRaw(Endpoint("api/LeaveFeedback/${target.id}", HttpMethod.DELETE))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                postError(t.serverMessage("Failed to delete that record"))
                return@launch
            }
            _state.update { it.copy(expandedId = it.expandedId.takeUnless { id -> id == target.id }) }
            reloadList(withStats = true).join()
            val after = _state.value
            if (!after.listFailed && after.entries.isEmpty() && after.page > 1) {
                _state.update { it.copy(page = maxOf(1, minOf(it.page - 1, it.totalPages))) }
                reloadList(withStats = false)
            }
        }
    }

    /** Retries a failed settings load. */
    fun retrySettings() {
        reloadSettings(keepError = false)
    }

    /** Edits the report channel id field, keeping digits only. */
    fun setChannelInput(value: String) = _state.update {
        it.copy(channelInput = value.filter { char -> char.isDigit() }, settingsError = null)
    }

    /** Saves the report channel typed into the field, keeping the prompt's on or off state. */
    fun saveChannel() {
        val settings = _state.value.settings ?: return
        saveSettings(enabled = settings.enabled)
    }

    /**
     * Turns the prompt on or off. The channel field goes along with it, so an
     * invalid unsaved channel blocks the toggle, as on the dashboard.
     */
    fun setEnabled(enabled: Boolean) = saveSettings(enabled = enabled)

    /**
     * Posts both settings fields; the bot defaults a missing channel to zero,
     * which would silently switch to the fallback. On failure the error and
     * the typed channel both stay on screen.
     */
    private fun saveSettings(enabled: Boolean) {
        val current = _state.value
        if (current.savingSettings || current.settings == null) return
        val trimmed = current.channelInput.trim()
        if (trimmed.isNotEmpty() && !ChannelIdPattern.matches(trimmed)) {
            _state.update { it.copy(settingsError = "That does not look like a channel ID") }
            return
        }
        val body = buildJsonObject {
            put("enabled", enabled)
            put("channelId", JsonPrimitive(trimmed.ifEmpty { "0" }))
        }
        _state.update { it.copy(savingSettings = true, settingsError = null) }
        viewModelScope.launch {
            try {
                val saved = api.send(
                    Endpoint(
                        "api/LeaveFeedback/settings",
                        HttpMethod.POST,
                        MewdekoJson.encodeToString(JsonObject.serializer(), body),
                    ),
                    LeaveFeedbackSettings.serializer(),
                )
                _state.update {
                    it.copy(
                        savingSettings = false,
                        settings = saved,
                        settingsLoadFailed = false,
                        channelInput = saved.channelInputText,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update {
                    it.copy(savingSettings = false, settingsError = t.serverMessage("Failed to save the settings"))
                }
                reloadSettings(keepError = true)
            }
        }
    }

    /** Resets to page one, collapses any comment, and refetches the list and stats. */
    private fun applyFilter(transform: (LeaveFeedbackState) -> LeaveFeedbackState) {
        _state.update { transform(it).copy(page = 1, expandedId = null) }
        reloadList(withStats = true)
    }

    /** Replaces any in flight list fetch with a new one. */
    private fun reloadList(withStats: Boolean): Job {
        listJob?.cancel()
        val job = viewModelScope.launch { fetchList(withStats) }
        listJob = job
        return job
    }

    /**
     * Fetches the current page, and the stats alongside when [withStats].
     * The rows are replaced by skeletons while it runs. A stats failure
     * keeps the previous counts; a list failure shows the inline error.
     */
    private suspend fun fetchList(withStats: Boolean) {
        val query = _state.value
        _state.update { it.copy(listLoading = true, listFailed = false) }
        try {
            coroutineScope {
                val stats = if (withStats) async { fetchStats() } else null
                val page = api.send(Endpoint(listPath(query)), LeaveFeedbackPage.serializer())
                val freshStats = stats?.await()
                _state.update {
                    it.copy(
                        entries = page.items,
                        total = page.total,
                        page = page.page.coerceAtLeast(1),
                        pageSize = page.pageSize.takeIf { size -> size > 0 } ?: it.pageSize,
                        stats = freshStats ?: it.stats,
                        listLoading = false,
                    )
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update { it.copy(entries = emptyList(), listLoading = false, listFailed = true) }
        }
    }

    /** The stats, or `null` when they failed, so the tiles keep their last values. */
    private suspend fun fetchStats(): LeaveFeedbackStats? = try {
        api.send(Endpoint("api/LeaveFeedback/stats"), LeaveFeedbackStats.serializer())
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        null
    }

    /**
     * Replaces any in flight settings fetch. A typed but unsaved channel is
     * kept rather than reseeded, and [keepError] keeps a save error visible.
     */
    private fun reloadSettings(keepError: Boolean): Job {
        settingsJob?.cancel()
        val job = viewModelScope.launch {
            _state.update { it.copy(settingsLoading = true) }
            try {
                val settings = api.send(Endpoint("api/LeaveFeedback/settings"), LeaveFeedbackSettings.serializer())
                _state.update {
                    val untouched = it.settings == null || !it.channelDirty
                    it.copy(
                        settings = settings,
                        settingsLoading = false,
                        settingsLoadFailed = false,
                        channelInput = if (untouched) settings.channelInputText else it.channelInput,
                        settingsError = if (keepError) it.settingsError else null,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update {
                    it.copy(settingsLoading = false, settingsLoadFailed = it.settings == null)
                }
            }
        }
        settingsJob = job
        return job
    }

    /** The list path for [state]'s page and filters, omitting empty ones. */
    private fun listPath(state: LeaveFeedbackState): String {
        val params = buildList {
            state.reasonFilter?.let { add("reason=${it.encoded()}") }
            state.statusFilter?.let { add("status=${it.encoded()}") }
            state.appliedSearch.takeIf { it.isNotEmpty() }?.let { add("search=${it.encoded()}") }
            add("page=${state.page}")
            add("pageSize=${state.pageSize}")
        }
        return "api/LeaveFeedback?" + params.joinToString("&")
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    /**
     * The server's own error text for a rejected request, or [fallback].
     *
     * The dashboard proxy rewraps error bodies as `{ "error": string }` or
     * `{ "error": { "message": string } }`; a plain text body is used as is.
     */
    private fun Throwable.serverMessage(fallback: String): String {
        val http = this as? ApiError.Http ?: return fallback
        val text = http.body.trim()
        if (text.isEmpty() || text.startsWith("<")) return fallback
        val parsed = runCatching { MewdekoJson.parseToJsonElement(text) }.getOrNull()
        val message = when (parsed) {
            is JsonObject -> when (val error = parsed["error"] ?: parsed["message"]) {
                is JsonPrimitive -> error.contentOrNull
                is JsonObject -> runCatching { error["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                else -> null
            }

            is JsonPrimitive -> parsed.contentOrNull
            null -> text
            else -> null
        }
        return message?.trim()?.takeIf { it.isNotEmpty() }?.take(300) ?: fallback
    }

    private companion object {
        /** A Discord snowflake as typed into the channel field. */
        val ChannelIdPattern = Regex("^\\d{17,20}$")
    }
}
