package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.AutoAssignRolesResponse
import dev.mewdeko.mobile.core.model.BirthdayUser
import dev.mewdeko.mobile.core.model.CountingChannel
import dev.mewdeko.mobile.core.model.FormSummary
import dev.mewdeko.mobile.core.model.MusicStatus
import dev.mewdeko.mobile.core.model.XpLeaderboardEntry
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.snowflakeIds
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import dev.mewdeko.mobile.feature.giveaways.GiveawayRecord
import dev.mewdeko.mobile.feature.messagestats.MessageStatsDetail
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
 * Community, entertainment and security load with the screen because the
 * pulse tiles and the now playing card need them above the fold; actions and
 * settings fetch the first time the automation band composes.
 */
enum class HomeSection(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    COMMUNITY("community", "Community"),
    ENTERTAINMENT("entertainment", "Entertainment"),
    ACTIONS("actions", "Actions"),
    SECURITY("security", "Security"),
    SETTINGS("settings", "Settings"),
}

/** Everything the community band and the pulse tiles read. */
data class CommunityData(
    val xpStats: XpServerStats? = null,
    val xpTop: List<XpLeaderboardEntry> = emptyList(),
    val messages: MessageStatsDetail? = null,
    val birthdays: BirthdaySummary? = null,
    val birthdaysToday: List<BirthdayUser> = emptyList(),
    val birthdaysUpcoming: List<BirthdayUser> = emptyList(),
    val tickets: TicketStatistics? = null,
    val ticketPanels: Int? = null,
    val highlights: List<StarboardHighlight> = emptyList(),
    val forms: List<FormSummary>? = null,
    val counting: List<CountingChannel>? = null,
    val patreonConnected: Boolean? = null,
    val patreonSupporters: Int? = null,
)

/** Everything the entertainment band and the now playing card read. */
data class EntertainmentData(
    val music: MusicStatus? = null,
    val giveaways: List<GiveawayRecord>? = null,
    val customVoiceChannels: Int? = null,
)

/** Automation counts the overview does not already own. */
data class ActionsData(
    val multiGreets: Int? = null,
    val repeaters: Int? = null,
)

/** Everything the safety band and the mod actions tile read. */
data class SecurityData(
    val protection: ProtectionFlags? = null,
    val warnings: List<RecentModerationAction>? = null,
)

/** Role assignment counts shown as automation chips. */
data class SettingsData(
    val autoAssignHumans: Int? = null,
    val autoAssignBots: Int? = null,
    val selfAssignable: Int? = null,
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
        ensureLoaded(HomeSection.COMMUNITY)
        ensureLoaded(HomeSection.ENTERTAINMENT)
        ensureLoaded(HomeSection.SECURITY)
    }

    /** Fetches [section] unless it is already loaded or in flight. */
    fun ensureLoaded(section: HomeSection) {
        val current = _state.value
        if (section in current.loaded || section in current.loading) return
        _state.update { it.copy(loading = it.loading + section) }
        viewModelScope.launch {
            runLoader(section)
            _state.update {
                it.copy(loading = it.loading - section, loaded = it.loaded + section)
            }
        }
    }

    /**
     * Refetches the profile and every band that has already loaded.
     *
     * Existing values stay on screen and are replaced in place as each call
     * lands, so a pull to refresh never empties a band.
     */
    fun refresh() {
        loadProfile()
        _state.value.loaded.forEach { section ->
            viewModelScope.launch { runLoader(section) }
        }
    }

    private suspend fun runLoader(section: HomeSection) {
        when (section) {
            HomeSection.OVERVIEW -> Unit
            HomeSection.COMMUNITY -> loadCommunity()
            HomeSection.ENTERTAINMENT -> loadEntertainment()
            HomeSection.ACTIONS -> loadActions()
            HomeSection.SECURITY -> loadSecurity()
            HomeSection.SETTINGS -> loadSettings()
        }
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
                many("api/Xp/$guildId/leaderboard?page=1&pageSize=5", XpLeaderboardEntry.serializer())
                    ?.let { v -> edit { it.copy(xpTop = v) } }
            },
            async {
                one("api/messagecount/$guildId/stats", MessageStatsDetail.serializer())
                    ?.let { v -> edit { it.copy(messages = v) } }
            },
            async {
                one("api/birthday/$guildId/stats", BirthdaySummary.serializer())
                    ?.let { v -> edit { it.copy(birthdays = v) } }
            },
            async {
                many("api/birthday/$guildId/today", BirthdayUser.serializer())
                    ?.let { v -> edit { it.copy(birthdaysToday = v) } }
            },
            async {
                many("api/birthday/$guildId/upcoming?days=7", BirthdayUser.serializer())
                    ?.let { v ->
                        edit { it.copy(birthdaysUpcoming = v.filter { user -> user.daysUntil > 0 }.take(5)) }
                    }
            },
            async {
                one("api/ticket/$guildId/statistics", TicketStatistics.serializer())
                    ?.let { v -> edit { it.copy(tickets = v) } }
            },
            async {
                count("api/Ticket/$guildId/panels")?.let { v -> edit { it.copy(ticketPanels = v) } }
            },
            async {
                many("api/Starboard/$guildId/highlights?limit=5", StarboardHighlight.serializer())
                    ?.let { v -> edit { it.copy(highlights = v) } }
            },
            async {
                many("api/forms/guild/$guildId?activeOnly=true", FormSummary.serializer())
                    ?.let { v -> edit { it.copy(forms = v) } }
            },
            async {
                many("api/Counting/$guildId/channels", CountingChannel.serializer())
                    ?.let { v -> edit { it.copy(counting = v) } }
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
                many("api/Giveaways/guild/$guildId", GiveawayRecord.serializer())
                    ?.let { v -> edit { it.copy(giveaways = v.filterNot { g -> g.isEnded }) } }
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
                    ?.let { v -> edit { it.copy(protection = v) } }
            },
            async {
                many("api/Moderation/$guildId/warnings", RecentModerationAction.serializer())
                    ?.let { v -> edit { it.copy(warnings = v) } }
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

/** The protection status payload, reduced to which modules are switched on. */
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
