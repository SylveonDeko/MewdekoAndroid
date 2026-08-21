package dev.mewdeko.mobile.core.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE helpers (RFC 7636) using the S256 challenge method. */
object Pkce {

    private val random = SecureRandom()

    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /**
     * Generates a cryptographically-random code verifier.
     *
     * @param byteLength Random bytes to draw before base64url encoding.
     */
    fun makeVerifier(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, FLAGS)
    }

    /** Derives the S256 challenge for the given verifier. */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, FLAGS)
    }
}
