package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

// region XP

/** Single entry in the guild XP leaderboard. */
@Serializable
data class XpLeaderboardEntry(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val totalXp: Long = 0L,
    val level: Int = 0,
    val levelXp: Long = 0L,
    val requiredXp: Long = 0L,
    val rank: Int = 0,
    val bonusXp: Long = 0L,
    val username: String = "Unknown",
    val avatarUrl: String? = null,
)

/** Aggregate XP statistics for the whole guild. */
@Serializable
data class XpServerStats(
    val totalUsers: Int = 0,
    val totalXp: Long = 0L,
    val averageLevel: Double = 0.0,
    val highestLevel: Int = 0,
)

// endregion

// region Message stats

/** Daily/total message counts and top users/channels for a guild. */
@Serializable
data class MessageStatsResponse(
    val enabled: Boolean = false,
    val dailyMessages: Int = 0,
    val totalMessages: Int = 0,
    val topUsers: List<TopUserEntry> = emptyList(),
    val topChannels: List<TopChannelEntry> = emptyList(),
)

/** A high-volume poster in the guild message leaderboard. */
@Serializable
data class TopUserEntry(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val totalMessages: Int = 0,
    val dailyMessages: Int = 0,
)

/** A high-volume channel in the guild message leaderboard. */
@Serializable
data class TopChannelEntry(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String = "Unknown",
    val totalMessages: Int = 0,
)

/** Daily-only message count summary. */
@Serializable
data class DailyMessageStats(
    val enabled: Boolean = false,
    val dailyMessages: Int = 0,
    val totalMessages: Int = 0,
)

// endregion

// region Birthdays

/** A guild member with an upcoming birthday. */
@Serializable
data class BirthdayUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val daysUntil: Int = 0,
)

// endregion

// region Tickets

/** Ticket volume counters for a guild. */
@Serializable
data class TicketGuildStatistics(
    val totalTickets: Int = 0,
    val openTickets: Int = 0,
    val closedTickets: Int = 0,
)

/** Minimal ticket panel shape; only the identity is used for counting. */
@Serializable
data class TicketPanelSummary(val id: Int = 0)

// endregion

// region Starboard

/** A highly-starred message surfaced on the guild starboard. */
@Serializable
data class StarboardHighlight(
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val starCount: Int = 0,
    val content: String? = null,
    val authorName: String = "Unknown",
    val authorAvatarUrl: String? = null,
    val imageUrl: String? = null,
    val starEmote: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
)

// endregion

// region Forms

/** Summary row for a configured form. */
@Serializable
data class FormSummary(
    val id: Int = 0,
    val name: String = "Form",
    val isActive: Boolean = false,
    val responseCount: Int? = null,
)

// endregion

// region Counting

/** A channel configured for the counting game. */
@Serializable
data class CountingChannel(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    val currentNumber: Int = 0,
    val highestNumber: Int = 0,
    val isActive: Boolean = false,
    val lastUsername: String? = null,
)

// endregion

// region Patreon

/** Whether Patreon integration is configured for the guild. */
@Serializable
data class PatreonStatus(val isConfigured: Boolean = false)

/** A single Patreon supporter. */
@Serializable
data class PatreonSupporterEntry(
    @Serializable(with = SnowflakeSerializer::class) val id: String = "",
    val fullName: String? = null,
    val amountCents: Int? = null,
)

// endregion

// region Giveaways

/** Summary row for a giveaway. */
@Serializable
data class GiveawaySummary(
    val id: Int = 0,
    val item: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val `when`: String? = null,
    val ended: Int? = null,
    val winners: Int? = null,
)

// endregion

// region Moderation

/** A warning issued against a guild member. */
@Serializable
data class WarningRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val reason: String? = null,
    val moderator: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val forgiven: Boolean = false,
)

// endregion

// region Protection

/** One protection module's enablement flag. */
@Serializable
data class ProtectionBranch(val enabled: Boolean? = null)

/** Enablement state of each anti-abuse protection module. */
@Serializable
data class ProtectionStatusRaw(
    val antiRaid: ProtectionBranch? = null,
    val antiSpam: ProtectionBranch? = null,
    val antiAlt: ProtectionBranch? = null,
    val antiMassMention: ProtectionBranch? = null,
) {
    /** Flattens the nested wire shape into a view-friendly summary. */
    fun toStatus(): ProtectionStatus = ProtectionStatus(
        antiRaidEnabled = antiRaid?.enabled == true,
        antiSpamEnabled = antiSpam?.enabled == true,
        antiAltEnabled = antiAlt?.enabled == true,
        antiMassMentionEnabled = antiMassMention?.enabled == true,
    )
}

/** Flattened protection state used by the UI. */
data class ProtectionStatus(
    val antiRaidEnabled: Boolean = false,
    val antiSpamEnabled: Boolean = false,
    val antiAltEnabled: Boolean = false,
    val antiMassMentionEnabled: Boolean = false,
) {
    /** How many protection modules are currently enabled. */
    val activeCount: Int
        get() = listOf(
            antiRaidEnabled,
            antiSpamEnabled,
            antiAltEnabled,
            antiMassMentionEnabled,
        ).count { it }
}

// endregion

// region Logging

/** Raw logging configuration as returned by the bot. */
@Serializable
data class LoggingConfigRaw(
    val enabled: Boolean = false,
    val logTypes: Map<String, JsonElement> = emptyMap(),
    val ignoredChannels: List<JsonElement> = emptyList(),
) {
    /** Reduces the raw map into the counts the UI displays. */
    fun toConfig(): LoggingConfig = LoggingConfig(
        enabled = enabled,
        totalLogTypes = logTypes.size,
        configuredChannels = logTypes.values.count { element ->
            element !is JsonNull && (element as? JsonPrimitive)?.content.let { it != null && it != "0" }
        },
        ignoredChannels = ignoredChannels.size,
    )
}

/** Logging counters used by the UI. */
data class LoggingConfig(
    val enabled: Boolean = false,
    val configuredChannels: Int = 0,
    val totalLogTypes: Int = 0,
    val ignoredChannels: Int = 0,
)

// endregion

// region Roles

/** Roles auto-applied to joining members and bots. */
@Serializable
data class AutoAssignRolesResponse(
    val normalRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val botRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
)

/** A role members may assign to themselves. */
@Serializable
data class SelfAssignableRoleEntry(
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val group: Int? = null,
)

// endregion

// region Guild config

/** The subset of guild configuration the app reads. */
@Serializable
data class GuildConfigSummary(
    val prefix: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val afkChannel: Snowflake? = null,
    val patreonMessage: String? = null,
)

// endregion
