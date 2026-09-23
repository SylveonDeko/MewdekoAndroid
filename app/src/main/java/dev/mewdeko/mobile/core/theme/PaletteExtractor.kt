package dev.mewdeko.mobile.core.theme

import android.graphics.Bitmap

/**
 * Builds a [GuildPalette] from a decoded guild icon with the web dashboard's
 * exact pipeline.
 *
 * Mirrors `colorThief.getPalette(img, 12)` followed by `colorStore.ts`: the
 * icon is read at its natural size, every 10th pixel is sampled (skipping
 * pixels with alpha below 125 and near-white pixels), [Mmcq] quantizes the
 * samples to 12 colors, and [DashboardColorStore] turns those into the
 * palette. Any failure lands on the dashboard's default palette.
 */
object PaletteExtractor {

    /** The color count the dashboard passes to `getPalette`. */
    private const val COLOR_COUNT = 12

    /** Extracts a palette from [bitmap], falling back to the default on failure. */
    fun extract(bitmap: Bitmap): GuildPalette = extractDashboardPalette(bitmap).toGuildPalette()

    /** Extracts the palette as the dashboard's hex and `hsl()` strings. */
    fun extractDashboardPalette(bitmap: Bitmap): DashboardPalette {
        val colors = runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val argb = IntArray(width * height)
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)
            quantizeArgb(argb, width * height)
        }.getOrNull()
        return DashboardColorStore.build(colors)
    }

    /** ColorThief's sampling and quantization over an unpremultiplied ARGB buffer. */
    fun quantizeArgb(argb: IntArray, pixelCount: Int): List<IntArray>? =
        Mmcq.quantize(Mmcq.samplePixels(argb, pixelCount, Mmcq.DEFAULT_QUALITY), COLOR_COUNT)
}
