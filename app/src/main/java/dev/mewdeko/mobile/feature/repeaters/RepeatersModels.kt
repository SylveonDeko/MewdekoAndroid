package dev.mewdeko.mobile.feature.repeaters

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * What causes a repeater to post again.
 *
 * Wire values match the bot's `StickyTriggerMode` C# enum exactly (0 through
 * 4), which is why [ON_NO_ACTIVITY], [IMMEDIATE], and [AFTER_MESSAGES] are
 * not in alphabetical order here.
 */
enum class StickyTriggerMode(val raw: Int, val label: String, val blurb: String) {
    TIME_INTERVAL(0, "Interval", "Repost on a fixed timer"),
    ON_ACTIVITY(1, "On Activity", "Repost once the channel gets busy enough"),
    ON_NO_ACTIVITY(2, "On No Activity", "Repost after a quiet period with no messages"),
    IMMEDIATE(3, "Immediate", "Repost as soon as another message arrives"),
    AFTER_MESSAGES(4, "After Messages", "Repost after a set number of new messages");

    /** Whether this mode needs the activity threshold and time window fields. */
    val usesActivitySettings: Boolean
        get() = this == ON_ACTIVITY || this == ON_NO_ACTIVITY || this == AFTER_MESSAGES

    companion object {
        /** Maps a wire value onto a mode, defaulting to [TIME_INTERVAL]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: TIME_INTERVAL
    }
}

/** A recurring or sticky message, matching `Mewdeko.Controllers.Common.Repeaters.RepeaterResponse`. */
@Serializable
data class RepeaterEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val message: String = "",
    val interval: String = "00:05:00",
    val startTimeOfDay: String? = null,
    val noRedundant: Boolean = false,
    val isEnabled: Boolean = true,
    val triggerMode: Int = 0,
    val activityThreshold: Int = 5,
    val activityTimeWindow: String = "00:05:00",
    val conversationDetection: Boolean = false,
    val conversationThreshold: Int = 3,
    val priority: Int = 50,
    val queuePosition: Int = 0,
    val timeConditions: String? = null,
    val maxAge: String? = null,
    val maxTriggers: Int? = null,
    val threadAutoSticky: Boolean = false,
    val threadOnlyMode: Boolean = false,
    val suppressNotifications: Boolean = false,
    val forumTagConditions: String? = null,
    val threadStickyMessages: String? = null,
    val displayCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastDisplayed: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = InstantSerializer::class) val nextExecution: Instant? = null,
    val guildTimezone: String = "UTC",
    val requiresTimezone: Boolean = false,
) {
    /** The typed form of [triggerMode]. */
    val trigger: StickyTriggerMode get() = StickyTriggerMode.from(triggerMode)
}

/** A forum tag available on a forum channel, matching `Common.ClientOperations.ForumTagInfo`. */
@Serializable
data class ForumTagLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "0",
    val name: String = "",
    val emoji: String? = null,
    val isModerated: Boolean = false,
)

/**
 * A forum channel and its available tags, matching
 * `Common.ClientOperations.ForumChannelInfo`. Only the fields the repeater
 * form needs are decoded; unknown keys are ignored by the shared JSON codec.
 */
@Serializable
data class ForumChannelLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "0",
    val name: String = "",
    val tags: List<ForumTagLite> = emptyList(),
)

/** Repeater statistics, matching `Mewdeko.Controllers.Common.Repeaters.RepeaterStatsResponse`. */
@Serializable
data class RepeaterStatsResponse(
    val totalRepeaters: Int = 0,
    val activeRepeaters: Int = 0,
    val disabledRepeaters: Int = 0,
    val totalDisplays: Int = 0,
    val triggerModeDistribution: Map<String, Int> = emptyMap(),
    val mostActiveRepeater: RepeaterEntry? = null,
    val timeScheduledRepeaters: Int = 0,
    val conversationAwareRepeaters: Int = 0,
)

/**
 * Time-of-day scheduling preset, matching the bot's `PATCH api/Repeaters/{guildId}/{id}`
 * `timeSchedulePreset` values (`business`, `evening`, `weekend`, `none`, `custom`) and the
 * dashboard's `TIME_SCHEDULE_PRESETS` list.
 */
enum class TimeSchedulePreset(val raw: String, val label: String) {
    NONE("none", "No schedule"),
    BUSINESS("business", "Business Hours (9-5)"),
    EVENING("evening", "Evening Hours"),
    WEEKEND("weekend", "Weekends Only"),
    CUSTOM("custom", "Custom Schedule");

    companion object {
        /** Maps a wire value onto a preset, defaulting to [NONE]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: if (raw.isNullOrBlank()) NONE else CUSTOM
    }
}

/** An interval preset offered as a quick pick before the free-form field. */
data class IntervalPreset(val value: String, val label: String)

/** The interval presets offered on the dashboard's create form. */
val IntervalPresets = listOf(
    IntervalPreset("00:01:00", "1 minute"),
    IntervalPreset("00:05:00", "5 minutes"),
    IntervalPreset("00:15:00", "15 minutes"),
    IntervalPreset("01:00:00", "1 hour"),
    IntervalPreset("06:00:00", "6 hours"),
    IntervalPreset("1.00:00:00", "Daily"),
)

/**
 * Formats a .NET `TimeSpan`-style interval string, such as `01:30:00` or the
 * `1.00:00:00` day-prefixed form, into short readable segments like `1h 30m`.
 */
fun formatIntervalReadable(interval: String): String {
    if (interval.isBlank()) return "Not set"
    val dotIndex = interval.indexOf('.')
    val days = if (dotIndex > 0) interval.substring(0, dotIndex).toIntOrNull() ?: 0 else 0
    val timePart = if (dotIndex > 0) interval.substring(dotIndex + 1) else interval
    val parts = timePart.split(":")
    val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val seconds = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val segments = buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (seconds > 0 && days == 0 && hours == 0) add("${seconds}s")
    }
    return if (segments.isEmpty()) "0m" else segments.joinToString(" ")
}
