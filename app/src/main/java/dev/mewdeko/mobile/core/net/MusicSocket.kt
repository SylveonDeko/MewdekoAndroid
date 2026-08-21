package dev.mewdeko.mobile.core.net

import android.util.Log
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.model.Snowflake
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MewdekoMusicWs"

/** A frame received from the music event WebSocket. */
sealed interface MusicSocketEvent {
    /** A decoded player snapshot. */
    data class Status(val status: MusicStatus) : MusicSocketEvent

    /** A frame that did not decode as a status snapshot. */
    data class Raw(val text: String) : MusicSocketEvent

    /** The socket closed cleanly. */
    data object Closed : MusicSocketEvent

    /** The socket failed. */
    data class Failed(val cause: Throwable) : MusicSocketEvent
}

/**
 * Live music event stream backed by the dashboard's WebSocket relay.
 *
 * Emits a [Flow]; collecting starts the socket and cancelling the collection
 * closes it, so callers never manage the connection by hand.
 */
@Singleton
class MusicSocket @Inject constructor(
    private val http: HttpClient,
    private val auth: AuthManager,
) {
    /**
     * Opens a stream of music events for the given guild and user.
     *
     * @param baseUrl The dashboard base URL, e.g. `https://dash.example.com`.
     * @param instanceBotId The bot instance the dashboard should route to.
     * @param guildId The guild whose player to subscribe to.
     * @param userId The acting Discord user.
     */
    fun connect(
        baseUrl: String,
        instanceBotId: Snowflake?,
        guildId: Snowflake,
        userId: Snowflake,
    ): Flow<MusicSocketEvent> = channelFlow {
        val token = runCatching { auth.currentAccessToken() }.getOrElse { cause ->
            send(MusicSocketEvent.Failed(cause))
            return@channelFlow
        }

        val wsUrl = buildString {
            append(baseUrl.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://"))
            append("/api/mobile/music/ws")
            append("?guildId=$guildId&userId=$userId")
            if (instanceBotId != null) append("&instance=$instanceBotId")
        }
        Log.i(TAG, "connecting to ${wsUrl.substringBefore("?")}")

        try {
            val session = http.webSocketSession(wsUrl) {
                header("Authorization", "Bearer $token")
                if (instanceBotId != null) header("X-Mobile-Instance", instanceBotId)
            }

            try {
                for (frame in session.incoming) {
                    val text = when (frame) {
                        is Frame.Text -> frame.readText()
                        is Frame.Binary -> String(frame.data)
                        else -> continue
                    }
                    send(decode(text))
                }
                send(MusicSocketEvent.Closed)
            } finally {
                runCatching { session.close() }
            }
        } catch (cause: Throwable) {
            Log.e(TAG, "socket failed: ${cause.message}")
            send(MusicSocketEvent.Failed(cause))
        }

        awaitClose { }
    }

    /**
     * Decodes one frame, accepting either a bare status object or the
     * `{ event, data }` envelope the relay sometimes wraps it in.
     *
     * Every [MusicStatus] field is optional, so a decode attempt against the
     * envelope's own top-level shape would succeed trivially instead of
     * failing over. The `data` key is checked structurally first so the
     * envelope is never mistaken for a status object.
     */
    private fun decode(text: String): MusicSocketEvent {
        val element = runCatching { MewdekoJson.parseToJsonElement(text).normalizeKeys() }
            .getOrNull() as? JsonObject
            ?: return MusicSocketEvent.Raw(text)

        val statusElement = element["data"] as? JsonObject ?: element

        return runCatching {
            MewdekoJson.decodeFromJsonElement(MusicStatus.serializer(), statusElement)
        }.getOrNull()?.let { MusicSocketEvent.Status(it) } ?: MusicSocketEvent.Raw(text)
    }
}
