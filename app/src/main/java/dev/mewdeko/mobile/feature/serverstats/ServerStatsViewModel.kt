package dev.mewdeko.mobile.feature.serverstats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildChannelLite
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import javax.inject.Inject
import io.ktor.http.HttpMethod as KtorMethod

/** Channel types offered by the Lookup channel picker and the ignored channels list. */
private val PickableChannelTypes = setOf("text", "voice", "stage", "announcement")

/** Screen state for Activity Stats. */
data class ServerStatsState(
    val section: String = "overview",
    val lookback: Int = 14,
    val channels: List<GuildChannelLite> = emptyList(),
    val roles: List<GuildRole> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val settings: ServerStatsSettings? = null,
    val exclusions: StatsExclusions = StatsExclusions(),
    val activityFilters: ActivityFilters = ActivityFilters(),
    val overview: ServerOverview? = null,
    val topGames: List<ActivityRow> = emptyList(),
    val messageSeries: List<SeriesPoint> = emptyList(),
    val voiceSeries: List<SeriesPoint> = emptyList(),
    val snapshots: List<GuildSnapshot> = emptyList(),
    val joinLeave: JoinLeaveSeries = JoinLeaveSeries(),
    val overviewLoading: Boolean = false,
    val overviewError: String? = null,
    val rankKind: StatKind = StatKind.MESSAGES,
    val topUsers: List<RankedEntry> = emptyList(),
    val topChannels: List<RankedEntry> = emptyList(),
    val topActivities: List<ActivityRow> = emptyList(),
    val topLoading: Boolean = false,
    val topError: String? = null,
    val isExporting: Boolean = false,
    val lookupUserId: Snowflake? = null,
    val userActivity: UserActivity? = null,
    val userGames: List<ActivityRow> = emptyList(),
    val userLoading: Boolean = false,
    val userError: String? = null,
    val lookupChannelId: Snowflake? = null,
    val channelActivity: ChannelActivity? = null,
    val channelLoading: Boolean = false,
    val channelError: String? = null,
    val gameQuery: String = "",
    val gameDetail: ActivityDetail? = null,
    val gameLoading: Boolean = false,
    val gameError: String? = null,
    val lookbackDraft: String = "14",
    val cooldownDraft: String = "0",
    val filterDraft: String = "",
) {
    /** Whether games and apps are tracked, which gates the games ranking and lookups. */
    val tracksActivities: Boolean get() = settings?.trackActivities == true

    /** The ranking kinds currently offered. */
    val rankKinds: List<StatKind>
        get() = if (tracksActivities) StatKind.entries else listOf(StatKind.MESSAGES, StatKind.VOICE)

    /** The window charts use: all time falls back to 90 days, like the dashboard. */
    val chartDays: Int get() = maxOf(1, if (lookback == 0) 90 else lookback)
}

/** Loads activity statistics, rankings, lookups, and tracking settings for one guild. */
@HiltViewModel
class ServerStatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
    private val http: HttpClient,
    private val auth: AuthManager,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ServerStatsState())

    /** Observable screen state. */
    val state: StateFlow<ServerStatsState> = _state.asStateFlow()

    private var hasLoadedOnce = false
    private var overviewJob: Job? = null
    private var topJob: Job? = null
    private var userJob: Job? = null
    private var channelJob: Job? = null
    private var gameJob: Job? = null
    private var pendingExport: PendingExport? = null

    private val base: String get() = "api/ServerStats/$guildId"

    init {
        load()
    }

    /** Loads guild lists and settings, then the overview, rankings, and any open lookups. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val channels = async { loadChannels() }
            val roles = async { list("api/ClientOperations/roles/$guildId", GuildRole.serializer()) }
            val members = async { list("api/ClientOperations/members/$guildId", GuildMember.serializer()) }
            val settings = async { api.send(Endpoint("$base/settings"), ServerStatsSettings.serializer()) }
            val exclusions = async { api.send(Endpoint("$base/exclusions"), StatsExclusions.serializer()) }
            val filters = async { api.send(Endpoint("$base/activity-filters"), ActivityFilters.serializer()) }

            val loadedSettings = settings.await()
            val firstLoad = !hasLoadedOnce
            hasLoadedOnce = true
            _state.update { current ->
                current.copy(
                    channels = channels.await(),
                    roles = roles.await().sortedBy { it.name.lowercase() },
                    members = members.await().sortedBy { it.displayName.ifEmpty { it.username }.lowercase() },
                    settings = loadedSettings,
                    exclusions = exclusions.await(),
                    activityFilters = filters.await(),
                    lookbackDraft = loadedSettings.defaultLookbackDays.toString(),
                    cooldownDraft = loadedSettings.messageCooldownSeconds.toString(),
                    lookback = if (firstLoad) loadedSettings.defaultLookbackDays else current.lookback,
                    rankKind = if (!loadedSettings.trackActivities && current.rankKind == StatKind.ACTIVITY) {
                        StatKind.MESSAGES
                    } else {
                        current.rankKind
                    },
                )
            }
        }
        refreshWindowed()
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Changes the shared lookback window and reloads everything that depends on it. */
    fun setLookback(days: Int) {
        if (_state.value.lookback == days) return
        _state.update { it.copy(lookback = days) }
        refreshWindowed()
    }

    /** Changes the ranking kind and reloads the rankings. */
    fun setRankKind(kind: StatKind) {
        _state.update { it.copy(rankKind = kind) }
        loadTop()
    }

    private fun refreshWindowed() {
        loadOverview()
        loadTop()
        val current = _state.value
        if (current.lookupUserId != null) loadUser()
        if (current.lookupChannelId != null) loadChannel()
        if (current.gameQuery.isNotBlank() && current.gameDetail != null) loadGame()
    }

    /** Loads overview tiles, highlights, and every chart series. */
    fun loadOverview() {
        overviewJob?.cancel()
        overviewJob = viewModelScope.launch {
            val snapshot = _state.value
            val days = snapshot.lookback
            val chartDays = snapshot.chartDays
            _state.update { it.copy(overviewLoading = true, overviewError = null) }
            try {
                coroutineScope {
                    val overview = async {
                        api.send(Endpoint("$base/overview?days=$days"), ServerOverview.serializer())
                    }
                    val games = async {
                        if (snapshot.tracksActivities) {
                            runCatching {
                                api.send(
                                    Endpoint("$base/top/activities?limit=5&days=$days"),
                                    ListSerializer(ActivityRow.serializer()),
                                )
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                    val messages = async {
                        api.send(
                            Endpoint("$base/series/${StatChartKind.MESSAGES}?days=$chartDays"),
                            ListSerializer(SeriesPoint.serializer()),
                        )
                    }
                    val voice = async {
                        api.send(
                            Endpoint("$base/series/${StatChartKind.VOICE}?days=$chartDays"),
                            ListSerializer(SeriesPoint.serializer()),
                        )
                    }
                    val snapshots = async {
                        api.send(
                            Endpoint("$base/series/${StatChartKind.MEMBERS}?days=$chartDays"),
                            ListSerializer(GuildSnapshot.serializer()),
                        )
                    }
                    val joinLeave = async {
                        api.send(
                            Endpoint("$base/series/${StatChartKind.GROWTH}?days=$chartDays"),
                            JoinLeaveSeries.serializer(),
                        )
                    }
                    val result = overview.await()
                    val gameRows = games.await()
                    val messageRows = messages.await()
                    val voiceRows = voice.await()
                    val snapshotRows = snapshots.await()
                    val joinLeaveRows = joinLeave.await()
                    _state.update {
                        it.copy(
                            overview = result,
                            topGames = gameRows,
                            messageSeries = messageRows,
                            voiceSeries = voiceRows,
                            snapshots = snapshotRows,
                            joinLeave = joinLeaveRows,
                            overviewLoading = false,
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(overviewLoading = false, overviewError = t.userFacingMessage) }
            }
        }
    }

    /** Loads the member, channel, or game rankings for the chosen kind. */
    fun loadTop() {
        topJob?.cancel()
        topJob = viewModelScope.launch {
            val snapshot = _state.value
            val kind = snapshot.rankKind
            val days = snapshot.lookback
            _state.update { it.copy(topLoading = true, topError = null) }
            try {
                coroutineScope {
                    val users = async {
                        api.send(
                            Endpoint("$base/top/users?kind=${kind.value}&limit=100&days=$days"),
                            ListSerializer(RankedEntry.serializer()),
                        )
                    }
                    val channels = async {
                        if (kind == StatKind.ACTIVITY) {
                            emptyList()
                        } else {
                            api.send(
                                Endpoint("$base/top/channels?kind=${kind.value}&limit=25&days=$days"),
                                ListSerializer(RankedEntry.serializer()),
                            )
                        }
                    }
                    val games = async {
                        if (kind == StatKind.ACTIVITY) {
                            api.send(
                                Endpoint("$base/top/activities?limit=50&days=$days"),
                                ListSerializer(ActivityRow.serializer()),
                            )
                        } else {
                            emptyList()
                        }
                    }
                    val userRows = users.await()
                    val channelRows = channels.await()
                    val gameRows = games.await()
                    _state.update {
                        it.copy(
                            topUsers = userRows,
                            topChannels = channelRows,
                            topActivities = gameRows,
                            topLoading = false,
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(topLoading = false, topError = t.userFacingMessage) }
            }
        }
    }

    /** Picks a member for the lookup section and loads their activity. */
    fun selectUser(id: Snowflake?) {
        _state.update {
            it.copy(lookupUserId = id, userActivity = null, userGames = emptyList(), userError = null)
        }
        if (id != null) loadUser()
    }

    /** Picks a channel for the lookup section and loads its activity. */
    fun selectChannel(id: Snowflake?) {
        _state.update { it.copy(lookupChannelId = id, channelActivity = null, channelError = null) }
        if (id != null) loadChannel()
    }

    /** Updates the "who plays" search text. */
    fun setGameQuery(value: String) = _state.update { it.copy(gameQuery = value) }

    /** Opens the lookup section on a member, as tapping a ranking row does. */
    fun openUser(id: Snowflake) {
        _state.update { it.copy(section = "lookup") }
        selectUser(id)
    }

    /** Opens the lookup section on a channel, as tapping a ranking row does. */
    fun openChannel(id: Snowflake) {
        _state.update { it.copy(section = "lookup") }
        selectChannel(id)
    }

    /** Opens the lookup section on a game, as tapping a game row does. */
    fun openGame(name: String) {
        _state.update { it.copy(section = "lookup", gameQuery = name) }
        loadGame()
    }

    /** Loads the selected member's activity and, when tracked, their games. */
    fun loadUser() {
        val id = _state.value.lookupUserId ?: return
        userJob?.cancel()
        userJob = viewModelScope.launch {
            val snapshot = _state.value
            val days = snapshot.lookback
            _state.update { it.copy(userLoading = true, userError = null) }
            try {
                coroutineScope {
                    val activity = async {
                        api.send(Endpoint("$base/user/$id?days=$days"), UserActivity.serializer())
                    }
                    val games = async {
                        if (snapshot.tracksActivities) {
                            runCatching {
                                api.send(
                                    Endpoint("$base/top/activities?limit=10&days=$days&userId=$id"),
                                    ListSerializer(ActivityRow.serializer()),
                                )
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                    }
                    val result = activity.await()
                    val gameRows = games.await()
                    _state.update { it.copy(userActivity = result, userGames = gameRows, userLoading = false) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(userLoading = false, userError = t.userFacingMessage) }
            }
        }
    }

    /** Loads the selected channel's activity. */
    fun loadChannel() {
        val id = _state.value.lookupChannelId ?: return
        channelJob?.cancel()
        channelJob = viewModelScope.launch {
            val days = _state.value.lookback
            _state.update { it.copy(channelLoading = true, channelError = null) }
            try {
                val result = api.send(Endpoint("$base/channel/$id?days=$days"), ChannelActivity.serializer())
                _state.update { it.copy(channelActivity = result, channelLoading = false) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(channelLoading = false, channelError = t.userFacingMessage) }
            }
        }
    }

    /** Loads who plays the game in the search field. */
    fun loadGame() {
        val name = _state.value.gameQuery.trim()
        if (name.isEmpty()) return
        gameJob?.cancel()
        gameJob = viewModelScope.launch {
            val days = _state.value.lookback
            _state.update { it.copy(gameLoading = true, gameError = null) }
            try {
                val result = api.send(
                    Endpoint("$base/activity?name=${name.urlEncoded()}&limit=25&days=$days"),
                    ActivityDetail.serializer(),
                )
                _state.update { it.copy(gameDetail = result, gameLoading = false) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(gameLoading = false, gameError = t.userFacingMessage) }
            }
        }
    }

    /**
     * Records the CSV file for the current ranking and returns its suggested
     * name, so the screen can ask where to save it. Kept here rather than in
     * the composable so it survives a configuration change while the system
     * file picker is open.
     */
    fun prepareExport(): String {
        val current = _state.value
        val slug = when (current.rankKind) {
            StatKind.MESSAGES -> "messages"
            StatKind.VOICE -> "voice"
            StatKind.ACTIVITY -> "games"
        }
        val pending = PendingExport(
            fileName = "activity-$slug-${current.lookback}d.csv",
            kind = current.rankKind,
            lookback = current.lookback,
        )
        pendingExport = pending
        return pending.fileName
    }

    /** Forgets a prepared export after the user cancels the file picker. */
    fun cancelExport() {
        pendingExport = null
    }

    /**
     * Downloads the prepared ranking CSV from `GET ServerStats/{g}/export` and
     * hands the bytes to [write], which persists them to the file the user picked.
     */
    fun export(write: suspend (ByteArray) -> Unit) = viewModelScope.launch {
        val pending = pendingExport ?: return@launch
        pendingExport = null
        _state.update { it.copy(isExporting = true) }
        try {
            val csv = downloadText("$base/export?kind=${pending.kind.value}&days=${pending.lookback}")
            withContext(Dispatchers.IO) { write(csv.toByteArray(Charsets.UTF_8)) }
            postSuccess("Saved ${pending.fileName}.")
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            postError("Failed to export the ranking.")
        } finally {
            _state.update { it.copy(isExporting = false) }
        }
    }

    /** Turns voice tracking on or off. */
    fun setTrackVoice(value: Boolean) =
        updateSettings({ it.copy(trackVoice = value) }) { put("trackVoice", JsonPrimitive(value)) }

    /** Turns hourly snapshots on or off. */
    fun setTrackSnapshots(value: Boolean) =
        updateSettings({ it.copy(trackSnapshots = value) }) { put("trackSnapshots", JsonPrimitive(value)) }

    /** Includes or excludes bots from stats. */
    fun setCountBots(value: Boolean) =
        updateSettings({ it.copy(countBots = value) }) { put("countBots", JsonPrimitive(value)) }

    /** Turns game and app tracking on or off. */
    fun setTrackActivities(value: Boolean) = updateSettings(
        optimistic = { it.copy(trackActivities = value) },
        onSaved = {
            if (!value && _state.value.rankKind == StatKind.ACTIVITY) {
                _state.update { it.copy(rankKind = StatKind.MESSAGES) }
                loadTop()
            }
            loadOverview()
        },
    ) { put("trackActivities", JsonPrimitive(value)) }

    /** Requires Discord attested activities when on. */
    fun setVerifyActivities(value: Boolean) =
        updateSettings({ it.copy(verifyActivities = value) }) { put("verifyActivities", JsonPrimitive(value)) }

    /** Sets whether one voice state is left out of voice time. */
    fun setVoiceStateIgnored(flag: VoiceStateFlag, ignored: Boolean) {
        val current = _state.value.settings?.voiceStates ?: 0
        val mask = if (ignored) current or flag.bit else current and flag.bit.inv()
        updateSettings({ it.copy(voiceStates = mask) }) { put("voiceStates", JsonPrimitive(mask)) }
    }

    /** Sets the activity filter mode. */
    fun setActivityFilterMode(mode: ActivityFilterMode) = updateSettings(
        optimistic = { it.copy(activityFilterMode = mode.value) },
        onSaved = { _state.update { it.copy(activityFilters = it.activityFilters.copy(mode = mode.value)) } },
    ) { put("activityFilterMode", JsonPrimitive(mode.value)) }

    /** Edits the default window draft. */
    fun setLookbackDraft(value: String) =
        _state.update { it.copy(lookbackDraft = value.filter(Char::isDigit).take(3)) }

    /** Edits the message cooldown draft. */
    fun setCooldownDraft(value: String) =
        _state.update { it.copy(cooldownDraft = value.filter(Char::isDigit).take(3)) }

    /** Saves the default window, 1 to 90 days. */
    fun saveLookbackDefault() {
        val days = _state.value.lookbackDraft.toIntOrNull()
        if (days == null || days !in 1..90) {
            postError("The default window must be between 1 and 90 days.")
            return
        }
        updateSettings({ it.copy(defaultLookbackDays = days) }) { put("defaultLookbackDays", JsonPrimitive(days)) }
    }

    /** Saves the message cooldown, 0 to 300 seconds. */
    fun saveCooldown() {
        val seconds = _state.value.cooldownDraft.toIntOrNull()
        if (seconds == null || seconds !in 0..300) {
            postError("The message cooldown must be between 0 and 300 seconds.")
            return
        }
        updateSettings({ it.copy(messageCooldownSeconds = seconds) }) {
            put("messageCooldownSeconds", JsonPrimitive(seconds))
        }
    }

    /** Adds a channel, role, or member exclusion. */
    fun addExclusion(kind: StatsExclusionKind, targetId: Snowflake?) {
        if (targetId.isNullOrEmpty()) return
        launchAction("Failed to add the exclusion.") {
            api.sendIgnoringBody(Endpoint("$base/exclusions/${kind.value}/$targetId", HttpMethod.POST))
            reloadExclusions()
        }
    }

    /** Removes a channel, role, or member exclusion. */
    fun removeExclusion(kind: StatsExclusionKind, targetId: Snowflake) =
        launchAction("Failed to remove the exclusion.") {
            api.sendIgnoringBody(Endpoint("$base/exclusions/${kind.value}/$targetId", HttpMethod.DELETE))
            reloadExclusions()
        }

    /** Edits the activity filter name draft. */
    fun setFilterDraft(value: String) = _state.update { it.copy(filterDraft = value.take(128)) }

    /** Adds the drafted game name to the activity filter list. */
    fun addFilterName() {
        val name = _state.value.filterDraft.trim()
        if (name.isEmpty()) return
        if (_state.value.activityFilters.names.any { it.equals(name, ignoreCase = true) }) {
            _state.update { it.copy(filterDraft = "") }
            return
        }
        toggleFilterName(name, clearDraft = true)
    }

    /** Removes a game name from the activity filter list. */
    fun removeFilterName(name: String) = toggleFilterName(name, clearDraft = false)

    private fun toggleFilterName(name: String, clearDraft: Boolean) =
        launchAction("Failed to update the activity filter.") {
            api.sendIgnoringBody(
                Endpoint(
                    "$base/activity-filters",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(String.serializer(), name),
                )
            )
            val filters = api.send(Endpoint("$base/activity-filters"), ActivityFilters.serializer())
            _state.update {
                it.copy(activityFilters = filters, filterDraft = if (clearDraft) "" else it.filterDraft)
            }
        }

    private suspend fun reloadExclusions() {
        val exclusions = api.send(Endpoint("$base/exclusions"), StatsExclusions.serializer())
        _state.update { it.copy(exclusions = exclusions) }
    }

    /**
     * Applies [optimistic] immediately, sends the partial update built by
     * [body], then replaces the settings with the server's copy. A failure
     * restores the previous settings and surfaces an error.
     */
    private fun updateSettings(
        optimistic: (ServerStatsSettings) -> ServerStatsSettings,
        onSaved: (() -> Unit)? = null,
        body: JsonObjectBuilder.() -> Unit,
    ) {
        val previous = _state.value.settings ?: return
        _state.update { it.copy(settings = optimistic(previous)) }
        viewModelScope.launch {
            try {
                val payload = MewdekoJson.encodeToString(JsonObject.serializer(), buildJsonObject(body))
                val updated = api.send(
                    Endpoint("$base/settings", HttpMethod.PUT, payload),
                    ServerStatsSettings.serializer(),
                )
                _state.update {
                    it.copy(
                        settings = updated,
                        lookbackDraft = updated.defaultLookbackDays.toString(),
                        cooldownDraft = updated.messageCooldownSeconds.toString(),
                    )
                }
                onSaved?.invoke()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.update { it.copy(settings = previous) }
                postError("Failed to save the setting.")
            }
        }
    }

    /**
     * Loads the guild's text, voice, stage, and announcement channels for the channel picker
     * and the ignored channels list, sorted by category then position. Categories and threads
     * are never included.
     *
     * Falls back to the older, untyped text and voice channel endpoints (with categories
     * stripped out by id) when the typed endpoint is not yet available on the bot.
     */
    private suspend fun loadChannels(): List<GuildChannelLite> {
        val typed = list("api/ClientOperations/guildchannels/$guildId", GuildChannelLite.serializer())
            .filter { it.id.isNotEmpty() && it.type in PickableChannelTypes }
        if (typed.isNotEmpty()) {
            return typed
                .distinctBy { it.id }
                .sortedWith(compareBy({ it.categoryName.orEmpty().lowercase() }, { it.position }))
        }

        return coroutineScope {
            val textChannels = async {
                list("api/ClientOperations/textchannels/$guildId", TextChannelLite.serializer())
            }
            val voiceChannels = async {
                list("api/ClientOperations/channels/$guildId/1", TextChannelLite.serializer())
            }
            val categories = async {
                list("api/ClientOperations/categories/$guildId", TextChannelLite.serializer())
            }
            val categoryIds = categories.await().map { it.id }.toSet()
            (textChannels.await() + voiceChannels.await())
                .filter { it.id.isNotEmpty() && it.id !in categoryIds }
                .distinctBy { it.id }
                .map { GuildChannelLite(id = it.id, name = it.name, type = "text") }
                .sortedBy { it.name.lowercase() }
        }
    }

    private suspend fun <T> list(
        path: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> = runCatching {
        api.send(Endpoint(path), ListSerializer(serializer))
    }.getOrDefault(emptyList())

    /**
     * Fetches a non-JSON response body as text. [ApiClient] only exposes
     * parsed JSON, so this mirrors its auth, instance header, and single
     * refresh-and-retry on 401 for the CSV export.
     */
    private suspend fun downloadText(path: String, allowRetry: Boolean = true): String {
        val baseUrl = api.currentBaseUrl() ?: throw ApiError.NotConfigured()
        val instance = api.currentInstance()
        val token = auth.currentAccessToken()
        val response: HttpResponse = try {
            http.request("$baseUrl/${path.trimStart('/')}") {
                method = KtorMethod.Get
                header("Authorization", "Bearer $token")
                instance?.let { header("X-Mobile-Instance", it) }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw ApiError.Transport(t)
        }
        val status = response.status.value
        if (status == 401 && allowRetry) {
            runCatching { auth.refresh() }
            return downloadText(path, allowRetry = false)
        }
        if (status == 401) throw ApiError.Unauthorized()
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw ApiError.Http(status, text)
        return text
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
}
