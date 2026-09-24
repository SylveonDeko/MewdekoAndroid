package dev.mewdeko.mobile.feature.owner.docker

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.http.HttpMethod as KtorMethod

/** How often the daemon summary is read again. */
private const val OVERVIEW_REFRESH_MS = 10_000L

/** How often a followed log asks for new lines. */
private const val LOG_POLL_MS = 2_000L

/** How often a running compose job is read again. */
private const val JOB_POLL_MS = 1_500L

/** How long the inline notice stays up. */
private const val NOTICE_MS = 6_000L

/** At most this many stats samples in flight, since each costs the daemon a second or two. */
private const val STATS_CONCURRENCY = 4

/** The ordinary request budget. */
private const val DEFAULT_TIMEOUT_MS = 30_000L

/** Stop and restart give the container 15 seconds and can take up to a minute. */
private const val ACTION_TIMEOUT_MS = 90_000L

/** The first compose job on a host may wait for the `docker:cli` image to pull. */
private const val JOB_START_TIMEOUT_MS = 330_000L

/**
 * Loads and acts on the containers and compose projects on the selected
 * instance's host, plus the self report of every registered instance.
 *
 * Requests go out directly on the shared [HttpClient] rather than through
 * [ApiClient.send], with the same bearer token, the same single refresh on a
 * 401, and the same `X-Mobile-Instance` pin. Doing it here lets the fleet list
 * aim one request at each registered bot, lets container actions and compose
 * jobs wait longer than the client's 30 second budget, and keeps the proxy's
 * error text so notices read like the dashboard's.
 */
@HiltViewModel
class DockerViewModel @Inject constructor(
    api: ApiClient,
    private val session: SessionHolder,
    private val http: HttpClient,
    private val auth: AuthManager,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(
        DockerState(
            selectedBotId = session.instance.value?.botId,
            instanceName = botName,
        ),
    )

    /** Observable screen state. */
    val state: StateFlow<DockerState> = _state.asStateFlow()

    private var overviewEverLoaded = false
    private val statsRunning = AtomicBoolean(false)
    private val logPollInFlight = AtomicBoolean(false)
    private var nextLineId = 0L
    private var noticeSeq = 0L
    private var noticeJob: Job? = null
    private var fleetJob: Job? = null
    private var tailJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            session.instance
                .map { it?.botId to it?.botName }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .drop(1)
                .collect { (botId, name) -> switchInstance(botId, name) }
        }
    }

    /**
     * Reads the daemon summary, the job list, and the fleet. The first call
     * holds the screen on a spinner until the summary answers; the job list
     * and the fleet fill in on their own.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        viewModelScope.launch { loadJobs() }
        launchFleet(refresh = false)
        loadOverview(initial = !overviewEverLoaded)
    }

    /** Reads the daemon summary again, for the Daemon section's Refresh button. */
    fun refreshOverview() {
        viewModelScope.launch { loadOverview() }
    }

    /**
     * Runs every poll while the screen is visible: the overview every ten
     * seconds, a followed log every two, and a running job every one and a
     * half. Cancelling the caller stops them all.
     */
    suspend fun runPolling() = coroutineScope {
        launch {
            while (isActive) {
                delay(OVERVIEW_REFRESH_MS.milliseconds)
                loadOverview()
            }
        }
        launch {
            while (isActive) {
                delay(LOG_POLL_MS.milliseconds)
                pollLogs()
            }
        }
        launch {
            while (isActive) {
                delay(JOB_POLL_MS.milliseconds)
                pollJob()
            }
        }
    }

    private suspend fun loadOverview(initial: Boolean = false) {
        try {
            val result = request("api/Docker/overview", DockerOverview.serializer())
            overviewEverLoaded = true
            _state.update { current ->
                var expanded = current.expanded
                if (initial) {
                    val own = result.containers.firstOrNull { it.isSelf }
                    val project = own?.composeProject
                    if (project != null) {
                        expanded = expanded + project
                    } else if (result.projects.isEmpty()) {
                        expanded = expanded + DOCKER_STANDALONE
                    }
                }
                val keep = current.selectedId != null && result.containers.any { it.id == current.selectedId }
                if (keep || current.selectedId == null) {
                    current.copy(overview = result, overviewError = null, expanded = expanded)
                } else {
                    current.copy(
                        overview = result,
                        overviewError = null,
                        expanded = expanded,
                        selectedId = null,
                        lines = emptyList(),
                        cursor = null,
                        logError = null,
                    )
                }
            }
            viewModelScope.launch { refreshStats() }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.update { it.copy(overviewError = describe(t, "Failed to reach the bot for the Docker overview")) }
        }
    }

    /**
     * Samples only the running containers in expanded groups, a few at a
     * time. A refresh already under way wins, and failed samples keep the
     * previous one.
     */
    private suspend fun refreshStats() {
        if (!statsRunning.compareAndSet(false, true)) return
        try {
            val current = _state.value
            val overview = current.overview ?: return
            val targets = overview.groups()
                .filter { it.key in current.expanded }
                .flatMap { it.containers }
                .filter { it.isRunning }
            if (targets.isEmpty()) return
            val queue = ConcurrentLinkedQueue(targets)
            coroutineScope {
                repeat(minOf(STATS_CONCURRENCY, targets.size)) {
                    launch {
                        while (true) {
                            val container = queue.poll() ?: break
                            try {
                                val sample = request(
                                    "api/Docker/containers/${container.id.encoded()}/stats",
                                    DockerContainerStats.serializer(),
                                )
                                _state.update { it.copy(stats = it.stats + (container.id to sample)) }
                            } catch (c: CancellationException) {
                                throw c
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        } finally {
            statsRunning.set(false)
        }
    }

    /** Expands or collapses a group, sampling its containers when it opens. */
    fun toggleGroup(key: String) {
        var opened = false
        _state.update {
            opened = key !in it.expanded
            it.copy(expanded = if (opened) it.expanded + key else it.expanded - key)
        }
        if (opened) viewModelScope.launch { refreshStats() }
    }

    /**
     * Starts, stops, or restarts [target]. The screen confirms stop and
     * restart first. The overview is read again whatever the outcome.
     */
    fun runAction(target: DockerContainerInfo, action: DockerContainerAction) {
        if (_state.value.busyContainers.containsKey(target.id)) return
        _state.update { it.copy(busyContainers = it.busyContainers + (target.id to action)) }
        viewModelScope.launch {
            try {
                val result = request(
                    "api/Docker/containers/${target.id.encoded()}/${action.path}",
                    DockerActionResult.serializer(),
                    method = KtorMethod.Post,
                    timeoutMillis = ACTION_TIMEOUT_MS,
                )
                notice("${target.name}: ${result.message ?: "${action.path} accepted"}", result.success)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                notice("${target.name}: ${describe(t, "${action.path} failed")}", false)
            } finally {
                _state.update { it.copy(busyContainers = it.busyContainers - target.id) }
            }
            loadOverview()
        }
    }

    /** Asks every registered instance again, forcing a fresh registry check. */
    fun checkForUpdates() = launchFleet(refresh = true)

    private fun launchFleet(refresh: Boolean) {
        fleetJob?.cancel()
        fleetJob = viewModelScope.launch { loadFleet(refresh) }
    }

    /**
     * Reads the instance list, then asks each instance what it runs, routed
     * to that instance by its bot id. Rows update one by one and keep their
     * previous answer while they are asked again.
     */
    private suspend fun loadFleet(refresh: Boolean) {
        _state.update { it.copy(fleetLoading = it.fleet.isEmpty(), fleetChecking = true, fleetError = null) }
        try {
            val instances = request("api/InstanceManagement", ListSerializer(DockerBotInstance.serializer()))
            _state.update { current ->
                val previous = current.fleet.associateBy { it.instance.id }
                current.copy(
                    fleet = instances.map { DockerFleetEntry(it, previous[it.id]?.info, null, loading = true) },
                    fleetLoading = false,
                )
            }
            coroutineScope {
                instances.forEach { instance ->
                    launch {
                        val outcome = runCatching {
                            val botId = instance.botId.takeIf { it.isNotBlank() && it != "0" }
                                ?: throw DockerHttpError(0, "No bot id is registered for this instance")
                            request(
                                "api/Docker/self${if (refresh) "?refresh=true" else ""}",
                                DockerSelfInfo.serializer(),
                                instance = botId,
                            )
                        }
                        outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        _state.update { current ->
                            current.copy(
                                fleet = current.fleet.map { entry ->
                                    if (entry.instance.id != instance.id) {
                                        entry
                                    } else {
                                        outcome.fold(
                                            onSuccess = { entry.copy(info = it, error = null, loading = false) },
                                            onFailure = { entry.copy(error = describe(it, "Did not answer"), loading = false) },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.update { it.copy(fleetError = describe(t, "Failed to load the instance list")) }
        } finally {
            _state.update { it.copy(fleetLoading = false, fleetChecking = false) }
        }
    }

    /**
     * Opens a fleet bot's container in the log viewer when it lives on the
     * selected instance's host; otherwise says where it runs.
     */
    fun showFleetLogs(entry: DockerFleetEntry) {
        val id = entry.info?.container?.id
        val container = id?.let { cid -> _state.value.overview?.containers?.firstOrNull { it.id == cid } }
        if (container == null) {
            notice("${entry.instance.botName} runs on another host; select that instance to read its logs", false)
            return
        }
        selectContainer(container)
    }

    /** Pulls the newest image and recreates one instance's own container. The screen confirms first. */
    fun updateBot(entry: DockerFleetEntry) {
        val current = _state.value
        if (entry.instance.id in current.updatingInstances || current.jobRunning) return
        val name = entry.instance.botName
        _state.update { it.copy(updatingInstances = it.updatingInstances + entry.instance.id) }
        viewModelScope.launch {
            try {
                val job = request(
                    "api/Docker/self/update",
                    DockerJob.serializer(),
                    method = KtorMethod.Post,
                    instance = entry.instance.botId.takeIf { it.isNotBlank() },
                    timeoutMillis = JOB_START_TIMEOUT_MS,
                )
                adoptJob(job)
                notice("$name: update started", true)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                notice("$name: ${describe(t, "Could not start the update")}", false)
            } finally {
                _state.update { it.copy(updatingInstances = it.updatingInstances - entry.instance.id) }
            }
        }
    }

    /** Updates the selected instance's whole compose project. The screen confirms first. */
    fun updateAll() {
        val current = _state.value
        if (current.updatingAll || current.jobRunning) return
        _state.update { it.copy(updatingAll = true) }
        viewModelScope.launch {
            try {
                val job = request(
                    "api/Docker/self/update-all",
                    DockerJob.serializer(),
                    method = KtorMethod.Post,
                    timeoutMillis = JOB_START_TIMEOUT_MS,
                )
                adoptJob(job)
                notice("Fleet update started", true)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                notice(describe(t, "Could not start the fleet update"), false)
            } finally {
                _state.update { it.copy(updatingAll = false) }
            }
        }
    }

    /** Starts a compose operation on a whole project. The screen confirms first. */
    fun startCompose(project: DockerComposeProject, operation: DockerComposeOperation) {
        if (_state.value.startingJob != null) return
        _state.update { it.copy(startingJob = project.name) }
        viewModelScope.launch {
            try {
                val job = request(
                    "api/Docker/projects/${project.name.encoded()}/${operation.path}",
                    DockerJob.serializer(),
                    method = KtorMethod.Post,
                    timeoutMillis = JOB_START_TIMEOUT_MS,
                )
                adoptJob(job)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                notice("${project.name}: ${describe(t, "Could not start ${operation.path}")}", false)
            } finally {
                _state.update { it.copy(startingJob = null) }
            }
        }
    }

    /** Makes [job] the active one and puts it at the head of the list. */
    private fun adoptJob(job: DockerJob) {
        _state.update { current ->
            current.copy(activeJob = job, jobs = listOf(job) + current.jobs.filter { it.id != job.id })
        }
    }

    /**
     * Shows [job] in the terminal panel and reads it in full, since list
     * entries carry no output.
     */
    fun selectJob(job: DockerJob) {
        _state.update { it.copy(activeJob = job) }
        viewModelScope.launch {
            try {
                val latest = request("api/Docker/jobs/${job.id.encoded()}", DockerJob.serializer())
                replaceJob(latest)
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
            }
        }
    }

    private fun replaceJob(latest: DockerJob) {
        _state.update { current ->
            current.copy(
                activeJob = if (current.activeJob?.id == latest.id) latest else current.activeJob,
                jobs = current.jobs.map { if (it.id == latest.id) latest else it },
            )
        }
    }

    /**
     * Reads the running job again. Failures are swallowed: an update that
     * recreates the selected bot's own container drops the connection for a
     * while, and the helper container carries on regardless. When the job
     * finishes the overview and the fleet are read again.
     */
    private suspend fun pollJob() {
        val job = _state.value.activeJob ?: return
        if (!job.isRunning) return
        try {
            val latest = request("api/Docker/jobs/${job.id.encoded()}", DockerJob.serializer())
            replaceJob(latest)
            if (!latest.isRunning) {
                loadOverview()
                launchFleet(refresh = false)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
        }
    }

    private suspend fun loadJobs() {
        try {
            val jobs = request("api/Docker/jobs", ListSerializer(DockerJob.serializer()))
            _state.update { current ->
                val running = jobs.firstOrNull { it.isRunning }
                current.copy(jobs = jobs, activeJob = current.activeJob ?: running)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
        }
    }

    /** Opens [container] in the log viewer with a fresh tail. */
    fun selectContainer(container: DockerContainerInfo) {
        if (container.id == _state.value.selectedId) return
        _state.update {
            it.copy(selectedId = container.id, lines = emptyList(), cursor = null, logError = null)
        }
        loadTail()
    }

    /** Changes how many lines a tail reads and reads it again. */
    fun setLineCount(count: Int) {
        _state.update { it.copy(lineCount = count) }
        loadTail()
    }

    /** Reads the selected container's tail again, replacing the buffer. */
    fun loadTail() {
        tailJob?.cancel()
        tailJob = viewModelScope.launch {
            val current = _state.value
            val id = current.selectedId ?: return@launch
            _state.update { it.copy(logLoading = true, logError = null) }
            try {
                val chunk = request(
                    "api/Docker/containers/${id.encoded()}/logs?tail=${current.lineCount}",
                    DockerLogChunk.serializer(),
                )
                if (_state.value.selectedId != id) return@launch
                val lines = chunk.lines.toViewLines()
                _state.update {
                    it.copy(
                        lines = lines,
                        cursor = chunk.cursor,
                        lastUpdated = Instant.now(),
                        tailVersion = it.tailVersion + 1,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (_state.value.selectedId == id) {
                    _state.update {
                        it.copy(logError = describe(t, "Failed to read the log"), lines = emptyList(), cursor = null)
                    }
                }
            } finally {
                _state.update { it.copy(logLoading = false) }
            }
        }
    }

    /**
     * Fetches the lines after the cursor (or a tail when there is none) and
     * appends them, keeping the newest [DOCKER_MAX_BUFFER]. Answers for a
     * container that is no longer selected are dropped.
     */
    private suspend fun pollLogs() {
        val current = _state.value
        val id = current.selectedId ?: return
        if (!current.follow || current.logLoading) return
        if (!logPollInFlight.compareAndSet(false, true)) return
        try {
            val cursor = current.cursor
            val path = if (cursor != null) {
                "api/Docker/containers/${id.encoded()}/logs?since=${cursor.encoded()}"
            } else {
                "api/Docker/containers/${id.encoded()}/logs?tail=${current.lineCount}"
            }
            val chunk = request(path, DockerLogChunk.serializer())
            if (_state.value.selectedId != id) return
            val appended = chunk.lines.toViewLines()
            _state.update { state ->
                if (state.selectedId != id) return@update state
                val merged = if (appended.isEmpty()) state.lines else (state.lines + appended).takeLast(DOCKER_MAX_BUFFER)
                state.copy(
                    lines = merged,
                    cursor = chunk.cursor ?: state.cursor,
                    lastUpdated = if (appended.isEmpty()) state.lastUpdated else Instant.now(),
                    logError = null,
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (_state.value.selectedId == id) {
                _state.update { it.copy(logError = describe(t, "Lost contact with the log; retrying")) }
            }
        } finally {
            logPollInFlight.set(false)
        }
    }

    /** Turns live following on or off. */
    fun toggleFollow() = _state.update { it.copy(follow = !it.follow) }

    /** Turns line wrapping on or off. */
    fun toggleWrap() = _state.update { it.copy(wrap = !it.wrap) }

    /** Shows or hides stdout lines. */
    fun toggleStdout() = _state.update { it.copy(showStdout = !it.showStdout) }

    /** Shows or hides stderr lines. */
    fun toggleStderr() = _state.update { it.copy(showStderr = !it.showStderr) }

    /** Updates the line filter. */
    fun setSearch(text: String) = _state.update { it.copy(search = text) }

    /** Clears the inline notice early. */
    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun notice(text: String, ok: Boolean) {
        val next = DockerNotice(++noticeSeq, text, ok)
        _state.update { it.copy(notice = next) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MS.milliseconds)
            _state.update { if (it.notice?.id == next.id) it.copy(notice = null) else it }
        }
    }

    /**
     * Starts over for a newly selected instance: every host scoped piece of
     * state is cleared and read again from the new host.
     */
    private fun switchInstance(botId: Snowflake?, name: String?) {
        tailJob?.cancel()
        fleetJob?.cancel()
        overviewEverLoaded = false
        _state.update {
            it.copy(
                selectedBotId = botId,
                instanceName = name?.takeIf { n -> n.isNotEmpty() },
                overview = null,
                overviewError = null,
                selectedId = null,
                lines = emptyList(),
                cursor = null,
                logError = null,
                stats = emptyMap(),
                expanded = emptySet(),
                activeJob = null,
                jobs = emptyList(),
                fleet = emptyList(),
                fleetLoading = true,
            )
        }
        load()
    }

    private fun List<DockerLogLine>.toViewLines(): List<DockerViewLine> = map { line ->
        DockerViewLine(
            id = nextLineId++,
            raw = line.text,
            plain = stripAnsi(line.text),
            isError = line.isError,
            timestamp = line.timestamp,
        )
    }

    /** The dashboard's wording for a failure: 403 is always the owner message. */
    private fun describe(t: Throwable, fallback: String): String = when (t) {
        is DockerHttpError -> if (t.status == 403) "Only bot owners can manage Docker" else t.message.ifBlank { fallback }
        else -> fallback
    }

    private suspend fun <T> request(
        path: String,
        strategy: DeserializationStrategy<T>,
        method: KtorMethod = KtorMethod.Get,
        instance: Snowflake? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    ): T {
        val element = requestRaw(path, method, instance, timeoutMillis, allowRetry = true)
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.decodeFromJsonElement(strategy, element.normalizeKeys())
            } catch (t: Throwable) {
                throw ApiError.Decoding(t)
            }
        }
    }

    /**
     * Issues one request against the dashboard proxy, routed to [instance]
     * or, when that is `null`, to the instance [api] is pinned to. An empty
     * or `null` body is an error: the proxy turns an empty bot answer, such
     * as a bare forbid, into a 200 carrying `null`.
     */
    private suspend fun requestRaw(
        path: String,
        method: KtorMethod,
        instance: Snowflake?,
        timeoutMillis: Long,
        allowRetry: Boolean,
    ): JsonElement {
        val base = api.currentBaseUrl() ?: throw ApiError.NotConfigured()
        val target = instance ?: api.currentInstance()
        val token = auth.currentAccessToken()
        val response: HttpResponse = try {
            http.request("$base/${path.trimStart('/')}") {
                this.method = method
                header("Authorization", "Bearer $token")
                target?.let { header("X-Mobile-Instance", it) }
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw ApiError.Transport(t)
        }
        val status = response.status.value
        if (status == 401 && allowRetry) {
            runCatching { auth.refresh() }
            return requestRaw(path, method, instance, timeoutMillis, allowRetry = false)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw DockerHttpError(status, dockerErrorMessage(status, text))
        if (text.isBlank()) throw DockerHttpError(status, "The bot sent an empty answer")
        val element = withContext(Dispatchers.Default) {
            try {
                MewdekoJson.parseToJsonElement(text)
            } catch (t: Throwable) {
                throw ApiError.Decoding(t)
            }
        }
        if (element is JsonNull) throw DockerHttpError(status, "The bot sent an empty answer")
        if (element is JsonObject && element.isEmpty()) throw DockerHttpError(status, "The bot sent an empty answer")
        return element
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
