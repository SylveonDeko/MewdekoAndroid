package dev.mewdeko.mobile.feature.owner.analytics

import dev.mewdeko.mobile.core.net.InstantParser
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong
import dev.mewdeko.mobile.core.model.LenientDoubleSerializer as LD
import dev.mewdeko.mobile.core.model.SnowflakeSerializer as SF

/**
 * The analytics tabs, in the dashboard's order; [id] matches the dashboard's
 * `tab` query value.
 */
enum class OwnerAnalyticsSection(val id: String, val label: String) {
    /** Fleet summary. */
    Overview("overview", "Overview"),

    /** Command usage. */
    Commands("commands", "Commands"),

    /** Gateway events. */
    Events("events", "Events"),

    /** Gateway and API latency. */
    Latency("latency", "Latency"),

    /** Server counts, growth and churn. */
    Servers("servers", "Servers"),

    /** Per guild activity and anomalies. */
    Guilds("guilds", "Guilds"),

    /** Feature adoption. */
    Features("features", "Features"),

    /** AI usage. */
    Ai("ai", "AI"),

    /** Music playback. */
    Music("music", "Music"),

    /** Dashboard and website traffic. */
    Website("website", "Website"),

    /** Alert rules and firings. */
    Alerts("alerts", "Alerts"),

    /** The telemetry pipeline's own health. */
    Pipeline("pipeline", "Pipeline"),
    ;

    companion object {
        /** The section for a dashboard `tab` id, falling back to [Overview]. */
        fun fromId(id: String?): OwnerAnalyticsSection = entries.firstOrNull { it.id == id } ?: Overview
    }
}

/** The preset windows of the filter bar and their length in seconds, in chip order. */
val AnalyticsRanges: List<Pair<String, Long>> = listOf(
    "15m" to 900L,
    "1h" to 3_600L,
    "6h" to 21_600L,
    "24h" to 86_400L,
    "7d" to 604_800L,
    "30d" to 2_592_000L,
)

/** The default preset window. */
const val DefaultAnalyticsRange = "24h"

/** Global filter fields honoured by widgets that read both the bot and shard labels. */
val HonoursBotShard: Set<String> = setOf("bot", "shard")

/** Global filter fields honoured by widgets that only read the bot label. */
val HonoursBot: Set<String> = setOf("bot")

/** Widgets that ignore the bot and shard filters, such as the website tab. */
val HonoursNothing: Set<String> = emptySet()

/**
 * The global filter bar state, mirroring the dashboard's `analyticsFilters`
 * store. [from] and [to] are ISO instants and only apply when both are set,
 * in which case [range] is ignored.
 */
data class AnalyticsFilters(
    /** The preset window id, one of [AnalyticsRanges]. */
    val range: String = DefaultAnalyticsRange,
    /** Custom window start as an ISO UTC instant, or empty. */
    val from: String = "",
    /** Custom window end as an ISO UTC instant, or empty. */
    val to: String = "",
    /** The bot id label filter, or empty for every bot. */
    val bot: String = "",
    /** The shard label filter, or empty for every shard. */
    val shard: String = "",
    /** The guild id filter, or empty for every guild. */
    val guild: String = "",
    /** Whether charts overlay the previous window of the same width. */
    val compare: Boolean = false,
) {
    /** Whether a custom window replaces the preset range. */
    val isCustom: Boolean get() = from.isNotEmpty() && to.isNotEmpty()

    /**
     * The window length in seconds: the preset's nominal length, or the
     * custom span with a one minute floor.
     */
    fun rangeSeconds(): Double {
        if (isCustom) {
            val start = utcParse(from)
            val end = utcParse(to)
            if (start != null && end != null) {
                return maxOf(60.0, (end.toEpochMilli() - start.toEpochMilli()) / 1000.0)
            }
        }
        return (AnalyticsRanges.firstOrNull { it.first == range }?.second ?: 86_400L).toDouble()
    }

    /**
     * The time window query pairs, or the same width one period earlier when
     * [shift] is set, exactly as the dashboard computes it.
     */
    fun timeParams(shift: Boolean = false, now: Instant = Instant.now()): List<Pair<String, String>> {
        if (isCustom) {
            val start = utcParse(from)
            val end = utcParse(to)
            if (start != null && end != null) {
                val span = end.toEpochMilli() - start.toEpochMilli()
                val offset = if (shift) span else 0L
                return listOf(
                    "from" to start.minusMillis(offset).toString(),
                    "to" to end.minusMillis(offset).toString(),
                )
            }
        }
        if (shift) {
            val width = (rangeSeconds() * 1000).toLong()
            return listOf(
                "from" to now.minusMillis(width * 2).toString(),
                "to" to now.minusMillis(width).toString(),
            )
        }
        return listOf("range" to range)
    }

    /**
     * The time window plus whichever global filters a widget honours, with
     * [fixed] label filters expanded into `f.<label>` pairs.
     */
    fun queryParams(
        honours: Set<String>,
        fixed: Map<String, String> = emptyMap(),
        shift: Boolean = false,
    ): List<Pair<String, String>> {
        val out = timeParams(shift).toMutableList()
        if ("bot" in honours && bot.isNotEmpty()) out += "bot" to bot
        if ("shard" in honours && shard.isNotEmpty()) out += "shard" to shard
        val labels = fixed.toMutableMap()
        if ("guild" in honours && guild.isNotEmpty()) labels["guild"] = guild
        labels.forEach { (key, value) -> if (value.isNotEmpty()) out += "f.$key" to value }
        return out
    }

    /** The time window plus the bot filter only, for endpoints that ignore shard and labels. */
    fun timeAndBot(): List<Pair<String, String>> = timeParams() + botParam()

    /** The bot filter alone, or nothing when every bot is selected. */
    fun botParam(): List<Pair<String, String>> = if (bot.isEmpty()) emptyList() else listOf("bot" to bot)

    /** The dashboard query string for these filters on [tab], as the web page writes it. */
    fun toQueryString(tab: String): String {
        val pairs = mutableListOf("tab" to tab)
        if (isCustom) {
            pairs += "from" to from
            pairs += "to" to to
        } else if (range != DefaultAnalyticsRange) {
            pairs += "range" to range
        }
        if (bot.isNotEmpty()) pairs += "bot" to bot
        if (shard.isNotEmpty()) pairs += "shard" to shard
        if (guild.isNotEmpty()) pairs += "guild" to guild
        if (compare) pairs += "compare" to "1"
        return pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
    }
}

/** One entry of the filter bar's bot picker. */
data class AnalyticsBotOption(
    /** The bot id, the value of the `bot` filter. */
    val id: String,
    /** The display name, falling back to the id. */
    val name: String,
)

/**
 * Analytics screen state: the visible tab, the global filters, the refresh
 * tick every widget reloads on, and the lists the filter bar offers.
 */
data class OwnerAnalyticsState(
    /** The visible tab. */
    val section: OwnerAnalyticsSection = OwnerAnalyticsSection.Overview,
    /** The global filter bar. */
    val filters: AnalyticsFilters = AnalyticsFilters(),
    /** Bumped every 30 seconds while visible and on refresh; widgets reload when it changes. */
    val tick: Int = 0,
    /** When the last tick fired, in epoch milliseconds. */
    val updatedAt: Long = System.currentTimeMillis(),
    /** Bot instances from the pipeline health's instance list. */
    val bots: List<AnalyticsBotOption> = emptyList(),
    /** Shard ids the registry has seen on `shard.latency`, sorted numerically. */
    val shards: List<String> = emptyList(),
    /** The metric registry, shared by the drill sheet, command selects and the rule editor. */
    val registry: List<MetricDescriptor> = emptyList(),
    /** When the page was last loaded, in epoch milliseconds, or `null` before the first load. */
    val loadedAt: Long? = null,
)

/** The alerts tab state, held by the view model so row actions can patch it. */
data class AlertsState(
    /** Every alert rule, newest first. */
    val rules: List<AlertRule> = emptyList(),
    /** Whether the rules are loading. */
    val rulesLoading: Boolean = true,
    /** What is firing right now. */
    val firing: List<FiringAlert> = emptyList(),
    /** The rule a row action is running for, whose buttons are disabled. */
    val busy: Int? = null,
    /** Whether the digest is being sent. */
    val digestBusy: Boolean = false,
)

/** One metric of the registry: its kind and every label key with sample values. */
@Serializable
data class MetricDescriptor(
    val metric: String = "",
    val kind: String = "",
    val labels: Map<String, List<String>> = emptyMap(),
    val lastSeen: String? = null,
)

/** One bucket of a series; [value] is absent for an empty bucket. */
@Serializable
data class SeriesPoint(
    val bucketUnix: Long = 0,
    @Serializable(LD::class) val value: Double? = null,
)

/** One line of a series result. */
@Serializable
data class SeriesLine(
    val name: String = "",
    val labels: Map<String, String> = emptyMap(),
    val points: List<SeriesPoint> = emptyList(),
)

/** A series query result: the bucket width in minutes and the lines, largest first. */
@Serializable
data class SeriesResult(
    val resolution: Int = 1,
    val series: List<SeriesLine> = emptyList(),
    val truncated: Boolean = false,
)

/** One number over the range; `{}` when there is no data. */
@Serializable
data class AggregateResult(
    @Serializable(LD::class) val value: Double? = null,
)

/** One label value and its aggregate. */
@Serializable
data class BreakdownRow(
    val name: String = "",
    @Serializable(LD::class) val value: Double = 0.0,
)

/** A command ranked by invocations. */
@Serializable
data class TopCommandRow(
    val command: String = "",
    val module: String? = null,
    @Serializable(LD::class) val count: Double = 0.0,
    @Serializable(LD::class) val failures: Double = 0.0,
    @Serializable(LD::class) val failureRate: Double = 0.0,
    @Serializable(LD::class) val avgMs: Double? = null,
    @Serializable(LD::class) val p95Ms: Double? = null,
    @Serializable(LD::class) val guilds: Double = 0.0,
)

/** A command and error class ranked by failures. */
@Serializable
data class FailingCommandRow(
    val command: String = "",
    val module: String? = null,
    val errorClass: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
    val lastSeen: String? = null,
    val lastMessage: String? = null,
)

/** One raw command invocation. No user ids are stored. */
@Serializable
data class InvocationRow(
    val id: Long = 0,
    val at: String? = null,
    val bot: String = "",
    val shard: Int? = null,
    @Serializable(SF::class) val guildId: String? = null,
    val guildSize: Int? = null,
    val kind: String = "",
    val module: String? = null,
    val command: String = "",
    val ok: Boolean = true,
    val errorClass: String? = null,
    val errorMessage: String? = null,
    @Serializable(LD::class) val durationMs: Double = 0.0,
    @Serializable(LD::class) val ackMs: Double? = null,
    val language: String? = null,
)

/** A server paged list. */
@Serializable
data class AnalyticsPage<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 25,
    val total: Long = 0,
)

/** One stored exception of a failing command. */
@Serializable
data class CommandErrorSample(
    val at: String? = null,
    @Serializable(SF::class) val guildId: String? = null,
    val kind: String = "",
    val module: String? = null,
    val message: String? = null,
    @Serializable(LD::class) val durationMs: Double = 0.0,
)

/** One cell of the hour by server size heatmap. */
@Serializable
data class HeatmapCell(
    val hour: Int = 0,
    val size: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
)

/** Command invocations by UTC hour and server size bucket. */
@Serializable
data class UsageHeatmap(
    val sizes: List<String> = emptyList(),
    val cells: List<HeatmapCell> = emptyList(),
)

/** A gateway event type and its count in range. */
@Serializable
data class EventCountRow(
    val type: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
)

/** A type and a count, used by several guild rows. */
@Serializable
data class TypeCount(
    val type: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
)

/** A guild ranked by gateway events. */
@Serializable
data class TopGuildRow(
    @Serializable(SF::class) val guildId: String = "",
    val name: String? = null,
    @Serializable(LD::class) val memberCount: Double? = null,
    @Serializable(LD::class) val events: Double = 0.0,
    val topTypes: List<TypeCount> = emptyList(),
)

/** An hour where a guild ran far above its usual level. */
@Serializable
data class GuildAnomalyRow(
    @Serializable(SF::class) val guildId: String = "",
    val name: String? = null,
    val eventType: String = "",
    val hour: String? = null,
    @Serializable(LD::class) val count: Double = 0.0,
    @Serializable(LD::class) val mean: Double = 0.0,
    @Serializable(LD::class) val stdDev: Double = 0.0,
    @Serializable(LD::class) val z: Double = 0.0,
)

/** One cell of a guild's type by hour activity matrix. */
@Serializable
data class TimelineCell(
    val hour: String = "",
    val type: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
)

/** A guild's activity by event type and hour. */
@Serializable
data class GuildTimeline(
    @Serializable(SF::class) val guildId: String = "",
    val types: List<String> = emptyList(),
    val cells: List<TimelineCell> = emptyList(),
)

/** One logged gateway event of a guild. */
@Serializable
data class GuildEventRow(
    val id: Long = 0,
    val at: String? = null,
    val eventType: String = "",
    val bot: String = "",
    val shard: Int? = null,
)

/** The live shape of a guild on the serving instance. */
@Serializable
data class GuildShape(
    @Serializable(LD::class) val memberCount: Double = 0.0,
    @Serializable(LD::class) val humans: Double = 0.0,
    @Serializable(LD::class) val bots: Double = 0.0,
    @Serializable(LD::class) val online: Double = 0.0,
    @Serializable(LD::class) val boosts: Double = 0.0,
    val boostTier: Int = 0,
    @Serializable(LD::class) val channels: Double = 0.0,
    @Serializable(LD::class) val roles: Double = 0.0,
    @Serializable(SF::class) val ownerId: String = "",
    val createdAt: String? = null,
)

/** A feature's use in one guild. */
@Serializable
data class GuildFeatureUse(
    val feature: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
    @Serializable(LD::class) val errors: Double = 0.0,
)

/** A command's use in one guild. */
@Serializable
data class GuildCommandUse(
    val command: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
    @Serializable(LD::class) val failures: Double = 0.0,
)

/** Everything known about one guild over the range. */
@Serializable
data class GuildCard(
    @Serializable(SF::class) val guildId: String = "",
    val name: String? = null,
    @Serializable(LD::class) val memberCount: Double? = null,
    val shard: Int? = null,
    val joinedAt: String? = null,
    val present: Boolean = false,
    @Serializable(LD::class) val commands: Double = 0.0,
    @Serializable(LD::class) val events: Double = 0.0,
    val features: List<GuildFeatureUse> = emptyList(),
    val shape: GuildShape? = null,
    val configuredFeatures: List<String> = emptyList(),
    val enabledFeatures: List<String> = emptyList(),
    val topCommands: List<GuildCommandUse> = emptyList(),
)

/** A feature's adoption across the fleet. */
@Serializable
data class FeatureAdoptionRow(
    val feature: String = "",
    @Serializable(LD::class) val activeGuilds: Double = 0.0,
    @Serializable(LD::class) val activity: Double = 0.0,
    @Serializable(LD::class) val errors: Double = 0.0,
    @Serializable(LD::class) val configured: Double? = null,
    @Serializable(LD::class) val enabled: Double? = null,
)

/** How many guilds use a given number of features. */
@Serializable
data class DepthBucket(
    val features: Int = 0,
    @Serializable(LD::class) val guilds: Double = 0.0,
)

/** One guild's size and feature count. */
@Serializable
data class DepthPoint(
    @Serializable(SF::class) val guildId: String = "",
    @Serializable(LD::class) val memberCount: Double? = null,
    val features: Int = 0,
)

/** Feature depth: the histogram and the size scatter. */
@Serializable
data class FeatureDepth(
    val histogram: List<DepthBucket> = emptyList(),
    val points: List<DepthPoint> = emptyList(),
)

/** One setting value's count from the latest census. */
@Serializable
data class SettingUsageRow(
    val metric: String = "",
    val table: String = "",
    val column: String = "",
    val value: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
)

/** One day and a value. */
@Serializable
data class DayValue(
    val day: String = "",
    @Serializable(LD::class) val value: Double = 0.0,
)

/** One bot's nightly snapshot. */
@Serializable
data class SnapshotRow(
    val day: String = "",
    val bot: String = "",
    @Serializable(LD::class) val guilds: Double = 0.0,
    @Serializable(LD::class) val users: Double = 0.0,
)

/** One guild of the serving instance with its shape and use. */
@Serializable
data class GuildOverviewRow(
    @Serializable(SF::class) val guildId: String = "",
    val name: String = "",
    val shard: Int = 0,
    val shape: GuildShape = GuildShape(),
    val joinedAt: String? = null,
    @Serializable(LD::class) val commands: Double = 0.0,
    @Serializable(LD::class) val events: Double = 0.0,
    val featuresUsed: Int = 0,
    val featuresConfigured: Int = 0,
    val featuresEnabled: Int = 0,
    val features: List<String> = emptyList(),
)

/** Joins and leaves of one day. */
@Serializable
data class ChurnDay(
    val day: String = "",
    @Serializable(LD::class) val joins: Double = 0.0,
    @Serializable(LD::class) val leaves: Double = 0.0,
)

/** Guild growth over the range. */
@Serializable
data class ChurnSummary(
    @Serializable(LD::class) val joins: Double = 0.0,
    @Serializable(LD::class) val leaves: Double = 0.0,
    @Serializable(LD::class) val net: Double = 0.0,
    @Serializable(LD::class) val bounced: Double = 0.0,
    @Serializable(LD::class) val bounceRate: Double? = null,
    val joinsBySize: List<BreakdownRow> = emptyList(),
    val leavesBySize: List<BreakdownRow> = emptyList(),
    val days: List<ChurnDay> = emptyList(),
)

/** How many guilds that joined on a day are still present. */
@Serializable
data class RetentionPoint(
    val day: String = "",
    @Serializable(LD::class) val joined: Double = 0.0,
    @Serializable(LD::class) val retained: Double = 0.0,
    @Serializable(LD::class) val rate: Double? = null,
)

/** A guild that added the bot and removed it within a day. */
@Serializable
data class BouncedGuildRow(
    @Serializable(SF::class) val guildId: String = "",
    val firstSeen: String? = null,
    val lastSeen: String? = null,
    @Serializable(LD::class) val events: Double = 0.0,
)

/** A guild that was active a week ago and has gone quiet. */
@Serializable
data class SilentGuildRow(
    @Serializable(SF::class) val guildId: String = "",
    val name: String? = null,
    @Serializable(LD::class) val memberCount: Double? = null,
    val lastActive: String? = null,
    @Serializable(LD::class) val events: Double = 0.0,
)

/** An exception group folded over the range. */
@Serializable
data class ErrorGroupRow(
    val type: String = "",
    val module: String? = null,
    val location: String? = null,
    val hash: String = "",
    @Serializable(LD::class) val count: Double = 0.0,
    val firstSeen: String? = null,
    val lastSeen: String? = null,
    val lastMessage: String? = null,
)

/** One hour of occurrences behind an exception group. */
@Serializable
data class ErrorOccurrenceRow(
    val hour: String? = null,
    val bot: String = "",
    val shard: Int? = null,
    val location: String? = null,
    val message: String? = null,
    @Serializable(LD::class) val count: Double = 0.0,
    val firstSeen: String? = null,
    val lastSeen: String? = null,
    @Serializable(SF::class) val lastGuildId: String? = null,
)

/** One AI model's use. */
@Serializable
data class AiModelRow(
    val model: String = "",
    val provider: String? = null,
    @Serializable(LD::class) val requests: Double = 0.0,
    @Serializable(LD::class) val failures: Double = 0.0,
    @Serializable(LD::class) val tokensIn: Double = 0.0,
    @Serializable(LD::class) val tokensOut: Double = 0.0,
    @Serializable(LD::class) val p95Ms: Double? = null,
    @Serializable(LD::class) val costUsd: Double? = null,
)

/** AI use over the range. */
@Serializable
data class AiSummary(
    val models: List<AiModelRow> = emptyList(),
    @Serializable(LD::class) val requests: Double = 0.0,
    @Serializable(LD::class) val tokensIn: Double = 0.0,
    @Serializable(LD::class) val tokensOut: Double = 0.0,
    @Serializable(LD::class) val costUsd: Double? = null,
)

/** A guild ranked by AI chat use. */
@Serializable
data class AiGuildRow(
    @Serializable(SF::class) val guildId: String = "",
    val name: String? = null,
    @Serializable(LD::class) val count: Double = 0.0,
    @Serializable(LD::class) val errors: Double = 0.0,
)

/** A website route's traffic. */
@Serializable
data class RouteRow(
    val route: String = "",
    @Serializable(LD::class) val views: Double = 0.0,
    @Serializable(LD::class) val visitors: Double = 0.0,
    @Serializable(LD::class) val p95Ms: Double? = null,
    @Serializable(LD::class) val errors: Double = 0.0,
)

/** A website route and failing status. */
@Serializable
data class RouteErrorRow(
    val route: String = "",
    val status: Int = 0,
    @Serializable(LD::class) val count: Double = 0.0,
    val lastSeen: String? = null,
)

/** The website login funnel. */
@Serializable
data class WebsiteFunnel(
    @Serializable(LD::class) val loginViews: Double = 0.0,
    @Serializable(LD::class) val loginVisitors: Double = 0.0,
    @Serializable(LD::class) val callbackViews: Double = 0.0,
    @Serializable(LD::class) val callbackVisitors: Double = 0.0,
    @Serializable(LD::class) val dashboardViews: Double = 0.0,
    @Serializable(LD::class) val dashboardVisitors: Double = 0.0,
)

/** One analytics table's size. */
@Serializable
data class PipelineTable(
    val table: String = "",
    @Serializable(LD::class) val rows: Double = 0.0,
    val newest: String? = null,
)

/** One registered bot instance. */
@Serializable
data class PipelineInstance(
    @Serializable(SF::class) val botId: String = "",
    val botName: String = "",
    val host: String = "",
    val port: Int = 0,
    val isActive: Boolean = false,
    val lastStatusUpdate: String? = null,
    val lastGuildCountAt: String? = null,
)

/** The telemetry pipeline's own state, per serving process. */
@Serializable
data class PipelineHealth(
    val enabled: Boolean = false,
    val lastFlushAt: String? = null,
    val lastRollupAt: String? = null,
    val lastMaintenanceAt: String? = null,
    val lastError: String? = null,
    val pendingSeries: Int = 0,
    val pendingRows: Int = 0,
    val tables: List<PipelineTable> = emptyList(),
    val instances: List<PipelineInstance> = emptyList(),
)

/** One group's live evaluation state of an alert rule. */
@Serializable
data class AlertRuleState(
    val groupKey: String = "",
    val state: String = "ok",
    val since: String? = null,
    @Serializable(LD::class) val lastValue: Double? = null,
    val lastNotifiedAt: String? = null,
)

/** A saved alert rule with its live states. */
@Serializable
data class AlertRule(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    val enabled: Boolean = true,
    val severity: String = "warning",
    val metric: String = "",
    val filters: Map<String, String> = emptyMap(),
    val groupBy: String? = null,
    val aggregation: String = "sum",
    val windowSeconds: Int = 300,
    val comparator: String = "gt",
    val baselineDays: Int? = null,
    val direction: String? = null,
    @Serializable(LD::class) val threshold: Double = 0.0,
    @Serializable(LD::class) val thresholdHigh: Double? = null,
    val forSeconds: Int = 0,
    val cooldownSeconds: Int = 900,
    val repeatSeconds: Int? = null,
    val webhookUrl: String = "",
    @Serializable(SF::class) val mentionRoleId: String? = null,
    @Serializable(SF::class) val threadId: String? = null,
    val notifyOnResolve: Boolean = true,
    val quietStartMinute: Int? = null,
    val quietEndMinute: Int? = null,
    val mutedUntil: String? = null,
    val minSamples: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    @Serializable(SF::class) val createdBy: String? = null,
    val states: List<AlertRuleState> = emptyList(),
    val firedLast30Days: Int = 0,
)

/** The create and update body of an alert rule. Snowflakes travel as strings. */
@Serializable
data class AlertRuleInput(
    val name: String = "",
    val description: String? = null,
    val enabled: Boolean = true,
    val severity: String = "warning",
    val metric: String = "",
    val filters: Map<String, String> = emptyMap(),
    val groupBy: String? = null,
    val aggregation: String = "sum",
    val windowSeconds: Int = 300,
    val comparator: String = "gt",
    val baselineDays: Int? = null,
    val direction: String? = null,
    val threshold: Double = 0.0,
    val thresholdHigh: Double? = null,
    val forSeconds: Int = 0,
    val cooldownSeconds: Int = 900,
    val repeatSeconds: Int? = null,
    val webhookUrl: String = "",
    val mentionRoleId: String? = null,
    val threadId: String? = null,
    val notifyOnResolve: Boolean = true,
    val quietStartMinute: Int? = null,
    val quietEndMinute: Int? = null,
    val minSamples: Int? = null,
)

/** Whether a test or digest reached Discord. */
@Serializable
data class AlertSendResult(val success: Boolean = false)

/** The mute answer; [mutedUntil] is absent after an unmute. */
@Serializable
data class AlertMuteResult(val mutedUntil: String? = null)

/** One alert state transition. */
@Serializable
data class AlertEvent(
    val id: Long = 0,
    val ruleId: Int = 0,
    val ruleName: String = "",
    val severity: String = "info",
    val groupKey: String = "",
    val at: String? = null,
    val fromState: String = "",
    val toState: String = "",
    @Serializable(LD::class) val value: Double? = null,
    @Serializable(LD::class) val threshold: Double = 0.0,
    val notified: Boolean = false,
)

/** A rule group that is firing right now. */
@Serializable
data class FiringAlert(
    val ruleId: Int = 0,
    val ruleName: String = "",
    val severity: String = "info",
    val metric: String = "",
    val groupKey: String = "",
    val since: String? = null,
    @Serializable(LD::class) val lastValue: Double? = null,
    @Serializable(LD::class) val threshold: Double = 0.0,
)

/** A threshold line a chart draws for an enabled rule on its metric. */
@Serializable
data class AlertBand(
    val id: Int = 0,
    val name: String = "",
    @Serializable(LD::class) val threshold: Double = 0.0,
    @Serializable(LD::class) val thresholdHigh: Double? = null,
    val comparator: String = "",
    val severity: String = "info",
)

/** How a number is rendered, mirroring the dashboard's `ValueFormat`. */
enum class ValueFormat {
    /** Rounded with separators. */
    Whole,

    /** 1.2k, 3.45M, 1.23B. */
    Compact,

    /** Rounded milliseconds. */
    Ms,

    /** A percentage with one decimal. */
    Pct,

    /** KB, MB or GB. */
    Bytes,

    /** Two decimals. */
    Decimal,

    /** A duration in seconds. */
    Seconds,
}

/** The placeholder for a value that is missing. */
const val Missing = "-"

private fun Double?.missing(): Boolean = this == null || isNaN()

/** Rounds with en-US thousands separators. */
fun fmtInt(x: Double?): String =
    if (x.missing()) Missing else String.format(Locale.US, "%,d", x!!.roundToLong())

/** 1.2k, 3.45M, 1.23B, small values to at most two decimals. */
fun fmtCompact(x: Double?): String {
    if (x.missing()) return Missing
    val v = x!!
    val a = abs(v)
    return when {
        a >= 1e9 -> String.format(Locale.US, "%.2fB", v / 1e9)
        a >= 1e6 -> String.format(Locale.US, "%.2fM", v / 1e6)
        a >= 1e3 -> String.format(Locale.US, "%.1fk", v / 1e3)
        a >= 10 || v == floor(v) -> v.roundToLong().toString()
        else -> String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
    }
}

/** Rounded milliseconds with separators. */
fun fmtMs(x: Double?): String = if (x.missing()) Missing else "${fmtInt(x)} ms"

/** A percentage with [digits] decimals. */
fun fmtPct(x: Double?, digits: Int = 1): String =
    if (x.missing()) Missing else String.format(Locale.US, "%.${digits}f%%", x)

/** KB, MB or GB. */
fun fmtBytes(x: Double?): String {
    if (x.missing()) return Missing
    val v = x!!
    return when {
        v >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", v / 1_073_741_824)
        v >= 1_048_576 -> String.format(Locale.US, "%.0f MB", v / 1_048_576)
        else -> "${(v / 1024).roundToLong()} KB"
    }
}

/** Seconds as `N s`, `N m`, `N.N h` or `N.N d`. */
fun fmtDuration(seconds: Double?): String {
    if (seconds.missing()) return Missing
    val s = seconds!!
    return when {
        s < 60 -> "${s.roundToLong()} s"
        s < 3600 -> "${(s / 60).roundToLong()} m"
        s < 86_400 -> String.format(Locale.US, "%.1f h", s / 3600)
        else -> String.format(Locale.US, "%.1f d", s / 86_400)
    }
}

/** Formats [value] as [format]. */
fun fmt(value: Double?, format: ValueFormat): String = when (format) {
    ValueFormat.Ms -> fmtMs(value)
    ValueFormat.Pct -> fmtPct(value)
    ValueFormat.Bytes -> fmtBytes(value)
    ValueFormat.Compact -> fmtCompact(value)
    ValueFormat.Decimal -> if (value.missing()) Missing else String.format(Locale.US, "%.2f", value)
    ValueFormat.Seconds -> fmtDuration(value)
    ValueFormat.Whole -> fmtInt(value)
}

/** Dollars with two decimals, or none from 100 up. */
fun fmtUsd(x: Double?): String {
    if (x.missing()) return Missing
    return if (x!! >= 100) String.format(Locale.US, "$%.0f", x) else String.format(Locale.US, "$%.2f", x)
}

/**
 * Parses a bot timestamp. Offsetless values, which the alert endpoints send,
 * are read as UTC, the same as the dashboard's `utcParse`.
 */
fun utcParse(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let { InstantParser.parse(it) }

/** Seconds since [value], or `null` when it is missing. */
fun ageSeconds(value: String?, now: Long = System.currentTimeMillis()): Double? =
    utcParse(value)?.let { (now - it.toEpochMilli()) / 1000.0 }

/** `N m ago` style age, or the placeholder. */
fun ago(value: String?): String = ageSeconds(value)?.let { "${fmtDuration(it)} ago" } ?: Missing

private val StampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)
private val ClockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)
private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd").withZone(ZoneOffset.UTC)
private val InputFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

/** `YYYY-MM-DD HH:MM` in UTC. */
fun stamp(value: String?): String = utcParse(value)?.let { StampFormat.format(it) } ?: Missing

/** `HH:MM:SS` in UTC. */
fun clock(value: String?): String = utcParse(value)?.let { ClockFormat.format(it) } ?: Missing

/** `HH:MM:SS` in UTC for an epoch millisecond time. */
fun clockOf(epochMillis: Long): String = ClockFormat.format(Instant.ofEpochMilli(epochMillis))

/** `MM-DD` of a day field. */
fun dayLabel(value: String?): String = utcParse(value)?.let { DayFormat.format(it) } ?: Missing

/** The custom range editor's `YYYY-MM-DD HH:MM` UTC text for an instant. */
fun rangeInputText(instant: Instant): String = InputFormat.format(instant)

/** Parses the custom range editor's UTC text, or `null` when it is not a date. */
fun parseRangeInput(text: String): Instant? {
    val trimmed = text.trim().replace('T', ' ')
    if (trimmed.isEmpty()) return null
    return runCatching { java.time.LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }
        .getOrNull()
        ?.toInstant(ZoneOffset.UTC)
}

/** A chart bucket's x label: `M/D` for hourly buckets over more than three days, `HH:MM` otherwise. */
fun bucketLabel(unix: Long, resolution: Int, rangeSecs: Double): String {
    val date = Instant.ofEpochSecond(unix).atZone(ZoneOffset.UTC)
    return if (resolution >= 60 && rangeSecs > 259_200) {
        "${date.monthValue}/${date.dayOfMonth}"
    } else {
        String.format(Locale.US, "%02d:%02d", date.hour, date.minute)
    }
}

/** Cuts [value] to [max] characters with an ellipsis. */
fun truncate(value: String?, max: Int): String {
    if (value.isNullOrEmpty()) return ""
    return if (value.length > max) value.take(max - 1) + "…" else value
}

/** Whether [value] looks like a guild id: 15 to 20 digits. */
fun isGuildId(value: String): Boolean = Regex("^\\d{15,20}$").matches(value)

/** Short comparator symbols for rule conditions and bands. */
val ComparatorSymbol: Map<String, String> = mapOf(
    "gt" to ">",
    "gte" to "≥",
    "lt" to "<",
    "lte" to "≤",
    "outside" to "outside",
    "pct_change" to "Δ%",
    "deviates" to "Δ%",
    "nodata" to "no data",
)

/** Rule evaluation windows in seconds. */
val AlertWindows: List<Int> = listOf(60, 300, 900, 1800, 3600, 21_600, 86_400)

/** How long a breach must hold before firing, in seconds. */
val AlertForSeconds: List<Int> = listOf(0, 60, 300, 900, 1800, 3600)

/** Minimum gaps between notifications, in seconds. */
val AlertCooldowns: List<Int> = listOf(0, 300, 900, 1800, 3600, 21_600, 86_400)

/** Re-notify intervals while firing, in seconds; zero means once. */
val AlertRepeats: List<Int> = listOf(0, 900, 1800, 3600, 21_600, 86_400)

/** Aggregations a rule can evaluate. */
val AlertAggregations: List<String> = listOf("sum", "avg", "min", "max", "last", "rate", "count", "p50", "p95", "p99")

/** Comparators a rule can use. */
val AlertComparators: List<String> = listOf("gt", "gte", "lt", "lte", "outside", "pct_change", "deviates", "nodata")

/** Whether [comparator] compares against a baseline rather than a fixed threshold. */
fun isBaselineComparator(comparator: String): Boolean = comparator == "pct_change" || comparator == "deviates"

/** The one line condition summary of a rule, as the dashboard's `describe()` builds it. */
fun describeRule(rule: AlertRuleInput): String {
    val head = "${rule.aggregation}(${rule.metric})"
    val window = fmtDuration(rule.windowSeconds.toDouble())
    return when {
        rule.comparator == "nodata" -> "${rule.metric} reports nothing for $window"
        rule.comparator == "outside" ->
            "$head outside ${fmtCompact(rule.threshold)} to ${fmtCompact(rule.thresholdHigh)} over $window"
        isBaselineComparator(rule.comparator) -> {
            val dir = when (rule.direction) {
                "up" -> "rises"
                "down" -> "falls"
                else -> "moves"
            }
            "$head $dir ${fmtCompact(rule.threshold)}% vs ${rule.baselineDays ?: 7}d baseline over $window"
        }
        else -> "$head ${ComparatorSymbol[rule.comparator] ?: rule.comparator} ${fmtCompact(rule.threshold)} over $window"
    }
}

/** The worst live state of a rule's groups. */
fun AlertRule.worstState(): String {
    val states = this.states.map { it.state }
    return when {
        "firing" in states -> "firing"
        "pending" in states -> "pending"
        else -> "ok"
    }
}

/** The newest notification time across the rule's groups, or `null`. */
fun AlertRule.lastFired(): Instant? = states.mapNotNull { utcParse(it.lastNotifiedAt) }.maxOrNull()

/** Whether the rule is muted right now. */
fun AlertRule.isMuted(): Boolean = utcParse(mutedUntil)?.isAfter(Instant.now()) == true

/** The editable body of a saved rule. */
fun AlertRule.toInput(): AlertRuleInput = AlertRuleInput(
    name = name,
    description = description,
    enabled = enabled,
    severity = severity,
    metric = metric,
    filters = filters,
    groupBy = groupBy,
    aggregation = aggregation,
    windowSeconds = windowSeconds,
    comparator = comparator,
    baselineDays = baselineDays,
    direction = direction,
    threshold = threshold,
    thresholdHigh = thresholdHigh,
    forSeconds = forSeconds,
    cooldownSeconds = cooldownSeconds,
    repeatSeconds = repeatSeconds,
    webhookUrl = webhookUrl,
    mentionRoleId = mentionRoleId,
    threadId = threadId,
    notifyOnResolve = notifyOnResolve,
    quietStartMinute = quietStartMinute,
    quietEndMinute = quietEndMinute,
    minSamples = minSamples,
)

/** `HH:MM` for minutes after UTC midnight, or empty. */
fun minutesToClock(minutes: Int?): String {
    if (minutes == null) return ""
    return String.format(Locale.US, "%02d:%02d", (minutes / 60) % 24, minutes % 60)
}

/** Minutes after UTC midnight for `H:MM` or `HH:MM`, or `null` when unparseable. */
fun clockToMinutes(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").find(value.trim()) ?: return null
    return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
}

/** Sorts label values numerically when both are numbers, otherwise alphabetically. */
val LabelValueOrder: Comparator<String> = Comparator { a, b ->
    val na = a.toDoubleOrNull()
    val nb = b.toDoubleOrNull()
    if (na != null && nb != null) na.compareTo(nb) else a.compareTo(b)
}
