package dev.mewdeko.mobile.core.theme

import android.graphics.Bitmap
import androidx.palette.graphics.Palette as AndroidPalette
import kotlin.math.abs
import kotlin.math.max

/**
 * Builds a [GuildPalette] from a decoded guild icon.
 *
 * Uses AndroidX [AndroidPalette] with a fixed selection score so a given icon
 * always lands on the same accent colour: saturation weighted 0.55,
 * proximity to mid lightness 0.25, and bucket frequency 0.20.
 */
object PaletteExtractor {

    private const val SWATCH_COUNT = 24
    private const val RESAMPLE_TARGET = 48

    /** Extracts a palette from [bitmap], falling back to the default on failure. */
    fun extract(bitmap: Bitmap): GuildPalette {
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, RESAMPLE_TARGET, RESAMPLE_TARGET, true)
        }.getOrNull() ?: return GuildPalette.Default

        val swatches = runCatching {
            AndroidPalette.from(scaled).maximumColorCount(SWATCH_COUNT).generate().swatches
        }.getOrNull().orEmpty()

        if (scaled !== bitmap) scaled.recycle()
        if (swatches.isEmpty()) return GuildPalette.Default

        val maxPopulation = swatches.maxOf { it.population }.takeIf { it > 0 }
            ?: return GuildPalette.Default

        val dominant = swatches
            .map { Rgb.fromArgb(it.rgb) to it.population }
            .filter { (rgb, _) ->
                val (h, s, l) = rgb.hsl
                @Suppress("UNUSED_EXPRESSION") h
                s > 0.06 && l > 0.05 && l < 0.95
            }
            .maxByOrNull { (rgb, population) -> score(rgb, population, maxPopulation) }
            ?.first
            ?: return GuildPalette.Default

        return GuildPalette.deriveFrom(dominant)
    }

    private fun score(rgb: Rgb, population: Int, maxPopulation: Int): Double {
        val (_, saturation, lightness) = rgb.hsl
        val lightnessScore = 1 - abs(lightness - 0.55) / 0.55
        val frequencyScore = population.toDouble() / maxPopulation.toDouble()
        return saturation * 0.55 + max(0.0, lightnessScore) * 0.25 + frequencyScore * 0.20
    }
}
