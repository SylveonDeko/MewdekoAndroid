package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.AutoAssignRolesResponse
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.snowflakeIds
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/**
 * One band of the guild home.
 *
 * Overview is the landing content and loads immediately; the rest fetch the
 * first time they scroll into view, so opening a guild costs one round of
 * calls rather than twenty.
 */
enum class HomeSection(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    COMMUNITY("community", "Community"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    ACTIONS("actions", "Actions"),
    SECURITY("security", "Security"),
    SETTINGS("settings", "Settings"),
}

/** Everything the community band renders. */
data class CommunityData(
    val xpStats: XpServerStats? = null,
    val xpTop: List<XpLeader> = emptyList(),
    val messages: DailyMessageStats? = null,
    val birthdays: BirthdaySummary? = null,
    val tickets: TicketStatistics? = null,
    val highlights: List<StarboardHighlight> = emptyList(),
    val activeForms: Int? = null,
    val countingChannels: Int? = null,
    val patreonConnected: Boolean? = null,
    val patreonSupporters: Int? = null,
)

/** Everything the entertainment band renders. */
data class EntertainmentData(
    val music: MusicStatus? = null,
    val giveaways: Int? = null,
    val customVoiceChannels: Int? = null,
)

/** Everything the actions band renders. */
data class ActionsData(
    val roleGreets: Int? = null,
    val roleStates: Int? = null,
    val multiGreets: Int? = null,
    val repeaters: Int? = null,
)

/** Everything the security band renders. */
data class SecurityData(
    val activeProtections: Int? = null,
    val totalProtections: Int = 5,
    val recentActions: List<RecentModerationAction> = emptyList(),
    val warnings: Int? = null,
    val loggingEnabled: Int? = null,
)

/** Everything the settings band renders. */
data class SettingsData(
    val prefix: String? = null,
    val autoAssignHumans: Int? = null,
    val autoAssignBots: Int? = null,
    val selfAssignable: Int? = null,
    val ticketPanels: Int? = null,
)

/** Guild home state. Each band tracks its own load so one failure is local. */
data class GuildHomeState(
    val profile: BotGuildProfile? = null,
    val community: CommunityData = CommunityData(),
    val entertainment: EntertainmentData = EntertainmentData(),
    val actions: ActionsData = ActionsData(),
    val security: SecurityData = SecurityData(),
    val settings: SettingsData = SettingsData(),
    val loading: Set<HomeSection> = emptySet(),
    val loaded: Set<HomeSection> = emptySet(),
) {
    /** Whether [section] is still fetching. */
    fun isLoading(section: HomeSection) = section in loading
}

/**
 * Backs the category bands on the guild home.
 *
 * Kept separate from the overview view model: that one owns the guild's
 * headline statistics and the palette, while this owns roughly twenty
 * independent previews whose only shared behaviour is being fetched on demand.
 */
@HiltViewModel
class GuildHomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(GuildHomeState())

    /** Observable screen state. */
    val state: StateFlow<GuildHomeState> = _state.asStateFlow()

    init {
        loadProfile()
    }

    /**
     * Fetches [section] unless it is already loaded or in flight.
     *
     * Called when a band scrolls into view.
     */
    fun ensureLoaded(section: HomeSection) {
        val current = _state.value
        if (section in current.loaded || section in current.loading) return
        _state.update { it.copy(loading = it.loading + section) }
        viewModelScope.launch {
            when (section) {
                HomeSection.OVERVIEW -> Unit
                HomeSection.COMMUNITY -> loadCommunity()
                HomeSection.ENTERTAINMENT -> loadEntertainment()
                HomeSection.ACTIONS -> loadActions()
                HomeSection.SECURITY -> loadSecurity()
                HomeSection.SETTINGS -> loadSettings()
            }
            _state.update {
                it.copy(loading = it.loading - section, loaded = it.loaded + section)
            }
        }
    }

    /** Drops every cached band so the next view refetches. */
    fun invalidate() {
        _state.update { GuildHomeState() }
        loadProfile()
    }

    private fun loadProfile() = viewModelScope.launch {
        one("api/guild/$guildId/bot-profile", BotGuildProfile.serializer())
            ?.let { profile -> _state.update { it.copy(profile = profile) } }
    }

    private suspend fun loadCommunity() = coroutineScope {
        fun edit(transform: (CommunityData) -> CommunityData) =
            _state.update { it.copy(community = transform(it.community)) }

        listOf(
            async {
                one("api/Xp/$guildId/stats", XpServerStats.serializer())
                    ?.let { v -> edit { it.copy(xpStats = v) } }
            },
            async {
                many("api/Xp/$guildId/leaderboard?page=1&pageSize=5", XpLeader.serializer())
                    ?.let { v -> edit { it.copy(xpTop = v) } }
            },
            async {
                one("api/messagecount/$guildId/daily", DailyMessageStats.serializer())
                    ?.let { v -> edit { it.copy(messages = v) } }
            },
            async {
                one("api/birthday/$guildId/stats", BirthdaySummary.serializer())
                    ?.let { v -> edit { it.copy(birthdays = v) } }
            },
            async {
                one("api/ticket/$guildId/statistics", TicketStatistics.serializer())
                    ?.let { v -> edit { it.copy(tickets = v) } }
            },
            async {
                many("api/Starboard/$guildId/highlights?limit=5", StarboardHighlight.serializer())
                    ?.let { v -> edit { it.copy(highlights = v) } }
            },
            async {
                count("api/forms/guild/$guildId?activeOnly=true")
                    ?.let { v -> edit { it.copy(activeForms = v) } }
            },
            async {
                count("api/Counting/$guildId/channels")
                    ?.let { v -> edit { it.copy(countingChannels = v) } }
            },
            async {
                one("api/patreon/oauth/status?guildId=$guildId", PatreonLinkStatus.serializer())
                    ?.let { v -> edit { it.copy(patreonConnected = v.connected) } }
            },
            async {
                count("api/patreon/supporters?guildId=$guildId")
                    ?.let { v -> edit { it.copy(patreonSupporters = v) } }
            },
        ).awaitAll()
    }

    private suspend fun loadEntertainment() = coroutineScope {
        fun edit(transform: (EntertainmentData) -> EntertainmentData) =
            _state.update { it.copy(entertainment = transform(it.entertainment)) }

        listOf(
            async {
                one("api/Music/$guildId/status?userId=$userId", MusicStatus.serializer())
                    ?.let { v -> edit { it.copy(music = v) } }
            },
            async {
                count("api/Giveaways/$guildId")?.let { v -> edit { it.copy(giveaways = v) } }
            },
            async {
                count("api/CustomVoice/$guildId/channels")
                    ?.let { v -> edit { it.copy(customVoiceChannels = v) } }
            },
        ).awaitAll()
    }

    private suspend fun loadActions() = coroutineScope {
        fun edit(transform: (ActionsData) -> ActionsData) =
            _state.update { it.copy(actions = transform(it.actions)) }

        listOf(
            async {
                count("api/RoleGreet/$guildId")?.let { v -> edit { it.copy(roleGreets = v) } }
            },
            async {
                count("api/RoleStates/$guildId/all")?.let { v -> edit { it.copy(roleStates = v) } }
            },
            async {
                count("api/MultiGreet/$guildId")?.let { v -> edit { it.copy(multiGreets = v) } }
            },
            async {
                count("api/Repeaters/$guildId")?.let { v -> edit { it.copy(repeaters = v) } }
            },
        ).awaitAll()
    }

    private suspend fun loadSecurity() = coroutineScope {
        fun edit(transform: (SecurityData) -> SecurityData) =
            _state.update { it.copy(security = transform(it.security)) }

        listOf(
            async {
                one("api/Administration/$guildId/protection/status", ProtectionFlags.serializer())
                    ?.let { v -> edit { it.copy(activeProtections = v.activeCount) } }
            },
            async {
                many("api/Moderation/$guildId/recent?limit=10", RecentModerationAction.serializer())
                    ?.let { v -> edit { it.copy(recentActions = v) } }
            },
            async {
                count("api/Moderation/$guildId/warnings")?.let { v -> edit { it.copy(warnings = v) } }
            },
        ).awaitAll()
    }

    private suspend fun loadSettings() = coroutineScope {
        fun edit(transform: (SettingsData) -> SettingsData) =
            _state.update { it.copy(settings = transform(it.settings)) }

        listOf(
            async {
                one("api/Administration/$guildId/auto-assign-roles", AutoAssignRolesResponse.serializer())
                    ?.let { v ->
                        edit {
                            it.copy(
                                autoAssignHumans = v.normalRoles.size,
                                autoAssignBots = v.botRoles.size,
                            )
                        }
                    }
            },
            async {
                selfAssignableCount()?.let { v -> edit { it.copy(selfAssignable = v) } }
            },
            async {
                count("api/Ticket/$guildId/panels")?.let { v -> edit { it.copy(ticketPanels = v) } }
            },
        ).awaitAll()
    }

    private suspend fun <T> one(path: String, strategy: DeserializationStrategy<T>): T? =
        runCatching { api.send(Endpoint(path), strategy) }.getOrNull()

    private suspend fun <T> many(path: String, strategy: KSerializer<T>): List<T>? =
        runCatching { api.send(Endpoint(path), ListSerializer(strategy)) }.getOrNull()

    private suspend fun count(path: String): Int? =
        runCatching { api.sendArrayCount(path) }.getOrNull()

    /**
     * Counts self-assignable roles.
     *
     * The bot returns a tuple here rather than a bare array, so the collection
     * arrives wrapped in an object.
     */
    private suspend fun selfAssignableCount(): Int? = runCatching {
        api.sendRaw(Endpoint("api/Administration/$guildId/self-assignable-roles"))
            .snowflakeIds()
            .size
    }.getOrNull()
}

/** The protection status payload, reduced to how many modules are switched on. */
@Serializable
data class ProtectionFlags(
    val antiRaid: Toggle = Toggle(),
    val antiSpam: Toggle = Toggle(),
    val antiAlt: Toggle = Toggle(),
    val antiMassMention: Toggle = Toggle(),
    val antiMassPost: Toggle = Toggle(),
) {
    /** How many of the five modules report themselves enabled. */
    val activeCount: Int
        get() = listOf(antiRaid, antiSpam, antiAlt, antiMassMention, antiMassPost)
            .count { it.enabled }

    /** The one field each protection block is read for here. */
    @Serializable
    data class Toggle(val enabled: Boolean = false)
}
