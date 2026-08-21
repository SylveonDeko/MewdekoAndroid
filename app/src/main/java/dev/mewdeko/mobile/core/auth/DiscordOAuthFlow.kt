package dev.mewdeko.mobile.core.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dev.mewdeko.mobile.core.model.RemoteMobileConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A Discord authorization code together with the PKCE verifier used to obtain it. */
data class OAuthResult(
    val code: String,
    val verifier: String,
    val redirectUri: String,
)

/** Errors surfaced from [DiscordOAuthFlow]. */
sealed class DiscordOAuthError(message: String) : Exception(message) {
    /** The user dismissed the browser without authorizing. */
    data object UserCancelled : DiscordOAuthError("Sign-in cancelled")

    /** The callback URL was missing a code, or its state did not match. */
    data object MalformedCallback : DiscordOAuthError("Malformed OAuth callback")

    /** Discord returned an explicit error in the callback. */
    data class DiscordRejected(val reason: String) : DiscordOAuthError("Discord error: $reason")
}

/**
 * Drives the Discord PKCE Authorization Code flow through a Custom Tab.
 *
 * The browser returns to the app via the `mewdeko-mobile://oauth/callback`
 * deep link, which [MainActivity] forwards to [handleCallback]. Presentation
 * and result delivery are necessarily separate calls, since the redirect
 * re-enters the app through an Intent rather than a single blocking call.
 */
@Singleton
class DiscordOAuthFlow @Inject constructor() {

    private val lock = Mutex()
    private var pending: PendingAuthorization? = null

    private data class PendingAuthorization(
        val state: String,
        val verifier: String,
        val redirectUri: String,
        val deferred: CompletableDeferred<OAuthResult>,
    )

    /**
     * Opens the Discord consent screen and suspends until the deep link comes
     * back, returning the authorization code and PKCE verifier.
     */
    suspend fun authorize(context: Context, config: RemoteMobileConfig.Discord): OAuthResult {
        val verifier = Pkce.makeVerifier()
        val challenge = Pkce.challenge(verifier)
        val state = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<OAuthResult>()

        lock.withLock {
            pending?.deferred?.completeExceptionally(DiscordOAuthError.UserCancelled)
            pending = PendingAuthorization(state, verifier, config.redirectUri, deferred)
        }

        val authUrl = Uri.parse(config.authorizeUrl).buildUpon()
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("scope", config.scopes)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .build()
            .launchUrl(context, authUrl)

        return deferred.await()
    }

    /**
     * Completes a suspended [authorize] call with the redirect the browser
     * delivered. Returns `true` when the URI belonged to an in-flight request.
     */
    suspend fun handleCallback(uri: Uri): Boolean = lock.withLock {
        val current = pending ?: return@withLock false
        pending = null

        val returnedState = uri.getQueryParameter("state")
        if (returnedState != current.state) {
            current.deferred.completeExceptionally(DiscordOAuthError.MalformedCallback)
            return@withLock true
        }
        uri.getQueryParameter("error")?.let { error ->
            current.deferred.completeExceptionally(DiscordOAuthError.DiscordRejected(error))
            return@withLock true
        }
        val code = uri.getQueryParameter("code")
        if (code == null) {
            current.deferred.completeExceptionally(DiscordOAuthError.MalformedCallback)
            return@withLock true
        }
        current.deferred.complete(
            OAuthResult(code = code, verifier = current.verifier, redirectUri = current.redirectUri)
        )
        true
    }

    /**
     * Fails any in-flight authorization. Called when the app resumes without a
     * callback, which means the user backed out of the Custom Tab.
     */
    suspend fun cancelPending() = lock.withLock {
        pending?.deferred?.completeExceptionally(DiscordOAuthError.UserCancelled)
        pending = null
    }

    /** Whether an authorization is currently waiting on a redirect. */
    suspend fun hasPending(): Boolean = lock.withLock { pending != null }
}
