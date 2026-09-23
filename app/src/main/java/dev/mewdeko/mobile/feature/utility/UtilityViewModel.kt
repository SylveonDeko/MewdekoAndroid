package dev.mewdeko.mobile.feature.utility

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import javax.inject.Inject

/** Utilities screen state across all seven sections. */
data class UtilityState(
    val section: String = UtilitySection.ALIASES,
    val loadedSections: Set<String> = emptySet(),
    val loadingSections: Set<String> = emptySet(),
    val sectionErrors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val textChannels: List<TextChannelLite> = emptyList(),
    val newsChannels: List<TextChannelLite> = emptyList(),
    val roles: List<GuildRole> = emptyList(),

    val aliases: List<CommandAlias> = emptyList(),
    val aliasTrigger: String = "",
    val aliasMapping: String = "",
    val aliasSearch: String = "",

    val quotes: List<GuildQuote> = emptyList(),
    val quoteTotal: Int = 0,
    val quotePage: Int = 1,
    val quoteSearch: String = "",
    val quoteKeyword: String = "",
    val quoteText: String = "",
    val editingQuoteId: Int? = null,
    val editQuoteKeyword: String = "",
    val editQuoteText: String = "",

    val autoPublish: List<AutoPublishChannel> = emptyList(),
    val publishUserDrafts: Map<Snowflake, String> = emptyMap(),
    val publishWordDrafts: Map<Snowflake, String> = emptyMap(),

    val streamRole: StreamRoleSettings? = null,
    val streamFromRole: Snowflake? = null,
    val streamAddRole: Snowflake? = null,
    val streamKeyword: String = "",
    val streamListType: String = "whitelist",
    val streamListUser: String = "",

    val ai: AiConfig? = null,
    val aiEnabled: Boolean = false,
    val aiChannelId: Snowflake? = null,
    val aiProvider: Int = AiProvider.OPEN_AI.value,
    val aiModel: String = "",
    val aiSystemPrompt: String = "",
    val aiWebSearch: Boolean = false,
    val aiHideWebSearch: Boolean = true,
    val aiApiKey: String = "",
    val aiWebhookUrl: String = "",
    val aiDirty: Boolean = false,
    val aiModels: List<AiModel> = emptyList(),
    val aiModelsLoading: Boolean = false,

    val nsfwTags: List<String> = emptyList(),
    val nsfwDraft: String = "",

    val roleMonitor: RoleMonitorConfig? = null,
    val rmRolePick: Snowflake? = null,
    val rmRolePunish: Int? = null,
    val rmPermPick: String? = null,
    val rmPermPunish: Int? = null,
    val rmWhitelistUser: String = "",
) {
    /** Aliases matching the search box, by trigger or mapping. */
    val filteredAliases: List<CommandAlias>
        get() {
            val term = aliasSearch.trim().lowercase()
            if (term.isEmpty()) return aliases
            return aliases.filter { it.trigger.contains(term) || it.mapping.lowercase().contains(term) }
        }

    /** Number of quote pages for the current search. */
    val quotePageCount: Int
        get() = if (quoteTotal <= 0) 1 else (quoteTotal + QuotePageSize - 1) / QuotePageSize

    /** Display name for a role id, falling back to the raw id. */
    fun roleName(id: Snowflake?): String {
        if (id.isNullOrEmpty() || id == "0") return "none"
        return roles.firstOrNull { it.id == id }?.name ?: id
    }
}

/** Loads and edits aliases, quotes, auto publish, stream role, AI, NSFW, and role monitor settings. */
@HiltViewModel
class UtilityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(UtilityState())

    /** Observable screen state. */
    val state: StateFlow<UtilityState> = _state.asStateFlow()

    private var quoteSearchJob: Job? = null

    private val base: String get() = "api/Utility/$guildId"

    init {
        load()
    }

    /**
     * Loads channels and roles plus the active section. Other sections are
     * marked stale so they reload the next time they are opened.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val section = _state.value.section
        _state.update { it.copy(loadedSections = it.loadedSections.intersect(setOf(section))) }
        coroutineScope {
            val text = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val news = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/channels/$guildId/5"),
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
            val sectionJob = async { fetchSection(section) }
            _state.update {
                it.copy(
                    textChannels = text.await().sortedBy { c -> c.name.lowercase() },
                    newsChannels = news.await().sortedBy { c -> c.name.lowercase() },
                    roles = roles.await().filter { r -> r.name != "@everyone" }.sortedBy { r -> r.name.lowercase() },
                )
            }
            sectionJob.await()
        }
    }

    /** Switches the visible section, loading it on first view. */
    fun setSection(section: String) {
        _state.update { it.copy(section = section) }
        val current = _state.value
        if (section !in current.loadedSections && section !in current.loadingSections) reloadSection(section)
    }

    /** Reloads one section in place without touching the others. */
    fun reloadSection(section: String = _state.value.section) {
        viewModelScope.launch { fetchSection(section) }
    }

    private suspend fun fetchSection(section: String) {
        _state.update {
            it.copy(
                loadingSections = it.loadingSections + section,
                sectionErrors = it.sectionErrors - section,
            )
        }
        try {
            when (section) {
                UtilitySection.ALIASES -> fetchAliases()
                UtilitySection.QUOTES -> fetchQuotes(1)
                UtilitySection.AUTO_PUBLISH -> fetchAutoPublish()
                UtilitySection.STREAM_ROLE -> fetchStreamRole()
                UtilitySection.AI -> fetchAi()
                UtilitySection.NSFW -> fetchNsfw()
                UtilitySection.ROLE_MONITOR -> fetchRoleMonitor()
            }
            _state.update {
                it.copy(
                    loadedSections = it.loadedSections + section,
                    loadingSections = it.loadingSections - section,
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) {
                _state.update { it.copy(loadingSections = it.loadingSections - section) }
                throw t
            }
            _state.update {
                it.copy(
                    loadingSections = it.loadingSections - section,
                    sectionErrors = it.sectionErrors + (section to t.userFacingMessage),
                )
            }
            if (section in _state.value.loadedSections) postError("Failed to refresh: ${t.userFacingMessage}")
        }
    }

    /**
     * Runs a mutation with the shared busy flag. Failures surface the
     * server's reason alongside [failure]; on success [then] refreshes the
     * affected data.
     */
    private fun act(
        failure: String,
        then: (suspend () -> Unit)? = null,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
            then?.let { refresh -> runCatching { refresh() } }
        } catch (t: Throwable) {
            postError("$failure: ${t.userFacingMessage}")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun send(path: String, method: HttpMethod, body: String? = null) {
        api.sendIgnoringBody(Endpoint("$base/$path", method, body))
    }

    private fun String.encoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun isUserId(value: String): Boolean = Regex("^\\d{15,22}$").matches(value)

    /** Aliases. */
    private suspend fun fetchAliases() {
        val result = api.send(Endpoint("$base/aliases"), ListSerializer(CommandAlias.serializer()))
        _state.update { it.copy(aliases = result) }
    }

    /** Sets the new alias trigger draft. */
    fun setAliasTrigger(value: String) = _state.update { it.copy(aliasTrigger = value) }

    /** Sets the new alias mapping draft. */
    fun setAliasMapping(value: String) = _state.update { it.copy(aliasMapping = value) }

    /** Sets the alias search filter. */
    fun setAliasSearch(value: String) = _state.update { it.copy(aliasSearch = value) }

    /** Adds or replaces an alias from the drafts. */
    fun addAlias() {
        val trigger = _state.value.aliasTrigger.trim()
        val mapping = _state.value.aliasMapping.trim()
        if (trigger.isEmpty() || mapping.isEmpty()) return
        act("Failed to add alias", then = ::fetchAliases) {
            send("aliases", HttpMethod.POST, jsonBody("trigger" to trigger, "mapping" to mapping))
            _state.update { it.copy(aliasTrigger = "", aliasMapping = "") }
        }
    }

    /** Removes one alias. */
    fun removeAlias(trigger: String) = act("Failed to remove alias", then = ::fetchAliases) {
        send("aliases/${trigger.encoded()}", HttpMethod.DELETE)
    }

    /** Removes every alias. */
    fun clearAliases() = act("Failed to clear aliases", then = ::fetchAliases) {
        send("aliases", HttpMethod.DELETE)
    }

    /** Quotes. */
    private suspend fun fetchQuotes(page: Int) {
        val search = _state.value.quoteSearch.trim().encoded()
        val result = api.send(
            Endpoint("$base/quotes?search=$search&page=$page&pageSize=$QuotePageSize"),
            QuoteListResponse.serializer(),
        )
        _state.update {
            it.copy(quotes = result.quotes, quoteTotal = result.total, quotePage = result.page)
        }
    }

    /** Sets the quote search and reloads the first page after a short pause. */
    fun setQuoteSearch(value: String) {
        _state.update { it.copy(quoteSearch = value) }
        quoteSearchJob?.cancel()
        quoteSearchJob = viewModelScope.launch {
            delay(350)
            runCatching { fetchQuotes(1) }.onFailure {
                if (it !is CancellationException) postError("Failed to search quotes: ${it.userFacingMessage}")
            }
        }
    }

    /** Loads another page of quotes. */
    fun goToQuotePage(page: Int) {
        if (page < 1 || page > _state.value.quotePageCount) return
        act("Failed to load quotes") { fetchQuotes(page) }
    }

    /** Sets the new quote keyword draft. */
    fun setQuoteKeyword(value: String) = _state.update { it.copy(quoteKeyword = value) }

    /** Sets the new quote text draft. */
    fun setQuoteText(value: String) = _state.update { it.copy(quoteText = value) }

    /** Adds a quote authored by the signed-in user. */
    fun addQuote() {
        val keyword = _state.value.quoteKeyword.trim()
        val text = _state.value.quoteText.trim()
        if (keyword.isEmpty() || text.isEmpty()) return
        if (userId.isEmpty()) {
            postError("Sign in again to add quotes.")
            return
        }
        act("Failed to add quote", then = { fetchQuotes(1) }) {
            send(
                "quotes",
                HttpMethod.POST,
                jsonBody("keyword" to keyword, "text" to text, "authorId" to userId.asSnowflakeNumber()),
            )
            _state.update { it.copy(quoteKeyword = "", quoteText = "") }
        }
    }

    /** Opens the inline editor for a quote. */
    fun startEditQuote(quote: GuildQuote) = _state.update {
        it.copy(editingQuoteId = quote.id, editQuoteKeyword = quote.keyword, editQuoteText = quote.text)
    }

    /** Closes the inline quote editor without saving. */
    fun cancelEditQuote() = _state.update { it.copy(editingQuoteId = null) }

    /** Sets the edited keyword. */
    fun setEditQuoteKeyword(value: String) = _state.update { it.copy(editQuoteKeyword = value) }

    /** Sets the edited text. */
    fun setEditQuoteText(value: String) = _state.update { it.copy(editQuoteText = value) }

    /** Saves the inline quote edit. */
    fun saveQuoteEdit() {
        val id = _state.value.editingQuoteId ?: return
        val keyword = _state.value.editQuoteKeyword.trim().ifEmpty { null }
        val text = _state.value.editQuoteText.trim().ifEmpty { null }
        act("Failed to update quote", then = { fetchQuotes(_state.value.quotePage) }) {
            send(
                "quotes/$id",
                HttpMethod.PUT,
                jsonBody(
                    "keyword" to (keyword ?: JsonNull),
                    "text" to (text ?: JsonNull),
                    "authorId" to 0L,
                ),
            )
            _state.update { it.copy(editingQuoteId = null) }
        }
    }

    /** Deletes a quote. */
    fun deleteQuote(quote: GuildQuote) = act(
        "Failed to delete quote",
        then = { fetchQuotes(_state.value.quotePage) },
    ) {
        send("quotes/${quote.id}", HttpMethod.DELETE)
    }

    /** Auto publish. */
    private suspend fun fetchAutoPublish() {
        val result = api.send(Endpoint("$base/autopublish"), ListSerializer(AutoPublishChannel.serializer()))
        _state.update { it.copy(autoPublish = result) }
    }

    /** Enables auto publishing for an announcement channel. */
    fun addAutoPublish(channelId: Snowflake?) {
        if (channelId.isNullOrEmpty()) return
        act("Failed to enable auto publish", then = ::fetchAutoPublish) {
            send("autopublish/$channelId", HttpMethod.POST)
        }
    }

    /** Disables auto publishing for a channel. */
    fun removeAutoPublish(channelId: Snowflake) = act("Failed to disable auto publish", then = ::fetchAutoPublish) {
        send("autopublish/$channelId", HttpMethod.DELETE)
    }

    /** Sets the user id draft for a channel's skip list. */
    fun setPublishUserDraft(channelId: Snowflake, value: String) = _state.update {
        it.copy(publishUserDrafts = it.publishUserDrafts + (channelId to value))
    }

    /** Sets the word draft for a channel's skip list. */
    fun setPublishWordDraft(channelId: Snowflake, value: String) = _state.update {
        it.copy(publishWordDrafts = it.publishWordDrafts + (channelId to value))
    }

    /** Stops publishing messages from the drafted user id. */
    fun addPublishUser(channelId: Snowflake) {
        val draft = _state.value.publishUserDrafts[channelId].orEmpty().trim()
        if (!isUserId(draft)) {
            postError("Enter a valid Discord user ID")
            return
        }
        act("Failed to blacklist user", then = ::fetchAutoPublish) {
            send("autopublish/$channelId/users/$draft", HttpMethod.POST)
            _state.update { it.copy(publishUserDrafts = it.publishUserDrafts + (channelId to "")) }
        }
    }

    /** Lets a user's messages be published again. */
    fun removePublishUser(channelId: Snowflake, targetId: Snowflake) =
        act("Failed to remove user", then = ::fetchAutoPublish) {
            send("autopublish/$channelId/users/$targetId", HttpMethod.DELETE)
        }

    /** Stops publishing messages containing the drafted word. */
    fun addPublishWord(channelId: Snowflake) {
        val draft = _state.value.publishWordDrafts[channelId].orEmpty().trim()
        if (draft.isEmpty()) return
        act("Failed to blacklist word", then = ::fetchAutoPublish) {
            send("autopublish/$channelId/words", HttpMethod.POST, jsonBody("word" to draft))
            _state.update { it.copy(publishWordDrafts = it.publishWordDrafts + (channelId to "")) }
        }
    }

    /** Removes a word from a channel's skip list. */
    fun removePublishWord(channelId: Snowflake, word: String) =
        act("Failed to remove word", then = ::fetchAutoPublish) {
            send("autopublish/$channelId/words/${word.encoded()}", HttpMethod.DELETE)
        }

    /** Stream role. */
    private suspend fun fetchStreamRole() {
        val result = api.send(Endpoint("$base/streamrole"), StreamRoleSettings.serializer())
        _state.update {
            it.copy(
                streamRole = result,
                streamFromRole = result.fromRoleId.takeIf { id -> id.isNotEmpty() && id != "0" },
                streamAddRole = result.addRoleId.takeIf { id -> id.isNotEmpty() && id != "0" },
                streamKeyword = result.keyword.orEmpty(),
            )
        }
    }

    /** Sets the eligible role draft. */
    fun setStreamFromRole(id: Snowflake?) = _state.update { it.copy(streamFromRole = id) }

    /** Sets the role-while-streaming draft. */
    fun setStreamAddRole(id: Snowflake?) = _state.update { it.copy(streamAddRole = id) }

    /** Sets the stream title keyword draft. */
    fun setStreamKeyword(value: String) = _state.update { it.copy(streamKeyword = value) }

    /** Picks which exception list the user id is added to. */
    fun setStreamListType(value: String) = _state.update { it.copy(streamListType = value) }

    /** Sets the exception user id draft. */
    fun setStreamListUser(value: String) = _state.update { it.copy(streamListUser = value) }

    /** Enables the stream role or updates its roles. */
    fun saveStreamRole() {
        val from = _state.value.streamFromRole
        val add = _state.value.streamAddRole
        if (from.isNullOrEmpty() || add.isNullOrEmpty()) {
            postError("Pick both roles first")
            return
        }
        act("Failed to enable stream role", then = ::fetchStreamRole) {
            send(
                "streamrole",
                HttpMethod.POST,
                jsonBody("fromRoleId" to from.asSnowflakeNumber(), "addRoleId" to add.asSnowflakeNumber()),
            )
        }
    }

    /** Disables the stream role. */
    fun stopStreamRole() = act("Failed to disable stream role", then = ::fetchStreamRole) {
        send("streamrole", HttpMethod.DELETE)
    }

    /** Saves the stream title keyword; empty clears it. */
    fun saveStreamKeyword() {
        val keyword = _state.value.streamKeyword.trim().ifEmpty { null }
        act("Failed to save keyword", then = ::fetchStreamRole) {
            send("streamrole/keyword", HttpMethod.POST, jsonBody("word" to (keyword ?: JsonNull)))
        }
    }

    /** Adds the drafted user id to the selected exception list. */
    fun addStreamListUser() {
        val draft = _state.value.streamListUser.trim()
        val list = _state.value.streamListType
        if (!isUserId(draft)) {
            postError("Enter a valid Discord user ID")
            return
        }
        act("Failed to update list", then = ::fetchStreamRole) {
            send("streamrole/$list/$draft", HttpMethod.POST)
            _state.update { it.copy(streamListUser = "") }
        }
    }

    /** Removes a user from an exception list. */
    fun removeStreamListUser(list: String, targetId: Snowflake) =
        act("Failed to update list", then = ::fetchStreamRole) {
            send("streamrole/$list/$targetId", HttpMethod.DELETE)
        }

    /** AI assistant. */
    private suspend fun fetchAi() {
        val result = api.send(Endpoint("$base/ai"), AiConfig.serializer())
        applyAi(result)
    }

    private fun applyAi(config: AiConfig) = _state.update {
        it.copy(
            ai = config,
            aiEnabled = config.enabled,
            aiChannelId = config.channelId.takeIf { id -> id.isNotEmpty() && id != "0" },
            aiProvider = config.provider,
            aiModel = config.model.orEmpty(),
            aiSystemPrompt = config.systemPrompt.orEmpty(),
            aiWebSearch = config.webSearchEnabled,
            aiHideWebSearch = config.hideWebSearchMessages,
            aiApiKey = "",
            aiWebhookUrl = "",
            aiDirty = false,
        )
    }

    private fun editAi(transform: (UtilityState) -> UtilityState) =
        _state.update { transform(it).copy(aiDirty = true) }

    /** Toggles the assistant. */
    fun setAiEnabled(value: Boolean) = editAi { it.copy(aiEnabled = value) }

    /** Sets the channel the assistant listens in. */
    fun setAiChannel(id: Snowflake?) = editAi { it.copy(aiChannelId = id) }

    /** Sets the provider and drops any model list fetched for the old one. */
    fun setAiProvider(value: Int) = editAi { it.copy(aiProvider = value, aiModels = emptyList()) }

    /** Sets the model id. */
    fun setAiModel(value: String) = editAi { it.copy(aiModel = value) }

    /** Sets the system prompt. */
    fun setAiSystemPrompt(value: String) = editAi { it.copy(aiSystemPrompt = value) }

    /** Toggles web search. */
    fun setAiWebSearch(value: Boolean) = editAi { it.copy(aiWebSearch = value) }

    /** Toggles hiding the web search notices. */
    fun setAiHideWebSearch(value: Boolean) = editAi { it.copy(aiHideWebSearch = value) }

    /** Sets a replacement API key. */
    fun setAiApiKey(value: String) = editAi { it.copy(aiApiKey = value) }

    /** Sets a replacement webhook URL. */
    fun setAiWebhookUrl(value: String) = editAi { it.copy(aiWebhookUrl = value) }

    /** Saves the AI assistant form. */
    fun saveAi() {
        val s = _state.value
        if (s.aiEnabled && s.aiChannelId.isNullOrEmpty()) {
            postError("Pick a channel before enabling the assistant")
            return
        }
        act("Failed to save AI settings") {
            val updated = api.send(
                Endpoint(
                    "$base/ai",
                    HttpMethod.PUT,
                    jsonBody(
                        "enabled" to s.aiEnabled,
                        "channelId" to (s.aiChannelId?.asSnowflakeNumber() ?: 0L),
                        "provider" to s.aiProvider,
                        "model" to s.aiModel,
                        "systemPrompt" to s.aiSystemPrompt,
                        "webSearchEnabled" to s.aiWebSearch,
                        "hideWebSearchMessages" to s.aiHideWebSearch,
                        "apiKey" to s.aiApiKey.trim().ifEmpty { null },
                        "webhookUrl" to s.aiWebhookUrl.trim().ifEmpty { null },
                    ),
                ),
                AiConfig.serializer(),
            )
            applyAi(updated)
        }
    }

    /** Removes the stored API key and turns the assistant off. */
    fun clearAiKey() = act("Failed to remove key", then = ::fetchAi) {
        send("ai", HttpMethod.PUT, jsonBody("clearApiKey" to true, "enabled" to false))
    }

    /** Fetches the provider's model list using the stored key. */
    fun loadAiModels() = viewModelScope.launch {
        _state.update { it.copy(aiModelsLoading = true) }
        try {
            val models = api.send(
                Endpoint("$base/ai/models?provider=${_state.value.aiProvider}"),
                ListSerializer(AiModel.serializer()),
            )
            _state.update { it.copy(aiModels = models) }
        } catch (t: Throwable) {
            _state.update { it.copy(aiModels = emptyList()) }
            postError(t.userFacingMessage.ifBlank { "Could not fetch models. Save an API key first." })
        } finally {
            _state.update { it.copy(aiModelsLoading = false) }
        }
    }

    /** Switches the model field back to free text entry. */
    fun clearAiModels() = _state.update { it.copy(aiModels = emptyList()) }

    /** NSFW blacklist. */
    private suspend fun fetchNsfw() {
        val result = api.send(Endpoint("$base/nsfw/blacklist"), ListSerializer(String.serializer()))
        _state.update { it.copy(nsfwTags = result.sorted()) }
    }

    /** Sets the tag draft. */
    fun setNsfwDraft(value: String) = _state.update { it.copy(nsfwDraft = value) }

    /** Blocks the drafted tag. */
    fun addNsfwTag() {
        val clean = _state.value.nsfwDraft.trim().lowercase()
        if (clean.isEmpty()) return
        if (clean in _state.value.nsfwTags) {
            postError("\"$clean\" is already blocked")
            return
        }
        toggleNsfw(clean)
    }

    /** Unblocks a tag. */
    fun removeNsfwTag(tag: String) = toggleNsfw(tag)

    private fun toggleNsfw(tag: String) = act("Failed to update blacklist", then = ::fetchNsfw) {
        send("nsfw/blacklist/${tag.encoded()}", HttpMethod.POST)
        _state.update { it.copy(nsfwDraft = "") }
    }

    /** Role monitor. */
    private suspend fun fetchRoleMonitor() {
        val result = api.send(Endpoint("$base/rolemonitor"), RoleMonitorConfig.serializer())
        _state.update { it.copy(roleMonitor = result) }
    }

    /** Saves the default punishment. */
    fun setRoleMonitorDefault(value: Int) = act("Failed to save default punishment", then = ::fetchRoleMonitor) {
        send("rolemonitor/default", HttpMethod.POST, jsonBody("punishment" to value))
    }

    /** Sets the role to blacklist. */
    fun setRmRolePick(id: Snowflake?) = _state.update { it.copy(rmRolePick = id) }

    /** Sets the blacklisted role's punishment override; `null` uses the default. */
    fun setRmRolePunish(value: Int?) = _state.update { it.copy(rmRolePunish = value) }

    /** Sets the permission to blacklist. */
    fun setRmPermPick(value: String?) = _state.update { it.copy(rmPermPick = value) }

    /** Sets the blacklisted permission's punishment override; `null` uses the default. */
    fun setRmPermPunish(value: Int?) = _state.update { it.copy(rmPermPunish = value) }

    /** Sets the trusted member id draft. */
    fun setRmWhitelistUser(value: String) = _state.update { it.copy(rmWhitelistUser = value) }

    /** Blacklists the picked role. */
    fun addRmRole() {
        val role = _state.value.rmRolePick ?: return
        val punish = _state.value.rmRolePunish
        act("Failed to blacklist role", then = ::fetchRoleMonitor) {
            send(
                "rolemonitor/roles",
                HttpMethod.POST,
                jsonBody("roleId" to role.asSnowflakeNumber(), "punishment" to (punish?.let { JsonPrimitive(it) } ?: JsonNull)),
            )
            _state.update { it.copy(rmRolePick = null, rmRolePunish = null) }
        }
    }

    /** Removes a role from the blacklist. */
    fun removeRmRole(roleId: Snowflake) = act("Failed to remove role", then = ::fetchRoleMonitor) {
        send("rolemonitor/roles/$roleId", HttpMethod.DELETE)
    }

    /** Blacklists the picked permission. */
    fun addRmPermission() {
        val permission = _state.value.rmPermPick ?: return
        val punish = _state.value.rmPermPunish
        act("Failed to blacklist permission", then = ::fetchRoleMonitor) {
            send(
                "rolemonitor/permissions",
                HttpMethod.POST,
                jsonBody(
                    "permission" to permission.asSnowflakeNumber(),
                    "punishment" to (punish?.let { JsonPrimitive(it) } ?: JsonNull),
                ),
            )
            _state.update { it.copy(rmPermPick = null, rmPermPunish = null) }
        }
    }

    /** Removes a permission from the blacklist. */
    fun removeRmPermission(permission: String) = act("Failed to remove permission", then = ::fetchRoleMonitor) {
        send("rolemonitor/permissions/$permission", HttpMethod.DELETE)
    }

    /** Trusts a role. */
    fun whitelistRole(roleId: Snowflake?) {
        if (roleId.isNullOrEmpty()) return
        act("Failed to whitelist role", then = ::fetchRoleMonitor) {
            send("rolemonitor/whitelist/roles/$roleId", HttpMethod.POST)
        }
    }

    /** Stops trusting a role. */
    fun unwhitelistRole(roleId: Snowflake) = act("Failed to remove role", then = ::fetchRoleMonitor) {
        send("rolemonitor/whitelist/roles/$roleId", HttpMethod.DELETE)
    }

    /** Trusts the drafted member id. */
    fun addRmWhitelistUser() {
        val draft = _state.value.rmWhitelistUser.trim()
        if (!isUserId(draft)) {
            postError("Enter a valid Discord user ID")
            return
        }
        act("Failed to whitelist user", then = ::fetchRoleMonitor) {
            send("rolemonitor/whitelist/users/$draft", HttpMethod.POST)
            _state.update { it.copy(rmWhitelistUser = "") }
        }
    }

    /** Stops trusting a member. */
    fun unwhitelistUser(targetId: Snowflake) = act("Failed to remove member", then = ::fetchRoleMonitor) {
        send("rolemonitor/whitelist/users/$targetId", HttpMethod.DELETE)
    }
}
