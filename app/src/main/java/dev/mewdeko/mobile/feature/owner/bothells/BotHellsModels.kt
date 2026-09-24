package dev.mewdeko.mobile.feature.owner.bothells

import androidx.compose.runtime.Immutable
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantParser
import kotlinx.serialization.Serializable
import java.time.Instant

/** Tab ids for the Bot Hells screen, matching the dashboard's `?tab=` values. */
object BotHellsTab {
    /** The evaluated server list with selection and bulk leave. */
    const val SERVERS = "servers"

    /** The bot wide thresholds, report channel, and auto leave switch. */
    const val SETTINGS = "settings"
}

/**
 * One server on the selected instance, evaluated against the thresholds.
 *
 * Mirrors `BotHellEntryResponse`. The controller answers through MVC `Ok()`,
 * so keys are camelCase and null properties ([iconUrl], [joinedAt]) are
 * omitted rather than sent as null. Ids arrive as bare numbers that can pass
 * 2^53, so they decode through [SnowflakeSerializer]. [ownerId] is `"0"` when
 * the guild is not cached.
 */
@Immutable
@Serializable
data class BotHellEntry(
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val guildName: String = "",
    val iconUrl: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val ownerId: Snowflake = "0",
    val total: Int = 0,
    val humans: Int = 0,
    val bots: Int = 0,
    val percent: Int = 0,
    val isBotHell: Boolean = false,
    val byCount: Boolean = false,
    val byPercent: Boolean = false,
    val complete: Boolean = false,
    val joinedAt: String? = null,
) {
    /** When the bot joined, parsed leniently (0 to 7 fractional digits, a missing zone read as UTC). */
    val joinedInstant: Instant?
        get() = joinedAt?.let(InstantParser::parse)

    /**
     * The trigger chip label. Matches the dashboard, which ignores [isBotHell]
     * here, so a server under the member minimum can still read "Count".
     */
    val triggerLabel: String
        get() = when {
            byCount && byPercent -> "Count and ratio"
            byCount -> "Count"
            byPercent -> "Ratio"
            else -> "Below thresholds"
        }

    /** Up to the first two letters of the name, uppercased, for the icon fallback. */
    val initials: String
        get() = guildName.trim().take(2).uppercase()
}

/**
 * The bot wide Bot Hell settings with the report channel resolved.
 *
 * Mirrors `BotHellSettingsResponse`. [guildId] and [guildName] describe the
 * report channel's server, not any listed entry. Snowflakes use `"0"` as the
 * sentinel: [channelId] 0 means the join/leave channel fallback,
 * [effectiveChannelId] 0 means nowhere. [channelName] and [guildName] are
 * omitted when they cannot be resolved.
 */
@Immutable
@Serializable
data class BotHellSettings(
    val minMembers: Int = 0,
    val botCount: Int = 0,
    val botPercent: Int = 0,
    val autoLeave: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val effectiveChannelId: Snowflake = "0",
    val usingFallback: Boolean = false,
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val guildName: String? = null,
    val reachable: Boolean = false,
) {
    /** The configured channel as the text field shows it: empty when using the fallback. */
    val channelInputText: String
        get() = if (channelId.isZeroSnowflake) "" else channelId
}

/**
 * `GET api/BotHell`: every server on the instance, flagged ones first, with
 * the global flagged count and the current settings.
 */
@Serializable
data class BotHellListResponse(
    val items: List<BotHellEntry> = emptyList(),
    val flagged: Int = 0,
    val settings: BotHellSettings? = null,
)

/**
 * `POST api/BotHell/leave`: the servers left (or deleted, when the bot owned
 * them) and the failures keyed by stringified guild id.
 */
@Serializable
data class BotHellLeaveResponse(
    val left: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val failed: Map<String, String> = emptyMap(),
)

/** The persistent line under the toolbar reporting the last leave. */
@Immutable
data class BotHellsBanner(
    val text: String,
    val isError: Boolean = false,
)

/** Bot Hells screen state: servers littered with bots, with bulk leave. */
@Immutable
data class BotHellsState(
    val tab: String = BotHellsTab.SERVERS,
    val entries: List<BotHellEntry> = emptyList(),
    val flaggedCount: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val flaggedOnly: Boolean = true,
    val search: String = "",
    val selected: Set<Snowflake> = emptySet(),
    val checking: Set<Snowflake> = emptySet(),
    val leaving: Boolean = false,
    val leavingCount: Int = 0,
    val leaveTargets: List<BotHellEntry> = emptyList(),
    val banner: BotHellsBanner? = null,
    val settings: BotHellSettings? = null,
    val settingsLoading: Boolean = false,
    val settingsLoadError: String? = null,
    val minMembersInput: String = "",
    val botCountInput: String = "",
    val botPercentInput: String = "",
    val channelInput: String = "",
    val savingSettings: Boolean = false,
    val settingsError: String? = null,
    val pendingAutoLeave: Boolean? = null,
) {
    /** Whether the leave confirmation is open. */
    val confirmingLeave: Boolean
        get() = leaveTargets.isNotEmpty()

    /** Total bots across every flagged server, for the stat tile. */
    val botsInFlagged: Int
        get() = entries.sumOf { if (it.isBotHell) it.bots else 0 }

    /** The auto leave switch position: the value being saved, else the saved one. */
    val autoLeaveShown: Boolean
        get() = pendingAutoLeave ?: settings?.autoLeave ?: false

    /**
     * Whether any threshold input or the trimmed channel input differs from
     * the saved settings, with a zero channel meaning empty.
     */
    val thresholdsDirty: Boolean
        get() {
            val saved = settings ?: return false
            return minMembersInput != saved.minMembers.toString() ||
                botCountInput != saved.botCount.toString() ||
                botPercentInput != saved.botPercent.toString() ||
                channelInput.trim() != saved.channelInputText
        }

    /** The entries the list shows: the flagged filter, then a name or id search. */
    fun visibleEntries(): List<BotHellEntry> {
        val term = search.trim().lowercase()
        return entries.filter { entry ->
            if (flaggedOnly && !entry.isBotHell) return@filter false
            if (term.isEmpty()) return@filter true
            entry.guildName.lowercase().contains(term) || entry.guildId.contains(term)
        }
    }
}

/** Whether a snowflake is the zero or missing sentinel. */
val Snowflake.isZeroSnowflake: Boolean
    get() = isEmpty() || this == "0"

/**
 * The "Flagging servers with ..." summary shown above the toolbar, matching
 * the dashboard's wording for each threshold combination.
 */
fun BotHellSettings.thresholdSummary(): String {
    val rule = when {
        botCount > 0 && botPercent > 0 -> "either $botCount+ bots or $botPercent%+ bots."
        botCount > 0 -> "$botCount+ bots."
        botPercent > 0 -> "$botPercent%+ bots."
        else -> "no thresholds set, so nothing is flagged."
    }
    return "Flagging servers with at least $minMembers members and $rule " +
        "Counts come from the member cache, use Recheck for exact numbers on large servers."
}
