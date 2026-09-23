package dev.mewdeko.mobile.feature.wordoftheday

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
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
import javax.inject.Inject

/** Word of the Day screen state. */
data class WordOfTheDayState(
    val section: String = "settings",
    val channelId: Snowflake? = null,
    val enabled: Boolean = false,
    val postHour: Int = 9,
    val timezone: String = "UTC",
    val pingRoleId: Snowflake? = null,
    val message: EmbedMessage = EmbedMessage(),
    val topic: String = "",
    val partOfSpeech: Int = 0,
    val difficulty: Int = 0,
    val sourceMode: Int = 0,
    val createThread: Boolean = false,
    val threadName: String = "",
    val threadAutoArchiveMinutes: Int = ThreadAutoArchive.DAY.minutes,
    val lastPosted: String = "Never",
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val words: List<WordOfTheDayWord> = emptyList(),
    val history: List<WordOfTheDayHistoryEntry> = emptyList(),
    val dayDrafts: List<RuleDraft> = emptyList(),
    val monthDrafts: List<RuleDraft> = emptyList(),
    val newWord: String = "",
    val newDefinition: String = "",
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val isPosting: Boolean = false,
    val isAddingWord: Boolean = false,
)

/** Loads and edits the guild's Word of the Day configuration, rules, custom words, and history. */
@HiltViewModel
class WordOfTheDayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    private val session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(WordOfTheDayState())

    /** Observable screen state. */
    val state: StateFlow<WordOfTheDayState> = _state.asStateFlow()

    private val base = "api/wordoftheday/$guildId"

    init {
        load()
    }

    /** Loads configuration, custom words, history, rules, and the guild's channels and roles. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val config = async {
                runCatching { api.send(Endpoint("$base/config"), WordOfTheDayConfig.serializer()) }.getOrNull()
            }
            val words = async { fetchWords() }
            val history = async { fetchHistory() }
            val rules = async { fetchRules() }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val cfg = config.await()
            val ruleList = rules.await()
            _state.update {
                it.copy(
                    channelId = cfg?.channelId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    enabled = cfg?.enabled ?: false,
                    postHour = cfg?.postHour ?: 9,
                    timezone = cfg?.timezone?.takeIf { tz -> tz.isNotBlank() } ?: "UTC",
                    pingRoleId = cfg?.pingRoleId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    message = EmbedMessage.parse(cfg?.messageTemplate),
                    topic = cfg?.topic.orEmpty(),
                    partOfSpeech = cfg?.partOfSpeech ?: 0,
                    difficulty = cfg?.difficulty ?: 0,
                    sourceMode = cfg?.sourceMode ?: 0,
                    createThread = cfg?.createThread ?: false,
                    threadName = cfg?.threadName.orEmpty(),
                    threadAutoArchiveMinutes = cfg?.threadAutoArchiveMinutes ?: ThreadAutoArchive.DAY.minutes,
                    lastPosted = cfg?.lastPostedDate?.let { d -> d.toString().take(10) } ?: "Never",
                    availableChannels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    words = words.await(),
                    history = history.await(),
                    dayDrafts = buildDrafts(ScheduleRuleType.DAY_OF_WEEK, ruleList),
                    monthDrafts = buildDrafts(ScheduleRuleType.MONTH, ruleList),
                    hasUnsavedChanges = false,
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Sets the posting channel. Clearing it also turns posting off. */
    fun setChannel(id: Snowflake?) = edit { it.copy(channelId = id, enabled = it.enabled && id != null) }

    /** Turns scheduled posting on or off. */
    fun setEnabled(value: Boolean) = edit { it.copy(enabled = value && it.channelId != null) }

    /** Sets the local hour to post at. */
    fun setPostHour(value: Int) = edit { it.copy(postHour = value.coerceIn(0, 23)) }

    /** Sets the guild timezone. */
    fun setTimezone(value: String) = edit { it.copy(timezone = value) }

    /** Sets the role mentioned with each post. */
    fun setPingRole(id: Snowflake?) = edit { it.copy(pingRoleId = id) }

    /** Sets the message template. */
    fun setMessage(value: EmbedMessage) = edit { it.copy(message = value) }

    /** Sets the base topic. */
    fun setTopic(value: String) = edit { it.copy(topic = value) }

    /** Sets the base part of speech filter. */
    fun setPartOfSpeech(value: Int) = edit { it.copy(partOfSpeech = value) }

    /** Sets the base difficulty filter. */
    fun setDifficulty(value: Int) = edit { it.copy(difficulty = value) }

    /** Sets the word source. */
    fun setSourceMode(value: Int) = edit { it.copy(sourceMode = value) }

    /** Turns the discussion thread created under each post on or off. */
    fun setCreateThread(value: Boolean) = edit { it.copy(createThread = value) }

    /** Sets the discussion thread name template. Empty falls back to the default on save. */
    fun setThreadName(value: String) = edit { it.copy(threadName = value) }

    /** Sets how long the discussion thread stays open before Discord archives it. */
    fun setThreadAutoArchiveMinutes(value: Int) = edit { it.copy(threadAutoArchiveMinutes = value) }

    /** Updates the pending word in the add form. */
    fun setNewWord(value: String) = _state.update { it.copy(newWord = value) }

    /** Updates the pending definition in the add form. */
    fun setNewDefinition(value: String) = _state.update { it.copy(newDefinition = value) }

    /** Writes the pending configuration edits. */
    fun save() = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(isSaving = true) }
        val template = current.message.serialize().let { if (it == "-") "" else it }
        val body = buildJsonObject {
            put("channelId", JsonPrimitive(current.channelId.orIfBlank("0")))
            put("enabled", JsonPrimitive(current.enabled))
            put("postHour", JsonPrimitive(current.postHour))
            put("timezone", JsonPrimitive(current.timezone))
            put("pingRoleId", JsonPrimitive(current.pingRoleId.orIfBlank("0")))
            put("messageTemplate", JsonPrimitive(template))
            put("topic", JsonPrimitive(current.topic.trim()))
            put("partOfSpeech", JsonPrimitive(current.partOfSpeech))
            put("difficulty", JsonPrimitive(current.difficulty))
            put("sourceMode", JsonPrimitive(current.sourceMode))
            put("createThread", JsonPrimitive(current.createThread))
            put("threadName", JsonPrimitive(current.threadName.trim()))
            put("threadAutoArchiveMinutes", JsonPrimitive(current.threadAutoArchiveMinutes))
        }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("$base/config", HttpMethod.PUT, MewdekoJson.encodeToString(JsonObject.serializer(), body))
            )
        }.isSuccess
        _state.update { it.copy(isSaving = false, hasUnsavedChanges = !ok) }
        if (ok) postSuccess("Word of the Day settings saved.") else postError("Failed to save settings.")
    }

    /** Wipes configuration, custom words, rules, and history. */
    fun reset() = viewModelScope.launch {
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/config/reset", HttpMethod.POST))
        }.isSuccess
        if (ok) {
            postSuccess("Word of the Day reset.")
            load(refreshing = true)
        } else {
            postError("Failed to reset.")
        }
    }

    /** Posts a fresh word to the channel right now. */
    fun postNow() = viewModelScope.launch {
        _state.update { it.copy(isPosting = true) }
        val result = runCatching {
            api.send(Endpoint("$base/post", HttpMethod.POST), WordEntry.serializer())
        }
        _state.update { it.copy(isPosting = false) }
        result.onSuccess { entry ->
            postSuccess("Posted \"${entry.word}\".")
            val history = fetchHistory()
            val cfg = runCatching {
                api.send(Endpoint("$base/config"), WordOfTheDayConfig.serializer())
            }.getOrNull()
            _state.update {
                it.copy(
                    history = history,
                    lastPosted = cfg?.lastPostedDate?.let { d -> d.toString().take(10) } ?: it.lastPosted,
                )
            }
        }.onFailure {
            postError("Couldn't post a word. Check the channel and filters.")
        }
    }

    /** Adds the word in the add form, looking up a definition when none was typed. */
    fun addWord() = viewModelScope.launch {
        val current = _state.value
        val word = current.newWord.trim()
        if (word.isEmpty()) return@launch
        _state.update { it.copy(isAddingWord = true) }
        val body = buildJsonObject {
            put("word", JsonPrimitive(word))
            put("definition", JsonPrimitive(current.newDefinition.trim().ifEmpty { null }))
            put("addedBy", JsonPrimitive(session.userId.ifEmpty { "0" }))
        }
        val result = runCatching {
            api.send(
                Endpoint("$base/words", HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
                WordOfTheDayWord.serializer(),
            )
        }
        result.onSuccess { added ->
            _state.update {
                it.copy(words = it.words + added, newWord = "", newDefinition = "", isAddingWord = false)
            }
        }.onFailure {
            _state.update { it.copy(isAddingWord = false) }
            postError("Couldn't add \"$word\". Try supplying a definition.")
        }
    }

    /** Removes a custom word. */
    fun removeWord(word: WordOfTheDayWord) = viewModelScope.launch {
        val encoded = Uri.encode(word.word)
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/words/$encoded", HttpMethod.DELETE))
        }.isSuccess
        if (ok) {
            _state.update { s -> s.copy(words = s.words.filterNot { it.id == word.id }) }
        } else {
            postError("Failed to remove \"${word.word}\".")
        }
    }

    /** Edits a rule draft without saving it. */
    fun updateDraft(id: String, transform: (RuleDraft) -> RuleDraft) = _state.update { s ->
        s.copy(
            dayDrafts = s.dayDrafts.map { if (it.id == id) transform(it) else it },
            monthDrafts = s.monthDrafts.map { if (it.id == id) transform(it) else it },
        )
    }

    /** Saves a rule draft to the bot. */
    fun saveRule(draft: RuleDraft) = viewModelScope.launch {
        updateDraft(draft.id) { it.copy(saving = true) }
        val body = buildJsonObject {
            put("ruleType", JsonPrimitive(draft.type.value))
            put("ruleKey", JsonPrimitive(draft.key))
            put("topic", JsonPrimitive(draft.topic.trim()))
            put("partOfSpeech", JsonPrimitive(draft.partOfSpeech))
            put("difficulty", JsonPrimitive(draft.difficulty))
        }
        val ok = runCatching {
            api.send(
                Endpoint("$base/schedule", HttpMethod.PUT, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
                WordOfTheDaySchedule.serializer(),
            )
        }.isSuccess
        updateDraft(draft.id) { it.copy(saving = false, exists = if (ok) true else it.exists) }
        if (!ok) postError("Failed to save the ${draft.name} rule.")
    }

    /** Deletes a rule and resets its draft. */
    fun clearRule(draft: RuleDraft) = viewModelScope.launch {
        updateDraft(draft.id) { it.copy(saving = true) }
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/schedule/${draft.type.value}/${draft.key}", HttpMethod.DELETE))
        }.isSuccess
        updateDraft(draft.id) {
            if (ok) RuleDraft(it.type, it.key, it.name) else it.copy(saving = false)
        }
        if (!ok) postError("Failed to clear the ${draft.name} rule.")
    }

    private suspend fun fetchWords(): List<WordOfTheDayWord> = runCatching {
        api.send(Endpoint("$base/words"), ListSerializer(WordOfTheDayWord.serializer()))
    }.getOrDefault(emptyList())

    private suspend fun fetchHistory(): List<WordOfTheDayHistoryEntry> = runCatching {
        api.send(Endpoint("$base/history?count=30"), ListSerializer(WordOfTheDayHistoryEntry.serializer()))
    }.getOrDefault(emptyList())

    private suspend fun fetchRules(): List<WordOfTheDaySchedule> = runCatching {
        api.send(Endpoint("$base/schedule"), ListSerializer(WordOfTheDaySchedule.serializer()))
    }.getOrDefault(emptyList())

    private fun buildDrafts(type: ScheduleRuleType, rules: List<WordOfTheDaySchedule>): List<RuleDraft> {
        val names = if (type == ScheduleRuleType.DAY_OF_WEEK) RuleDraft.dayNames else RuleDraft.monthNames
        val offset = if (type == ScheduleRuleType.DAY_OF_WEEK) 0 else 1
        return names.mapIndexed { index, name ->
            val key = index + offset
            val rule = rules.firstOrNull { it.ruleType == type.value && it.ruleKey == key }
            RuleDraft(
                type = type,
                key = key,
                name = name,
                topic = rule?.topic.orEmpty(),
                partOfSpeech = rule?.partOfSpeech ?: 0,
                difficulty = rule?.difficulty ?: 0,
                exists = rule != null,
            )
        }
    }

    private fun edit(transform: (WordOfTheDayState) -> WordOfTheDayState) {
        _state.update { transform(it).copy(hasUnsavedChanges = true) }
    }

    private fun Snowflake?.orIfBlank(fallback: String): String =
        if (isNullOrEmpty()) fallback else this
}
