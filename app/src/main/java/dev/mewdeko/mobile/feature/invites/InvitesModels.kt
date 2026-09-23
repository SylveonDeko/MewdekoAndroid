package dev.mewdeko.mobile.feature.invites

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Time window for invite leaderboards and analytics. Mirrors `StatsRange` (numeric on the wire). */
enum class InviteStatsRange(val value: Int, val label: String) {
    ALL_TIME(0, "All time"),
    DAILY(1, "Last 24 hours"),
    WEEKLY(2, "Last 7 days"),
    MONTHLY(3, "Last 30 days");

    companion object {
        /** Resolves a stored value, defaulting to all time. */
        fun from(value: Int): InviteStatsRange = entries.firstOrNull { it.value == value } ?: ALL_TIME
    }
}

/** Kinds of invite tracking exclusion. Mirrors `InviteExclusionKind` (numeric on the wire). */
enum class InviteExclusionKind(val value: Int, val label: String, val note: String) {
    BLACKLISTED_USER(0, "Blacklisted inviters", "Never earn invite credit"),
    BLACKLISTED_ROLE(1, "Blacklisted roles", "Holders never earn invite credit"),
    HIDDEN_USER(2, "Hidden from leaderboard", "Still tracked, never shown");
}

/** Scope of an invite reset. Mirrors `InviteResetScope` (numeric on the wire). */
enum class InviteResetScope(val value: Int) {
    SERVER(0),
    LEFT_MEMBERS(1),
}

/** Invite tracking configuration returned by `GET settings`. */
@Serializable
data class InviteSettings(
    val isEnabled: Boolean = false,
    val removeInviteOnLeave: Boolean = false,
    val minAccountAge: String = "0.00:00:00",
    val countRejoins: Boolean = true,
    val fakeOnNoAvatar: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val linkChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val logChannelId: Snowflake? = null,
)

/** One entry in the invite leaderboard, also used for the analytics top-inviters list. */
@Serializable
data class InviteLeaderboardEntry(
    val rank: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val avatarUrl: String? = null,
    val total: Int = 0,
    val regular: Int = 0,
    val left: Int = 0,
    val fake: Int = 0,
    val bonus: Int = 0,
    val retention: Double? = null,
    @Serializable(with = InstantSerializer::class) val latestJoinAt: Instant? = null,
)

/** A lightweight guild member reference used for the inviter and member pickers. */
@Serializable
data class InviteMemberLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "Unknown",
    val displayName: String = "Unknown",
    val avatarUrl: String? = null,
    val isBot: Boolean = false,
)

/** A lightweight user reference returned by the inviter and invited-users endpoints. */
@Serializable
data class InviteUserLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "Unknown",
    val discriminator: String? = null,
    val avatarUrl: String? = null,
)

/** Who invited a member and how they arrived. */
@Serializable
data class InviterInfo(
    val inviter: InviteUserLite? = null,
    val inviteCode: String? = null,
    val joinType: String = "Unknown",
    val isFake: Boolean = false,
    val fakeReason: String = "None",
    @Serializable(with = InstantSerializer::class) val joinedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val leftAt: Instant? = null,
)

/** A member's full invite breakdown and rank. */
@Serializable
data class InviteBreakdown(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val total: Int = 0,
    val regular: Int = 0,
    val left: Int = 0,
    val fake: Int = 0,
    val bonus: Int = 0,
    val rank: Int? = null,
)

/** One witnessed join. */
@Serializable
data class InvitedRecord(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val avatarUrl: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val inviterId: Snowflake = "",
    val inviteCode: String? = null,
    val joinType: String = "Unknown",
    val isFake: Boolean = false,
    val fakeReason: String = "None",
    @Serializable(with = InstantSerializer::class) val joinedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val leftAt: Instant? = null,
)

/** A page of witnessed joins. */
@Serializable
data class InvitedPage(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 25,
    val items: List<InvitedRecord> = emptyList(),
)

/** An invite code with its label and use count, used by analytics. */
@Serializable
data class InviteCodeSummary(
    val code: String = "",
    val label: String? = null,
    val joins: Int = 0,
)

/** One day of joins and leaves in the growth series. */
@Serializable
data class GrowthPoint(
    val day: String = "",
    val joins: Int = 0,
    val leaves: Int = 0,
)

/** The source split of witnessed joins in a window. */
@Serializable
data class InviteSources(
    val invite: Int = 0,
    val vanity: Int = 0,
    val bot: Int = 0,
    val unknown: Int = 0,
)

/** Growth analytics for a window. */
@Serializable
data class InviteAnalytics(
    val range: Int = 0,
    val joins: Int = 0,
    val leaves: Int = 0,
    val netGrowth: Int = 0,
    val fakeJoins: Int = 0,
    val stayed: Int = 0,
    val retention: Double? = null,
    val sources: InviteSources = InviteSources(),
    val topCodes: List<InviteCodeSummary> = emptyList(),
    val topInviters: List<InviteLeaderboardEntry> = emptyList(),
    val series: List<GrowthPoint> = emptyList(),
)

/** A Discord invite code in the guild, with its label and credited owner. */
@Serializable
data class GuildInviteCode(
    val code: String = "",
    val url: String = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val inviterId: Snowflake? = null,
    val inviterName: String? = null,
    val uses: Int = 0,
    val maxUses: Int? = null,
    val maxAge: Int? = null,
    val isTemporary: Boolean = false,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val label: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val labelRoleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val ownerUserId: Snowflake? = null,
)

/** A labelled invite code, including labels for codes that no longer exist in the guild. */
@Serializable
data class InviteLabel(
    val id: Int = 0,
    val inviteCode: String = "",
    val label: String = "",
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val ownerUserId: Snowflake? = null,
)

/** Editable draft of a code's label and role-on-join, keyed by invite code. */
data class LabelDraft(
    val label: String = "",
    val roleId: Snowflake? = null,
    val dirty: Boolean = false,
)

/** CSV content staged for the share sheet. */
data class PendingExport(val filename: String, val content: String)
