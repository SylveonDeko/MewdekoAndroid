package dev.mewdeko.mobile.feature.polls

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant

/** The bot's `PollType` enum, with the labels the dashboard shows. */
enum class PollType(val value: Int, val label: String) {
    YES_NO(0, "Yes / No"),
    SINGLE_CHOICE(1, "Single choice"),
    MULTI_CHOICE(2, "Multiple choice"),
    ANONYMOUS(3, "Anonymous"),
    ROLE_RESTRICTED(4, "Role restricted");

    companion object {
        /** The order the create form's type picker lists the choices in. */
        val pickerOrder = listOf(SINGLE_CHOICE, MULTI_CHOICE, YES_NO, ANONYMOUS, ROLE_RESTRICTED)

        /** Resolves a raw enum value, or `null` when it is unknown. */
        fun from(value: Int?): PollType? = PollType.entries.firstOrNull { it.value == value }

        /** Resolves an enum member name such as `MultiChoice`, ignoring case and separators. */
        fun fromName(name: String?): PollType? {
            val key = name?.replace("_", "")?.lowercase() ?: return null
            return PollType.entries.firstOrNull { it.name.replace("_", "").lowercase() == key }
        }
    }
}

/**
 * Decodes a `PollType` that may arrive as its numeric value or its member
 * name, falling back to single choice when neither matches.
 */
object PollTypeCodeSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("PollTypeCode", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return PollType.SINGLE_CHOICE.value
        primitive.intOrNull?.let { return it }
        return PollType.fromName(primitive.content)?.value ?: PollType.SINGLE_CHOICE.value
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/** One answer on a poll, from `PollOptionResponse`. */
@Serializable
data class PollOption(
    val id: Int = 0,
    val text: String = "",
    val index: Int = 0,
    val color: String? = null,
    val emote: String? = null,
    val voteCount: Int = 0,
    val votePercentage: Double = 0.0,
)

/** Aggregate voting figures, from `PollStatsResponse`. */
@Serializable
data class PollStats(
    val totalVotes: Int = 0,
    val uniqueVoters: Int = 0,
    val participationRate: Double = 0.0,
    val peakVotingHour: Int = 0,
)

/** A poll returned by `GET Poll/{guildId}` and `GET Poll/{guildId}/{pollId}`. */
@Serializable
data class Poll(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val creatorId: Snowflake = "",
    val creatorName: String? = null,
    val question: String = "",
    @Serializable(with = PollTypeCodeSerializer::class) val type: Int = 1,
    val options: List<PollOption> = emptyList(),
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val closedAt: Instant? = null,
    val isActive: Boolean = false,
    val stats: PollStats? = null,
) {
    /** The poll's type, when the value is known. */
    val pollType: PollType? get() = PollType.from(type)

    /** Total votes across every option, as the dashboard counts them. */
    val totalVotes: Int get() = options.sumOf { it.voteCount }
}

/** A queued poll from `GET Poll/{guildId}/scheduled`. */
@Serializable
data class ScheduledPoll(
    val id: Int = 0,
    val question: String = "",
    @Serializable(with = PollTypeCodeSerializer::class) val type: Int = 1,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val creatorId: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val scheduledFor: Instant? = null,
    val durationMinutes: Int? = null,
    @Serializable(with = InstantSerializer::class) val scheduledAt: Instant? = null,
    val isExecuted: Boolean = false,
    @Serializable(with = InstantSerializer::class) val executedAt: Instant? = null,
    val createdPollId: Int? = null,
    val isCancelled: Boolean = false,
    @Serializable(with = InstantSerializer::class) val cancelledAt: Instant? = null,
) {
    /** Whether the poll is still waiting to be posted. */
    val isPending: Boolean get() = !isExecuted && !isCancelled
}

/** Envelope returned by `GET Poll/{guildId}/scheduled`. */
@Serializable
data class ScheduledPollsResponse(
    val scheduledPolls: List<ScheduledPoll> = emptyList(),
    val count: Int = 0,
)

/**
 * A saved poll template from `GET Poll/{guildId}/templates`. [options] and
 * [settings] are JSON documents stored verbatim by the bot.
 */
@Serializable
data class PollTemplate(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val name: String = "",
    val question: String = "",
    val options: String = "",
    val settings: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val creatorId: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
) {
    /** The option texts stored in the template, in order. */
    fun optionTexts(): List<String> {
        val parsed = runCatching { MewdekoJson.parseToJsonElement(options) }.getOrNull() as? JsonArray
            ?: return emptyList()
        return parsed.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.content
                is JsonObject -> element.caseInsensitive("text")?.let { (it as? JsonPrimitive)?.content }
                else -> null
            }
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** The stored poll settings, parsed leniently. */
    fun parsedSettings(): TemplateSettings {
        val obj = settings?.let { raw -> runCatching { MewdekoJson.parseToJsonElement(raw) }.getOrNull() }
            as? JsonObject ?: return TemplateSettings()
        fun bool(key: String) = (obj.caseInsensitive(key) as? JsonPrimitive)?.booleanOrNull
        val typeElement = listOf("defaultType", "type")
            .firstNotNullOfOrNull { obj.caseInsensitive(it) } as? JsonPrimitive
        val explicitType = typeElement?.let { PollType.from(it.intOrNull) ?: PollType.fromName(it.content) }
        val allowedRoles = (obj.caseInsensitive("allowedRoles") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { id -> id.isNotBlank() && id != "0" } }
            .orEmpty()
        return TemplateSettings(
            type = explicitType,
            allowMultipleVotes = bool("allowMultipleVotes") ?: false,
            isAnonymous = bool("isAnonymous") ?: false,
            allowVoteChanges = bool("allowVoteChanges") ?: true,
            showResults = bool("showResults") ?: true,
            showProgressBars = bool("showProgressBars") ?: true,
            allowedRoles = allowedRoles,
            durationMinutes = (obj.caseInsensitive("durationMinutes") as? JsonPrimitive)?.intOrNull,
        )
    }

    private fun JsonObject.caseInsensitive(key: String): JsonElement? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
}

/** Poll settings recovered from a template's stored JSON. */
data class TemplateSettings(
    val type: PollType? = null,
    val allowMultipleVotes: Boolean = false,
    val isAnonymous: Boolean = false,
    val allowVoteChanges: Boolean = true,
    val showResults: Boolean = true,
    val showProgressBars: Boolean = true,
    val allowedRoles: List<Snowflake> = emptyList(),
    val durationMinutes: Int? = null,
) {
    /** The type to prefill: the stored one, else inferred from the vote flags. */
    val resolvedType: PollType
        get() = type ?: when {
            allowMultipleVotes -> PollType.MULTI_CHOICE
            isAnonymous -> PollType.ANONYMOUS
            allowedRoles.isNotEmpty() -> PollType.ROLE_RESTRICTED
            else -> PollType.SINGLE_CHOICE
        }
}

/** Guild-wide figures from `GET Poll/{guildId}/analytics`. */
@Serializable
data class PollAnalytics(
    val totalPolls: Int = 0,
    val activePolls: Int = 0,
    val closedPolls: Int = 0,
    val totalVotes: Int = 0,
    val averageVotesPerPoll: Double = 0.0,
    @Serializable(with = PollTypeCodeSerializer::class) val mostPopularPollType: Int = -1,
    val pollsCreatedByDay: Map<String, Int> = emptyMap(),
    val timeframe: String = "month",
)

/** The create form's working copy, also seeded from templates. */
data class PollDraft(
    val question: String = "",
    val options: List<String> = listOf("", ""),
    val type: PollType = PollType.SINGLE_CHOICE,
    val channelId: Snowflake? = null,
    val durationMinutes: String = "",
    val allowedRoles: List<Snowflake> = emptyList(),
    val allowVoteChanges: Boolean = true,
    val showResults: Boolean = true,
    val showProgressBars: Boolean = true,
    val scheduleFor: Instant? = null,
    val saveAsTemplate: Boolean = false,
    val templateName: String = "",
) {
    companion object {
        /** Longest question the form accepts. */
        const val MAX_QUESTION = 300

        /** Longest option text the form accepts. */
        const val MAX_OPTION = 100

        /** Longest template name the form accepts. */
        const val MAX_TEMPLATE_NAME = 100

        /** Fewest options a non yes/no poll may have. */
        const val MIN_OPTIONS = 2

        /** Most options Discord components allow. */
        const val MAX_OPTIONS = 25

        /** Longest duration the form accepts, in minutes (two weeks). */
        const val MAX_DURATION_MINUTES = 20160
    }
}
