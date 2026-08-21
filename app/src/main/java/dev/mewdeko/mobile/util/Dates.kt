package dev.mewdeko.mobile.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

private val ShortDate: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

private val ShortDateTime: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())

/** Formats as a medium-length local date, e.g. `12 Mar 2026`. */
fun Instant.shortDate(): String = ShortDate.format(this)

/** Formats as a medium local date with a short time. */
fun Instant.shortDateTime(): String = ShortDateTime.format(this)

/**
 * Renders the gap to now in words, e.g. `3 days ago` or `in 2 hours`,
 * without pulling in a formatting library.
 */
fun Instant.relativeToNow(now: Instant = Instant.now()): String {
    val duration = Duration.between(this, now)
    val seconds = duration.seconds
    val future = seconds < 0
    val magnitude = abs(seconds)

    val (value, unit) = when {
        magnitude < 60 -> return if (future) "in a moment" else "just now"
        magnitude < 3_600 -> magnitude / 60 to "minute"
        magnitude < 86_400 -> magnitude / 3_600 to "hour"
        magnitude < 2_592_000 -> magnitude / 86_400 to "day"
        magnitude < 31_536_000 -> magnitude / 2_592_000 to "month"
        else -> magnitude / 31_536_000 to "year"
    }

    val plural = if (value == 1L) unit else "${unit}s"
    return if (future) "in $value $plural" else "$value $plural ago"
}

/** Renders a duration in seconds as `1h 2m 3s`, omitting zero components. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return buildList {
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (secs > 0 || isEmpty()) add("${secs}s")
    }.joinToString(" ")
}

/** Formats a count with thousands separators. */
fun Int.withSeparators(): String = "%,d".format(this)

/** Formats a long count with thousands separators. */
fun Long.withSeparators(): String = "%,d".format(this)

/**
 * Renders large counts compactly: `1.2k`, `3.4M`. Used in stat tiles where a
 * full separated number would wrap.
 */
fun Long.compact(): String = when {
    this >= 1_000_000_000 -> "%.1fB".format(this / 1_000_000_000.0)
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000 -> "%.1fk".format(this / 1_000.0)
    else -> toString()
}

/** Renders large counts compactly. */
fun Int.compact(): String = toLong().compact()
