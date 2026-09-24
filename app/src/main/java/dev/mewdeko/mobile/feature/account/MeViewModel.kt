package dev.mewdeko.mobile.feature.account

import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.AddHighlightResponse
import dev.mewdeko.mobile.core.model.AfkStatus
import dev.mewdeko.mobile.core.model.CurrencyData
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.model.HighlightSettings
import dev.mewdeko.mobile.core.model.InviteStats
import dev.mewdeko.mobile.core.model.MessageStats
import dev.mewdeko.mobile.core.model.MyGiveawayEntry
import dev.mewdeko.mobile.core.model.MyReminder
import dev.mewdeko.mobile.core.model.MySuggestion
import dev.mewdeko.mobile.core.model.PreferenceToggleResponse
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.StarboardStats
import dev.mewdeko.mobile.core.model.UserAnalytics
import dev.mewdeko.mobile.core.model.UserHighlight
import dev.mewdeko.mobile.core.model.UserPreferences
import dev.mewdeko.mobile.core.model.UserProfile
import dev.mewdeko.mobile.core.model.UserReputation
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.net.normalizeKeys
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.theme.GuildColorStore
import dev.mewdeko.mobile.core.ui.StatusMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

/** Every independently loaded block of the Me tab. */
enum class MeSection {
    Profile,
    Preferences,
    Analytics,
    Reminders,
    Afk,
    Reputation,
    Highlights,
    HighlightSettings,
    Suggestions,
    Currency,
    Giveaways,
    Invites,
    Messages,
    Starboard,
}

/**
 * The starboard payload for one guild. A null [stats] means the guild has no
 * starboard configured, which the bot reports with an empty body.
 */
data class StarboardResult(val stats: StarboardStats?)

/**
 * Everything the Me tab shows.
 *
 * Every section stays null until it has loaded, so a card can tell loading
 * (null and not in [failed]) from a failure (null and in [failed]) from an
 * empty result (a loaded empty list).
 */
data class MeState(
    val guilds: List<Guild>? = null,
    val guildsLoading: Boolean = false,
    val guildsError: String? = null,
    val selectedGuildId: Snowflake? = null,
    val profile: UserProfile? = null,
    val preferences: UserPreferences? = null,
    val analytics: UserAnalytics? = null,
    val reminders: List<MyReminder>? = null,
    val afk: AfkStatus? = null,
    val reputation: UserReputation? = null,
    val highlights: List<UserHighlight>? = null,
    val highlightSettings: HighlightSettings? = null,
    val suggestions: List<MySuggestion>? = null,
    val currency: CurrencyData? = null,
    val giveaways: List<MyGiveawayEntry>? = null,
    val invites: InviteStats? = null,
    val messages: MessageStats? = null,
    val starboard: StarboardResult? = null,
    val failed: Set<MeSection> = emptySet(),
    val isRefreshing: Boolean = false,
    val dashboardHost: String? = null,
) {
    /** The guild whose per-guild sections are shown, once the list has loaded. */
    val selectedGuild: Guild? get() = guilds?.firstOrNull { it.id == selectedGuildId }

    /** Whether [section] failed its most recent load. */
    fun hasFailed(section: MeSection): Boolean = section in failed

    /** Clears every per-guild section so no card shows the previous guild's data. */
    fun withoutGuildSections(): MeState = copy(
        afk = null,
        reputation = null,
        highlights = null,
        highlightSettings = null,
        suggestions = null,
        currency = null,
        giveaways = null,
        invites = null,
        messages = null,
        starboard = null,
        failed = failed - GuildSections,
    )
}

/** Sections whose data depends on the selected guild. */
private val GuildSections = setOf(
    MeSection.Afk,
    MeSection.Reputation,
    MeSection.Highlights,
    MeSection.HighlightSettings,
    MeSection.Suggestions,
    MeSection.Currency,
    MeSection.Giveaways,
    MeSection.Invites,
    MeSection.Messages,
    MeSection.Starboard,
)

/**
 * The per-user Me tab.
 *
 * The guild list feeds a server picker; the selected guild drives the
 * per-guild sections, while profile, preferences, analytics and reminders
 * are user-wide and load once per session and on refresh. Every section
 * loads concurrently and publishes as soon as it resolves, and a generation
 * counter drops results that land after the guild changed.
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val api: ApiClient,
    private val session: SessionHolder,
    private val colorStore: GuildColorStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MeState(selectedGuildId = savedState.get<String>(SelectedGuildKey)),
    )

    /** Observable screen state. */
    val state: StateFlow<MeState> = _state.asStateFlow()

    private val _status = MutableStateFlow<StatusMessage?>(null)

    /** The pending error message, if any. Successes are never announced. */
    val status: StateFlow<StatusMessage?> = _status.asStateFlow()

    private val userId: Snowflake get() = session.userId

    private var guildJob: Job? = null
    private var globalJob: Job? = null
    private var guildGeneration = 0
    private var globalGeneration = 0
    private var hasAppeared = false

    init {
        viewModelScope.launch {
            _state.update { state ->
                state.copy(dashboardHost = api.currentBaseUrl()?.let { it.toUri().host })
            }
        }
        viewModelScope.launch {
            fetchGuilds()
            loadGlobal()
            loadGuild()
        }
    }

    /**
     * Themes the app from the user's avatar while the Me tab is on screen,
     * matching iOS, which lights the tab from the avatar rather than a guild.
     */
    fun applyUserPalette(avatarUrl: String?) = colorStore.update(avatarUrl)

    /** Returns the theme to the default palette when the Me tab leaves. */
    fun releaseUserPalette(avatarUrl: String?) = colorStore.release(avatarUrl)

    /**
     * Called each time the screen starts. The first call is covered by the
     * initial load; later ones refetch every section quietly, so toggles and
     * AFK state changed elsewhere show their server value on return.
     */
    fun onAppear() {
        if (!hasAppeared) {
            hasAppeared = true
            return
        }
        loadGlobal()
        loadGuild()
    }

    /** Pull to refresh: reloads the guild list, then every section. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            fetchGuilds()
            listOfNotNull(loadGlobal(), loadGuild()).joinAll()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /** Retries the guild list after it failed to load. */
    fun retryGuilds() {
        viewModelScope.launch {
            fetchGuilds()
            loadGlobal()
            loadGuild()
        }
    }

    /** Switches the guild whose per-guild sections are shown. */
    fun selectGuild(id: Snowflake) {
        if (id == _state.value.selectedGuildId) return
        select(id)
        if (_state.value.profile == null && globalJob?.isActive != true) loadGlobal()
        loadGuild()
    }

    /** Adds a highlight word; [onDone] learns whether it was saved. */
    fun addHighlight(word: String, onDone: (Boolean) -> Unit) = mutate(
        onFailure = { onDone(false) },
    ) { guildId ->
        api.send(
            Endpoint(mePath(guildId, "highlights"), HttpMethod.POST, jsonString(word)),
            AddHighlightResponse.serializer(),
        )
        val updated = runCatching {
            getList(guildId, "highlights", UserHighlight.serializer())
        }.getOrNull()
        if (updated != null && isSelected(guildId)) {
            _state.update { it.copy(highlights = updated, failed = it.failed - MeSection.Highlights) }
        }
        onDone(true)
    }

    /** Removes a highlight word, optimistically, restoring it if the bot refuses. */
    fun removeHighlight(id: Int) {
        val guildId = _state.value.selectedGuildId ?: return
        val before = _state.value.highlights
        _state.update { state -> state.copy(highlights = state.highlights?.filterNot { it.id == id }) }
        viewModelScope.launch {
            try {
                api.sendIgnoringBody(Endpoint(mePath(guildId, "highlights/$id"), HttpMethod.DELETE))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (isSelected(guildId)) _state.update { it.copy(highlights = before) }
                _status.value = StatusMessage.error(t.userFacingMessage)
            }
        }
    }

    /** Turns highlight DMs on or off for the selected guild, optimistically. */
    fun setHighlightsEnabled(enabled: Boolean) {
        val guildId = _state.value.selectedGuildId ?: return
        val before = _state.value.highlightSettings ?: return
        _state.update { it.copy(highlightSettings = before.copy(highlightsEnabled = enabled)) }
        viewModelScope.launch {
            try {
                api.sendIgnoringBody(
                    Endpoint(
                        mePath(guildId, "highlights/settings"),
                        HttpMethod.PUT,
                        jsonBody("highlightsEnabled" to enabled),
                    )
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (isSelected(guildId)) _state.update { it.copy(highlightSettings = before) }
                _status.value = StatusMessage.error(t.userFacingMessage)
            }
        }
    }

    /**
     * Sets or updates the AFK message for the selected guild. [onDone]
     * receives null on success, or the bot's message (such as the guild's
     * length limit) on failure, for the editor to show inline.
     */
    fun setAfk(message: String, onDone: (String?) -> Unit) {
        val guildId = _state.value.selectedGuildId ?: return
        viewModelScope.launch {
            try {
                api.sendIgnoringBody(
                    Endpoint(
                        mePath(guildId, "afk"),
                        HttpMethod.POST,
                        jsonBody("message" to message, "isTimed" to false),
                    )
                )
                refreshAfk(guildId)
                onDone(null)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                onDone(t.userFacingMessage)
            }
        }
    }

    /** Clears the AFK status for the selected guild. */
    fun clearAfk() = mutate { guildId ->
        api.sendIgnoringBody(Endpoint(mePath(guildId, "afk"), HttpMethod.DELETE))
        refreshAfk(guildId)
    }

    /** Toggles level-up ping notifications. */
    fun toggleLevelUpPings() = toggle(PreferenceToggle.LevelUpPings)

    /** Toggles whether the bot shows the user's pronouns. */
    fun togglePronouns() = toggle(PreferenceToggle.Pronouns)

    /** Toggles the guided setup preference. */
    fun toggleGuidedSetup() = toggle(PreferenceToggle.GuidedSetup)

    /** Toggles receiving greet DMs. */
    fun toggleGreetDms() = toggle(PreferenceToggle.GreetDms)

    /** Toggles inclusion in stat tracking. */
    fun toggleStats() = toggle(PreferenceToggle.Stats)

    /** Toggles birthday announcements. */
    fun toggleBirthdayAnnouncements() = toggle(PreferenceToggle.BirthdayAnnouncements)

    /** Clears the pending message once the snackbar has shown it. */
    fun clearStatus() {
        _status.value = null
    }

    /**
     * Loads the guild list, keeping the current selection when it is still
     * present and otherwise defaulting to the first guild, as iOS does.
     */
    private suspend fun fetchGuilds() {
        val firstLoad = _state.value.guilds == null
        _state.update { it.copy(guildsLoading = firstLoad, guildsError = null) }
        try {
            val guilds = api.send(
                Endpoint("api/ClientOperations/mutualguilds/$userId?adminOnly=false"),
                ListSerializer(Guild.serializer()),
            ).sortedBy { it.name.lowercase() }
            val current = _state.value.selectedGuildId
            val next = current?.takeIf { id -> guilds.any { it.id == id } } ?: guilds.firstOrNull()?.id
            _state.update { it.copy(guilds = guilds, guildsLoading = false) }
            if (next != current) select(next)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.update { it.copy(guildsLoading = false, guildsError = t.userFacingMessage) }
            if (!firstLoad) _status.value = StatusMessage.error(t.userFacingMessage)
        }
    }

    /**
     * Records [id] as the selection, cancelling the in-flight guild load and
     * clearing the per-guild sections so they show their loading state.
     */
    private fun select(id: Snowflake?) {
        guildJob?.cancel()
        guildGeneration++
        savedState[SelectedGuildKey] = id
        _state.update { it.withoutGuildSections().copy(selectedGuildId = id) }
    }

    /**
     * Loads the user-wide sections. The route still carries a guild id, and
     * reminders checks membership, so this waits for a selected guild.
     */
    private fun loadGlobal(): Job? {
        val guildId = _state.value.selectedGuildId ?: return null
        globalJob?.cancel()
        val generation = ++globalGeneration
        val current = { generation == globalGeneration }
        return viewModelScope.launch {
            section(MeSection.Profile, current, { get(guildId, "profile", UserProfile.serializer()) }) { s, v ->
                s.copy(profile = v)
            }
            section(MeSection.Preferences, current, { get(guildId, "preferences", UserPreferences.serializer()) }) { s, v ->
                s.copy(preferences = v)
            }
            section(MeSection.Analytics, current, { get(guildId, "analytics", UserAnalytics.serializer()) }) { s, v ->
                s.copy(analytics = v)
            }
            section(MeSection.Reminders, current, { getList(guildId, "reminders", MyReminder.serializer()) }) { s, v ->
                s.copy(reminders = v)
            }
        }.also { globalJob = it }
    }

    /** Loads every per-guild section for the selected guild. */
    private fun loadGuild(): Job? {
        val guildId = _state.value.selectedGuildId ?: return null
        guildJob?.cancel()
        val generation = ++guildGeneration
        val current = { generation == guildGeneration && _state.value.selectedGuildId == guildId }
        return viewModelScope.launch {
            section(MeSection.Afk, current, { get(guildId, "afk", AfkStatus.serializer()) }) { s, v ->
                s.copy(afk = v)
            }
            section(MeSection.Reputation, current, { get(guildId, "reputation", UserReputation.serializer()) }) { s, v ->
                s.copy(reputation = v)
            }
            section(MeSection.Highlights, current, { getList(guildId, "highlights", UserHighlight.serializer()) }) { s, v ->
                s.copy(highlights = v)
            }
            section(
                MeSection.HighlightSettings,
                current,
                { get(guildId, "highlights/settings", HighlightSettings.serializer()) },
            ) { s, v -> s.copy(highlightSettings = v) }
            section(MeSection.Suggestions, current, { getList(guildId, "suggestions", MySuggestion.serializer()) }) { s, v ->
                s.copy(suggestions = v)
            }
            section(MeSection.Currency, current, { get(guildId, "currency", CurrencyData.serializer()) }) { s, v ->
                s.copy(currency = v)
            }
            section(MeSection.Giveaways, current, { getList(guildId, "giveaways", MyGiveawayEntry.serializer()) }) { s, v ->
                s.copy(giveaways = v)
            }
            section(MeSection.Invites, current, { get(guildId, "invites", InviteStats.serializer()) }) { s, v ->
                s.copy(invites = v)
            }
            section(MeSection.Messages, current, { get(guildId, "messages", MessageStats.serializer()) }) { s, v ->
                s.copy(messages = v)
            }
            section(MeSection.Starboard, current, { getStarboard(guildId) }) { s, v ->
                s.copy(starboard = v)
            }
        }.also { guildJob = it }
    }

    /**
     * Fetches one section in its own coroutine and publishes it the moment it
     * resolves, unless [isCurrent] says a newer load has superseded it. A
     * failure keeps whatever the section showed before and flags it.
     */
    private fun <T> CoroutineScope.section(
        section: MeSection,
        isCurrent: () -> Boolean,
        fetch: suspend () -> T,
        apply: (MeState, T) -> MeState,
    ) = launch {
        val result = try {
            Result.success(fetch())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
        if (!isCurrent()) return@launch
        _state.update { state ->
            result.fold(
                onSuccess = { apply(state, it).copy(failed = state.failed - section) },
                onFailure = { state.copy(failed = state.failed + section) },
            )
        }
    }

    private fun toggle(which: PreferenceToggle) {
        val guildId = _state.value.selectedGuildId ?: return
        val before = _state.value
        if (which.global && before.preferences == null) return
        if (!which.global && before.profile == null) return
        _state.update(which.flip)
        viewModelScope.launch {
            try {
                val response = api.send(
                    Endpoint(mePath(guildId, which.path), HttpMethod.POST),
                    PreferenceToggleResponse.serializer(),
                )
                _state.update { it.applying(response) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update(which.flip)
                _status.value = StatusMessage.error(t.userFacingMessage)
            }
        }
    }

    private fun mutate(
        onFailure: () -> Unit = {},
        block: suspend (Snowflake) -> Unit,
    ) {
        val guildId = _state.value.selectedGuildId ?: return
        viewModelScope.launch {
            try {
                block(guildId)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                onFailure()
                _status.value = StatusMessage.error(t.userFacingMessage)
            }
        }
    }

    private suspend fun refreshAfk(guildId: Snowflake) {
        val afk = runCatching { get(guildId, "afk", AfkStatus.serializer()) }.getOrNull() ?: return
        if (isSelected(guildId)) _state.update { it.copy(afk = afk, failed = it.failed - MeSection.Afk) }
    }

    private fun isSelected(guildId: Snowflake) = _state.value.selectedGuildId == guildId

    private fun mePath(guildId: Snowflake, tail: String) = "api/me/$guildId/$userId/$tail"

    private suspend fun <T> get(
        guildId: Snowflake,
        tail: String,
        strategy: DeserializationStrategy<T>,
    ): T = api.send(Endpoint(mePath(guildId, tail)), strategy)

    private suspend fun <T> getList(
        guildId: Snowflake,
        tail: String,
        strategy: KSerializer<T>,
    ): List<T> = api.send(Endpoint(mePath(guildId, tail)), ListSerializer(strategy))

    /**
     * The starboard payload. A guild without a starboard answers with no
     * body, which reaches the app as JSON `null` through the dashboard proxy
     * or as an empty body directly.
     */
    private suspend fun getStarboard(guildId: Snowflake): StarboardResult {
        val raw = api.sendRaw(Endpoint(mePath(guildId, "starboard")))
        if (raw is JsonNull || (raw is JsonObject && raw.isEmpty())) return StarboardResult(null)
        return StarboardResult(
            MewdekoJson.decodeFromJsonElement(StarboardStats.serializer(), raw.normalizeKeys()),
        )
    }

    private companion object {
        /** SavedStateHandle key for the selected guild, so it survives process death. */
        const val SelectedGuildKey = "meSelectedGuildId"
    }
}

/**
 * One of the six preference switches: its toggle endpoint, whether it lives
 * in the preferences payload (otherwise the profile payload), and the local
 * flip applied before the bot answers.
 */
private enum class PreferenceToggle(
    val path: String,
    val global: Boolean,
    val flip: (MeState) -> MeState,
) {
    LevelUpPings("preferences/toggle-levelup-pings", true, { s ->
        s.copy(preferences = s.preferences?.let { it.copy(levelUpPingsDisabled = !it.levelUpPingsDisabled) })
    }),
    Pronouns("preferences/toggle-pronouns", true, { s ->
        s.copy(preferences = s.preferences?.let { it.copy(pronounsDisabled = !it.pronounsDisabled) })
    }),
    GuidedSetup("preferences/toggle-guided-setup", true, { s ->
        s.copy(preferences = s.preferences?.let { it.copy(prefersGuidedSetup = !it.prefersGuidedSetup) })
    }),
    GreetDms("profile/toggle-greet-dms", false, { s ->
        s.copy(profile = s.profile?.let { it.copy(greetDmsOptOut = !it.greetDmsOptOut) })
    }),
    Stats("profile/toggle-stats", false, { s ->
        s.copy(profile = s.profile?.let { it.copy(statsOptOut = !it.statsOptOut) })
    }),
    BirthdayAnnouncements("profile/toggle-birthday-announcements", false, { s ->
        s.copy(profile = s.profile?.let { it.copy(birthdayAnnouncementsEnabled = !it.birthdayAnnouncementsEnabled) })
    }),
}

/** Applies the server's answer to a preference toggle over the optimistic flip. */
private fun MeState.applying(response: PreferenceToggleResponse): MeState = copy(
    preferences = preferences?.let { prefs ->
        prefs.copy(
            levelUpPingsDisabled = response.levelUpPingsDisabled ?: prefs.levelUpPingsDisabled,
            pronounsDisabled = response.pronounsDisabled ?: prefs.pronounsDisabled,
            prefersGuidedSetup = response.prefersGuidedSetup ?: prefs.prefersGuidedSetup,
        )
    },
    profile = profile?.let { profile ->
        profile.copy(
            greetDmsOptOut = response.greetDmsOptOut ?: profile.greetDmsOptOut,
            statsOptOut = response.statsOptOut ?: profile.statsOptOut,
            birthdayAnnouncementsEnabled = response.birthdayAnnouncementsEnabled
                ?: profile.birthdayAnnouncementsEnabled,
        )
    },
)
