package dev.mewdeko.mobile.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

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
        val element = json.decodeJsonElement()
        if (element is JsonNull) return ScalarString(null)
        return ScalarString((element as? JsonPrimitive)?.content)
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

/** A generic `{ success, message }` acknowledgement. */
@Serializable
data class ActionResult(
    val success: Boolean = true,
    val message: String? = null,
    val error: String? = null,
)
