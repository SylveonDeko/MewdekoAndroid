package dev.mewdeko.mobile.feature.afk

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** AFK record persisted for a single user. */
@Serializable
data class AfkRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val message: String? = null,
    val wasTimed: Boolean = false,
    @Serializable(with = InstantSerializer::class) val `when`: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
)

/** A guild member with their optional AFK status. */
@Serializable
data class UserWithAfk(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val nickname: String? = null,
    val avatarUrl: String? = null,
    val afkStatus: AfkRecord? = null,
) {
    /** Whether this member currently has a non-empty AFK message set. */
    val hasActiveAfk: Boolean get() = !afkStatus?.message.isNullOrEmpty()

    /** The name to show in lists, preferring the guild nickname. */
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: username
}

/** Removal-trigger options for AFK clearing. */
enum class AfkRemovalType(val raw: Int, val label: String, val blurb: String) {
    SELF_DISABLE(1, "Self Disable", "Members must clear it themselves with .afk or .afkrm."),
    ON_MESSAGE(2, "On Message", "Cleared when the member sends a message."),
    ON_TYPE(3, "On Type", "Cleared when the member starts typing."),
    EITHER(4, "Either", "Cleared when the member types or sends a message.");

    companion object {
        /** Maps a wire value onto a removal type, defaulting to [EITHER]. */
        fun from(raw: Int): AfkRemovalType = entries.firstOrNull { it.raw == raw } ?: EITHER
    }
}

/** Parses and formats the AFK removal timeout string. */
object AfkTime {

    /** Renders a duration in seconds as a compact `1h2m3s` string. */
    fun secondsToString(seconds: Int): String {
        if (seconds <= 0) return "0s"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val out = buildString {
            if (hours > 0) append("${hours}h")
            if (minutes > 0) append("${minutes}m")
            if (secs > 0) append("${secs}s")
        }
        return out.ifEmpty { "0s" }
    }

    /** Parses a compact `1h2m3s` string back into seconds. */
    fun stringToSeconds(raw: String): Int {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty() || trimmed == "0s") return 0
        var total = 0
        var current = StringBuilder()
        trimmed.forEach { ch ->
            if (ch.isDigit()) {
                current.append(ch)
            } else {
                val value = current.toString().toIntOrNull()
                if (value != null) {
                    total += when (ch) {
                        'h' -> value * 3600
                        'm' -> value * 60
                        's' -> value
                        else -> 0
                    }
                }
                current = StringBuilder()
            }
        }
        return total
    }
}
