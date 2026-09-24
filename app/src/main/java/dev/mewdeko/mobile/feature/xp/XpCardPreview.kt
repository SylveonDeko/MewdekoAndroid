package dev.mewdeko.mobile.feature.xp

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withScale
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.theme.LocalGuildPalette

/** The guild palette as the renderer's chrome colours. */
@Composable
@ReadOnlyComposable
fun rememberXpCardInk(): XpCardInk {
    val palette = LocalGuildPalette.current
    return XpCardInk(
        primary = palette.primary.color.toArgb(),
        secondary = palette.secondary.color.toArgb(),
        accent = palette.accent.color.toArgb(),
        text = MaterialTheme.colorScheme.onSurface.toArgb(),
    )
}

/**
 * The card background: the custom URL through [images] when one is set,
 * otherwise the bundled default. The flag says whether the default is in
 * use, which draws the palette tint under its transparent areas.
 */
fun xpBackground(url: String, assets: XpCardAssets, images: XpImageCache): Pair<Bitmap?, Boolean> =
    if (url.isBlank()) assets.defaultBackground to true else images.get(url) to false

/**
 * The parent screen's rank card preview.
 *
 * Draws through the same [XpCardRenderer] the designer uses, in preview
 * mode, fitted to the available width, so the two can never disagree. The
 * card is sized to the background's natural size when it has loaded, which
 * is the size the bot renders at, and [onBackgroundSize] reports that size
 * so the stored template size can be synced to it the way the web editors
 * do on load.
 */
@Composable
fun XpCardPreview(
    template: XpTemplate,
    customElements: List<XpCustomElement>,
    builtInOrder: List<String>,
    backgroundUrl: String,
    data: XpCardData,
    modifier: Modifier = Modifier,
    onBackgroundSize: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets = remember { XpCardAssets.get(context) }
    val images = remember { XpImageCache(context, scope) }
    val renderer = remember { XpCardRenderer(assets) }
    val ink = rememberXpCardInk()
    val palette = LocalGuildPalette.current

    val (background, isDefault) = xpBackground(backgroundUrl, assets, images)
    val reportSize by rememberUpdatedState(onBackgroundSize)
    LaunchedEffect(background, template.id) {
        background?.let { reportSize(it.width, it.height) }
    }
    val sized = if (background != null) {
        template.copy(outputSizeX = background.width, outputSizeY = background.height)
    } else {
        template
    }
    val scene = XpCardScene(
        template = sized,
        customElements = customElements,
        builtInOrder = sanitizeBuiltInOrder(builtInOrder),
        data = data,
        background = background,
        defaultBackground = isDefault,
    )
    val ratio = scene.cardWidth.toFloat() / scene.cardHeight.toFloat()
    val shape = RoundedCornerShape(12.dp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .background(GuildPalette.SlateSidebar)
            .background(xpEditorBackdrop(palette))
            .border(1.dp, palette.primary.color.copy(alpha = DashAlpha.Hex30), shape),
    ) {
        val scale = size.width / scene.cardWidth
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withScale(scale, scale) {
                renderer.draw(this, scene, XpRenderOptions(mode = XpDesignerMode.PREVIEW), ink) { images.get(it) }
            }
        }
    }
}

/**
 * The designer's backdrop glow, drawn over the dashboard's darkest slate
 * ([GuildPalette.SlateSidebar]): the guild gradient's start at the `20`
 * tint in the centre fading to its end at the `10` tint.
 */
fun xpEditorBackdrop(palette: GuildPalette): Brush = Brush.radialGradient(
    listOf(
        palette.gradientStart.color.copy(alpha = DashAlpha.Hex20),
        palette.gradientEnd.color.copy(alpha = DashAlpha.Hex10),
    ),
)
