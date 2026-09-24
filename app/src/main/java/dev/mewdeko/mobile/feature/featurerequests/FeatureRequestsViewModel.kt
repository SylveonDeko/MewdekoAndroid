package dev.mewdeko.mobile.feature.featurerequests

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import javax.inject.Inject

/** Section ids for the feature request tabs. */
object FeatureRequestSections {
    /** The paged, filterable list of every request. */
    const val BROWSE = "browse"

    /** The submission form. */
    const val SUGGEST = "submit"

    /** The signed-in user's own submissions. */
    const val MINE = "mine"

    /** Bot owner report channel settings. */
    const val SETTINGS = "settings"
}

/** Browse sort keys accepted by `GET FeatureRequests?sort=`. */
object FeatureRequestSort {
    /** Most upvoted first. */
    const val VOTES = "votes"

    /** Most recently submitted first. */
    const val NEWEST = "newest"
}

/** Feature requests screen state. */
data class FeatureRequestsState(
    val section: String = FeatureRequestSections.BROWSE,
    val isOwner: Boolean = false,
    val entries: List<FeatureRequestEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = PAGE_SIZE,
    val pageLoading: Boolean = false,
    val pageError: String? = null,
    val statusFilter: String? = FeatureRequestStatus.OPEN.key,
    val categoryFilter: String? = null,
    val sort: String = FeatureRequestSort.VOTES,
    val search: String = "",
    val expandedId: Int? = null,
    val mine: List<FeatureRequestEntry> = emptyList(),
    val mineLoading: Boolean = false,
    val mineError: String? = null,
    val stats: FeatureRequestStats? = null,
    val settings: FeatureRequestSettings? = null,
    val settingsLoading: Boolean = false,
    val settingsError: String? = null,
    val channelInput: String = "",
    val savingSettings: Boolean = false,
    val form: FeatureRequestDraft = FeatureRequestDraft(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submitted: FeatureRequestEntry? = null,
    val statusTarget: FeatureRequestEntry? = null,
    val statusChoice: String = FeatureRequestStatus.OPEN.key,
    val statusNote: String = "",
    val savingStatus: Boolean = false,
    val statusError: String? = null,
    val deleteTarget: FeatureRequestEntry? = null,
) {
    /** How many pages the current filters span. */
    val totalPages: Int get() = maxOf(1, (total + pageSize - 1) / pageSize)

    /** Whether the channel field differs from the saved setting. */
    val channelDirty: Boolean
        get() = settings != null && channelInput.trim() != settings.channelInputText

    /** Whether the Suggest form can be sent right now. */
    val canSubmit: Boolean get() = !submitting && form.isValid

    companion object {
        /** The page size the dashboard uses. */
        const val PAGE_SIZE = 25
    }
}

/**
 * Drives the user scoped feature request board: browsing with filters and
 * paging, upvoting, submitting, the user's own submissions, and the bot owner
 * tools (stats, status changes, deletion, report channel).
 */
@HiltViewModel
class FeatureRequestsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(FeatureRequestsState())

    /** Observable screen state. */
    val state: StateFlow<FeatureRequestsState> = _state.asStateFlow()

    private var pageJob: Job? = null

    private val votesInFlight = mutableSetOf<Int>()

    init {
        load()
    }

    /** Resolves the owner flag, then loads the page, the user's submissions, and owner data. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val owner = checkOwner()
        _state.update {
            it.copy(
                isOwner = owner,
                section = if (!owner && it.section == FeatureRequestSections.SETTINGS) {
                    FeatureRequestSections.BROWSE
                } else {
                    it.section
                },
            )
        }
        pageJob?.cancel()
        coroutineScope {
            val mine = async { fetchMine() }
            val stats = async { fetchStats() }
            val settings = async { fetchSettings() }
            fetchPage()
            mine.await()
            stats.await()
            settings.await()
        }
    }

    /** Switches the visible tab. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Updates the search text without querying; [applyFilters] runs the search. */
    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    /** Clears the search text and reloads the first page. */
    fun clearSearch() {
        _state.update { it.copy(search = "") }
        applyFilters()
    }

    /** Filters by status, or any status when `null`. */
    fun setStatusFilter(key: String?) {
        _state.update { it.copy(statusFilter = key?.takeIf { value -> value.isNotEmpty() }) }
        applyFilters()
    }

    /** Filters by category, or any category when `null`. */
    fun setCategoryFilter(key: String?) {
        _state.update { it.copy(categoryFilter = key?.takeIf { value -> value.isNotEmpty() }) }
        applyFilters()
    }

    /** Switches between most voted and newest first. */
    fun setSort(key: String) {
        if (_state.value.sort == key) return
        _state.update { it.copy(sort = key) }
        applyFilters()
    }

    /** Returns to the first page and reloads with the current filters. */
    fun applyFilters() {
        _state.update { it.copy(page = 1, expandedId = null) }
        reloadPage()
    }

    /** Moves to another page, ignoring out of range requests. */
    fun changePage(next: Int) {
        val current = _state.value
        if (next < 1 || next > current.totalPages || current.pageLoading) return
        _state.update { it.copy(page = next, expandedId = null) }
        reloadPage()
    }

    /** Reloads the current page, used by the inline retry. */
    fun retryPage() = reloadPage()

    /** Reloads the user's submissions, used by the inline retry. */
    fun retryMine() {
        viewModelScope.launch { fetchMine() }
    }

    /** Reloads the owner settings, used by the inline retry. */
    fun retrySettings() {
        viewModelScope.launch { fetchSettings() }
    }

    /** Expands or collapses a request card. */
    fun toggleExpand(id: Int) = _state.update {
        it.copy(expandedId = if (it.expandedId == id) null else id)
    }

    /**
     * Flips the vote locally first so the button feels instant, then
     * reconciles with the count the bot reports, rolling back on failure.
     */
    fun toggleVote(entry: FeatureRequestEntry) {
        if (!votesInFlight.add(entry.id)) return
        val beforeVotes = entry.votes
        val beforeVoted = entry.voted
        val nextVoted = !beforeVoted
        patchEntry(entry.id) {
            it.copy(voted = nextVoted, votes = beforeVotes + if (nextVoted) 1 else -1)
        }
        viewModelScope.launch {
            try {
                val result = api.send(
                    Endpoint("api/FeatureRequests/${entry.id}/vote", HttpMethod.POST),
                    FeatureRequestVoteResult.serializer(),
                )
                patchEntry(entry.id) { it.copy(votes = result.votes, voted = result.voted) }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                patchEntry(entry.id) { it.copy(votes = beforeVotes, voted = beforeVoted) }
                postError("Failed to save your vote.")
            } finally {
                votesInFlight.remove(entry.id)
            }
        }
    }

    /** Sets the Suggest form category. */
    fun setFormCategory(key: String?) {
        if (key.isNullOrEmpty()) return
        _state.update { it.copy(form = it.form.copy(category = key)) }
    }

    /** Sets the Suggest form title, capped at the bot's limit. */
    fun setFormTitle(value: String) = _state.update {
        it.copy(form = it.form.copy(title = value.take(FeatureRequestDraft.TITLE_MAX)))
    }

    /** Sets the Suggest form body, capped at the bot's limit. */
    fun setFormBody(value: String) = _state.update {
        it.copy(form = it.form.copy(body = value.take(FeatureRequestDraft.BODY_MAX)))
    }

    /** Sets whether the current server is attached to the submission. */
    fun setAttachGuild(value: Boolean) = _state.update {
        it.copy(form = it.form.copy(attachGuild = value))
    }

    /** Hides the thank you banner so another request can be written. */
    fun dismissSubmitted() = _state.update { it.copy(submitted = null) }

    /** Sends the Suggest form to the bot. */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        val form = current.form
        val attachedGuild = guildId.toLongOrNull()?.takeIf { form.attachGuild && it > 0 }
        val body = buildJsonObject {
            put("guildId", attachedGuild?.let { JsonPrimitive(it) } ?: JsonNull)
            put("category", JsonPrimitive(form.category))
            put("title", JsonPrimitive(form.title.trim()))
            put("body", JsonPrimitive(form.body.trim()))
        }
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                val created = api.send(
                    Endpoint(
                        "api/FeatureRequests",
                        HttpMethod.POST,
                        MewdekoJson.encodeToString(JsonObject.serializer(), body),
                    ),
                    FeatureRequestEntry.serializer(),
                )
                _state.update {
                    it.copy(
                        submitting = false,
                        submitted = created,
                        form = FeatureRequestDraft(attachGuild = it.form.attachGuild),
                    )
                }
                coroutineScope {
                    launch { fetchMine() }
                    launch { fetchStats() }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update {
                    it.copy(submitting = false, submitError = t.serverMessage("Failed to submit your request."))
                }
            }
        }
    }

    /** Opens the status editor for [entry], prefilled with its current status and note. */
    fun openStatus(entry: FeatureRequestEntry) = _state.update {
        it.copy(
            statusTarget = entry,
            statusChoice = entry.status,
            statusNote = entry.ownerNote.orEmpty(),
            statusError = null,
        )
    }

    /** Picks the status to save. */
    fun setStatusChoice(key: String?) {
        if (key.isNullOrEmpty()) return
        _state.update { it.copy(statusChoice = key) }
    }

    /** Edits the note left for the submitter. */
    fun setStatusNote(value: String) = _state.update {
        it.copy(statusNote = value.take(FeatureRequestDraft.NOTE_MAX))
    }

    /** Closes the status editor without saving. */
    fun dismissStatus() = _state.update {
        if (it.savingStatus) it else it.copy(statusTarget = null, statusError = null)
    }

    /** Saves the chosen status and note on the targeted request. */
    fun saveStatus() {
        val current = _state.value
        val target = current.statusTarget ?: return
        if (current.savingStatus) return
        val body = buildJsonObject {
            put("status", JsonPrimitive(current.statusChoice))
            put("note", current.statusNote.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        _state.update { it.copy(savingStatus = true, statusError = null) }
        viewModelScope.launch {
            try {
                val updated = api.send(
                    Endpoint(
                        "api/FeatureRequests/${target.id}/status",
                        HttpMethod.POST,
                        MewdekoJson.encodeToString(JsonObject.serializer(), body),
                    ),
                    FeatureRequestEntry.serializer(),
                )
                patchEntry(target.id) {
                    it.copy(status = updated.status, ownerNote = updated.ownerNote, updatedAt = updated.updatedAt)
                }
                _state.update { it.copy(savingStatus = false, statusTarget = null) }
                fetchStats()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update {
                    it.copy(savingStatus = false, statusError = t.serverMessage("Failed to update the status."))
                }
            }
        }
    }

    /** Asks for confirmation before deleting [entry]. */
    fun requestDelete(entry: FeatureRequestEntry) = _state.update { it.copy(deleteTarget = entry) }

    /** Cancels a pending delete. */
    fun dismissDelete() = _state.update { it.copy(deleteTarget = null) }

    /** Deletes the request awaiting confirmation. */
    fun confirmDelete() {
        val target = _state.value.deleteTarget ?: return
        _state.update { it.copy(deleteTarget = null) }
        viewModelScope.launch {
            try {
                api.sendIgnoringBody(Endpoint("api/FeatureRequests/${target.id}", HttpMethod.DELETE))
                _state.update { state ->
                    state.copy(
                        mine = state.mine.filterNot { it.id == target.id },
                        expandedId = state.expandedId.takeIf { it != target.id },
                    )
                }
                reloadPage()
                fetchStats()
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                postError("Failed to delete that request.")
            }
        }
    }

    /** Edits the report channel id field. */
    fun setChannelInput(value: String) = _state.update {
        it.copy(channelInput = value.filter { char -> char.isDigit() })
    }

    /** Saves the report channel, where blank means the join/leave channel fallback. */
    fun saveChannel() {
        val current = _state.value
        if (current.savingSettings) return
        val trimmed = current.channelInput.trim()
        if (trimmed.isNotEmpty() && !ChannelIdPattern.matches(trimmed)) {
            _state.update { it.copy(settingsError = "That does not look like a channel ID.") }
            return
        }
        val channelId = if (trimmed.isEmpty()) 0L else trimmed.toLongOrNull()
        if (channelId == null) {
            _state.update { it.copy(settingsError = "That does not look like a channel ID.") }
            return
        }
        val body = buildJsonObject { put("channelId", JsonPrimitive(channelId)) }
        _state.update { it.copy(savingSettings = true, settingsError = null) }
        viewModelScope.launch {
            try {
                val saved = api.send(
                    Endpoint(
                        "api/FeatureRequests/settings",
                        HttpMethod.POST,
                        MewdekoJson.encodeToString(JsonObject.serializer(), body),
                    ),
                    FeatureRequestSettings.serializer(),
                )
                _state.update {
                    it.copy(savingSettings = false, settings = saved, channelInput = saved.channelInputText)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update {
                    it.copy(savingSettings = false, settingsError = t.serverMessage("Failed to save the settings."))
                }
                fetchSettings(clearError = false)
            }
        }
    }

    private fun reloadPage() {
        pageJob?.cancel()
        pageJob = viewModelScope.launch { fetchPage() }
    }

    private suspend fun fetchPage() {
        val current = _state.value
        _state.update { it.copy(pageLoading = true, pageError = null) }
        try {
            val result = api.send(Endpoint(pagePath(current)), FeatureRequestPage.serializer())
            _state.update { it.copy(entries = result.items, total = result.total, pageLoading = false) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update { it.copy(pageLoading = false, pageError = "Failed to load feature requests.") }
        }
    }

    private suspend fun fetchMine() {
        _state.update { it.copy(mineLoading = true, mineError = null) }
        try {
            val items = api.send(
                Endpoint("api/FeatureRequests/mine"),
                ListSerializer(FeatureRequestEntry.serializer()),
            )
            _state.update { it.copy(mine = items, mineLoading = false) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update { it.copy(mineLoading = false, mineError = "Failed to load your requests.") }
        }
    }

    private suspend fun fetchStats() {
        if (!_state.value.isOwner) return
        try {
            val stats = api.send(Endpoint("api/FeatureRequests/stats"), FeatureRequestStats.serializer())
            _state.update { it.copy(stats = stats) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
        }
    }

    private suspend fun fetchSettings(clearError: Boolean = true) {
        if (!_state.value.isOwner) return
        _state.update { it.copy(settingsLoading = true) }
        try {
            val settings = api.send(Endpoint("api/FeatureRequests/settings"), FeatureRequestSettings.serializer())
            _state.update {
                it.copy(
                    settings = settings,
                    channelInput = settings.channelInputText,
                    settingsLoading = false,
                    settingsError = if (clearError) null else it.settingsError,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update {
                it.copy(
                    settingsLoading = false,
                    settingsError = if (clearError) "Failed to load the settings." else it.settingsError,
                )
            }
        }
    }

    private suspend fun checkOwner(): Boolean {
        if (userId.isEmpty()) return false
        return try {
            val raw = api.sendRaw(Endpoint("api/Ownership/$userId"))
            (raw as? JsonPrimitive)?.booleanOrNull ?: false
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            false
        }
    }

    private fun patchEntry(id: Int, transform: (FeatureRequestEntry) -> FeatureRequestEntry) {
        _state.update { state ->
            state.copy(
                entries = state.entries.map { if (it.id == id) transform(it) else it },
                mine = state.mine.map { if (it.id == id) transform(it) else it },
                statusTarget = state.statusTarget?.let { if (it.id == id) transform(it) else it },
            )
        }
    }

    private fun pagePath(state: FeatureRequestsState): String {
        val params = buildList {
            state.statusFilter?.let { add("status=${it.encoded()}") }
            state.categoryFilter?.let { add("category=${it.encoded()}") }
            state.search.trim().takeIf { it.isNotEmpty() }?.let { add("search=${it.encoded()}") }
            add("sort=${state.sort.encoded()}")
            add("page=${state.page}")
            add("pageSize=${state.pageSize}")
        }
        return "api/FeatureRequests?" + params.joinToString("&")
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8")

    /** The bot's own error text for a rejected request, or [fallback]. */
    private fun Throwable.serverMessage(fallback: String): String {
        val http = this as? ApiError.Http ?: return fallback
        val text = http.body.trim().removeSurrounding("\"").trim()
        return if (text.isEmpty() || text.startsWith("{") || text.startsWith("<")) fallback else text.take(300)
    }

    private companion object {
        /** A Discord snowflake as typed into the channel field. */
        val ChannelIdPattern = Regex("^\\d{17,20}$")
    }
}
