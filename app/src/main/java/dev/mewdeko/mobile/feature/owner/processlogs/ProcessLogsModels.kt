package dev.mewdeko.mobile.feature.owner.processlogs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.InstantParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Where the bot got its process list from, mirroring the bot's
 * `Pm2ListSource`, which serialises as an integer.
 */
object Pm2ListSource {
    /** pm2 is not running on the host. */
    const val NONE = 0

    /** The live `pm2 jlist` answer. */
    const val DAEMON = 1

    /** Rebuilt from the file names in pm2's log directory. */
    const val LOG_DIRECTORY = 2
}

/**
 * One process pm2 manages on the bot's host, the bot's `Pm2ProcessInfo`.
 *
 * `Pm2Controller` answers through MVC, so keys are camelCase, and every
 * nullable property is omitted when it has no value.
 */
@Immutable
@Serializable
data class Pm2ProcessInfo(
    /** pm2's id, negative for entries rebuilt from the log directory. */
    val pmId: Int = 0,
    /** The process name. */
    val name: String = "unknown",
    /** online, stopping, stopped, launching, errored, waiting restart, or unknown. */
    val status: String = "unknown",
    /** The operating system process id, when running. */
    val pid: Int? = null,
    /** Percent of one core in use when the list was read. */
    val cpu: Double? = null,
    /** Resident memory in bytes. */
    val memoryBytes: Long? = null,
    /** How many times pm2 has restarted it. */
    val restarts: Int = 0,
    /** When the current incarnation started, ISO 8601 in UTC. */
    val startedAt: String? = null,
    /** fork_mode or cluster_mode. */
    val execMode: String? = null,
    /** The script pm2 runs. */
    val script: String? = null,
    /** The stdout log file. */
    val outLogPath: String? = null,
    /** The stderr log file. */
    val errorLogPath: String? = null,
    /** The stdout log's size, when the file exists. */
    val outLogBytes: Long? = null,
    /** The stderr log's size, when the file exists. */
    val errorLogBytes: Long? = null,
    /** Whether this is the bot instance answering the request. */
    val isSelf: Boolean = false,
) {
    /** The log path for [stream]. */
    fun logPath(stream: Pm2Stream): String? = if (stream == Pm2Stream.Error) errorLogPath else outLogPath

    /** The log size for [stream]. */
    fun logBytes(stream: Pm2Stream): Long? = if (stream == Pm2Stream.Error) errorLogBytes else outLogBytes

    /** How long the process has been up, or `-` when it is not online or the start time is unknown. */
    fun uptime(nowMillis: Long): String {
        if (status != "online") return "-"
        val started = startedAt?.let(InstantParser::parse) ?: return "-"
        return formatDuration((nowMillis - started.toEpochMilli()) / 1000)
    }
}

/** The process list along with how it was obtained, the bot's `Pm2ProcessList`. */
@Immutable
@Serializable
data class Pm2ProcessList(
    /** A [Pm2ListSource] value. */
    val source: Int = Pm2ListSource.NONE,
    /** Why the list looks the way it does, when there is something to say. */
    val message: String? = null,
    /** Every process, ascending by pm2 id. */
    val processes: List<Pm2ProcessInfo> = emptyList(),
)

/**
 * A run of complete log lines plus the byte offsets needed to continue from
 * it, the bot's `Pm2LogChunk`.
 */
@Immutable
@Serializable
data class Pm2LogChunk(
    /** 0 for stdout, 1 for stderr. */
    val stream: Int = 0,
    /** The file the lines came from. */
    val path: String? = null,
    /** The file length at read time. */
    val fileSize: Long = 0,
    /** Byte offset of the first returned line. */
    val start: Long = 0,
    /** Byte offset just past the last complete line: the follow cursor. */
    val end: Long = 0,
    /** Oldest first, terminators stripped, ANSI escapes left in place. */
    val lines: List<String> = emptyList(),
    /** Earlier lines were skipped because of the 1 MB read cap. */
    val truncated: Boolean = false,
    /** The file shrank, so this is a fresh tail that replaces the buffer. */
    val rotated: Boolean = false,
)

/** Which of a process's two log files is shown. */
enum class Pm2Stream(
    /** The query string spelling. */
    val query: String,
    /** The picker label, matching the dashboard. */
    val label: String,
) {
    /** stdout. */
    Out("out", "stdout"),

    /** stderr. */
    Error("error", "stderr"),
}

/** The line counts the dashboard offers, with their picker labels. */
val Pm2LineOptions: List<Pair<Int, String>> = listOf(
    100 to "100",
    300 to "300",
    500 to "500",
    1000 to "1k",
    2000 to "2k",
)

/** Serilog's level tags as they appear in the console template. */
enum class LogLevel(
    /** The name shown in the chip's description. */
    val fullName: String,
) {
    /** Verbose. */
    VRB("Verbose"),

    /** Debug. */
    DBG("Debug"),

    /** Information. */
    INF("Info"),

    /** Warning. */
    WRN("Warning"),

    /** Error. */
    ERR("Error"),

    /** Fatal. */
    FTL("Fatal"),
}

/** One styled run of a log line. [Color.Unspecified] means the panel's default ink. */
@Immutable
data class AnsiSpan(
    val text: String,
    val color: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)

/**
 * A log line as the viewer holds it, with the plain text, styled runs and
 * level worked out once on arrival.
 */
@Immutable
data class LogLine(
    /** A stable key for the lazy list. */
    val id: Long,
    /** The text with every escape removed, for search, copy and saving. */
    val plain: String,
    /** The styled runs, or `null` when the line carries no escapes. */
    val spans: List<AnsiSpan>?,
    /** The level printed on the line itself, `null` for continuation lines such as stack traces. */
    val level: LogLevel?,
    /** The level the line belongs to, inherited from the last tagged line. */
    val effectiveLevel: LogLevel?,
)

/** Process Logs screen state: the pm2 process list and the followed log. */
@Immutable
data class ProcessLogsState(
    /** The last process list, `null` before the first answer. */
    val processList: Pm2ProcessList? = null,
    /** Whether the first process list load is in flight. */
    val processesLoading: Boolean = true,
    /** Why the process list could not be read. */
    val processesError: String? = null,
    /** When the process list last arrived, in epoch milliseconds, for uptimes. */
    val processesAt: Long = 0,
    /** The selected process's pm2 id. */
    val selectedPmId: Int? = null,
    /** The stream being read. */
    val stream: Pm2Stream = Pm2Stream.Out,
    /** How many lines a tail load asks for. */
    val lineCount: Int = 300,
    /** Whether new lines are polled for. */
    val follow: Boolean = true,
    /** Whether long lines wrap instead of scrolling sideways. */
    val wrap: Boolean = false,
    /** The client side line filter. */
    val search: String = "",
    /** Levels hidden by the chips. */
    val hiddenLevels: Set<LogLevel> = emptySet(),
    /** The buffered lines, oldest first, at most [MAX_BUFFER]. */
    val lines: List<LogLine> = emptyList(),
    /** The chunk the buffer continues from, `null` when there is nothing to follow. */
    val chunk: Pm2LogChunk? = null,
    /** Whether a tail load is in flight. */
    val logLoading: Boolean = false,
    /** The log's error banner. */
    val logError: String? = null,
    /** When the buffer last changed, in epoch milliseconds. */
    val lastUpdated: Long? = null,
    /** Lines that arrived while the user was scrolled up. */
    val pendingNew: Int = 0,
    /** Bumped whenever the view should jump to the newest line. */
    val scrollToken: Int = 0,
) {
    /** The selected process, when it is still in the list. */
    val selected: Pm2ProcessInfo?
        get() = selectedPmId?.let { id -> processList?.processes?.firstOrNull { it.pmId == id } }

    companion object {
        /** The most lines the viewer keeps, matching the dashboard. */
        const val MAX_BUFFER = 5000
    }
}

/**
 * Formats a byte count like the dashboard: `-` when unknown, whole bytes
 * under 1 KB, otherwise one decimal in the largest fitting 1024 based unit.
 */
fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "-"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

/** Formats seconds as a compact duration: `3d 4h`, `5h 12m` or `42m`. */
fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0)
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Formats a count with grouping separators, like the dashboard's `formatNumber`. */
fun formatCount(value: Number): String = String.format(Locale.getDefault(), "%,d", value.toLong())

/**
 * Text for a failed pm2 call.
 *
 * The proxy wraps the bot's plain text errors as `{"error": "..."}`, so that
 * text is shown when present. A 403 gets the dashboard's owner message, and
 * anything unreadable falls back to [fallback].
 */
fun describePm2Error(error: Throwable, fallback: String): String = when (error) {
    is ApiError.Http -> when {
        error.status == 403 -> "Only bot owners can read process logs"
        else -> proxyErrorText(error.body) ?: fallback
    }

    is ApiError.Unauthorized -> "Your session expired. Sign in again."
    is ApiError.NotConfigured -> "No dashboard selected."
    else -> fallback
}

/** The `error` or `message` text of a proxy error body, or the body itself when it is plain text. */
private fun proxyErrorText(body: String): String? {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return null
    val parsed = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(trimmed) }.getOrNull()
    if (parsed is JsonObject) {
        val text = (parsed["error"] as? JsonPrimitive)?.contentOrNull
            ?: (parsed["message"] as? JsonPrimitive)?.contentOrNull
            ?: (parsed["title"] as? JsonPrimitive)?.contentOrNull
        return text?.takeIf { it.isNotBlank() }?.take(300)
    }
    if (parsed != null) return null
    return trimmed.take(300)
}

/**
 * Ports the dashboard's `ansi.ts`: strips escapes, finds Serilog level tags,
 * and splits a line into SGR styled runs.
 */
object Ansi {
    /** Any escape sequence. */
    private val anyEscape = Regex("\u001b\\[[0-9;?]*[ -/]*[@-~]")

    /** Only the SGR (color and style) sequences. */
    private val sgr = Regex("\u001b\\[([0-9;]*)m")

    /** The Serilog level tag. */
    private val levelTag = Regex("\\[(VRB|DBG|INF|WRN|ERR|FTL)]")

    private val nextId = AtomicLong(0)

    /**
     * The 16 standard terminal colors, tuned for the dark log panel, with
     * black and bright black lifted to greys. These are the log's own content
     * colors, the same table the dashboard renders, not interface chrome.
     */
    private val basic: List<Color> = listOf(
        Color(0xFF6B7280),
        Color(0xFFF87171),
        Color(0xFF4ADE80),
        Color(0xFFFBBF24),
        Color(0xFF60A5FA),
        Color(0xFFE879F9),
        Color(0xFF22D3EE),
        Color(0xFFE5E7EB),
        Color(0xFF9CA3AF),
        Color(0xFFFCA5A5),
        Color(0xFF86EFAC),
        Color(0xFFFDE68A),
        Color(0xFF93C5FD),
        Color(0xFFF0ABFC),
        Color(0xFF67E8F9),
        Color(0xFFFFFFFF),
    )

    /** Removes every escape sequence. */
    fun strip(text: String): String = if (text.indexOf('\u001b') < 0) text else anyEscape.replace(text, "")

    /** The level tag on a plain line, or `null` for continuation lines. */
    fun detectLevel(plain: String): LogLevel? =
        levelTag.find(plain)?.groupValues?.get(1)?.let { runCatching { LogLevel.valueOf(it) }.getOrNull() }

    /**
     * Converts raw lines into view lines, carrying [previousLevel] forward
     * onto untagged continuation lines.
     */
    fun toViewLines(raw: List<String>, previousLevel: LogLevel?): List<LogLine> {
        var carried = previousLevel
        return raw.map { text ->
            val plain = strip(text)
            val level = detectLevel(plain)
            if (level != null) carried = level
            LogLine(
                id = nextId.getAndIncrement(),
                plain = plain,
                spans = if (text.contains("\u001b[")) parse(text) else null,
                level = level,
                effectiveLevel = carried,
            )
        }
    }

    /** An xterm 256 color index as a color. */
    private fun paletteColor(index: Int): Color? {
        if (index !in 0..255) return null
        if (index < 16) return basic[index]
        if (index < 232) {
            val cube = index - 16
            val step = { v: Int -> if (v == 0) 0 else 55 + v * 40 }
            return Color(step(cube / 36), step((cube % 36) / 6), step(cube % 6))
        }
        val grey = 8 + (index - 232) * 10
        return Color(grey, grey, grey)
    }

    /** Splits a raw line into styled runs. */
    fun parse(text: String): List<AnsiSpan> {
        val spans = mutableListOf<AnsiSpan>()
        var style = AnsiSpan("")
        var last = 0

        fun push(chunk: String) {
            val clean = strip(chunk)
            if (clean.isEmpty()) return
            val previous = spans.lastOrNull()
            if (previous != null && previous.copy(text = "") == style.copy(text = "")) {
                spans[spans.lastIndex] = previous.copy(text = previous.text + clean)
            } else {
                spans += style.copy(text = clean)
            }
        }

        for (match in sgr.findAll(text)) {
            push(text.substring(last, match.range.first))
            last = match.range.last + 1
            val body = match.groupValues[1]
            val params = if (body.isEmpty()) listOf(0) else body.split(';').map { it.toIntOrNull() ?: 0 }
            style = apply(params, style)
        }
        push(text.substring(last))
        return spans
    }

    /** Applies one SGR parameter list: resets, intensity, and the 16, 256 and 24 bit color forms. */
    private fun apply(params: List<Int>, current: AnsiSpan): AnsiSpan {
        var next = current
        var i = 0
        while (i < params.size) {
            when (val code = params[i]) {
                0 -> return AnsiSpan("")
                1 -> next = next.copy(bold = true)
                2 -> next = next.copy(dim = true)
                3 -> next = next.copy(italic = true)
                4 -> next = next.copy(underline = true)
                22 -> next = next.copy(bold = false, dim = false)
                23 -> next = next.copy(italic = false)
                24 -> next = next.copy(underline = false)
                in 30..37 -> next = next.copy(color = basic[code - 30])
                in 90..97 -> next = next.copy(color = basic[code - 90 + 8])
                39 -> next = next.copy(color = Color.Unspecified)
                in 40..47 -> next = next.copy(background = basic[code - 40])
                in 100..107 -> next = next.copy(background = basic[code - 100 + 8])
                49 -> next = next.copy(background = Color.Unspecified)
                38, 48 -> {
                    val mode = params.getOrNull(i + 1)
                    val resolved: Color?
                    when {
                        mode == 5 && i + 2 < params.size -> {
                            resolved = paletteColor(params[i + 2])
                            i += 2
                        }

                        mode == 2 && i + 4 < params.size -> {
                            resolved = Color(
                                params[i + 2].coerceIn(0, 255),
                                params[i + 3].coerceIn(0, 255),
                                params[i + 4].coerceIn(0, 255),
                            )
                            i += 4
                        }

                        else -> return next
                    }
                    val value = resolved ?: Color.Unspecified
                    next = if (code == 38) next.copy(color = value) else next.copy(background = value)
                }
            }
            i++
        }
        return next
    }
}
