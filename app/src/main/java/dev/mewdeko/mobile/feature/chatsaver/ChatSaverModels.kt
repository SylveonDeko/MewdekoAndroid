package dev.mewdeko.mobile.feature.chatsaver

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Summary row for one saved chat log, from `GET Chat/{guildId}/logs`. */
@Serializable
data class ChatLogSummary(
    val id: String = "0",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
    val name: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val timestamp: String? = null,
    val messageCount: Int = 0,
) {
    /** The label to show, preferring the user-assigned name. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: channelName?.let { "#$it" }
            ?: "Log $id"
}

/** The author block inside an archived or freshly fetched message. */
@Serializable
data class ChatLogAuthor(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "Unknown",
    val avatarUrl: String? = null,
)

/** One attachment on a message: an image shown inline, or a file link with its size. */
@Serializable
data class ChatLogAttachment(
    val url: String = "",
    val proxyUrl: String = "",
    val filename: String = "",
    val fileSize: Long = 0,
)

/** The author block inside an embed. */
@Serializable
data class ChatLogEmbedAuthor(
    val name: String = "",
    val iconUrl: String? = null,
)

/** One embed on a message. */
@Serializable
data class ChatLogEmbed(
    val type: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val thumbnail: String? = null,
    val author: ChatLogEmbedAuthor? = null,
)

/**
 * One message, either freshly fetched from `GET Chat/{guildId}/{channelId}/messages` or
 * archived inside a saved log. Both endpoints return the same shape.
 */
@Serializable
data class ChatLogMessage(
    val id: String = "",
    val content: String? = null,
    val author: ChatLogAuthor = ChatLogAuthor(),
    @Serializable(with = InstantSerializer::class) val timestamp: Instant = Instant.EPOCH,
    val attachments: List<ChatLogAttachment> = emptyList(),
    val embeds: List<ChatLogEmbed> = emptyList(),
)

/** A saved chat log with its full message body, from `GET Chat/{guildId}/logs/{logId}`. */
@Serializable
data class ChatLogDetail(
    val id: String = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
    val name: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val timestamp: String? = null,
    val messageCount: Int = 0,
    val messages: List<ChatLogMessage> = emptyList(),
)

/** The id returned by `POST Chat/{guildId}/logs`. */
@Serializable
data class SavedChatLogId(val id: String = "")

/** The unit a fetch time window is measured in, mirroring the dashboard's picker. */
enum class ChatTimeUnit(val id: String, val label: String, val maxAmount: Int) {
    MINUTES("minutes", "Minutes", 4320),
    HOURS("hours", "Hours", 72),
    DAYS("days", "Days", 3);

    companion object {
        /** Resolves a stored id, defaulting to hours. */
        fun from(id: String?): ChatTimeUnit = entries.firstOrNull { it.id == id } ?: HOURS
    }
}

/** A generated HTML transcript waiting to be shared through an intent. */
data class ChatLogExport(val filename: String, val content: String)
