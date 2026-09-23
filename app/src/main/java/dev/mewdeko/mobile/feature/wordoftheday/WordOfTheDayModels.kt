package dev.mewdeko.mobile.feature.wordoftheday

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** Where a guild's daily word is drawn from. */
enum class WordSourceMode(val value: Int, val label: String, val blurb: String) {
    DICTIONARY(0, "Dictionary", "Datamuse words filtered by topic, part of speech, and difficulty"),
    CUSTOM(1, "Custom list", "Only words added to this server's list"),
    MIXED(2, "Mixed", "Custom words first, then the dictionary once they run out");

    companion object {
        /** Resolves a stored value, defaulting to the dictionary. */
        fun from(value: Int): WordSourceMode = entries.firstOrNull { it.value == value } ?: DICTIONARY
    }
}

/** Part of speech filter. Zero means any on the base config and inherit on a rule. */
enum class WordPartOfSpeech(val value: Int, val label: String) {
    ANY(0, "Any"),
    NOUN(1, "Noun"),
    VERB(2, "Verb"),
    ADJECTIVE(3, "Adjective"),
    ADVERB(4, "Adverb");

    companion object {
        /** Resolves a stored value, defaulting to any. */
        fun from(value: Int?): WordPartOfSpeech = entries.firstOrNull { it.value == value } ?: ANY
    }
}

/** Frequency based difficulty filter. Zero means any on the base config and inherit on a rule. */
enum class WordDifficulty(val value: Int, val label: String) {
    ANY(0, "Any"),
    COMMON(1, "Common"),
    MODERATE(2, "Moderate"),
    RARE(3, "Rare");

    companion object {
        /** Resolves a stored value, defaulting to any. */
        fun from(value: Int?): WordDifficulty = entries.firstOrNull { it.value == value } ?: ANY
    }
}

/** Calendar unit a schedule rule applies to. */
enum class ScheduleRuleType(val value: Int) {
    DAY_OF_WEEK(0),
    MONTH(1);
}

/** How long an idle discussion thread stays open before Discord archives it. */
enum class ThreadAutoArchive(val minutes: Int, val label: String) {
    HOUR(60, "1 hour"),
    DAY(1440, "1 day"),
    THREE_DAYS(4320, "3 days"),
    WEEK(10080, "1 week");

    companion object {
        /** Resolves a stored value, defaulting to one day. */
        fun from(minutes: Int): ThreadAutoArchive = entries.firstOrNull { it.minutes == minutes } ?: DAY
    }
}

/** Thread name used when a guild has not set an override. */
const val DefaultThreadName = "Word of the day: %wotd.word%"

/** Word of the Day configuration returned by `GET /wordoftheday/{guildId}/config`. */
@Serializable
data class WordOfTheDayConfig(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val enabled: Boolean = false,
    val postHour: Int = 9,
    val timezone: String = "UTC",
    @Serializable(with = SnowflakeSerializer::class) val pingRoleId: Snowflake? = null,
    val messageTemplate: String? = null,
    val topic: String? = null,
    val partOfSpeech: Int = 0,
    val difficulty: Int = 0,
    val sourceMode: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastPostedDate: Instant? = null,
    val customWordCount: Int = 0,
    val createThread: Boolean = false,
    val threadName: String? = null,
    val threadAutoArchiveMinutes: Int = ThreadAutoArchive.DAY.minutes,
)

/** One word in the guild's custom pool. */
@Serializable
data class WordOfTheDayWord(
    val id: Int = 0,
    val word: String = "",
    val partOfSpeech: String? = null,
    val definition: String? = null,
    val example: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val addedBy: Snowflake = "",
    val timesUsed: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastUsed: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A word the bot has already posted. */
@Serializable
data class WordOfTheDayHistoryEntry(
    val id: Int = 0,
    val word: String = "",
    val partOfSpeech: String? = null,
    val definition: String = "",
    val example: String? = null,
    val phonetic: String? = null,
    @Serializable(with = InstantSerializer::class) val postedOn: Instant? = null,
)

/** A weekday or month rule overriding the base filters. */
@Serializable
data class WordOfTheDaySchedule(
    val id: Int = 0,
    val ruleType: Int = 0,
    val ruleKey: Int = 0,
    val topic: String? = null,
    val partOfSpeech: Int? = null,
    val difficulty: Int? = null,
)

/** A word the bot just posted via the post-now endpoint. */
@Serializable
data class WordEntry(
    val word: String = "",
    val partOfSpeech: String? = null,
    val definition: String = "",
    val example: String? = null,
    val phonetic: String? = null,
    val isCustom: Boolean = false,
)

/** Editable draft of one weekday or month rule on the schedule tab. */
data class RuleDraft(
    val type: ScheduleRuleType,
    val key: Int,
    val name: String,
    val topic: String = "",
    val partOfSpeech: Int = 0,
    val difficulty: Int = 0,
    val exists: Boolean = false,
    val saving: Boolean = false,
) {
    /** Stable identity for list keys and lookups. */
    val id: String get() = "${type.value}-$key"

    /** Short human summary of what the rule overrides. */
    val summary: String
        get() {
            val parts = buildList {
                if (topic.isNotBlank()) add("topic \"${topic.trim()}\"")
                if (partOfSpeech != 0) add(WordPartOfSpeech.from(partOfSpeech).label.lowercase())
                if (difficulty != 0) add(WordDifficulty.from(difficulty).label.lowercase())
            }
            return if (parts.isEmpty()) "inherits base settings" else parts.joinToString(", ")
        }

    companion object {
        /** Display names for weekday keys, Sunday first to match `DayOfWeek` on the bot. */
        val dayNames = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        /** Display names for month keys 1 to 12. */
        val monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}

/** Common timezone presets offered in the picker. */
data class WordOfTheDayTimezone(val id: String, val label: String) {
    companion object {
        /** The timezone choices offered in the picker. */
        val presets = listOf(
            WordOfTheDayTimezone("UTC", "UTC (GMT+0)"),
            WordOfTheDayTimezone("America/New_York", "Eastern Time (GMT-5)"),
            WordOfTheDayTimezone("America/Chicago", "Central Time (GMT-6)"),
            WordOfTheDayTimezone("America/Denver", "Mountain Time (GMT-7)"),
            WordOfTheDayTimezone("America/Los_Angeles", "Pacific Time (GMT-8)"),
            WordOfTheDayTimezone("America/Anchorage", "Alaska (GMT-9)"),
            WordOfTheDayTimezone("Pacific/Honolulu", "Hawaii (GMT-10)"),
            WordOfTheDayTimezone("America/Sao_Paulo", "Sao Paulo (GMT-3)"),
            WordOfTheDayTimezone("Europe/London", "London (GMT+0)"),
            WordOfTheDayTimezone("Europe/Paris", "Paris (GMT+1)"),
            WordOfTheDayTimezone("Europe/Berlin", "Berlin (GMT+1)"),
            WordOfTheDayTimezone("Europe/Madrid", "Madrid (GMT+1)"),
            WordOfTheDayTimezone("Europe/Athens", "Athens (GMT+2)"),
            WordOfTheDayTimezone("Europe/Moscow", "Moscow (GMT+3)"),
            WordOfTheDayTimezone("Asia/Dubai", "Dubai (GMT+4)"),
            WordOfTheDayTimezone("Asia/Kolkata", "India (GMT+5:30)"),
            WordOfTheDayTimezone("Asia/Bangkok", "Bangkok (GMT+7)"),
            WordOfTheDayTimezone("Asia/Shanghai", "Shanghai (GMT+8)"),
            WordOfTheDayTimezone("Asia/Singapore", "Singapore (GMT+8)"),
            WordOfTheDayTimezone("Asia/Tokyo", "Tokyo (GMT+9)"),
            WordOfTheDayTimezone("Asia/Seoul", "Seoul (GMT+9)"),
            WordOfTheDayTimezone("Australia/Sydney", "Sydney (GMT+10)"),
            WordOfTheDayTimezone("Pacific/Auckland", "Auckland (GMT+12)"),
        )
    }
}
