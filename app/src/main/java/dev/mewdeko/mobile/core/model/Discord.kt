package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A Discord guild the signed-in user is a member of. */
@Serializable
data class Guild(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val icon: String? = null,
    val owner: Boolean = false,
    val permissions: Long = 0L,
    val hasAdminAccess: Boolean? = null,
) {
    /** CDN URL for the guild icon at 128px, or `null` if none is set. */
    val iconUrl: String?
        get() {
            val icon = icon?.takeIf { it.isNotEmpty() } ?: return null
            val ext = if (icon.startsWith("a_")) "gif" else "png"
            return "https://cdn.discordapp.com/icons/$id/$icon.$ext?size=128"
        }
}

/** Detailed metadata for a single guild. */
@Serializable
data class GuildInfo(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val icon: String? = null,
    val iconUrl: String? = null,
    val banner: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val memberCount: Int = 0,
    val premiumTier: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val ownerId: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val createdAt: Instant = Instant.EPOCH,
)

/** Whether a given bot instance is in a guild, plus its public summary. */
@Serializable
data class HasGuildResponse(
    val hasGuild: Boolean = false,
    val guildName: String? = null,
    val memberCount: Int? = null,
    val iconUrl: String? = null,
    val description: String? = null,
)

/** A member of a guild. */
@Serializable
data class GuildMember(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isBot: Boolean = false,
)

/** A role in a guild. */
@Serializable
data class GuildRole(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
)

/** A text or voice channel in a guild. */
@Serializable
data class GuildChannel(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val type: Int = 0,
)

/** A signed-in Discord user as known to the app. */
@Serializable
data class MobileUser(
    val id: String = "",
    val username: String = "",
    val globalName: String = "",
    val avatar: String? = null,
) {
    /** The user's display name. */
    val displayName: String get() = globalName.ifEmpty { username }

    /** CDN URL for the user's avatar at 128px, or `null` if none is set. */
    val avatarUrl: String?
        get() = avatar?.takeIf { it.isNotEmpty() }?.let {
            "https://cdn.discordapp.com/avatars/$id/$it.png?size=128"
        }
}

/** A bot instance the app can route requests to. */
@Serializable
data class MobileInstance(
    @Serializable(with = SnowflakeSerializer::class) val botId: Snowflake = "",
    val botName: String = "",
    val botAvatar: String? = null,
    val isActive: Boolean = false,
) {
    /** CDN URL for the bot's avatar at 64px, or `null` if none is set. */
    val avatarUrl: String?
        get() {
            val avatar = botAvatar?.takeIf { it.isNotEmpty() } ?: return null
            if (avatar.startsWith("http")) return avatar
            return "https://cdn.discordapp.com/avatars/$botId/$avatar.png?size=64"
        }
}

/** A list of bot instances available to the signed-in user. */
@Serializable
data class InstancesResponse(val instances: List<MobileInstance> = emptyList())

/** Status of the running bot instance. */
@Serializable
data class BotStatus(
    val botName: String = "",
    val botAvatar: String? = null,
    val botBanner: String? = null,
    val botVersion: String = "",
    val botLatency: Int = 0,
    val botStatus: String = "",
    val commandsCount: Int = 0,
    val modulesCount: Int = 0,
    val textCommandsCount: Int? = null,
    val slashCommandsCount: Int? = null,
    val dNetVersion: String = "",
    val userCount: Int = 0,
    val commitHash: String = "",
    @Serializable(with = SnowflakeSerializer::class) val botId: Snowflake = "",
)
