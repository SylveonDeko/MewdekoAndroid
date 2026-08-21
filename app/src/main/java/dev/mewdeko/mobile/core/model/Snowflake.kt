package dev.mewdeko.mobile.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/** A Discord snowflake identifier carried as a string to preserve 64-bit precision. */
typealias Snowflake = String

/** The Discord epoch start, in milliseconds since the Unix epoch. */
val DISCORD_EPOCH_MILLIS: Long = 1_420_070_400_000L

/** The snowflake parsed as an unsigned integer, or `null` if non-numeric. */
val Snowflake.snowflakeValue: ULong?
    get() = toULongOrNull()

/**
 * The timestamp embedded in the snowflake, or `null` if the receiver is not a
 * valid numeric snowflake.
 */
val Snowflake.creationDate: Instant?
    get() {
        val value = snowflakeValue ?: return null
        val millis = (value shr 22) + DISCORD_EPOCH_MILLIS.toULong()
        return Instant.ofEpochMilli(millis.toLong())
    }

/**
 * Decodes a snowflake that may arrive as either a JSON string or an unsigned
 * integer.
 */
object SnowflakeSerializer : KSerializer<Snowflake> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Snowflake", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Snowflake {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return ""
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: Snowflake) {
        encoder.encodeString(value)
    }
}
