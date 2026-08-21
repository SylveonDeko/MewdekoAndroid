package dev.mewdeko.mobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

private val Context.instanceDataStore by preferencesDataStore("dev.mewdeko.mobile.instances")

/**
 * Remembers which bot instance the user picked per dashboard, so a dashboard
 * hosting several bots reopens on the same one.
 */
@Singleton
class InstanceStore @Inject constructor(private val context: Context) {

    private fun key(serverId: String) = stringPreferencesKey("selectedInstance.$serverId")

    /** Persists the chosen instance for [serverId]. */
    suspend fun save(instance: MobileInstance, serverId: String) {
        context.instanceDataStore.edit { it[key(serverId)] = MewdekoJson.encodeToString(instance) }
    }

    /** Reads the remembered instance for [serverId], or `null` when none. */
    suspend fun load(serverId: String): MobileInstance? {
        val raw = context.instanceDataStore.data.first()[key(serverId)] ?: return null
        return runCatching { MewdekoJson.decodeFromString<MobileInstance>(raw) }.getOrNull()
    }

    /** Forgets the remembered instance for [serverId]. */
    suspend fun clear(serverId: String) {
        context.instanceDataStore.edit { it.remove(key(serverId)) }
    }
}
