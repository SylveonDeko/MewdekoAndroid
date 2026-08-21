package dev.mewdeko.mobile.core.auth

import android.util.Log
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.model.RemoteMobileConfig
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.core.store.SecureStore
import dev.mewdeko.mobile.core.store.StoredTokens
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MewdekoAuth"

/** Errors surfaced by [AuthManager]. */
sealed class AuthError(message: String) : Exception(message) {
    /** No active server has been selected. */
    data object NotConfigured : AuthError("No server configured")

    /** The dashboard rejected the authorization code exchange. */
    data class LoginFailed(val status: Int, val body: String) : AuthError("Login failed ($status): $body")

    /** The refresh token was rejected. */
    data class RefreshFailed(val status: Int) : AuthError("Refresh failed ($status)")

    /** No tokens are stored for the active server. */
    data object MissingTokens : AuthError("No stored credentials")
}

/** A freshly minted access/refresh token pair plus, on initial login, the user. */
@Serializable
data class LoginResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Int = 0,
    val user: MobileUser? = null,
)

@Serializable
private data class LoginRequest(
    val code: String,
    val codeVerifier: String,
    val redirectUri: String,
)

@Serializable
private data class RefreshRequest(val refreshToken: String)

/** Body for redeeming a store-reviewer demo code. */
@Serializable
private data class DemoLoginRequest(val code: String)

/**
 * Coordinates token persistence, refresh, and PKCE sign-in for the currently
 * active server. Tokens and user records are keyed per server id so switching
 * dashboards preserves credentials for each one.
 */
@Singleton
class AuthManager @Inject constructor(
    private val store: SecureStore,
    private val http: HttpClient,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private var baseUrl: String? = null
    private var serverId: String? = null
    private var remoteConfig: RemoteMobileConfig? = null
    private var inflightRefresh: Deferred<StoredTokens>? = null

    /** Seconds of remaining validity below which the token is refreshed early. */
    private val earlyRefreshSlack = 30L

    /**
     * Sets the active dashboard endpoint and the server id its credentials are
     * stored under.
     */
    suspend fun configure(baseUrl: String, serverId: String) = lock.withLock {
        this.baseUrl = baseUrl.trimEnd('/')
        this.serverId = serverId
        this.remoteConfig = null
    }

    /** Stores the dashboard's public mobile config for later use. */
    suspend fun applyRemote(config: RemoteMobileConfig) = lock.withLock {
        this.remoteConfig = config
    }

    /** The dashboard's published mobile config, if it has been fetched. */
    suspend fun currentRemoteConfig(): RemoteMobileConfig? = lock.withLock { remoteConfig }

    /** Whether tokens exist for the active server. */
    suspend fun hasStoredTokens(): Boolean {
        val id = lock.withLock { serverId } ?: return false
        return store.loadTokens(id) != null
    }

    /** The cached user record for the active server, if any. */
    suspend fun storedUser(): MobileUser? {
        val id = lock.withLock { serverId } ?: return null
        return store.loadUser(id)
    }

    /** Returns a valid access token, refreshing it when near expiry. */
    suspend fun currentAccessToken(): String {
        val id = lock.withLock { serverId } ?: throw AuthError.NotConfigured
        val tokens = store.loadTokens(id) ?: throw AuthError.MissingTokens
        val secondsLeft = tokens.accessExpiresAt.epochSecond - Instant.now().epochSecond
        if (secondsLeft > earlyRefreshSlack) return tokens.accessToken
        return refresh().accessToken
    }

    /**
     * Refreshes the token pair, coalescing concurrent callers onto a single
     * in-flight request.
     */
    suspend fun refresh(): StoredTokens {
        val existing = lock.withLock { inflightRefresh }
        if (existing != null && existing.isActive) return existing.await()

        val task = scope.async { performRefresh() }
        lock.withLock { inflightRefresh = task }
        return try {
            task.await()
        } finally {
            lock.withLock { if (inflightRefresh === task) inflightRefresh = null }
        }
    }

    /**
     * Redeems a demo code for a session, standing in for a Discord sign-in.
     *
     * @param code The code published to app store reviewers.
     * @return The signed-in user.
     * @throws AuthError.LoginFailed when the code is rejected.
     */
    suspend fun signInWithDemoCode(code: String): MobileUser {
        val (base, id) = lock.withLock { baseUrl to serverId }
        if (base == null || id == null) throw AuthError.NotConfigured

        val response = http.post("$base/api/mobile/auth/demo") {
            contentType(ContentType.Application.Json)
            setBody(MewdekoJson.encodeToString(DemoLoginRequest(code.trim())))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.e(TAG, "demo login failed: HTTP ${response.status.value}")
            throw AuthError.LoginFailed(response.status.value, text)
        }

        val decoded = MewdekoJson.decodeFromJsonElement(
            LoginResponse.serializer(),
            MewdekoJson.parseToJsonElement(text).normalizeKeys(),
        )
        store.saveTokens(decoded.toStoredTokens(), id)
        val user = decoded.user ?: throw AuthError.LoginFailed(-1, "no user")
        store.saveUser(user, id)
        return user
    }

    /** Exchanges an authorization code for tokens and returns the signed-in user. */
    suspend fun signIn(authorization: OAuthResult): MobileUser {
        val (base, id) = lock.withLock { baseUrl to serverId }
        if (base == null || id == null) throw AuthError.NotConfigured

        val response = http.post("$base/api/mobile/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                MewdekoJson.encodeToString(
                    LoginRequest(
                        code = authorization.code,
                        codeVerifier = authorization.verifier,
                        redirectUri = authorization.redirectUri,
                    )
                )
            )
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.e(TAG, "login failed: HTTP ${response.status.value}, $text")
            throw AuthError.LoginFailed(response.status.value, text)
        }

        val decoded = MewdekoJson.decodeFromJsonElement(
            LoginResponse.serializer(),
            MewdekoJson.parseToJsonElement(text).normalizeKeys(),
        )
        store.saveTokens(decoded.toStoredTokens(), id)
        val user = decoded.user ?: throw AuthError.LoginFailed(-1, "no user")
        store.saveUser(user, id)
        return user
    }

    /**
     * Revokes the session on the active server and clears its local
     * credentials. Other servers' credentials are left alone.
     */
    suspend fun signOut(revokeOnServer: Boolean = true) {
        val (base, id) = lock.withLock { baseUrl to serverId }
        if (id == null) return
        val tokens = store.loadTokens(id)
        if (revokeOnServer && base != null && tokens != null) {
            runCatching {
                http.post("$base/api/mobile/auth/logout") {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer ${tokens.accessToken}")
                    setBody(MewdekoJson.encodeToString(RefreshRequest(tokens.refreshToken)))
                }
            }
        }
        store.clearTokens(id)
    }

    private suspend fun performRefresh(): StoredTokens {
        val (base, id) = lock.withLock { baseUrl to serverId }
        if (base == null || id == null) throw AuthError.NotConfigured
        val existing = store.loadTokens(id) ?: throw AuthError.MissingTokens

        val response = http.post("$base/api/mobile/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(MewdekoJson.encodeToString(RefreshRequest(existing.refreshToken)))
        }
        if (!response.status.isSuccess()) {
            Log.e(TAG, "refresh failed: HTTP ${response.status.value}")
            throw AuthError.RefreshFailed(response.status.value)
        }
        val decoded = MewdekoJson.decodeFromJsonElement(
            LoginResponse.serializer(),
            MewdekoJson.parseToJsonElement(response.bodyAsText()).normalizeKeys(),
        )
        val stored = decoded.toStoredTokens()
        store.saveTokens(stored, id)
        return stored
    }

    private fun LoginResponse.toStoredTokens() = StoredTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        accessExpiresAt = Instant.now().plusSeconds(expiresIn.toLong()),
    )
}
