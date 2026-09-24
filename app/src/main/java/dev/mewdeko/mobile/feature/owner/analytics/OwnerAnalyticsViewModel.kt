package dev.mewdeko.mobile.feature.owner.analytics

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.feature.owner.OwnerFeatureViewModel
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import javax.inject.Inject

private const val TAG = "OwnerAnalytics"

/** How long the metric registry stays cached before it is read again. */
private const val RegistryTtlMillis = 5 * 60_000L

/**
 * Loads fleet telemetry for the owner analytics tabs.
 *
 * Holds the global filter bar, the refresh tick, and the shared metric
 * registry. Each widget loads its own data through the typed readers below,
 * keyed on the filters and the tick, the same way every dashboard component
 * does; the alerts tab's rules live here so row actions can patch them.
 * Every path is fleet level and goes out against the selected bot instance.
 */
@HiltViewModel
class OwnerAnalyticsViewModel @Inject constructor(
    api: ApiClient,
    session: SessionHolder,
) : OwnerFeatureViewModel(api, session) {

    private val _state = MutableStateFlow(OwnerAnalyticsState())

    /** Observable screen state. */
    val state: StateFlow<OwnerAnalyticsState> = _state.asStateFlow()

    private val _alerts = MutableStateFlow(AlertsState())

    /** The alerts tab's rules, firing list, and busy flags. */
    val alerts: StateFlow<AlertsState> = _alerts.asStateFlow()

    private val registryLock = Mutex()
    private var registryCache: List<MetricDescriptor>? = null
    private var registryAt = 0L
    private var alertsJob: Job? = null

    init {
        load()
    }

    /**
     * Loads what the filter bar offers: the metric registry, the bot
     * instances and the shard ids. A refresh also bumps the tick so every
     * visible widget reloads. Failures here are logged and leave the lists
     * empty, as the dashboard does.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val registry = async { registry(force = refreshing) }
            val health = async { runCatching { health() }.getOrNull() }
            val metrics = registry.await()
            val instances = health.await()?.instances.orEmpty()
            _state.update { current ->
                current.copy(
                    registry = metrics,
                    bots = instances.map { AnalyticsBotOption(it.botId, it.botName.ifEmpty { it.botId }) },
                    shards = labelValuesOf(metrics, "shard.latency", "shard"),
                    loadedAt = System.currentTimeMillis(),
                )
            }
        }
        if (refreshing) refreshNow()
    }

    /** Switches the visible tab. */
    fun setSection(section: OwnerAnalyticsSection) = _state.update { it.copy(section = section) }

    /** Applies a filter bar change; every widget reloads on the new filters. */
    fun updateFilters(transform: (AnalyticsFilters) -> AnalyticsFilters) =
        _state.update { it.copy(filters = transform(it.filters)) }

    /** Bumps the refresh tick now, as the dashboard's refresh button and 30 second timer do. */
    fun refreshNow() = _state.update { it.copy(tick = it.tick + 1, updatedAt = System.currentTimeMillis()) }

    /** The dashboard link for the current tab and filters, or `null` when no dashboard is configured. */
    suspend fun shareLink(): String? {
        val base = api.currentBaseUrl() ?: return null
        val current = _state.value
        return "${base.trimEnd('/')}/owner/analytics?${current.filters.toQueryString(current.section.id)}"
    }

    /** The metric registry, cached for five minutes and shared by every widget. */
    suspend fun registry(force: Boolean = false): List<MetricDescriptor> = registryLock.withLock {
        val cached = registryCache
        val fresh = System.currentTimeMillis() - registryAt < RegistryTtlMillis
        if (cached != null && fresh && !force) return@withLock cached
        val loaded = try {
            get("metrics", emptyList(), ListSerializer(MetricDescriptor.serializer())).orEmpty()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "metric registry failed: ${t.message}")
            return@withLock cached.orEmpty()
        }
        registryCache = loaded
        registryAt = System.currentTimeMillis()
        loaded
    }

    /** Label keys a metric carries, `bot` excluded since the filter bar owns it. */
    suspend fun metricLabels(metric: String): List<String> =
        registry().firstOrNull { it.metric == metric }?.labels?.keys?.filter { it != "bot" }.orEmpty()

    /** Sample values the registry has seen for one label on one metric, sorted numerically first. */
    suspend fun labelValues(metric: String, label: String): List<String> = labelValuesOf(registry(), metric, label)

    private fun labelValuesOf(registry: List<MetricDescriptor>, metric: String, label: String): List<String> =
        registry.firstOrNull { it.metric == metric }?.labels?.get(label).orEmpty().sortedWith(LabelValueOrder)

    /**
     * Reads `api/Analytics/<route>` and decodes it with [strategy]. A `null`
     * body, which the proxy sends for an empty or forbidden reply, reads as
     * `null` rather than failing. Keys are decoded verbatim since the bot
     * sends camelCase and label keys must not be renamed.
     */
    suspend fun <T> get(
        route: String,
        params: List<Pair<String, String>>,
        strategy: DeserializationStrategy<T>,
    ): T? {
        val raw = api.sendRaw(Endpoint(analyticsPath(route, params)))
        if (raw is JsonNull) return null
        return withContext(Dispatchers.Default) {
            try {
                MewdekoJson.decodeFromJsonElement(strategy, raw)
            } catch (t: Throwable) {
                Log.e(TAG, "decode failure on $route: ${t.message}")
                throw ApiError.Decoding(t)
            }
        }
    }

    private suspend fun <T> send(
        route: String,
        method: HttpMethod,
        params: List<Pair<String, String>> = emptyList(),
        body: String? = null,
        strategy: DeserializationStrategy<T>,
    ): T? {
        val raw = api.sendRaw(Endpoint(analyticsPath(route, params), method, body))
        if (raw is JsonNull) return null
        return withContext(Dispatchers.Default) { MewdekoJson.decodeFromJsonElement(strategy, raw) }
    }

    /** A time series. `max` is omitted when null so the server default of ten applies. */
    suspend fun series(
        metric: String,
        agg: String,
        groupBy: String?,
        max: Int?,
        params: List<Pair<String, String>>,
    ): SeriesResult? {
        val query = buildList {
            add("metric" to metric)
            add("agg" to agg)
            if (!groupBy.isNullOrEmpty()) add("groupBy" to groupBy)
            if (max != null) add("max" to max.toString())
            addAll(params)
        }
        return get("series", query, SeriesResult.serializer())
    }

    /** One number over the range. */
    suspend fun aggregate(metric: String, agg: String, params: List<Pair<String, String>>): AggregateResult? =
        get("aggregate", listOf("metric" to metric, "agg" to agg) + params, AggregateResult.serializer())

    /** Top values of [label] for [metric]. */
    suspend fun breakdown(
        metric: String,
        label: String,
        agg: String,
        limit: Int,
        params: List<Pair<String, String>>,
    ): List<BreakdownRow> = get(
        "breakdown",
        listOf("metric" to metric, "label" to label, "agg" to agg, "limit" to limit.toString()) + params,
        ListSerializer(BreakdownRow.serializer()),
    ).orEmpty()

    /** Commands ranked by invocations. */
    suspend fun topCommands(params: List<Pair<String, String>>, limit: Int): List<TopCommandRow> =
        get("commands/top", params + ("limit" to limit.toString()), ListSerializer(TopCommandRow.serializer())).orEmpty()

    /** Commands ranked by failures. */
    suspend fun failingCommands(params: List<Pair<String, String>>, limit: Int): List<FailingCommandRow> =
        get("commands/failing", params + ("limit" to limit.toString()), ListSerializer(FailingCommandRow.serializer()))
            .orEmpty()

    /** One page of the raw invocation log. */
    suspend fun invocations(params: List<Pair<String, String>>, page: Int, pageSize: Int): AnalyticsPage<InvocationRow>? =
        get(
            "commands/invocations",
            params + listOf("page" to page.toString(), "pageSize" to pageSize.toString()),
            AnalyticsPage.serializer(InvocationRow.serializer()),
        )

    /** Stored exceptions of one failing command and error class. */
    suspend fun commandErrorSamples(
        time: List<Pair<String, String>>,
        command: String,
        error: String,
        limit: Int,
    ): List<CommandErrorSample> = get(
        "commands/error-samples",
        time + listOf("command" to command, "error" to error, "limit" to limit.toString()),
        ListSerializer(CommandErrorSample.serializer()),
    ).orEmpty()

    /** Invocations by UTC hour and server size. */
    suspend fun commandHeatmap(params: List<Pair<String, String>>): UsageHeatmap? =
        get("commands/heatmap", params, UsageHeatmap.serializer())

    /** Gateway event counts by type. */
    suspend fun eventCounts(params: List<Pair<String, String>>): List<EventCountRow> =
        get("events/counts", params, ListSerializer(EventCountRow.serializer())).orEmpty()

    /** Guilds ranked by events. */
    suspend fun topGuilds(params: List<Pair<String, String>>, eventType: String, limit: Int): List<TopGuildRow> {
        val query = params + listOfNotNull(
            eventType.takeIf { it.isNotEmpty() }?.let { "eventType" to it },
            "limit" to limit.toString(),
        )
        return get("guilds/top", query, ListSerializer(TopGuildRow.serializer())).orEmpty()
    }

    /** Hours where a guild ran far above its usual level. */
    suspend fun guildAnomalies(params: List<Pair<String, String>>, minCount: Int, limit: Int): List<GuildAnomalyRow> =
        get(
            "guilds/anomalies",
            params + listOf("minCount" to minCount.toString(), "limit" to limit.toString()),
            ListSerializer(GuildAnomalyRow.serializer()),
        ).orEmpty()

    /** One guild's activity by type and hour. */
    suspend fun guildTimeline(guildId: String, params: List<Pair<String, String>>): GuildTimeline? =
        get("guilds/${enc(guildId)}/timeline", params, GuildTimeline.serializer())

    /** One page of a guild's logged events. */
    suspend fun guildEvents(
        guildId: String,
        time: List<Pair<String, String>>,
        eventType: String,
        page: Int,
        pageSize: Int,
    ): AnalyticsPage<GuildEventRow>? {
        val query = time + listOfNotNull(
            eventType.takeIf { it.isNotEmpty() }?.let { "eventType" to it },
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
        )
        return get("guilds/${enc(guildId)}/events", query, AnalyticsPage.serializer(GuildEventRow.serializer()))
    }

    /** Everything known about one guild. */
    suspend fun guildCard(guildId: String, time: List<Pair<String, String>>): GuildCard? =
        get("guilds/${enc(guildId)}/card", time, GuildCard.serializer())

    /** Feature adoption across the fleet. */
    suspend fun featureAdoption(params: List<Pair<String, String>>): List<FeatureAdoptionRow> =
        get("features/adoption", params, ListSerializer(FeatureAdoptionRow.serializer())).orEmpty()

    /** Features per guild. */
    suspend fun featureDepth(params: List<Pair<String, String>>): FeatureDepth? =
        get("features/depth", params, FeatureDepth.serializer())

    /** The latest settings census for a feature. */
    suspend fun featureSettings(feature: String): List<SettingUsageRow> =
        get("features/settings", listOf("feature" to feature), ListSerializer(SettingUsageRow.serializer())).orEmpty()

    /** Daily census values of a feature metric. */
    suspend fun featureCensus(metric: String, days: Int): List<DayValue> =
        get("features/census", listOf("metric" to metric, "days" to days.toString()), ListSerializer(DayValue.serializer()))
            .orEmpty()

    /** Nightly per bot snapshots. */
    suspend fun serverSnapshots(days: Int, bot: List<Pair<String, String>>): List<SnapshotRow> =
        get("servers/snapshots", listOf("days" to days.toString()) + bot, ListSerializer(SnapshotRow.serializer()))
            .orEmpty()

    /** Every guild of the serving instance. */
    suspend fun serverOverview(params: List<Pair<String, String>>): List<GuildOverviewRow> =
        get("servers/overview", params, ListSerializer(GuildOverviewRow.serializer())).orEmpty()

    /** Joins, leaves and bounces over the range. */
    suspend fun growthChurn(params: List<Pair<String, String>>): ChurnSummary? =
        get("growth/churn", params, ChurnSummary.serializer())

    /** Retention by join day. */
    suspend fun growthRetention(days: Int, params: List<Pair<String, String>>): List<RetentionPoint> =
        get("growth/retention", listOf("days" to days.toString()) + params, ListSerializer(RetentionPoint.serializer()))
            .orEmpty()

    /** Guilds that bounced. */
    suspend fun growthBounced(params: List<Pair<String, String>>, limit: Int): List<BouncedGuildRow> =
        get("growth/bounced", params + ("limit" to limit.toString()), ListSerializer(BouncedGuildRow.serializer()))
            .orEmpty()

    /** Guilds that went quiet. */
    suspend fun growthSilent(bot: List<Pair<String, String>>, limit: Int): List<SilentGuildRow> =
        get("growth/silent", bot + ("limit" to limit.toString()), ListSerializer(SilentGuildRow.serializer())).orEmpty()

    /** Exception groups. */
    suspend fun errors(params: List<Pair<String, String>>, limit: Int): List<ErrorGroupRow> =
        get("errors", params + ("limit" to limit.toString()), ListSerializer(ErrorGroupRow.serializer())).orEmpty()

    /** Hourly occurrences behind one exception group. */
    suspend fun errorSamples(row: ErrorGroupRow, params: List<Pair<String, String>>, limit: Int): List<ErrorOccurrenceRow> {
        val query = params + listOfNotNull(
            "type" to row.type,
            row.module?.let { "module" to it },
            row.hash.takeIf { it.isNotEmpty() }?.let { "hash" to it },
            "limit" to limit.toString(),
        )
        return get("errors/samples", query, ListSerializer(ErrorOccurrenceRow.serializer())).orEmpty()
    }

    /** AI use by model. */
    suspend fun aiSummary(params: List<Pair<String, String>>): AiSummary? =
        get("ai/summary", params, AiSummary.serializer())

    /** Guilds ranked by AI chat use. */
    suspend fun aiGuilds(params: List<Pair<String, String>>, limit: Int): List<AiGuildRow> =
        get("ai/guilds", params + ("limit" to limit.toString()), ListSerializer(AiGuildRow.serializer())).orEmpty()

    /** Website routes by views. */
    suspend fun websiteRoutes(time: List<Pair<String, String>>, limit: Int): List<RouteRow> =
        get("website/routes", time + ("limit" to limit.toString()), ListSerializer(RouteRow.serializer())).orEmpty()

    /** Website 5xx responses by route. */
    suspend fun websiteErrors(time: List<Pair<String, String>>, limit: Int): List<RouteErrorRow> =
        get("website/errors", time + ("limit" to limit.toString()), ListSerializer(RouteErrorRow.serializer())).orEmpty()

    /** The login funnel. */
    suspend fun websiteFunnel(time: List<Pair<String, String>>): WebsiteFunnel? =
        get("website/funnel", time, WebsiteFunnel.serializer())

    /** The pipeline's own state and the instance list. */
    suspend fun health(): PipelineHealth? = get("health", emptyList(), PipelineHealth.serializer())

    /** Threshold lines for charts of [metric]. */
    suspend fun alertBands(metric: String): List<AlertBand> =
        get("alerts/bands", listOf("metric" to metric), ListSerializer(AlertBand.serializer())).orEmpty()

    /** What is firing now. */
    suspend fun firingAlerts(): List<FiringAlert> =
        get("alerts/firing", emptyList(), ListSerializer(FiringAlert.serializer())).orEmpty()

    /** One page of alert transitions, optionally for one rule. */
    suspend fun alertEvents(page: Int, pageSize: Int, ruleId: Int?): AnalyticsPage<AlertEvent>? {
        val query = listOfNotNull(
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            ruleId?.let { "ruleId" to it.toString() },
        )
        return get("alerts/events", query, AnalyticsPage.serializer(AlertEvent.serializer()))
    }

    /**
     * Loads the alert rules and the firing list. Failures are logged and
     * render as empty, as on the dashboard.
     */
    fun loadAlerts() {
        alertsJob?.cancel()
        alertsJob = viewModelScope.launch {
            _alerts.update { it.copy(rulesLoading = true) }
            coroutineScope {
                val rules = async {
                    runLogged("alert rules") {
                        get("alerts/rules", emptyList(), ListSerializer(AlertRule.serializer())).orEmpty()
                    }
                }
                val firing = async { runLogged("firing alerts") { firingAlerts() } }
                val loadedRules = rules.await()
                val loadedFiring = firing.await()
                _alerts.update {
                    it.copy(
                        rules = loadedRules ?: emptyList(),
                        firing = loadedFiring ?: emptyList(),
                        rulesLoading = false,
                    )
                }
            }
        }
    }

    /**
     * Flips a rule on or off by sending the whole rule back. The row shows
     * the new state, so success posts nothing.
     */
    fun toggleRule(rule: AlertRule) = ruleAction(rule, "Rule update failed") {
        val saved = send(
            "alerts/rules/${rule.id}",
            HttpMethod.PUT,
            body = encodeRule(rule.toInput().copy(enabled = !rule.enabled)),
            strategy = AlertRule.serializer(),
        )
        if (saved != null) replaceRule(saved)
    }

    /** Sends a test notification; its result lands in Discord, so both outcomes are reported. */
    fun testRule(rule: AlertRule) = ruleAction(rule, "Test failed") {
        val result = send("alerts/rules/${rule.id}/test", HttpMethod.POST, strategy = AlertSendResult.serializer())
        if (result?.success == true) {
            postSuccess("Test sent for ${rule.name}")
        } else {
            postError("Discord rejected the test for ${rule.name}")
        }
    }

    /** Mutes a rule for an hour, or unmutes it when it is muted. The row shows the pill. */
    fun muteRule(rule: AlertRule) = ruleAction(rule, "Mute failed") {
        val minutes = if (rule.isMuted()) 0 else 60
        val result = send(
            "alerts/rules/${rule.id}/mute",
            HttpMethod.POST,
            params = listOf("minutes" to minutes.toString()),
            strategy = AlertMuteResult.serializer(),
        )
        replaceRule(rule.copy(mutedUntil = result?.mutedUntil))
    }

    /** Deletes a rule after the screen's confirmation. The row disappears, so success posts nothing. */
    fun deleteRule(rule: AlertRule) = ruleAction(rule, "Delete failed") {
        api.sendRaw(Endpoint(analyticsPath("alerts/rules/${rule.id}", emptyList()), HttpMethod.DELETE))
        _alerts.update { current -> current.copy(rules = current.rules.filterNot { it.id == rule.id }) }
    }

    /** Sends the alert digest now; it lands in Discord, so both outcomes are reported. */
    fun sendDigest() {
        if (_alerts.value.digestBusy) return
        viewModelScope.launch {
            _alerts.update { it.copy(digestBusy = true) }
            try {
                val result = send("alerts/digest", HttpMethod.POST, strategy = AlertSendResult.serializer())
                if (result?.success == true) postSuccess("Digest sent") else postError("Digest not sent")
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "digest failed: ${t.message}")
                postError(analyticsErrorMessage(t, "Digest failed"))
            } finally {
                _alerts.update { it.copy(digestBusy = false) }
            }
        }
    }

    /**
     * Creates or updates a rule. On success the row is replaced or prepended
     * and [onDone] gets `null`; on failure [onDone] gets the server's message
     * so the editor can show it inline.
     */
    fun saveRule(existing: AlertRule?, input: AlertRuleInput, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val saved = if (existing != null) {
                    send("alerts/rules/${existing.id}", HttpMethod.PUT, body = encodeRule(input), strategy = AlertRule.serializer())
                } else {
                    send("alerts/rules", HttpMethod.POST, body = encodeRule(input), strategy = AlertRule.serializer())
                }
                if (saved != null) replaceRule(saved)
                onDone(null)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "rule save failed: ${t.message}")
                onDone(analyticsErrorMessage(t, "Save failed"))
            }
        }
    }

    private fun replaceRule(saved: AlertRule) = _alerts.update { current ->
        val rules = if (current.rules.any { it.id == saved.id }) {
            current.rules.map { if (it.id == saved.id) saved else it }
        } else {
            listOf(saved) + current.rules
        }
        current.copy(rules = rules)
    }

    private fun ruleAction(rule: AlertRule, fallback: String, block: suspend () -> Unit) {
        if (_alerts.value.busy != null) return
        viewModelScope.launch {
            _alerts.update { it.copy(busy = rule.id) }
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.w(TAG, "$fallback: ${t.message}")
                postError(analyticsErrorMessage(t, fallback))
            } finally {
                _alerts.update { it.copy(busy = null) }
            }
        }
    }

    private fun encodeRule(input: AlertRuleInput): String =
        MewdekoJson.encodeToString(AlertRuleInput.serializer(), input)

    private suspend fun <T> runLogged(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Log.w(TAG, "$what failed: ${t.message}")
        null
    }
}

/** Percent-encodes one path segment or query value. */
private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

/** `api/Analytics/<route>?<query>` with empty values dropped. */
private fun analyticsPath(route: String, params: List<Pair<String, String>>): String {
    val query = params
        .filter { it.second.isNotEmpty() }
        .joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }
    return if (query.isEmpty()) "api/Analytics/$route" else "api/Analytics/$route?$query"
}

/**
 * The message to show for a failed analytics call. The proxy rewraps a bot
 * `{error}` body as `{"error":"API error","details":{"error":"<message>"}}`,
 * so the detail wins; plain text errors arrive as `{error}` and problem
 * details as `{error:{message}}`.
 */
fun analyticsErrorMessage(t: Throwable, fallback: String): String {
    if (t is ApiError.Http) {
        val root = runCatching { MewdekoJson.parseToJsonElement(t.body) }.getOrNull() as? JsonObject
        val detail = ((root?.get("details") as? JsonObject)?.get("error") as? JsonPrimitive)?.contentOrNull
        if (!detail.isNullOrBlank()) return detail
        when (val error = root?.get("error")) {
            is JsonPrimitive -> error.contentOrNull?.takeIf { it.isNotBlank() && it != "API error" }?.let { return it }
            is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
            else -> Unit
        }
        return fallback
    }
    return t.userFacingMessage.ifBlank { fallback }
}
