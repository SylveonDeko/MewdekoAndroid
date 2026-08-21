package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A user-defined highlight word that pings the user when seen. */
@Serializable
data class UserHighlight(
    val id: Int = 0,
    val word: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** Highlight engagement settings for a user in a guild. */
@Serializable
data class HighlightSettings(
    val highlightsEnabled: Boolean = false,
    val ignoredChannels: List<String> = emptyList(),
    val ignoredUsers: List<String> = emptyList(),
)

/** AFK presence and message for a user in a guild. */
@Serializable
data class AfkStatus(
    val isAfk: Boolean = false,
    val message: String = "",
    @Serializable(with = InstantSerializer::class) val `when`: Instant? = null,
    val wasTimed: Boolean = false,
)

/** Reputation totals and streak metadata for a user. */
@Serializable
data class UserReputation(
    val totalRep: Int = 0,
    val rank: Int = 0,
    val totalGiven: Int = 0,
    val totalReceived: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastGivenAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastReceivedAt: Instant? = null,
)

/** Cross-guild user preferences. */
@Serializable
data class UserPreferences(
    val levelUpPingsDisabled: Boolean = false,
    val pronounsDisabled: Boolean = false,
    val prefersGuidedSetup: Boolean = false,
    val dashboardExperienceLevel: Int = 0,
    val hasCompletedAnyWizard: Boolean = false,
)

/** User-supplied profile fields and birthday settings. */
@Serializable
data class UserProfile(
    val bio: String = "",
    val zodiacSign: String = "",
    val profilePrivacy: Int = 0,
    val birthdayDisplayMode: Int = 0,
    val greetDmsOptOut: Boolean = false,
    val statsOptOut: Boolean = false,
    @Serializable(with = InstantSerializer::class) val birthday: Instant? = null,
    val birthdayTimezone: String = "",
    val birthdayAnnouncementsEnabled: Boolean = false,
    val profileColor: Long? = null,
    val profileImageUrl: String = "",
    val switchFriendCode: String = "",
    val pronouns: String = "",
)

/** A suggestion submitted by the user with vote counts. */
@Serializable
data class MySuggestion(
    val id: Int = 0,
    val suggestionId: Int? = null,
    val suggestion1: String? = null,
    val currentState: Int = 0,
    val stateName: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val emoteCount1: Int = 0,
    val emoteCount2: Int = 0,
    val emoteCount3: Int = 0,
    val emoteCount4: Int = 0,
    val emoteCount5: Int = 0,
)

/** Currency balance plus a window of recent transactions. */
@Serializable
data class CurrencyData(
    val balance: Long = 0L,
    val recentTransactions: List<CurrencyTransaction> = emptyList(),
)

/** A single currency transaction. */
@Serializable
data class CurrencyTransaction(
    val id: Int = 0,
    val amount: Long = 0L,
    val description: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A giveaway the user entered. */
@Serializable
data class MyGiveawayEntry(
    val id: Int = 0,
    val item: String? = null,
    val winnerCount: Int = 1,
    @Serializable(with = InstantSerializer::class) val `when`: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val isEnded: Boolean = false,
    @Serializable(with = InstantSerializer::class) val entryDate: Instant? = null,
)

/** A scheduled reminder. */
@Serializable
data class MyReminder(
    val id: Int = 0,
    val message: String? = null,
    @Serializable(with = InstantSerializer::class) val `when`: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val serverId: Snowflake? = null,
    val isExpired: Boolean = false,
)

/** Cross-guild user analytics. */
@Serializable
data class UserAnalytics(
    val totalServers: Int = 0,
    val xpData: List<XpEntry> = emptyList(),
    val globalBalance: Long = 0L,
    val totalSuggestions: Int = 0,
    val recentActivity: RecentActivity = RecentActivity(),
)

/** Per-guild XP entry within [UserAnalytics]. */
@Serializable
data class XpEntry(
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val guildName: String = "Unknown",
    val totalXp: Long = 0L,
    val level: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastActivity: Instant? = null,
)

/** Most-recent activity timestamps surfaced in [UserAnalytics]. */
@Serializable
data class RecentActivity(
    @Serializable(with = InstantSerializer::class) val lastAfkSet: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastSuggestion: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastXpGain: Instant? = null,
)

/** Invite stats for a user in a guild. */
@Serializable
data class InviteStats(
    val inviteCount: Int = 0,
    val invitedUsers: List<InvitedUser> = emptyList(),
)

/** A user invited by the signed-in user. */
@Serializable
data class InvitedUser(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "",
    val displayName: String = "",
    val joinedAt: String = "Unknown",
)

/** Aggregate message stats and per-channel breakdown for a user. */
@Serializable
data class MessageStats(
    val totalMessages: Int = 0,
    val enabled: Boolean = false,
    val channelBreakdown: List<ChannelMessageStat> = emptyList(),
)

/** Per-channel message activity for a user. */
@Serializable
data class ChannelMessageStat(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String = "Unknown",
    val count: Int = 0,
    val lastActivity: String = "",
)

/** Starboard contribution statistics for a user. */
@Serializable
data class StarboardStats(
    val starsGiven: Int? = null,
    val starsReceived: Int? = null,
    val postsOnStarboard: Int? = null,
    val topPostStars: Int? = null,
)

/** Wire shape for adding a new highlight. */
@Serializable
data class AddHighlightResponse(
    val word: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** Toggle response for a single boolean preference. */
@Serializable
data class PreferenceToggleResponse(
    val levelUpPingsDisabled: Boolean? = null,
    val pronounsDisabled: Boolean? = null,
    val prefersGuidedSetup: Boolean? = null,
    val greetDmsOptOut: Boolean? = null,
    val statsOptOut: Boolean? = null,
    val birthdayAnnouncementsEnabled: Boolean? = null,
)

/** Stored role state for a single user. */
@Serializable
data class RoleStateRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val savedRoles: String? = null,
)

/** Configured role greet entry. */
@Serializable
data class RoleGreet(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val message: String? = null,
    val disabled: Boolean? = null,
)
