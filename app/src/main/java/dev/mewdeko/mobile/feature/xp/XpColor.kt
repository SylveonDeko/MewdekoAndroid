package dev.mewdeko.mobile.feature.xp

import androidx.compose.ui.graphics.Color

/**
 * Parses the AARRGGBB (or bare RRGGBB) hex strings the built-in template
 * fields (`TextColor`, `BarColor`, `AwardedColor`, ...) are stored as, the
 * way SkiaSharp's `SKColor.Parse` reads them: an optional `#`, eight digits
 * as alpha first, six digits as opaque.
 */
fun parseArgbHex(raw: String, fallback: Color = Color.White): Color {
    val hex = raw.removePrefix("#").trim()
    return try {
        when (hex.length) {
            8 -> Color(
                red = hex.substring(2, 4).toInt(16) / 255f,
                green = hex.substring(4, 6).toInt(16) / 255f,
                blue = hex.substring(6, 8).toInt(16) / 255f,
                alpha = hex.substring(0, 2).toInt(16) / 255f,
            )

            6 -> Color(
                red = hex.substring(0, 2).toInt(16) / 255f,
                green = hex.substring(2, 4).toInt(16) / 255f,
                blue = hex.substring(4, 6).toInt(16) / 255f,
                alpha = 1f,
            )

            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}

/** Whether [raw] is a colour the bot's `SKColor.Parse` accepts without throwing. */
fun isValidTemplateHex(raw: String): Boolean {
    val hex = raw.removePrefix("#").trim()
    return (hex.length == 6 || hex.length == 8) && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}

/** Encodes a [Color] back into the uppercase AARRGGBB hex form the template fields use. */
fun argbHexOf(color: Color): String {
    fun channel(value: Float) = Math.round(value * 255).coerceIn(0, 255)
        .toString(16).padStart(2, '0')
    return (channel(color.alpha) + channel(color.red) + channel(color.green) + channel(color.blue)).uppercase()
}

/** Encodes a [Color] as the `#RRGGBB` form custom element colours are written in. */
fun cssHexOf(color: Color): String {
    fun channel(value: Float) = Math.round(value * 255).coerceIn(0, 255)
        .toString(16).padStart(2, '0')
    return "#" + (channel(color.red) + channel(color.green) + channel(color.blue)).uppercase()
}

/**
 * Parses a custom element colour (`fill`, `stroke`, `shadowColor`,
 * `trackFill`) the way the bot's `ParseElementColor` does: `SKColor.TryParse`
 * on the string with a leading `#`, so eight digits read as AARRGGBB (not the
 * CSS `#RRGGBBAA` the dashboard assumes), six digits are opaque, and anything
 * blank or unparsable is transparent.
 */
fun parseElementColor(raw: String?): Color {
    if (raw.isNullOrBlank()) return Color.Transparent
    val hex = raw.trim().removePrefix("#")
    return try {
        when (hex.length) {
            8 -> Color(
                red = hex.substring(2, 4).toInt(16) / 255f,
                green = hex.substring(4, 6).toInt(16) / 255f,
                blue = hex.substring(6, 8).toInt(16) / 255f,
                alpha = hex.substring(0, 2).toInt(16) / 255f,
            )

            6 -> Color(
                red = hex.substring(0, 2).toInt(16) / 255f,
                green = hex.substring(2, 4).toInt(16) / 255f,
                blue = hex.substring(4, 6).toInt(16) / 255f,
                alpha = 1f,
            )

            4 -> Color(
                red = hex.substring(1, 2).repeat(2).toInt(16) / 255f,
                green = hex.substring(2, 3).repeat(2).toInt(16) / 255f,
                blue = hex.substring(3, 4).repeat(2).toInt(16) / 255f,
                alpha = hex.substring(0, 1).repeat(2).toInt(16) / 255f,
            )

            3 -> Color(
                red = hex.substring(0, 1).repeat(2).toInt(16) / 255f,
                green = hex.substring(1, 2).repeat(2).toInt(16) / 255f,
                blue = hex.substring(2, 3).repeat(2).toInt(16) / 255f,
                alpha = 1f,
            )

            else -> Color.Transparent
        }
    } catch (_: Throwable) {
        Color.Transparent
    }
}
