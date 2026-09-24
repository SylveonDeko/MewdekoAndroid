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
    val dateAdded: String? = null,
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
    val dateAdded: String? = null,
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
    val dateAdded: String? = null,
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
    val dateAdded: String? = null,
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

/**
 * The kinds of [XpCustomElement] the mobile editor can add. [label] is the
 * new layer's default label, matching both web editors.
 */
enum class XpCustomElementType(val raw: String, val label: String, val title: String) {
    RECTANGLE("rectangle", "Rectangle", "Rectangle"),
    ELLIPSE("ellipse", "Ellipse", "Ellipse"),
    LINE("line", "Line", "Line"),
    TEXT("text", "Custom text", "Text"),
    IMAGE("image", "Image", "Image"),
    PROGRESS("progress", "XP progress", "Progress"),
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
    val dateAdded: String? = null,
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

/**
 * The club layers the template stores but the bot's renderer never reads.
 * They are shown as inactive layers so they can still be positioned, and are
 * never written into `builtInOrderJson`.
 */
val ClubBuiltInIds = listOf("club-icon", "club-name")

/** Human labels for every built-in element id, including the dormant club layers. */
val BuiltInElementLabels = mapOf(
    "user-text" to "Username",
    "guild-level" to "Guild Level",
    "progress-bar" to "XP Progress Bar",
    "awarded" to "Awarded XP",
    "guild-rank" to "Guild Rank",
    "time-on-level" to "Time on Level",
    "user-icon" to "User Avatar",
    "club-icon" to "Club Icon",
    "club-name" to "Club Name",
)

/** The built-in ids drawn as text with the common baseline rule. */
val BuiltInTextIds = setOf("user-text", "guild-rank", "guild-level", "time-on-level", "awarded", "club-name")

/** The built-in ids drawn as a rectangular image. */
val BuiltInImageIds = setOf("user-icon", "club-icon")

/**
 * The bot's order sanitisation: saved ids that are in [DefaultBuiltInOrder]
 * keep their saved order (de-duplicated), then every missing default is
 * appended in default order. Unknown ids, including the club layers, drop.
 */
fun sanitizeBuiltInOrder(saved: List<String>?): List<String> {
    if (saved.isNullOrEmpty()) return DefaultBuiltInOrder
    val known = saved.filter { it in DefaultBuiltInOrder }.distinct()
    return known + DefaultBuiltInOrder.filterNot { it in known }
}

/**
 * How long a member has held their current level.
 *
 * The bot exposes a C# value tuple, which may arrive as named `days`,
 * `hours`, `minutes` keys or as `item1`, `item2`, `item3`, so both spellings
 * are accepted and [normalized] picks whichever carries a value.
 */
@Serializable
data class XpTimeOnLevel(
    val days: Int = 0,
    val hours: Int = 0,
    val minutes: Int = 0,
    val item1: Int? = null,
    val item2: Int? = null,
    val item3: Int? = null,
) {
    /** The tuple with the `itemN` spelling folded into the named fields. */
    fun normalized(): XpTimeOnLevel = XpTimeOnLevel(
        days = if (days != 0) days else item1 ?: 0,
        hours = if (hours != 0) hours else item2 ?: 0,
        minutes = if (minutes != 0) minutes else item3 ?: 0,
    )
}

/**
 * Everything the card renderer substitutes into the template: the bot's
 * `FullUserStats` reduced to the fields the drawing rules read.
 */
data class XpCardData(
    val username: String,
    val displayName: String,
    val nickname: String,
    val avatarUrl: String?,
    val level: Int,
    val rank: Int,
    val totalXp: Long,
    val levelXp: Long,
    val requiredXp: Long,
    val bonusXp: Long,
    val timeOnLevel: XpTimeOnLevel,
    val clubName: String,
    val guildName: String,
    val userId: String,
    val guildId: String,
) {
    /** The bar and progress fraction, clamped the way the custom progress element clamps. */
    val progress: Float
        get() = if (requiredXp <= 0L) 1f else (levelXp.toFloat() / requiredXp.toFloat()).coerceIn(0f, 1f)

    companion object {
        /** The dashboard's sample member, extended with what the bot-accurate text needs. */
        fun sample(guildName: String, guildId: String, userId: String) = XpCardData(
            username = "QuantumViper42",
            displayName = "Quantum Viper",
            nickname = "Quantum Viper",
            avatarUrl = null,
            level = 47,
            rank = 12,
            totalXp = 234_567L,
            levelXp = 4_890L,
            requiredXp = 7_200L,
            bonusXp = 150L,
            timeOnLevel = XpTimeOnLevel(days = 2, hours = 8, minutes = 23),
            clubName = "Elite Gamers",
            guildName = guildName,
            userId = userId,
            guildId = guildId,
        )
    }
}

/**
 * The bot's `GetTimeSpent`: Humanizer's single largest unit with weeks as
 * the ceiling, followed by " ago". A zero span reads "no time ago".
 */
fun humanizeTimeOnLevel(time: XpTimeOnLevel): String {
    fun unit(value: Int, name: String) = if (value == 1) "1 $name" else "$value ${name}s"
    val text = when {
        time.days >= 7 -> unit(time.days / 7, "week")
        time.days > 0 -> unit(time.days, "day")
        time.hours > 0 -> unit(time.hours, "hour")
        time.minutes > 0 -> unit(time.minutes, "minute")
        else -> "no time"
    }
    return "$text ago"
}

/**
 * The bot's awarded XP string: `(+ N)` for a positive bonus, `(-N)` for a
 * negative one (the minus comes from the number itself).
 */
fun awardedXpText(bonusXp: Long): String =
    if (bonusXp > 0) "(+ $bonusXp)" else "($bonusXp)"

/** Resolves the `%xp.*%` placeholders the bot's `ResolveElementText` understands. */
fun resolveXpPlaceholders(text: String, data: XpCardData): String {
    val percent = if (data.requiredXp == 0L) 100.0 else data.levelXp * 100.0 / data.requiredXp
    val progress = String.format(java.util.Locale.US, "%.1f%%", percent)
    return text
        .replace("%xp.user%", data.username, ignoreCase = true)
        .replace("%xp.user.name%", data.username, ignoreCase = true)
        .replace("%xp.user.displayname%", data.displayName, ignoreCase = true)
        .replace("%xp.user.nickname%", data.nickname, ignoreCase = true)
        .replace("%xp.user.id%", data.userId, ignoreCase = true)
        .replace("%xp.level.current%", data.level.toString(), ignoreCase = true)
        .replace("%xp.level.next%", (data.level + 1).toString(), ignoreCase = true)
        .replace("%xp.total%", data.totalXp.toString(), ignoreCase = true)
        .replace("%xp.current%", data.levelXp.toString(), ignoreCase = true)
        .replace("%xp.needed%", data.requiredXp.toString(), ignoreCase = true)
        .replace("%xp.remaining%", maxOf(0L, data.requiredXp - data.levelXp).toString(), ignoreCase = true)
        .replace("%xp.progress%", progress, ignoreCase = true)
        .replace("%xp.rank%", data.rank.toString(), ignoreCase = true)
        .replace("%xp.guild%", data.guildName, ignoreCase = true)
        .replace("%xp.guild.name%", data.guildName, ignoreCase = true)
        .replace("%xp.guild.id%", data.guildId, ignoreCase = true)
}

/** The placeholder list shown under custom text fields. */
const val XpPlaceholderHelp: String =
    "Placeholders: %xp.user%, %xp.user.name%, %xp.user.displayname%, %xp.user.nickname%, " +
        "%xp.user.id%, %xp.level.current%, %xp.level.next%, %xp.total%, %xp.current%, " +
        "%xp.needed%, %xp.remaining%, %xp.progress%, %xp.rank%, %xp.guild%, %xp.guild.name%, " +
        "%xp.guild.id%"

/** A point in card units. */
data class XpPoint(val x: Float, val y: Float)

/**
 * The bot's `DrawXpBar` polygon for [bar] at [percent] progress: A, then A
 * and B each pushed along the fill direction by `barLength * percent`, then
 * B, closing the parallelogram.
 */
fun barPolygon(bar: XpTemplateBar, percent: Float): List<XpPoint> {
    val length = bar.barLength * percent
    val ax = bar.barPointAx.toFloat()
    val ay = bar.barPointAy.toFloat()
    val bx = bar.barPointBx.toFloat()
    val by = bar.barPointBy.toFloat()
    val (c, d) = when (bar.barDirection) {
        0 -> XpPoint(ax, ay - length) to XpPoint(bx, by - length)
        1 -> XpPoint(ax, ay + length) to XpPoint(bx, by + length)
        2 -> XpPoint(ax - length, ay) to XpPoint(bx - length, by)
        else -> XpPoint(ax + length, ay) to XpPoint(bx + length, by)
    }
    return listOf(XpPoint(ax, ay), c, d, XpPoint(bx, by))
}

/** Even-odd point in polygon test. */
fun pointInPolygon(px: Float, py: Float, polygon: List<XpPoint>): Boolean {
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val pi = polygon[i]
        val pj = polygon[j]
        val crosses = (pi.y > py) != (pj.y > py) &&
            px < (pj.x - pi.x) * (py - pi.y) / (pj.y - pi.y) + pi.x
        if (crosses) inside = !inside
        j = i
    }
    return inside
}

/** Distance from a point to the segment `a`-`b`. */
fun distanceToSegment(px: Float, py: Float, a: XpPoint, b: XpPoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((px - a.x) * dx + (py - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val cx = a.x + t * dx
    val cy = a.y + t * dy
    return kotlin.math.hypot(px - cx, py - cy)
}

/** Whether a point is inside the polygon or within [tolerance] of any of its edges. */
fun hitsPolygon(px: Float, py: Float, polygon: List<XpPoint>, tolerance: Float): Boolean {
    if (pointInPolygon(px, py, polygon)) return true
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[(i + 1) % polygon.size]
        if (distanceToSegment(px, py, a, b) <= tolerance) return true
    }
    return false
}

/**
 * The committed position rule: snapped to the grid when [snap] is on,
 * otherwise rounded to an integer because the template columns are ints.
 */
fun snapCoordinate(value: Float, snap: Boolean, gridSize: Int): Int {
    if (snap && gridSize > 0) return (Math.round(value / gridSize) * gridSize)
    return Math.round(value)
}

/** The placement, size, colour, and visibility of one built-in text element. */
data class XpTextSpec(
    val x: Int,
    val y: Int,
    val fontSize: Int,
    val color: String,
    val shown: Boolean,
)

/** The placement, size, and visibility of one built-in image element. */
data class XpBoxSpec(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val shown: Boolean,
)

/** The text fields of built-in element [id], or null when [id] is not a text element. */
fun XpTemplate.textSpec(id: String): XpTextSpec? = when (id) {
    "user-text" -> with(templateUser) { XpTextSpec(textX, textY, fontSize, textColor, showText) }
    "guild-rank" -> with(templateGuild) {
        XpTextSpec(guildRankX, guildRankY, guildRankFontSize, guildRankColor, showGuildRank)
    }
    "guild-level" -> with(templateGuild) {
        XpTextSpec(guildLevelX, guildLevelY, guildLevelFontSize, guildLevelColor, showGuildLevel)
    }
    "time-on-level" -> XpTextSpec(timeOnLevelX, timeOnLevelY, timeOnLevelFontSize, timeOnLevelColor, showTimeOnLevel)
    "awarded" -> XpTextSpec(awardedX, awardedY, awardedFontSize, awardedColor, showAwarded)
    "club-name" -> with(templateClub) {
        XpTextSpec(clubNameX, clubNameY, clubNameFontSize, clubNameColor, showClubName)
    }
    else -> null
}

/** Writes [spec] back into the columns of built-in text element [id]. */
fun XpTemplate.withTextSpec(id: String, spec: XpTextSpec): XpTemplate = when (id) {
    "user-text" -> copy(
        templateUser = templateUser.copy(
            textX = spec.x, textY = spec.y, fontSize = spec.fontSize, textColor = spec.color, showText = spec.shown,
        ),
    )
    "guild-rank" -> copy(
        templateGuild = templateGuild.copy(
            guildRankX = spec.x, guildRankY = spec.y, guildRankFontSize = spec.fontSize,
            guildRankColor = spec.color, showGuildRank = spec.shown,
        ),
    )
    "guild-level" -> copy(
        templateGuild = templateGuild.copy(
            guildLevelX = spec.x, guildLevelY = spec.y, guildLevelFontSize = spec.fontSize,
            guildLevelColor = spec.color, showGuildLevel = spec.shown,
        ),
    )
    "time-on-level" -> copy(
        timeOnLevelX = spec.x, timeOnLevelY = spec.y, timeOnLevelFontSize = spec.fontSize,
        timeOnLevelColor = spec.color, showTimeOnLevel = spec.shown,
    )
    "awarded" -> copy(
        awardedX = spec.x, awardedY = spec.y, awardedFontSize = spec.fontSize,
        awardedColor = spec.color, showAwarded = spec.shown,
    )
    "club-name" -> copy(
        templateClub = templateClub.copy(
            clubNameX = spec.x, clubNameY = spec.y, clubNameFontSize = spec.fontSize,
            clubNameColor = spec.color, showClubName = spec.shown,
        ),
    )
    else -> this
}

/** The image fields of built-in element [id], or null when [id] is not an image element. */
fun XpTemplate.boxSpec(id: String): XpBoxSpec? = when (id) {
    "user-icon" -> with(templateUser) { XpBoxSpec(iconX, iconY, iconSizeX, iconSizeY, showIcon) }
    "club-icon" -> with(templateClub) { XpBoxSpec(clubIconX, clubIconY, clubIconSizeX, clubIconSizeY, showClubIcon) }
    else -> null
}

/** Writes [spec] back into the columns of built-in image element [id]. */
fun XpTemplate.withBoxSpec(id: String, spec: XpBoxSpec): XpTemplate = when (id) {
    "user-icon" -> copy(
        templateUser = templateUser.copy(
            iconX = spec.x, iconY = spec.y, iconSizeX = spec.width, iconSizeY = spec.height, showIcon = spec.shown,
        ),
    )
    "club-icon" -> copy(
        templateClub = templateClub.copy(
            clubIconX = spec.x, clubIconY = spec.y, clubIconSizeX = spec.width, clubIconSizeY = spec.height,
            showClubIcon = spec.shown,
        ),
    )
    else -> this
}

/** Whether built-in element [id] has its show flag on. */
fun XpTemplate.isBuiltInShown(id: String): Boolean = when (id) {
    "progress-bar" -> templateBar.showBar
    else -> textSpec(id)?.shown ?: boxSpec(id)?.shown ?: false
}

/** Sets the show flag of built-in element [id]. */
fun XpTemplate.withBuiltInShown(id: String, shown: Boolean): XpTemplate {
    if (id == "progress-bar") return copy(templateBar = templateBar.copy(showBar = shown))
    textSpec(id)?.let { return withTextSpec(id, it.copy(shown = shown)) }
    boxSpec(id)?.let { return withBoxSpec(id, it.copy(shown = shown)) }
    return this
}

/** The drag origin of built-in element [id]: its x/y, or point A for the bar. */
fun XpTemplate.builtInOrigin(id: String): XpPoint? {
    if (id == "progress-bar") return XpPoint(templateBar.barPointAx.toFloat(), templateBar.barPointAy.toFloat())
    textSpec(id)?.let { return XpPoint(it.x.toFloat(), it.y.toFloat()) }
    boxSpec(id)?.let { return XpPoint(it.x.toFloat(), it.y.toFloat()) }
    return null
}

/**
 * Moves built-in element [id] to ([x], [y]). The bar moves as a whole: A
 * lands on the target and B is translated by the same delta.
 */
fun XpTemplate.withBuiltInPosition(id: String, x: Int, y: Int): XpTemplate {
    if (id == "progress-bar") {
        val dx = x - templateBar.barPointAx
        val dy = y - templateBar.barPointAy
        return copy(
            templateBar = templateBar.copy(
                barPointAx = x,
                barPointAy = y,
                barPointBx = templateBar.barPointBx + dx,
                barPointBy = templateBar.barPointBy + dy,
            ),
        )
    }
    textSpec(id)?.let { return withTextSpec(id, it.copy(x = x, y = y)) }
    boxSpec(id)?.let { return withBoxSpec(id, it.copy(x = x, y = y)) }
    return this
}

/** Whether [id] names one of the nine built-in elements. */
fun isBuiltInId(id: String): Boolean = id in DefaultBuiltInOrder || id in ClubBuiltInIds

/**
 * The template's built-in colour columns the bot parses with the throwing
 * `SKColor.Parse`; any invalid value here would break card generation.
 */
fun XpTemplate.invalidColorFields(): List<String> = buildList {
    if (!isValidTemplateHex(templateUser.textColor)) add("Username")
    if (!isValidTemplateHex(templateGuild.guildLevelColor)) add("Guild Level")
    if (!isValidTemplateHex(templateGuild.guildRankColor)) add("Guild Rank")
    if (!isValidTemplateHex(awardedColor)) add("Awarded XP")
    if (!isValidTemplateHex(timeOnLevelColor)) add("Time on Level")
    if (!isValidTemplateHex(templateBar.barColor)) add("XP Progress Bar")
    if (!isValidTemplateHex(templateClub.clubNameColor)) add("Club Name")
}

/** Builds a new custom element of [type] with the web editors' defaults. */
fun newCustomElement(type: XpCustomElementType, zIndex: Int): XpCustomElement {
    val isLine = type == XpCustomElementType.LINE
    val isText = type == XpCustomElementType.TEXT
    return XpCustomElement(
        id = "custom-${java.util.UUID.randomUUID()}",
        type = type.raw,
        label = type.label,
        visible = true,
        zIndex = zIndex,
        x = 80.0,
        y = 80.0,
        width = if (isLine) 180.0 else 140.0,
        height = if (isLine) 0.0 else 64.0,
        rotation = 0.0,
        opacity = 1.0,
        cornerRadius = if (type == XpCustomElementType.RECTANGLE) 12.0 else 0.0,
        fill = if (isText) "#FFFFFF" else "#5865F2",
        stroke = "#00000000",
        strokeWidth = if (isLine) 4.0 else 0.0,
        text = if (isText) "Level %xp.level.current% • Rank #%xp.rank%" else "",
        fontSize = 24.0,
        textAlign = "left",
        url = "",
        gradientEnd = "",
        gradientAngle = 0.0,
        shadowColor = "#00000080",
        shadowBlur = 0.0,
        shadowX = 0.0,
        shadowY = 4.0,
        progressStyle = "rounded",
        trackFill = "#FFFFFF30",
        segments = 10,
    )
}

/** The base every preset item is merged over. */
private val PresetBase = XpCustomElement(
    visible = true, opacity = 1.0, rotation = 0.0, stroke = "#00000000", strokeWidth = 0.0,
    shadowColor = "#00000080", shadowBlur = 0.0, shadowX = 0.0, shadowY = 4.0, gradientEnd = "",
    gradientAngle = 0.0, text = "", fontSize = 24.0, textAlign = "left", url = "", trackFill = "#FFFFFF30",
    segments = 10, width = 120.0, height = 60.0, x = 80.0, y = 80.0, cornerRadius = 0.0,
)

/** The three starter layouts shared with the dashboard editors. */
val XpTemplatePresets: Map<String, List<XpCustomElement>> = mapOf(
    "minimal" to listOf(
        PresetBase.copy(
            type = "text", label = "Level and rank", x = 130.0, y = 90.0, width = 360.0, height = 36.0,
            fill = "#FFFFFF", text = "Level %xp.level.current%  •  Rank #%xp.rank%", fontSize = 26.0,
        ),
        PresetBase.copy(
            type = "progress", label = "XP progress", x = 130.0, y = 140.0, width = 540.0, height = 18.0,
            fill = "#5865F2", trackFill = "#FFFFFF30", cornerRadius = 9.0, progressStyle = "rounded",
        ),
    ),
    "glass" to listOf(
        PresetBase.copy(
            type = "rectangle", label = "Glass panel", x = 110.0, y = 45.0, width = 610.0, height = 190.0,
            fill = "#111827B8", stroke = "#FFFFFF30", strokeWidth = 1.0, cornerRadius = 24.0,
            shadowBlur = 16.0, shadowY = 8.0,
        ),
        PresetBase.copy(
            type = "text", label = "Profile heading", x = 145.0, y = 76.0, width = 480.0, height = 40.0,
            fill = "#FFFFFF", text = "%xp.user.displayname%", fontSize = 30.0,
        ),
        PresetBase.copy(
            type = "progress", label = "XP progress", x = 145.0, y = 160.0, width = 520.0, height = 20.0,
            fill = "#7C3AED", gradientEnd = "#22D3EE", trackFill = "#FFFFFF25", cornerRadius = 10.0,
            progressStyle = "rounded",
        ),
    ),
    "gaming" to listOf(
        PresetBase.copy(
            type = "rectangle", label = "Rank plate", x = 485.0, y = 38.0, width = 250.0, height = 72.0,
            fill = "#EF4444", gradientEnd = "#F59E0B", cornerRadius = 8.0, rotation = -2.0,
        ),
        PresetBase.copy(
            type = "text", label = "Rank", x = 505.0, y = 52.0, width = 210.0, height = 42.0,
            fill = "#FFFFFF", text = "RANK  #%xp.rank%", fontSize = 30.0, textAlign = "center",
        ),
        PresetBase.copy(
            type = "progress", label = "Segmented XP", x = 130.0, y = 205.0, width = 590.0, height = 22.0,
            fill = "#F59E0B", trackFill = "#FFFFFF25", progressStyle = "segmented", segments = 12,
            cornerRadius = 3.0,
        ),
    ),
)

/**
 * The initials drawn in the avatar placeholder: the first letter of up to
 * two whitespace separated words, uppercased.
 */
fun avatarInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }

/** The bot's default template values for a fresh card. */
fun botDefaultTemplate(base: XpTemplate): XpTemplate = XpTemplate(
    id = base.id,
    guildId = base.guildId,
    outputSizeX = 800,
    outputSizeY = 246,
    templateUserId = base.templateUserId,
    templateGuildId = base.templateGuildId,
    templateClubId = base.templateClubId,
    templateBarId = base.templateBarId,
    dateAdded = base.dateAdded,
    templateUser = XpTemplateUser(id = base.templateUser.id, dateAdded = base.templateUser.dateAdded),
    templateBar = XpTemplateBar(id = base.templateBar.id, dateAdded = base.templateBar.dateAdded),
    templateGuild = XpTemplateGuild(id = base.templateGuild.id, dateAdded = base.templateGuild.dateAdded),
    templateClub = XpTemplateClub(id = base.templateClub.id, dateAdded = base.templateClub.dateAdded),
)
