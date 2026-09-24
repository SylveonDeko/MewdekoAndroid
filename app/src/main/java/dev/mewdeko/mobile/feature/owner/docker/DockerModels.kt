package dev.mewdeko.mobile.feature.owner.docker

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantParser
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.max
import kotlin.math.min

/**
 * Response models for the bot's `DockerController`.
 *
 * The controller answers through `Ok(obj)` and `StatusCode(502, obj)`, so
 * every payload is ASP.NET's default camelCase with null members omitted and
 * enums written as integers. Dates stay strings here and are parsed only for
 * display, so the log cursor (a nanosecond RFC 3339 timestamp) is never
 * rounded through an [Instant].
 */
object DockerAvailability {
    /** The daemon answered. */
    const val AVAILABLE = 0

    /** No socket or `DOCKER_HOST` was found on the host. */
    const val NOT_CONFIGURED = 1

    /** A daemon endpoint exists but did not answer. */
    const val UNREACHABLE = 2
}

/** The integer states of a compose helper job. */
object DockerJobStatus {
    /** The helper container is still running. */
    const val RUNNING = 0

    /** The helper container exited with code zero. */
    const val SUCCEEDED = 1

    /** The helper container exited with a non zero code or vanished. */
    const val FAILED = 2
}

/** One container the daemon knows about. */
@Serializable
data class DockerContainerInfo(
    /** The full 64 character container id. */
    val id: String = "",
    /** The container name, without the leading slash. */
    val name: String = "",
    /** The image reference the container was created from. */
    val image: String = "",
    /** The daemon state: running, exited, paused, restarting, created or dead. */
    val state: String = "",
    /** The daemon's human readable status, such as `Up 4 days (healthy)`. */
    val status: String = "",
    /** The healthcheck state, absent when the container has no healthcheck. */
    val health: String? = null,
    /** When the container was created, ISO 8601 UTC. */
    val createdAt: String? = null,
    /** The compose project label, when started by compose. */
    val composeProject: String? = null,
    /** The compose service label, when started by compose. */
    val composeService: String? = null,
    /** Published ports as `host:container/proto`, or `container/proto` when unpublished. */
    val ports: List<String> = emptyList(),
    /** Whether this is the container the answering bot instance runs in. */
    val isSelf: Boolean = false,
    /** Whether the container was started with a TTY. */
    val tty: Boolean = false,
) {
    /** Whether the daemon reports the container as running. */
    val isRunning: Boolean get() = state == "running"

    /** The first 12 characters of the id, as `docker ps` prints it. */
    val shortId: String get() = id.take(12)
}

/** A compose project found through container labels. */
@Serializable
data class DockerComposeProject(
    /** The compose project name, case sensitive. */
    val name: String = "",
    /** The project's working directory on the host. */
    val workingDir: String? = null,
    /** The compose files the project was started with. */
    val configFiles: List<String> = emptyList(),
    /** How many of its containers are running. */
    val running: Int = 0,
    /** How many containers carry the project label. */
    val total: Int = 0,
    /** Whether the compose files are visible from inside the bot, required for pull, up and update. */
    val operable: Boolean = false,
)

/** The daemon summary: counts, every container and every compose project. */
@Serializable
data class DockerOverview(
    /** One of [DockerAvailability]. */
    val availability: Int = DockerAvailability.UNREACHABLE,
    /** Why the daemon is unavailable, when it is. */
    val message: String? = null,
    /** The socket or `DOCKER_HOST` value in use. */
    val endpoint: String? = null,
    /** The engine version. */
    val serverVersion: String? = null,
    /** The host operating system. */
    val operatingSystem: String? = null,
    /** The host CPU architecture. */
    val architecture: String? = null,
    /** Running container count. */
    val running: Int = 0,
    /** Stopped container count. */
    val stopped: Int = 0,
    /** Image count. */
    val images: Int = 0,
    /** Whether compose operations can run on this host. */
    val composeAvailable: Boolean = false,
    /** Every container, grouped by project with standalone ones last. */
    val containers: List<DockerContainerInfo> = emptyList(),
    /** Every compose project, sorted by name. */
    val projects: List<DockerComposeProject> = emptyList(),
) {
    /** Whether the daemon answered. */
    val isAvailable: Boolean get() = availability == DockerAvailability.AVAILABLE
}

/** One resource sample for a running container. */
@Serializable
data class DockerContainerStats(
    /** The container id. */
    val id: String = "",
    /** Percent of the host's total CPU, above 100 on multi core hosts. */
    val cpuPercent: Double = 0.0,
    /** Memory in use, excluding cache. */
    val memoryBytes: Long = 0,
    /** The memory limit, the host total when unlimited; zero when unknown. */
    val memoryLimitBytes: Long = 0,
    /** Bytes received over every network. */
    val networkRxBytes: Long = 0,
    /** Bytes sent over every network. */
    val networkTxBytes: Long = 0,
    /** Process count inside the container. */
    val pids: Long = 0,
    /** When the sample was taken. */
    val sampledAt: String? = null,
)

/** One log line. */
@Serializable
data class DockerLogLine(
    /** The RFC 3339 nanosecond timestamp, kept verbatim since it is the follow cursor. */
    val timestamp: String = "",
    /** Whether the line came from stderr. */
    val isError: Boolean = false,
    /** The line text, ANSI color codes included. */
    val text: String = "",
)

/** A slice of a container log. */
@Serializable
data class DockerLogChunk(
    /** The container the lines belong to. */
    val containerId: String = "",
    /** The lines, oldest first. */
    val lines: List<DockerLogLine> = emptyList(),
    /** The timestamp to pass as `since` next time, absent on an empty fresh tail. */
    val cursor: String? = null,
)

/** The answer to a start, stop or restart. */
@Serializable
data class DockerActionResult(
    /** Whether the daemon accepted the action. */
    val success: Boolean = false,
    /** What the daemon said. */
    val message: String? = null,
)

/** The newest image published to Docker Hub for this bot. */
@Serializable
data class DockerPublishedImage(
    /** The repository, such as `sylveondeko/mewdeko`. */
    val repository: String = "",
    /** The tag, normally `nightly`. */
    val tag: String = "",
    /** The commit the published image was built from. */
    val gitSha: String? = null,
    /** The image digest. */
    val digest: String? = null,
    /** When the image was pushed. */
    val publishedAt: String? = null,
    /** When the registry was last asked. */
    val checkedAt: String? = null,
    /** Why the registry could not be read, when it could not. */
    val error: String? = null,
)

/** What one bot instance runs and whether it can be updated from here. */
@Serializable
data class DockerSelfInfo(
    /** The bot's assembly version. */
    val botVersion: String = "",
    /** The commit the running build came from. */
    val gitSha: String? = null,
    /** When the running build was made. */
    val buildDate: String? = null,
    /** When the bot process started. */
    val startedAt: String? = null,
    /** The container the bot runs in, when it runs in one. */
    val container: DockerContainerInfo? = null,
    /** The compose project that container belongs to. */
    val project: DockerComposeProject? = null,
    /** The newest published image. */
    val published: DockerPublishedImage? = null,
    /** Whether a newer build is published; absent when either commit is unknown. */
    val updateAvailable: Boolean? = null,
    /** Whether this instance can be updated from the dashboard. */
    val canUpdate: Boolean = false,
    /** Why [canUpdate] is false. */
    val updateBlockedReason: String? = null,
)

/** A compose operation running in a `docker:cli` helper container. */
@Serializable
data class DockerJob(
    /** The helper container id, used to poll the job. */
    val id: String = "",
    /** The helper container name. */
    val name: String = "",
    /** The services the job is limited to; empty for the whole project. */
    val services: List<String> = emptyList(),
    /** The compose project. */
    val project: String = "",
    /** pull, up or update. */
    val operation: String = "",
    /** The shell steps joined with `&&`. */
    val command: String = "",
    /** One of [DockerJobStatus]. */
    val status: Int = DockerJobStatus.RUNNING,
    /** When the helper container was created. */
    val startedAt: String? = null,
    /** When the helper container exited. */
    val finishedAt: String? = null,
    /** The helper's exit code once finished. */
    val exitCode: Int? = null,
    /** Combined stdout and stderr, ANSI kept; always empty in the list endpoint. */
    val output: List<String> = emptyList(),
) {
    /** Whether the helper is still running. */
    val isRunning: Boolean get() = status == DockerJobStatus.RUNNING

    /** `{operation}` plus the services it is limited to, if any. */
    val operationLabel: String
        get() = if (services.isEmpty()) operation else "$operation (${services.joinToString(", ")})"

    /** The job's status word. */
    val statusLabel: String
        get() = when (status) {
            DockerJobStatus.SUCCEEDED -> "succeeded"
            DockerJobStatus.FAILED -> "failed"
            else -> "running"
        }
}

/**
 * A registered bot instance from `api/InstanceManagement`. The bot id is a
 * raw JSON number above 2^53, so it is decoded losslessly as a string.
 */
@Serializable
data class DockerBotInstance(
    /** The registry row id. */
    val id: Int = 0,
    /** The bot's display name. */
    val botName: String = "",
    /** The bot's avatar URL or hash. */
    val botAvatar: String? = null,
    /** The bot's Discord id, sent as `X-Mobile-Instance` to route a request to it. */
    @Serializable(with = SnowflakeSerializer::class) val botId: Snowflake = "",
    /** Whether the instance is marked active. */
    val isActive: Boolean = false,
    /** The port the bot's API listens on. */
    val port: Int = 0,
    /** The host the bot's API listens on. */
    val host: String? = null,
) {
    /** The avatar at 64px, or `null` when none is set. */
    val avatarUrl: String?
        get() {
            val avatar = botAvatar?.takeIf { it.isNotBlank() } ?: return null
            if (avatar.startsWith("http")) return avatar
            if (botId.isBlank()) return null
            return "https://cdn.discordapp.com/avatars/$botId/$avatar.png?size=64"
        }
}

/** Container actions the page can ask for. */
enum class DockerContainerAction(val path: String, val verb: String) {
    /** Starts a stopped container. */
    Start("start", "Start"),

    /** Stops a running container. */
    Stop("stop", "Stop"),

    /** Restarts a running container. */
    Restart("restart", "Restart"),
}

/** Compose operations the page can start. */
enum class DockerComposeOperation(val path: String, val label: String) {
    /** Pulls images and rebuilds local builds. */
    Pull("pull", "Pull"),

    /** Brings the project up. */
    Up("up", "Up"),

    /** Pull, then up. */
    Update("update", "Update"),
}

/** One registered instance and what it said about its own build. */
data class DockerFleetEntry(
    /** The registered instance. */
    val instance: DockerBotInstance,
    /** Its latest answer, kept while it is asked again. */
    val info: DockerSelfInfo? = null,
    /** Why it did not answer. */
    val error: String? = null,
    /** Whether it is being asked right now. */
    val loading: Boolean = false,
)

/** The inline banner under the Daemon section. */
data class DockerNotice(
    /** Distinguishes notices so an old auto dismiss never clears a newer one. */
    val id: Long,
    /** The sentence shown. */
    val text: String,
    /** Green when true, red otherwise. */
    val ok: Boolean,
)

/** A log line as the viewer holds it, with plain text worked out once on arrival. */
data class DockerViewLine(
    /** A stable key for the lazy list. */
    val id: Long,
    /** The text with ANSI codes. */
    val raw: String,
    /** The text without ANSI codes, for search and copy. */
    val plain: String,
    /** Whether the line came from stderr. */
    val isError: Boolean,
    /** The line's timestamp. */
    val timestamp: String,
)

/** A project card: the compose project (or the standalone bucket) and its containers. */
data class DockerGroup(
    /** The expansion key: the project name, or [DOCKER_STANDALONE]. */
    val key: String,
    /** The project, or `null` for standalone containers. */
    val project: DockerComposeProject?,
    /** The containers in this group. */
    val containers: List<DockerContainerInfo>,
) {
    /** The card title. */
    val title: String get() = project?.name ?: "Standalone containers"

    /** Running containers in the group. */
    val running: Int get() = project?.running ?: containers.count { it.isRunning }

    /** Containers in the group. */
    val total: Int get() = project?.total ?: containers.size
}

/** The client side key of the standalone group, the same as the dashboard's. */
const val DOCKER_STANDALONE = "__standalone"

/** The log line counts the viewer offers, with their labels. */
val DockerLineOptions: List<Pair<Int, String>> = listOf(
    100 to "100",
    300 to "300",
    500 to "500",
    1000 to "1k",
    2000 to "2k",
)

/**
 * Groups containers into one card per compose project, in the overview's
 * order, with containers that match no project in a trailing standalone card.
 */
fun DockerOverview.groups(): List<DockerGroup> {
    val byProject = LinkedHashMap<String, MutableList<DockerContainerInfo>>()
    projects.forEach { byProject[it.name] = mutableListOf() }
    val standalone = mutableListOf<DockerContainerInfo>()
    containers.forEach { container ->
        val bucket = container.composeProject?.let { byProject[it] }
        (bucket ?: standalone).add(container)
    }
    val result = projects.map { DockerGroup(it.name, it, byProject[it.name].orEmpty()) }
    return if (standalone.isEmpty()) result else result + DockerGroup(DOCKER_STANDALONE, null, standalone)
}

/** Screen state for the Docker page. */
data class DockerState(
    /** The selected instance's bot id, which marks its fleet row. */
    val selectedBotId: Snowflake? = null,
    /** The selected instance's name, the app bar subtitle. */
    val instanceName: String? = null,
    /** The latest daemon summary. */
    val overview: DockerOverview? = null,
    /** Why the last overview read failed. */
    val overviewError: String? = null,
    /** Expanded group keys; they survive refreshes. */
    val expanded: Set<String> = emptySet(),
    /** The latest resource sample per container id. */
    val stats: Map<String, DockerContainerStats> = emptyMap(),
    /** The action in flight per container id. */
    val busyContainers: Map<String, DockerContainerAction> = emptyMap(),
    /** The inline banner. */
    val notice: DockerNotice? = null,
    /** Known compose jobs, newest first. */
    val jobs: List<DockerJob> = emptyList(),
    /** The job shown in the terminal panel. */
    val activeJob: DockerJob? = null,
    /** The project whose compose job is being started. */
    val startingJob: String? = null,
    /** Every registered instance and its self report. */
    val fleet: List<DockerFleetEntry> = emptyList(),
    /** Whether the fleet is loading with nothing to show yet. */
    val fleetLoading: Boolean = true,
    /** Whether the fleet is being asked again. */
    val fleetChecking: Boolean = false,
    /** Why the instance list could not be read. */
    val fleetError: String? = null,
    /** Registry row ids of instances whose update is being started. */
    val updatingInstances: Set<Int> = emptySet(),
    /** Whether the fleet wide update is being started. */
    val updatingAll: Boolean = false,
    /** The container open in the log viewer. */
    val selectedId: String? = null,
    /** How many lines a tail asks for. */
    val lineCount: Int = 300,
    /** Whether new lines are fetched every two seconds. */
    val follow: Boolean = true,
    /** Whether long lines wrap. */
    val wrap: Boolean = false,
    /** The case insensitive line filter. */
    val search: String = "",
    /** Whether stdout lines are shown. */
    val showStdout: Boolean = true,
    /** Whether stderr lines are shown. */
    val showStderr: Boolean = true,
    /** The buffered log lines, capped at [DOCKER_MAX_BUFFER]. */
    val lines: List<DockerViewLine> = emptyList(),
    /** The follow cursor, verbatim. */
    val cursor: String? = null,
    /** Whether a tail is loading. */
    val logLoading: Boolean = false,
    /** Why the log could not be read. */
    val logError: String? = null,
    /** When lines last arrived. */
    val lastUpdated: Instant? = null,
    /** Bumped on every fresh tail so the viewer jumps to the bottom. */
    val tailVersion: Int = 0,
) {
    /** Whether the daemon answered. */
    val available: Boolean get() = overview?.isAvailable == true

    /** The container open in the log viewer, if it still exists. */
    val selected: DockerContainerInfo?
        get() = selectedId?.let { id -> overview?.containers?.firstOrNull { it.id == id } }

    /** Whether the active job is still running, which blocks every other job. */
    val jobRunning: Boolean get() = activeJob?.isRunning == true

    /** Instances with a newer build published. */
    val fleetUpdatesAvailable: Int get() = fleet.count { it.info?.updateAvailable == true }

    /** Whether the selected instance is part of a compose fleet that can be updated. */
    val fleetCanUpdateAll: Boolean
        get() = fleet.any { it.info?.canUpdate == true && it.instance.botId == selectedBotId }
}

/** The web viewer's buffer cap. */
const val DOCKER_MAX_BUFFER = 5000

/** An HTTP failure with the message the dashboard proxy carried. */
class DockerHttpError(val status: Int, override val message: String) : Exception(message)

/**
 * Pulls the readable message out of a proxy error body, in the dashboard's
 * order: a failed container action's `details.message`, then `error` as a
 * string, `error.message`, `message`, the raw text, and finally the status.
 */
fun dockerErrorMessage(status: Int, text: String): String {
    val element = runCatching { MewdekoJson.parseToJsonElement(text) }.getOrNull()
    if (element is JsonObject) {
        val details = element["details"] as? JsonObject
        (details?.get("message") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        when (val error = element["error"]) {
            is JsonPrimitive -> error.contentOrNull?.takeIf { it.isNotBlank() }?.let { return friendlyProxyError(it) }
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            else -> Unit
        }
        (element["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    }
    if (element is JsonPrimitive && element.isString && element.content.isNotBlank()) return element.content
    if (text.isNotBlank()) return text.take(300)
    return "Request failed with status $status"
}

/** Turns the proxy's machine codes into sentences. */
private fun friendlyProxyError(code: String): String = when (code) {
    "unknown_instance" -> "The dashboard does not route to this instance"
    "select_instance_required" -> "No bot instance is selected"
    "invalid_token", "session_revoked" -> "Your session expired. Sign in again."
    else -> code
}

/** The dashboard's `formatAgo`: `just now`, `5m ago`, `3h ago`, `2d ago`, or `-`. */
fun dockerAgo(iso: String?, now: Instant = Instant.now()): String {
    val then = iso?.let { InstantParser.parse(it) } ?: return "-"
    val seconds = max(0L, now.epochSecond - then.epochSecond)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/** The dashboard's `formatBytes`: B, KB, MB, GB or TB with one decimal above bytes. */
fun dockerBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.size - 1) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

private val LocalClock: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/** The local wall clock time of [instant], for the log status bar. */
fun dockerClock(instant: Instant): String = LocalClock.format(instant)

/** Any escape sequence, so a line can be reduced to plain text. */
private val AnsiPattern = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

/** Removes every escape sequence, leaving the text a person would read. */
fun stripAnsi(text: String): String = if (text.indexOf('\u001B') < 0) text else AnsiPattern.replace(text, "")

/**
 * One run of log text and the SGR styling active when it was written.
 * Colors are indexes into the 16 standard terminal slots, which the viewer
 * maps onto palette roles; 256 and 24 bit colors are folded onto the nearest
 * slot so every rendered color still comes from the palette.
 */
data class AnsiSpan(
    val text: String,
    val color: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

/** Splits a raw line into styled runs; lines without escapes come back as one plain run. */
fun parseAnsi(text: String): List<AnsiSpan> {
    if (!text.contains("\u001B[")) return listOf(AnsiSpan(text))
    val spans = mutableListOf<AnsiSpan>()
    var style = AnsiSpan("")
    var last = 0
    AnsiPattern.findAll(text).forEach { match ->
        if (match.range.first > last) spans += style.copy(text = text.substring(last, match.range.first))
        val sequence = match.value
        if (sequence.endsWith("m")) {
            val body = sequence.substring(2, sequence.length - 1)
            if (body.all { it.isDigit() || it == ';' }) {
                val params = if (body.isEmpty()) listOf(0) else body.split(';').map { it.toIntOrNull() ?: 0 }
                style = applySgr(params, style)
            }
        }
        last = match.range.last + 1
    }
    if (last < text.length) spans += style.copy(text = text.substring(last))
    return spans.filter { it.text.isNotEmpty() }
}

/** Applies one SGR parameter list to [style]; unknown codes are ignored. */
private fun applySgr(params: List<Int>, style: AnsiSpan): AnsiSpan {
    var next = style
    var i = 0
    while (i < params.size) {
        val code = params[i]
        when {
            code == 0 -> next = AnsiSpan("")
            code == 1 -> next = next.copy(bold = true)
            code == 2 -> next = next.copy(dim = true)
            code == 3 -> next = next.copy(italic = true)
            code == 4 -> next = next.copy(underline = true)
            code == 22 -> next = next.copy(bold = false, dim = false)
            code == 23 -> next = next.copy(italic = false)
            code == 24 -> next = next.copy(underline = false)
            code in 30..37 -> next = next.copy(color = code - 30)
            code in 90..97 -> next = next.copy(color = code - 90 + 8)
            code == 39 -> next = next.copy(color = null)
            code in 40..47 -> next = next.copy(background = code - 40)
            code in 100..107 -> next = next.copy(background = code - 100 + 8)
            code == 49 -> next = next.copy(background = null)
            code == 38 || code == 48 -> {
                val mode = params.getOrNull(i + 1)
                val slot: Int? = when {
                    mode == 5 && i + 2 < params.size -> {
                        val index = params[i + 2]
                        i += 2
                        xtermSlot(index)
                    }

                    mode == 2 && i + 4 < params.size -> {
                        val slot = rgbSlot(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                        slot
                    }

                    else -> return next
                }
                next = if (code == 38) next.copy(color = slot) else next.copy(background = slot)
            }
        }
        i++
    }
    return next
}

/** Folds an xterm 256 color index onto one of the 16 standard slots. */
private fun xtermSlot(index: Int): Int? {
    if (index !in 0..255) return null
    if (index < 16) return index
    if (index < 232) {
        val cube = index - 16
        val step = { v: Int -> if (v == 0) 0 else 55 + v * 40 }
        return rgbSlot(step(cube / 36), step((cube % 36) / 6), step(cube % 6))
    }
    val grey = 8 + (index - 232) * 10
    return rgbSlot(grey, grey, grey)
}

/** The standard slot whose hue and lightness is closest to an RGB color. */
private fun rgbSlot(r: Int, g: Int, b: Int): Int {
    val rf = r.coerceIn(0, 255) / 255.0
    val gf = g.coerceIn(0, 255) / 255.0
    val bf = b.coerceIn(0, 255) / 255.0
    val high = max(rf, max(gf, bf))
    val low = min(rf, min(gf, bf))
    val lightness = (high + low) / 2
    val chroma = high - low
    if (chroma < 0.15) {
        return when {
            lightness < 0.3 -> 0
            lightness < 0.75 -> 8
            lightness < 0.9 -> 7
            else -> 15
        }
    }
    val hue = when (high) {
        rf -> 60 * (((gf - bf) / chroma) % 6)
        gf -> 60 * (((bf - rf) / chroma) + 2)
        else -> 60 * (((rf - gf) / chroma) + 4)
    }.let { if (it < 0) it + 360 else it }
    val base = when {
        hue < 20 || hue >= 330 -> 1
        hue < 70 -> 3
        hue < 160 -> 2
        hue < 200 -> 6
        hue < 260 -> 4
        else -> 5
    }
    return if (lightness > 0.65) base + 8 else base
}
