package dev.mewdeko.mobile.feature.filter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import javax.inject.Inject

/** Message Filters screen state. */
data class FilterState(
    val section: String = "filters",
    val settings: FilterSettings = FilterSettings(),
    val channels: List<TextChannelLite> = emptyList(),
    val newWord: String = "",
    val newAutoBanWord: String = "",
    val wordSearch: String = "",
    val isSaving: Boolean = false,
) {
    /** Filtered words matching the search box, case-insensitively. */
    val visibleFilteredWords: List<String>
        get() {
            val term = wordSearch.trim().lowercase()
            return if (term.isEmpty()) settings.filteredWords
            else settings.filteredWords.filter { it.lowercase().contains(term) }
        }

    /** Total channel overrides across all three filters. */
    val channelOverrideCount: Int
        get() = settings.channelSettings.let {
            it.wordFilterChannels.size + it.inviteFilterChannels.size + it.linkFilterChannels.size
        }

    /** Display name for a channel id, falling back to the raw id. */
    fun channelName(id: Snowflake): String = channels.firstOrNull { it.id == id }?.name ?: id
}

/** Loads and edits the guild's word, invite, and link filters, word lists, and channel overrides. */
@HiltViewModel
class FilterViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(FilterState())

    /** Observable screen state. */
    val state: StateFlow<FilterState> = _state.asStateFlow()

    private val base = "api/Filter/$guildId"

    init {
        load()
    }

    /** Loads filter settings and the guild's text channels. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val settings = async { api.send(Endpoint("$base/settings"), FilterSettings.serializer()) }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val loaded = settings.await()
            val channelList = channels.await().sortedBy { it.name.lowercase() }
            _state.update { it.copy(settings = loaded, channels = channelList) }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Updates the pending filtered word. */
    fun setNewWord(value: String) = _state.update { it.copy(newWord = value) }

    /** Updates the pending auto-ban word. */
    fun setNewAutoBanWord(value: String) = _state.update { it.copy(newAutoBanWord = value) }

    /** Updates the filtered word search term. */
    fun setWordSearch(value: String) = _state.update { it.copy(wordSearch = value) }

    /** Flips one server-wide filter, sending all three flags as the bot expects. */
    fun toggleServerFilter(kind: FilterKind) = viewModelScope.launch {
        val current = _state.value.settings.serverSettings
        val next = when (kind) {
            FilterKind.WORD -> current.copy(filterWords = !current.filterWords)
            FilterKind.INVITE -> current.copy(filterInvites = !current.filterInvites)
            FilterKind.LINK -> current.copy(filterLinks = !current.filterLinks)
        }
        val body = buildJsonObject {
            put("filterWords", JsonPrimitive(next.filterWords))
            put("filterInvites", JsonPrimitive(next.filterInvites))
            put("filterLinks", JsonPrimitive(next.filterLinks))
        }
        _state.update { it.copy(isSaving = true) }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("$base/server-settings", HttpMethod.PUT, MewdekoJson.encodeToString(JsonObject.serializer(), body))
            )
        }.isSuccess
        _state.update { s ->
            if (ok) s.copy(isSaving = false, settings = s.settings.copy(serverSettings = next))
            else s.copy(isSaving = false)
        }
        if (!ok) postError("Failed to update filter.")
    }

    /** Flips one warning toggle, sending only that key. */
    fun toggleWarning(warning: FilterWarning) = viewModelScope.launch {
        val value = !warning.value(_state.value.settings.serverSettings)
        val body = buildJsonObject { put(warning.key, JsonPrimitive(value)) }
        _state.update { it.copy(isSaving = true) }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("$base/warnings", HttpMethod.PUT, MewdekoJson.encodeToString(JsonObject.serializer(), body))
            )
        }.isSuccess
        _state.update { s ->
            if (ok) s.copy(
                isSaving = false,
                settings = s.settings.copy(serverSettings = warning.apply(s.settings.serverSettings, value)),
            )
            else s.copy(isSaving = false)
        }
        if (!ok) postError("Failed to update warning setting.")
    }

    /** Adds the pending filtered word. */
    fun addFilteredWord() = toggleWord(_state.value.newWord, autoBan = false)

    /** Adds the pending auto-ban word. */
    fun addAutoBanWord() = toggleWord(_state.value.newAutoBanWord, autoBan = true)

    /** Removes a word from the filtered or auto-ban list. */
    fun removeWord(word: String, autoBan: Boolean) = toggleWord(word, autoBan)

    /** Toggles a word through the bot, which adds it when missing and removes it when present. */
    private fun toggleWord(raw: String, autoBan: Boolean) = viewModelScope.launch {
        val clean = raw.trim().lowercase()
        if (clean.isEmpty()) return@launch
        val segment = if (autoBan) "autoban-words" else "words"
        _state.update { it.copy(isSaving = true) }
        val result = runCatching {
            api.send(
                Endpoint("$base/$segment/${encodePathSegment(clean)}", HttpMethod.POST),
                FilterWordToggleResult.serializer(),
            )
        }
        result.onSuccess { toggled ->
            val word = toggled.word.ifEmpty { clean }
            _state.update { s ->
                val list = if (autoBan) s.settings.autoBanWords else s.settings.filteredWords
                val updated = if (toggled.added) {
                    if (list.contains(word)) list else list + word
                } else {
                    list.filterNot { it == word }
                }
                s.copy(
                    isSaving = false,
                    settings = if (autoBan) s.settings.copy(autoBanWords = updated)
                    else s.settings.copy(filteredWords = updated),
                    newWord = if (autoBan) s.newWord else "",
                    newAutoBanWord = if (autoBan) "" else s.newAutoBanWord,
                )
            }
        }.onFailure {
            _state.update { it.copy(isSaving = false) }
            postError("Failed to update word list.")
        }
    }

    /** Removes every filtered word. */
    fun clearFilteredWords() = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/words", HttpMethod.DELETE))
        }.isSuccess
        _state.update { s ->
            if (ok) s.copy(isSaving = false, wordSearch = "", settings = s.settings.copy(filteredWords = emptyList()))
            else s.copy(isSaving = false)
        }
        if (!ok) postError("Failed to clear words.")
    }

    /** Adds or removes a channel override for one filter. */
    fun toggleChannel(kind: FilterKind, channelId: Snowflake?) = viewModelScope.launch {
        if (channelId.isNullOrEmpty()) return@launch
        _state.update { it.copy(isSaving = true) }
        val result = runCatching {
            api.send(
                Endpoint("$base/channels/$channelId/${kind.pathSegment}-filter", HttpMethod.POST),
                FilterChannelToggleResult.serializer(),
            )
        }
        result.onSuccess { toggled ->
            _state.update { s ->
                val current = kind.channels(s.settings.channelSettings)
                val updated = if (toggled.enabled) {
                    if (current.contains(channelId)) current else current + channelId
                } else {
                    current.filterNot { it == channelId }
                }
                s.copy(
                    isSaving = false,
                    settings = s.settings.copy(channelSettings = kind.withChannels(s.settings.channelSettings, updated)),
                )
            }
        }.onFailure {
            _state.update { it.copy(isSaving = false) }
            postError("Failed to update channel filter.")
        }
    }

    /** Percent-encodes a path segment, keeping spaces as `%20` rather than `+`. */
    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
