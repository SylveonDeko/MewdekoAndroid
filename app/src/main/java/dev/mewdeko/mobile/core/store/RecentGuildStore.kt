package dev.mewdeko.mobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentsDataStore by preferencesDataStore("dev.mewdeko.mobile.recents")

/**
 * Remembers the guild each user opened last, so the Servers screen can offer
 * it as "Jump back in" across launches.
 */
@Singleton
class RecentGuildStore @Inject constructor(private val context: Context) {

    private fun key(userId: String) = stringPreferencesKey("lastGuild.$userId")

    /** Emits the last opened guild id for [userId], or `null` when none. */
    fun lastGuildId(userId: String): Flow<String?> =
        context.recentsDataStore.data
            .map { it[key(userId)]?.takeIf(String::isNotEmpty) }
            .distinctUntilChanged()

    /** Records [guildId] as the guild [userId] opened last. */
    suspend fun record(userId: String, guildId: String) {
        if (userId.isEmpty() || guildId.isEmpty()) return
        context.recentsDataStore.edit { it[key(userId)] = guildId }
    }
}
