package dev.mewdeko.mobile.feature.liveboards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * What a live board shows. Mirrors the bot's `LiveBoardKind`, which travels
 * as its numeric value.
 */
enum class LiveBoardKind(
    val value: Int,
    val label: String,
    val icon: ImageVector,
    val isLeaderboard: Boolean,
) {
    INVITE_LEADERBOARD(0, "Invite leaderboard", Icons.Default.PersonAdd, true),
    MESSAGE_LEADERBOARD(1, "Message leaderboard", Icons.Default.Forum, true),
    VOICE_LEADERBOARD(2, "Voice leaderboard", Icons.Default.RecordVoiceOver, true),
    JOINS_CHART(3, "Joins chart", Icons.Default.BarChart, false),
    LEAVES_CHART(4, "Leaves chart", Icons.AutoMirrored.Filled.Logout, false),
    GROWTH_CHART(5, "Growth chart", Icons.AutoMirrored.Filled.TrendingUp, false),
    MEMBERS_CHART(6, "Members chart", Icons.Default.Groups, false),
    MESSAGES_CHART(7, "Messages chart", Icons.Default.AutoGraph, false),
    SERVER_OVERVIEW(8, "Server overview", Icons.Default.Dashboard, false),
    INVITE_STATS(9, "Invite analytics", Icons.Default.Link, false),
    ACTIVITY_LEADERBOARD(10, "Top games", Icons.Default.SportsEsports, true);

    companion object {
        /** The kind with [value], or `null` for a value this app does not know. */
        fun of(value: Int): LiveBoardKind? = entries.firstOrNull { it.value == value }
    }
}

/**
 * The window a board covers. Mirrors the bot's `StatsRange`, numeric on the
 * wire, in the order the dashboard's window picker offers them.
 */
enum class LiveBoardRange(val value: Int, val shortLabel: String, val label: String) {
    DAILY(1, "24h", "Last 24 hours"),
    WEEKLY(2, "7d", "Last 7 days"),
    MONTHLY(3, "30d", "Last 30 days"),
    ALL_TIME(0, "All", "All time");

    companion object {
        /** The range with [value], or `null` for a value this app does not know. */
        fun of(value: Int): LiveBoardRange? = entries.firstOrNull { it.value == value }
    }
}

/** How often server reports post. Mirrors the bot's `ReportFrequency`. */
enum class ReportFrequency(val value: Int, val label: String) {
    DAILY(0, "Daily"),
    WEEKLY(1, "Weekly"),
    MONTHLY(2, "Monthly");

    companion object {
        /** The frequency with [value], or `null` for a value this app does not know. */
        fun of(value: Int): ReportFrequency? = entries.firstOrNull { it.value == value }
    }
}

/** One live board, as returned by `GET LiveBoards/{guildId}`. */
@Serializable
data class LiveBoard(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    val kind: Int = 0,
    val range: Int = 2,
    val pin: Boolean = true,
    val entries: Int = 10,
    val intervalMinutes: Int = 15,
    @Serializable(with = InstantSerializer::class) val lastUpdateAt: Instant? = null,
) {
    /** The decoded board kind, when known. */
    val kindEnum: LiveBoardKind? get() = LiveBoardKind.of(kind)

    /** The label shown for this board's kind. */
    val kindLabel: String get() = kindEnum?.label ?: "Board $kind"

    /** The label shown for this board's window. */
    val rangeLabel: String get() = LiveBoardRange.of(range)?.label ?: "Range $range"

    /** Whether a row count applies to this board. */
    val showsRows: Boolean get() = kindEnum?.isLeaderboard == true
}

/** Server report settings, as returned by `GET` and `PUT LiveBoards/{guildId}/report`. */
@Serializable
data class ServerReportSettings(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val frequency: Int = 1,
    @Serializable(with = InstantSerializer::class) val lastSentAt: Instant? = null,
) {
    /** The configured channel, ignoring the empty and zero placeholders. */
    val activeChannelId: Snowflake? get() = channelId?.takeIf { it.isNotEmpty() && it != "0" }
}

/** Bounds the bot and dashboard apply to new boards. */
object LiveBoardLimits {
    /** The most boards a guild may have. */
    const val MAX_BOARDS = 10

    /** The fewest rows a leaderboard shows. */
    const val MIN_ENTRIES = 3

    /** The most rows a leaderboard shows. */
    const val MAX_ENTRIES = 25

    /** The shortest refresh interval, in minutes. */
    const val MIN_INTERVAL = 5

    /** The longest refresh interval, in minutes. */
    const val MAX_INTERVAL = 1440
}
