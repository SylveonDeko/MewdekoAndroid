package dev.mewdeko.mobile.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** An RGB triple with components in `0..1`. */
data class Rgb(val r: Double, val g: Double, val b: Double) {

    /** The Compose color for this triple at full opacity. */
    val color: Color get() = Color(r.toFloat(), g.toFloat(), b.toFloat(), 1f)

    /** WCAG relative luminance. */
    val luminance: Double get() = ColorUtils.calculateLuminance(color.toArgb()).toDouble()

    /** Conversion to HSL with components in `0..1`. */
    val hsl: Triple<Double, Double, Double>
        get() {
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val l = (maxC + minC) / 2
            if (abs(maxC - minC) < 1e-9) return Triple(0.0, 0.0, l)
            val d = maxC - minC
            val s = if (l > 0.5) d / (2 - maxC - minC) else d / (maxC + minC)
            val h = when (maxC) {
                r -> (g - b) / d + if (g < b) 6 else 0
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            }
            return Triple(h / 6, s, l)
        }

    /** Rotates the hue by the given number of degrees. */
    fun rotated(degrees: Double): Rgb {
        val (h, s, l) = hsl
        var hue = (h + degrees / 360) % 1.0
        if (hue < 0) hue += 1
        return fromHsl(hue, s, l)
    }

    /** Returns a variant lifted to a lightness range readable on a dark UI. */
    fun adjustedForDarkUi(): Rgb {
        val (h, s, l) = hsl
        return fromHsl(h, max(s, 0.5), min(max(l, 0.45), 0.7))
    }

    /** Returns a variant lowered to a lightness range readable on a light UI. */
    fun adjustedForLightUi(): Rgb {
        val (h, s, l) = hsl
        return fromHsl(h, max(s, 0.45), min(max(l, 0.32), 0.5))
    }

    /** Returns a more saturated variant, used for accent roles. */
    fun boostedSaturation(): Rgb {
        val (h, s, l) = hsl
        return fromHsl(h, min(1.0, s * 1.2 + 0.1), l)
    }

    /**
     * Returns a desaturated variant whose lightness is iteratively pushed
     * toward or away from [background] until it meets a 3:1 contrast ratio
     * (WCAG AA Large).
     */
    fun softenedForReadability(background: Rgb): Rgb {
        val (h, s, _) = hsl
        val saturation = s * 0.5
        val bgLuminance = background.luminance
        val lighten = bgLuminance < 0.5
        var lightness = if (lighten) 0.7 else 0.35
        repeat(8) {
            val candidate = fromHsl(h, saturation, lightness)
            if (contrastRatio(candidate.luminance, bgLuminance) >= 3.0) return candidate
            lightness = if (lighten) min(lightness + 0.05, 0.95) else max(lightness - 0.05, 0.05)
        }
        return fromHsl(h, saturation, lightness)
    }

    /** Returns this color at the given lightness, keeping hue and saturation. */
    fun atLightness(target: Double): Rgb {
        val (h, s, _) = hsl
        return fromHsl(h, s, target)
    }

    /** Returns this color at the given saturation and lightness. */
    fun toned(saturation: Double, lightness: Double): Rgb {
        val (h, _, _) = hsl
        return fromHsl(h, saturation, lightness)
    }

    companion object {
        /** Builds an [Rgb] from HSL components (each `0..1`). */
        fun fromHsl(h: Double, s: Double, l: Double): Rgb {
            if (s == 0.0) return Rgb(l, l, l)
            fun hueToRgb(p: Double, q: Double, tIn: Double): Double {
                var t = tIn
                if (t < 0) t += 1
                if (t > 1) t -= 1
                return when {
                    t < 1.0 / 6 -> p + (q - p) * 6 * t
                    t < 1.0 / 2 -> q
                    t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                    else -> p
                }
            }
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            return Rgb(
                r = hueToRgb(p, q, h + 1.0 / 3),
                g = hueToRgb(p, q, h),
                b = hueToRgb(p, q, h - 1.0 / 3),
            )
        }

        /** Parses a `#RRGGBB` or `#RRGGBBAA` hex string, black on malformed input. */
        fun fromHex(hex: String): Rgb {
            val s = hex.removePrefix("#")
            val value = s.toULongOrNull(16) ?: return Rgb(0.0, 0.0, 0.0)
            return when (s.length) {
                6 -> Rgb(
                    ((value shr 16) and 0xFFuL).toDouble() / 255,
                    ((value shr 8) and 0xFFuL).toDouble() / 255,
                    (value and 0xFFuL).toDouble() / 255,
                )

                8 -> Rgb(
                    ((value shr 24) and 0xFFuL).toDouble() / 255,
                    ((value shr 16) and 0xFFuL).toDouble() / 255,
                    ((value shr 8) and 0xFFuL).toDouble() / 255,
                )

                else -> Rgb(0.0, 0.0, 0.0)
            }
        }

        /** Builds an [Rgb] from a packed ARGB integer, ignoring alpha. */
        fun fromArgb(argb: Int): Rgb = Rgb(
            ((argb shr 16) and 0xFF).toDouble() / 255,
            ((argb shr 8) and 0xFF).toDouble() / 255,
            (argb and 0xFF).toDouble() / 255,
        )

        /** WCAG contrast ratio between two relative luminances. */
        fun contrastRatio(a: Double, b: Double): Double =
            (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }
}

/** Parses a `#RRGGBB` hex string into a Compose [Color]. */
fun colorFromHex(hex: String): Color = Rgb.fromHex(hex).color
