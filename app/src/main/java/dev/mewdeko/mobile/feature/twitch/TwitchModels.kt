package dev.mewdeko.mobile.feature.twitch

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** OAuth, bot, and alert configuration returned by `GET api/Twitch/oauth/status`. */
@Serializable
data class TwitchOAuthStatus(
    val isConfigured: Boolean = false,
    val hasBotAccount: Boolean = false,
    val hasChannelAuthorization: Boolean = false,
    val useEventSub: Boolean = true,
    val botUsername: String? = null,
    val botDisplayName: String? = null,
    val channelUsername: String? = null,
    val channelDisplayName: String? = null,
    val twitchUserId: String? = null,
    val commandPrefix: String? = null,
    val language: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val goLiveChannelId: Snowflake? = null,
    val goLiveMessage: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val subNotificationChannelId: Snowflake? = null,
    val subNotificationMessage: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val raidNotificationChannelId: Snowflake? = null,
    val raidNotificationMessage: String? = null,
    @Serializable(with = InstantSerializer::class) val botTokenExpiry: Instant? = null,
    @Serializable(with = InstantSerializer::class) val channelTokenExpiry: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastAuthorizedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastEventAt: Instant? = null,
) {
    /** The bot account's display label, or `null` when none is connected. */
    val botLabel: String?
        get() = botDisplayName?.takeIf { it.isNotBlank() } ?: botUsername?.takeIf { it.isNotBlank() }

    /** The broadcaster channel's display label, or `null` when none is connected. */
    val channelLabel: String?
        get() = channelDisplayName?.takeIf { it.isNotBlank() } ?: channelUsername?.takeIf { it.isNotBlank() }

    /** The dashboard's connection summary: Ready, Needs setup, or Not connected. */
    val connectionLabel: String
        get() = when {
            isConfigured -> "Ready"
            hasBotAccount || hasChannelAuthorization -> "Needs setup"
            else -> "Not connected"
        }
}

/**
 * The stored guild configuration returned by `GET api/Twitch/config`. Only
 * read for the real Enabled flag, which the status payload does not carry.
 */
@Serializable
data class TwitchConfigSnapshot(
    val twitchChannel: String = "",
    val commandPrefix: String = "!",
    val enabled: Boolean = false,
    val useEventSub: Boolean = true,
    val language: String? = null,
)

/** The authorization link returned by `GET api/Twitch/oauth/url`. */
@Serializable
data class TwitchOAuthUrl(
    val authorizationUrl: String = "",
    val state: String = "",
    val mode: String = "",
)

/** One stored EventSub subscription in the health snapshot. */
@Serializable
data class TwitchSubscriptionHealth(
    val twitchSubscriptionId: String = "",
    val type: String = "",
    val status: String = "",
    val sessionId: String? = null,
    @Serializable(with = InstantSerializer::class) val lastUpdatedAt: Instant? = null,
)

/** Scope and EventSub diagnostics returned by `GET api/Twitch/health`. */
@Serializable
data class TwitchHealth(
    val hasConfig: Boolean = false,
    val enabled: Boolean = false,
    val twitchChannel: String? = null,
    val eventSubEnabled: Boolean = false,
    val hasBotAccount: Boolean = false,
    val hasChannelAuthorization: Boolean = false,
    val botMissingScopes: List<String> = emptyList(),
    val channelMissingScopes: List<String> = emptyList(),
    @Serializable(with = InstantSerializer::class) val botTokenExpiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val channelTokenExpiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastEventAt: Instant? = null,
    val subscriptions: List<TwitchSubscriptionHealth> = emptyList(),
)

/** A built-in Twitch chat command and the permission it requires. */
@Serializable
data class TwitchChatCommand(
    val name: String = "",
    val permission: String = "",
)

/** A dashboard-managed custom Twitch chat command. */
@Serializable
data class TwitchCustomCommand(
    val id: Int = 0,
    val name: String = "",
    val response: String = "",
    val permission: String = "Everyone",
    val cooldownSeconds: Int = 0,
    val enabled: Boolean = true,
    val useCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastUsedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastUpdatedAt: Instant? = null,
)

/** The rendered output of `POST api/Twitch/custom-commands/preview`. */
@Serializable
data class TwitchCommandPreview(val response: String = "")

/** A repeating Twitch chat message timer. */
@Serializable
data class TwitchTimer(
    val id: Int = 0,
    val name: String = "",
    val messages: String = "",
    val intervalMinutes: Int = 10,
    val minChatMessages: Int = 5,
    val onlineOnly: Boolean = true,
    val randomizeMessages: Boolean = false,
    val enabled: Boolean = true,
    @Serializable(with = InstantSerializer::class) val lastSentAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val lastUpdatedAt: Instant? = null,
) {
    /** The non-empty message lines the timer rotates through. */
    val messageLines: List<String>
        get() = messages.lines().map { it.trim() }.filter { it.isNotEmpty() }
}

/** The message sent by `POST api/Twitch/timers/test`. */
@Serializable
data class TwitchTimerTest(val message: String = "")

/** A saved Twitch chat quote. */
@Serializable
data class TwitchQuote(
    val id: Int = 0,
    val text: String = "",
    val author: String? = null,
    val addedBy: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A channel point redemption action template. */
@Serializable
data class TwitchRedemptionAction(
    val id: Int = 0,
    val rewardTitle: String = "",
    val twitchResponse: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val discordChannelId: Snowflake? = null,
    val discordMessage: String? = null,
    val enabled: Boolean = true,
    @Serializable(with = InstantSerializer::class) val lastUpdatedAt: Instant? = null,
)

/** A Discord member linked to a Twitch username. */
@Serializable
data class TwitchAccountLink(
    @Serializable(with = SnowflakeSerializer::class) val discordUserId: Snowflake = "",
    val twitchUsername: String = "",
)

/** Template variables grouped by feature area, from `GET api/Twitch/variables`. */
@Serializable
data class TwitchVariableDocs(
    val groups: Map<String, List<String>> = emptyMap(),
)

/** The result of a Live Tools action. */
@Serializable
data class TwitchActionResult(
    val success: Boolean = false,
    val message: String = "",
    val url: String? = null,
)

/** A bare `{ message }` acknowledgement. */
@Serializable
data class TwitchMessageResult(val message: String = "")

/** Permission levels a custom Twitch command can require, in the bot's enum spelling. */
enum class TwitchPermission(val value: String) {
    EVERYONE("Everyone"),
    SUBSCRIBER("Subscriber"),
    VIP("Vip"),
    MOD("Mod"),
    BROADCASTER("Broadcaster"),
}

/** Which Twitch identity an OAuth or disconnect call targets. */
enum class TwitchOAuthMode(val value: String, val label: String) {
    BOT("bot", "bot account"),
    CHANNEL("channel", "channel authorization"),
}

/** A dashboard-generated test event type. */
enum class TwitchTestEvent(val value: String, val label: String) {
    GO_LIVE("golive", "go-live"),
    SUB("sub", "sub"),
    RAID("raid", "raid"),
}

/** Every Live Tools operation, used to track which one is running. */
enum class TwitchLiveAction { CHAT, MARKER, CLIP, POLL, TIMEOUT, BAN, UNBAN, DELETE }

/** A static reference entry for a Discord slash command in the Twitch module. */
data class TwitchSlashCommand(
    val name: String,
    val usage: String,
    val description: String,
    val permission: String,
)

/** Static reference data mirrored from the dashboard page. */
object TwitchReference {

    /** The Discord slash commands the Twitch module exposes. */
    val slashCommands = listOf(
        TwitchSlashCommand("/twitch set", "<channel> [prefix]", "Set the Twitch channel this server's bot should join and enable chat commands.", "Manage Server"),
        TwitchSlashCommand("/twitch remove", "", "Remove the Twitch channel configuration and leave the channel.", "Manage Server"),
        TwitchSlashCommand("/twitch config", "", "Show the current Twitch configuration for this server.", "Everyone"),
        TwitchSlashCommand("/twitch golive-channel", "<channel> [message]", "Set the Discord channel and optional message template for go-live notifications.", "Manage Server"),
        TwitchSlashCommand("/twitch golive-clear", "", "Clear the go-live notification channel.", "Manage Server"),
        TwitchSlashCommand("/twitch link", "<user> <twitchUsername>", "Link a Discord user to their Twitch account.", "Manage Server"),
        TwitchSlashCommand("/twitch unlink", "<user>", "Remove a Discord user's Twitch account link.", "Manage Server"),
        TwitchSlashCommand("/twitch links", "", "List all Twitch account links for this server.", "Everyone"),
        TwitchSlashCommand("/twitch language", "<languageTag>", "Override the language used for Twitch chat responses.", "Manage Server"),
        TwitchSlashCommand("/twitch prefix", "<prefix>", "Change the Twitch chat command prefix.", "Manage Server"),
    )

    /** Fallback custom command variables when the variables endpoint is unavailable. */
    val commandVariables = listOf(
        "%user%", "%display%", "%channel%", "%args%", "%target%", "%discord%", "%random:yes|no%", "%count:name%", "%stream%",
    )

    /** Fallback timer variables. */
    val timerVariables = listOf("%channel%", "%url%", "%stream%", "%count:name%", "%random:a|b|c%")

    /** Fallback go-live placeholders. */
    val goLiveVariables = listOf("%streamer%", "%title%", "%game%", "%url%", "%viewers%")

    /** Fallback sub notification variables. */
    val subVariables = listOf("%user%", "%display%", "%channel%", "%tier%")

    /** Fallback raid notification variables. */
    val raidVariables = listOf("%raider%", "%channel%", "%viewers%")

    /** Fallback channel point redemption variables. */
    val redemptionVariables = listOf("%user%", "%display%", "%channel%", "%reward%", "%input%", "%url%")
}
