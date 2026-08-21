package dev.mewdeko.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bolt
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
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
)

/**
 * The canonical list of every per-guild feature page, grouped into
 * categories.
 */
object NavigationCatalog {

    /** Every feature page, in catalog order. */
    val items: List<FeatureCatalogItem> = listOf(
        // Community
        FeatureCatalogItem("xp", "XP System", Icons.Default.Star, FeatureCategory.COMMUNITY, "Leveling, leaderboard, and rewards"),
        FeatureCatalogItem("reputation", "Reputation", Icons.Default.EmojiEvents, FeatureCategory.COMMUNITY, "Member-to-member reputation"),
        FeatureCatalogItem("highlights", "Highlights", Icons.Default.NotificationsActive, FeatureCategory.COMMUNITY, "Word and phrase notifications"),
        FeatureCatalogItem("birthday", "Birthdays", Icons.Default.Cake, FeatureCategory.COMMUNITY, "Birthday announcements and roles"),
        FeatureCatalogItem("starboard", "Starboard", Icons.Default.Star, FeatureCategory.COMMUNITY, "Star-pinned message board"),
        FeatureCatalogItem("confessions", "Confessions", Icons.Default.Lock, FeatureCategory.COMMUNITY, "Anonymous confession submissions"),
        FeatureCatalogItem("counting", "Counting", Icons.Default.Numbers, FeatureCategory.COMMUNITY, "Counting game channels"),
        FeatureCatalogItem("forms", "Forms", Icons.Default.Description, FeatureCategory.COMMUNITY, "Custom forms and surveys"),
        FeatureCatalogItem("invites", "Invites", Icons.Default.Groups, FeatureCategory.COMMUNITY, "Invite tracking and rewards"),
        FeatureCatalogItem("patreon", "Patreon", Icons.Default.Favorite, FeatureCategory.COMMUNITY, "Patreon supporter integration"),
        FeatureCatalogItem("statchannels", "Stat Channels", Icons.Default.Equalizer, FeatureCategory.COMMUNITY, "Live stat voice channels"),
        FeatureCatalogItem("streams", "Streams", Icons.Default.VideoCameraFront, FeatureCategory.COMMUNITY, "Twitch and YouTube notifications"),
        FeatureCatalogItem("suggestions", "Suggestions", Icons.Default.TipsAndUpdates, FeatureCategory.COMMUNITY, "Member suggestion box"),
        FeatureCatalogItem("todo", "Todo Lists", Icons.Default.Checklist, FeatureCategory.COMMUNITY, "Personal and shared task lists"),
        FeatureCatalogItem("votes", "Votes", Icons.Default.ThumbUp, FeatureCategory.COMMUNITY, "Polls and voting"),

        // Entertainment
        FeatureCatalogItem("music", "Music", Icons.Default.MusicNote, FeatureCategory.ENTERTAINMENT, "Lavalink-backed music player"),
        FeatureCatalogItem("customvoice", "Custom Voice", Icons.Default.Mic, FeatureCategory.ENTERTAINMENT, "User-owned temporary voice channels"),
        FeatureCatalogItem("giveaways", "Giveaways", Icons.Default.CardGiftcard, FeatureCategory.ENTERTAINMENT, "Run prize draws"),
        FeatureCatalogItem("minecraft", "Minecraft", Icons.Default.Widgets, FeatureCategory.ENTERTAINMENT, "Minecraft server status"),
        FeatureCatalogItem("tickets", "Tickets", Icons.Default.ConfirmationNumber, FeatureCategory.ENTERTAINMENT, "Support ticket system"),

        // Actions
        FeatureCatalogItem("afk", "AFK System", Icons.Default.DarkMode, FeatureCategory.ACTIONS, "Away-from-keyboard status"),
        FeatureCatalogItem("chat-triggers", "Chat Triggers", Icons.Default.Bolt, FeatureCategory.ACTIONS, "Custom keyword reactions"),
        FeatureCatalogItem("embedbuilder", "Embeds", Icons.Default.ViewAgenda, FeatureCategory.ACTIONS, "Compose and send rich embeds"),
        FeatureCatalogItem("feeds", "Feeds", Icons.Default.RssFeed, FeatureCategory.ACTIONS, "RSS and social feeds"),
        FeatureCatalogItem("multigreets", "Greets", Icons.Default.WavingHand, FeatureCategory.ACTIONS, "Welcome and goodbye messages"),
        FeatureCatalogItem("repeaters", "Repeaters", Icons.Default.Repeat, FeatureCategory.ACTIONS, "Recurring scheduled messages"),
        FeatureCatalogItem("rolegreets", "Role Greets", Icons.Default.PersonAddAlt, FeatureCategory.ACTIONS, "Greet on role assignment"),
        FeatureCatalogItem("rolestates", "Role States", Icons.Default.Sync, FeatureCategory.ACTIONS, "Persist roles across rejoins"),
        FeatureCatalogItem("statusroles", "Status Roles", Icons.AutoMirrored.Filled.VolumeUp, FeatureCategory.ACTIONS, "Roles based on Discord status"),

        // Security
        FeatureCatalogItem("administration", "Administration", Icons.Default.AdminPanelSettings, FeatureCategory.SECURITY, "Server administration and protections"),
        FeatureCatalogItem("chatsaver", "Chat Saver", Icons.Default.Storage, FeatureCategory.SECURITY, "Archive and save chat messages"),
        FeatureCatalogItem("logging", "Logging", Icons.Default.ManageSearch, FeatureCategory.SECURITY, "Audit and event logs"),
        FeatureCatalogItem("moderation", "Moderation", Icons.Default.Shield, FeatureCategory.SECURITY, "Warnings, bans, and mod tools"),

        // Analytics
        FeatureCatalogItem("messagestats", "Message Stats", Icons.Default.MarkEmailUnread, FeatureCategory.ANALYTICS, "Per-channel and per-user activity"),
        FeatureCatalogItem("performance", "Performance", Icons.Default.Speed, FeatureCategory.ANALYTICS, "Bot CPU, memory, and shard health", ownerOnly = true),

        // Settings
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
