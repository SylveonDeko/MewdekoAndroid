package dev.mewdeko.mobile.feature.guildlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.store.RecentGuildStore
import dev.mewdeko.mobile.core.ui.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** Guild list screen state. */
data class GuildListState(
    val guilds: List<Guild> = emptyList(),
    val query: String = "",
    val load: LoadState = LoadState(),
    val lastGuildId: String? = null,
    val inviteUrl: String? = null,
) {
    /** The query without surrounding whitespace. */
    val trimmedQuery: String
        get() = query.trim()

    /** Whether a search is narrowing the grid. */
    val isSearching: Boolean
        get() = trimmedQuery.isNotEmpty()

    /** The guilds matching the current search query. */
    val visibleGuilds: List<Guild>
        get() = if (!isSearching) guilds
        else guilds.filter { it.name.contains(trimmedQuery, ignoreCase = true) }

    /** How many of the guilds the user owns. */
    val ownedCount: Int
        get() = guilds.count { it.owner }

    /** The guild opened last, when it is still in the list. */
    val recentGuild: Guild?
        get() = lastGuildId?.let { id -> guilds.firstOrNull { it.id == id } }
}

/** Loads the user's mutual-with-bot, admin-permission guilds. */
@HiltViewModel
class GuildListViewModel @Inject constructor(
    private val api: ApiClient,
    private val session: SessionHolder,
    private val recents: RecentGuildStore,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GuildListState())

    /** Observable screen state. */
    val state: StateFlow<GuildListState> = _state.asStateFlow()

    /** The persisted last opened guild id, which may be newer than the one on screen. */
    private var storedLastGuildId: String? = null

    /**
     * Whether a guild was opened from this screen and the screen has not
     * come back yet. While set, a newly recorded recent is held back so the
     * grid does not reflow under the outgoing navigation transition.
     */
    private var awayInGuild = false

    init {
        load()
        viewModelScope.launch {
            recents.lastGuildId(session.userId).collect { id ->
                storedLastGuildId = id
                if (!awayInGuild) _state.update { it.copy(lastGuildId = id) }
            }
        }
        viewModelScope.launch {
            val inviteUrl = authManager.currentRemoteConfig()?.instance?.inviteUrl
            if (!inviteUrl.isNullOrBlank()) _state.update { it.copy(inviteUrl = inviteUrl) }
        }
    }

    /** Fetches the guild list, optionally as a pull to refresh. */
    fun load(refreshing: Boolean = false) = viewModelScope.launch {
        _state.update { it.copy(load = it.load.loading(refreshing)) }
        try {
            val guilds = api.send(
                Endpoint("api/ClientOperations/mutualguilds/${session.userId}?adminOnly=true"),
                ListSerializer(Guild.serializer()),
            )
            _state.update {
                it.copy(
                    guilds = guilds.sortedBy { guild -> guild.name.lowercase() },
                    load = it.load.loaded(),
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(load = it.load.failed(t.userFacingMessage)) }
        }
    }

    /** Updates the search query. */
    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /** Remembers [guild] as the last opened guild, for "Jump back in". */
    fun recordOpened(guild: Guild) {
        awayInGuild = true
        storedLastGuildId = guild.id
        val userId = session.userId
        viewModelScope.launch { recents.record(userId, guild.id) }
    }

    /** Shows the latest recorded recent once the screen is visible again. */
    fun onScreenShown() {
        awayInGuild = false
        _state.update { it.copy(lastGuildId = storedLastGuildId) }
    }
}
