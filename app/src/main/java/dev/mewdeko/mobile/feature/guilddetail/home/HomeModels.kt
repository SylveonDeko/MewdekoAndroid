package dev.mewdeko.mobile.feature.guilddetail.home

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** The bot's per-guild identity, editable from the dashboard's overview. */
@Serializable
data class BotGuildProfile(
    val nickname: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
)

/** Guild-wide XP totals. */
@Serializable
data class XpServerStats(
    val totalUsers: Int = 0,
    val totalXp: Long = 0,
    val averageLevel: Double = 0.0,
    val highestLevel: Int = 0,
)

/** One rank on the XP leaderboard. */
@Serializable
data class XpLeader(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val avatarUrl: String? = null,
    val totalXp: Long = 0,
    val level: Int = 0,
    val rank: Int = 0,
)

/** Message counter totals, including whether counting is switched on at all. */
@Serializable
data class DailyMessageStats(
    val enabled: Boolean = false,
    val dailyMessages: Long = 0,
    val totalMessages: Long = 0,
)

/** Birthday counts for the guild. */
@Serializable
data class BirthdaySummary(
    val usersWithBirthdays: Int = 0,
    val todaysBirthdayCount: Int = 0,
)

/** Aggregate ticket counters and response times. */
@Serializable
data class TicketStatistics(
    val totalTickets: Int = 0,
    val openTickets: Int = 0,
    val closedTickets: Int = 0,
    val averageResponseTime: Double = 0.0,
    val averageResolutionTime: Double = 0.0,
)

/** One recently starred message. */
@Serializable
data class StarboardHighlight(
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val content: String? = null,
    val authorName: String = "",
    val authorAvatarUrl: String? = null,
    val imageUrl: String? = null,
    val starEmote: String = "⭐",
    val starCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

/** One recent moderation action. */
@Serializable
data class RecentModerationAction(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val moderator: String? = null,
    val reason: String? = null,
    val forgiven: Boolean = false,
    val punishment: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** Whether the guild has connected a Patreon creator account. */
@Serializable
data class PatreonLinkStatus(
    val connected: Boolean = false,
    val campaignId: String? = null,
)
