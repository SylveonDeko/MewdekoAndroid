package dev.mewdeko.mobile.feature.suggestions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.time.Instant
import javax.inject.Inject

/** Where a suggestion currently stands. */
enum class SuggestionState(val raw: Int, val label: String) {
    SUGGESTED(0, "Suggested"),
    ACCEPTED(1, "Accepted"),
    DENIED(2, "Denied"),
    CONSIDERED(3, "Considered"),
    IMPLEMENTED(4, "Implemented");

    companion object {
        /** Maps a wire value onto a state, defaulting to [SUGGESTED]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: SUGGESTED
    }
}

/** The submitting member, when the bot could resolve them. */
@Serializable
data class SuggestionUser(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake? = null,
    val username: String = "Unknown",
    val avatarUrl: String? = null,
)

/** Reaction tallies for a suggestion. */
@Serializable
data class SuggestionEmoteCounts(
    val emote1: Int = 0,
    val emote2: Int = 0,
    val emote3: Int = 0,
    val emote4: Int = 0,
    val emote5: Int = 0,
) {
    /** The tallies in display order. */
    val values: List<Int> get() = listOf(emote1, emote2, emote3, emote4, emote5)
}

/** One submitted suggestion. */
@Serializable
data class SuggestionRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val suggestionId: String = "0",
    val suggestion1: String = "",
    val currentState: Int = 0,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val stateChangeUser: Snowflake? = null,
    val stateChangeCount: Int = 0,
    val emoteCounts: SuggestionEmoteCounts? = null,
    val user: SuggestionUser? = null,
) {
    /** The suggestion's public number. */
    val number: Long get() = suggestionId.toLongOrNull() ?: 0L

    /** The typed form of [currentState]. */
    val state: SuggestionState get() = SuggestionState.from(currentState)
}

/** The editable suggestion settings, as one snapshot. */
data class SuggestionsSettings(
    val suggestChannel: Snowflake? = null,
    val acceptChannel: Snowflake? = null,
    val denyChannel: Snowflake? = null,
    val considerChannel: Snowflake? = null,
    val implementChannel: Snowflake? = null,
    val minLength: Int = 0,
    val maxLength: Int = 2000,
    val threadsType: Int = 0,
    val emoteMode: Int = 0,
    val acceptMessage: String = "",
    val denyMessage: String = "",
    val considerMessage: String = "",
    val implementMessage: String = "",
    val suggestionMessage: String = "",
    val emotes: String = "",
    val archiveOnAccept: Boolean = false,
    val archiveOnDeny: Boolean = false,
    val archiveOnConsider: Boolean = false,
    val archiveOnImplement: Boolean = false,
)

/** Suggestions screen state. */
data class SuggestionsState(
    val suggestions: List<SuggestionRecord> = emptyList(),
    val settings: SuggestionsSettings = SuggestionsSettings(),
    val loadedSettings: SuggestionsSettings = SuggestionsSettings(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val section: String = "list",
    val stateFilter: SuggestionState? = null,
) {
    /** Whether any setting differs from what the server has. */
    val hasUnsavedSettings: Boolean get() = settings != loadedSettings

    /** Suggestions matching the current state filter. */
    val visible: List<SuggestionRecord>
        get() = stateFilter?.let { filter -> suggestions.filter { it.state == filter } }
            ?: suggestions

    /** How many suggestions sit in each state. */
    fun countFor(state: SuggestionState): Int = suggestions.count { it.state == state }
}

/** The member suggestion box. */
@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(SuggestionsState())

    /** Observable screen state. */
    val state: StateFlow<SuggestionsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the suggestion list and every setting. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val suggestions = async {
                runCatching {
                    api.send(
                        Endpoint("api/Suggestions/$guildId"),
                        ListSerializer(SuggestionRecord.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val suggestCh = async { snowflake("suggestChannel") }
            val acceptCh = async { snowflake("acceptChannel") }
            val denyCh = async { snowflake("denyChannel") }
            val considerCh = async { snowflake("considerChannel") }
            val implementCh = async { snowflake("implementChannel") }
            val minLen = async { int("minLength") }
            val maxLen = async { int("maxLength") }
            val threads = async { int("suggestThreadsType") }
            val emoteMode = async { int("emoteMode") }
            val acceptMsg = async { text("acceptMessage") }
            val denyMsg = async { text("denyMessage") }
            val considerMsg = async { text("considerMessage") }
            val implementMsg = async { text("implementMessage") }
            val suggestMsg = async { text("suggestionMessage") }
            val emotes = async { text("suggestEmotes") }
            val archAccept = async { bool("archiveOnAccept") }
            val archDeny = async { bool("archiveOnDeny") }
            val archConsider = async { bool("archiveOnConsider") }
            val archImplement = async { bool("archiveOnImplement") }

            val snapshot = SuggestionsSettings(
                suggestChannel = suggestCh.await(),
                acceptChannel = acceptCh.await(),
                denyChannel = denyCh.await(),
                considerChannel = considerCh.await(),
                implementChannel = implementCh.await(),
                minLength = minLen.await() ?: 0,
                maxLength = maxLen.await() ?: 2000,
                threadsType = threads.await() ?: 0,
                emoteMode = emoteMode.await() ?: 0,
                acceptMessage = acceptMsg.await().orEmpty(),
                denyMessage = denyMsg.await().orEmpty(),
                considerMessage = considerMsg.await().orEmpty(),
                implementMessage = implementMsg.await().orEmpty(),
                suggestionMessage = suggestMsg.await().orEmpty(),
                emotes = emotes.await().orEmpty(),
                archiveOnAccept = archAccept.await(),
                archiveOnDeny = archDeny.await(),
                archiveOnConsider = archConsider.await(),
                archiveOnImplement = archImplement.await(),
            )

            _state.update {
                it.copy(
                    suggestions = suggestions.await().sortedByDescending { entry -> entry.number },
                    settings = snapshot,
                    loadedSettings = snapshot,
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Filters the list by state, or clears the filter with null. */
    fun setStateFilter(state: SuggestionState?) = _state.update { it.copy(stateFilter = state) }

    /** Applies an edit to the staged settings. */
    fun edit(transform: (SuggestionsSettings) -> SuggestionsSettings) =
        _state.update { it.copy(settings = transform(it.settings)) }

    /**
     * Writes every setting that changed.
     *
     * The bot exposes one endpoint per setting, so this diffs against the
     * loaded snapshot and only sends what actually differs.
     */
    fun saveSettings() = viewModelScope.launch {
        val current = _state.value.settings
        val loaded = _state.value.loadedSettings
        var ok = true

        suspend fun send(tail: String, body: String) {
            ok = ok && runCatching {
                api.sendIgnoringBody(
                    Endpoint("api/Suggestions/$guildId/$tail", HttpMethod.POST, body)
                )
            }.isSuccess
        }

        if (current.minLength != loaded.minLength) send("minLength", jsonInt(current.minLength))
        if (current.maxLength != loaded.maxLength) send("maxLength", jsonInt(current.maxLength))
        if (current.suggestChannel != loaded.suggestChannel) {
            send("suggestChannel", (current.suggestChannel?.toLongOrNull() ?: 0L).toString())
        }
        if (current.acceptChannel != loaded.acceptChannel) {
            send("acceptChannel", (current.acceptChannel?.toLongOrNull() ?: 0L).toString())
        }
        if (current.denyChannel != loaded.denyChannel) {
            send("denyChannel", (current.denyChannel?.toLongOrNull() ?: 0L).toString())
        }
        if (current.considerChannel != loaded.considerChannel) {
            send("considerChannel", (current.considerChannel?.toLongOrNull() ?: 0L).toString())
        }
        if (current.implementChannel != loaded.implementChannel) {
            send("implementChannel", (current.implementChannel?.toLongOrNull() ?: 0L).toString())
        }
        if (current.acceptMessage != loaded.acceptMessage) {
            send("acceptMessage", jsonString(current.acceptMessage))
        }
        if (current.denyMessage != loaded.denyMessage) {
            send("denyMessage", jsonString(current.denyMessage))
        }
        if (current.considerMessage != loaded.considerMessage) {
            send("considerMessage", jsonString(current.considerMessage))
        }
        if (current.implementMessage != loaded.implementMessage) {
            send("implementMessage", jsonString(current.implementMessage))
        }
        if (current.suggestionMessage != loaded.suggestionMessage) {
            send("suggestionMessage", jsonString(current.suggestionMessage))
        }
        if (current.emotes != loaded.emotes) send("suggestEmotes", jsonString(current.emotes))
        if (current.archiveOnAccept != loaded.archiveOnAccept) {
            send("archiveOnAccept", jsonBool(current.archiveOnAccept))
        }
        if (current.archiveOnDeny != loaded.archiveOnDeny) {
            send("archiveOnDeny", jsonBool(current.archiveOnDeny))
        }
        if (current.archiveOnConsider != loaded.archiveOnConsider) {
            send("archiveOnConsider", jsonBool(current.archiveOnConsider))
        }
        if (current.archiveOnImplement != loaded.archiveOnImplement) {
            send("archiveOnImplement", jsonBool(current.archiveOnImplement))
        }

        if (ok) {
            _state.update { it.copy(loadedSettings = it.settings) }
            postSuccess("Settings saved.")
        } else {
            postError("Some settings failed to save.")
        }
    }

    /** Moves a suggestion into a new state, with an optional reason. */
    fun setState(suggestion: SuggestionRecord, state: SuggestionState, reason: String?) =
        launchAction("Failed to update suggestion.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Suggestions/$guildId/${suggestion.number}",
                    HttpMethod.PATCH,
                    jsonBody(
                        "state" to state.raw,
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "reason" to reason.orEmpty(),
                    ),
                )
            )
            _state.update { current ->
                current.copy(
                    suggestions = current.suggestions.map {
                        if (it.id == suggestion.id) it.copy(currentState = state.raw) else it
                    },
                )
            }
            postSuccess("Marked ${state.label.lowercase()}.")
        }

    /** Deletes one suggestion. */
    fun delete(suggestion: SuggestionRecord) = launchAction("Failed to delete suggestion.") {
        api.sendIgnoringBody(
            Endpoint("api/Suggestions/$guildId/${suggestion.number}", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(suggestions = it.suggestions.filterNot { entry -> entry.id == suggestion.id })
        }
        postSuccess("Suggestion deleted.")
    }

    /** Deletes every suggestion. */
    fun clearAll() = launchAction("Failed to clear suggestions.") {
        api.sendIgnoringBody(Endpoint("api/Suggestions/$guildId/clear", HttpMethod.DELETE))
        _state.update { it.copy(suggestions = emptyList()) }
        postSuccess("All suggestions cleared.")
    }

    private suspend fun int(tail: String): Int? = runCatching {
        api.send(Endpoint("api/Suggestions/$guildId/$tail"), Int.serializer())
    }.getOrNull()

    private suspend fun bool(tail: String): Boolean = runCatching {
        api.send(Endpoint("api/Suggestions/$guildId/$tail"), Boolean.serializer())
    }.getOrDefault(false)

    private suspend fun text(tail: String): String? = runCatching {
        api.send(Endpoint("api/Suggestions/$guildId/$tail"), ScalarString.serializer()).value
    }.getOrNull()

    private suspend fun snowflake(tail: String): Snowflake? =
        text(tail)?.takeIf { it.isNotEmpty() && it != "0" }
}
