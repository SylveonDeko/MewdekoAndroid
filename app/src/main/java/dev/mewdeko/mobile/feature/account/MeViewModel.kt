package dev.mewdeko.mobile.feature.account

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
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.StatusMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** Everything the Me tab shows for one guild. */
data class MeState(
    val guilds: List<Guild> = emptyList(),
    val selectedGuild: Guild? = null,
    val profile: UserProfile? = null,
    val preferences: UserPreferences? = null,
    val afk: AfkStatus? = null,
    val reputation: UserReputation? = null,
    val highlights: List<UserHighlight> = emptyList(),
    val highlightSettings: HighlightSettings? = null,
    val suggestions: List<MySuggestion> = emptyList(),
    val currency: CurrencyData? = null,
    val giveaways: List<MyGiveawayEntry> = emptyList(),
    val reminders: List<MyReminder> = emptyList(),
    val invites: InviteStats? = null,
    val messages: MessageStats? = null,
    val starboard: StarboardStats? = null,
    val analytics: UserAnalytics? = null,
    val load: LoadState = LoadState(),
)

/**
 * The per-user, per-guild Me tab.
 *
 * Every section loads concurrently and publishes independently, so a section
 * the bot has disabled does not blank the rest of the screen.
 */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val api: ApiClient,
    private val session: SessionHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(MeState())

    /** Observable screen state. */
    val state: StateFlow<MeState> = _state.asStateFlow()

    private val _status = MutableStateFlow<StatusMessage?>(null)

    /** The pending transient message, if any. */
    val status: StateFlow<StatusMessage?> = _status.asStateFlow()

    private val userId: Snowflake get() = session.userId

    init {
        loadGuilds()
    }

    /** Loads the guild picker's options. */
    fun loadGuilds() = viewModelScope.launch {
        _state.update { it.copy(load = it.load.loading()) }
        try {
            val guilds = api.send(
                Endpoint("api/ClientOperations/mutualguilds/$userId?adminOnly=false"),
                ListSerializer(Guild.serializer()),
            ).sortedBy { it.name.lowercase() }
            _state.update { it.copy(guilds = guilds, load = it.load.loaded()) }
            guilds.firstOrNull()?.let { selectGuild(it) }
        } catch (t: Throwable) {
            _state.update { it.copy(load = it.load.failed(t.userFacingMessage)) }
        }
    }

    /** Switches the guild whose stats are shown and reloads every section. */
    fun selectGuild(guild: Guild) {
        _state.update { it.copy(selectedGuild = guild) }
        load()
    }

    /** Reloads every section for the selected guild. */
    fun load(refreshing: Boolean = false) = viewModelScope.launch {
        val guildId = _state.value.selectedGuild?.id ?: return@launch
        _state.update { it.copy(load = it.load.loading(refreshing)) }

        coroutineScope {
            val profile = async { get(guildId, "profile", UserProfile.serializer()) }
            val preferences = async { get(guildId, "preferences", UserPreferences.serializer()) }
            val afk = async { get(guildId, "afk", AfkStatus.serializer()) }
            val reputation = async { get(guildId, "reputation", UserReputation.serializer()) }
            val highlights = async {
                getList(guildId, "highlights", UserHighlight.serializer())
            }
            val highlightSettings = async {
                get(guildId, "highlights/settings", HighlightSettings.serializer())
            }
            val suggestions = async { getList(guildId, "suggestions", MySuggestion.serializer()) }
            val currency = async { get(guildId, "currency", CurrencyData.serializer()) }
            val giveaways = async { getList(guildId, "giveaways", MyGiveawayEntry.serializer()) }
            val reminders = async { getList(guildId, "reminders", MyReminder.serializer()) }
            val invites = async { get(guildId, "invites", InviteStats.serializer()) }
            val messages = async { get(guildId, "messages", MessageStats.serializer()) }
            val starboard = async { get(guildId, "starboard", StarboardStats.serializer()) }
            val analytics = async { get(guildId, "analytics", UserAnalytics.serializer()) }

            _state.update {
                it.copy(
                    profile = profile.await(),
                    preferences = preferences.await(),
                    afk = afk.await(),
                    reputation = reputation.await(),
                    highlights = highlights.await(),
                    highlightSettings = highlightSettings.await(),
                    suggestions = suggestions.await(),
                    currency = currency.await(),
                    giveaways = giveaways.await(),
                    reminders = reminders.await(),
                    invites = invites.await(),
                    messages = messages.await(),
                    starboard = starboard.await(),
                    analytics = analytics.await(),
                    load = it.load.loaded(),
                )
            }
        }
    }

    /** Adds a highlight word. */
    fun addHighlight(word: String) = mutate("Failed to add highlight.") { guildId ->
        api.send(
            Endpoint(mePath(guildId, "highlights"), HttpMethod.POST, jsonString(word)),
            AddHighlightResponse.serializer(),
        )
        _state.update { it.copy(highlights = getList(guildId, "highlights", UserHighlight.serializer())) }
        _status.value = StatusMessage.success("Highlight added.")
    }

    /** Removes a highlight word. */
    fun removeHighlight(id: Int) = mutate("Failed to remove highlight.") { guildId ->
        api.sendIgnoringBody(Endpoint(mePath(guildId, "highlights/$id"), HttpMethod.DELETE))
        _state.update { it.copy(highlights = it.highlights.filterNot { entry -> entry.id == id }) }
        _status.value = StatusMessage.success("Highlight removed.")
    }

    /** Turns highlight notifications on or off for this guild. */
    fun setHighlightsEnabled(enabled: Boolean) = mutate("Failed to update highlights.") { guildId ->
        api.sendIgnoringBody(
            Endpoint(
                mePath(guildId, "highlights/settings"),
                HttpMethod.PUT,
                jsonBody("highlightsEnabled" to enabled),
            )
        )
        _state.update {
            it.copy(
                highlightSettings = it.highlightSettings?.copy(highlightsEnabled = enabled)
                    ?: HighlightSettings(highlightsEnabled = enabled),
            )
        }
    }

    /** Sets the signed-in user's AFK message for this guild. */
    fun setAfk(message: String) = mutate("Failed to set AFK.") { guildId ->
        api.sendIgnoringBody(
            Endpoint(
                mePath(guildId, "afk"),
                HttpMethod.POST,
                jsonBody("message" to message, "isTimed" to false),
            )
        )
        _state.update { it.copy(afk = get(guildId, "afk", AfkStatus.serializer())) }
        _status.value = StatusMessage.success("AFK set.")
    }

    /** Clears the signed-in user's AFK status for this guild. */
    fun clearAfk() = mutate("Failed to clear AFK.") { guildId ->
        api.sendIgnoringBody(Endpoint(mePath(guildId, "afk"), HttpMethod.DELETE))
        _state.update { it.copy(afk = get(guildId, "afk", AfkStatus.serializer())) }
        _status.value = StatusMessage.success("AFK cleared.")
    }

    /** Toggles level-up ping notifications. */
    fun toggleLevelUpPings() = togglePreference("preferences/toggle-levelup-pings")

    /** Toggles whether the user's pronouns are shown. */
    fun togglePronouns() = togglePreference("preferences/toggle-pronouns")

    /** Toggles the guided setup preference. */
    fun toggleGuidedSetup() = togglePreference("preferences/toggle-guided-setup")

    /** Toggles receiving greet DMs. */
    fun toggleGreetDms() = togglePreference("profile/toggle-greet-dms")

    /** Toggles inclusion in stats collection. */
    fun toggleStats() = togglePreference("profile/toggle-stats")

    /** Toggles birthday announcements. */
    fun toggleBirthdayAnnouncements() = togglePreference("profile/toggle-birthday-announcements")

    /** Clears the pending message once the snackbar has shown it. */
    fun clearStatus() {
        _status.value = null
    }

    private fun togglePreference(path: String) = mutate("Failed to update preference.") { guildId ->
        val response = api.send(
            Endpoint(mePath(guildId, path), HttpMethod.POST),
            PreferenceToggleResponse.serializer(),
        )
        _state.update { current ->
            current.copy(
                preferences = current.preferences?.let { prefs ->
                    prefs.copy(
                        levelUpPingsDisabled = response.levelUpPingsDisabled
                            ?: prefs.levelUpPingsDisabled,
                        pronounsDisabled = response.pronounsDisabled ?: prefs.pronounsDisabled,
                        prefersGuidedSetup = response.prefersGuidedSetup ?: prefs.prefersGuidedSetup,
                    )
                },
                profile = current.profile?.let { profile ->
                    profile.copy(
                        greetDmsOptOut = response.greetDmsOptOut ?: profile.greetDmsOptOut,
                        statsOptOut = response.statsOptOut ?: profile.statsOptOut,
                        birthdayAnnouncementsEnabled = response.birthdayAnnouncementsEnabled
                            ?: profile.birthdayAnnouncementsEnabled,
                    )
                },
            )
        }
    }

    private fun mutate(
        failureMessage: String,
        block: suspend (Snowflake) -> Unit,
    ) = viewModelScope.launch {
        val guildId = _state.value.selectedGuild?.id ?: return@launch
        try {
            block(guildId)
        } catch (t: Throwable) {
            _status.value = StatusMessage.error(failureMessage)
        }
    }

    private fun mePath(guildId: Snowflake, tail: String) = "api/me/$guildId/$userId/$tail"

    private suspend fun <T> get(
        guildId: Snowflake,
        tail: String,
        strategy: DeserializationStrategy<T>,
    ): T? = runCatching { api.send(Endpoint(mePath(guildId, tail)), strategy) }.getOrNull()

    private suspend fun <T> getList(
        guildId: Snowflake,
        tail: String,
        strategy: KSerializer<T>,
    ): List<T> = runCatching {
        api.send(Endpoint(mePath(guildId, tail)), ListSerializer(strategy))
    }.getOrDefault(emptyList())
}
