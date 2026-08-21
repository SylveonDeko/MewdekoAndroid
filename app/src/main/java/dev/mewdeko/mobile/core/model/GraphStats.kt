package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant

/** Time-series counts and a summary for member joins or leaves. */
@Serializable
data class GraphStats(
    val dailyStats: List<DailyStat> = emptyList(),
    val summary: GraphSummary = GraphSummary(),
)

/** A single day's count in a graph series. */
@Serializable
data class DailyStat(
    @Serializable(with = InstantSerializer::class) val date: Instant = Instant.EPOCH,
    val count: Int = 0,
)

/** Aggregate metrics for a graph series. */
@Serializable
data class GraphSummary(
    val total: Int = 0,
    @Serializable(with = LenientDoubleSerializer::class) val average: Double = 0.0,
    @Serializable(with = InstantSerializer::class) val peakDate: Instant = Instant.EPOCH,
    val peakCount: Int = 0,
)

/** Decodes a double that the bot may emit as either a number or a string. */
object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return 0.0
        return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull() ?: 0.0
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}
