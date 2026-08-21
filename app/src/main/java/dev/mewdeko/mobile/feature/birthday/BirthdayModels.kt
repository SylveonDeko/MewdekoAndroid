package dev.mewdeko.mobile.feature.birthday

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** One toggleable birthday subsystem, as a bit in the config's feature mask. */
enum class BirthdayFeature(
    val bit: Int,
    val label: String,
    val blurb: String,
    val icon: ImageVector,
) {
    ANNOUNCEMENTS(1, "Announcements", "Send birthday messages in the configured channel", Icons.Default.Campaign),
    BIRTHDAY_ROLE(2, "Birthday Role", "Assign a temporary role for 24 hours", Icons.Default.WorkspacePremium),
    REMINDERS(4, "Reminders", "DM users ahead of their own birthday", Icons.Default.Notifications),
    PING_ROLE(8, "Ping Role", "Mention the configured role with announcements", Icons.Default.AlternateEmail),
    TIMEZONE_SUPPORT(16, "Timezone Support", "Trigger birthdays in each member's timezone", Icons.Default.Public);

    /** Whether this feature's bit is set in [mask]. */
    fun isEnabled(mask: Int): Boolean = mask and bit != 0
}

/** Birthday configuration returned by `GET /birthday/{guildId}/config`. */
@Serializable
data class BirthdayConfig(
    @Serializable(with = SnowflakeSerializer::class) val birthdayChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val birthdayRoleId: Snowflake? = null,
    val birthdayMessage: String = "",
    @Serializable(with = SnowflakeSerializer::class) val birthdayPingRoleId: Snowflake? = null,
    val birthdayReminderDays: Int = 1,
    val defaultTimezone: String = "UTC",
    val enabledFeatures: Int = 0,
)

/** Aggregate guild-wide birthday stats. */
@Serializable
data class BirthdayStats(
    val totalUsers: Int = 0,
    val usersWithBirthdays: Int = 0,
    val usersWithAnnouncementsEnabled: Int = 0,
    val todaysBirthdayCount: Int = 0,
    val birthdaySetPercentage: Double = 0.0,
)

/** Full birthday record for one member. */
@Serializable
data class BirthdayUserDetail(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val nickname: String? = null,
    val avatarUrl: String? = null,
    @Serializable(with = InstantSerializer::class) val birthday: Instant? = null,
    val birthdayDisplayMode: Int = 0,
    val birthdayAnnouncementsEnabled: Boolean = false,
    val birthdayTimezone: String? = null,
    val daysUntil: Int? = null,
) {
    /** The name to show in lists, preferring the guild nickname. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
}

/** Common timezone presets offered in the picker. */
data class BirthdayTimezone(val id: String, val label: String) {
    companion object {
        /** The timezone choices offered in the picker. */
        val presets = listOf(
            BirthdayTimezone("UTC", "UTC (GMT+0)"),
            BirthdayTimezone("America/New_York", "Eastern Time (GMT-5)"),
            BirthdayTimezone("America/Chicago", "Central Time (GMT-6)"),
            BirthdayTimezone("America/Denver", "Mountain Time (GMT-7)"),
            BirthdayTimezone("America/Los_Angeles", "Pacific Time (GMT-8)"),
            BirthdayTimezone("Europe/London", "London (GMT+0)"),
            BirthdayTimezone("Europe/Paris", "Paris (GMT+1)"),
            BirthdayTimezone("Europe/Berlin", "Berlin (GMT+1)"),
            BirthdayTimezone("Europe/Moscow", "Moscow (GMT+3)"),
            BirthdayTimezone("Asia/Tokyo", "Tokyo (GMT+9)"),
            BirthdayTimezone("Asia/Shanghai", "Shanghai (GMT+8)"),
            BirthdayTimezone("Asia/Dubai", "Dubai (GMT+4)"),
            BirthdayTimezone("Australia/Sydney", "Sydney (GMT+10)"),
        )
    }
}
