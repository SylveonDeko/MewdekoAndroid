package dev.mewdeko.mobile.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import dev.mewdeko.mobile.core.net.scalarText
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/**
 * Wrapper for endpoints that return a bare JSON string, integer, or null
 * rather than an object. The bot's simple getters do this throughout.
 */
@Serializable(with = ScalarStringSerializer::class)
data class ScalarString(val value: String?)

/** Decodes a bare JSON scalar into [ScalarString]. */
object ScalarStringSerializer : KSerializer<ScalarString> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ScalarString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): ScalarString {
        val json = decoder as? JsonDecoder ?: return ScalarString(decoder.decodeString())
        return ScalarString(json.decodeJsonElement().scalarText())
    }

    override fun serialize(encoder: Encoder, value: ScalarString) {
        encoder.encodeString(value.value.orEmpty())
    }
}

/** Lightweight text channel reference returned by `ClientOperations/textchannels`. */
@Serializable
data class TextChannelLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
)

/**
 * A single, typed guild channel returned by `ClientOperations/guildchannels`. Unlike
 * [TextChannelLite] this carries the concrete channel type and category, so callers can
 * filter out categories and threads and label results by the channel's category.
 */
@Serializable
data class GuildChannelLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val type: String = "",
    @Serializable(with = SnowflakeSerializer::class) val categoryId: Snowflake? = null,
    val categoryName: String? = null,
    val position: Int = 0,
)

/** A generic `{ success, message }` acknowledgement. */
@Serializable
data class ActionResult(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
)
