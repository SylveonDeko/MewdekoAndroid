package dev.mewdeko.mobile.core.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.mewdeko.mobile.R

/** Font Awesome Utility Duo icons, the set the dashboard uses. Names match the dashboard's `fa-` classes. */
enum class FaGlyph(val cssName: String, val codePoint: Int) {
    ArrowDown("arrow-down", 0xF063),
    ArrowRotateLeft("arrow-rotate-left", 0xF0E2),
    ArrowUp("arrow-up", 0xF062),
    ArrowUpArrowDown("arrow-up-arrow-down", 0xE099),
    ArrowsRotate("arrows-rotate", 0xF021),
    Bars("bars", 0xF0C9),
    Bell("bell",0xF0F3),
    Bolt("bolt", 0xF0E7),
    Calendar("calendar", 0xF133),
    ChartSimple("chart-simple", 0xE473),
    Check("check", 0xF00C),
    Circle("circle", 0xF111),
    CircleCheck("circle-check", 0xF058),
    CircleExclamation("circle-exclamation", 0xF06A),
    CircleInfo("circle-info", 0xF05A),
    CirclePlus("circle-plus", 0xF055),
    Clock("clock", 0xF017),
    Code("code", 0xF121),
    Comments("comments", 0xF086),
    Crown("crown", 0xF521),
    Ellipsis("ellipsis", 0xF141),
    Envelope("envelope", 0xF0E0),
    EnvelopeOpen("envelope-open", 0xF2B6),
    Eye("eye", 0xF06E),
    EyeSlash("eye-slash", 0xF070),
    FaceSmile("face-smile", 0xF118),
    Filter("filter", 0xF0B0),
    Fire("fire", 0xF06D),
    FloppyDisk("floppy-disk", 0xF0C7),
    Folder("folder", 0xF07B),
    FolderPlus("folder-plus", 0xF65E),
    Gauge("gauge", 0xF624),
    Gift("gift", 0xF06B),
    Globe("globe", 0xF0AC),
    Heart("heart", 0xF004),
    IdCard("id-card", 0xF2C2),
    LayerGroup("layer-group", 0xF5FD),
    Link("link", 0xF0C1),
    LockOpen("lock-open", 0xF3C1),
    MagnifyingGlass("magnifying-glass", 0xF002),
    Microphone("microphone", 0xF130),
    Pause("pause", 0xF04C),
    Pen("pen", 0xF304),
    Play("play", 0xF04B),
    Sliders("sliders", 0xF1DE),
    Sort("sort", 0xF0DC),
    Sparkles("sparkles", 0xF890),
    Star("star", 0xF005),
    Trash("trash", 0xF1F8),
    Trophy("trophy", 0xF091),
    Unlock("unlock", 0xF09C),
    User("user", 0xF007),
    Users("users", 0xF0C0),
    WandMagicSparkles("wand-magic-sparkles", 0xE2CA),
    Xmark("xmark", 0xF00D);

    /** The primary layer as text. */
    val primary: String get() = String(Character.toChars(codePoint))

    /** The secondary layer as text. The bundled font maps it to the primary code point plus 0x100000. */
    val secondary: String get() = String(Character.toChars(codePoint + 0x100000))
}

/** The bundled Font Awesome Utility Duo font. */
val FaUtilityDuo = FontFamily(Font(R.font.fa_utility_duo))

/** One glyph in the bundled font. */
@Serializable
data class FaGlyphEntry(val name: String = "", val codepoint: Int = 0, val aliases: List<String> = emptyList())

/**
 * Every glyph in the bundled font, by name and alias, read once from assets/fa-glyphs.json. Covers names
 * [FaGlyph] does not, such as icons servers pick for their achievements.
 */
object FaGlyphTable {
    @Volatile
    private var entries: List<FaGlyphEntry>? = null

    @Volatile
    private var byName: Map<String, Int> = emptyMap()

    /** Every glyph, sorted by name. */
    fun all(context: Context): List<FaGlyphEntry> {
        entries?.let { return it }
        val loaded = runCatching {
            context.assets.open("fa-glyphs.json").bufferedReader().use { reader ->
                MewdekoJson.decodeFromString(ListSerializer(FaGlyphEntry.serializer()), reader.readText())
            }
        }.getOrDefault(emptyList())
        val map = HashMap<String, Int>()
        loaded.forEach { entry ->
            map[entry.name] = entry.codepoint
            entry.aliases.forEach { alias -> map.putIfAbsent(alias, entry.codepoint) }
        }
        byName = map
        entries = loaded
        return loaded
    }

    /** The code point of a glyph name, with or without the fa: prefix. */
    fun codepoint(context: Context, name: String): Int? {
        val bare = name.removePrefix("fa:")
        all(context)
        return byName[bare] ?: FaGlyph.entries.firstOrNull { it.cssName == bare }?.codePoint
    }
}

/**
 * A two layer Font Awesome icon: the primary layer at full strength over a 40% secondary layer.
 *
 * @param glyph The icon.
 * @param size The icon size.
 * @param tint The primary color; the current content color when null.
 * @param secondaryTint The secondary color; the primary color when null.
 * @param contentDescription What the icon means when it stands alone, such as in an icon button.
 */
@Composable
fun FaIcon(
    glyph: FaGlyph,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null,
    secondaryTint: Color? = null,
    contentDescription: String? = null,
) {
    FaIcon(glyph.codePoint, modifier, size, tint, secondaryTint, contentDescription)
}

/**
 * A Font Awesome icon by name, such as trophy or fa:trophy, for icons servers pick. Unknown names draw
 * the trophy.
 */
@Composable
fun FaIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val codePoint = remember(name) { FaGlyphTable.codepoint(context, name) ?: FaGlyph.Trophy.codePoint }
    FaIcon(codePoint, modifier, size, tint, null, contentDescription)
}

/** A Font Awesome icon by its primary layer's code point. */
@Composable
fun FaIcon(
    codePoint: Int,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color? = null,
    secondaryTint: Color? = null,
    contentDescription: String? = null,
) {
    val primaryColor = tint ?: LocalContentColor.current
    val secondaryColor = secondaryTint ?: primaryColor
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val style = TextStyle(fontFamily = FaUtilityDuo, fontSize = fontSize, lineHeight = 1.em)
    val semantics = Modifier.clearAndSetSemantics {
        if (contentDescription != null) this.contentDescription = contentDescription
    }
    Box(modifier.then(semantics), contentAlignment = Alignment.Center) {
        Text(String(Character.toChars(codePoint + 0x100000)), style = style,
            color = secondaryColor.copy(alpha = secondaryColor.alpha * 0.4f))
        Text(String(Character.toChars(codePoint)), style = style, color = primaryColor)
    }
}
