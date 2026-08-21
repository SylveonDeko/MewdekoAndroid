package dev.mewdeko.mobile.feature.performance

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** Timing for one instrumented method. */
@Serializable
data class PerfMethod(
    val methodName: String = "unknown",
    val callCount: Long = 0L,
    val totalTime: Double = 0.0,
    val avgExecutionTime: Double = 0.0,
)

/** Throughput and error rate for one gateway event type. */
@Serializable
data class PerfEvent(
    val eventType: String = "?",
    val totalProcessed: Long = 0L,
    val totalErrors: Long = 0L,
    val averageExecutionTime: Double = 0.0,
    val errorRate: Double = 0.0,
)

/** Throughput and error rate for one bot module. */
@Serializable
data class PerfModule(
    val moduleName: String = "?",
    val eventsProcessed: Long = 0L,
    val errors: Long = 0L,
    val averageExecutionTime: Double = 0.0,
    val errorRate: Double = 0.0,
)

/** Performance screen state. */
data class PerformanceState(
    val methods: List<PerfMethod> = emptyList(),
    val events: List<PerfEvent> = emptyList(),
    val modules: List<PerfModule> = emptyList(),
    val section: String = "methods",
) {
    /** Total calls recorded across every instrumented method. */
    val totalCalls: Long get() = methods.sumOf { it.callCount }

    /** Total events processed across every event type. */
    val totalEvents: Long get() = events.sumOf { it.totalProcessed }

    /** Total errors across every event type. */
    val totalErrors: Long get() = events.sumOf { it.totalErrors }
}

/** Bot CPU, throughput, and error telemetry. Owner-only in the catalog. */
@HiltViewModel
class PerformanceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(PerformanceState())

    /** Observable screen state. */
    val state: StateFlow<PerformanceState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads every telemetry series. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val methods = async { list("methods", PerfMethod.serializer()) }
            val events = async { list("events", PerfEvent.serializer()) }
            val modules = async { list("modules", PerfModule.serializer()) }

            _state.update {
                it.copy(
                    methods = methods.await().sortedByDescending { entry -> entry.totalTime },
                    events = events.await().sortedByDescending { entry -> entry.totalProcessed },
                    modules = modules.await().sortedByDescending { entry -> entry.eventsProcessed },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Discards every collected sample on the bot. */
    fun clearData() = launchAction("Failed to clear performance data.") {
        api.sendIgnoringBody(Endpoint("api/Performance/$guildId/clear", HttpMethod.POST))
        postSuccess("Performance data cleared.")
        load(refreshing = true)
    }

    private suspend fun <T> list(tail: String, strategy: KSerializer<T>): List<T> = runCatching {
        api.send(Endpoint("api/Performance/$guildId/$tail"), ListSerializer(strategy))
    }.getOrDefault(emptyList())
}
