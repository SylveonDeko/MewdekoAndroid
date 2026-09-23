package dev.mewdeko.mobile.core.theme

import kotlin.math.max
import kotlin.math.min

/**
 * A line by line port of the modified median cut quantizer (MMCQ) in
 * `@lokesh.dhakar/quantize` 1.4.0, the copy ColorThief 2.6 bundles and the web
 * dashboard runs on every guild icon.
 *
 * Every quirk is kept on purpose so an icon quantizes to the same colors, in
 * the same order, as it does in the browser: the `else if` min/max scan in
 * [vboxFromPixels], the truncating `~~` casts, the empty boxes a single-cell
 * split can produce, the stable priority queue ordering, and the short
 * circuit that returns the raw unique colors when there are few enough.
 */
internal object Mmcq {

    private const val SIGBITS = 5
    private const val RSHIFT = 8 - SIGBITS
    private const val MAX_ITERATIONS = 1000
    private const val FRACT_BY_POPULATIONS = 0.75
    private const val MULT = 1 shl (8 - SIGBITS)

    /** ColorThief's default sampling step: every 10th pixel. */
    const val DEFAULT_QUALITY = 10

    /**
     * ColorThief's `createPixelArray`: walks every [quality]th pixel of an
     * ARGB buffer and keeps it when alpha is at least 125 and it is not near
     * white (all channels above 250).
     */
    fun samplePixels(argb: IntArray, pixelCount: Int, quality: Int = DEFAULT_QUALITY): List<IntArray> {
        val out = ArrayList<IntArray>()
        var i = 0
        while (i < pixelCount) {
            val c = argb[i]
            val a = (c ushr 24) and 0xFF
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (a >= 125) {
                if (!(r > 250 && g > 250 && b > 250)) out.add(intArrayOf(r, g, b))
            }
            i += quality
        }
        return out
    }

    /**
     * Quantizes [pixels] (each `[r, g, b]`) to at most [maxColors] colors.
     *
     * Returns `null` where the JavaScript returns `false` (no pixels), and
     * the palette in the order `CMap.palette()` yields it.
     */
    fun quantize(pixels: List<IntArray>, maxColors: Int): List<IntArray>? {
        require(maxColors in 1..256) { "Invalid maximum color count. It must be an integer between 1 and 256." }
        if (pixels.isEmpty() || maxColors < 2) return null

        val seen = HashSet<Int>()
        val unique = ArrayList<IntArray>()
        for (pixel in pixels) {
            val key = (pixel[0] shl 16) or (pixel[1] shl 8) or pixel[2]
            if (seen.add(key)) unique.add(pixel)
        }
        if (unique.size <= maxColors) return unique.map { it.copyOf() }

        val histo = getHisto(pixels)
        val vbox = vboxFromPixels(pixels, histo)
        val pq = PQueue<VBox> { a, b -> a.count().compareTo(b.count()) }
        pq.push(vbox)

        iter(pq, histo, FRACT_BY_POPULATIONS * maxColors)

        val pq2 = PQueue<VBox> { a, b -> (a.count() * a.volume()).compareTo(b.count() * b.volume()) }
        while (pq.size() > 0) pq2.push(pq.pop())

        iter(pq2, histo, maxColors.toDouble())

        val palette = ArrayList<IntArray>()
        while (pq2.size() > 0) palette.add(pq2.pop().avg().copyOf())
        return palette
    }

    private fun colorIndex(r: Int, g: Int, b: Int): Int = (r shl (2 * SIGBITS)) + (g shl SIGBITS) + b

    private fun IntArray.at(index: Int): Int = if (index in indices) this[index] else 0

    /** A priority queue that sorts lazily and pops the greatest, as the JavaScript `PQueue` does. */
    private class PQueue<T>(private val comparator: Comparator<T>) {
        private val contents = ArrayList<T>()
        private var sorted = false

        fun push(item: T) {
            contents.add(item)
            sorted = false
        }

        fun pop(): T {
            if (!sorted) {
                contents.sortWith(comparator)
                sorted = true
            }
            return contents.removeAt(contents.lastIndex)
        }

        fun size(): Int = contents.size
    }

    /** A box in the 5 bit per channel color space, with the JavaScript's lazy caches. */
    private class VBox(
        var r1: Int,
        var r2: Int,
        var g1: Int,
        var g2: Int,
        var b1: Int,
        var b2: Int,
        val histo: IntArray,
    ) {
        private var volumeCache = 0L
        private var countCache = 0L
        private var countSet = false
        private var avgCache: IntArray? = null

        fun volume(): Long {
            if (volumeCache == 0L) {
                volumeCache = (r2 - r1 + 1).toLong() * (g2 - g1 + 1).toLong() * (b2 - b1 + 1).toLong()
            }
            return volumeCache
        }

        fun count(): Long {
            if (!countSet) {
                var npix = 0L
                for (i in r1..r2) for (j in g1..g2) for (k in b1..b2) npix += histo.at(colorIndex(i, j, k))
                countCache = npix
                countSet = true
            }
            return countCache
        }

        fun copy(): VBox = VBox(r1, r2, g1, g2, b1, b2, histo)

        fun avg(): IntArray {
            avgCache?.let { return it }
            val result = if (r1 == r2 && g1 == g2 && b1 == b2) {
                intArrayOf(r1 shl RSHIFT, g1 shl RSHIFT, b1 shl RSHIFT)
            } else {
                var ntot = 0L
                var rsum = 0.0
                var gsum = 0.0
                var bsum = 0.0
                for (i in r1..r2) for (j in g1..g2) for (k in b1..b2) {
                    val hval = histo.at(colorIndex(i, j, k))
                    ntot += hval
                    rsum += hval * (i + 0.5) * MULT
                    gsum += hval * (j + 0.5) * MULT
                    bsum += hval * (k + 0.5) * MULT
                }
                if (ntot != 0L) {
                    intArrayOf((rsum / ntot).toInt(), (gsum / ntot).toInt(), (bsum / ntot).toInt())
                } else {
                    intArrayOf(
                        (MULT * (r1 + r2 + 1) / 2.0).toInt(),
                        (MULT * (g1 + g2 + 1) / 2.0).toInt(),
                        (MULT * (b1 + b2 + 1) / 2.0).toInt(),
                    )
                }
            }
            avgCache = result
            return result
        }

        fun lo(dim: Char): Int = when (dim) {
            'r' -> r1
            'g' -> g1
            else -> b1
        }

        fun hi(dim: Char): Int = when (dim) {
            'r' -> r2
            'g' -> g2
            else -> b2
        }

        fun setLo(dim: Char, value: Int) {
            when (dim) {
                'r' -> r1 = value
                'g' -> g1 = value
                else -> b1 = value
            }
        }

        fun setHi(dim: Char, value: Int) {
            when (dim) {
                'r' -> r2 = value
                'g' -> g2 = value
                else -> b2 = value
            }
        }
    }

    private fun getHisto(pixels: List<IntArray>): IntArray {
        val histo = IntArray(1 shl (3 * SIGBITS))
        for (pixel in pixels) {
            val index = colorIndex(pixel[0] shr RSHIFT, pixel[1] shr RSHIFT, pixel[2] shr RSHIFT)
            histo[index] += 1
        }
        return histo
    }

    private fun vboxFromPixels(pixels: List<IntArray>, histo: IntArray): VBox {
        var rmin = 1000000
        var rmax = 0
        var gmin = 1000000
        var gmax = 0
        var bmin = 1000000
        var bmax = 0
        for (pixel in pixels) {
            val rval = pixel[0] shr RSHIFT
            val gval = pixel[1] shr RSHIFT
            val bval = pixel[2] shr RSHIFT
            if (rval < rmin) rmin = rval else if (rval > rmax) rmax = rval
            if (gval < gmin) gmin = gval else if (gval > gmax) gmax = gval
            if (bval < bmin) bmin = bval else if (bval > bmax) bmax = bval
        }
        return VBox(rmin, rmax, gmin, gmax, bmin, bmax, histo)
    }

    /**
     * Splits [vbox] at the median of its longest axis. A box holding one
     * pixel comes back as a lone copy, as in the JavaScript.
     */
    private fun medianCutApply(histo: IntArray, vbox: VBox): List<VBox> {
        check(vbox.count() != 0L) { "medianCutApply on an empty box" }
        val rw = vbox.r2 - vbox.r1 + 1
        val gw = vbox.g2 - vbox.g1 + 1
        val bw = vbox.b2 - vbox.b1 + 1
        val maxw = max(rw, max(gw, bw))
        if (vbox.count() == 1L) return listOf(vbox.copy())

        val dim = when (maxw) {
            rw -> 'r'
            gw -> 'g'
            else -> 'b'
        }
        val lo = vbox.lo(dim)
        val hi = vbox.hi(dim)
        var total = 0L
        val partialsum = LongArray(hi - lo + 1)
        for (i in lo..hi) {
            var sum = 0L
            when (dim) {
                'r' -> for (j in vbox.g1..vbox.g2) for (k in vbox.b1..vbox.b2) sum += histo.at(colorIndex(i, j, k))
                'g' -> for (j in vbox.r1..vbox.r2) for (k in vbox.b1..vbox.b2) sum += histo.at(colorIndex(j, i, k))
                else -> for (j in vbox.r1..vbox.r2) for (k in vbox.g1..vbox.g2) sum += histo.at(colorIndex(j, k, i))
            }
            total += sum
            partialsum[i - lo] = total
        }

        fun partial(i: Int): Long = if (i in lo..hi) partialsum[i - lo] else 0L
        fun lookahead(i: Int): Long = if (i in lo..hi) total - partialsum[i - lo] else 0L

        for (i in lo..hi) {
            if (partial(i) > total / 2.0) {
                val vbox1 = vbox.copy()
                val vbox2 = vbox.copy()
                val left = i - lo
                val right = hi - i
                var d2 = if (left <= right) {
                    min(hi - 1, (i + right / 2.0).toInt())
                } else {
                    max(lo, (i - 1 - left / 2.0).toInt())
                }
                while (partial(d2) == 0L) d2++
                var count2 = lookahead(d2)
                while (count2 == 0L && partial(d2 - 1) != 0L) {
                    d2--
                    count2 = lookahead(d2)
                }
                vbox1.setHi(dim, d2)
                vbox2.setLo(dim, d2 + 1)
                return listOf(vbox1, vbox2)
            }
        }
        error("medianCutApply found no cut")
    }

    private fun iter(lh: PQueue<VBox>, histo: IntArray, target: Double) {
        var ncolors = lh.size()
        var niters = 0
        while (niters < MAX_ITERATIONS) {
            if (ncolors >= target) return
            if (niters++ > MAX_ITERATIONS) return
            val vbox = lh.pop()
            if (vbox.count() == 0L) {
                lh.push(vbox)
                niters++
                continue
            }
            val vboxes = medianCutApply(histo, vbox)
            lh.push(vboxes[0])
            if (vboxes.size > 1) {
                lh.push(vboxes[1])
                ncolors++
            }
        }
    }
}
