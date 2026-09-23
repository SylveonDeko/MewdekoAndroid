package dev.mewdeko.mobile.feature.auditlog

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLEncoder
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

/** Audit log screen state. */
data class AuditlogState(
    val entries: List<AuditLogEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val actionFilter: AuditAction? = null,
    val sectionFilter: String? = null,
    val expandedId: Int? = null,
) {
    /** The number of pages at the fixed page size, never below one. */
    val totalPages: Int get() = max(1, ceil(total / AUDIT_LOG_PAGE_SIZE.toDouble()).toInt())

    /**
     * The distinct sections on the loaded page, plus the active section filter
     * so the selector can still show it when the page is empty.
     */
    val sections: List<String>
        get() = (entries.map { it.section } + listOfNotNull(sectionFilter))
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
}

/** Loads and pages through the guild's dashboard audit log with action and section filters. */
@HiltViewModel
class AuditlogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(AuditlogState())

    /** Observable screen state. */
    val state: StateFlow<AuditlogState> = _state.asStateFlow()

    init {
        load()
    }

    /** Fetches the current page with the current filters. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val current = _state.value
        val result = api.send(Endpoint(pathFor(current)), AuditLogPage.serializer())
        _state.update { it.copy(entries = result.items, total = result.total) }
    }

    /** Filters by action (or clears it with `null`), returning to the first page. */
    fun setActionFilter(action: AuditAction?) {
        _state.update { it.copy(actionFilter = action, page = 1, expandedId = null) }
        load()
    }

    /** Filters by dashboard section (or clears it with `null`), returning to the first page. */
    fun setSectionFilter(section: String?) {
        _state.update { it.copy(sectionFilter = section, page = 1, expandedId = null) }
        load()
    }

    /** Moves to [page] when it is within range, collapsing any expanded entry. */
    fun goToPage(page: Int) {
        val current = _state.value
        if (page < 1 || page > current.totalPages) return
        _state.update { it.copy(page = page, expandedId = null) }
        load()
    }

    /** Expands or collapses the change details of one entry. */
    fun toggleExpanded(id: Int) {
        _state.update { it.copy(expandedId = if (it.expandedId == id) null else id) }
    }

    private fun pathFor(state: AuditlogState): String {
        val params = buildList {
            state.actionFilter?.let { add("action=${it.value}") }
            state.sectionFilter?.takeIf { it.isNotBlank() }?.let {
                add("section=${URLEncoder.encode(it, "UTF-8")}")
            }
            add("page=${state.page}")
            add("pageSize=$AUDIT_LOG_PAGE_SIZE")
        }
        return "api/AuditLog/$guildId?${params.joinToString("&")}"
    }
}
