package dev.mewdeko.mobile.core.model

import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/** Lifecycle states a music player can be in. */
enum class PlayerState(val raw: Int) {
    NOT_PLAYING(0),
    PLAYING(1),
    PAUSED(2),
    DESTROYED(3);

    /** Whether the player is currently producing audio. */
    val isPlaying: Boolean get() = this == PLAYING

    /** Whether playback is paused. */
    val isPaused: Boolean get() = this == PAUSED

    companion object {
        /** Maps a wire value onto a state, defaulting to [NOT_PLAYING]. */
        fun from(raw: Int): PlayerState = entries.firstOrNull { it.raw == raw } ?: NOT_PLAYING
    }
}

/** Decodes [PlayerState] from its integer wire representation. */
object PlayerStateSerializer : KSerializer<PlayerState> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PlayerState", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): PlayerState =
        PlayerState.from(decoder.decodeInt())

    override fun serialize(encoder: Encoder, value: PlayerState) = encoder.encodeInt(value.raw)
}

/** Metadata for a single audio track. */
@Serializable
data class Track(
    val title: String = "",
    val author: String? = null,
    val duration: String? = null,
    val identifier: String? = null,
    val uri: String? = null,
    val artworkUri: String? = null,
    val sourceName: String? = null,
    val isLiveStream: Boolean? = null,
    val isSeekable: Boolean? = null,
)

/** A Discord user reference attached to a queued track. */
@Serializable
data class PartialUser(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
)

/** A track in the queue together with the user that requested it. */
@Serializable
data class QueueTrack(
    val index: Int? = null,
    val track: Track = Track(),
    val requester: PartialUser? = null,
) {
    /** Stable identity for list keying. */
    val id: String get() = track.uri ?: track.title
}

/** Current playback position information. */
@Serializable
data class PlayerPosition(
    val relativePosition: String? = null,
    val position: String? = null,
    @Serializable(with = InstantSerializer::class) val syncedAt: Instant? = null,
) {
    /** A wall-clock time string suitable for display (mm:ss or hh:mm:ss). */
    val displayValue: String? get() = relativePosition ?: position
}

/** A snapshot of the music player's current state for a guild. */
@Serializable
data class MusicStatus(
    val currentTrack: QueueTrack? = null,
    val queue: List<QueueTrack>? = null,
    @Serializable(with = PlayerStateSerializer::class) val state: PlayerState = PlayerState.NOT_PLAYING,
    val volume: Double? = null,
    val position: PlayerPosition? = null,
    val repeatMode: Int? = null,
    val isInVoiceChannel: Boolean? = null,
    val botInChannel: Boolean? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val channelName: String? = null,
)
