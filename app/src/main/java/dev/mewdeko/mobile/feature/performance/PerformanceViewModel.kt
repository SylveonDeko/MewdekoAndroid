package dev.mewdeko.mobile.feature.performance

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** The four sections of the owner Performance page, in the dashboard's order. */
enum class PerfSection(val id: String, val title: String) {
    OVERVIEW("overview", "Overview"),
    METHODS("methods", "Methods"),
    EVENTS("events", "Events"),
    MODULES("modules", "Modules"),
    ;

    companion object {
        /** Looks a section up by its tab id, defaulting to [OVERVIEW]. */
        fun fromId(id: String): PerfSection = entries.firstOrNull { it.id == id } ?: OVERVIEW
    }
}

/** One instrumented method's call count and timing, from `api/SystemInfo`'s `topMethods`. */
@Serializable
data class SystemTopMethod(
    val name: String = "",
    val avgTime: Double = 0.0,
)

/**
 * The process and host snapshot behind the Overview tab, from `api/SystemInfo`.
 *
 * Owner checked server side: the call is sent with the signed-in user's id
 * and answers 403 for anyone else, so a failure here almost always means the
 * cached ownership answer went stale.
 */
@Serializable
data class SystemInfo(
    val cpuUsage: Double = 0.0,
    val memoryUsageMb: Double = 0.0,
    val totalMemoryMb: Double = 0.0,
    val uptime: String = "",
    @Serializable(with = InstantSerializer::class)
    val processStartTime: Instant = Instant.EPOCH,
    val threadCount: Int = 0,
    val topMethods: List<SystemTopMethod> = emptyList(),
)

/** Timing for one instrumented method, from `api/Performance/methods`. */
@Serializable
data class PerfMethod(
    val methodName: String = "unknown",
    val callCount: Long = 0L,
    val totalTime: Double = 0.0,
    val avgExecutionTime: Double = 0.0,
    @Serializable(with = InstantSerializer::class)
    val lastExecuted: Instant = Instant.EPOCH,
)

/** Throughput and error rate for one gateway event type, from `api/Performance/events`. */
@Serializable
data class PerfEvent(
    val eventType: String = "",
    val totalProcessed: Long = 0L,
    val totalErrors: Long = 0L,
    val totalExecutionTime: Long = 0L,
    val averageExecutionTime: Double = 0.0,
    val errorRate: Double = 0.0,
)

/** Throughput and error rate for one bot module, from `api/Performance/modules`. */
@Serializable
data class PerfModule(
    val moduleName: String = "",
    val eventsProcessed: Long = 0L,
    val errors: Long = 0L,
    val totalExecutionTime: Long = 0L,
    val averageExecutionTime: Double = 0.0,
    val errorRate: Double = 0.0,
)

/** The columns [PerfMethod] rows can be sorted by; the dashboard always sorts these descending. */
enum class MethodSortField(val label: String) {
    NAME("Method"),
    CALLS("Calls"),
    AVG_TIME("Avg Time"),
    TOTAL_TIME("Total Time"),
    LAST_EXECUTED("Last Executed"),
}

/** The columns shared by [PerfEvent] and [PerfModule] rows, sortable either direction. */
enum class MetricSortField(val label: String) {
    NAME("Name"),
    PROCESSED("Processed"),
    ERRORS("Errors"),
    ERROR_RATE("Error Rate"),
    AVG_TIME("Avg Time"),
    TOTAL_TIME("Total Time"),
}

/** Performance screen state, one slice per tab. */
data class PerformanceState(
    val section: PerfSection = PerfSection.OVERVIEW,

    val overview: SystemInfo? = null,
    val overviewLoading: Boolean = false,
    val overviewError: String? = null,

    val methods: List<PerfMethod> = emptyList(),
    val methodsLoading: Boolean = false,
    val methodsError: String? = null,
    val methodSort: MethodSortField? = null,
    val clearing: Boolean = false,

    val events: List<PerfEvent> = emptyList(),
    val eventsLoading: Boolean = false,
    val eventsError: String? = null,
    val eventSort: MetricSortField = MetricSortField.PROCESSED,
    val eventSortDescending: Boolean = true,

    val modules: List<PerfModule> = emptyList(),
    val modulesLoading: Boolean = false,
    val modulesError: String? = null,
    val moduleSort: MetricSortField = MetricSortField.PROCESSED,
    val moduleSortDescending: Boolean = true,
)

/**
 * Bot CPU, memory, method, event, and module telemetry, opened from the owner
 * panel. Fleet level: every call acts on whichever instance [api] is pinned
 * to, and `api/SystemInfo` also names the signed-in owner so the bot can
 * check them server side.
 *
 * Each tab loads independently and, once visible, polls on its own interval:
 * Overview every 5 seconds, Methods every 30, Events and Modules every 10.
 * [PerformanceScreen] drives that through [poll], starting a fresh loop
 * whenever the visible section changes.
 */
@HiltViewModel
class PerformanceViewModel @Inject constructor(
    api: ApiClient,
    session: SessionHolder,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(PerformanceState())

    /** Observable screen state. */
    val state: StateFlow<PerformanceState> = _state.asStateFlow()

    init {
        markLoaded()
    }

    /** Switches the visible tab; [poll] picks up the change and reloads it. */
    fun setSection(section: PerfSection) = _state.update { it.copy(section = section) }

    /**
     * Runs while [section] is on screen: an immediate load followed by that
     * tab's polling interval, for as long as the caller's scope lives.
     */
    suspend fun poll(section: PerfSection) {
        val intervalMs = when (section) {
            PerfSection.OVERVIEW -> 5_000L
            PerfSection.METHODS -> 30_000L
            PerfSection.EVENTS -> 10_000L
            PerfSection.MODULES -> 10_000L
        }
        while (true) {
            loadSection(section)
            delay(intervalMs.milliseconds)
        }
    }

    /** Reloads the currently visible tab immediately, for its Refresh button. */
    fun refreshCurrent() {
        viewModelScope.launch { loadSection(_state.value.section) }
    }

    private suspend fun loadSection(section: PerfSection) = when (section) {
        PerfSection.OVERVIEW -> loadOverview()
        PerfSection.METHODS -> loadMethods()
        PerfSection.EVENTS -> loadEvents()
        PerfSection.MODULES -> loadModules()
    }

    private suspend fun loadOverview() {
        if (_state.value.overviewLoading) return
        _state.update { it.copy(overviewLoading = true, overviewError = null) }
        try {
            val info = api.send(Endpoint("api/SystemInfo?userId=$userId"), SystemInfo.serializer())
            _state.update { it.copy(overview = info, overviewLoading = false) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update {
                it.copy(overviewLoading = false, overviewError = "Failed to load system information")
            }
        }
    }

    private suspend fun loadMethods() {
        if (_state.value.methodsLoading) return
        _state.update { it.copy(methodsLoading = true, methodsError = null) }
        try {
            val fetched = api.send(Endpoint("api/Performance/methods"), ListSerializer(PerfMethod.serializer()))
            _state.update { it.copy(methods = fetched, methodSort = null, methodsLoading = false) }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update {
                it.copy(methods = emptyList(), methodsLoading = false, methodsError = "Failed to load performance data")
            }
        }
    }

    private suspend fun loadEvents() {
        if (_state.value.eventsLoading) return
        _state.update { it.copy(eventsLoading = true, eventsError = null) }
        try {
            val fetched = api.send(Endpoint("api/Performance/events"), ListSerializer(PerfEvent.serializer()))
            _state.update {
                it.copy(events = fetched.sortedByEventField(it.eventSort, it.eventSortDescending), eventsLoading = false)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update {
                it.copy(events = emptyList(), eventsLoading = false, eventsError = "Failed to load event metrics")
            }
        }
    }

    private suspend fun loadModules() {
        if (_state.value.modulesLoading) return
        _state.update { it.copy(modulesLoading = true, modulesError = null) }
        try {
            val fetched = api.send(Endpoint("api/Performance/modules"), ListSerializer(PerfModule.serializer()))
            _state.update {
                it.copy(modules = fetched.sortedByModuleField(it.moduleSort, it.moduleSortDescending), modulesLoading = false)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _state.update {
                it.copy(modules = emptyList(), modulesLoading = false, modulesError = "Failed to load module metrics")
            }
        }
    }

    /** Picks the Methods column sorted by, always descending; the next poll drops it. */
    fun setMethodSort(field: MethodSortField) = _state.update {
        it.copy(methodSort = field, methods = it.methods.sortedByField(field))
    }

    /** Picks or toggles the Events column sorted by; a new field selects descending. */
    fun setEventSort(field: MetricSortField) = _state.update {
        val descending = if (it.eventSort == field) !it.eventSortDescending else true
        it.copy(
            eventSort = field,
            eventSortDescending = descending,
            events = it.events.sortedByEventField(field, descending),
        )
    }

    /** Picks or toggles the Modules column sorted by; a new field selects descending. */
    fun setModuleSort(field: MetricSortField) = _state.update {
        val descending = if (it.moduleSort == field) !it.moduleSortDescending else true
        it.copy(
            moduleSort = field,
            moduleSortDescending = descending,
            modules = it.modules.sortedByModuleField(field, descending),
        )
    }

    /**
     * Discards the method timing samples collected on the bot. Event and
     * module counters reset on their own, hourly, and are not touched by
     * this call.
     */
    fun clearMethodData() {
        if (_state.value.clearing) return
        viewModelScope.launch {
            _state.update { it.copy(clearing = true) }
            try {
                api.sendIgnoringBody(Endpoint("api/Performance/clear", HttpMethod.POST))
                loadMethods()
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                _state.update { it.copy(methodsError = "Failed to clear performance data") }
            } finally {
                _state.update { it.copy(clearing = false) }
            }
        }
    }
}

/** Sorts by [field] descending when set, or leaves the server's own order otherwise. */
private fun List<PerfMethod>.sortedByField(field: MethodSortField?): List<PerfMethod> = when (field) {
    null -> this
    MethodSortField.NAME -> sortedByDescending { it.methodName.lowercase() }
    MethodSortField.CALLS -> sortedByDescending { it.callCount }
    MethodSortField.AVG_TIME -> sortedByDescending { it.avgExecutionTime }
    MethodSortField.TOTAL_TIME -> sortedByDescending { it.totalTime }
    MethodSortField.LAST_EXECUTED -> sortedByDescending { it.lastExecuted }
}

/** Sorts by [field] in [descending] order. */
private fun List<PerfEvent>.sortedByEventField(field: MetricSortField, descending: Boolean): List<PerfEvent> {
    val sorted = when (field) {
        MetricSortField.NAME -> sortedBy { it.eventType.lowercase() }
        MetricSortField.PROCESSED -> sortedBy { it.totalProcessed }
        MetricSortField.ERRORS -> sortedBy { it.totalErrors }
        MetricSortField.ERROR_RATE -> sortedBy { it.errorRate }
        MetricSortField.AVG_TIME -> sortedBy { it.averageExecutionTime }
        MetricSortField.TOTAL_TIME -> sortedBy { it.totalExecutionTime }
    }
    return if (descending) sorted.reversed() else sorted
}

/** Sorts by [field] in [descending] order. */
private fun List<PerfModule>.sortedByModuleField(field: MetricSortField, descending: Boolean): List<PerfModule> {
    val sorted = when (field) {
        MetricSortField.NAME -> sortedBy { it.moduleName.lowercase() }
        MetricSortField.PROCESSED -> sortedBy { it.eventsProcessed }
        MetricSortField.ERRORS -> sortedBy { it.errors }
        MetricSortField.ERROR_RATE -> sortedBy { it.errorRate }
        MetricSortField.AVG_TIME -> sortedBy { it.averageExecutionTime }
        MetricSortField.TOTAL_TIME -> sortedBy { it.totalExecutionTime }
    }
    return if (descending) sorted.reversed() else sorted
}
