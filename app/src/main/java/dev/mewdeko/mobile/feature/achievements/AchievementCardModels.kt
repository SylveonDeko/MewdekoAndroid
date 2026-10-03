package dev.mewdeko.mobile.feature.achievements

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/** What fills an achievement card behind its elements. */
@Serializable
data class AchievementCardBackground(
    /** palette, solid, gradient, or image. */
    val kind: String = "palette",
    val color: String = "#1a202c",
    val color2: String = "primary@40",
    val angle: Double = 135.0,
    /** An https URL or upload:id. */
    val url: String = "",
    /** cover or contain. */
    val fit: String = "cover",
    /** How much the page color covers an image, 0 to 1. */
    val dim: Double = 0.35,
    /** Whether the palette wash is laid over solid, gradient, and image backgrounds. */
    val wash: Boolean = false,
)

/**
 * One element of an achievement card. Colors are tokens: #rrggbb, #rrggbbaa, or a palette name (primary,
 * secondary, accent, text, muted, grade) with an optional hex alpha such as primary@30.
 */
@Serializable
data class AchievementCardElement(
    val id: String = "",
    val type: String = "rectangle",
    val name: String = "",
    val visible: Boolean = true,
    val show: String = "always",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 160.0,
    val h: Double = 60.0,
    val rotation: Double = 0.0,
    val opacity: Double = 1.0,
    val fill: String = "",
    val fill2: String = "",
    val fillAngle: Double = 135.0,
    val stroke: String = "",
    val strokeWidth: Double = 0.0,
    val radius: Double = 0.0,
    val shadowColor: String = "",
    val shadowBlur: Double = 0.0,
    val shadowX: Double = 0.0,
    val shadowY: Double = 0.0,
    val color: String = "text",
    val color2: String = "muted",
    val color3: String = "",
    val fontSize: Double = 24.0,
    val bold: Boolean = true,
    val align: String = "left",
    val uppercase: Boolean = false,
    val spacing: Double = 0.0,
    val lineHeight: Double = 1.25,
    val maxLines: Int = 1,
    val text: String = "",
    val glyph: String = "",
    val url: String = "",
    val fit: String = "cover",
    val autoWidth: Boolean = true,
    val followId: String = "",
    val besideId: String = "",
    val gap: Double = 10.0,
) {
    /** The name shown in the layer list. */
    val displayName: String get() = name.ifEmpty { CardKinds.label(type) }
}

/** A card design: its size and background, and its elements back to front. */
@Serializable
data class AchievementCardTemplate(
    val width: Int = 1200,
    val height: Int = 420,
    val radius: Double = 36.0,
    val borderColor: String = "primary@30",
    val borderWidth: Double = 2.0,
    val shadow: Boolean = true,
    val background: AchievementCardBackground = AchievementCardBackground(),
    val elements: List<AchievementCardElement> = emptyList(),
)

/** A saved card design. */
@Serializable
data class AchievementCardDesign(
    val id: Int = 0,
    val name: String = "",
    val template: AchievementCardTemplate = AchievementCardTemplate(),
    val dateUpdated: String = "",
) {
    /** Identifies this version of the design, for cached previews. */
    val versionKey: String get() = "$id:$dateUpdated"
}

/** Which design categories and achievements pick instead of the default. */
@Serializable
data class AchievementCardAssignments(
    val categories: Map<String, Int> = emptyMap(),
    val achievements: Map<String, Int> = emptyMap(),
)

/** Size and count limits of card designs. */
@Serializable
data class AchievementCardLimits(
    val minWidth: Int = 600,
    val maxWidth: Int = 1600,
    val minHeight: Int = 200,
    val maxHeight: Int = 900,
    val maxElements: Int = 64,
    val maxText: Int = 300,
    val maxImages: Int = 30,
    val maxDesigns: Int = 20,
    val nameLength: Int = 40,
)

/** A server's card designs, its default, what uses which, and what the designer needs. */
@Serializable
data class AchievementCardResponse(
    val designs: List<AchievementCardDesign> = emptyList(),
    /** The default design, or null for the built in one. */
    val defaultId: Int? = null,
    val builtIn: AchievementCardTemplate = AchievementCardTemplate(),
    val assignments: AchievementCardAssignments = AchievementCardAssignments(),
    val images: List<AchievementIconUpload> = emptyList(),
    val placeholders: List<String> = emptyList(),
    /** primary, secondary, accent, text, muted, background as hex. */
    val palette: Map<String, String> = emptyMap(),
    val limits: AchievementCardLimits = AchievementCardLimits(),
) {
    /** The default design's name. */
    val defaultName: String get() = designs.firstOrNull { it.id == defaultId }?.name ?: "Built in"

    /** How many categories and achievements pick a design. */
    fun usage(id: Int): String {
        val categories = assignments.categories.values.count { it == id }
        val achievements = assignments.achievements.values.count { it == id }
        return listOfNotNull(
            categories.takeIf { it > 0 }?.let { "$it ${if (it == 1) "category" else "categories"}" },
            achievements.takeIf { it > 0 }?.let { "$it ${if (it == 1) "achievement" else "achievements"}" },
        ).joinToString(", ")
    }
}

/** Where one element landed on a drawn card, in card pixels. */
@Serializable
data class AchievementCardBox(
    val id: String = "",
    val x: Double = 0.0,
    val y: Double = 0.0,
    val w: Double = 0.0,
    val h: Double = 0.0,
    val drawn: Boolean = true,
)

/** A drawn card and where each element landed. */
@Serializable
data class AchievementCardPreview(
    val image: String = "",
    val width: Int = 1200,
    val height: Int = 420,
    val margin: Int = 40,
    val layout: List<AchievementCardBox> = emptyList(),
    val template: AchievementCardTemplate? = null,
) {
    /** The PNG bytes. */
    val bytes: ByteArray?
        get() = image.substringAfter(',', "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }.getOrNull() }
}

/** The body of a design create or update; [makeDefault] also makes a new design the server default. */
@Serializable
data class AchievementCardDesignRequest(
    val name: String? = null,
    val template: AchievementCardTemplate? = null,
    val makeDefault: Boolean = false,
)

/** What the designer opens: a saved design, or one that is only created when saved. */
data class CardEditTarget(
    /** The saved design, or null until the first save creates it. */
    val id: Int?,
    val name: String,
    val template: AchievementCardTemplate,
    /** Whether the first save also makes it the default, as when the built in default is edited. */
    val makeDefault: Boolean,
    /** Keeps two openings of the same design distinct. */
    val token: Long = System.nanoTime(),
)

/** The body of a preview request. */
@Serializable
data class AchievementCardPreviewRequest(val template: AchievementCardTemplate, val locked: Boolean, val key: String? = null)

/** The body of a default change; null id means the built in design. */
@Serializable
data class AchievementCardDefaultRequest(val id: Int? = null)

/** The body of an assignment; null id follows the default. */
@Serializable
data class AchievementCardAssignRequest(val category: Boolean, val key: String, val id: Int? = null)

/** What each element kind is called and its glyph. */
object CardKinds {
    /** Kinds every card has once. */
    val builtIn = listOf("icon", "grade", "label", "title", "description", "progress", "avatar", "member", "category", "points", "more")

    /** Kinds a server can add. */
    val custom = listOf("rectangle", "ellipse", "text", "image", "glyph")

    /** Kinds drawn as text. */
    val text = listOf("label", "title", "description", "member", "text")

    /** Kinds drawn as badges. */
    val pill = listOf("grade", "category", "points", "more")

    /** Kinds with a fill, outline, and corners. */
    val shape = listOf("rectangle", "ellipse", "icon", "avatar", "progress", "grade", "category", "points", "more")

    /** The kind's name. */
    fun label(type: String): String = when (type) {
        "icon" -> "Icon tile"
        "grade" -> "Grade badge"
        "label" -> "State line"
        "title" -> "Title"
        "description" -> "Description"
        "progress" -> "Progress bar"
        "avatar" -> "Avatar"
        "member" -> "Member line"
        "category" -> "Category badge"
        "points" -> "Points badge"
        "more" -> "More badge"
        "rectangle" -> "Rectangle"
        "ellipse" -> "Ellipse"
        "text" -> "Text"
        "image" -> "Image"
        "glyph" -> "Icon"
        else -> type
    }

    /** The kind's Font Awesome glyph name. */
    fun glyph(type: String): String = when (type) {
        "icon" -> "crown"
        "grade" -> "trophy"
        "label" -> "tag"
        "title", "description", "text" -> "font"
        "progress" -> "chart-simple"
        "avatar" -> "user"
        "member" -> "users"
        "category" -> "layer-group"
        "points" -> "star"
        "more" -> "plus"
        "ellipse" -> "circle"
        "image" -> "image"
        "glyph" -> "wand-magic-sparkles"
        else -> "square"
    }

    /** Whether a kind is built in. */
    fun isBuiltIn(type: String): Boolean = type in builtIn

    /** A new custom element in the middle of a card. */
    fun newElement(type: String, template: AchievementCardTemplate): AchievementCardElement {
        val (w, h) = when (type) {
            "rectangle" -> 320.0 to 120.0
            "ellipse" -> 160.0 to 160.0
            "text" -> 420.0 to 40.0
            "image" -> 200.0 to 200.0
            else -> 120.0 to 120.0
        }
        var n = 1
        while (template.elements.any { it.id == "$type-$n" }) n++
        return AchievementCardElement(
            id = "$type-$n", type = type,
            x = ((template.width - w) / 2).let { Math.round(it).toDouble() },
            y = ((template.height - h) / 2).let { Math.round(it).toDouble() },
            w = w, h = h,
            fill = if (type == "rectangle" || type == "ellipse") "primary@20" else "",
            radius = when (type) { "rectangle" -> 24.0; "image" -> 16.0; else -> 0.0 },
            color = if (type == "glyph") "primary" else "text",
            color2 = if (type == "text") "primary" else "muted",
            color3 = if (type == "glyph") "secondary" else "",
            fontSize = 28.0,
            text = if (type == "text") "{achievement.name}" else "",
            glyph = if (type == "glyph") "star" else "",
        )
    }
}

/** Card color tokens: palette names or the grade color with a hex alpha, or hex. */
object CardTokens {
    /** Token names offered as swatches. */
    val names = listOf("primary", "secondary", "accent", "text", "muted", "grade")

    /** Splits a token into its base and alpha from 0 to 255. */
    fun parse(value: String): Pair<String, Int> {
        if (value.isEmpty()) return "" to 255
        if (value.startsWith("#")) {
            return if (value.length == 9) value.take(7) to (value.takeLast(2).toIntOrNull(16) ?: 255) else value to 255
        }
        val parts = value.split("@", limit = 2)
        return parts[0] to (parts.getOrNull(1)?.toIntOrNull(16) ?: 255)
    }

    /** Joins a base and alpha back into a token. */
    fun build(base: String, alpha: Int): String {
        val clamped = alpha.coerceIn(0, 255)
        if (clamped >= 255) return base
        val hex = "%02x".format(clamped)
        return if (base.startsWith("#")) "$base$hex" else "$base@$hex"
    }

    /** A hex string as a color. */
    fun hexColor(hex: String): Color {
        val clean = hex.removePrefix("#")
        val value = clean.take(6).toLongOrNull(16) ?: return Color.White
        return Color(0xFF000000 or value)
    }

    /** The token as a color, or null for none. */
    fun color(value: String, palette: Map<String, String>, grade: String): Color? {
        val (base, alpha) = parse(value)
        if (base.isEmpty()) return null
        val hex = when {
            base.startsWith("#") -> base
            base == "grade" -> grade
            else -> palette[base] ?: "#ffffff"
        }
        return hexColor(hex).copy(alpha = alpha / 255f)
    }

    /** A short description, such as "primary at 19%". */
    fun label(value: String): String {
        if (value.isEmpty()) return "None"
        val (base, alpha) = parse(value)
        return if (alpha >= 255) base else "$base at ${Math.round(alpha / 2.55)}%"
    }
}
