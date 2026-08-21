package dev.mewdeko.mobile.feature.highlights

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject

/** A highlight word registered by a guild member. */
@Serializable
data class GuildHighlight(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val word: String = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A frequently-registered highlight word. */
@Serializable
data class TopWord(val word: String = "", val count: Int = 0)

/** A member with many highlight words. */
@Serializable
data class TopHighlightUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val highlightCount: Int = 0,
)

/** A recently-added highlight. */
@Serializable
data class RecentHighlight(
    val word: String = "",
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** Aggregate highlight statistics for the guild. */
@Serializable
data class HighlightsStats(
    val totalHighlights: Int = 0,
    val totalUsers: Int = 0,
    @SerialName("topHighlightedWords") val topWords: List<TopWord> = emptyList(),
    val topUsers: List<TopHighlightUser> = emptyList(),
    val recentHighlights: List<RecentHighlight> = emptyList(),
)

/** A member who has muted highlights or scoped them to fewer channels. */
@Serializable
data class DisabledHighlightUser(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val ignoredChannelsCount: Int = 0,
    val ignoredUsersCount: Int = 0,
)

/** One member's highlight words, grouped for display. */
data class HighlightGroup(
    val userId: Snowflake,
    val username: String,
    val words: List<GuildHighlight>,
)

/** Highlights screen state. */
data class HighlightsState(
    val all: List<GuildHighlight> = emptyList(),
    val stats: HighlightsStats? = null,
    val disabled: List<DisabledHighlightUser> = emptyList(),
    val searchResults: List<GuildHighlight> = emptyList(),
    val query: String = "",
    val section: String = "members",
) {
    /** Highlight words grouped by the member that registered them. */
    val grouped: List<HighlightGroup>
        get() = all.groupBy { it.userId }
            .map { (userId, entries) ->
                HighlightGroup(
                    userId = userId,
                    username = entries.firstOrNull()?.username ?: "Unknown",
                    words = entries.sortedBy { it.word.lowercase() },
                )
            }
            .sortedBy { it.username.lowercase() }
}

/** Guild-wide view of members' highlight words. */
@HiltViewModel
class HighlightsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(HighlightsState())

    /** Observable screen state. */
    val state: StateFlow<HighlightsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads every highlight, the stats block, and the disabled list. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val all = async {
                runCatching {
                    api.send(
                        Endpoint("api/Highlights/$guildId"),
                        ListSerializer(GuildHighlight.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/Highlights/$guildId/stats"),
                        HighlightsStats.serializer(),
                    )
                }.getOrNull()
            }
            val disabled = async {
                runCatching {
                    api.send(
                        Endpoint("api/Highlights/$guildId/disabled"),
                        ListSerializer(DisabledHighlightUser.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(all = all.await(), stats = stats.await(), disabled = disabled.await())
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Runs a server-side search, clearing results for a blank term. */
    fun search(term: String) = viewModelScope.launch {
        _state.update { it.copy(query = term) }
        val trimmed = term.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchResults = emptyList()) }
            return@launch
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val results = runCatching {
            api.send(
                Endpoint("api/Highlights/$guildId/search?searchTerm=$encoded"),
                ListSerializer(GuildHighlight.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(searchResults = results) }
    }

    /** Removes one highlight word. */
    fun delete(highlight: GuildHighlight) = launchAction("Failed to delete highlight.") {
        api.sendIgnoringBody(
            Endpoint("api/Highlights/$guildId/${highlight.id}", HttpMethod.DELETE)
        )
        _state.update { current ->
            current.copy(
                all = current.all.filterNot { it.id == highlight.id },
                searchResults = current.searchResults.filterNot { it.id == highlight.id },
            )
        }
        postSuccess("Deleted.")
    }

    /** Removes every highlight word registered by one member. */
    fun deleteAllForUser(userId: Snowflake) = launchAction("Failed to clear user highlights.") {
        api.sendIgnoringBody(
            Endpoint("api/Highlights/$guildId/user/$userId", HttpMethod.DELETE)
        )
        _state.update { current ->
            current.copy(
                all = current.all.filterNot { it.userId == userId },
                searchResults = current.searchResults.filterNot { it.userId == userId },
            )
        }
        postSuccess("Removed all highlights for that member.")
    }
}
