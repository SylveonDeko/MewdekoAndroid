package dev.mewdeko.mobile.feature.xp

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the designer canvas is editable or shows the card as the bot draws it. */
enum class XpDesignerMode { EDIT, PREVIEW }

/** The tabs of the designer's bottom sheet. */
enum class XpSheetTab { LAYERS, PROPERTIES, TOOLS }

/** The smallest zoom the designer allows. */
const val XpMinZoom = 0.5f

/** The largest zoom the designer allows. */
const val XpMaxZoom = 3f

/**
 * View state for the rank card designer: zoom, pan, selection, mode, grid
 * and snap toggles, and the bottom sheet.
 *
 * Held by [XpViewModel] so it survives recomposition and configuration
 * changes, but kept out of [XpState]: pan and zoom change on every gesture
 * frame and are read only in the canvas draw phase, so routing them through
 * the screen's state flow would recompose the whole screen per frame. None
 * of this is part of an undo snapshot.
 *
 * Coordinates: the canvas maps card pixels to view pixels as
 * `view = pan + card * zoom * density`, so a zoom of one draws one card
 * pixel per dp, the same scale as a point on iOS.
 */
@Stable
class XpDesignerController {
    /** Whether the full-screen designer is showing. */
    var open by mutableStateOf(false)

    /** Card pixels per dp. */
    var zoom by mutableFloatStateOf(1f)
        private set

    /** Horizontal offset of the card origin, in view pixels. */
    var panX by mutableFloatStateOf(0f)
        private set

    /** Vertical offset of the card origin, in view pixels. */
    var panY by mutableFloatStateOf(0f)
        private set

    /** The selected element id, built-in or custom. */
    var selectedId by mutableStateOf<String?>(null)

    /** The element under a hovering pointer, if any. */
    var hoveredId by mutableStateOf<String?>(null)

    /** Edit or preview. */
    var mode by mutableStateOf(XpDesignerMode.EDIT)

    /** Whether the viewer's own stats replace the sample member. */
    var useRealData by mutableStateOf(false)

    /** Whether the grid is drawn. */
    var showGrid by mutableStateOf(false)

    /** Whether committed positions snap to the grid. */
    var snapToGrid by mutableStateOf(true)

    /** The grid spacing in card pixels, 5 to 50 in steps of 5. */
    var gridSize by mutableIntStateOf(10)

    /** Whether the rulers are drawn outside the card's top and left edges. */
    var showRulers by mutableStateOf(false)

    /** Whether width and height edits on image elements stay equal. */
    var lockProportions by mutableStateOf(false)

    /** Whether the bottom sheet is showing. */
    var sheetOpen by mutableStateOf(false)

    /** The bottom sheet's active tab. */
    var sheetTab by mutableStateOf(XpSheetTab.LAYERS)

    /** Canvas width in view pixels. */
    var viewWidth by mutableFloatStateOf(0f)
        private set

    /** Canvas height in view pixels. */
    var viewHeight by mutableFloatStateOf(0f)
        private set

    /** Device pixels per dp, captured with the view size. */
    var density by mutableFloatStateOf(1f)
        private set

    /** View pixels per card pixel. */
    val scale: Float get() = zoom * density

    /**
     * Records the canvas size and re-centres the card at the fit zoom
     * whenever the view or the card size changes.
     */
    fun onViewSize(width: Float, height: Float, density: Float, cardWidth: Int, cardHeight: Int) {
        val changed = width != viewWidth || height != viewHeight || density != this.density
        viewWidth = width
        viewHeight = height
        this.density = density
        if (changed) resetView(cardWidth, cardHeight)
    }

    /**
     * The initial zoom: the card fitted inside the view with a 12dp margin
     * on each side, clamped to 50 to 100 percent.
     */
    fun fitZoom(cardWidth: Int, cardHeight: Int): Float {
        if (viewWidth <= 0f || viewHeight <= 0f) return 1f
        val widthDp = viewWidth / density
        val heightDp = viewHeight / density
        val fit = minOf(
            (widthDp - 24f) / cardWidth.coerceAtLeast(1),
            (heightDp - 24f) / cardHeight.coerceAtLeast(1),
        )
        return fit.coerceIn(XpMinZoom, 1f)
    }

    /** Restores the fit zoom and centres the card. */
    fun resetView(cardWidth: Int, cardHeight: Int) {
        zoom = fitZoom(cardWidth, cardHeight)
        panX = (viewWidth - cardWidth * scale) / 2f
        panY = (viewHeight - cardHeight * scale) / 2f
    }

    /** Pans by a view-space delta. */
    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    /**
     * Multiplies the zoom by [factor], clamped, keeping the view point
     * ([centerX], [centerY]) fixed over the same card point.
     */
    fun zoomAbout(centerX: Float, centerY: Float, factor: Float) {
        val next = (zoom * factor).coerceIn(XpMinZoom, XpMaxZoom)
        if (next == zoom) return
        val ratio = next / zoom
        panX = centerX - (centerX - panX) * ratio
        panY = centerY - (centerY - panY) * ratio
        zoom = next
    }

    /** Steps the zoom by [delta] about the view centre, for the toolbar buttons. */
    fun stepZoom(delta: Float) {
        val target = (Math.round((zoom + delta) * 10f) / 10f).coerceIn(XpMinZoom, XpMaxZoom)
        zoomAbout(viewWidth / 2f, viewHeight / 2f, target / zoom)
    }

    /** Converts a view point to card pixels. */
    fun toCard(viewX: Float, viewY: Float): XpPoint =
        XpPoint((viewX - panX) / scale, (viewY - panY) / scale)

    /** Opens the bottom sheet on [tab]. */
    fun openSheet(tab: XpSheetTab) {
        sheetTab = tab
        sheetOpen = true
    }

    /** Clears the selection, falling back from the Properties tab. */
    fun clearSelection() {
        selectedId = null
        if (sheetTab == XpSheetTab.PROPERTIES) sheetTab = XpSheetTab.LAYERS
    }
}
