package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Minecraft's sixteen legacy colour codes, keyed by their section-sign digit or letter. */
private val McColorCodes: Map<Char, Color> = mapOf(
    '0' to Color(0xFF000000),
    '1' to Color(0xFF0000AA),
    '2' to Color(0xFF00AA00),
    '3' to Color(0xFF00AAAA),
    '4' to Color(0xFFAA0000),
    '5' to Color(0xFFAA00AA),
    '6' to Color(0xFFFFAA00),
    '7' to Color(0xFFAAAAAA),
    '8' to Color(0xFF555555),
    '9' to Color(0xFF5555FF),
    'a' to Color(0xFF55FF55),
    'b' to Color(0xFF55FFFF),
    'c' to Color(0xFFFF5555),
    'd' to Color(0xFFFF55FF),
    'e' to Color(0xFFFFFF55),
    'f' to Color(0xFFFFFFFF),
)

/** The default colour used before any code, or after a `§r` reset. */
private val McDefaultColor = Color(0xFFAAAAAA)

/**
 * Renders a string carrying Minecraft's section-sign (`§`) colour and format
 * codes as an [AnnotatedString], for RCON console output and in-game chat
 * templates.
 */
fun mcColorText(raw: String): AnnotatedString = buildAnnotatedString {
    var color = McDefaultColor
    var bold = false
    var italic = false
    var underline = false
    var strikethrough = false

    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '§' && i + 1 < raw.length) {
            when (val code = raw[i + 1].lowercaseChar()) {
                'l' -> bold = true
                'o' -> italic = true
                'n' -> underline = true
                'm' -> strikethrough = true
                'k' -> Unit
                'r' -> {
                    color = McDefaultColor
                    bold = false
                    italic = false
                    underline = false
                    strikethrough = false
                }

                else -> McColorCodes[code]?.let { color = it }
            }
            i += 2
            continue
        }

        val decoration = when {
            underline && strikethrough -> TextDecoration.combine(
                listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            )

            underline -> TextDecoration.Underline
            strikethrough -> TextDecoration.LineThrough
            else -> null
        }
        withStyle(
            SpanStyle(
                color = color,
                fontWeight = if (bold) FontWeight.Bold else null,
                fontStyle = if (italic) FontStyle.Italic else null,
                textDecoration = decoration,
            ),
        ) {
            append(ch)
        }
        i++
    }
}
