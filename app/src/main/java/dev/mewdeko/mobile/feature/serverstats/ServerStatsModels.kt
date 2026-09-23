package dev.mewdeko.mobile.feature.serverstats

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Which activity a ranking or export measures. Mirrors the bot's `StatKind`, numeric on the wire. */
enum class StatKind(val value: Int, val label: String) {
    MESSAGES(0, "Messages"),
    VOICE(1, "Voice"),
    ACTIVITY(2, "Games"),
}

/** The chart kinds the series endpoint accepts. Mirrors the bot's `StatChartKind`. */
object StatChartKind {
    /** Messages per bucket. */
    const val MESSAGES = 0

    /** Voice hours per bucket. */
    const val VOICE = 1

    /** Hourly guild snapshots (members, statuses, in voice). */
    const val MEMBERS = 2

    /** Joins and leaves per day. */
    const val GROWTH = 6
}

/** What an exclusion targets. Mirrors the bot's `StatsExclusionKind`. */
enum class StatsExclusionKind(val value: Int, val label: String) {
    CHANNEL(0, "channel"),
    ROLE(1, "role"),
    USER(2, "member"),
}

/** How the activity name list is applied. Mirrors the bot's `ActivityFilterMode`. */
enum class ActivityFilterMode(val value: Int, val label: String, val blurb: String) {
    BLACKLIST(0, "Blacklist", "The listed games are ignored; everything else is tracked."),
    WHITELIST(1, "Whitelist", "Only the listed games are tracked."),
}

/** A voice state that can be left out of voice time. Mirrors the bot's `VoiceStateFlags`. */
enum class VoiceStateFlag(val bit: Int, val label: String, val hint: String) {
    SELF_MUTED(1, "Self muted", "Time with the mic off"),
    SELF_DEAFENED(2, "Self deafened", "Time with sound off"),
    SERVER_MUTED(4, "Server muted", "Muted by a moderator"),
    SERVER_DEAFENED(8, "Server deafened", "Deafened by a moderator"),
    AFK(16, "AFK channel", "Time in the AFK channel"),
    ALONE(32, "Alone", "The only human in the channel"),
    STREAMING(64, "Streaming", "Time with a stream running"),
    VIDEO(128, "Camera on", "Time with video on");

    /** Whether this state's bit is set in [mask]. */
    fun isSet(mask: Int): Boolean = mask and bit != 0
}

/** One lookback window choice; 0 means all time. */
data class LookbackOption(val days: Int, val label: String) {
    companion object {
        /** The windows offered by the picker, matching the dashboard. */
        val all = listOf(
            LookbackOption(1, "24h"),
            LookbackOption(7, "7d"),
            LookbackOption(14, "14d"),
            LookbackOption(30, "30d"),
            LookbackOption(90, "90d"),
            LookbackOption(0, "All"),
        )

        /** A human name for a window, e.g. `Last 14 days`. */
        fun describe(days: Int): String = when (days) {
            0 -> "All time"
            1 -> "Last 24 hours"
            else -> "Last $days days"
        }
    }
}

/** A ranked member or channel with its resolved name. */
@Serializable
data class NamedEntry(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val value: Long = 0,
    val name: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
) {
    /** The name to show for a member entry. */
    val memberLabel: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: id

    /** The name to show for a channel entry. */
    val channelLabel: String
        get() = "#${name?.takeIf { it.isNotBlank() } ?: id}"
}

/** A row from a ranking endpoint. */
@Serializable
data class RankedEntry(
    val rank: Int = 0,
    val entry: NamedEntry = NamedEntry(),
)

/** Live member and presence counts. */
@Serializable
data class GuildNow(
    val members: Int = 0,
    val humans: Int = 0,
    val bots: Int = 0,
    val online: Int = 0,
    val idle: Int = 0,
    val dnd: Int = 0,
    val offline: Int = 0,
    val inVoice: Int = 0,
)

/** The guild wide overview from `GET ServerStats/{g}/overview`. */
@Serializable
data class ServerOverview(
    val lookbackDays: Int = 0,
    val messages: Long = 0,
    val voiceSeconds: Long = 0,
    val messageContributors: Int = 0,
    val voiceContributors: Int = 0,
    val joins: Int = 0,
    val leaves: Int = 0,
    val netGrowth: Int = 0,
    val topMessageUser: NamedEntry? = null,
    val topVoiceUser: NamedEntry? = null,
    val topMessageChannel: NamedEntry? = null,
    val topVoiceChannel: NamedEntry? = null,
    val now: GuildNow = GuildNow(),
)

/** One member's activity from `GET ServerStats/{g}/user/{id}`. */
@Serializable
data class UserActivity(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val lookbackDays: Int = 0,
    val messages: Long = 0,
    val voiceSeconds: Long = 0,
    val messageRank: Int? = null,
    val voiceRank: Int? = null,
    val allTimeMessages: Long = 0,
    val allTimeVoiceSeconds: Long = 0,
    val topMessageChannels: List<NamedEntry> = emptyList(),
    val topVoiceChannels: List<NamedEntry> = emptyList(),
)

/** One channel's activity from `GET ServerStats/{g}/channel/{id}`. */
@Serializable
data class ChannelActivity(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val lookbackDays: Int = 0,
    val messages: Long = 0,
    val voiceSeconds: Long = 0,
    val contributors: Int = 0,
    val topMessageUsers: List<NamedEntry> = emptyList(),
    val topVoiceUsers: List<NamedEntry> = emptyList(),
)

/** A game or app ranking row from `GET ServerStats/{g}/top/activities`. */
@Serializable
data class ActivityRow(
    val rank: Int = 0,
    val name: String = "",
    @Serializable(with = SnowflakeSerializer::class) val applicationId: Snowflake? = null,
    val type: String = "",
    val seconds: Long = 0,
    val players: Int = 0,
    val activeNow: Int = 0,
)

/** Who plays one game, from `GET ServerStats/{g}/activity`. */
@Serializable
data class ActivityDetail(
    val name: String = "",
    val activeNow: Int = 0,
    val players: Int = 0,
    val totalSeconds: Long = 0,
    val top: List<RankedEntry> = emptyList(),
)

/** One point of a message or voice series. */
@Serializable
data class SeriesPoint(
    @Serializable(with = InstantSerializer::class) val bucket: Instant = Instant.EPOCH,
    val value: Double = 0.0,
)

/** One hourly guild snapshot. */
@Serializable
data class GuildSnapshot(
    @Serializable(with = InstantSerializer::class) val timestamp: Instant = Instant.EPOCH,
    val members: Int = 0,
    val humans: Int = 0,
    val bots: Int = 0,
    val online: Int = 0,
    val idle: Int = 0,
    val dnd: Int = 0,
    val offline: Int = 0,
    val inVoice: Int = 0,
)

/** Joins and leaves per day. */
@Serializable
data class JoinLeaveSeries(
    val joins: List<SeriesPoint> = emptyList(),
    val leaves: List<SeriesPoint> = emptyList(),
)

/** Tracking settings from `GET/PUT ServerStats/{g}/settings`. */
@Serializable
data class ServerStatsSettings(
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val trackVoice: Boolean = true,
    val trackSnapshots: Boolean = true,
    val messageCooldownSeconds: Int = 0,
    val defaultLookbackDays: Int = 14,
    val countBots: Boolean = false,
    val voiceStates: Int = 0,
    val trackActivities: Boolean = false,
    val verifyActivities: Boolean = true,
    val activityFilterMode: Int = 0,
)

/** Exclusions of every kind from `GET ServerStats/{g}/exclusions`. */
@Serializable
data class StatsExclusions(
    val channels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val roles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val users: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
) {
    /** The ids excluded for [kind]. */
    fun of(kind: StatsExclusionKind): List<Snowflake> = when (kind) {
        StatsExclusionKind.CHANNEL -> channels
        StatsExclusionKind.ROLE -> roles
        StatsExclusionKind.USER -> users
    }
}

/** The activity filter from `GET ServerStats/{g}/activity-filters`. */
@Serializable
data class ActivityFilters(
    val mode: Int = 0,
    val names: List<String> = emptyList(),
)

/** A CSV export ready to be written to a user-chosen file. */
data class PendingExport(
    val fileName: String,
    val kind: StatKind,
    val lookback: Int,
)
