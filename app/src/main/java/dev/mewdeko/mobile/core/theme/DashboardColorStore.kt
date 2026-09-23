package dev.mewdeko.mobile.core.theme

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette exactly as the web dashboard's `colorStore` emits it.
 *
 * Solid slots are `#rrggbb`. [muted] and the gradient slots are the CSS
 * `hsl(h, s%, l%)` strings the dashboard writes; [toGuildPalette] resolves
 * them to RGB the same way.
 */
data class DashboardPalette(
    val primary: String,
    val secondary: String,
    val accent: String,
    val text: String,
    val muted: String,
    val background: String,
    val gradientStart: String,
    val gradientMid: String,
    val gradientEnd: String,
) {
    /** Resolves every slot to RGB for the Material theme. */
    fun toGuildPalette(): GuildPalette = GuildPalette(
        primary = Rgb.fromHex(primary),
        secondary = Rgb.fromHex(secondary),
        accent = Rgb.fromHex(accent),
        muted = Rgb.fromHex(DashboardColorStore.cssToHex(muted)),
        gradientStart = Rgb.fromHex(DashboardColorStore.cssToHex(gradientStart)),
        gradientMid = Rgb.fromHex(DashboardColorStore.cssToHex(gradientMid)),
        gradientEnd = Rgb.fromHex(DashboardColorStore.cssToHex(gradientEnd)),
    )
}

/**
 * A line by line port of the palette builders in the web dashboard's
 * `src/lib/stores/colorStore.ts`.
 *
 * Takes the up to 12 colors ColorThief returns and picks, adjusts and
 * formats them with the same arithmetic, rounding and loop limits, so an
 * icon yields the same hex values on Android as on the website. HSL values
 * are degrees and percents, as in the TypeScript.
 */
object DashboardColorStore {

    /** The dashboard's `DEFAULT_PALETTE`, used whenever extraction fails. */
    val Default = DashboardPalette(
        primary = "#3b82f6",
        secondary = "#8b5cf6",
        accent = "#ec4899",
        text = "#ffffff",
        muted = "#9ca3af",
        background = "#121828",
        gradientStart = "#3a86ff",
        gradientMid = "#8338ec",
        gradientEnd = "#ff006e",
    )

    /** The pre-computed luminance of the dashboard's dark UI background. */
    private const val DARK_BG_LUMINANCE = 0.03

    /**
     * The tail of `extractColorsUncached`: fewer than three colors falls
     * back to [Default], otherwise the cartoon or generic builder runs.
     */
    fun build(palette: List<IntArray>?): DashboardPalette {
        if (palette == null || palette.size < 3) return Default
        return runCatching {
            if (isLikelyCartoon(palette)) buildCartoonPalette(palette) else buildGenericPalette(palette)
        }.getOrDefault(Default)
    }

    /** JavaScript's `Math.round`: halves round toward positive infinity. */
    private fun jsRound(x: Double): Long = Math.round(x)

    /** WCAG relative luminance of an 8 bit color. */
    fun getLuminance(r: Int, g: Int, b: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun getLuminance(c: IntArray): Double = getLuminance(c[0], c[1], c[2])

    /** WCAG contrast ratio between two luminances. */
    fun getContrastRatio(l1: Double, l2: Double): Double {
        val lightest = max(l1, l2)
        val darkest = min(l1, l2)
        return (lightest + 0.05) / (darkest + 0.05)
    }

    /** `rgbToHsl`: hue in degrees, saturation and lightness in percent. */
    fun rgbToHsl(rIn: Int, gIn: Int, bIn: Int): DoubleArray {
        val r = rIn / 255.0
        val g = gIn / 255.0
        val b = bIn / 255.0
        val maxC = max(max(r, g), b)
        val minC = min(min(r, g), b)
        var h = 0.0
        val s: Double
        val l = (maxC + minC) / 2
        if (maxC != minC) {
            val d = maxC - minC
            s = if (l > 0.5) d / (2 - maxC - minC) else d / (maxC + minC)
            when (maxC) {
                r -> h = (g - b) / d + (if (g < b) 6 else 0)
                g -> h = (b - r) / d + 2
                b -> h = (r - g) / d + 4
            }
            h /= 6
        } else {
            s = 0.0
        }
        return doubleArrayOf(h * 360, s * 100, l * 100)
    }

    private fun rgbToHsl(c: IntArray): DoubleArray = rgbToHsl(c[0], c[1], c[2])

    /** `hslToRgb`: degrees and percents in, rounded 8 bit channels out. */
    fun hslToRgb(hIn: Double, sIn: Double, lIn: Double): IntArray {
        val h = hIn / 360
        val s = sIn / 100
        val l = lIn / 100
        val r: Double
        val g: Double
        val b: Double
        if (s == 0.0) {
            r = l
            g = l
            b = l
        } else {
            fun hue2rgb(p: Double, q: Double, tIn: Double): Double {
                var t = tIn
                if (t < 0) t += 1
                if (t > 1) t -= 1
                if (t < 1.0 / 6) return p + (q - p) * 6 * t
                if (t < 1.0 / 2) return q
                if (t < 2.0 / 3) return p + (q - p) * (2.0 / 3 - t) * 6
                return p
            }
            val q = if (l < 0.5) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            r = hue2rgb(p, q, h + 1.0 / 3)
            g = hue2rgb(p, q, h)
            b = hue2rgb(p, q, h - 1.0 / 3)
        }
        return intArrayOf(jsRound(r * 255).toInt(), jsRound(g * 255).toInt(), jsRound(b * 255).toInt())
    }

    /** `rgbToHex`: lowercase `#rrggbb`. */
    fun rgbToHex(r: Int, g: Int, b: Int): String =
        "#" + listOf(r, g, b).joinToString("") { x ->
            val hex = x.toString(16)
            if (hex.length == 1) "0$hex" else hex
        }

    private fun rgbToHex(c: IntArray): String = rgbToHex(c[0], c[1], c[2])

    /** `hslToString`: every component rounded, as in `hsl(200, 30%, 85%)`. */
    fun hslToString(h: Double, s: Double, l: Double): String =
        "hsl(${jsRound(h)}, ${jsRound(s)}%, ${jsRound(l)}%)"

    /** Resolves a `#rrggbb` or `hsl(h, s%, l%)` slot to a hex string through [hslToRgb]. */
    fun cssToHex(value: String): String {
        if (value.startsWith("#")) return value
        val parts = value.removePrefix("hsl(").removeSuffix(")").split(",").map {
            it.trim().removeSuffix("%").toDouble()
        }
        return rgbToHex(hslToRgb(parts[0], parts[1], parts[2]))
    }

    /** `adjustForContrast`: pushes a color to [minContrast] against the dark UI background. */
    fun adjustForContrast(color: IntArray, minContrast: Double = 4.5): IntArray {
        val (r, g, b) = color
        val (h, s, l) = rgbToHsl(r, g, b)
        val currentLuminance = getLuminance(r, g, b)
        var contrast = getContrastRatio(currentLuminance, DARK_BG_LUMINANCE)

        if (contrast >= minContrast) return intArrayOf(r, g, b)

        val newS = min(s + 15, 100.0)
        var attempts = 0

        while (contrast < minContrast && attempts < 5) {
            attempts++
            val newColor = hslToRgb(h, newS, l)
            val newLuminance = getLuminance(newColor)
            contrast = getContrastRatio(newLuminance, DARK_BG_LUMINANCE)
            if (contrast >= minContrast) return newColor
        }

        attempts = 0
        var newL = l
        val lightnessStep = 5

        while (contrast < minContrast && attempts < 20) {
            newL = min(95.0, newL + lightnessStep)
            attempts++
            val newColor = hslToRgb(h, newS, newL)
            val newLuminance = getLuminance(newColor)
            contrast = getContrastRatio(newLuminance, DARK_BG_LUMINANCE)
            if (newL >= 95 || contrast >= minContrast) break
        }

        return hslToRgb(h, newS, newL)
    }

    /** `createMutedColor`: a desaturated tone holding 3:1 against [color]. */
    fun createMutedColor(color: IntArray, textColor: String): String {
        val (h, s, l) = rgbToHsl(color)
        val backgroundLuminance = getLuminance(color)

        if (textColor == "#ffffff" || textColor == "#f0f0f0") {
            val newSaturation = min(s * 0.6, 30.0)
            var newLightness = max(l + 20, 60.0)
            var attempts = 0
            while (attempts < 10) {
                val testColor = hslToRgb(h, newSaturation, newLightness)
                val contrast = getContrastRatio(getLuminance(testColor), backgroundLuminance)
                if (contrast >= 3.0) break
                newLightness = min(newLightness + 5, 85.0)
                attempts++
            }
            return hslToString(h, newSaturation, newLightness)
        } else {
            val newSaturation = min(s * 0.5, 25.0)
            var newLightness = min(l - 20, 40.0)
            var attempts = 0
            while (attempts < 10) {
                val testColor = hslToRgb(h, newSaturation, newLightness)
                val contrast = getContrastRatio(getLuminance(testColor), backgroundLuminance)
                if (contrast >= 3.0) break
                newLightness = max(newLightness - 5, 15.0)
                attempts++
            }
            return hslToString(h, newSaturation, newLightness)
        }
    }

    /** `scoreColor`: saturation, mid lightness, colorfulness and a hue accent bonus. */
    fun scoreColor(color: IntArray): Double {
        val (h, s, l) = rgbToHsl(color)
        val saturationScore = s / 100
        val lightnessScore = 1 - abs(l - 55) / 55
        val colorfulness = if (s > 20) 1.0 else s / 20
        var accentBonus = 0.0
        if ((h >= 0 && h <= 60) || (h >= 340 && h <= 360)) {
            accentBonus = min(0.5, (s / 100) * 0.5)
        }
        if (h >= 180 && h <= 300) {
            accentBonus = min(0.3, (s / 100) * 0.3)
        }
        return saturationScore * 0.5 + lightnessScore * 0.2 + colorfulness * 0.1 + accentBonus * 0.2
    }

    /** `isAnimeSkinTone`: light peachy tones. */
    fun isAnimeSkinTone(h: Double, s: Double, l: Double): Boolean = h >= 10 && h <= 40 && s < 40 && l > 70

    /** `isAnimeEyeColor`: gold, amber, red, blue, green or purple at a usable saturation. */
    fun isAnimeEyeColor(h: Double, s: Double, l: Double): Boolean {
        val inEyeHueRange = (h >= 35 && h <= 55) ||
            (h >= 0 && h <= 10) ||
            (h >= 200 && h <= 240) ||
            (h >= 90 && h <= 150) ||
            (h >= 250 && h <= 290)
        return inEyeHueRange && s > 50 && l > 30 && l < 75
    }

    /** `isAnimeColorBand`: light hair or skin, vivid accessories, or mid-tone clothing. */
    fun isAnimeColorBand(h: Double, s: Double, l: Double): Boolean =
        (l > 80 && s < 20) || (s > 70 && l > 50 && l < 65) || (s > 50 && l > 40 && l < 70)

    /** `isLikelyCartoon`: whether the extracted colors read as cartoon or anime art. */
    fun isLikelyCartoon(palette: List<IntArray>): Boolean {
        var highSaturationCount = 0
        val distinctColorCount = HashSet<Double>()
        var hasSkinTones = false
        var hasEyeColors = false
        var hasAnimeColorPattern = false

        for (color in palette) {
            val (h, s, l) = rgbToHsl(color)
            if (s > 50) highSaturationCount++
            distinctColorCount.add(floor(h / 30))
            if (isAnimeSkinTone(h, s, l)) hasSkinTones = true
            if (isAnimeEyeColor(h, s, l)) hasEyeColors = true
            if (isAnimeColorBand(h, s, l)) hasAnimeColorPattern = true
        }

        return (highSaturationCount >= 1 && distinctColorCount.size >= 3) ||
            (hasSkinTones && hasEyeColors) ||
            (hasAnimeColorPattern && distinctColorCount.size >= 2)
    }

    /** A color with its HSL and score, the entries of `colorAnalysis`. */
    private class Analysis(val color: IntArray, val h: Double, val s: Double, val l: Double, val score: Double)

    /** JavaScript's `(a, b) => b.score - a.score` comparator, ties kept in order. */
    private fun descending(a: Double, b: Double): Int {
        val d = b - a
        return if (d > 0) 1 else if (d < 0) -1 else 0
    }

    /** `buildCartoonPalette`: warm and cool accent contrast plus prominent eye colors. */
    fun buildCartoonPalette(palette: List<IntArray>): DashboardPalette {
        val colorAnalysis = palette.map { color ->
            val (h, s, l) = rgbToHsl(color)
            Analysis(color, h, s, l, scoreColor(color))
        }.sortedWith { a, b -> descending(a.score, b.score) }

        val scoredColors = colorAnalysis
        val topColors = scoredColors.take(5)

        val warmAccents = colorAnalysis
            .filter { ((it.h >= 0 && it.h <= 60) || (it.h >= 340 && it.h <= 360)) && it.s > 50 }
            .sortedWith { a, b -> descending(a.score, b.score) }

        val coolAccents = colorAnalysis
            .filter { it.h >= 180 && it.h <= 300 && it.s > 40 }
            .sortedWith { a, b -> descending(a.score, b.score) }

        var primary: IntArray
        var secondary: IntArray
        var accent: IntArray

        if (warmAccents.isNotEmpty() && coolAccents.isNotEmpty()) {
            primary = coolAccents[0].color
            secondary = warmAccents[0].color
            accent = (warmAccents.getOrNull(1) ?: coolAccents.getOrNull(1) ?: scoredColors[2]).color
        } else {
            primary = topColors[0].color
            secondary = topColors[1].color
            accent = topColors[2].color

            val primaryHue = rgbToHsl(primary)[0]
            if ((primaryHue >= 0 && primaryHue <= 60) || (primaryHue >= 300 && primaryHue <= 360)) {
                secondary = hslToRgb((primaryHue + 180) % 360, 85.0, 60.0)
            } else {
                accent = hslToRgb((primaryHue + 180) % 360, 85.0, 60.0)
            }
        }

        val eyeColorCandidates = colorAnalysis.filter {
            ((it.h >= 35 && it.h <= 55) || (it.h >= 0 && it.h <= 30 && it.s > 70)) &&
                it.l > 40 && it.l < 75 && it.s > 50
        }
        if (eyeColorCandidates.isNotEmpty()) accent = eyeColorCandidates[0].color

        return assemble(adjustForContrast(primary, 4.5), adjustForContrast(secondary, 4.5), adjustForContrast(accent, 4.5), 90.0, 65.0)
    }

    /** `buildGenericPalette`: the three highest scoring colors. */
    fun buildGenericPalette(palette: List<IntArray>): DashboardPalette {
        val sortedColors = palette.sortedWith { a, b -> descending(scoreColor(a), scoreColor(b)) }
        val primary = sortedColors[0]
        val secondary = sortedColors.getOrNull(1) ?: sortedColors[0]
        val accent = sortedColors.getOrNull(2) ?: sortedColors[0]
        return assemble(adjustForContrast(primary), adjustForContrast(secondary), adjustForContrast(accent), 80.0, 60.0)
    }

    /** The shared return block of both builders. */
    private fun assemble(
        adjustedPrimary: IntArray,
        adjustedSecondary: IntArray,
        adjustedAccent: IntArray,
        gradientSaturation: Double,
        gradientLightness: Double,
    ): DashboardPalette {
        val textColor = "#ffffff"
        val primaryHsl = rgbToHsl(adjustedPrimary)
        val secondaryHsl = rgbToHsl(adjustedSecondary)
        val accentHsl = rgbToHsl(adjustedAccent)
        return DashboardPalette(
            primary = rgbToHex(adjustedPrimary),
            secondary = rgbToHex(adjustedSecondary),
            accent = rgbToHex(adjustedAccent),
            text = textColor,
            muted = createMutedColor(adjustedPrimary, textColor),
            background = "#121828",
            gradientStart = hslToString(primaryHsl[0], gradientSaturation, gradientLightness),
            gradientMid = hslToString(secondaryHsl[0], gradientSaturation, gradientLightness),
            gradientEnd = hslToString(accentHsl[0], gradientSaturation, gradientLightness),
        )
    }
}
