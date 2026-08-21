package dev.mewdeko.mobile.core.net

import android.util.Log
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.model.Snowflake
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton
import io.ktor.http.HttpMethod as KtorMethod

private const val TAG = "MewdekoApi"

/**
 * Issues authenticated HTTP requests against the configured dashboard.
 *
 * A single mutable base URL and instance id guarded by a mutex, one
 * automatic refresh-and-retry on 401, and decoding performed off the main
 * dispatcher.
 */
@Singleton
class ApiClient @Inject constructor(
    private val http: HttpClient,
    private val auth: AuthManager,
) {
    private val stateLock = Mutex()
    private var instanceBotId: Snowflake? = null

    /**
     * Records the bot instance to attach to authenticated requests via the
     * `X-Mobile-Instance` header. Pass `null` to let the server pick, which
     * only works when the dashboard has exactly one instance configured.
     */
    suspend fun setInstance(botId: Snowflake?) = stateLock.withLock {
        this.instanceBotId = botId
    }

    /** The bot instance currently pinned for requests, if any. */
    suspend fun currentInstance(): Snowflake? = stateLock.withLock { instanceBotId }

    /**
     * The dashboard this client is pointed at, if configured.
     *
     * Delegates to [AuthManager] rather than keeping its own copy, so this
     * can never point at a different server than the one [auth] is issuing
     * tokens for.
     */
    suspend fun currentBaseUrl(): String? = auth.currentBaseUrl()

    /**
     * Issues a request and decodes the response with [strategy]. On a 401 the
     * auth manager is asked to refresh and the request is retried once;
     * further 401s surface as [ApiError.Unauthorized].
     */
    suspend fun <T> send(endpoint: Endpoint, strategy: DeserializationStrategy<T>): T {
        val raw = sendRaw(endpoint)
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.decodeFromJsonElement(strategy, raw.normalizeKeys())
            } catch (t: Throwable) {
                Log.e(TAG, "decode failure on ${endpoint.path}: ${t.message}")
                throw ApiError.Decoding(t)
            }
        }
    }

    /** Issues a request whose response body is not needed. */
    suspend fun sendIgnoringBody(endpoint: Endpoint) {
        perform(endpoint, allowRetry = endpoint.requiresAuth)
    }

    /** Issues a request and returns the parsed but undecoded JSON tree. */
    suspend fun sendRaw(endpoint: Endpoint): JsonElement {
        val text = perform(endpoint, allowRetry = endpoint.requiresAuth)
        if (text.isBlank()) return JsonObject(emptyMap())
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.parseToJsonElement(text)
            } catch (t: Throwable) {
                throw ApiError.Decoding(t)
            }
        }
    }

    /**
     * Returns the length of a top-level JSON array without materialising its
     * elements. Used by the overview to avoid decoding multi-megabyte member
     * and role-state payloads when only the row count is rendered.
     */
    suspend fun sendArrayCount(path: String): Int {
        val element = sendRaw(Endpoint(path))
        return element.firstArrayOrNull()?.size ?: 0
    }

    private suspend fun perform(endpoint: Endpoint, allowRetry: Boolean): String {
        val base = currentBaseUrl() ?: throw ApiError.NotConfigured
        val url = "$base/${endpoint.path.trimStart('/')}"

        val response: HttpResponse = try {
            http.request(url) {
                method = endpoint.method.toKtor()
                if (endpoint.body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(endpoint.body)
                }
                if (endpoint.requiresAuth) {
                    header("Authorization", "Bearer ${auth.currentAccessToken()}")
                    currentInstance()?.let { header("X-Mobile-Instance", it) }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "transport failure for ${endpoint.path}: ${t.message}")
            throw ApiError.Transport(t)
        }

        val status = response.status.value
        if (status == 401 && allowRetry) {
            Log.i(TAG, "401 on ${endpoint.path}, refreshing and retrying")
            runCatching { auth.refresh() }
            return perform(endpoint, allowRetry = false)
        }
        if (status == 401) throw ApiError.Unauthorized

        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.e(TAG, "HTTP $status on ${endpoint.path}: $text")
            throw ApiError.Http(status, text)
        }
        return text
    }

    private fun HttpMethod.toKtor(): KtorMethod = when (this) {
        HttpMethod.GET -> KtorMethod.Get
        HttpMethod.POST -> KtorMethod.Post
        HttpMethod.PUT -> KtorMethod.Put
        HttpMethod.PATCH -> KtorMethod.Patch
        HttpMethod.DELETE -> KtorMethod.Delete
    }
}

/** Reified convenience over [ApiClient.send]. */
suspend inline fun <reified T> ApiClient.get(path: String): T =
    send(Endpoint(path), MewdekoJson.serializersModule.serializer())

/** Reified convenience for a request with an explicit method and body. */
suspend inline fun <reified T> ApiClient.call(
    path: String,
    method: HttpMethod,
    body: String? = null,
): T = send(Endpoint(path, method, body), kotlinx.serialization.serializer())
