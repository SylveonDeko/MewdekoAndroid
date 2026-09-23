package dev.mewdeko.mobile.feature.xp

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import kotlinx.serialization.Serializable

/** The avatar, name, and icon shown for a member on their rank card. */
@Serializable
data class XpTemplateUser(
    val id: Int = 0,
    val textColor: String = "FF000000",
    val fontSize: Int = 50,
    val textX: Int = 120,
    val textY: Int = 70,
    val showText: Boolean = true,
    val iconX: Int = 27,
    val iconY: Int = 24,
    val iconSizeX: Int = 73,
    val iconSizeY: Int = 74,
    val showIcon: Boolean = true,
)

/** The XP progress bar's geometry, color, and transparency. */
@Serializable
data class XpTemplateBar(
    val id: Int = 0,
    val barColor: String = "FF000000",
    val barPointAx: Int = 319,
    val barPointAy: Int = 119,
    val barPointBx: Int = 284,
    val barPointBy: Int = 250,
    val barLength: Int = 452,
    val barTransparency: Int = 90,
    val barDirection: Int = 3,
    val showBar: Boolean = true,
)

/** The guild rank and guild level text placements. */
@Serializable
data class XpTemplateGuild(
    val id: Int = 0,
    val guildLevelColor: String = "FF000000",
    val guildLevelFontSize: Int = 27,
    val guildLevelX: Int = 42,
    val guildLevelY: Int = 206,
    val showGuildLevel: Boolean = true,
    val guildRankColor: String = "FF000000",
    val guildRankFontSize: Int = 25,
    val guildRankX: Int = 148,
    val guildRankY: Int = 211,
    val showGuildRank: Boolean = true,
)

/** Club icon and name placement, carried through unedited on mobile. */
@Serializable
data class XpTemplateClub(
    val id: Int = 0,
    val clubIconX: Int = 717,
    val clubIconY: Int = 37,
    val clubIconSizeX: Int = 49,
    val clubIconSizeY: Int = 49,
    val showClubIcon: Boolean = true,
    val clubNameColor: String = "FF000000",
    val clubNameFontSize: Int = 32,
    val clubNameX: Int = 649,
    val clubNameY: Int = 50,
    val showClubName: Boolean = true,
)

/**
 * A user-drawn element layered onto the rank card, decoded from the
 * guild's `customElementsJson`.
 *
 * Mirrors the shape the dashboard's template editors read and write; every
 * field is optional server-side, so a full default set keeps decoding of an
 * older or partially populated element safe.
 */
@Serializable
data class XpCustomElement(
    val id: String = "",
    val type: String = "rectangle",
    val label: String = "Element",
    val visible: Boolean = true,
    val zIndex: Int = 0,
    val x: Double = 80.0,
    val y: Double = 80.0,
    val width: Double = 140.0,
    val height: Double = 64.0,
    val rotation: Double = 0.0,
    val opacity: Double = 1.0,
    val cornerRadius: Double = 0.0,
    val fill: String = "#5865F2",
    val stroke: String = "#00000000",
    val strokeWidth: Double = 0.0,
    val text: String = "",
    val fontSize: Double = 24.0,
    val textAlign: String = "left",
    val url: String = "",
    val gradientEnd: String = "",
    val gradientAngle: Double = 0.0,
    val shadowColor: String = "#00000080",
    val shadowBlur: Double = 0.0,
    val shadowX: Double = 0.0,
    val shadowY: Double = 4.0,
    val progressStyle: String = "rounded",
    val trackFill: String = "#FFFFFF30",
    val segments: Int = 10,
)

/** The kinds of [XpCustomElement] the mobile editor can add. */
enum class XpCustomElementType(val raw: String, val label: String) {
    RECTANGLE("rectangle", "Rectangle"),
    ELLIPSE("ellipse", "Ellipse"),
    LINE("line", "Line"),
    TEXT("text", "Text"),
    IMAGE("image", "Image"),
    PROGRESS("progress", "Progress bar"),
}

/** The full rank card template for a guild. */
@Serializable
data class XpTemplate(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val outputSizeX: Int = 797,
    val outputSizeY: Int = 279,
    val timeOnLevelFormat: String = "{0}d{1}h{2}m",
    val timeOnLevelX: Int = 50,
    val timeOnLevelY: Int = 204,
    val timeOnLevelFontSize: Int = 20,
    val timeOnLevelColor: String = "FF000000",
    val showTimeOnLevel: Boolean = true,
    val awardedX: Int = 445,
    val awardedY: Int = 347,
    val awardedFontSize: Int = 25,
    val awardedColor: String = "ffffffff",
    val showAwarded: Boolean = false,
    val templateUserId: Int = 0,
    val templateGuildId: Int = 0,
    val templateClubId: Int = 0,
    val templateBarId: Int = 0,
    val customElementsJson: String? = null,
    val builtInOrderJson: String? = null,
    val templateUser: XpTemplateUser = XpTemplateUser(),
    val templateBar: XpTemplateBar = XpTemplateBar(),
    val templateGuild: XpTemplateGuild = XpTemplateGuild(),
    val templateClub: XpTemplateClub = XpTemplateClub(),
)

/** The ids of every built-in rank card element, in their default draw order. */
val DefaultBuiltInOrder = listOf(
    "user-text", "guild-level", "progress-bar", "awarded", "guild-rank", "time-on-level", "user-icon",
)

/** Human labels for the built-in element ids used in [DefaultBuiltInOrder]. */
val BuiltInElementLabels = mapOf(
    "user-text" to "Username",
    "guild-level" to "Guild level",
    "progress-bar" to "XP progress bar",
    "awarded" to "Awarded XP",
    "guild-rank" to "Guild rank",
    "time-on-level" to "Time on level",
    "user-icon" to "User avatar",
)
