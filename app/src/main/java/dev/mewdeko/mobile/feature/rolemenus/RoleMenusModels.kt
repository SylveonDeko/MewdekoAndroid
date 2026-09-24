package dev.mewdeko.mobile.feature.rolemenus

import androidx.compose.ui.graphics.Color
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/** Whether a menu shows one dropdown or a row of buttons. */
enum class RoleMenuStyle(val value: Int, val label: String, val blurb: String) {
    DROPDOWN(0, "Dropdown", "One tidy list with room for descriptions."),
    BUTTONS(1, "Buttons", "A button per role, five to a row.");

    companion object {
        /** Resolves a stored value, defaulting to the dropdown. */
        fun from(value: Int): RoleMenuStyle = entries.firstOrNull { it.value == value } ?: DROPDOWN
    }
}

/** Whether members can hold several roles from a menu or only one. */
enum class RoleMenuMode(val value: Int, val label: String, val blurb: String) {
    PICK_ANY(0, "Pick any", "Members turn each role on or off."),
    PICK_ONE(1, "Pick one", "Choosing a role swaps out the one they had.");

    companion object {
        /** Resolves a stored value, defaulting to pick any. */
        fun from(value: Int): RoleMenuMode = entries.firstOrNull { it.value == value } ?: PICK_ANY
    }
}

/** Whether members get a private note listing what changed. */
enum class RoleMenuReplyMode(val value: Int, val label: String) {
    PRIVATE(0, "Private"),
    SILENT(1, "Silent");

    companion object {
        /** Resolves a stored value, defaulting to private. */
        fun from(value: Int): RoleMenuReplyMode = entries.firstOrNull { it.value == value } ?: PRIVATE
    }
}

/** The Discord button colors a buttons menu can use, with the shared preview colors. */
enum class RoleMenuButtonColor(val value: Int, val label: String, val color: Color) {
    BLURPLE(1, "Blurple", Color(0xFF5865F2)),
    GREY(2, "Grey", Color(0xFF4E5058)),
    GREEN(3, "Green", Color(0xFF248046)),
    RED(4, "Red", Color(0xFFDA373C));

    companion object {
        /** Resolves a stored value, treating anything outside 1 to 4 as grey like the bot does. */
        fun from(value: Int): RoleMenuButtonColor = entries.firstOrNull { it.value == value } ?: GREY
    }
}

/** Where a menu's message stands in Discord. */
enum class RoleMenuStatus(val key: String, val label: String) {
    LIVE("live", "Live"),
    PAUSED("paused", "Paused"),
    NOT_POSTED("not_posted", "Not posted"),
    CHANNEL_MISSING("channel_missing", "Channel missing");

    companion object {
        /** Resolves the bot's status key, defaulting to live. */
        fun from(key: String): RoleMenuStatus = entries.firstOrNull { it.key == key } ?: LIVE
    }
}

/** Size limits and default texts shared with the bot and the other clients. */
object RoleMenuLimits {
    /** Most menus per server. */
    const val MaxMenus = 50

    /** Most options per menu. */
    const val MaxOptions = 25

    /** Buttons per row. */
    const val ButtonsPerRow = 5

    /** Longest menu name. */
    const val NameLength = 100

    /** Longest option name. */
    const val LabelLength = 80

    /** Longest option description. */
    const val DescriptionLength = 100

    /** Longest dropdown hint text. */
    const val PlaceholderLength = 150

    /** Name used when a menu's name is left blank. */
    const val DefaultName = "Pick your roles"

    /** First line of the default message. */
    const val DefaultDescription = "Use the menu below to pick your roles."

    /** Dropdown hint text for a pick one menu. */
    const val DefaultPlaceholderOne = "Choose a role"

    /** Dropdown hint text for a pick any menu. */
    const val DefaultPlaceholderAny = "Choose roles to add or remove"

    /** The default dropdown hint text for [mode]. */
    fun defaultPlaceholder(mode: RoleMenuMode): String =
        if (mode == RoleMenuMode.PICK_ONE) DefaultPlaceholderOne else DefaultPlaceholderAny

    /**
     * Clamps a menu's limits the way the bot does: pick one gives a maximum
     * of 1 and a minimum of 0 or 1; pick any clamps the maximum to 0 through
     * [optionCount] and the minimum to 0 through the maximum, or through
     * [optionCount] when there is no maximum.
     */
    fun clamp(mode: RoleMenuMode, min: Int, max: Int, optionCount: Int): Pair<Int, Int> {
        if (mode == RoleMenuMode.PICK_ONE) return (if (min >= 1) 1 else 0) to 1
        val count = optionCount.coerceAtLeast(0)
        val clampedMax = max.coerceIn(0, count)
        val clampedMin = min.coerceIn(0, if (clampedMax == 0) count else clampedMax)
        return clampedMin to clampedMax
    }
}

/** One option on a role menu, as returned by the bot. */
@Serializable
data class RoleMenuOption(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val roleName: String? = null,
    val roleColor: Long = 0,
    val label: String = "",
    val emoji: String? = null,
    val description: String? = null,
    val buttonStyle: Int = 2,
    val position: Int = 0,
    val problem: String? = null,
)

/** A role menu with its options and posting state, from `GET api/rolemenus/{guildId}`. */
@Serializable
data class RoleMenu(
    val id: Int = 0,
    val name: String = "",
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    val jumpUrl: String? = null,
    val message: String? = null,
    val style: Int = 0,
    val placeholder: String? = null,
    val mode: Int = 0,
    val minRoles: Int = 0,
    val maxRoles: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val requiredRoleId: Snowflake? = null,
    val replyMode: Int = 0,
    val enabled: Boolean = true,
    val status: String = "live",
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateModified: Instant? = null,
    val options: List<RoleMenuOption> = emptyList(),
) {
    /** The typed posting state. */
    val statusKind: RoleMenuStatus get() = RoleMenuStatus.from(status)

    /** The channel as "#name", or "Deleted channel" when it is gone. */
    val channelLabel: String get() = channelName?.let { "#$it" } ?: "Deleted channel"
}

/** A channel the menu editor can offer. */
@Serializable
data class LookupChannel(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val categoryName: String? = null,
    val position: Int = 0,
    val canPost: Boolean = false,
    val problem: String? = null,
)

/** A role the menu editor can offer. */
@Serializable
data class LookupRole(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val color: Long = 0,
    val position: Int = 0,
    val assignable: Boolean = false,
    val problem: String? = null,
)

/** A custom emoji from this server that an option can use. */
@Serializable
data class LookupEmoji(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val animated: Boolean = false,
    val formatted: String = "",
    val url: String = "",
)

/** Size limits the bot reports with the lookups. */
@Serializable
data class LookupLimits(
    val maxMenus: Int = RoleMenuLimits.MaxMenus,
    val maxOptions: Int = RoleMenuLimits.MaxOptions,
    val buttonsPerRow: Int = RoleMenuLimits.ButtonsPerRow,
    val nameLength: Int = RoleMenuLimits.NameLength,
    val labelLength: Int = RoleMenuLimits.LabelLength,
    val descriptionLength: Int = RoleMenuLimits.DescriptionLength,
    val placeholderLength: Int = RoleMenuLimits.PlaceholderLength,
)

/** Channels, roles, and emojis for the editor, from `GET api/rolemenus/{guildId}/lookups`. */
@Serializable
data class RoleMenuLookups(
    val channels: List<LookupChannel> = emptyList(),
    val roles: List<LookupRole> = emptyList(),
    val emojis: List<LookupEmoji> = emptyList(),
    val botCanManageRoles: Boolean = true,
    val menuCount: Int = 0,
    val limits: LookupLimits = LookupLimits(),
)

/** One emoji and role pair in an older emoji role setup. */
@Serializable
data class RoleMenuImportPair(
    val emoji: String = "",
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake = "",
    val roleName: String? = null,
    val roleExists: Boolean = false,
)

/** An older emoji role setup that can be moved into a role menu. */
@Serializable
data class RoleMenuImportSource(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    val channelName: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake = "",
    val jumpUrl: String = "",
    val exclusive: Boolean = false,
    val pairs: List<RoleMenuImportPair> = emptyList(),
)

/** One editable option in the menu editor. [key] is a stable local identity; [id] is the server's. */
data class RoleMenuOptionDraft(
    val key: String = UUID.randomUUID().toString(),
    val id: Int? = null,
    val roleId: Snowflake = "",
    val label: String = "",
    val emoji: String = "",
    val description: String = "",
    val buttonStyle: Int = RoleMenuButtonColor.GREY.value,
    val problem: String? = null,
) {
    companion object {
        /** Builds a draft from a saved option, keeping its server id. */
        fun from(option: RoleMenuOption) = RoleMenuOptionDraft(
            id = option.id.takeIf { it > 0 },
            roleId = option.roleId,
            label = option.label,
            emoji = option.emoji.orEmpty(),
            description = option.description.orEmpty(),
            buttonStyle = RoleMenuButtonColor.from(option.buttonStyle).value,
            problem = option.problem,
        )
    }
}

/** The whole menu being created or edited. [id] is null for a new menu. */
data class RoleMenuDraft(
    val id: Int? = null,
    val name: String = "",
    val channelId: Snowflake? = null,
    val message: EmbedMessage = EmbedMessage(),
    val style: RoleMenuStyle = RoleMenuStyle.DROPDOWN,
    val placeholder: String = "",
    val mode: RoleMenuMode = RoleMenuMode.PICK_ANY,
    val minRoles: Int = 0,
    val maxRoles: Int = 0,
    val requiredRoleId: Snowflake? = null,
    val replyMode: RoleMenuReplyMode = RoleMenuReplyMode.PRIVATE,
    val enabled: Boolean = true,
    val options: List<RoleMenuOptionDraft> = emptyList(),
) {
    /** Whether this draft edits a saved menu. */
    val isEditing: Boolean get() = id != null

    /** Returns a copy with its limits clamped for the current mode and option count. */
    fun clamped(): RoleMenuDraft {
        val (min, max) = RoleMenuLimits.clamp(mode, minRoles, maxRoles, options.size)
        return copy(minRoles = min, maxRoles = max)
    }

    /** Why the draft can't be saved yet, first failing rule first, or null when it can. */
    val invalidReason: String?
        get() = when {
            channelId.isNullOrBlank() -> "Pick a channel"
            options.isEmpty() -> "Add at least one option"
            options.any { it.roleId.isBlank() } -> "Every option needs a role"
            options.map { it.roleId }.toSet().size != options.size -> "Each role can only be on a menu once"
            options.any { it.label.trim().length > RoleMenuLimits.LabelLength } -> "Names can be up to 80 characters"
            else -> null
        }

    companion object {
        /** Builds a draft from a saved menu. */
        fun from(menu: RoleMenu): RoleMenuDraft = RoleMenuDraft(
            id = menu.id,
            name = menu.name,
            channelId = menu.channelId.takeIf { it.isNotBlank() && it != "0" },
            message = EmbedMessage.parse(menu.message).copy(components = emptyList()),
            style = RoleMenuStyle.from(menu.style),
            placeholder = menu.placeholder.orEmpty(),
            mode = RoleMenuMode.from(menu.mode),
            minRoles = menu.minRoles,
            maxRoles = menu.maxRoles,
            requiredRoleId = menu.requiredRoleId?.takeIf { it.isNotBlank() && it != "0" },
            replyMode = RoleMenuReplyMode.from(menu.replyMode),
            enabled = menu.enabled,
            options = menu.options.sortedBy { it.position }.map(RoleMenuOptionDraft::from),
        )
    }
}

/** One option as the preview shows it: the name on the menu, its emoji, and its description. */
data class RoleMenuPreviewOption(
    val label: String,
    val emoji: String,
    val description: String,
    val buttonColor: RoleMenuButtonColor,
)

/**
 * The message the bot posts when a menu has none of its own: the menu name
 * as the title, and the intro line followed by one line per option.
 */
fun buildDefaultRoleMenuMessage(name: String, options: List<RoleMenuPreviewOption>): EmbedMessage {
    val lines = options.joinToString("\n") { option ->
        (if (option.emoji.isNotBlank()) option.emoji + " " else "") + "**" + option.label + "**" +
            (if (option.description.isNotBlank()) ": " + option.description else "")
    }
    return EmbedMessage(
        embeds = listOf(
            EmbedSpec(
                title = name.trim().ifEmpty { RoleMenuLimits.DefaultName },
                description = RoleMenuLimits.DefaultDescription + "\n\n" + lines,
            )
        )
    )
}

/** A custom Discord emoji parsed out of its "<:name:id>" or "<a:name:id>" text. */
data class CustomEmojiRef(val name: String, val id: String, val animated: Boolean) {
    /** The CDN image for this emoji at preview size. */
    val url: String get() = "https://cdn.discordapp.com/emojis/$id.${if (animated) "gif" else "png"}?size=48"

    companion object {
        private val pattern = Regex("^<(a?):([A-Za-z0-9_~]+):(\\d+)>$")

        /** Parses [raw], or returns null when it is not custom emoji text. */
        fun parse(raw: String): CustomEmojiRef? {
            val match = pattern.matchEntire(raw.trim()) ?: return null
            val (animated, name, id) = match.destructured
            return CustomEmojiRef(name, id, animated == "a")
        }
    }
}
