package dev.mewdeko.mobile.feature.dashboardaccess

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Whether an access grant or manager entry targets a single user or everyone holding a role. */
enum class AccessTargetType(val value: Int, val label: String) {
    USER(0, "User"),
    ROLE(1, "Role");

    companion object {
        /** Maps the bot's numeric `DashboardAccessTargetType` to a case, defaulting to [USER]. */
        fun from(value: Int): AccessTargetType = entries.firstOrNull { it.value == value } ?: USER
    }
}

/** How much a grant allows for one dashboard section, mirroring `DashboardAccessLevel`. */
enum class AccessLevel(val value: Int, val label: String) {
    NONE(0, "None"),
    VIEW(1, "View"),
    MANAGE(2, "Manage");

    companion object {
        /** Maps the bot's numeric level to a case, defaulting to [NONE]. */
        fun from(value: Int): AccessLevel = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/** Response of `GET api/DashboardAccess/{guildId}/settings`. */
@Serializable
data class DashboardAccessSettings(
    val adminsCanManageAccess: Boolean = false,
    val canManageAccess: Boolean = false,
    val isGuildOwner: Boolean = false,
)

/** One explicit access-list manager from `GET api/DashboardAccess/{guildId}/managers`. */
@Serializable
data class DashboardAccessManager(
    val id: Int = 0,
    val targetType: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val targetId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val grantedBy: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The typed target kind. */
    val type: AccessTargetType get() = AccessTargetType.from(targetType)
}

/**
 * One restricted access grant from `GET api/DashboardAccess/{guildId}/grants`.
 *
 * [sections] maps a bot controller name to its numeric level. The bot does not
 * camelCase dictionary keys, so grants are decoded without key normalisation
 * and then passed through [canonicalSections].
 */
@Serializable
data class DashboardAccessGrant(
    val id: Int = 0,
    val targetType: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val targetId: Snowflake = "",
    val sections: Map<String, Int> = emptyMap(),
) {
    /** The typed target kind. */
    val type: AccessTargetType get() = AccessTargetType.from(targetType)

    /** Section levels keyed by the canonical controller name, dropping anything at [AccessLevel.NONE]. */
    fun canonicalSections(): Map<String, AccessLevel> = sections.entries
        .mapNotNull { (key, value) ->
            val level = AccessLevel.from(value)
            if (level == AccessLevel.NONE) null
            else DashboardAccessSections.canonicalName(key) to level
        }
        .toMap()
}

/** One grantable dashboard feature and the bot API controllers it depends on. */
data class DashboardAccessGroup(
    val label: String,
    val category: AccessCategory,
    val icon: ImageVector,
    val sections: List<String>,
)

/** Dashboard feature categories, in the dashboard's display order. */
enum class AccessCategory(val label: String) {
    COMMUNITY("Community"),
    ENTERTAINMENT("Entertainment"),
    ACTIONS("Actions"),
    SECURITY("Security"),
    ANALYTICS("Analytics"),
    SETTINGS("Settings"),
}

/**
 * The grantable dashboard features, mirroring `dashboardAccessSections.ts` on
 * the web dashboard (every non-owner nav item that maps to a controller).
 */
object DashboardAccessSections {

    /** Every grantable feature, in the dashboard's navigation order. */
    val groups: List<DashboardAccessGroup> = listOf(
        DashboardAccessGroup("Administration", AccessCategory.SECURITY, Icons.Default.AdminPanelSettings, listOf("Administration", "Protection")),
        DashboardAccessGroup("AFK System", AccessCategory.ACTIONS, Icons.Default.DarkMode, listOf("Afk")),
        DashboardAccessGroup("Audit Log", AccessCategory.SECURITY, Icons.Default.History, listOf("AuditLog")),
        DashboardAccessGroup("Channel Access", AccessCategory.SECURITY, Icons.Default.LockOpen, listOf("ChannelAccess")),
        DashboardAccessGroup("Birthdays", AccessCategory.COMMUNITY, Icons.Default.Cake, listOf("Birthday")),
        DashboardAccessGroup("Word of the Day", AccessCategory.COMMUNITY, Icons.AutoMirrored.Filled.MenuBook, listOf("WordOfTheDay")),
        DashboardAccessGroup("Chat Saver", AccessCategory.SECURITY, Icons.Default.Storage, listOf("Chat")),
        DashboardAccessGroup("Confessions", AccessCategory.COMMUNITY, Icons.Default.Lock, listOf("Confessions")),
        DashboardAccessGroup("Counting", AccessCategory.COMMUNITY, Icons.Default.Numbers, listOf("Counting")),
        DashboardAccessGroup("Currency", AccessCategory.ENTERTAINMENT, Icons.Default.Payments, listOf("Currency")),
        DashboardAccessGroup("Minecraft", AccessCategory.ENTERTAINMENT, Icons.Default.Widgets, listOf("Minecraft")),
        DashboardAccessGroup("Stat Channels", AccessCategory.COMMUNITY, Icons.Default.Equalizer, listOf("StatChannel")),
        DashboardAccessGroup("Custom Voice", AccessCategory.ENTERTAINMENT, Icons.Default.Mic, listOf("CustomVoice")),
        DashboardAccessGroup("Embeds", AccessCategory.ACTIONS, Icons.Default.ViewAgenda, listOf("Embeds")),
        DashboardAccessGroup("Feeds", AccessCategory.ACTIONS, Icons.Default.RssFeed, listOf("Feeds")),
        DashboardAccessGroup("Forms", AccessCategory.COMMUNITY, Icons.Default.Description, listOf("Forms")),
        DashboardAccessGroup("Giveaways", AccessCategory.ENTERTAINMENT, Icons.Default.CardGiftcard, listOf("Giveaways")),
        DashboardAccessGroup("Greets", AccessCategory.ACTIONS, Icons.Default.WavingHand, listOf("MultiGreet")),
        DashboardAccessGroup("Highlights", AccessCategory.COMMUNITY, Icons.Default.NotificationsActive, listOf("Highlights")),
        DashboardAccessGroup("Invites", AccessCategory.COMMUNITY, Icons.Default.Groups, listOf("InviteTracking")),
        DashboardAccessGroup("Activity Stats", AccessCategory.ANALYTICS, Icons.Default.QueryStats, listOf("ServerStats")),
        DashboardAccessGroup("Stat Roles", AccessCategory.COMMUNITY, Icons.Default.Leaderboard, listOf("StatRoles")),
        DashboardAccessGroup("Live Boards", AccessCategory.COMMUNITY, Icons.Default.Dashboard, listOf("LiveBoards")),
        DashboardAccessGroup("Logging", AccessCategory.SECURITY, Icons.AutoMirrored.Filled.ManageSearch, listOf("Logging")),
        DashboardAccessGroup("Message Stats", AccessCategory.ANALYTICS, Icons.Default.MarkEmailUnread, listOf("MessageCount")),
        DashboardAccessGroup("Message Filters", AccessCategory.SECURITY, Icons.Default.FilterAlt, listOf("Filter")),
        DashboardAccessGroup("Polls", AccessCategory.COMMUNITY, Icons.Default.Poll, listOf("Poll")),
        DashboardAccessGroup("Utilities", AccessCategory.ACTIONS, Icons.Default.Build, listOf("Utility", "Guild")),
        DashboardAccessGroup("Moderation", AccessCategory.SECURITY, Icons.Default.Shield, listOf("Moderation", "Protection")),
        DashboardAccessGroup("Music", AccessCategory.ENTERTAINMENT, Icons.Default.MusicNote, listOf("Music")),
        DashboardAccessGroup("Patreon", AccessCategory.COMMUNITY, Icons.Default.Favorite, listOf("Patreon")),
        DashboardAccessGroup("Repeaters", AccessCategory.ACTIONS, Icons.Default.Repeat, listOf("Repeaters")),
        DashboardAccessGroup("Reputation", AccessCategory.COMMUNITY, Icons.Default.EmojiEvents, listOf("Reputation")),
        DashboardAccessGroup("Role Greets", AccessCategory.ACTIONS, Icons.Default.PersonAddAlt, listOf("RoleGreet")),
        DashboardAccessGroup("Role Menus", AccessCategory.ACTIONS, Icons.AutoMirrored.Filled.PlaylistAddCheck, listOf("RoleMenus")),
        DashboardAccessGroup("Role States", AccessCategory.ACTIONS, Icons.Default.Sync, listOf("RoleStates")),
        DashboardAccessGroup("Settings", AccessCategory.SETTINGS, Icons.Default.Tune, listOf("Guild")),
        DashboardAccessGroup("Starboard", AccessCategory.COMMUNITY, Icons.Default.Star, listOf("Starboard")),
        DashboardAccessGroup("Status Roles", AccessCategory.ACTIONS, Icons.AutoMirrored.Filled.VolumeUp, listOf("StatusRoles")),
        DashboardAccessGroup("Stream Alerts", AccessCategory.COMMUNITY, Icons.Default.VideoCameraFront, listOf("StreamNotifications")),
        DashboardAccessGroup("Twitch Bot", AccessCategory.COMMUNITY, Icons.Default.LiveTv, listOf("Twitch")),
        DashboardAccessGroup("Suggestions", AccessCategory.COMMUNITY, Icons.Default.TipsAndUpdates, listOf("Suggestions")),
        DashboardAccessGroup("Tickets", AccessCategory.COMMUNITY, Icons.Default.ConfirmationNumber, listOf("Ticket")),
        DashboardAccessGroup("Todo Lists", AccessCategory.COMMUNITY, Icons.Default.Checklist, listOf("Todo")),
        DashboardAccessGroup("Triggers", AccessCategory.ACTIONS, Icons.Default.Bolt, listOf("ChatTriggers")),
        DashboardAccessGroup("Votes", AccessCategory.COMMUNITY, Icons.Default.ThumbUp, listOf("Votes")),
        DashboardAccessGroup("XP System", AccessCategory.COMMUNITY, Icons.Default.Insights, listOf("Xp")),
    )

    /** Features grouped by category, in category display order, skipping empty categories. */
    val grouped: List<Pair<AccessCategory, List<DashboardAccessGroup>>> =
        AccessCategory.entries.mapNotNull { category ->
            val entries = groups.filter { it.category == category }
            if (entries.isEmpty()) null else category to entries
        }

    private val canonicalByLower: Map<String, String> =
        groups.flatMap { it.sections }.associateBy { it.lowercase() }

    /**
     * Restores a section name's canonical casing, so a key that came back
     * lowercased still lines up with the feature rows. Unknown names pass
     * through unchanged so they are preserved when the grant is resaved.
     */
    fun canonicalName(section: String): String = canonicalByLower[section.lowercase()] ?: section
}
