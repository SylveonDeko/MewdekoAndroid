package dev.mewdeko.mobile.core.net

import android.util.Log
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.model.Snowflake
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MewdekoMusicStream"

/** A frame received from the live music event stream. */
sealed interface MusicSocketEvent {
    /** A decoded player snapshot. */
    data class Status(val status: MusicStatus) : MusicSocketEvent

    /** A frame that did not decode as a status snapshot. */
    data class Raw(val text: String) : MusicSocketEvent

    /** The stream ended cleanly. */
    data object Closed : MusicSocketEvent

    /** The stream failed. */
    data class Failed(val cause: Throwable) : MusicSocketEvent
}

/**
 * Live music event stream, as Server-Sent Events from the dashboard's
 * `/api/music/stream` route. The dashboard opens the bot's events endpoint
 * server side with the credentials the bot's access filter expects and pipes
 * the frames through, so nothing here needs a WebSocket upgrade.
 *
 * Emits a [Flow]; collecting starts the stream and cancelling the collection
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
     * @param userId The acting Discord user. The dashboard resolves the user
     *   from the token, so this is not sent.
     */
    fun connect(
        baseUrl: String,
        instanceBotId: Snowflake?,
        guildId: Snowflake,
        @Suppress("UNUSED_PARAMETER") userId: Snowflake,
    ): Flow<MusicSocketEvent> = channelFlow {
        val token = runCatching { auth.currentAccessToken() }.getOrElse { cause ->
            send(MusicSocketEvent.Failed(cause))
            return@channelFlow
        }

        val url = "${baseUrl.trimEnd('/')}/api/music/stream?guildId=$guildId"
        Log.i(TAG, "connecting to ${url.substringBefore("?")}")

        try {
            http.prepareGet(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "text/event-stream")
                header("Cache-Control", "no-cache")
                if (instanceBotId != null) header("X-Mobile-Instance", instanceBotId)
                // The bot heartbeats every 30 seconds; the stream itself is open-ended.
                timeout {
                    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                    socketTimeoutMillis = 120_000
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    send(MusicSocketEvent.Failed(IllegalStateException("stream responded ${response.status.value}")))
                    return@execute
                }

                val channel = response.bodyAsChannel()
                var eventName = "message"
                val dataLines = ArrayList<String>()

                while (isActive) {
                    val line = channel.readUTF8Line() ?: break
                    when {
                        line.isEmpty() -> {
                            if (dataLines.isNotEmpty() && eventName == "status") {
                                send(decode(dataLines.joinToString("\n")))
                            }
                            eventName = "message"
                            dataLines.clear()
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> eventName = line.substring(6).trim()
                        line.startsWith("data:") -> dataLines.add(line.substring(5).trimStart(' '))
                    }
                }
                send(MusicSocketEvent.Closed)
            }
        } catch (cause: Throwable) {
            if (isActive) {
                Log.e(TAG, "stream failed: ${cause.message}")
                send(MusicSocketEvent.Failed(cause))
            }
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
