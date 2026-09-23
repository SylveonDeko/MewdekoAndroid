package dev.mewdeko.mobile.feature.xp

import androidx.compose.ui.graphics.Color

/**
 * Parses the AARRGGBB (or bare RRGGBB) hex strings the built-in template
 * fields (`TextColor`, `BarColor`, `AwardedColor`, ...) are stored as.
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
    } catch (t: Throwable) {
        fallback
    }
}

/** Encodes a [Color] back into the AARRGGBB hex form the template fields use. */
fun argbHexOf(color: Color): String {
    fun channel(value: Float) = (value * 255).toInt().coerceIn(0, 255)
        .toString(16).padStart(2, '0')
    return (channel(color.alpha) + channel(color.red) + channel(color.green) + channel(color.blue)).uppercase()
}

/**
 * Parses the CSS-style #RRGGBB / #RRGGBBAA hex strings custom card elements
 * (`fill`, `stroke`, `shadowColor`, `trackFill`) are stored as.
 */
fun parseCssHex(raw: String, fallback: Color = Color(0xFF5865F2)): Color {
    val hex = raw.removePrefix("#").trim()
    return try {
        when (hex.length) {
            8 -> Color(
                red = hex.substring(0, 2).toInt(16) / 255f,
                green = hex.substring(2, 4).toInt(16) / 255f,
                blue = hex.substring(4, 6).toInt(16) / 255f,
                alpha = hex.substring(6, 8).toInt(16) / 255f,
            )

            6 -> Color(
                red = hex.substring(0, 2).toInt(16) / 255f,
                green = hex.substring(2, 4).toInt(16) / 255f,
                blue = hex.substring(4, 6).toInt(16) / 255f,
                alpha = 1f,
            )

            else -> fallback
        }
    } catch (t: Throwable) {
        fallback
    }
}
