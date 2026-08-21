package dev.mewdeko.mobile.feature.guildlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.userFacingMessage
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
) {
    /** The guilds matching the current search query. */
    val visibleGuilds: List<Guild>
        get() = if (query.isBlank()) guilds
        else guilds.filter { it.name.contains(query, ignoreCase = true) }
}

/** Loads the user's mutual-with-bot, admin-permission guilds. */
@HiltViewModel
class GuildListViewModel @Inject constructor(
    private val api: ApiClient,
    private val session: SessionHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(GuildListState())

    /** Observable screen state. */
    val state: StateFlow<GuildListState> = _state.asStateFlow()

    init {
        load()
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
}
