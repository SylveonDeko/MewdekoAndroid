package dev.mewdeko.mobile.feature.utility

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A command alias returned by `GET Utility/{guildId}/aliases`. */
@Serializable
data class CommandAlias(
    val id: Int = 0,
    val trigger: String = "",
    val mapping: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A saved quote, as returned by the quote list, add, and update endpoints. */
@Serializable
data class GuildQuote(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val keyword: String = "",
    val authorName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val authorId: Snowflake = "",
    val text: String = "",
    val useCount: Long = 0,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** One page of quotes from `GET Utility/{guildId}/quotes`. */
@Serializable
data class QuoteListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 25,
    val quotes: List<GuildQuote> = emptyList(),
)

/** An auto published announcement channel with its skip lists. */
@Serializable
data class AutoPublishChannel(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    val blacklistedUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val blacklistedWords: List<String> = emptyList(),
)

/** A user on the stream role whitelist or blacklist. */
@Serializable
data class StreamRoleUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
)

/** Stream role settings from `GET Utility/{guildId}/streamrole`. */
@Serializable
data class StreamRoleSettings(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val addRoleId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val fromRoleId: Snowflake = "",
    val keyword: String? = null,
    val whitelist: List<StreamRoleUser> = emptyList(),
    val blacklist: List<StreamRoleUser> = emptyList(),
)

/** AI assistant configuration with the API key masked. */
@Serializable
data class AiConfig(
    val enabled: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val provider: Int = 0,
    val providerName: String? = null,
    val model: String? = null,
    val systemPrompt: String? = null,
    val webSearchEnabled: Boolean = false,
    val hideWebSearchMessages: Boolean = true,
    val hasApiKey: Boolean = false,
    val apiKeyHint: String? = null,
    val customEmbed: String? = null,
    val hasWebhook: Boolean = false,
    val tokensUsed: Long = 0,
)

/** A model offered by an AI provider. */
@Serializable
data class AiModel(
    val id: String = "",
    val name: String = "",
)

/** A role that nobody may be given. */
@Serializable
data class BlacklistedRoleEntry(
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val punishment: Int? = null,
)

/** A permission that roles may not be granted. */
@Serializable
data class BlacklistedPermissionEntry(
    @Serializable(with = SnowflakeSerializer::class) val permission: Snowflake = "",
    val permissionName: String = "",
    val punishment: Int? = null,
)

/** Role monitor configuration from `GET Utility/{guildId}/rolemonitor`. */
@Serializable
data class RoleMonitorConfig(
    val defaultPunishment: Int = UtilityPunishment.NONE,
    val blacklistedRoles: List<BlacklistedRoleEntry> = emptyList(),
    val blacklistedPermissions: List<BlacklistedPermissionEntry> = emptyList(),
    val whitelistedRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val whitelistedUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
)

/** The bot's `AiService.AiProvider` values, in the order the dashboard offers them. */
enum class AiProvider(val value: Int, val label: String) {
    OPEN_AI(0, "OpenAI"),
    CLAUDE(2, "Claude (Anthropic)"),
    GROQ(1, "Groq"),
}

/** The bot's `PunishmentAction` values and the subset role monitor offers. */
object UtilityPunishment {
    /** `PunishmentAction.None`, which only reverts the change. */
    const val NONE = 11

    /** Every `PunishmentAction` name by value, for labelling stored entries. */
    val names: Map<Int, String> = mapOf(
        0 to "Mute",
        1 to "Kick",
        2 to "Ban",
        3 to "Softban",
        4 to "Remove all roles",
        5 to "Chat mute",
        6 to "Voice mute",
        7 to "Add role",
        8 to "Delete",
        9 to "Warn",
        10 to "Timeout",
        11 to "Only revert the change",
    )

    /** The punishments the role monitor offers, in dashboard order. */
    val choices: List<Pair<Int, String>> = listOf(
        NONE to "Only revert the change",
        9 to "Warn",
        4 to "Remove all roles",
        0 to "Mute",
        10 to "Timeout",
        1 to "Kick",
        2 to "Ban",
    )

    /** Label for an entry's punishment, where `null` means the default applies. */
    fun label(value: Int?): String =
        if (value == null) "Default" else names[value] ?: value.toString()
}

/**
 * Dangerous Discord permissions worth monitoring, as `GuildPermission` bit
 * values paired with their display names.
 */
val MonitoredPermissions: List<Pair<String, String>> = listOf(
    "8" to "Administrator",
    "4" to "Ban Members",
    "2" to "Kick Members",
    "1099511627776" to "Moderate Members",
    "32" to "Manage Server",
    "268435456" to "Manage Roles",
    "16" to "Manage Channels",
    "536870912" to "Manage Webhooks",
    "8192" to "Manage Messages",
    "134217728" to "Manage Nicknames",
    "17179869184" to "Manage Threads",
    "8589934592" to "Manage Events",
    "1073741824" to "Manage Expressions",
    "131072" to "Mention Everyone",
)

/** Sections of the utilities screen, in tab order. */
object UtilitySection {
    /** Command aliases. */
    const val ALIASES = "aliases"

    /** Saved quotes. */
    const val QUOTES = "quotes"

    /** Announcement channel auto publishing. */
    const val AUTO_PUBLISH = "autopublish"

    /** Role granted while streaming. */
    const val STREAM_ROLE = "streamrole"

    /** AI assistant. */
    const val AI = "ai"

    /** NSFW tag blacklist. */
    const val NSFW = "nsfw"

    /** Role and permission monitoring. */
    const val ROLE_MONITOR = "rolemonitor"
}

/** The page size the dashboard uses for the quote list. */
const val QuotePageSize = 25
