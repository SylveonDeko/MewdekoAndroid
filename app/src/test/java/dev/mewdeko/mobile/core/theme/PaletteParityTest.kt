package dev.mewdeko.mobile.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

/**
 * Parity with the web dashboard's `colorStore.ts` and ColorThief 2.6.
 *
 * Every expected value was printed by the node oracle
 * `/tmp/palette-parity/android-parity.mjs`, which runs the verbatim
 * `colorStore.ts` builders and the real bundled ColorThief and quantize
 * modules from `MewdekoDash/mewdash/node_modules`.
 */
class PaletteParityTest {

    private fun rgb(vararg values: Int): List<IntArray> = values.toList().chunked(3).map { it.toIntArray() }

    private fun assertPalette(
        input: List<IntArray>,
        solid: List<String>,
        css: List<String>,
        resolved: List<String>,
    ) {
        val palette = DashboardColorStore.build(input)
        assertEquals(solid, listOf(palette.primary, palette.secondary, palette.accent, palette.text, palette.background))
        val slots = listOf(palette.muted, palette.gradientStart, palette.gradientMid, palette.gradientEnd)
        assertEquals(css, slots)
        assertEquals(resolved, slots.map(DashboardColorStore::cssToHex))
    }

    /** Gradient with a translucent strip and a white band, the same formula as the oracle. */
    private fun gradientImage(): IntArray = IntArray(64 * 64) { index ->
        val x = index % 64
        val y = index / 64
        var r = x * 4
        var g = y * 4
        var b = (x * y) % 256
        var a = 255
        if (x < 4) a = 100
        if (y < 4) {
            r = 255
            g = 255
            b = 255
        }
        (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Four flat quadrants crossed by a varying diagonal stripe, the same formula as the oracle. */
    private fun blocksImage(): IntArray = IntArray(64 * 64) { index ->
        val x = index % 64
        val y = index / 64
        val (r, g, b) = when {
            abs(x - y) < 6 -> Triple(250, (x * 7) % 256, 120 + y)
            x < 32 && y < 32 -> Triple(30, 60, 200)
            x >= 32 && y < 32 -> Triple(240, 200, 60)
            x < 32 -> Triple(20, 20, 30)
            else -> Triple(200 + (x % 3), 40, 90 + (y % 2))
        }
        (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun hexes(colors: List<IntArray>): List<String> = colors.map { DashboardColorStore.rgbToHex(it[0], it[1], it[2]) }

    @Test
    fun cartoonSet() = assertPalette(
        rgb(
            230, 40, 40, 40, 90, 220, 250, 210, 40, 245, 215, 190, 40, 180, 80, 30, 30, 40,
            200, 60, 160, 120, 200, 240, 250, 140, 40, 90, 40, 150, 240, 240, 235, 60, 60, 70,
        ),
        listOf("#78c8f0", "#fad228", "#fad228", "#ffffff", "#121828"),
        listOf("hsl(200, 30%, 85%)", "hsl(200, 90%, 65%)", "hsl(49, 90%, 65%)", "hsl(49, 90%, 65%)"),
        listOf("#cddde4", "#55c1f6", "#f6d955", "#f6d955"),
    )

    @Test
    fun photoSet() = assertPalette(
        rgb(
            120, 110, 100, 90, 85, 80, 160, 150, 140, 60, 55, 50, 180, 170, 160, 100, 95, 90,
            140, 130, 115, 75, 70, 65, 150, 145, 142, 45, 42, 40, 130, 125, 120, 110, 100, 85,
        ),
        listOf("#a0968c", "#b29e80", "#b4aaa0", "#ffffff", "#121828"),
        listOf("hsl(30, 6%, 85%)", "hsl(30, 80%, 60%)", "hsl(36, 80%, 60%)", "hsl(30, 80%, 60%)"),
        listOf("#dbd9d6", "#eb9947", "#eba947", "#eb9947"),
    )

    @Test
    fun nearMonochromeBlueSet() = assertPalette(
        rgb(
            20, 40, 90, 30, 60, 130, 40, 80, 170, 25, 50, 110, 50, 90, 180, 35, 70, 150,
            15, 30, 70, 45, 85, 160, 60, 100, 190, 28, 55, 120, 38, 75, 140, 22, 45, 100,
        ),
        listOf("#7da0ee", "#7c9ee9", "#7da0ee", "#ffffff", "#121828"),
        listOf("hsl(221, 30%, 85%)", "hsl(221, 80%, 60%)", "hsl(221, 80%, 60%)", "hsl(221, 80%, 60%)"),
        listOf("#cdd5e4", "#477beb", "#477beb", "#477beb"),
    )

    @Test
    fun grayscaleSet() = assertPalette(
        rgb(
            10, 10, 10, 40, 40, 40, 80, 80, 80, 120, 120, 120, 160, 160, 160, 200, 200, 200,
            30, 30, 30, 60, 60, 60, 100, 100, 100, 140, 140, 140, 180, 180, 180, 220, 220, 220,
        ),
        listOf("#b39898", "#a0a0a0", "#b89e9e", "#ffffff", "#121828"),
        listOf("hsl(0, 9%, 85%)", "hsl(0, 80%, 60%)", "hsl(0, 80%, 60%)", "hsl(0, 80%, 60%)"),
        listOf("#dcd5d5", "#eb4747", "#eb4747", "#eb4747"),
    )

    @Test
    fun fewerThanThreeColorsFallsBackToDefault() {
        assertEquals(DashboardColorStore.Default, DashboardColorStore.build(rgb(200, 50, 50, 50, 50, 200)))
        assertEquals(DashboardColorStore.Default, DashboardColorStore.build(null))
    }

    @Test
    fun quantizesGradientLikeColorThief() {
        val argb = gradientImage()
        assertEquals(360, Mmcq.samplePixels(argb, argb.size).size)
        val colors = PaletteExtractor.quantizeArgb(argb, argb.size)!!
        assertEquals(
            listOf(
                "#2ba3a5", "#6dac22", "#8a766e", "#a1e084", "#ad58e8", "#e66d84",
                "#e4941d", "#8781b9", "#862190", "#592a23", "#a2dfe2", "#302f7d",
            ),
            hexes(colors),
        )
        assertPalette(
            colors,
            listOf("#c57af9", "#e4941d", "#e4941d", "#ffffff", "#121828"),
            listOf("hsl(275, 30%, 85%)", "hsl(275, 90%, 65%)", "hsl(36, 90%, 65%)", "hsl(36, 90%, 65%)"),
            listOf("#dbcde4", "#b355f6", "#f6b655", "#f6b655"),
        )
    }

    @Test
    fun quantizesBlocksLikeColorThief() {
        val argb = blocksImage()
        assertEquals(410, Mmcq.samplePixels(argb, argb.size).size)
        val colors = PaletteExtractor.quantizeArgb(argb, argb.size)!!
        assertEquals(
            listOf(
                "#f4cc3c", "#fc5296", "#1c3ccc", "#cc2c5c", "#fccc9a", "#14141c",
                "#f8d074", "#f8d074", "#f8d074", "#505c48", "#540c48", "#140c4c",
            ),
            hexes(colors),
        )
        assertPalette(
            colors,
            listOf("#889cf9", "#f4cc3c", "#f4cc3c", "#ffffff", "#121828"),
            listOf("hsl(229, 30%, 85%)", "hsl(229, 90%, 65%)", "hsl(47, 90%, 65%)", "hsl(47, 90%, 65%)"),
            listOf("#cdd1e4", "#5573f6", "#f6d355", "#f6d355"),
        )
    }
}
