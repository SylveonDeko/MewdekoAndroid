package dev.mewdeko.mobile.feature.embed

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** A named embed stored against a user or shared with a guild. */
@Serializable
data class SavedEmbed(
    val id: Int = 0,
    val embedName: String? = null,
    val jsonCode: String = "",
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    val isGuildShared: Boolean = false,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The name to show in a list, falling back to the row id. */
    val displayName: String get() = embedName?.takeIf { it.isNotBlank() } ?: "Embed #$id"
}

/** A webhook identity a message can be sent as. */
@Serializable
data class EmbedPersona(
    val id: Int = 0,
    val name: String = "",
    val avatarUrl: String? = null,
    val hasUploadedAvatar: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    val isGuildShared: Boolean = false,
)

/**
 * A channel the signed-in user may post in, annotated with what both they and
 * the bot are permitted to do there.
 */
@Serializable
data class SendableChannel(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    @Serializable(with = SnowflakeSerializer::class) val categoryId: Snowflake? = null,
    val categoryName: String? = null,
    val position: Int = 0,
    val isThread: Boolean = false,
    val isAnnouncement: Boolean = false,
    val canSend: Boolean = false,
    val canEmbed: Boolean = false,
    val canMentionEveryone: Boolean = false,
    val canUseWebhooks: Boolean = false,
    val botCanSend: Boolean = false,
    val botCanEmbed: Boolean = false,
    val botCanUseWebhooks: Boolean = false,
    val restriction: String? = null,
) {
    /** Whether a plain embed can actually be delivered here. */
    val isUsable: Boolean get() = canSend && botCanSend && botCanEmbed

    /** Why this channel cannot be posted in, when it cannot. */
    val blockedReason: String?
        get() = when {
            isUsable -> null
            restriction?.isNotBlank() == true -> restriction
            !canSend -> "You cannot post here"
            !botCanSend -> "The bot cannot post here"
            else -> "The bot cannot embed here"
        }
}

/** What the bot reports after delivering a message. */
@Serializable
data class SendEmbedResult(
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String = "",
    val messageLink: String = "",
    val sentViaWebhook: Boolean = false,
    val webhookUsername: String? = null,
    val personaName: String? = null,
    val mentionsSuppressed: Boolean = false,
)
