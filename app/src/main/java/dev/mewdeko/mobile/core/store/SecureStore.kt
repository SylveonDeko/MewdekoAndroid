package dev.mewdeko.mobile.core.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** An access/refresh token pair plus the access token's expiry. */
@Serializable
data class StoredTokens(
    val accessToken: String,
    val refreshToken: String,
    @Serializable(with = InstantSerializer::class) val accessExpiresAt: Instant,
)

/**
 * Persistent secure storage for the user's session tokens and profile,
 * backed by the Android Keystore via [EncryptedSharedPreferences].
 *
 * Entries are keyed per server id so switching dashboards preserves
 * credentials for each one.
 */
@Singleton
class SecureStore @Inject constructor(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "dev.mewdeko.mobile.secure",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun tokenKey(serverId: String) = "tokens.$serverId"

    private fun userKey(serverId: String) = "user.$serverId"

    /** Persists the token pair for [serverId]. */
    fun saveTokens(tokens: StoredTokens, serverId: String) {
        prefs.edit().putString(tokenKey(serverId), MewdekoJson.encodeToString(tokens)).apply()
    }

    /** Reads the token pair for [serverId], or `null` when none is stored. */
    fun loadTokens(serverId: String): StoredTokens? =
        prefs.getString(tokenKey(serverId), null)
            ?.let { runCatching { MewdekoJson.decodeFromString<StoredTokens>(it) }.getOrNull() }

    /** Removes the token pair for [serverId]. */
    fun clearTokens(serverId: String) {
        prefs.edit().remove(tokenKey(serverId)).apply()
    }

    /** Persists the signed-in user record for [serverId]. */
    fun saveUser(user: MobileUser, serverId: String) {
        prefs.edit().putString(userKey(serverId), MewdekoJson.encodeToString(user)).apply()
    }

    /** Reads the signed-in user record for [serverId], or `null` when absent. */
    fun loadUser(serverId: String): MobileUser? =
        prefs.getString(userKey(serverId), null)
            ?.let { runCatching { MewdekoJson.decodeFromString<MobileUser>(it) }.getOrNull() }

    /** Wipes both token and user records for a given server. */
    fun clearAll(serverId: String) {
        prefs.edit().remove(tokenKey(serverId)).remove(userKey(serverId)).apply()
    }
}
