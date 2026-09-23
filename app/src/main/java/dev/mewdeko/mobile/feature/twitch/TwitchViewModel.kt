package dev.mewdeko.mobile.feature.twitch

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import javax.inject.Inject

/** A list on the Twitch screen that can fail to load independently of the rest. */
enum class TwitchList { HEALTH, LINKS, CHAT_COMMANDS, CUSTOM_COMMANDS, TIMERS, QUOTES, REDEMPTIONS }

/** Bot Settings and Alerts fields, which the dashboard saves together through `POST api/Twitch/config`. */
data class TwitchSettingsDraft(
    val commandPrefix: String = "!",
    val language: String = "",
    val enabled: Boolean = true,
    val useEventSub: Boolean = true,
    val goLiveChannelId: Snowflake? = null,
    val goLiveMessage: EmbedMessage = EmbedMessage(),
    val subChannelId: Snowflake? = null,
    val subMessage: String = "",
    val raidChannelId: Snowflake? = null,
    val raidMessage: String = "",
)

/** The custom command create/edit form. */
data class TwitchCommandDraft(
    val name: String = "",
    val response: String = "",
    val permission: String = TwitchPermission.EVERYONE.value,
    val cooldownSeconds: String = "0",
    val enabled: Boolean = true,
    val testArgs: String = "",
    val editing: Boolean = false,
)

/** The timer create/edit form. */
data class TwitchTimerDraft(
    val name: String = "",
    val messages: String = "",
    val intervalMinutes: String = "10",
    val minChatMessages: String = "5",
    val onlineOnly: Boolean = true,
    val randomizeMessages: Boolean = false,
    val enabled: Boolean = true,
    val editing: Boolean = false,
)

/** The channel point action create/edit form. */
data class TwitchRedemptionDraft(
    val rewardTitle: String = "",
    val twitchResponse: String = "",
    val discordChannelId: Snowflake? = null,
    val discordMessage: String = "",
    val editing: Boolean = false,
)

/** The Live Tools inputs. */
data class TwitchLiveDraft(
    val chatMessage: String = "",
    val markerDescription: String = "",
    val pollTitle: String = "",
    val pollChoices: String = "Yes\nNo",
    val pollDurationSeconds: String = "60",
    val moderationUsername: String = "",
    val moderationDurationSeconds: String = "600",
    val moderationReason: String = "",
    val deleteMessageId: String = "",
)

/** Twitch screen state. */
data class TwitchState(
    val section: String = "setup",
    val subSection: String = "connect",
    val status: TwitchOAuthStatus? = null,
    val health: TwitchHealth? = null,
    val variables: TwitchVariableDocs? = null,
    val isBotOwner: Boolean = false,
    val channels: List<TextChannelLite> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val links: List<TwitchAccountLink> = emptyList(),
    val chatCommands: List<TwitchChatCommand> = emptyList(),
    val customCommands: List<TwitchCustomCommand> = emptyList(),
    val timers: List<TwitchTimer> = emptyList(),
    val quotes: List<TwitchQuote> = emptyList(),
    val redemptions: List<TwitchRedemptionAction> = emptyList(),
    val failedLists: Set<TwitchList> = emptySet(),
    val settings: TwitchSettingsDraft = TwitchSettingsDraft(),
    val hasUnsavedSettings: Boolean = false,
    val isSaving: Boolean = false,
    val connecting: TwitchOAuthMode? = null,
    val disconnecting: Boolean = false,
    val pendingAuthUrl: String? = null,
    val awaitingOAuth: Boolean = false,
    val commandDraft: TwitchCommandDraft = TwitchCommandDraft(),
    val commandPreview: String = "",
    val commandSaving: Boolean = false,
    val commandPreviewing: Boolean = false,
    val timerDraft: TwitchTimerDraft = TwitchTimerDraft(),
    val timerSaving: Boolean = false,
    val timerTesting: String? = null,
    val redemptionDraft: TwitchRedemptionDraft = TwitchRedemptionDraft(),
    val redemptionSaving: Boolean = false,
    val quoteText: String = "",
    val quoteAuthor: String = "",
    val quoteSearch: String = "",
    val quoteSaving: Boolean = false,
    val quotesLoading: Boolean = false,
    val live: TwitchLiveDraft = TwitchLiveDraft(),
    val liveRunning: TwitchLiveAction? = null,
    val lastClipUrl: String? = null,
    val testRunning: TwitchTestEvent? = null,
    val linkUserId: Snowflake? = null,
    val linkUsername: String = "",
    val linking: Boolean = false,
) {
    /** The command prefix to show in front of custom command names. */
    val prefix: String get() = settings.commandPrefix.ifBlank { "!" }

    /** Variables for [group], falling back to [fallback] when the docs endpoint returned nothing. */
    fun variablesFor(group: String, fallback: List<String>): List<String> =
        variables?.groups?.get(group)?.takeIf { it.isNotEmpty() } ?: fallback

    /** The display name for a linked Discord member, or a mention-style fallback. */
    fun memberName(id: Snowflake): String =
        members.firstOrNull { it.id == id }?.let { member -> member.displayName.ifBlank { member.username } }
            ?: "<@$id>"
}

/**
 * Drives the Twitch Bot screen: OAuth connection, health, bot settings,
 * custom commands, timers, quotes, alerts, channel point actions, live tools,
 * and account links, all through `api/Twitch/`.
 */
@HiltViewModel
class TwitchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    private val session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(TwitchState())

    /** Observable screen state. */
    val state: StateFlow<TwitchState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads the status, config, lists, channels, members, and owner flag in parallel. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val search = _state.value.quoteSearch
        coroutineScope {
            val status = async { api.send(Endpoint(twitch("oauth/status")), TwitchOAuthStatus.serializer()) }
            val config = async { optional { api.send(Endpoint(twitch("config")), TwitchConfigSnapshot.serializer()) } }
            val health = async { optional { fetchHealth() } }
            val variables = async { optional { api.send(Endpoint("api/Twitch/variables"), TwitchVariableDocs.serializer()) } }
            val links = async { optional { fetchLinks() } }
            val chatCommands = async {
                optional { api.send(Endpoint("api/Twitch/chat-commands"), ListSerializer(TwitchChatCommand.serializer())) }
            }
            val customCommands = async { optional { fetchCustomCommands() } }
            val timers = async { optional { fetchTimers() } }
            val quotes = async { optional { fetchQuotes(search) } }
            val redemptions = async { optional { fetchRedemptions() } }
            val channels = async {
                optional {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.orEmpty()
            }
            val members = async {
                optional {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.orEmpty()
            }
            val owner = async { checkOwner() }

            val statusValue = status.await()
            val configValue = config.await()
            val healthValue = health.await()
            val linksValue = links.await()
            val chatCommandsValue = chatCommands.await()
            val customCommandsValue = customCommands.await()
            val timersValue = timers.await()
            val quotesValue = quotes.await()
            val redemptionsValue = redemptions.await()

            val failed = buildSet {
                if (healthValue == null) add(TwitchList.HEALTH)
                if (linksValue == null) add(TwitchList.LINKS)
                if (chatCommandsValue == null) add(TwitchList.CHAT_COMMANDS)
                if (customCommandsValue == null) add(TwitchList.CUSTOM_COMMANDS)
                if (timersValue == null) add(TwitchList.TIMERS)
                if (quotesValue == null) add(TwitchList.QUOTES)
                if (redemptionsValue == null) add(TwitchList.REDEMPTIONS)
            }

            _state.update {
                it.copy(
                    status = statusValue,
                    health = healthValue,
                    variables = variables.await(),
                    isBotOwner = owner.await(),
                    channels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    members = members.await()
                        .filterNot { member -> member.isBot }
                        .sortedBy { member -> member.displayName.ifBlank { member.username }.lowercase() },
                    links = linksValue.orEmpty(),
                    chatCommands = chatCommandsValue.orEmpty(),
                    customCommands = customCommandsValue.orEmpty(),
                    timers = timersValue.orEmpty(),
                    quotes = quotesValue.orEmpty(),
                    redemptions = redemptionsValue.orEmpty(),
                    failedLists = failed,
                    settings = settingsFrom(statusValue, configValue),
                    hasUnsavedSettings = false,
                )
            }
        }
    }

    /** Switches the top-level section and resets the sub-section to its first entry. */
    fun setSection(id: String) = _state.update {
        it.copy(section = id, subSection = TwitchSections.firstSubSection(id))
    }

    /** Switches the sub-section within the current section. */
    fun setSubSection(id: String) = _state.update { it.copy(subSection = id) }

    /** Requests an OAuth authorization URL for [mode]; the screen opens it once it arrives. */
    fun connect(mode: TwitchOAuthMode) = viewModelScope.launch {
        _state.update { it.copy(connecting = mode) }
        try {
            val response = api.send(
                Endpoint(twitch("oauth/url", "mode" to mode.value)),
                TwitchOAuthUrl.serializer(),
            )
            _state.update {
                it.copy(
                    connecting = null,
                    pendingAuthUrl = response.authorizationUrl.takeIf { url -> url.isNotBlank() },
                )
            }
            if (response.authorizationUrl.isBlank()) postError("Failed to start Twitch ${mode.value} authorization.")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.update { it.copy(connecting = null) }
            postError(t.serverMessage("Failed to start Twitch ${mode.value} authorization."))
        }
    }

    /** Marks the pending authorization URL as opened, so the screen refreshes when the user returns. */
    fun authUrlOpened() = _state.update { it.copy(pendingAuthUrl = null, awaitingOAuth = true) }

    /** Called when the screen resumes; reloads status if an authorization was in progress. */
    fun onResumed() {
        if (!_state.value.awaitingOAuth) return
        _state.update { it.copy(awaitingOAuth = false) }
        load(refreshing = true)
    }

    /** Removes the channel authorization or, for bot owners, the shared bot account. */
    fun disconnect(mode: TwitchOAuthMode) = viewModelScope.launch {
        _state.update { it.copy(disconnecting = true) }
        val ok = attempt("Failed to disconnect Twitch ${mode.label}.") {
            api.sendIgnoringBody(
                Endpoint(twitch("oauth/disconnect", "mode" to mode.value), HttpMethod.DELETE)
            )
        }
        _state.update { it.copy(disconnecting = false) }
        if (ok) load(refreshing = true)
    }

    /** Sets the Twitch chat command prefix, capped at eight characters like the dashboard. */
    fun setCommandPrefix(value: String) = editSettings { it.copy(commandPrefix = value.take(8)) }

    /** Sets the language override; blank uses the server default. */
    fun setLanguage(value: String) = editSettings { it.copy(language = value) }

    /** Enables or disables the Twitch chat bot for this server. */
    fun setEnabled(value: Boolean) = editSettings { it.copy(enabled = value) }

    /** Chooses between EventSub and legacy IRC for chat events. */
    fun setUseEventSub(value: Boolean) = editSettings { it.copy(useEventSub = value) }

    /** Sets the go-live Discord channel. */
    fun setGoLiveChannel(id: Snowflake?) = editSettings { it.copy(goLiveChannelId = id) }

    /** Sets the go-live message. */
    fun setGoLiveMessage(value: EmbedMessage) = editSettings { it.copy(goLiveMessage = value) }

    /** Sets the sub notification Discord channel. */
    fun setSubChannel(id: Snowflake?) = editSettings { it.copy(subChannelId = id) }

    /** Sets the sub notification template. */
    fun setSubMessage(value: String) = editSettings { it.copy(subMessage = value) }

    /** Sets the raid notification Discord channel. */
    fun setRaidChannel(id: Snowflake?) = editSettings { it.copy(raidChannelId = id) }

    /** Sets the raid notification template. */
    fun setRaidMessage(value: String) = editSettings { it.copy(raidMessage = value) }

    /** Appends a template variable to the sub or raid message. */
    fun appendAlertVariable(target: TwitchTestEvent, variable: String) = when (target) {
        TwitchTestEvent.SUB -> editSettings { it.copy(subMessage = it.subMessage + variable) }
        TwitchTestEvent.RAID -> editSettings { it.copy(raidMessage = it.raidMessage + variable) }
        TwitchTestEvent.GO_LIVE -> Unit
    }

    /** Saves bot settings and alert routing through `POST api/Twitch/config`. */
    fun saveSettings() = viewModelScope.launch {
        val draft = _state.value.settings
        _state.update { it.copy(isSaving = true) }
        val goLive = draft.goLiveMessage.serialize().takeUnless { it == "-" }.orEmpty()
        val body = buildJsonObject {
            put("commandPrefix", JsonPrimitive(draft.commandPrefix))
            put("language", JsonPrimitive(draft.language.trim()))
            put("enabled", JsonPrimitive(draft.enabled))
            put("useEventSub", JsonPrimitive(draft.useEventSub))
            put("goLiveChannelId", JsonPrimitive(draft.goLiveChannelId.snowflakeNumber()))
            put("goLiveMessage", JsonPrimitive(goLive))
            put("subNotificationChannelId", JsonPrimitive(draft.subChannelId.snowflakeNumber()))
            put("subNotificationMessage", JsonPrimitive(draft.subMessage))
            put("raidNotificationChannelId", JsonPrimitive(draft.raidChannelId.snowflakeNumber()))
            put("raidNotificationMessage", JsonPrimitive(draft.raidMessage))
        }
        val ok = attempt("Failed to save Twitch settings.") {
            api.sendIgnoringBody(Endpoint(twitch("config"), HttpMethod.POST, body.encode()))
        }
        _state.update { it.copy(isSaving = false, hasUnsavedSettings = !ok) }
        if (ok) load(refreshing = true)
    }

    /** Sends a test go-live, sub, or raid event through the configured templates. */
    fun sendTestEvent(event: TwitchTestEvent) = viewModelScope.launch {
        _state.update { it.copy(testRunning = event) }
        try {
            val result = api.send(
                Endpoint(twitch("test/${event.value}"), HttpMethod.POST),
                TwitchMessageResult.serializer(),
            )
            postSuccess(result.message.ifBlank { "Sent test ${event.label} event." })
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError(t.serverMessage("Failed to send test ${event.label} event."))
        }
        _state.update { it.copy(testRunning = null) }
        refreshHealth()
    }

    /** Updates the custom command form. */
    fun updateCommandDraft(transform: (TwitchCommandDraft) -> TwitchCommandDraft) =
        _state.update { it.copy(commandDraft = transform(it.commandDraft)) }

    /** Loads [command] into the form for editing. */
    fun editCommand(command: TwitchCustomCommand) = _state.update {
        it.copy(
            commandDraft = TwitchCommandDraft(
                name = command.name,
                response = command.response,
                permission = command.permission,
                cooldownSeconds = command.cooldownSeconds.toString(),
                enabled = command.enabled,
                testArgs = it.commandDraft.testArgs,
                editing = true,
            ),
            commandPreview = "",
        )
    }

    /** Clears the custom command form. */
    fun resetCommandDraft() = _state.update { it.copy(commandDraft = TwitchCommandDraft(), commandPreview = "") }

    /** Creates or updates the custom command in the form. */
    fun saveCommand() = viewModelScope.launch {
        val draft = _state.value.commandDraft
        if (draft.name.isBlank() || draft.response.isBlank()) return@launch
        _state.update { it.copy(commandSaving = true) }
        val body = buildJsonObject {
            put("name", JsonPrimitive(draft.name.trim()))
            put("response", JsonPrimitive(draft.response))
            put("permission", JsonPrimitive(draft.permission))
            put("cooldownSeconds", JsonPrimitive(draft.cooldownSeconds.toIntOrNull()?.coerceIn(0, 86_400) ?: 0))
            put("enabled", JsonPrimitive(draft.enabled))
        }
        val ok = attempt("Failed to save Twitch command.") {
            api.sendIgnoringBody(Endpoint(twitch("custom-commands"), HttpMethod.POST, body.encode()))
        }
        _state.update {
            if (ok) it.copy(commandSaving = false, commandDraft = TwitchCommandDraft(), commandPreview = "")
            else it.copy(commandSaving = false)
        }
        if (ok) reloadCustomCommands()
    }

    /** Removes a custom command by name. */
    fun removeCommand(command: TwitchCustomCommand) = viewModelScope.launch {
        _state.update { it.copy(commandSaving = true) }
        val ok = attempt("Failed to remove Twitch command.") {
            api.sendIgnoringBody(
                Endpoint(twitch("custom-commands", "name" to command.name), HttpMethod.DELETE)
            )
        }
        _state.update {
            val reset = ok && it.commandDraft.name == command.name
            it.copy(
                commandSaving = false,
                commandDraft = if (reset) TwitchCommandDraft() else it.commandDraft,
                commandPreview = if (reset) "" else it.commandPreview,
            )
        }
        if (ok) reloadCustomCommands()
    }

    /** Renders the saved command named in the form with the test args, without sending it to chat. */
    fun previewCommand() = viewModelScope.launch {
        val draft = _state.value.commandDraft
        if (draft.name.isBlank()) return@launch
        _state.update { it.copy(commandPreviewing = true) }
        val body = buildJsonObject {
            put("name", JsonPrimitive(draft.name.trim()))
            put("args", draft.testArgs.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        try {
            val result = api.send(
                Endpoint(twitch("custom-commands/preview"), HttpMethod.POST, body.encode()),
                TwitchCommandPreview.serializer(),
            )
            _state.update { it.copy(commandPreview = result.response) }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError(t.serverMessage("Failed to preview Twitch command."))
        }
        _state.update { it.copy(commandPreviewing = false) }
        refreshHealth()
    }

    /** Updates the timer form. */
    fun updateTimerDraft(transform: (TwitchTimerDraft) -> TwitchTimerDraft) =
        _state.update { it.copy(timerDraft = transform(it.timerDraft)) }

    /** Loads [timer] into the form for editing. */
    fun editTimer(timer: TwitchTimer) = _state.update {
        it.copy(
            timerDraft = TwitchTimerDraft(
                name = timer.name,
                messages = timer.messages,
                intervalMinutes = timer.intervalMinutes.toString(),
                minChatMessages = timer.minChatMessages.toString(),
                onlineOnly = timer.onlineOnly,
                randomizeMessages = timer.randomizeMessages,
                enabled = timer.enabled,
                editing = true,
            )
        )
    }

    /** Clears the timer form. */
    fun resetTimerDraft() = _state.update { it.copy(timerDraft = TwitchTimerDraft()) }

    /** Creates or updates the timer in the form. */
    fun saveTimer() = viewModelScope.launch {
        val draft = _state.value.timerDraft
        if (draft.name.isBlank() || draft.messages.isBlank()) return@launch
        _state.update { it.copy(timerSaving = true) }
        val interval = draft.intervalMinutes.toIntOrNull()?.takeIf { it > 0 }?.coerceAtMost(1440) ?: 10
        val body = buildJsonObject {
            put("name", JsonPrimitive(draft.name.trim()))
            put("messages", JsonPrimitive(draft.messages))
            put("intervalMinutes", JsonPrimitive(interval))
            put("minChatMessages", JsonPrimitive(draft.minChatMessages.toIntOrNull()?.coerceIn(0, 10_000) ?: 0))
            put("onlineOnly", JsonPrimitive(draft.onlineOnly))
            put("randomizeMessages", JsonPrimitive(draft.randomizeMessages))
            put("enabled", JsonPrimitive(draft.enabled))
        }
        val ok = attempt("Failed to save Twitch timer.") {
            api.sendIgnoringBody(Endpoint(twitch("timers"), HttpMethod.POST, body.encode()))
        }
        _state.update {
            if (ok) it.copy(timerSaving = false, timerDraft = TwitchTimerDraft()) else it.copy(timerSaving = false)
        }
        if (ok) reloadTimers()
    }

    /** Enables or disables a timer without editing it. */
    fun setTimerEnabled(timer: TwitchTimer, enabled: Boolean) = viewModelScope.launch {
        val body = buildJsonObject { put("enabled", JsonPrimitive(enabled)) }
        val ok = attempt("Failed to update Twitch timer.") {
            api.sendIgnoringBody(
                Endpoint(twitch("timers/state", "name" to timer.name), HttpMethod.POST, body.encode())
            )
        }
        if (ok) {
            _state.update { state ->
                state.copy(timers = state.timers.map { if (it.name == timer.name) it.copy(enabled = enabled) else it })
            }
        }
    }

    /** Sends one of a timer's messages to Twitch chat immediately. */
    fun testTimer(timer: TwitchTimer) = viewModelScope.launch {
        _state.update { it.copy(timerTesting = timer.name) }
        try {
            val result = api.send(
                Endpoint(twitch("timers/test", "name" to timer.name), HttpMethod.POST),
                TwitchTimerTest.serializer(),
            )
            postSuccess("Sent timer: ${result.message}")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError(t.serverMessage("Failed to test Twitch timer."))
        }
        _state.update { it.copy(timerTesting = null) }
        refreshHealth()
    }

    /** Removes a timer by name. */
    fun removeTimer(timer: TwitchTimer) = viewModelScope.launch {
        _state.update { it.copy(timerSaving = true) }
        val ok = attempt("Failed to remove Twitch timer.") {
            api.sendIgnoringBody(Endpoint(twitch("timers", "name" to timer.name), HttpMethod.DELETE))
        }
        _state.update {
            val reset = ok && it.timerDraft.name == timer.name
            it.copy(timerSaving = false, timerDraft = if (reset) TwitchTimerDraft() else it.timerDraft)
        }
        if (ok) reloadTimers()
    }

    /** Sets the new quote text. */
    fun setQuoteText(value: String) = _state.update { it.copy(quoteText = value) }

    /** Sets the new quote author. */
    fun setQuoteAuthor(value: String) = _state.update { it.copy(quoteAuthor = value) }

    /** Sets the quote search filter. */
    fun setQuoteSearch(value: String) = _state.update { it.copy(quoteSearch = value) }

    /** Reloads the quote list with the current search filter. */
    fun refreshQuotes() = viewModelScope.launch {
        _state.update { it.copy(quotesLoading = true) }
        val quotes = optional { fetchQuotes(_state.value.quoteSearch) }
        _state.update {
            it.copy(
                quotesLoading = false,
                quotes = quotes ?: it.quotes,
                failedLists = it.failedLists.toggle(TwitchList.QUOTES, quotes == null),
            )
        }
    }

    /** Adds a quote, crediting the signed-in user as the one who added it. */
    fun addQuote() = viewModelScope.launch {
        val current = _state.value
        if (current.quoteText.isBlank()) return@launch
        _state.update { it.copy(quoteSaving = true) }
        val addedBy = session.user.value?.username?.takeIf { it.isNotBlank() }
        val body = buildJsonObject {
            put("text", JsonPrimitive(current.quoteText.trim()))
            put("author", current.quoteAuthor.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("addedBy", addedBy?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        val ok = attempt("Failed to save Twitch quote.") {
            api.sendIgnoringBody(Endpoint(twitch("quotes"), HttpMethod.POST, body.encode()))
        }
        _state.update {
            if (ok) it.copy(quoteSaving = false, quoteText = "", quoteAuthor = "") else it.copy(quoteSaving = false)
        }
        if (ok) {
            refreshQuotes()
            refreshHealth()
        }
    }

    /** Removes a quote by id. */
    fun removeQuote(quote: TwitchQuote) = viewModelScope.launch {
        _state.update { it.copy(quoteSaving = true) }
        val ok = attempt("Failed to remove Twitch quote.") {
            api.sendIgnoringBody(
                Endpoint(twitch("quotes", "quoteId" to quote.id.toString()), HttpMethod.DELETE)
            )
        }
        _state.update { it.copy(quoteSaving = false) }
        if (ok) {
            refreshQuotes()
            refreshHealth()
        }
    }

    /** Updates the channel point action form. */
    fun updateRedemptionDraft(transform: (TwitchRedemptionDraft) -> TwitchRedemptionDraft) =
        _state.update { it.copy(redemptionDraft = transform(it.redemptionDraft)) }

    /** Loads [action] into the form for editing. */
    fun editRedemption(action: TwitchRedemptionAction) = _state.update {
        it.copy(
            redemptionDraft = TwitchRedemptionDraft(
                rewardTitle = action.rewardTitle,
                twitchResponse = action.twitchResponse.orEmpty(),
                discordChannelId = action.discordChannelId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                discordMessage = action.discordMessage.orEmpty(),
                editing = true,
            )
        )
    }

    /** Clears the channel point action form. */
    fun resetRedemptionDraft() = _state.update { it.copy(redemptionDraft = TwitchRedemptionDraft()) }

    /** Creates or updates the channel point action in the form. */
    fun saveRedemption() = viewModelScope.launch {
        val draft = _state.value.redemptionDraft
        if (draft.rewardTitle.isBlank()) return@launch
        _state.update { it.copy(redemptionSaving = true) }
        val body = buildJsonObject {
            put("rewardTitle", JsonPrimitive(draft.rewardTitle.trim()))
            put("twitchResponse", draft.twitchResponse.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("discordChannelId", JsonPrimitive(draft.discordChannelId.snowflakeNumber()))
            put("discordMessage", draft.discordMessage.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        val ok = attempt("Failed to save redemption action.") {
            api.sendIgnoringBody(Endpoint(twitch("redemptions"), HttpMethod.POST, body.encode()))
        }
        _state.update {
            if (ok) it.copy(redemptionSaving = false, redemptionDraft = TwitchRedemptionDraft())
            else it.copy(redemptionSaving = false)
        }
        if (ok) reloadRedemptions()
    }

    /** Removes a channel point action by reward title. */
    fun removeRedemption(action: TwitchRedemptionAction) = viewModelScope.launch {
        _state.update { it.copy(redemptionSaving = true) }
        val ok = attempt("Failed to remove redemption action.") {
            api.sendIgnoringBody(
                Endpoint(twitch("redemptions", "rewardTitle" to action.rewardTitle), HttpMethod.DELETE)
            )
        }
        _state.update {
            val reset = ok && it.redemptionDraft.rewardTitle == action.rewardTitle
            it.copy(
                redemptionSaving = false,
                redemptionDraft = if (reset) TwitchRedemptionDraft() else it.redemptionDraft,
            )
        }
        if (ok) reloadRedemptions()
    }

    /** Updates the Live Tools inputs. */
    fun updateLive(transform: (TwitchLiveDraft) -> TwitchLiveDraft) =
        _state.update { it.copy(live = transform(it.live)) }

    /** Runs a Live Tools action against the connected channel. */
    fun runLiveAction(action: TwitchLiveAction) = viewModelScope.launch {
        val live = _state.value.live
        val endpoint = when (action) {
            TwitchLiveAction.CHAT -> post(
                "chat/send",
                buildJsonObject { put("message", JsonPrimitive(live.chatMessage)) },
            )

            TwitchLiveAction.MARKER -> post(
                "marker",
                buildJsonObject {
                    put(
                        "description",
                        live.markerDescription.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull,
                    )
                },
            )

            TwitchLiveAction.CLIP -> Endpoint(twitch("clip"), HttpMethod.POST)

            TwitchLiveAction.POLL -> post(
                "poll",
                buildJsonObject {
                    put("title", JsonPrimitive(live.pollTitle))
                    put(
                        "choices",
                        JsonArray(live.pollChoices.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { JsonPrimitive(it) }),
                    )
                    put("durationSeconds", JsonPrimitive(live.pollDurationSeconds.toIntOrNull()?.takeIf { it > 0 } ?: 60))
                },
            )

            TwitchLiveAction.UNBAN -> post(
                "moderation/unban",
                buildJsonObject { put("username", JsonPrimitive(live.moderationUsername)) },
            )

            TwitchLiveAction.DELETE -> post(
                "moderation/delete-message",
                buildJsonObject { put("messageId", JsonPrimitive(live.deleteMessageId)) },
            )

            TwitchLiveAction.TIMEOUT, TwitchLiveAction.BAN -> post(
                "moderation/ban",
                buildJsonObject {
                    put("username", JsonPrimitive(live.moderationUsername))
                    put(
                        "durationSeconds",
                        if (action == TwitchLiveAction.TIMEOUT) {
                            JsonPrimitive(live.moderationDurationSeconds.toIntOrNull()?.takeIf { it > 0 } ?: 600)
                        } else {
                            JsonNull
                        },
                    )
                    put(
                        "reason",
                        live.moderationReason.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull,
                    )
                },
            )
        }

        _state.update { it.copy(liveRunning = action) }
        try {
            val result = api.send(endpoint, TwitchActionResult.serializer())
            if (action == TwitchLiveAction.CLIP && !result.url.isNullOrBlank()) {
                _state.update { it.copy(lastClipUrl = result.url) }
            }
            val text = listOfNotNull(result.message.takeIf { it.isNotBlank() }, result.url?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { if (result.success) "Done." else "Twitch rejected the action." }
            if (result.success) postSuccess(text) else postError(text)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError(t.serverMessage("Failed to run Twitch action."))
        }
        _state.update { it.copy(liveRunning = null) }
        refreshHealth()
    }

    /** Selects the Discord member to link. */
    fun setLinkUser(id: Snowflake?) = _state.update { it.copy(linkUserId = id) }

    /** Sets the Twitch username to link. */
    fun setLinkUsername(value: String) = _state.update { it.copy(linkUsername = value) }

    /** Links the selected member to the entered Twitch username. */
    fun addLink() = viewModelScope.launch {
        val current = _state.value
        val userId = current.linkUserId ?: return@launch
        if (current.linkUsername.isBlank()) return@launch
        _state.update { it.copy(linking = true) }
        val body = buildJsonObject {
            put("discordUserId", JsonPrimitive(userId.snowflakeNumber()))
            put("twitchUsername", JsonPrimitive(current.linkUsername.trim()))
        }
        val ok = attempt("Failed to save Twitch account link.") {
            api.sendIgnoringBody(Endpoint(twitch("links"), HttpMethod.POST, body.encode()))
        }
        _state.update {
            if (ok) it.copy(linking = false, linkUserId = null, linkUsername = "") else it.copy(linking = false)
        }
        if (ok) reloadLinks()
    }

    /** Removes a member's Twitch link. */
    fun removeLink(link: TwitchAccountLink) = viewModelScope.launch {
        _state.update { it.copy(linking = true) }
        val ok = attempt("Failed to remove Twitch account link.") {
            api.sendIgnoringBody(
                Endpoint(twitch("links", "discordUserId" to link.discordUserId), HttpMethod.DELETE)
            )
        }
        _state.update { it.copy(linking = false) }
        if (ok) reloadLinks()
    }

    private fun settingsFrom(status: TwitchOAuthStatus, config: TwitchConfigSnapshot?) = TwitchSettingsDraft(
        commandPrefix = status.commandPrefix?.takeIf { it.isNotBlank() } ?: "!",
        language = status.language.orEmpty(),
        enabled = config?.enabled ?: status.hasChannelAuthorization,
        useEventSub = status.useEventSub,
        goLiveChannelId = status.goLiveChannelId.cleanSnowflake(),
        goLiveMessage = EmbedMessage.parse(status.goLiveMessage),
        subChannelId = status.subNotificationChannelId.cleanSnowflake(),
        subMessage = status.subNotificationMessage.orEmpty(),
        raidChannelId = status.raidNotificationChannelId.cleanSnowflake(),
        raidMessage = status.raidNotificationMessage.orEmpty(),
    )

    private fun editSettings(transform: (TwitchSettingsDraft) -> TwitchSettingsDraft) =
        _state.update { it.copy(settings = transform(it.settings), hasUnsavedSettings = true) }

    private suspend fun refreshHealth() {
        val health = optional { fetchHealth() } ?: return
        _state.update { it.copy(health = health, failedLists = it.failedLists - TwitchList.HEALTH) }
    }

    private suspend fun reloadCustomCommands() = reloadList(TwitchList.CUSTOM_COMMANDS, { fetchCustomCommands() }) { state, list ->
        state.copy(customCommands = list)
    }

    private suspend fun reloadTimers() = reloadList(TwitchList.TIMERS, { fetchTimers() }) { state, list ->
        state.copy(timers = list)
    }

    private suspend fun reloadRedemptions() = reloadList(TwitchList.REDEMPTIONS, { fetchRedemptions() }) { state, list ->
        state.copy(redemptions = list)
    }

    private suspend fun reloadLinks() = reloadList(TwitchList.LINKS, { fetchLinks() }) { state, list ->
        state.copy(links = list)
    }

    private suspend fun <T> reloadList(
        list: TwitchList,
        fetch: suspend () -> T,
        merge: (TwitchState, T) -> TwitchState,
    ) {
        val value = optional { fetch() }
        _state.update { state ->
            val updated = if (value != null) merge(state, value) else state
            updated.copy(failedLists = updated.failedLists.toggle(list, value == null))
        }
    }

    private suspend fun fetchHealth(): TwitchHealth =
        api.send(Endpoint(twitch("health")), TwitchHealth.serializer())

    private suspend fun fetchLinks(): List<TwitchAccountLink> =
        fetchList("links", TwitchAccountLink.serializer())

    private suspend fun fetchCustomCommands(): List<TwitchCustomCommand> =
        fetchList("custom-commands", TwitchCustomCommand.serializer())

    private suspend fun fetchTimers(): List<TwitchTimer> =
        fetchList("timers", TwitchTimer.serializer())

    private suspend fun fetchRedemptions(): List<TwitchRedemptionAction> =
        fetchList("redemptions", TwitchRedemptionAction.serializer())

    private suspend fun fetchQuotes(search: String): List<TwitchQuote> = api.send(
        Endpoint(twitch("quotes", "search" to search, "limit" to "50")),
        ListSerializer(TwitchQuote.serializer()),
    )

    private suspend fun <T> fetchList(tail: String, item: KSerializer<T>): List<T> =
        api.send(Endpoint(twitch(tail)), ListSerializer(item))

    private suspend fun checkOwner(): Boolean {
        if (userId.isEmpty()) return false
        return try {
            val raw = api.sendRaw(Endpoint("api/Ownership/$userId"))
            (raw as? JsonPrimitive)?.booleanOrNull ?: false
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            false
        }
    }

    private suspend fun attempt(failure: String, block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        postError(t.serverMessage(failure))
        false
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        null
    }

    private fun post(tail: String, body: JsonObject) = Endpoint(twitch(tail), HttpMethod.POST, body.encode())

    private fun twitch(tail: String, vararg query: Pair<String, String>): String {
        val params = buildString {
            append("guildId=").append(guildId)
            query.forEach { (key, value) -> append('&').append(key).append('=').append(value.urlEncoded()) }
        }
        return "api/Twitch/$tail?$params"
    }

    private fun JsonObject.encode(): String = MewdekoJson.encodeToString(JsonObject.serializer(), this)

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun Snowflake?.snowflakeNumber(): Long = this?.toLongOrNull() ?: 0L

    private fun Snowflake?.cleanSnowflake(): Snowflake? = this?.takeIf { it.isNotEmpty() && it != "0" }

    private fun Set<TwitchList>.toggle(list: TwitchList, failed: Boolean): Set<TwitchList> =
        if (failed) this + list else this - list

    /** The bot's `{ error }` or `{ message }` text for an HTTP failure, else [fallback]. */
    private fun Throwable.serverMessage(fallback: String): String {
        val http = this as? ApiError.Http ?: return fallback
        val parsed = runCatching { MewdekoJson.parseToJsonElement(http.body).jsonObject }.getOrNull()
        val text = parsed?.let { obj ->
            (obj["error"] as? JsonPrimitive)?.contentOrNull ?: (obj["message"] as? JsonPrimitive)?.contentOrNull
        }
        return text?.takeIf { it.isNotBlank() } ?: fallback
    }
}

/** Section and sub-section ids shared by the view model and the screen. */
object TwitchSections {
    /** Top-level section ids. */
    const val SETUP = "setup"
    const val COMMANDS = "commands"
    const val EVENTS = "events"
    const val LINKS = "links"

    /** Sub-section ids. */
    const val CONNECT = "connect"
    const val BOT_SETTINGS = "botsettings"
    const val CUSTOM = "custom"
    const val TIMERS = "timers"
    const val QUOTES = "quotes"
    const val ALERTS = "alerts"
    const val LIVE = "live"

    /** The first sub-section shown when [section] is selected. */
    fun firstSubSection(section: String): String = when (section) {
        SETUP -> CONNECT
        COMMANDS -> CUSTOM
        EVENTS -> ALERTS
        else -> ""
    }
}
