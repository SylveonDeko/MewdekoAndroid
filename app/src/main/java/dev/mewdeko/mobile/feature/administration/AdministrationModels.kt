package dev.mewdeko.mobile.feature.administration

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Anti-raid's current configuration and hit counter. */
@Serializable
data class AntiRaidSummary(
    val enabled: Boolean = false,
    val userThreshold: Int = 0,
    val seconds: Int = 0,
    val action: Int = 0,
    val punishDuration: Int = 0,
    val usersCount: Int = 0,
)

/** Anti-spam's current configuration and hit counter. */
@Serializable
data class AntiSpamSummary(
    val enabled: Boolean = false,
    val messageThreshold: Int = 0,
    val action: Int = 0,
    val muteTime: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val ignoredChannels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val userCount: Int = 0,
)

/** Anti-alt's current configuration and hit counter. */
@Serializable
data class AntiAltSummary(
    val enabled: Boolean = false,
    val minAge: String = "",
    val action: Int = 0,
    val actionDuration: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val counter: Int = 0,
)

/** Anti-mass-mention's current configuration and hit counter. */
@Serializable
data class AntiMassMentionSummary(
    val enabled: Boolean = false,
    val mentionThreshold: Int = 0,
    val maxMentionsInTimeWindow: Int = 0,
    val timeWindowSeconds: Int = 0,
    val action: Int = 0,
    val muteTime: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val ignoreBots: Boolean = false,
    val userCount: Int = 0,
)

/** Anti-mass-post's current configuration and hit counter. */
@Serializable
data class AntiMassPostSummary(
    val enabled: Boolean = false,
    val channelThreshold: Int = 3,
    val timeWindowSeconds: Int = 60,
    val contentSimilarityThreshold: Double = 0.8,
    val minContentLength: Int = 20,
    val checkLinksOnly: Boolean = true,
    val checkDuplicateContent: Boolean = true,
    val requireIdenticalContent: Boolean = false,
    val caseSensitive: Boolean = false,
    val deleteMessages: Boolean = true,
    val notifyUser: Boolean = true,
    val action: Int = 0,
    val punishDuration: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val ignoreBots: Boolean = true,
    val maxMessagesTracked: Int = 50,
    val userCount: Int = 0,
    val counter: Int = 0,
)

/** Anti-pattern's current configuration and hit counter. */
@Serializable
data class AntiPatternSummary(
    val enabled: Boolean = false,
    val action: Int = 0,
    val punishDuration: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val checkAccountAge: Boolean = false,
    val maxAccountAgeMonths: Int = 6,
    val checkJoinTiming: Boolean = false,
    val maxJoinHours: Double = 48.0,
    val checkBatchCreation: Boolean = false,
    val checkOfflineStatus: Boolean = false,
    val checkNewAccounts: Boolean = false,
    val newAccountDays: Int = 7,
    val minimumScore: Int = 15,
    val patternCount: Int = 0,
    val counter: Int = 0,
)

/** Anti-post-channel (honeypot) current configuration and hit counter. */
@Serializable
data class AntiPostChannelSummary(
    val enabled: Boolean = false,
    val action: Int = 0,
    val deleteMessages: Boolean = true,
    val notifyUser: Boolean = true,
    val punishDuration: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val ignoreBots: Boolean = true,
    val channelCount: Int = 0,
    val channels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val ignoredRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val ignoredUsers: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val counter: Int = 0,
)

/** Anti-image-hash's current configuration and hit counter. */
@Serializable
data class AntiImageHashSummary(
    val enabled: Boolean = false,
    val action: Int = 2,
    val punishDuration: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val hashThreshold: Int = 31,
    val deleteMessages: Boolean = true,
    val notifyUser: Boolean = true,
    val ignoreBots: Boolean = true,
    val checkEmbeds: Boolean = true,
    val checkBorders: Boolean = true,
    val usePresetList: Boolean = false,
    val presetTriggers: Int = 0,
    val presetCount: Int = 0,
    val maxImageSizeMb: Int = 8,
    val hashCount: Int = 0,
    val ignoredRoles: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val ignoredChannels: List<@Serializable(with = SnowflakeSerializer::class) Snowflake> = emptyList(),
    val counter: Int = 0,
)

/** Wraps the `Protection/{g}/status` response just far enough to pull the image-hash block out of it. */
@Serializable
data class ImageHashStatusWrapper(
    val antiImageHash: AntiImageHashSummary = AntiImageHashSummary(),
)

/** Every protection module's state in one payload, as returned by `Administration/{g}/protection/status`. */
@Serializable
data class ProtectionStatusDetail(
    val antiRaid: AntiRaidSummary = AntiRaidSummary(),
    val antiSpam: AntiSpamSummary = AntiSpamSummary(),
    val antiAlt: AntiAltSummary = AntiAltSummary(),
    val antiMassMention: AntiMassMentionSummary = AntiMassMentionSummary(),
    val antiMassPost: AntiMassPostSummary = AntiMassPostSummary(),
    val antiPattern: AntiPatternSummary = AntiPatternSummary(),
    val antiPostChannel: AntiPostChannelSummary = AntiPostChannelSummary(),
)

/** What a protection module does to an offender. */
enum class AntiPunishmentAction(val raw: Int, val label: String) {
    MUTE(0, "Mute"),
    KICK(1, "Kick"),
    BAN(2, "Ban"),
    SOFTBAN(3, "Soft-ban"),
    REMOVE_ROLES(4, "Remove roles"),
    CHAT_MUTE(5, "Chat mute"),
    VOICE_MUTE(6, "Voice mute"),
    ADD_ROLE(7, "Add role"),
    DELETE(8, "Delete"),
    WARN(9, "Warn"),
    TIMEOUT(10, "Timeout"),
    NONE(11, "None");

    companion object {
        /** Maps a wire value onto an action, defaulting to [MUTE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: MUTE
    }
}

/** A role granted while a member sits in a particular voice channel. */
@Serializable
data class VoiceChannelRoleEntry(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val roleName: String = "",
)

/** One timezone the server can be set to. */
@Serializable
data class GuildTimezoneEntry(
    val id: String = "",
    val displayName: String = "",
    val offset: String = "",
)

/** A regex pattern anti-pattern checks usernames and display names against. */
@Serializable
data class AntiPatternPatternEntry(
    val id: Int = 0,
    val name: String? = null,
    val pattern: String = "",
    val checkUsername: Boolean = true,
    val checkDisplayName: Boolean = true,
)

/** A single blocked image in the anti-image-hash list. */
@Serializable
data class BannedImageHashEntry(
    val id: Int = 0,
    val hash: String = "",
    val variants: String? = null,
    val quality: Int = 0,
    val name: String? = null,
    val sourceUrl: String? = null,
    val action: Int? = null,
    val punishDuration: Int? = null,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val hitCount: Int = 0,
)

/** The result of hashing an image without blocking it yet. */
@Serializable
data class ImageHashPreview(
    val hash: String? = null,
    val quality: Int = 0,
    val reliable: Boolean = false,
    val minQuality: Int = 0,
)

/** A single self-assignable role, flattened out of the tuple the bot returns. */
data class SelfAssignableRoleEntry(
    val roleId: Snowflake,
    val roleName: String,
    val group: Int,
    val levelRequirement: Int,
)

/** The self-assignable roles feature's full state: exclusivity, roles, and named groups. */
data class SelfAssignableRolesPayload(
    val exclusive: Boolean = false,
    val roles: List<SelfAssignableRoleEntry> = emptyList(),
    val groups: Map<Int, String> = emptyMap(),
)

/** One emoji-to-role pair inside a reaction role setup. */
@Serializable
data class ReactionRoleEntry(
    val emoteName: String = "",
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
)

/** A message whose reactions grant roles. */
@Serializable
data class ReactionRoleMessageEntry(
    val index: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val exclusive: Boolean = false,
    val reactionRoles: List<ReactionRoleEntry> = emptyList(),
)

/** The `reaction-roles` GET response. */
@Serializable
data class ReactionRolesResponse(
    val success: Boolean = false,
    val reactionRoles: List<ReactionRoleMessageEntry> = emptyList(),
)

/** A per-guild command cooldown. */
@Serializable
data class CommandCooldownEntry(
    val commandName: String? = null,
    val seconds: Int = 0,
)

/** A command whose required Discord permission has been overridden. */
@Serializable
data class PermissionOverrideEntry(
    val command: String = "",
    val permission: String = "",
)

/** One ordered rule in the permissions manager. */
@Serializable
data class PermissionRuleEntry(
    val id: Int = 0,
    val primaryTarget: Int = 3,
    @Serializable(with = SnowflakeSerializer::class) val primaryTargetId: Snowflake? = null,
    val secondaryTarget: Int = 2,
    val secondaryTargetName: String? = null,
    val isCustomCommand: Boolean = false,
    val state: Boolean = false,
    val index: Int = 0,
)

/** The permissions manager's full cached state for a guild. */
@Serializable
data class PermissionCachePayload(
    val permRole: String? = null,
    val verbose: Boolean = true,
    val permissions: List<PermissionRuleEntry> = emptyList(),
)

/** A bot command, as listed by the commands/modules endpoint. */
@Serializable
data class CommandInfo(
    val commandName: String = "",
    val description: String = "",
)

/** A command module and the commands inside it. */
@Serializable
data class ModuleInfo(
    val name: String = "",
    val commands: List<CommandInfo> = emptyList(),
)

/** Server recovery's current setup status. */
@Serializable
data class ServerRecoveryStatusPayload(
    val isSetup: Boolean = false,
    val recoveryKey: String? = null,
)

/** A per-channel override for "delete message on command". */
@Serializable
data class DeleteMessageChannelEntry(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val state: Boolean = false,
)

/** The full "delete message on command" configuration. */
@Serializable
data class DeleteMessageOnCommandPayload(
    val enabled: Boolean = false,
    val channels: List<DeleteMessageChannelEntry> = emptyList(),
)

/** Which protection module the edit sheet is configuring. */
enum class ProtectionEditor(val label: String) {
    RAID("Anti-raid"),
    SPAM("Anti-spam"),
    ALT("Anti-alt"),
    MASS_MENTION("Anti-mass-mention"),
}

/** Which part of the administration screen is showing. */
enum class AdminSection(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    PROTECTION("protection", "Protection"),
    ROLES("roles", "Roles"),
    AUTOMATION("automation", "Automation"),
    ADVANCED("advanced", "Advanced"),
}

/** The three states a per-channel "delete message on command" override can be in. */
enum class DeleteMsgState(val raw: Int, val label: String) {
    ENABLE(0, "Enable"),
    DISABLE(1, "Disable"),
    INHERIT(2, "Inherit");

    companion object {
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: INHERIT
    }
}

/**
 * Pulls a self-assignable roles payload out of the raw JSON the bot returns for
 * `self-assignable-roles`. The endpoint hands back a C# tuple `(Exclusive, Roles, GroupNames)`,
 * which serialises as an object whose fields carry the three values rather than the named shape a
 * plain model would expect, so this walks the tree defensively instead of decoding it directly.
 */
fun JsonElement.toSelfAssignableRolesPayload(): SelfAssignableRolesPayload {
    val root = this as? JsonObject ?: return SelfAssignableRolesPayload()

    val exclusive = root.values.firstNotNullOfOrNull { (it as? JsonPrimitive)?.booleanOrNull } ?: false

    val rolesArray = root.values.firstNotNullOfOrNull { it as? JsonArray } ?: JsonArray(emptyList())
    val roles = rolesArray.mapNotNull { it.toSelfAssignableRoleEntry() }

    val groupsObject = root.entries.firstOrNull { (_, value) ->
        value is JsonObject && value.values.isNotEmpty() && value.values.all { it is JsonPrimitive }
    }?.value as? JsonObject
    val groups = groupsObject
        ?.entries
        ?.mapNotNull { (key, value) ->
            val groupId = key.toIntOrNull() ?: return@mapNotNull null
            val name = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            groupId to name
        }
        ?.toMap()
        ?: emptyMap()

    return SelfAssignableRolesPayload(exclusive = exclusive, roles = roles, groups = groups)
}

/** Reads one `(SelfAssignableRole Model, IRole Role)` tuple element out of the roles array. */
private fun JsonElement.toSelfAssignableRoleEntry(): SelfAssignableRoleEntry? {
    val roleId = findFirstValue("roleId")?.contentOrNull?.takeIf { it.isNotBlank() && it != "0" }
        ?: return null
    val group = findFirstValue("group")?.intOrNull ?: 0
    val levelRequirement = findFirstValue("levelRequirement")?.intOrNull ?: 0
    val roleName = findFirstValue("name")?.contentOrNull ?: "Role $roleId"
    return SelfAssignableRoleEntry(
        roleId = roleId,
        roleName = roleName,
        group = group,
        levelRequirement = levelRequirement,
    )
}

/** Recursively finds the first object entry whose key matches [key], case-insensitively. */
private fun JsonElement.findFirstValue(key: String): JsonPrimitive? = when (this) {
    is JsonObject -> entries.firstOrNull { (k, _) -> k.equals(key, ignoreCase = true) }
        ?.value
        ?.let { it as? JsonPrimitive }
        ?: values.firstNotNullOfOrNull { it.findFirstValue(key) }

    is JsonArray -> firstNotNullOfOrNull { it.findFirstValue(key) }
    else -> null
}

/** One custom guild emoji, as offered by the emoji picker endpoint. */
@Serializable
data class EmojiInfo(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val animated: Boolean = false,
)

/** A guild's basic identity, as embedded in the emoji picker response. */
@Serializable
data class ClientGuildInfo(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
)

/** One guild's worth of custom emojis, as returned by `ClientOperations/emojis/{userId}`. */
@Serializable
data class GuildEmojiInfo(
    val guild: ClientGuildInfo = ClientGuildInfo(),
    val emojis: List<EmojiInfo> = emptyList(),
)

/** Discord's literal reaction-emote syntax for a custom guild emoji. */
fun EmojiInfo.toEmoteName(): String = "<${if (animated) "a" else ""}:$name:$id>"
