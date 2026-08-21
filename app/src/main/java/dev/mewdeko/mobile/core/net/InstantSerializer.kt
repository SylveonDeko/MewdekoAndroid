package dev.mewdeko.mobile.core.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Parses the range of timestamp shapes the bot emits: ISO-8601 with or without
 * fractional seconds and offset, .NET style timestamps with no timezone (which
 * are treated as UTC), and bare dates.
 */
object InstantParser {

    private val zonedFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private val localFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter()

    private val spacedFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter()

    /** Returns the parsed instant, or `null` when no known format matches. */
    fun parse(raw: String): Instant? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        runCatching { return Instant.from(zonedFormatter.parse(trimmed)) }
        runCatching { return Instant.parse(trimmed) }
        runCatching { return LocalDateTime.parse(trimmed, localFormatter).toInstant(ZoneOffset.UTC) }
        runCatching { return LocalDateTime.parse(trimmed, spacedFormatter).toInstant(ZoneOffset.UTC) }
        runCatching {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        }
        return null
    }
}

/**
 * Lenient [Instant] codec. Unparseable or null values decode to the Unix
 * epoch rather than throwing; prefer a nullable property when absence is
 * meaningful.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val json = decoder as? JsonDecoder ?: return Instant.EPOCH
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return Instant.EPOCH
        primitive.longOrNull?.let { return Instant.ofEpochMilli(it) }
        return InstantParser.parse(primitive.content) ?: Instant.EPOCH
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }
}
