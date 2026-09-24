package dev.mewdeko.mobile.feature.guildlist

import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.Rgb
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.guildWash
import dev.mewdeko.mobile.core.ui.isDarkScheme
import dev.mewdeko.mobile.feature.guilddetail.home.rememberReducedMotion

/** The corner radius of a guild tile, matching the iOS tile radius. */
private val TileRadius = 20.dp

/** The corner radius of the "Jump back in" card, matching the iOS card radius. */
private val CardRadius = 24.dp

/** The shortest a two column guild tile may be. */
private val TileMinHeight = 116.dp

/** The inner padding of a guild tile. */
private val TilePadding = 14.dp

/** The shortest the "Jump back in" card may be. */
private val JumpCardMinHeight = 180.dp

/** Whether the platform can blur a layer, through `RenderEffect` on API 31 and up. */
private val SupportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The edge, in pixels, an icon is decoded at when the platform cannot blur.
 *
 * Stretching a tiny decode across the tile with bilinear filtering gives a
 * soft color field close enough to a blur on API 26 to 30.
 */
private const val BlurFallbackPx = 10

/** The 1.4 saturation boost the iOS blurred icon applies. */
private val SaturationBoost = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.4f) })

/**
 * Scales the element down slightly while [interaction] is pressed, the
 * Android counterpart of the iOS press style. Static under reduced motion.
 */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource, reduced: Boolean): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * A guild in the Servers grid: the card wash and a palette border with the
 * guild's own blurred icon glowing through it, or its banner when it has
 * one, then the icon, the name and an owner pill.
 *
 * In [singleColumn] layouts, used at large font scales, the tile becomes a
 * row with the name and pill beside the icon.
 */
@Composable
fun GuildTile(
    guild: Guild,
    singleColumn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(TileRadius)
    val interaction = remember { MutableInteractionSource() }
    val reduced = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, reduced)
            .clip(shape)
            .background(base)
            .background(guildWash())
            .border(guildBorder(), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Open server",
                onClick = onClick,
            ),
    ) {
        GuildBackdrop(
            guild = guild,
            iconAlpha = 0.55f,
            bannerScrim = Brush.verticalGradient(
                0f to base.copy(alpha = 0f),
                0.4f to base.copy(alpha = 0.35f),
                1f to base.copy(alpha = 0.94f),
            ),
            modifier = Modifier.matchParentSize(),
        )
        if (singleColumn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(TilePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TileAvatar(guild)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TileName(guild.name, reserveTwoLines = false)
                    if (guild.owner) OwnerPill()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TileMinHeight)
                    .padding(TilePadding),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    TileAvatar(guild)
                    Spacer(Modifier.weight(1f).width(4.dp))
                    if (guild.owner) OwnerPill()
                }
                Spacer(Modifier.height(10.dp))
                Spacer(Modifier.weight(1f))
                TileName(guild.name, reserveTwoLines = true)
            }
        }
    }
}

/**
 * The 44dp guild icon on a tile, on a solid canvas disc with a thin ring so
 * it separates from a busy banner.
 */
@Composable
private fun TileAvatar(guild: Guild) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Avatar(
        url = guild.iconUrl,
        contentDescription = null,
        size = 44,
        fallbackText = guild.name,
        ring = if (guild.bannerUrl != null) BorderStroke(2.dp, base) else null,
        modifier = Modifier.background(base, CircleShape),
    )
}

/**
 * A guild name on up to two lines. With [reserveTwoLines] it always takes
 * two lines of height and sits on the bottom one, so every tile in a grid
 * row is the same height whether its name wraps or not.
 */
@Composable
private fun TileName(name: String, reserveTwoLines: Boolean) {
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val text = @Composable {
        Text(
            text = name,
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (!reserveTwoLines) {
        text()
        return
    }
    val lineHeight = if (style.lineHeight.isSpecified) style.lineHeight else 20.sp
    val twoLines = with(LocalDensity.current) { (lineHeight * 2).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = twoLines),
        contentAlignment = Alignment.BottomStart,
    ) {
        text()
    }
}

/**
 * The large "Jump back in" card for the last opened guild: the guild's
 * blurred icon or banner behind the card wash, fading into the page canvas,
 * with a ringed 56dp icon, the name, and an owner or admin pill.
 */
@Composable
fun JumpBackInCard(guild: Guild, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CardRadius)
    val interaction = remember { MutableInteractionSource() }
    val reduced = rememberReducedMotion()
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val canvas = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = JumpCardMinHeight)
            .pressScale(interaction, reduced)
            .clip(shape)
            .background(base)
            .background(guildWash())
            .border(guildBorder(), shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = "Open server",
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {},
    ) {
        GuildBackdrop(
            guild = guild,
            iconAlpha = 1f,
            bannerScrim = Brush.verticalGradient(
                0f to base.copy(alpha = 0f),
                0.45f to base.copy(alpha = 0.3f),
                1f to base.copy(alpha = 0.7f),
            ),
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(canvas.copy(alpha = 0f), canvas.copy(alpha = 0.75f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Avatar(
                url = guild.iconUrl,
                contentDescription = null,
                size = 56,
                fallbackText = guild.name,
                ring = BorderStroke(3.dp, canvas),
                modifier = Modifier.background(canvas, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "JUMP BACK IN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "Jump back in" },
                )
                Text(
                    text = guild.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (guild.owner) {
                    OwnerPill()
                } else {
                    LegiblePill(
                        text = "Admin",
                        icon = Icons.Default.AdminPanelSettings,
                        seed = LocalGuildPalette.current.secondary,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The layers behind a tile or card: the guild's blurred, saturated icon at
 * [iconAlpha], then its banner scaled to fill once it loads, with
 * [bannerScrim] fading it into the card at the bottom and the card wash laid
 * back over it so the palette still reads. With no banner, or while it
 * loads, only the blurred icon shows.
 */
@Composable
private fun GuildBackdrop(
    guild: Guild,
    iconAlpha: Float,
    bannerScrim: Brush,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val banner = guild.bannerUrl
    Box(modifier = modifier.clearAndSetSemantics {}) {
        BlurredIcon(url = guild.iconUrl, alpha = iconAlpha, modifier = Modifier.matchParentSize())
        if (banner != null) {
            var loaded by remember(banner) { mutableStateOf(false) }
            val scrimAlpha by animateFloatAsState(
                targetValue = if (loaded) 1f else 0f,
                animationSpec = tween(350),
                label = "bannerScrim",
            )
            AsyncImage(
                model = remember(banner) {
                    ImageRequest.Builder(context).data(banner).crossfade(350).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize(),
            )
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(scrimAlpha)
                        .background(bannerScrim)
                        .background(guildWash()),
                )
            }
        }
    }
}

/**
 * A guild icon scaled to fill, blurred and saturated into a color field.
 *
 * Blurs through `RenderEffect` on API 31 and up. Below that the icon is
 * decoded at a few pixels and stretched with bilinear filtering, which
 * reads as the same soft wash of the icon's colors.
 */
@Composable
private fun BlurredIcon(url: String?, alpha: Float, modifier: Modifier = Modifier) {
    if (url == null) return
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (!SupportsBlur) {
                    size(BlurFallbackPx)
                    precision(Precision.EXACT)
                }
            }
            .crossfade(300)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        colorFilter = SaturationBoost,
        filterQuality = FilterQuality.Low,
        modifier = modifier.then(if (SupportsBlur) Modifier.blur(40.dp) else Modifier),
    )
}

/** The Owner pill with a crown, in the palette primary. */
@Composable
fun OwnerPill() {
    LegiblePill(
        text = "Owner",
        icon = Icons.Default.WorkspacePremium,
        seed = LocalGuildPalette.current.primary,
    )
}

/**
 * A state pill that stays legible over any banner or blurred icon.
 *
 * The backing is opaque: the page canvas with the [seed] hue mixed in at
 * the dashboard's `20` tint, dark in the dark theme and light in the light
 * one. The label is the seed walked until it holds 4.5:1 on that backing,
 * and a thin ring of the same ink outlines it against bright images.
 */
@Composable
private fun LegiblePill(text: String, icon: ImageVector, seed: Rgb) {
    val palette = LocalGuildPalette.current
    val dark = isDarkScheme()
    val role = remember(palette, seed, dark) { palette.toneRole(seed, dark) }
    Surface(
        shape = CircleShape,
        color = role.container,
        contentColor = role.onContainer,
        border = BorderStroke(1.dp, role.onContainer.copy(alpha = DashAlpha.Hex40)),
        modifier = Modifier.heightIn(min = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(13.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A placeholder in the shape of a guild tile that breathes softly while the
 * list loads. Static under reduced motion. Only the first announces
 * "Loading servers"; the rest are hidden from accessibility services.
 */
@Composable
fun GuildSkeletonTile(announce: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(TileRadius)
    val quiet = MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex20)
    val reduced = rememberReducedMotion()
    val breath: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "guildSkeleton").animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "guildSkeletonAlpha",
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = breath.value }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .background(guildWash())
            .border(guildBorder(), shape)
            .heightIn(min = TileMinHeight)
            .padding(TilePadding)
            .clearAndSetSemantics {
                if (announce) contentDescription = "Loading servers"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(quiet, CircleShape),
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 110.dp, height = 12.dp)
                .background(quiet, CircleShape),
        )
        Box(
            Modifier
                .size(width = 70.dp, height = 10.dp)
                .background(quiet, CircleShape),
        )
    }
}
