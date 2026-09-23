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
    /**
     * Whether the channel can be picked at all.
     *
     * A hard block (cannot see, cannot send) makes a channel unusable even for
     * plain text. Missing Embed Links only blocks a send when the composed
     * message actually carries an embed, which [SendPanel] checks separately.
     */
    val isUsable: Boolean get() = restriction.isNullOrBlank()

    /** Why this channel cannot be posted in at all, when it cannot. */
    val blockedReason: String? get() = restriction

    /** Whether sending through a webhook is available in this channel. */
    val webhookUsable: Boolean get() = canUseWebhooks && botCanUseWebhooks
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
) {
    /** The identity the message was posted under, for the result summary. */
    val identityLabel: String?
        get() = personaName ?: webhookUsername?.takeIf { it.isNotBlank() }
}

/** A chat trigger a button or select option can fire when pressed. */
@Serializable
data class EmbedTriggerOption(
    val id: Int = 0,
    val trigger: String = "",
    val response: String = "",
) {
    /** Label shown in the trigger picker, falling back when the trigger text is blank. */
    val displayName: String get() = trigger.takeIf { it.isNotBlank() } ?: "Unnamed trigger"
}

/** Basic guild info returned alongside a guild's emoji list. */
@Serializable
data class EmbedGuildInfo(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val iconUrl: String? = null,
)

/** A guild emoji available for buttons and select options. */
@Serializable
data class EmbedEmojiInfo(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val animated: Boolean = false,
    val isAvailable: Boolean? = null,
    val requireColons: Boolean = false,
    val url: String = "",
) {
    /** The `<:name:id>` or `<a:name:id>` form Discord expects in a message. */
    val formatted: String get() = "<${if (animated) "a" else ""}:$name:$id>"
}

/** A guild's emoji list, as returned by the mutual-guild emoji picker endpoint. */
@Serializable
data class EmbedGuildEmojis(
    val guild: EmbedGuildInfo = EmbedGuildInfo(),
    val emojis: List<EmbedEmojiInfo> = emptyList(),
)
