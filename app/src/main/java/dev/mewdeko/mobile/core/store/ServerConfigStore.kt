package dev.mewdeko.mobile.core.store

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverDataStore by preferencesDataStore("dev.mewdeko.mobile.servers")

/** A user-supplied dashboard the app talks to. */
@Serializable
data class ServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val baseUrl: String = "",
)

/** On-disk shape of the saved server list. */
@Serializable
data class ServerListPayload(
    val servers: List<ServerConfig> = emptyList(),
    val selectedId: String? = null,
) {
    /** The active server, falling back to the first known one. */
    val active: ServerConfig?
        get() = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
}

/**
 * Persistent storage for the user's known dashboards. Keeps a list plus a
 * currently selected id so per-server credentials outlive a switch.
 */
@Singleton
class ServerConfigStore @Inject constructor(private val context: Context) {

    private val listKey = stringPreferencesKey("serverList")

    /** Emits the saved server list whenever it changes. */
    val payload: Flow<ServerListPayload> = context.serverDataStore.data.map { prefs ->
        prefs[listKey]
            ?.let { runCatching { MewdekoJson.decodeFromString<ServerListPayload>(it) }.getOrNull() }
            ?: ServerListPayload()
    }

    /** Reads the current list once. */
    suspend fun snapshot(): ServerListPayload = payload.first()

    /** Returns the currently active server, if any. */
    suspend fun active(): ServerConfig? = snapshot().active

    /** Inserts or updates a server (matched by id) and marks it active. */
    suspend fun upsert(config: ServerConfig): ServerConfig {
        mutate { current ->
            val servers = current.servers.toMutableList()
            val index = servers.indexOfFirst { it.id == config.id }
            if (index >= 0) servers[index] = config else servers.add(config)
            ServerListPayload(servers, config.id)
        }
        return config
    }

    /** Marks an existing server active without changing its fields. */
    suspend fun setActive(id: String) = mutate { current ->
        if (current.servers.none { it.id == id }) current else current.copy(selectedId = id)
    }

    /**
     * Removes a server. If it was active, the first remaining one becomes
     * active instead.
     */
    suspend fun remove(id: String) = mutate { current ->
        val servers = current.servers.filterNot { it.id == id }
        val selected = if (current.selectedId == id) servers.firstOrNull()?.id else current.selectedId
        ServerListPayload(servers, selected)
    }

    /** Drops every saved server. */
    suspend fun clear() = mutate { ServerListPayload() }

    private suspend fun mutate(transform: (ServerListPayload) -> ServerListPayload) {
        context.serverDataStore.edit { prefs ->
            val current = prefs[listKey]
                ?.let { runCatching { MewdekoJson.decodeFromString<ServerListPayload>(it) }.getOrNull() }
                ?: ServerListPayload()
            prefs[listKey] = MewdekoJson.encodeToString(transform(current))
        }
    }
}
