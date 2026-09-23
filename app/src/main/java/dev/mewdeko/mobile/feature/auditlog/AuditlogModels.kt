package dev.mewdeko.mobile.feature.auditlog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

/** The fixed page size the dashboard requests. */
const val AUDIT_LOG_PAGE_SIZE: Int = 50

/** Visual tone of an action badge, mirroring the dashboard's primary, secondary, and accent tones. */
enum class AuditTone { PRIMARY, SECONDARY, ACCENT }

/** The kind of dashboard activity recorded in an audit entry (bot `AuditAction`). */
enum class AuditAction(
    val value: Int,
    val label: String,
    val icon: ImageVector,
    val tone: AuditTone,
) {
    VIEW(0, "Viewed", Icons.Default.Visibility, AuditTone.SECONDARY),
    CREATE(1, "Created", Icons.Default.Add, AuditTone.PRIMARY),
    UPDATE(2, "Updated", Icons.Default.Edit, AuditTone.PRIMARY),
    DELETE(3, "Deleted", Icons.Default.Delete, AuditTone.ACCENT),
    ACCESS(4, "Accessed", Icons.AutoMirrored.Filled.Login, AuditTone.SECONDARY);

    companion object {
        /** Resolves the raw numeric action, or `null` for values this build does not know. */
        fun fromValue(value: Int): AuditAction? = entries.firstOrNull { it.value == value }
    }
}

/** One page of audit entries returned by `GET api/AuditLog/{guildId}`. */
@Serializable
data class AuditLogPage(
    val items: List<AuditLogEntry> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = AUDIT_LOG_PAGE_SIZE,
)

/** A single dashboard audit log entry (bot `AuditLogEntryResponse`). */
@Serializable
data class AuditLogEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val userName: String = "",
    val action: Int = 0,
    val section: String = "",
    val endpoint: String = "",
    val httpMethod: String = "",
    val changes: JsonElement? = null,
    val userAgent: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The decoded action, when known. */
    val auditAction: AuditAction? get() = AuditAction.fromValue(action)

    /** The name to show for the acting user, falling back to their id. */
    val displayUser: String get() = userName.ifBlank { userId }

    /**
     * The resource path the request hit, with the method prefix and the
     * leading `botapi/` segment stripped.
     */
    val endpointPath: String
        get() {
            var path = endpoint
            val space = path.indexOf(' ')
            if (space != -1) path = path.substring(space + 1)
            return path
                .replace(BotApiPrefix, "")
                .removePrefix("/")
        }
}

/** One readable field change inside an entry's details. */
data class AuditChangeRow(
    val label: String,
    val before: String?,
    val after: String,
)

/** Matches the leading `/botapi/` segment of a recorded endpoint. */
private val BotApiPrefix = Regex("^/?botapi/", RegexOption.IGNORE_CASE)

/** Friendly display names for each bot controller, matching the dashboard. */
private val SectionLabels: Map<String, String> = mapOf(
    "Administration" to "Administration",
    "Afk" to "AFK System",
    "AuditLog" to "Audit Log",
    "Birthday" to "Birthdays",
    "BotConfig" to "Bot Config",
    "BotStatus" to "Bot Status",
    "Chat" to "Chat Saver",
    "ChatTriggers" to "Triggers",
    "ClientOperations" to "Client Operations",
    "Confessions" to "Confessions",
    "Counting" to "Counting",
    "CustomVoice" to "Custom Voice",
    "Feeds" to "Feeds",
    "Filter" to "Word Filter",
    "Forms" to "Forms",
    "Giveaways" to "Giveaways",
    "Guild" to "Server",
    "GuildConfig" to "Server Settings",
    "Highlights" to "Highlights",
    "InstanceManagement" to "Instance Management",
    "InviteTracking" to "Invites",
    "JoinLeave" to "Join / Leave",
    "LastFm" to "Last.fm",
    "Logging" to "Logging",
    "Me" to "Account",
    "MessageCount" to "Message Stats",
    "Minecraft" to "Minecraft",
    "MinecraftBridge" to "Minecraft Bridge",
    "Moderation" to "Moderation",
    "MultiGreets" to "Greets",
    "Music" to "Music",
    "Ownership" to "Ownership",
    "Patreon" to "Patreon",
    "Performance" to "Performance",
    "Permissions" to "Permissions",
    "Poll" to "Polls",
    "Protection" to "Protection",
    "Repeaters" to "Repeaters",
    "Reputation" to "Reputation",
    "Reviews" to "Reviews",
    "RoleGreet" to "Role Greets",
    "RoleStates" to "Role States",
    "Starboard" to "Starboard",
    "StatChannel" to "Stat Channels",
    "StatusRoles" to "Status Roles",
    "StreamNotifications" to "Streams",
    "Suggestions" to "Suggestions",
    "SystemInfo" to "System Info",
    "Ticket" to "Tickets",
    "Todo" to "Todo Lists",
    "Votes" to "Votes",
    "Webhook" to "Webhooks",
    "Wizard" to "Setup Wizard",
    "Xp" to "XP System",
)

/** The friendly label for a controller section, falling back to a humanized name. */
fun auditSectionLabel(section: String): String = SectionLabels[section] ?: humanizeAuditKey(section)

/**
 * Turns a raw property name (PascalCase, camelCase, or snake_case) into a
 * spaced, capitalised label.
 */
fun humanizeAuditKey(key: String): String = key
    .replace(Regex("([a-z0-9])([A-Z])"), "\$1 \$2")
    .replace(Regex("[_-]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .replaceFirstChar { it.uppercaseChar() }

/** Renders a raw JSON value as something a non-technical user can read. */
fun humanizeAuditValue(value: JsonElement?): String = when (value) {
    null, JsonNull -> "none"
    is JsonPrimitive -> when {
        value.isString -> value.content.ifEmpty { "empty" }
        value.booleanOrNull != null -> if (value.booleanOrNull == true) "On" else "Off"
        else -> value.content
    }
    is JsonArray -> if (value.isEmpty()) "none" else "${value.size} item${if (value.size == 1) "" else "s"}"
    is JsonObject -> value.toString()
}

/**
 * Reads [key] from the object, also trying the lower-camel form because the
 * API client lowercases the first letter of every key while `changed` keeps
 * the original casing.
 */
private fun JsonObject.lookup(key: String): JsonElement? =
    this[key] ?: this[key.replaceFirstChar { it.lowercaseChar() }]

/**
 * Flattens a bare request body into readable rows, unwrapping the single
 * controller-argument layer (for example `{ request: { ... } }`).
 */
private fun flattenAuditSet(obj: JsonObject): List<AuditChangeRow> = obj.entries.flatMap { (key, value) ->
    if (value is JsonObject) {
        value.entries.map { (innerKey, innerValue) ->
            AuditChangeRow(humanizeAuditKey(innerKey), null, humanizeAuditValue(innerValue))
        }
    } else {
        listOf(AuditChangeRow(humanizeAuditKey(key), null, humanizeAuditValue(value)))
    }
}

/**
 * Turns the raw before/after change document into a flat list of readable
 * field changes. Uses the `changed` key list when present, otherwise the
 * union of keys on both sides.
 */
fun summarizeAuditChanges(changes: JsonElement?): List<AuditChangeRow> {
    val set = changes as? JsonObject ?: return emptyList()
    val before = set.lookup("before")
    val after = set.lookup("after")
    val changedKeys = (set.lookup("changed") as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

    if (before is JsonObject && after is JsonObject) {
        val keys = changedKeys ?: LinkedHashSet(before.keys + after.keys).toList()
        return keys.map { key ->
            AuditChangeRow(
                label = humanizeAuditKey(key),
                before = humanizeAuditValue(before.lookup(key)),
                after = humanizeAuditValue(after.lookup(key)),
            )
        }
    }

    if (before != null && after != null) {
        return listOf(AuditChangeRow("Value", humanizeAuditValue(before), humanizeAuditValue(after)))
    }

    if (after is JsonObject) return flattenAuditSet(after)
    if (after != null) return listOf(AuditChangeRow("Value", null, humanizeAuditValue(after)))
    return emptyList()
}
