package dev.mewdeko.mobile.navigation

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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
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

/** Grouping used by the feature browser. */
enum class FeatureCategory(val label: String) {
    COMMUNITY("Community"),
    ENTERTAINMENT("Entertainment"),
    ACTIONS("Actions"),
    SECURITY("Security"),
    ANALYTICS("Analytics"),
    SETTINGS("Settings");

    companion object {
        /** Fixed display order for the feature browser's category sections. */
        val order = listOf(COMMUNITY, ENTERTAINMENT, ACTIONS, SECURITY, ANALYTICS, SETTINGS)
    }
}

/** One feature page shown in the global feature browser. */
data class FeatureCatalogItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val category: FeatureCategory,
    val summary: String,
    val ownerOnly: Boolean = false,
    /**
     * Extra search terms for features whose everyday name does not appear in
     * [label] or [summary], such as "auto role" for Administration's
     * self-assignable roles.
     */
    val keywords: List<String> = emptyList(),
)

/**
 * The canonical list of every per-guild feature page, grouped into
 * categories.
 */
object NavigationCatalog {

    /** Every feature page, in catalog order. */
    val items: List<FeatureCatalogItem> = listOf(
        FeatureCatalogItem("xp", "XP System", Icons.Default.Star, FeatureCategory.COMMUNITY, "Leveling, leaderboard, and rewards"),
        FeatureCatalogItem("reputation", "Reputation", Icons.Default.EmojiEvents, FeatureCategory.COMMUNITY, "Member-to-member reputation"),
        FeatureCatalogItem("highlights", "Highlights", Icons.Default.NotificationsActive, FeatureCategory.COMMUNITY, "Word and phrase notifications"),
        FeatureCatalogItem("birthday", "Birthdays", Icons.Default.Cake, FeatureCategory.COMMUNITY, "Birthday announcements and roles"),
        FeatureCatalogItem("wordoftheday", "Word of the Day", Icons.AutoMirrored.Filled.MenuBook, FeatureCategory.COMMUNITY, "Daily vocabulary word with topics and filters"),
        FeatureCatalogItem("liveboards", "Live Boards", Icons.Default.PushPin, FeatureCategory.COMMUNITY, "Self-refreshing leaderboards, charts, and server reports"),
        FeatureCatalogItem("starboard", "Starboard", Icons.Default.Star, FeatureCategory.COMMUNITY, "Star-pinned message board"),
        FeatureCatalogItem("confessions", "Confessions", Icons.Default.Lock, FeatureCategory.COMMUNITY, "Anonymous confession submissions"),
        FeatureCatalogItem("counting", "Counting", Icons.Default.Numbers, FeatureCategory.COMMUNITY, "Counting game channels"),
        FeatureCatalogItem("forms", "Forms", Icons.Default.Description, FeatureCategory.COMMUNITY, "Custom forms and surveys"),
        FeatureCatalogItem("invites", "Invites", Icons.Default.Groups, FeatureCategory.COMMUNITY, "Invite tracking and rewards"),
        FeatureCatalogItem("patreon", "Patreon", Icons.Default.Favorite, FeatureCategory.COMMUNITY, "Patreon supporter integration"),
        FeatureCatalogItem("statchannels", "Stat Channels", Icons.Default.Equalizer, FeatureCategory.COMMUNITY, "Live stat voice channels"),
        FeatureCatalogItem("statroles", "Stat Roles", Icons.Default.MilitaryTech, FeatureCategory.COMMUNITY, "Roles earned by activity and removed when it stops"),
        FeatureCatalogItem("streams", "Streams", Icons.Default.VideoCameraFront, FeatureCategory.COMMUNITY, "Twitch and YouTube notifications"),
        FeatureCatalogItem("twitch", "Twitch Bot", Icons.Default.LiveTv, FeatureCategory.COMMUNITY, "Twitch chat bot, alerts, and live tools"),
        FeatureCatalogItem("suggestions", "Suggestions", Icons.Default.TipsAndUpdates, FeatureCategory.COMMUNITY, "Member suggestion box"),
        FeatureCatalogItem("feature-requests", "Feature Requests", Icons.Default.Lightbulb, FeatureCategory.COMMUNITY, "Suggest features, report bugs, and upvote ideas"),
        FeatureCatalogItem("todo", "Todo Lists", Icons.Default.Checklist, FeatureCategory.COMMUNITY, "Personal and shared task lists"),
        FeatureCatalogItem("polls", "Polls", Icons.Default.Poll, FeatureCategory.COMMUNITY, "Create, schedule, and review polls"),
        FeatureCatalogItem("votes", "Votes", Icons.Default.ThumbUp, FeatureCategory.COMMUNITY, "Vote tracking, reward roles, and leaderboard"),

        FeatureCatalogItem("music", "Music", Icons.Default.MusicNote, FeatureCategory.ENTERTAINMENT, "Play music in voice channels"),
        FeatureCatalogItem("customvoice", "Custom Voice", Icons.Default.Mic, FeatureCategory.ENTERTAINMENT, "User-owned temporary voice channels"),
        FeatureCatalogItem("giveaways", "Giveaways", Icons.Default.CardGiftcard, FeatureCategory.ENTERTAINMENT, "Run prize draws"),
        FeatureCatalogItem("currency", "Currency", Icons.Default.Paid, FeatureCategory.ENTERTAINMENT, "Economy analytics, settings, shop, and balances"),
        FeatureCatalogItem("minecraft", "Minecraft", Icons.Default.Widgets, FeatureCategory.ENTERTAINMENT, "Minecraft server status"),
        FeatureCatalogItem("tickets", "Tickets", Icons.Default.ConfirmationNumber, FeatureCategory.ENTERTAINMENT, "Support ticket system"),

        FeatureCatalogItem("afk", "AFK System", Icons.Default.DarkMode, FeatureCategory.ACTIONS, "Away-from-keyboard status"),
        FeatureCatalogItem("chat-triggers", "Chat Triggers", Icons.Default.Bolt, FeatureCategory.ACTIONS, "Custom keyword reactions"),
        FeatureCatalogItem("embedbuilder", "Embeds", Icons.Default.ViewAgenda, FeatureCategory.ACTIONS, "Compose and send rich embeds"),
        FeatureCatalogItem("feeds", "Feeds", Icons.Default.RssFeed, FeatureCategory.ACTIONS, "RSS and social feeds"),
        FeatureCatalogItem(
            "multigreets",
            "Greets",
            Icons.Default.WavingHand,
            FeatureCategory.ACTIONS,
            "Welcome and goodbye messages",
            keywords = listOf("welcome"),
        ),
        FeatureCatalogItem("repeaters", "Repeaters", Icons.Default.Repeat, FeatureCategory.ACTIONS, "Recurring scheduled messages"),
        FeatureCatalogItem("rolegreets", "Role Greets", Icons.Default.PersonAddAlt, FeatureCategory.ACTIONS, "Greet on role assignment"),
        FeatureCatalogItem("role-menus", "Role Menus", Icons.AutoMirrored.Filled.PlaylistAddCheck, FeatureCategory.ACTIONS, "Dropdowns and buttons that let members pick their own roles"),
        FeatureCatalogItem("rolestates", "Role States", Icons.Default.Sync, FeatureCategory.ACTIONS, "Persist roles across rejoins"),
        FeatureCatalogItem("statusroles", "Status Roles", Icons.AutoMirrored.Filled.VolumeUp, FeatureCategory.ACTIONS, "Roles based on Discord status"),
        FeatureCatalogItem("utility", "Utilities", Icons.Default.Build, FeatureCategory.ACTIONS, "Aliases, quotes, auto publish, stream role, AI, NSFW filter, role monitor"),

        FeatureCatalogItem(
            "administration",
            "Administration",
            Icons.Default.AdminPanelSettings,
            FeatureCategory.SECURITY,
            "Server administration and protections",
            keywords = listOf(
                "auto role", "join role", "self-assign", "self assign", "reaction roles",
                "protection", "anti-raid", "anti raid", "anti-spam", "permissions",
            ),
        ),
        FeatureCatalogItem("auditlog", "Audit Log", Icons.Default.Policy, FeatureCategory.SECURITY, "Who accessed the dashboard, what they changed, and what they viewed"),
        FeatureCatalogItem("chatsaver", "Chat Saver", Icons.Default.Storage, FeatureCategory.SECURITY, "Archive and save chat messages"),
        FeatureCatalogItem("access", "Dashboard Access", Icons.Default.Key, FeatureCategory.SECURITY, "Restricted dashboard access for users and roles"),
        FeatureCatalogItem("filter", "Message Filters", Icons.Default.FilterAlt, FeatureCategory.SECURITY, "Block words, invites, and links"),
        FeatureCatalogItem("channel-access", "Channel Access", Icons.Default.Lock, FeatureCategory.SECURITY, "Applications and member votes for locked channels"),
        FeatureCatalogItem("logging", "Logging", Icons.AutoMirrored.Filled.ManageSearch, FeatureCategory.SECURITY, "Audit and event logs"),
        FeatureCatalogItem(
            "moderation",
            "Moderation",
            Icons.Default.Shield,
            FeatureCategory.SECURITY,
            "Warnings, bans, and mod tools",
            keywords = listOf("mute", "ban", "kick", "timeout", "warn"),
        ),

        FeatureCatalogItem("messagestats", "Message Stats", Icons.Default.MarkEmailUnread, FeatureCategory.ANALYTICS, "Per-channel and per-user activity"),
        FeatureCatalogItem("serverstats", "Activity Stats", Icons.Default.Insights, FeatureCategory.ANALYTICS, "Messages, voice time, games and member growth"),

        FeatureCatalogItem("settings", "Settings", Icons.Default.Tune, FeatureCategory.SETTINGS, "Per-guild bot configuration"),
    )

    /** Lookup by catalog id. */
    val byId: Map<String, FeatureCatalogItem> = items.associateBy { it.id }

    /** Categories in display order, each with its items sorted by label. */
    val grouped: List<Pair<FeatureCategory, List<FeatureCatalogItem>>> =
        FeatureCategory.order.mapNotNull { category ->
            val entries = items.filter { it.category == category }
            if (entries.isEmpty()) null
            else category to entries.sortedBy { it.label.lowercase() }
        }

    /** The icon used for the guild overview entry point. */
    val overviewIcon = Icons.Default.Dashboard
}
