package dev.mewdeko.mobile.feature.owner.leavefeedback

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantParser
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The two tabs of the Leave Feedback screen, matching the dashboard's tab ids.
 */
object LeaveFeedbackTabs {
    /** The stats, reasons, filters, and paged responses. */
    const val RESPONSES = "responses"

    /** The bot wide prompt toggle and report channel. */
    const val SETTINGS = "settings"
}

/**
 * The status filter keys `GET LeaveFeedback?status=` accepts, with the
 * dashboard's labels, in the dashboard's order.
 */
enum class LeaveFeedbackStatusFilter(val key: String, val label: String) {
    /** Answered and not dismissed. */
    Answered("answered", "Answered"),

    /** Left a non-empty comment. */
    Commented("commented", "Left a comment"),

    /** Dismissed the prompt. */
    Dismissed("dismissed", "Dismissed"),

    /** Never answered. */
    Pending("pending", "No response"),
}

/**
 * One leave feedback record, `LeaveFeedbackEntryResponse`.
 *
 * The controller answers through MVC with the Web defaults, so keys are
 * camelCase and null properties are omitted rather than sent as null. Every
 * nullable field is therefore optional and absent by default. Snowflakes
 * arrive as strings when long and as the number 0 when unset.
 */
@Serializable
data class LeaveFeedbackEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val guildName: String = "",
    val memberCount: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val ownerId: Snowflake = "0",
    val joinedAt: String? = null,
    val reason: String? = null,
    val reasonLabel: String? = null,
    val comment: String? = null,
    val dismissed: Boolean = false,
    val answeredAt: String? = null,
    val dateAdded: String? = null,
) {
    /** When the bot joined the server, if recorded. */
    val joinedInstant: Instant?
        get() = joinedAt?.let(InstantParser::parse)

    /** When the owner answered, if they did. */
    val answeredInstant: Instant?
        get() = answeredAt?.let(InstantParser::parse)

    /** When the bot left and the record was written. */
    val addedInstant: Instant?
        get() = dateAdded?.let(InstantParser::parse)

    /** Whether the owner left a comment worth expanding. */
    val hasComment: Boolean
        get() = !comment.isNullOrEmpty()

    /** The server name, or a stand in when the bot never learned it. */
    val displayName: String
        get() = guildName.ifEmpty { "Unknown server" }

    /**
     * How long the bot stayed, from [joinedAt] to [dateAdded], in the
     * dashboard's words, or `null` when either end is missing or the span is
     * negative.
     */
    val tenure: String?
        get() {
            val joined = joinedInstant ?: return null
            val left = addedInstant ?: return null
            val span = Duration.between(joined, left)
            if (span.isNegative) return null
            val days = span.toDays()
            return when {
                days == 0L -> "Under a day"
                days == 1L -> "1 day"
                days < 30L -> "$days days"
                else -> {
                    val months = days / 30L
                    if (months == 1L) "1 month" else "$months months"
                }
            }
        }
}

/** One page of records, `LeaveFeedbackPageResponse`. */
@Serializable
data class LeaveFeedbackPage(
    val items: List<LeaveFeedbackEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = LEAVE_FEEDBACK_PAGE_SIZE,
)

/** One reason and how many owners picked it, `LeaveFeedbackReasonResponse`. */
@Serializable
data class LeaveFeedbackReason(
    val key: String = "",
    val label: String = "",
    val count: Int = 0,
)

/**
 * Counts over every record, ignoring the list filters,
 * `LeaveFeedbackStatsResponse`. [reasons] always carries all seven keys in
 * the bot's display order, zero counts included.
 */
@Serializable
data class LeaveFeedbackStats(
    val total: Int = 0,
    val answered: Int = 0,
    val dismissed: Int = 0,
    val pending: Int = 0,
    val withComment: Int = 0,
    val reasons: List<LeaveFeedbackReason> = emptyList(),
) {
    /** The share of prompts that got any answer, as a whole percent. */
    val responseRate: Int
        get() = if (total > 0) ((answered + dismissed).toDouble() / total * 100).roundToInt() else 0

    /** Reasons at least one owner picked, most picked first. */
    val reasonsGiven: List<LeaveFeedbackReason>
        get() = reasons.filter { it.count > 0 }.sortedByDescending { it.count }

    /**
     * The bar fill for [reason], its count over [answered], clamped to one:
     * a record that picked a reason and was later dismissed counts toward the
     * reason but not toward answered.
     */
    fun barFraction(reason: LeaveFeedbackReason): Float =
        if (answered > 0) (reason.count.toFloat() / answered).coerceIn(0f, 1f) else 0f
}

/**
 * The bot wide prompt settings, `LeaveFeedbackSettingsResponse`.
 *
 * [channelName], [guildId], and [guildName] describe the effective channel,
 * which is the configured one or the join/leave channel when [channelId] is
 * zero. The names are omitted when the bot could not resolve them.
 */
@Serializable
data class LeaveFeedbackSettings(
    val enabled: Boolean = true,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    @Serializable(with = SnowflakeSerializer::class) val effectiveChannelId: Snowflake = "0",
    val usingFallback: Boolean = false,
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val guildName: String? = null,
    val reachable: Boolean = false,
) {
    /** The configured channel as editable text, empty when the fallback is in use. */
    val channelInputText: String
        get() = channelId.takeUnless { it.isBlank() || it == "0" }.orEmpty()

    /** Whether no channel resolves at all, so answers are posted nowhere. */
    val hasNoEffectiveChannel: Boolean
        get() = effectiveChannelId.isBlank() || effectiveChannelId == "0"
}

/** The dashboard's fixed page size. */
const val LEAVE_FEEDBACK_PAGE_SIZE: Int = 50

/**
 * Leave Feedback screen state: why servers removed the bot, answered by
 * their owners, plus the bot wide prompt settings.
 */
data class LeaveFeedbackState(
    /** The selected tab, one of [LeaveFeedbackTabs]. */
    val tab: String = LeaveFeedbackTabs.RESPONSES,
    /** The current page of records. */
    val entries: List<LeaveFeedbackEntry> = emptyList(),
    /** Records matching the filters, across every page. */
    val total: Int = 0,
    /** The 1 based page on screen. */
    val page: Int = 1,
    /** Records per page. */
    val pageSize: Int = LEAVE_FEEDBACK_PAGE_SIZE,
    /** Whether the list is being fetched; the rows are replaced by skeletons. */
    val listLoading: Boolean = true,
    /** Whether the last list fetch failed. */
    val listFailed: Boolean = false,
    /** Counts over every record, or `null` before they load. */
    val stats: LeaveFeedbackStats? = null,
    /** The reason key filter, or `null` for every reason. */
    val reasonFilter: String? = null,
    /** The status filter key, or `null` for every status. */
    val statusFilter: String? = null,
    /** The search text as typed. */
    val searchInput: String = "",
    /** The trimmed search the list was fetched with. */
    val appliedSearch: String = "",
    /** The record whose comment is expanded, if any. */
    val expandedId: Int? = null,
    /** The record awaiting delete confirmation. */
    val deleteTarget: LeaveFeedbackEntry? = null,
    /** The prompt settings, or `null` before they load. */
    val settings: LeaveFeedbackSettings? = null,
    /** Whether the settings are being fetched. */
    val settingsLoading: Boolean = true,
    /** Whether the settings failed to load. */
    val settingsLoadFailed: Boolean = false,
    /** The report channel id as typed. */
    val channelInput: String = "",
    /** Whether a settings save is in flight. */
    val savingSettings: Boolean = false,
    /** The last settings save or validation error. */
    val settingsError: String? = null,
) {
    /** Pages across [total], never less than one. */
    val totalPages: Int
        get() = maxOf(1, (total + pageSize - 1) / pageSize)

    /** Whether the channel field differs from the saved channel. */
    val channelDirty: Boolean
        get() = settings != null && channelInput.trim() != settings.channelInputText
}
