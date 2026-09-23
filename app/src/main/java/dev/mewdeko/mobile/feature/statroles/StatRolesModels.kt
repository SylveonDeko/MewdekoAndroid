package dev.mewdeko.mobile.feature.statroles

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** What a stat role measures. Mirrors `StatRoleStat` on the bot, which is numeric on the wire. */
enum class StatRoleStat(val value: Int, val label: String, val blurb: String) {
    MESSAGES(0, "Messages", "Messages sent in the window"),
    VOICE_MINUTES(1, "Voice minutes", "Minutes spent in voice in the window"),
    INVITES(2, "Invites", "Net invites, all time"),
    JOINED_DAYS(3, "Days in server", "Days since the member joined"),
    ACCOUNT_DAYS(4, "Account age (days)", "Days since the account was created"),
    ACTIVITY_MINUTES(5, "Minutes in a game", "Minutes in one game or app, or any, in the window");

    /** Whether the stat is measured over a lookback window, which also makes daily streaks possible. */
    val usesWindow: Boolean get() = this == MESSAGES || this == VOICE_MINUTES || this == ACTIVITY_MINUTES

    /** Whether the stat can be limited to specific channels. */
    val supportsChannelFilter: Boolean get() = this == MESSAGES || this == VOICE_MINUTES

    companion object {
        /** Resolves a wire value, defaulting to messages. */
        fun from(value: Int): StatRoleStat = entries.firstOrNull { it.value == value } ?: MESSAGES
    }
}

/** How members qualify for a stat role. Mirrors `StatRoleLimit` on the bot. */
enum class StatRoleLimit(val value: Int, val label: String, val blurb: String) {
    THRESHOLD(0, "Threshold", "Value between a minimum and an optional maximum"),
    TOP_RANK(1, "Top rank", "Ranked between two positions"),
    TOP_PERCENT(2, "Top percent", "Ranked within a percentile band"),
    DAILY_STREAK(3, "Daily streak", "Met a per day minimum on enough days");

    companion object {
        /** Resolves a wire value, defaulting to threshold. */
        fun from(value: Int): StatRoleLimit = entries.firstOrNull { it.value == value } ?: THRESHOLD
    }
}

/** A stat role as returned by `GET StatRoles/{guildId}`. */
@Serializable
data class StatRole(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val name: String = "",
    val enabled: Boolean = false,
    val statType: Int = 0,
    val limitType: Int = 0,
    val minimum: Long = 0,
    val maximum: Long? = null,
    val lookbackDays: Int = 0,
    val topStart: Int = 1,
    val topEnd: Int = 10,
    val requiredDays: Int = 0,
    val permanent: Boolean = false,
    val invert: Boolean = false,
    val applyToBots: Boolean = false,
    val groupName: String? = null,
    val activityName: String? = null,
    val channelFilter: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val roleWhitelist: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val roleBlacklist: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val ignoredUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    @Serializable(with = SnowflakeSerializer::class) val notifyChannelId: Snowflake? = null,
    val notifyDm: Boolean = false,
    val notifyMessage: String? = null,
    val intervalMinutes: Int = 180,
    @Serializable(with = InstantSerializer::class) val lastRunAt: Instant? = null,
    val condition: String = "",
) {
    /** The announcement channel, ignoring the zero id the bot may emit for none. */
    val announceChannelId: Snowflake? get() = notifyChannelId?.takeIf { it.isNotEmpty() && it != "0" }

    /** The last evaluation, or `null` when the role has never run. */
    val lastRun: Instant? get() = lastRunAt?.takeIf { it != Instant.EPOCH }
}

/** One member in a preview or run result. */
@Serializable
data class StatRoleMember(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val value: Long = 0,
    val rank: Int? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
)

/** The result of `POST StatRoles/{guildId}/{id}/preview` or `/run`. */
@Serializable
data class StatRoleRunResult(
    val statRoleId: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val qualifyingCount: Int = 0,
    val qualifying: List<StatRoleMember> = emptyList(),
    val toGrant: List<StatRoleMember> = emptyList(),
    val toRemove: List<StatRoleMember> = emptyList(),
    val granted: Int = 0,
    val removed: Int = 0,
    val failed: Int = 0,
)

/** A preview or run result pinned under its stat role card. */
data class StatRoleResultPanel(
    val result: StatRoleRunResult,
    val preview: Boolean,
)

/**
 * Editable copy of a stat role for the create and edit form. Numeric fields
 * are kept as text so a half-typed value never snaps back while editing.
 */
data class StatRoleDraft(
    val id: Int? = null,
    val roleId: Snowflake? = null,
    val name: String = "",
    val statType: StatRoleStat = StatRoleStat.MESSAGES,
    val limitType: StatRoleLimit = StatRoleLimit.THRESHOLD,
    val minimum: String = "100",
    val maximum: String = "",
    val lookbackDays: Int = 30,
    val topStart: String = "1",
    val topEnd: String = "10",
    val requiredDays: String = "5",
    val permanent: Boolean = false,
    val invert: Boolean = false,
    val applyToBots: Boolean = false,
    val groupName: String = "",
    val activityName: String = "",
    val channelFilter: List<Snowflake> = emptyList(),
    val roleWhitelist: List<Snowflake> = emptyList(),
    val roleBlacklist: List<Snowflake> = emptyList(),
    val ignoredUsers: List<Snowflake> = emptyList(),
    val notifyChannelId: Snowflake? = null,
    val notifyDm: Boolean = false,
    val notifyMessage: String = "",
    val intervalMinutes: String = "180",
) {
    /** Whether this draft creates a new stat role rather than editing one. */
    val isNew: Boolean get() = id == null

    /** Whether the chosen limit is a daily streak on a stat that cannot support one. */
    val streakInvalid: Boolean get() = limitType == StatRoleLimit.DAILY_STREAK && !statType.usesWindow

    companion object {
        /** Maximum length of the display name. */
        const val NAME_MAX = 64

        /** Maximum length of the group name. */
        const val GROUP_MAX = 32

        /** Maximum length of the game or app name. */
        const val ACTIVITY_MAX = 128

        /** Smallest evaluation interval the bot accepts. */
        const val MIN_INTERVAL = 10

        /** Longest lookback window the bot accepts. */
        const val MAX_LOOKBACK = 90

        /** Builds a draft from an existing stat role. */
        fun from(role: StatRole): StatRoleDraft = StatRoleDraft(
            id = role.id,
            roleId = role.roleId.takeIf { it.isNotEmpty() && it != "0" },
            name = role.name,
            statType = StatRoleStat.from(role.statType),
            limitType = StatRoleLimit.from(role.limitType),
            minimum = role.minimum.toString(),
            maximum = role.maximum?.toString().orEmpty(),
            lookbackDays = role.lookbackDays.coerceIn(0, MAX_LOOKBACK),
            topStart = role.topStart.toString(),
            topEnd = role.topEnd.toString(),
            requiredDays = role.requiredDays.toString(),
            permanent = role.permanent,
            invert = role.invert,
            applyToBots = role.applyToBots,
            groupName = role.groupName.orEmpty(),
            activityName = role.activityName.orEmpty(),
            channelFilter = role.channelFilter,
            roleWhitelist = role.roleWhitelist,
            roleBlacklist = role.roleBlacklist,
            ignoredUsers = role.ignoredUsers,
            notifyChannelId = role.announceChannelId,
            notifyDm = role.notifyDm,
            notifyMessage = role.notifyMessage.orEmpty(),
            intervalMinutes = role.intervalMinutes.toString(),
        )
    }
}
