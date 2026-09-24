package dev.mewdeko.mobile.feature.xp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The bundled rank card assets: the bot's Noto Sans faces and its default
 * background, decoded once per process.
 */
class XpCardAssets private constructor(context: Context) {
    /** Noto Sans Bold, used by every built-in text element. */
    val bold: Typeface = runCatching { Typeface.createFromAsset(context.assets, "fonts/NotoSans-Bold.ttf") }
        .getOrDefault(Typeface.DEFAULT_BOLD)

    /** Noto Sans Regular, used by custom text layers. */
    val regular: Typeface = runCatching { Typeface.createFromAsset(context.assets, "fonts/NotoSans-Regular.ttf") }
        .getOrDefault(Typeface.DEFAULT)

    /** The bot's `default_xp_background.png` at its natural 800 by 246 size. */
    val defaultBackground: Bitmap? = runCatching {
        context.assets.open("images/default_xp_background.png").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    companion object {
        @Volatile
        private var instance: XpCardAssets? = null

        /** The process-wide assets. */
        fun get(context: Context): XpCardAssets = instance ?: synchronized(this) {
            instance ?: XpCardAssets(context.applicationContext).also { instance = it }
        }
    }
}

/**
 * Decoded bitmaps for avatars, custom image layers, and custom backgrounds,
 * keyed by URL.
 *
 * [get] never blocks: a miss starts a Coil load and returns null, and the
 * finished bitmap lands in snapshot state, so a canvas that read it during
 * draw is invalidated and redraws.
 */
@Stable
class XpImageCache(private val context: Context, private val scope: CoroutineScope) {
    private val bitmaps = mutableStateMapOf<String, Bitmap>()
    private val failures = mutableStateMapOf<String, Boolean>()
    private val pending = mutableSetOf<String>()

    /** The bitmap for [url], starting a load when it has not been requested yet. */
    fun get(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        bitmaps[url]?.let { return it }
        if (failures[url] == true || !pending.add(url)) return null
        scope.launch {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val bitmap = runCatching {
                (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
            }.getOrNull()
            pending.remove(url)
            if (bitmap != null) bitmaps[url] = bitmap else failures[url] = true
        }
        return null
    }

    /** Whether loading [url] has failed. */
    fun failed(url: String?): Boolean = !url.isNullOrBlank() && failures[url] == true
}

/** The guild palette colours the designer chrome is drawn in, as ARGB ints. */
data class XpCardInk(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val text: Int,
)

/**
 * Everything the renderer draws: the staged template and layers, the
 * sanitised built-in order, the member data, and the background.
 */
data class XpCardScene(
    val template: XpTemplate,
    val customElements: List<XpCustomElement>,
    val builtInOrder: List<String>,
    val data: XpCardData,
    val background: Bitmap?,
    val defaultBackground: Boolean,
) {
    /** The card width the bot renders at: the background's, or the stored size without one. */
    val cardWidth: Int get() = template.outputSizeX.coerceAtLeast(1)

    /** The card height, see [cardWidth]. */
    val cardHeight: Int get() = template.outputSizeY.coerceAtLeast(1)
}

/**
 * How to draw a [XpCardScene].
 *
 * [unit] is the size of one dp in card pixels (`1 / zoom`), so chrome
 * strokes and handles stay a constant size on screen at any zoom.
 */
data class XpRenderOptions(
    val mode: XpDesignerMode,
    val selectedId: String? = null,
    val hoveredId: String? = null,
    val showGrid: Boolean = false,
    val gridSize: Int = 10,
    val showRulers: Boolean = false,
    val unit: Float = 1f,
) {
    /** Whether edit-only drawing (chrome, grid, placeholders) applies. */
    val edit: Boolean get() = mode == XpDesignerMode.EDIT
}

/**
 * Draws the rank card the way the bot's SkiaSharp `XpCardGenerator` does,
 * in card pixels, onto an [android.graphics.Canvas] whose transform the
 * caller has already set up. Also measures and hit tests elements with the
 * same fonts, so selection matches the pixels drawn.
 */
class XpCardRenderer(private val assets: XpCardAssets) {
    private val paint = Paint()
    private val track = Paint()
    private val chrome = Paint(Paint.ANTI_ALIAS_FLAG)
    private val measure = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val bounds = Rect()
    private val rect = RectF()

    /** Draws [scene] with [options]; [images] resolves avatar and layer URLs. */
    fun draw(
        canvas: Canvas,
        scene: XpCardScene,
        options: XpRenderOptions,
        ink: XpCardInk,
        images: (String) -> Bitmap?,
    ) {
        val width = scene.cardWidth.toFloat()
        val height = scene.cardHeight.toFloat()
        canvas.withSave {
            if (!options.edit) clipRect(0f, 0f, width, height)

            if (scene.defaultBackground) drawBackdrop(this, width, height, ink)
            scene.background?.let { drawBitmap(it, 0f, 0f, bitmapPaint) }

            if (options.edit) {
                chromeStroke(withAlpha(ink.primary, 0x40), options.unit)
                drawRect(0f, 0f, width, height, chrome)
                if (options.showGrid) drawGrid(this, width, height, options, ink)
            }

            scene.builtInOrder.forEach { id -> drawBuiltIn(this, id, scene, options, ink, images) }
            if (options.edit) {
                drawClubIcon(this, scene.template, options, ink)
                drawClubName(this, scene, options, ink)
            }
            scene.customElements.filter { it.visible }.sortedBy { it.zIndex }.forEach { element ->
                drawCustom(this, element, scene.data, options, ink, images)
            }

            if (options.edit) {
                options.hoveredId?.takeIf { it != options.selectedId }?.let {
                    drawChrome(this, it, scene, options, ink, selected = false)
                }
                options.selectedId?.let { drawChrome(this, it, scene, options, ink, selected = true) }
                if (options.showRulers) drawRulers(this, width, height, options, ink)
            }
        }
    }

    /**
     * The ids in draw order, bottom first: the sanitised built-ins, then the
     * club placeholders in edit mode, then custom layers by `zIndex`.
     */
    fun renderOrder(scene: XpCardScene, edit: Boolean): List<String> = buildList {
        addAll(scene.builtInOrder)
        if (edit) addAll(ClubBuiltInIds)
        scene.customElements.sortedBy { it.zIndex }.forEach { add(it.id) }
    }

    /**
     * The front-most visible element under card point ([x], [y]), or null.
     * Every hit box is inflated to at least 44dp square on screen.
     */
    fun hitTest(scene: XpCardScene, options: XpRenderOptions, x: Float, y: Float): String? {
        val minSize = 44f * options.unit
        renderOrder(scene, edit = true).asReversed().forEach { id ->
            if (hits(scene, id, x, y, minSize)) return id
        }
        return null
    }

    private fun hits(scene: XpCardScene, id: String, x: Float, y: Float, minSize: Float): Boolean {
        val template = scene.template
        if (id == "progress-bar") {
            if (!template.templateBar.showBar) return false
            val polygon = barPolygon(template.templateBar, 1f)
            return hitsPolygon(x, y, polygon, 18f)
        }
        template.textSpec(id)?.let { spec ->
            if (!spec.shown) return false
            val text = builtInText(id, scene.data, edit = true) ?: return false
            val box = builtInTextBox(text, spec)
            box.inset(-10f, -10f)
            if (box.width() < 120f) box.right = box.left + 120f
            return inflated(box, minSize).contains(x, y)
        }
        template.boxSpec(id)?.let { spec ->
            if (!spec.shown) return false
            val box = RectF(spec.x.toFloat(), spec.y.toFloat(), (spec.x + spec.width).toFloat(), (spec.y + spec.height).toFloat())
            return inflated(box, minSize).contains(x, y)
        }
        val element = scene.customElements.firstOrNull { it.id == id } ?: return false
        if (!element.visible) return false
        return when (element.type) {
            "text" -> {
                val box = customTextBox(element, scene.data)
                box.inset(-10f, -10f)
                if (box.width() < 120f) box.right = box.left + 120f
                inflated(box, minSize).contains(x, y)
            }
            "image" -> inflated(customRect(element), minSize).contains(x, y)
            else -> {
                val box = customRect(element)
                box.top -= 8f
                box.bottom += 8f
                inflated(box, minSize).contains(x, y)
            }
        }
    }

    private fun inflated(box: RectF, minSize: Float): RectF {
        val out = RectF(box)
        if (out.width() < minSize) {
            val grow = (minSize - out.width()) / 2f
            out.left -= grow
            out.right += grow
        }
        if (out.height() < minSize) {
            val grow = (minSize - out.height()) / 2f
            out.top -= grow
            out.bottom += grow
        }
        return out
    }

    /** The string built-in text element [id] draws, or null when it draws nothing. */
    fun builtInText(id: String, data: XpCardData, edit: Boolean): String? = when (id) {
        "user-text" -> data.username
        "guild-rank" -> data.rank.toString()
        "guild-level" -> data.level.toString()
        "time-on-level" -> humanizeTimeOnLevel(data.timeOnLevel.normalized())
        "awarded" -> when {
            data.bonusXp != 0L -> awardedXpText(data.bonusXp)
            edit -> awardedXpText(150L)
            else -> null
        }
        "club-name" -> data.clubName
        else -> null
    }

    /** The measured selection box of a built-in text element: glyph bounds padded by 5. */
    private fun builtInTextBox(text: String, spec: XpTextSpec): RectF {
        measure.typeface = assets.bold
        measure.textSize = spec.fontSize.toFloat()
        measure.textAlign = Paint.Align.LEFT
        val width = measure.measureText(text)
        measure.getTextBounds(text, 0, text.length, bounds)
        val ascent = max(0f, -bounds.top.toFloat())
        val descent = max(0f, bounds.bottom.toFloat())
        return RectF(spec.x - 5f, spec.y - ascent - 5f, spec.x + width + 5f, spec.y + descent + 5f)
    }

    /** The measured box of a custom text layer: top at `y`, left per alignment. */
    private fun customTextBox(element: XpCustomElement, data: XpCardData): RectF {
        val text = resolveXpPlaceholders(element.text, data)
        measure.typeface = assets.regular
        measure.textSize = element.fontSize.toFloat()
        measure.textAlign = Paint.Align.LEFT
        val width = measure.measureText(text)
        measure.getTextBounds(text, 0, text.length, bounds)
        val height = max(element.fontSize.toFloat(), (bounds.bottom - bounds.top).toFloat())
        val anchor = customTextAnchor(element)
        val left = when (element.textAlign) {
            "center" -> anchor - width / 2f
            "right" -> anchor - width
            else -> anchor
        }
        val top = element.y.toFloat()
        return RectF(left, top, left + width, top + height)
    }

    private fun customTextAnchor(element: XpCustomElement): Float = when (element.textAlign) {
        "center" -> (element.x + element.width / 2).toFloat()
        "right" -> (element.x + element.width).toFloat()
        else -> element.x.toFloat()
    }

    private fun customRect(element: XpCustomElement): RectF {
        val x1 = element.x.toFloat()
        val y1 = element.y.toFloat()
        val x2 = (element.x + element.width).toFloat()
        val y2 = (element.y + element.height).toFloat()
        return RectF(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    private fun drawBackdrop(canvas: Canvas, width: Float, height: Float, ink: XpCardInk) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            0f, 0f, width, height,
            intArrayOf(withAlpha(ink.primary, 0x14), withAlpha(ink.primary, 0x20), withAlpha(ink.secondary, 0x14)),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
    }

    private fun drawGrid(canvas: Canvas, width: Float, height: Float, options: XpRenderOptions, ink: XpCardInk) {
        val grid = options.gridSize.coerceAtLeast(1).toFloat()
        chrome.reset()
        chrome.isAntiAlias = true
        chrome.style = Paint.Style.STROKE
        chrome.strokeWidth = 0.5f * options.unit
        chrome.color = withAlpha(ink.primary, 0x20)
        chrome.pathEffect = DashPathEffect(floatArrayOf(2f * options.unit, 4f * options.unit), 0f)
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height, chrome)
            x += grid
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width, y, chrome)
            y += grid
        }
        chrome.pathEffect = null
    }

    private fun drawRulers(canvas: Canvas, width: Float, height: Float, options: XpRenderOptions, ink: XpCardInk) {
        chrome.reset()
        chrome.isAntiAlias = true
        chrome.style = Paint.Style.FILL
        chrome.color = withAlpha(ink.primary, 0x08)
        canvas.drawRect(0f, -30f, width, 0f, chrome)
        canvas.drawRect(-30f, 0f, 0f, height, chrome)
        chromeStroke(withAlpha(ink.primary, 0x40), options.unit)
        canvas.drawRect(0f, -30f, width, 0f, chrome)
        canvas.drawRect(-30f, 0f, 0f, height, chrome)
        measure.typeface = assets.regular
        measure.textSize = 10f
        measure.textAlign = Paint.Align.LEFT
        measure.color = withAlpha(ink.text, 0xB0)
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, -10f, x, 0f, chrome)
            canvas.drawText(x.toInt().toString(), x + 2f, -14f, measure)
            x += 50f
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(-10f, y, 0f, y, chrome)
            canvas.withRotation(-90f, -14f, y - 2f) {
                drawText(y.toInt().toString(), -14f, y - 2f, measure)
            }
            y += 50f
        }
    }

    private fun drawBuiltIn(
        canvas: Canvas,
        id: String,
        scene: XpCardScene,
        options: XpRenderOptions,
        ink: XpCardInk,
        images: (String) -> Bitmap?,
    ) {
        val template = scene.template
        when (id) {
            "progress-bar" -> if (template.templateBar.showBar) {
                val bar = template.templateBar
                val polygon = barPolygon(bar, scene.data.progress)
                path.reset()
                path.moveTo(polygon[0].x, polygon[0].y)
                for (i in 1 until polygon.size) path.lineTo(polygon[i].x, polygon[i].y)
                path.close()
                paint.reset()
                paint.isAntiAlias = true
                paint.style = Paint.Style.FILL
                paint.color = withAlpha(parseArgbHex(bar.barColor).toArgb(), bar.barTransparency.coerceIn(0, 255))
                canvas.drawPath(path, paint)
            }
            "user-icon" -> drawAvatar(canvas, scene, options, ink, images)
            else -> {
                val spec = template.textSpec(id) ?: return
                if (!spec.shown) return
                val text = builtInText(id, scene.data, options.edit) ?: return
                drawBuiltInText(canvas, text, spec, 255)
            }
        }
    }

    private fun drawBuiltInText(canvas: Canvas, text: String, spec: XpTextSpec, alpha: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.typeface = assets.bold
        paint.textSize = spec.fontSize.toFloat()
        paint.textAlign = Paint.Align.LEFT
        val color = parseArgbHex(spec.color).toArgb()
        paint.color = withAlpha(color, (android.graphics.Color.alpha(color) * alpha) / 255)
        canvas.drawText(text, spec.x.toFloat(), spec.y.toFloat(), paint)
    }

    private fun drawAvatar(
        canvas: Canvas,
        scene: XpCardScene,
        options: XpRenderOptions,
        ink: XpCardInk,
        images: (String) -> Bitmap?,
    ) {
        val spec = scene.template.boxSpec("user-icon") ?: return
        if (!spec.shown) return
        rect.set(spec.x.toFloat(), spec.y.toFloat(), (spec.x + spec.width).toFloat(), (spec.y + spec.height).toFloat())
        val radius = (spec.width / 2).toFloat()
        val avatar = scene.data.avatarUrl?.let(images)
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        if (avatar != null) {
            val shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            matrix.setRectToRect(
                RectF(0f, 0f, avatar.width.toFloat(), avatar.height.toFloat()),
                rect,
                Matrix.ScaleToFit.FILL,
            )
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.shader = null
        } else {
            paint.color = withAlpha(ink.primary, 0x30)
            canvas.drawRoundRect(rect, radius, radius, paint)
            val initials = avatarInitials(scene.data.displayName.ifBlank { scene.data.username })
            if (initials.isNotEmpty()) {
                paint.color = ink.text
                paint.typeface = assets.bold
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = max(14f, min(spec.width, spec.height) / 3f)
                val metrics = paint.fontMetrics
                canvas.drawText(initials, rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, paint)
            }
        }
        if (options.edit) {
            chromeStroke(withAlpha(ink.primary, 0x40), options.unit)
            canvas.drawRect(rect, chrome)
        }
    }

    private fun drawClubIcon(canvas: Canvas, template: XpTemplate, options: XpRenderOptions, ink: XpCardInk) {
        val spec = template.boxSpec("club-icon") ?: return
        if (!spec.shown) return
        rect.set(spec.x.toFloat(), spec.y.toFloat(), (spec.x + spec.width).toFloat(), (spec.y + spec.height).toFloat())
        chromeStroke(ink.primary, options.unit)
        chrome.pathEffect = DashPathEffect(floatArrayOf(4f * options.unit, 4f * options.unit), 0f)
        canvas.drawRect(rect, chrome)
        chrome.pathEffect = null
        measure.typeface = assets.bold
        measure.textSize = 12f
        measure.textAlign = Paint.Align.CENTER
        measure.color = ink.text
        val metrics = measure.fontMetrics
        canvas.drawText("Club Icon", rect.centerX(), rect.centerY() - (metrics.ascent + metrics.descent) / 2f, measure)
    }

    private fun drawClubName(canvas: Canvas, scene: XpCardScene, options: XpRenderOptions, ink: XpCardInk) {
        val spec = scene.template.textSpec("club-name") ?: return
        if (!spec.shown) return
        drawBuiltInText(canvas, scene.data.clubName, spec, 128)
        val box = builtInTextBox(scene.data.clubName, spec)
        chromeStroke(ink.primary, options.unit)
        chrome.pathEffect = DashPathEffect(floatArrayOf(4f * options.unit, 4f * options.unit), 0f)
        canvas.drawRect(box, chrome)
        chrome.pathEffect = null
    }

    private fun drawCustom(
        canvas: Canvas,
        element: XpCustomElement,
        data: XpCardData,
        options: XpRenderOptions,
        ink: XpCardInk,
        images: (String) -> Bitmap?,
    ) {
        val x = element.x.toFloat()
        val y = element.y.toFloat()
        val w = element.width.toFloat()
        val h = element.height.toFloat()
        val radius = element.cornerRadius.toFloat()
        val alpha = (255 * element.opacity.coerceIn(0.0, 1.0)).toInt()
        canvas.withRotation(element.rotation.toFloat(), x + w / 2f, y + h / 2f) {
            rect.set(x, y, x + w, y + h)

            paint.reset()
            paint.isAntiAlias = true
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(elementArgb(element.fill), alpha)
            if (element.gradientEnd.isNotBlank()) {
                val radians = Math.toRadians(element.gradientAngle).toFloat()
                val reach = max(w, h) / 2f
                val cx = rect.centerX()
                val cy = rect.centerY()
                val vx = cos(radians) * reach
                val vy = sin(radians) * reach
                paint.shader = LinearGradient(
                    cx - vx, cy - vy, cx + vx, cy + vy,
                    intArrayOf(elementArgb(element.fill), elementArgb(element.gradientEnd)),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            if (element.shadowBlur > 0) {
                paint.setShadowLayer(
                    shadowRadiusForSigma(element.shadowBlur.toFloat()),
                    element.shadowX.toFloat(),
                    element.shadowY.toFloat(),
                    elementArgb(element.shadowColor),
                )
            }

            when (element.type.lowercase()) {
                "ellipse" -> canvas.drawOval(rect, paint)
                "line" -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = max(1f, element.strokeWidth.toFloat())
                    canvas.drawLine(x, y, x + w, y + h, paint)
                }
                "text" -> {
                    paint.typeface = assets.regular
                    paint.textSize = element.fontSize.toFloat()
                    paint.textAlign = when (element.textAlign) {
                        "center" -> Paint.Align.CENTER
                        "right" -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT
                    }
                    canvas.drawText(
                        resolveXpPlaceholders(element.text, data),
                        customTextAnchor(element),
                        y + element.fontSize.toFloat(),
                        paint,
                    )
                }
                "image" -> {
                    val bitmap = element.url.takeIf(::isHttpUrl)?.let(images)
                    if (bitmap != null) {
                        paint.isFilterBitmap = true
                        canvas.drawBitmap(bitmap, null, rect, paint)
                    } else if (options.edit) {
                        chrome.reset()
                        chrome.isAntiAlias = true
                        chrome.style = Paint.Style.FILL
                        chrome.color = withAlpha(ink.primary, 0x30)
                        canvas.drawRect(rect, chrome)
                    }
                }
                "progress" -> drawCustomProgress(canvas, element, data, radius)
                else -> canvas.drawRoundRect(rect, radius, radius, paint)
            }

            if (element.strokeWidth > 0 && element.type !in setOf("line", "text", "image")) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = element.strokeWidth.toFloat()
                paint.color = withAlpha(elementArgb(element.stroke), alpha)
                if (element.type == "ellipse") canvas.drawOval(rect, paint) else canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }
    }

    private fun drawCustomProgress(canvas: Canvas, element: XpCustomElement, data: XpCardData, radius: Float) {
        val progress = data.progress
        track.reset()
        track.isAntiAlias = true
        track.style = Paint.Style.FILL
        track.color = elementArgb(element.trackFill)
        when (element.progressStyle) {
            "radial" -> {
                val stroke = max(
                    2f,
                    if (element.strokeWidth > 0) element.strokeWidth.toFloat() else min(rect.width(), rect.height()) / 8f,
                )
                listOf(track, paint).forEach {
                    it.style = Paint.Style.STROKE
                    it.strokeWidth = stroke
                    it.strokeCap = Paint.Cap.ROUND
                }
                canvas.drawArc(rect, -90f, 360f, false, track)
                canvas.drawArc(rect, -90f, 360f * progress, false, paint)
            }
            "segmented" -> {
                val count = element.segments.coerceIn(2, 50)
                val gap = max(2f, rect.width() * 0.01f)
                val segment = (rect.width() - gap * (count - 1)) / count
                val filled = ceil(progress * count).toInt()
                for (i in 0 until count) {
                    val left = rect.left + i * (segment + gap)
                    canvas.drawRoundRect(
                        RectF(left, rect.top, left + segment, rect.bottom),
                        radius,
                        radius,
                        if (i < filled) paint else track,
                    )
                }
            }
            else -> {
                canvas.drawRoundRect(rect, radius, radius, track)
                canvas.drawRoundRect(
                    RectF(rect.left, rect.top, rect.left + rect.width() * progress, rect.bottom),
                    radius,
                    radius,
                    paint,
                )
            }
        }
    }

    private fun drawChrome(
        canvas: Canvas,
        id: String,
        scene: XpCardScene,
        options: XpRenderOptions,
        ink: XpCardInk,
        selected: Boolean,
    ) {
        val template = scene.template
        val color = if (selected) ink.accent else ink.primary
        val width = if (selected) 2f else 1f
        if (id == "progress-bar") {
            val bar = template.templateBar
            chrome.reset()
            chrome.isAntiAlias = true
            chrome.style = Paint.Style.FILL
            chrome.color = color
            canvas.drawCircle(bar.barPointAx.toFloat(), bar.barPointAy.toFloat(), 5f * options.unit, chrome)
            canvas.drawCircle(bar.barPointBx.toFloat(), bar.barPointBy.toFloat(), 5f * options.unit, chrome)
            return
        }
        template.textSpec(id)?.let { spec ->
            val text = builtInText(id, scene.data, edit = true) ?: return
            drawTextChrome(canvas, builtInTextBox(text, spec), color, width, selected, options)
            return
        }
        template.boxSpec(id)?.let { spec ->
            rect.set(spec.x.toFloat(), spec.y.toFloat(), (spec.x + spec.width).toFloat(), (spec.y + spec.height).toFloat())
            drawImageChrome(canvas, RectF(rect), BuiltInElementLabels[id] ?: id, color, width, options, ink)
            return
        }
        val element = scene.customElements.firstOrNull { it.id == id } ?: return
        when (element.type) {
            "text" -> drawTextChrome(canvas, customTextBox(element, scene.data), color, width, selected, options)
            "image" -> drawImageChrome(
                canvas, customRect(element), element.label.ifBlank { "Image" }, color, width, options, ink,
            )
            else -> {
                val x = element.x.toFloat()
                val y = element.y.toFloat()
                chromeStroke(color, options.unit * width)
                canvas.drawRect(
                    x - 3f,
                    y - 3f,
                    x + element.width.toFloat() + 3f,
                    y - 3f + max(6f, element.height.toFloat() + 6f),
                    chrome,
                )
            }
        }
    }

    private fun drawTextChrome(
        canvas: Canvas,
        box: RectF,
        color: Int,
        width: Float,
        selected: Boolean,
        options: XpRenderOptions,
    ) {
        chromeStroke(color, options.unit * width)
        if (!selected) chrome.pathEffect = DashPathEffect(floatArrayOf(4f * options.unit, 4f * options.unit), 0f)
        canvas.drawRect(box, chrome)
        chrome.pathEffect = null
    }

    private fun drawImageChrome(
        canvas: Canvas,
        box: RectF,
        label: String,
        color: Int,
        width: Float,
        options: XpRenderOptions,
        ink: XpCardInk,
    ) {
        chromeStroke(color, options.unit * width)
        canvas.drawRect(box, chrome)
        val strip = min(18f, box.height())
        chrome.reset()
        chrome.isAntiAlias = true
        chrome.style = Paint.Style.FILL
        chrome.color = withAlpha(android.graphics.Color.BLACK, 0xA6)
        canvas.drawRect(box.left, box.bottom - strip, box.right, box.bottom, chrome)
        measure.typeface = assets.bold
        measure.textSize = 11f
        measure.textAlign = Paint.Align.CENTER
        measure.color = ink.text
        val metrics = measure.fontMetrics
        canvas.drawText(
            label,
            box.centerX(),
            box.bottom - strip / 2f - (metrics.ascent + metrics.descent) / 2f,
            measure,
        )
    }

    private fun chromeStroke(color: Int, strokeWidth: Float) {
        chrome.reset()
        chrome.isAntiAlias = true
        chrome.style = Paint.Style.STROKE
        chrome.strokeWidth = strokeWidth
        chrome.color = color
    }
}

/** [color] with its alpha channel replaced by [alpha] (0 to 255), like SkiaSharp's `WithAlpha`. */
fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

/** A custom element colour string as an ARGB int, with the bot's `ParseElementColor` semantics. */
fun elementArgb(raw: String?): Int = parseElementColor(raw).toArgb()

/** Whether [url] is an absolute http or https URL, the only kind the bot fetches for image layers. */
fun isHttpUrl(url: String): Boolean {
    val trimmed = url.trim()
    return (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
        runCatching { java.net.URI(trimmed).host != null }.getOrDefault(false)
}

/**
 * The Android shadow layer radius whose blur sigma matches Skia's drop
 * shadow sigma: Android converts a radius r to `0.57735 * r + 0.5`.
 */
fun shadowRadiusForSigma(sigma: Float): Float = max(0.01f, (sigma - 0.5f) / 0.57735f)
