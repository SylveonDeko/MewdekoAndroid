package dev.mewdeko.mobile.feature.messagestats

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Message volume for one member. */
@Serializable
data class MessageStatsUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
    val percentage: Double = 0.0,
)

/** Message volume for one channel. */
@Serializable
data class MessageStatsChannel(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
    val percentage: Double = 0.0,
)

/** Total message volume for a single hour of the day, in the guild's aggregate history. */
@Serializable
data class BusiestHour(
    val hour: Int = 0,
    val messageCount: Long = 0L,
)

/** Total message volume for a single day of the week, in the guild's aggregate history. */
@Serializable
data class BusiestDay(
    val day: String = "",
    val messageCount: Long = 0L,
)

/** Guild-wide message counters plus the top members and channels. */
@Serializable
data class MessageStatsDetail(
    val enabled: Boolean = false,
    val topUsers: List<MessageStatsUser> = emptyList(),
    val topChannels: List<MessageStatsChannel> = emptyList(),
    val leastActiveUser: MessageStatsUser? = null,
    val leastActiveChannel: MessageStatsChannel? = null,
    val busiestHours: List<BusiestHour> = emptyList(),
    val busiestDays: List<BusiestDay> = emptyList(),
    val totalMessages: Long = 0L,
    val dailyMessages: Long = 0L,
    @Serializable(with = InstantSerializer::class) val lastUpdated: Instant? = null,
)

/** The full member leaderboard. */
@Serializable
data class MessageStatsLeaderboard(
    val enabled: Boolean = false,
    val leaderboard: List<MessageStatsUser> = emptyList(),
)

/** Whether message counting is switched on for the guild. */
@Serializable
data class MessageCountStatus(val enabled: Boolean = false)

/** Export file format for the client-built stats export. */
enum class MessageStatsExportFormat(val id: String, val label: String, val extension: String, val mimeType: String) {
    CSV("csv", "CSV", "csv", "text/csv"),
    JSON("json", "JSON", "json", "application/json");

    companion object {
        /** Looks up a format by its [id], defaulting to [CSV]. */
        fun from(id: String?): MessageStatsExportFormat = entries.firstOrNull { it.id == id } ?: CSV
    }
}
