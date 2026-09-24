package dev.mewdeko.mobile.feature.owner.bothells

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

/**
 * Finds servers littered with bots and leaves them in bulk, mirroring the
 * dashboard's `/owner/bot-hells`.
 *
 * Everything belongs to the selected bot instance: the server list, the
 * thresholds, and the report channel. The list arrives whole in one response
 * and is filtered locally; settings ride along with it rather than being
 * fetched separately. There is no polling; refreshes are manual.
 */
@HiltViewModel
class BotHellsViewModel @Inject constructor(
    api: ApiClient,
    private val session: SessionHolder,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(BotHellsState())

    /** Observable screen state. */
    val state: StateFlow<BotHellsState> = _state.asStateFlow()

    /** The selected bot's own id, used to warn when a leave will delete a server the bot owns. */
    val botId: Snowflake?
        get() = session.instance.value?.botId?.takeIf { it.isNotEmpty() }

    init {
        markLoaded()
        load()
        viewModelScope.launch {
            session.instance
                .map { it?.botId }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    _state.update { current -> BotHellsState(tab = current.tab) }
                    load()
                }
        }
    }

    /**
     * Fetches every server on the instance with the flagged count and the
     * settings. The chrome stays on screen throughout: skeleton rows cover a
     * first load, and a failure is shown in the list area.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val result = request(Endpoint(LIST_PATH), BotHellListResponse.serializer())
            _state.update { current ->
                val present = result.items.mapTo(HashSet()) { it.guildId }
                current.copy(
                    loading = false,
                    entries = result.items,
                    flaggedCount = result.flagged,
                    settings = result.settings ?: current.settings,
                    settingsLoadError = null,
                    selected = current.selected.filterTo(HashSet()) { it in present },
                ).reseeded()
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            val message = if (t is NotOwnerException) NOT_OWNER_MESSAGE else "Failed to load servers"
            _state.update { it.copy(loading = false, error = message) }
        }
    }

    /** Switches between the servers and settings tabs. */
    fun setTab(id: String) = _state.update { it.copy(tab = id) }

    /** Updates the name or id search. */
    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    /** Flips between flagged servers only (the default) and every server. */
    fun toggleFlaggedOnly() = _state.update { it.copy(flaggedOnly = !it.flaggedOnly) }

    /** Adds or removes one server from the selection. */
    fun toggleSelect(id: Snowflake) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    /**
     * Selects every listed server, or clears them when all are already
     * selected, like the dashboard table's header checkbox.
     */
    fun toggleSelectAllVisible() = _state.update { current ->
        val visible = current.visibleEntries().map { it.guildId }
        val allSelected = visible.isNotEmpty() && visible.all { it in current.selected }
        current.copy(
            selected = if (allSelected) current.selected - visible.toSet() else current.selected + visible,
        )
    }

    /** Replaces the selection with every flagged server. */
    fun selectAllFlagged() = _state.update { current ->
        current.copy(selected = current.entries.filter { it.isBotHell }.mapTo(HashSet()) { it.guildId })
    }

    /** Clears the selection. */
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /**
     * Re-evaluates one server after the bot downloads its full member list,
     * the only way to get exact counts for large servers whose cache is
     * partial. Several rechecks may run at once; each keeps its own row
     * spinner, and a failure is a transient message so the list stays.
     */
    fun recheck(entry: BotHellEntry) {
        val id = entry.guildId
        if (id.isEmpty() || id in _state.value.checking) return
        _state.update { it.copy(checking = it.checking + id) }
        viewModelScope.launch {
            try {
                val updated = request(
                    Endpoint("$LIST_PATH/$id/check", HttpMethod.POST),
                    BotHellEntry.serializer(),
                )
                _state.update { current ->
                    val entries = current.entries.map { if (it.guildId == id) updated else it }
                    current.copy(entries = entries, flaggedCount = entries.count { it.isBotHell })
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                postError(
                    when {
                        t is NotOwnerException -> NOT_OWNER_MESSAGE
                        t is ApiError.Http && t.status == 404 -> "The bot is no longer in ${entry.displayName}"
                        else -> "Failed to recheck ${entry.displayName}"
                    },
                )
            } finally {
                _state.update { it.copy(checking = it.checking - id) }
            }
        }
    }

    /** Opens the leave confirmation for [targets]. */
    fun requestLeave(targets: List<BotHellEntry>) {
        if (targets.isEmpty() || _state.value.leaving) return
        _state.update { it.copy(leaveTargets = targets) }
    }

    /** Opens the leave confirmation for every selected server. */
    fun requestLeaveSelected() {
        val current = _state.value
        requestLeave(current.entries.filter { it.guildId in current.selected })
    }

    /** Closes the leave confirmation without leaving anything. */
    fun cancelLeave() = _state.update { it.copy(leaveTargets = emptyList()) }

    /**
     * Leaves the confirmed servers in one request. The bot works through them
     * one at a time (deleting any it owns), so this can take a while; the
     * outcome, including partial failures, lands in the banner and the list
     * is re-read. Never retried automatically.
     */
    fun confirmLeave() {
        val targets = _state.value.leaveTargets
        if (targets.isEmpty() || _state.value.leaving) return
        _state.update {
            it.copy(leaveTargets = emptyList(), leaving = true, leavingCount = targets.size, banner = null)
        }
        viewModelScope.launch {
            try {
                val ids = targets.map { it.guildId }
                    .filter { id -> id.isNotEmpty() && id.all(Char::isDigit) }
                    .distinct()
                val body = "{\"guildIds\":[${ids.joinToString(",")}]}"
                val result = request(
                    Endpoint("$LIST_PATH/leave", HttpMethod.POST, body),
                    BotHellLeaveResponse.serializer(),
                )
                val left = result.left.size
                val failed = result.failed
                val text = if (failed.isEmpty()) {
                    "Left $left server${if (left == 1) "" else "s"}"
                } else {
                    "Left $left, ${failed.size} failed: ${failed.values.joinToString("; ")}"
                }
                _state.update {
                    it.copy(banner = BotHellsBanner(text, isError = failed.isNotEmpty()), selected = emptySet())
                }
                load()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val message = when (t) {
                    is NotOwnerException -> NOT_OWNER_MESSAGE
                    is ApiError.Transport ->
                        "The connection dropped before the bot finished. Refresh to see which servers it left."
                    else -> t.apiMessage("Failed to leave the selected servers")
                }
                _state.update { it.copy(banner = BotHellsBanner(message, isError = true)) }
                if (t is ApiError.Transport) load()
            } finally {
                _state.update { it.copy(leaving = false, leavingCount = 0) }
            }
        }
    }

    /** Edits the minimum members input. */
    fun setMinMembers(value: String) = _state.update { it.copy(minMembersInput = value) }

    /** Edits the bot count input. */
    fun setBotCount(value: String) = _state.update { it.copy(botCountInput = value) }

    /** Edits the bot percentage input. */
    fun setBotPercent(value: String) = _state.update { it.copy(botPercentInput = value) }

    /** Edits the report channel id input. */
    fun setChannel(value: String) = _state.update { it.copy(channelInput = value) }

    /** Saves the typed thresholds and channel, keeping the current auto leave value. */
    fun saveThresholds() {
        val settings = _state.value.settings ?: return
        saveSettings(settings.autoLeave)
    }

    /**
     * Flips auto leave. Like the dashboard, this saves immediately and also
     * commits whatever is typed in the threshold fields.
     */
    fun setAutoLeave(value: Boolean) = saveSettings(value)

    /** Re-reads the settings on their own, for the settings tab's retry. */
    fun retrySettings() {
        if (_state.value.settingsLoading) return
        viewModelScope.launch {
            _state.update { it.copy(settingsLoading = true, settingsLoadError = null) }
            val ok = fetchSettings()
            _state.update {
                it.copy(
                    settingsLoading = false,
                    settingsLoadError = if (ok) null else "Failed to load the settings",
                )
            }
        }
    }

    /**
     * Validates and saves every setting. On success the list is re-read so
     * the flags reflect the new thresholds; the screen shows the change, so
     * there is no success message. On failure the error stays visible while
     * the inputs are reseeded from a fresh read.
     */
    private fun saveSettings(autoLeave: Boolean) {
        val current = _state.value
        if (current.settings == null || current.savingSettings) return
        val minMembers = current.minMembersInput.trim().toIntOrNull()
        val botCount = current.botCountInput.trim().toIntOrNull()
        val botPercent = current.botPercentInput.trim().toIntOrNull()
        val channel = current.channelInput.trim()

        if (minMembers == null || botCount == null || botPercent == null ||
            minMembers < 0 || botCount < 0 || botPercent < 0
        ) {
            _state.update { it.copy(settingsError = "Thresholds must be whole numbers, zero or above") }
            return
        }
        if (botPercent > 100) {
            _state.update { it.copy(settingsError = "The bot percentage cannot be above 100") }
            return
        }
        if (channel.isNotEmpty() && !CHANNEL_ID.matches(channel)) {
            _state.update { it.copy(settingsError = "That does not look like a channel ID") }
            return
        }

        _state.update { it.copy(savingSettings = true, settingsError = null, pendingAutoLeave = autoLeave) }
        viewModelScope.launch {
            try {
                val body = "{\"minMembers\":$minMembers,\"botCount\":$botCount," +
                    "\"botPercent\":$botPercent,\"autoLeave\":$autoLeave," +
                    "\"channelId\":${channel.ifEmpty { "0" }}}"
                val saved = request(
                    Endpoint(SETTINGS_PATH, HttpMethod.POST, body),
                    BotHellSettings.serializer(),
                )
                _state.update { it.copy(settings = saved, pendingAutoLeave = null).reseeded() }
                load()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val message = if (t is NotOwnerException) {
                    NOT_OWNER_MESSAGE
                } else {
                    t.apiMessage("Failed to save the settings")
                }
                _state.update { it.copy(settingsError = message, pendingAutoLeave = null) }
                fetchSettings()
            } finally {
                _state.update { it.copy(savingSettings = false, pendingAutoLeave = null) }
            }
        }
    }

    /**
     * Reads the settings alone and reseeds the inputs, leaving any save
     * error in place. Returns whether the read succeeded.
     */
    private suspend fun fetchSettings(): Boolean = try {
        val settings = request(Endpoint(SETTINGS_PATH), BotHellSettings.serializer())
        _state.update { it.copy(settings = settings, settingsLoadError = null).reseeded() }
        true
    } catch (c: CancellationException) {
        throw c
    } catch (_: Throwable) {
        false
    }

    /**
     * Sends [endpoint] and decodes the body with [strategy].
     *
     * The bot answers non-owners with `Forbid()`, which reaches the app as a
     * 401 or 403, or, because the dashboard proxy turns an empty upstream
     * body into JSON `null` with a 200, as a bare `null`. All of those raise
     * [NotOwnerException].
     */
    private suspend fun <T> request(endpoint: Endpoint, strategy: DeserializationStrategy<T>): T {
        val raw: JsonElement = try {
            api.sendRaw(endpoint)
        } catch (e: ApiError.Http) {
            if (e.status == 401 || e.status == 403) throw NotOwnerException()
            throw e
        }
        if (raw is JsonNull) throw NotOwnerException()
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.decodeFromJsonElement(strategy, raw.normalizeKeys())
            } catch (t: Throwable) {
                throw ApiError.Decoding(t)
            }
        }
    }

    /** Raised when the bot refuses a request because the user is not one of its owners. */
    private class NotOwnerException : Exception(NOT_OWNER_MESSAGE)

    private companion object {
        const val LIST_PATH = "api/BotHell"
        const val SETTINGS_PATH = "api/BotHell/settings"
        const val NOT_OWNER_MESSAGE = "Only the bot's owners can use Bot Hells."
        val CHANNEL_ID = Regex("^\\d{17,20}$")
    }
}

/** The name to use in messages, falling back to the id when the name is blank. */
private val BotHellEntry.displayName: String
    get() = guildName.ifBlank { guildId }

/** Copies the saved settings into the four inputs; the channel is empty when using the fallback. */
private fun BotHellsState.reseeded(): BotHellsState {
    val saved = settings ?: return this
    return copy(
        minMembersInput = saved.minMembers.toString(),
        botCountInput = saved.botCount.toString(),
        botPercentInput = saved.botPercent.toString(),
        channelInput = saved.channelInputText,
    )
}

/**
 * The user facing text for a failed request, mirroring the dashboard's
 * `errorMessageFrom`: the proxy's string `error`, then `error.message`, then
 * a top level `message`, then the raw body, then the status. Anything that
 * is not an HTTP failure uses [fallback].
 */
private fun Throwable.apiMessage(fallback: String): String {
    if (this !is ApiError.Http) return fallback
    val parsed = runCatching { MewdekoJson.parseToJsonElement(body) }.getOrNull() as? JsonObject
    if (parsed != null) {
        val error = parsed["error"]
        (error as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() }?.let { return it }
        (parsed["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return body.trim().takeIf { it.isNotEmpty() }?.take(300) ?: "Request failed with status $status"
}
