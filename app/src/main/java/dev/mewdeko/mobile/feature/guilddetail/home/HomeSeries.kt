package dev.mewdeko.mobile.feature.guilddetail.home

import dev.mewdeko.mobile.core.model.GraphStats
import dev.mewdeko.mobile.feature.messagestats.BusiestHour
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Pure series math and formatting behind the guild home.
 *
 * Kept free of Compose so every rule here can be unit tested on the JVM.
 */
object HomeSeries {

    /** One UTC day of member flow. */
    data class FlowDay(val date: LocalDate, val joins: Int, val leaves: Int) {
        /** Joins minus leaves. */
        val net: Int get() = joins - leaves
    }

    /** The longest span of days the flow chart will show. */
    private const val MaxFlowDays = 31L

    /**
     * Merges the join and leave series into one zero-filled run of UTC days.
     *
     * The server groups by UTC day, so buckets use the UTC date. The run spans
     * from the earliest date to the later of the latest date and [today],
     * capped at the last 31 days. Empty when neither series has data.
     */
    fun flow(
        joins: GraphStats?,
        leaves: GraphStats?,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): List<FlowDay> {
        val joinDays = bucket(joins)
        val leaveDays = bucket(leaves)
        val dates = joinDays.keys + leaveDays.keys
        if (dates.isEmpty()) return emptyList()
        val end = maxOf(dates.max(), today)
        val start = maxOf(dates.min(), end.minusDays(MaxFlowDays - 1))
        val days = ChronoUnit.DAYS.between(start, end).toInt()
        return (0..days).map { offset ->
            val date = start.plusDays(offset.toLong())
            FlowDay(date, joinDays[date] ?: 0, leaveDays[date] ?: 0)
        }
    }

    /** Sums a series' counts per UTC day. */
    private fun bucket(stats: GraphStats?): Map<LocalDate, Int> =
        stats?.dailyStats.orEmpty()
            .filter { it.date != Instant.EPOCH }
            .groupBy { it.date.atOffset(ZoneOffset.UTC).toLocalDate() }
            .mapValues { (_, entries) -> entries.sumOf { it.count } }

    /** The period a flow covers, in words. */
    fun periodLabel(flow: List<FlowDay>): String = when (flow.size) {
        0 -> ""
        1 -> "Last day"
        else -> "Last ${flow.size} days"
    }

    /**
     * Estimates the member count at the end of each flow day by walking back
     * from today's [total]. Never below zero.
     */
    fun memberEstimate(total: Int, flow: List<FlowDay>): List<Int> {
        if (flow.isEmpty()) return emptyList()
        val values = IntArray(flow.size)
        values[flow.lastIndex] = total
        for (index in flow.lastIndex - 1 downTo 0) {
            values[index] = (values[index + 1] - flow[index + 1].net).coerceAtLeast(0)
        }
        return values.toList()
    }

    /**
     * Counts [instants] per UTC day over the last [days] days, oldest first,
     * zero-filled. Epoch placeholders are ignored.
     */
    fun dailyCounts(
        instants: List<Instant>,
        days: Int = 14,
        today: LocalDate = LocalDate.now(ZoneOffset.UTC),
    ): List<Int> {
        val counts = IntArray(days)
        instants.forEach { instant ->
            if (instant == Instant.EPOCH) return@forEach
            val date = instant.atOffset(ZoneOffset.UTC).toLocalDate()
            val index = days - 1 - ChronoUnit.DAYS.between(date, today).toInt()
            if (index in 0 until days) counts[index]++
        }
        return counts.toList()
    }

    /**
     * Counts [instants] in the last seven days and in the seven before that.
     *
     * Returns `current` for `(now - 7d, now]` and `previous` for
     * `(now - 14d, now - 7d]`.
     */
    fun weekOverWeek(instants: List<Instant>, now: Instant = Instant.now()): Pair<Int, Int> {
        val weekAgo = now.minus(Duration.ofDays(7))
        val twoWeeksAgo = now.minus(Duration.ofDays(14))
        var current = 0
        var previous = 0
        instants.forEach { instant ->
            when {
                instant.isAfter(weekAgo) && !instant.isAfter(now) -> current++
                instant.isAfter(twoWeeksAgo) && !instant.isAfter(weekAgo) -> previous++
            }
        }
        return current to previous
    }

    /**
     * Re-buckets the server's UTC busiest hours into local hours of the day.
     *
     * Returns 24 totals indexed by local hour; hours that collide after the
     * shift are summed.
     */
    fun localHourBuckets(
        hours: List<BusiestHour>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val buckets = LongArray(24)
        val anchor = ZonedDateTime.now(ZoneOffset.UTC)
        hours.forEach { entry ->
            if (entry.hour !in 0..23) return@forEach
            val local = anchor.withHour(entry.hour).withZoneSameInstant(zone).hour
            buckets[local] += entry.messageCount
        }
        return buckets.toList()
    }

    /** The busiest bucket, or null when every bucket is empty. */
    fun peakHour(buckets: List<Long>): Int? {
        val max = buckets.maxOrNull() ?: return null
        if (max <= 0L) return null
        return buckets.indexOf(max)
    }

    /** An hour of the day as "21:00" or "9 PM". */
    fun hourLabel(hour: Int, is24h: Boolean): String {
        if (is24h) return "%02d:00".format(Locale.US, hour)
        val twelve = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "AM" else "PM"
        return "$twelve $suffix"
    }

    /**
     * A short count: plain below a thousand, then "12.4K", "1.2M" and
     * "3.4B", with a trailing ".0" dropped.
     */
    fun compact(n: Long): String {
        val magnitude = kotlin.math.abs(n)
        if (magnitude < 1_000) return n.toString()
        val units = listOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")
        var index = when {
            magnitude < 1_000_000 -> 0
            magnitude < 1_000_000_000 -> 1
            else -> 2
        }
        var scaled = Math.round(n.toDouble() / units[index].first * 10.0) / 10.0
        if (kotlin.math.abs(scaled) >= 1_000 && index < units.lastIndex) {
            index++
            scaled = Math.round(n.toDouble() / units[index].first * 10.0) / 10.0
        }
        val text = String.format(Locale.US, "%.1f", scaled).removeSuffix(".0")
        return text + units[index].second
    }

    /** A signed count: "+18", "-3" or "0". */
    fun signed(n: Int): String = when {
        n > 0 -> "+$n"
        else -> n.toString()
    }

    /**
     * Parses a .NET style duration into seconds.
     *
     * Accepts "mm:ss", "hh:mm:ss" and "d.hh:mm:ss", each optionally followed
     * by a fractional part. Null or malformed input gives null.
     */
    fun parseClock(raw: String?): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val firstColon = text.indexOf(':')
        if (firstColon < 0) return null
        var days = 0L
        var rest = text
        val dot = text.indexOf('.')
        if (dot in 0 until firstColon) {
            days = text.substring(0, dot).toLongOrNull() ?: return null
            rest = text.substring(dot + 1)
        }
        val parts = rest.split(':')
        if (parts.size !in 2..3) return null
        val numbers = parts.mapIndexed { index, part ->
            val clean = if (index == parts.lastIndex) part.substringBefore('.') else part
            clean.toLongOrNull() ?: return null
        }
        if (numbers.any { it < 0 }) return null
        val seconds = if (numbers.size == 3) {
            numbers[0] * 3_600 + numbers[1] * 60 + numbers[2]
        } else {
            numbers[0] * 60 + numbers[1]
        }
        return days * 86_400 + seconds
    }

    /** Seconds as "m:ss", or "h:mm:ss" from one hour up. */
    fun clock(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0)
        val h = safe / 3_600
        val m = (safe % 3_600) / 60
        val s = safe % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    /** The birthday card title for today's names. */
    fun birthdayTitle(names: List<String>): String = when (names.size) {
        0 -> "Happy birthday"
        1 -> "Happy birthday, ${names[0]}"
        2 -> "Happy birthday, ${names[0]} and ${names[1]}"
        else -> "Happy birthday, ${names[0]} and ${names.size - 1} others"
    }

    /** Renders a minute count as a rounded human duration. */
    fun roundedMinutes(minutes: Double): String = when {
        minutes >= 1440 -> "%.1f days".format(minutes / 1440)
        minutes >= 60 -> "%.1f hours".format(minutes / 60)
        else -> "%.0f min".format(minutes)
    }
}
