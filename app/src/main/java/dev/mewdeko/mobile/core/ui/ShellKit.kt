package dev.mewdeko.mobile.core.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.Rgb
import kotlin.math.max
import kotlin.math.min

/**
 * Spacing, radii, and widths of the shell screens (sign in, dashboard setup,
 * the bot picker, the offline screen), matching the iOS Luminous tokens on a
 * 4dp grid.
 */
object ShellDimens {
    /** 4dp. */
    val xxs = 4.dp

    /** 8dp. */
    val xs = 8.dp

    /** 12dp. */
    val s = 12.dp

    /** 16dp. */
    val m = 16.dp

    /** 20dp, the gap between top level blocks of a shell screen. */
    val l = 20.dp

    /** 28dp, the top inset of a shell screen under its bar. */
    val xl = 28.dp

    /** 40dp, the bottom inset of a shell screen. */
    val xxl = 40.dp

    /** 56dp, the top inset of the sign in screen, which has no bar. */
    val hero = 56.dp

    /** The leading and trailing margin of shell content. */
    val inset = 20.dp

    /** The radius of fields and chips drawn as rounded rectangles. */
    val controlRadius = 14.dp

    /** The radius of tiles and grouped surfaces. */
    val tileRadius = 20.dp

    /** The radius of cards. */
    val cardRadius = 24.dp

    /** The radius of the floating action dock. */
    val heroRadius = 32.dp

    /** How wide shell content may grow before it centers on wide screens. */
    val contentMaxWidth = 560.dp
}

/** The meaning of a tinted element, resolved through [ShellRoles.tint]. */
enum class ShellTone {
    /** The palette primary. */
    Brand,

    /** Success and enabled states, the semantic green. */
    Positive,

    /** Errors and destructive actions, the semantic red. */
    Negative,

    /** Warnings, the semantic orange. */
    Caution,

    /** Disabled or inactive states, the palette's muted ink. */
    Neutral,
}

/**
 * The palette derived roles that Material's color scheme does not carry,
 * mirroring the iOS `Palette.roles`.
 *
 * The three semantic hues are the only home of green, red, and orange. Each
 * is anchored at a fixed hue, pulled 15 percent toward the palette primary so
 * it belongs to the guild, then walked in lightness until it holds 3:1 on
 * the canvas.
 */
@Immutable
data class ShellRoles(
    /** The palette primary. */
    val brand: Color,
    /** The semantic green. */
    val positive: Color,
    /** The semantic red. */
    val negative: Color,
    /** The semantic orange. */
    val caution: Color,
    /** The palette's muted ink, for inactive states. */
    val neutral: Color,
    /** Chevrons and placeholders, the muted ink at 70 percent. */
    val textTertiary: Color,
    /** The quiet fill of fields and chips, primary at the `08` tint. */
    val fillQuiet: Color,
    /** A faint separator in the text color. */
    val hairline: Color,
    /** Text links, the primary made readable on the canvas. */
    val link: Color,
) {
    /** The color for [tone]. */
    fun tint(tone: ShellTone): Color = when (tone) {
        ShellTone.Brand -> brand
        ShellTone.Positive -> positive
        ShellTone.Negative -> negative
        ShellTone.Caution -> caution
        ShellTone.Neutral -> neutral
    }
}

/** Resolves [ShellRoles] from the palette and scheme in scope. */
@Composable
fun rememberShellRoles(): ShellRoles {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalGuildPalette.current
    val dark = isDarkScheme()
    val link = readableInk(scheme.primary, scheme.background)
    return remember(palette, dark, scheme.primary, scheme.background, scheme.onSurface, scheme.onSurfaceVariant, link) {
        val canvas = Rgb.fromArgb(scheme.background.toArgb())
        ShellRoles(
            brand = scheme.primary,
            positive = semanticTone(145.0, dark, palette.primary, canvas).color,
            negative = semanticTone(355.0, dark, palette.primary, canvas).color,
            caution = semanticTone(35.0, dark, palette.primary, canvas).color,
            neutral = scheme.onSurfaceVariant,
            textTertiary = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            fillQuiet = scheme.primary.copy(alpha = DashAlpha.Hex08),
            hairline = scheme.onSurface.copy(alpha = if (dark) 0.10f else 0.08f),
            link = link,
        )
    }
}

/**
 * A semantic hue that belongs to the palette: [hueDegrees] at a fixed
 * saturation and lightness, mixed 15 percent toward [primary], then stepped
 * away from [background] until it holds 3:1. The same recipe as the iOS
 * `RGB.semanticTone`.
 */
private fun semanticTone(hueDegrees: Double, dark: Boolean, primary: Rgb, background: Rgb): Rgb {
    val anchor = Rgb.fromHsl(
        h = hueDegrees / 360.0,
        s = if (dark) 0.62 else 0.70,
        l = if (dark) 0.58 else 0.40,
    )
    var tone = anchor.mixed(primary, 0.15)
    val backgroundLuminance = background.luminance
    repeat(8) {
        if (Rgb.contrastRatio(tone.luminance, backgroundLuminance) >= 3.0) return tone
        val (h, s, l) = tone.hsl
        val next = if (dark) min(l + 0.04, 0.96) else max(l - 0.04, 0.04)
        tone = Rgb.fromHsl(h, s, next)
    }
    return tone
}

/**
 * Whether the system has animations switched off, read from the animator
 * duration scale once per composition.
 */
@Composable
private fun rememberShellReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * The shell type ramp, the Android counterpart of the iOS Luminous text
 * styles, built on the app's Material type scale.
 */
object ShellType {
    /** Screen headlines. */
    val display: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.5).sp)

    /** Card and empty state titles. */
    val heading: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp)

    /** Row titles and button labels. */
    val rowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)

    /** Supporting sentences under a title. */
    val meta: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    /** Fine print and hosts. */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    /** The small uppercase line above a title. */
    val overline: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

/** The small uppercase line above a title, in the muted ink. */
@Composable
fun ShellOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ShellType.overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Which screen a [LuminousCanvas] sits behind. */
enum class CanvasStyle {
    /** Shell and hub screens: the palette gradient glow. */
    Hero,

    /** Error screens: the glow is lit by the semantic red. */
    Alert,
}

/** How far the page glow reaches from the top center before it holds its edge tint. */
private val CanvasGlowRadius = 480.dp

/**
 * The page background of shell screens: the neutral canvas with one glow at
 * the top center.
 *
 * The plain hero canvas is exactly [guildGlow]. [CanvasStyle.Alert] draws
 * the same radial recipe in the semantic red, and [drifts] slowly sways the
 * glow center side to side, as the iOS sign in canvas does. Drift is off
 * under reduced motion. The canvas is hidden from accessibility services.
 */
@Composable
fun LuminousCanvas(
    modifier: Modifier = Modifier,
    style: CanvasStyle = CanvasStyle.Hero,
    drifts: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberShellReducedMotion()
    val base = modifier
        .fillMaxSize()
        .background(scheme.background)
        .clearAndSetSemantics { }
    val animate = drifts && !reduced
    if (style == CanvasStyle.Hero && !animate) {
        Box(base.guildGlow())
        return
    }
    val palette = LocalGuildPalette.current
    val roles = rememberShellRoles()
    val scale = if (isDarkScheme()) 1f else 0.5f
    val alert = style == CanvasStyle.Alert
    val start = (if (alert) roles.negative else palette.gradientStart.color).copy(alpha = DashAlpha.Hex15 * scale)
    val middle = (if (alert) roles.negative else palette.gradientMid.color).copy(alpha = DashAlpha.Hex10 * scale)
    val end = (if (alert) roles.negative else palette.gradientEnd.color).copy(alpha = DashAlpha.Hex05 * scale)
    val drift: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "canvasDrift").animateFloat(
            initialValue = -0.06f,
            targetValue = 0.06f,
            animationSpec = infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "canvasDriftX",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Box(
        base.drawBehind {
            val glow = Brush.radialGradient(
                0f to start,
                0.5f to middle,
                1f to end,
                center = Offset(size.width * (0.5f + drift.value), 0f),
                radius = CanvasGlowRadius.toPx(),
            )
            drawRect(glow)
        },
    )
}

/** The elevation of a [glassSurface]. */
enum class SurfaceLevel {
    /** A quiet group: primary at the `08` tint, no border. */
    Group,

    /** A card: the dashboard wash with a `30` border in the tint. */
    Card,

    /** A floating layer such as the action dock: a raised canvas under the wash. */
    Floating,
}

/**
 * Draws a Luminous surface behind the content in [shape], the counterpart of
 * the iOS `glassSurface`.
 *
 * Cards reuse the dashboard card recipe of [GuildCard] ([guildWash] over the
 * container-low tone and a [guildBorder]); [tint] only changes the border.
 */
@Composable
fun Modifier.glassSurface(
    level: SurfaceLevel = SurfaceLevel.Card,
    tint: Color? = null,
    shape: Shape = RoundedCornerShape(ShellDimens.cardRadius),
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val accent = tint ?: scheme.primary
    return when (level) {
        SurfaceLevel.Group -> this.background(accent.copy(alpha = DashAlpha.Hex08), shape)
        SurfaceLevel.Card -> this
            .washed(scheme.surfaceContainerLow, guildWash(), shape)
            .border(guildBorder(accent), shape)

        SurfaceLevel.Floating -> this
            .washed(scheme.surfaceContainerHigh.copy(alpha = 0.96f), guildWash(), shape)
            .border(guildBorder(accent), shape)
    }
}

/**
 * Scales the element to 97 percent and dims it slightly while
 * [interaction] is pressed, the Android counterpart of the iOS
 * `LuminousPressStyle`. Only dims under reduced motion.
 */
@Composable
fun Modifier.pressFeedback(interaction: MutableInteractionSource): Modifier {
    val reduced = rememberShellReducedMotion()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = if (pressed) 0.9f else 1f
    }
}

/** The weight of a [LuminousButton]. */
enum class LuminousButtonVariant {
    /** The solid primary, only for the single main action on a screen. */
    Prominent,

    /** The tonal button for every other action: primary at `20` with a `30` border. */
    Glass,

    /** The tonal recipe in the semantic red, for removing things. */
    Destructive,
}

/**
 * A capsule button on the dashboard button recipe, the counterpart of the
 * iOS `LuminousButtonStyle`.
 *
 * [loading] swaps the leading [icon] for a small spinner, and [spinIcon]
 * rotates the icon instead (still under reduced motion). Disabled buttons
 * fade to 45 percent.
 */
@Composable
fun LuminousButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: LuminousButtonVariant = LuminousButtonVariant.Prominent,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
    compact: Boolean = false,
    loading: Boolean = false,
    icon: ImageVector? = null,
    spinIcon: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val tint = if (variant == LuminousButtonVariant.Destructive) roles.negative else scheme.primary
    val content = if (variant == LuminousButtonVariant.Prominent) scheme.onPrimary else readableInk(tint, scheme.background)
    val interaction = remember { MutableInteractionSource() }
    val shape = CircleShape
    val fill = if (variant == LuminousButtonVariant.Prominent) {
        Modifier.background(tint, shape)
    } else {
        Modifier
            .background(tint.copy(alpha = DashAlpha.Hex20), shape)
            .border(1.dp, tint.copy(alpha = DashAlpha.Hex30), shape)
    }
    Row(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (compact) 36.dp else 48.dp)
            .pressFeedback(interaction)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .then(fill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) stateDescription = "In progress" }
            .padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = content,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else if (icon != null) {
            SpinningIcon(icon = icon, tint = content, spinning = spinIcon)
        }
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.labelLarge else ShellType.rowTitle,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/** An 18dp icon that turns continuously while [spinning], still under reduced motion. */
@Composable
private fun SpinningIcon(icon: ImageVector, tint: Color, spinning: Boolean) {
    val reduced = rememberShellReducedMotion()
    val rotation: State<Float> = if (spinning && !reduced) {
        rememberInfiniteTransition(label = "iconSpin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "iconSpinDegrees",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer { rotationZ = rotation.value },
    )
}

/**
 * A quiet text action in the link color, such as "Sign out" under a list.
 * [tone] recolors it, for example [ShellTone.Negative] for "Forget".
 */
@Composable
fun ShellTextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ShellTone = ShellTone.Brand,
    enabled: Boolean = true,
) {
    val roles = rememberShellRoles()
    val ink = if (tone == ShellTone.Brand) roles.link else readableInk(roles.tint(tone), MaterialTheme.colorScheme.background)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = ink,
            disabledContentColor = ink.copy(alpha = 0.45f),
        ),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(text, style = ShellType.rowTitle)
    }
}

/**
 * An image, such as the Mewdeko logo, inside a primary icon background:
 * primary at `20` with a `30` border.
 */
@Composable
fun LogoOrb(
    painter: Painter,
    modifier: Modifier = Modifier,
    size: OrbSize = OrbSize.ExtraLarge,
) {
    val primary = MaterialTheme.colorScheme.primary
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size.diameter)
            .background(primary.copy(alpha = DashAlpha.Hex20), CircleShape)
            .border(1.dp, primary.copy(alpha = DashAlpha.Hex30), CircleShape)
            .padding(size.diameter * 0.14f)
            .clip(CircleShape),
    )
}

/**
 * The Mewdeko logo in an extra large floating orb, circled by a 2dp halo of
 * the palette's three gradient stops and lit from behind by a soft glow of
 * the first stop. Drawn, never a shadow.
 */
@Composable
fun HaloLogo(painter: Painter, modifier: Modifier = Modifier) {
    val palette = LocalGuildPalette.current
    val dark = isDarkScheme()
    val halo = Brush.sweepGradient(
        listOf(
            palette.gradientStart.color.copy(alpha = 0.6f),
            palette.gradientMid.color.copy(alpha = 0.6f),
            palette.gradientEnd.color.copy(alpha = 0.6f),
            palette.gradientStart.color.copy(alpha = 0.6f),
        ),
    )
    val glow = palette.gradientStart.color.copy(alpha = if (dark) 0.35f else 0.15f)
    val diameter = OrbSize.ExtraLarge.diameter
    Box(
        modifier = modifier
            .size(diameter + 8.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(glow, glow.copy(alpha = 0f)),
                        center = center,
                        radius = size.minDimension / 2f + 24.dp.toPx(),
                    ),
                    radius = size.minDimension / 2f + 24.dp.toPx(),
                )
            }
            .border(2.dp, halo, CircleShape)
            .padding(4.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(diameter)
                .glassSurface(SurfaceLevel.Floating, shape = CircleShape)
                .padding(diameter * 0.14f)
                .clip(CircleShape),
        )
    }
}

/**
 * The leading aligned hero of shell screens: an extra large glyph orb (or a
 * [logo] orb), an [overline], a display [title], and a [message].
 *
 * [pulsing] breathes the orb, as the iOS offline screen's symbol pulse does;
 * it is still under reduced motion.
 */
@Composable
fun ShellHero(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    logo: Painter? = null,
    overline: String? = null,
    message: String? = null,
    tone: ShellTone = ShellTone.Brand,
    pulsing: Boolean = false,
) {
    val roles = rememberShellRoles()
    val reduced = rememberShellReducedMotion()
    val orbAlpha: State<Float> = if (pulsing && !reduced) {
        rememberInfiniteTransition(label = "heroPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "heroPulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ShellDimens.s),
    ) {
        val orbModifier = Modifier
            .padding(bottom = ShellDimens.xs)
            .graphicsLayer { alpha = orbAlpha.value }
        if (logo != null) {
            LogoOrb(painter = logo, modifier = orbModifier)
        } else if (icon != null) {
            GlyphOrb(icon = icon, tint = roles.tint(tone), size = OrbSize.ExtraLarge, modifier = orbModifier)
        }
        if (overline != null) ShellOverline(overline)
        Text(
            text = title,
            style = ShellType.display,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (message != null) {
            Text(
                text = message,
                style = ShellType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A section title with a small leading orb, above a grouped card. */
@Composable
fun ShellSectionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ShellDimens.xxs)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) GlyphOrb(icon = icon, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = ShellType.heading,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A state capsule drawn in a [ShellTone]: a glyph plus a word, never color
 * alone. See [StatePillOn] and [StatePillOff] for the common pair.
 */
@Composable
fun ShellStatePill(text: String, tone: ShellTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    StatePill(text = text, tone = rememberShellRoles().tint(tone), modifier = modifier, icon = icon)
}

/** An enabled or active state, in the semantic green with a check. */
@Composable
fun StatePillOn(modifier: Modifier = Modifier, text: String = "On") {
    ShellStatePill(text = text, tone = ShellTone.Positive, modifier = modifier, icon = Icons.Default.CheckCircle)
}

/** A disabled or inactive state, in the muted ink with a dash. */
@Composable
fun StatePillOff(modifier: Modifier = Modifier, text: String = "Off") {
    ShellStatePill(text = text, tone = ShellTone.Neutral, modifier = modifier, icon = Icons.Default.RemoveCircleOutline)
}

/** An inline notice: a toned orb and a sentence, the counterpart of the iOS `CalloutRow`. */
@Composable
fun ShellCallout(
    text: String,
    modifier: Modifier = Modifier,
    tone: ShellTone = ShellTone.Caution,
    icon: ImageVector = when (tone) {
        ShellTone.Negative -> Icons.Default.Error
        ShellTone.Positive -> Icons.Default.CheckCircle
        ShellTone.Brand, ShellTone.Neutral -> Icons.Default.Info
        ShellTone.Caution -> Icons.Default.Warning
    },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphOrb(icon = icon, tint = rememberShellRoles().tint(tone))
        Text(
            text = text,
            style = ShellType.caption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A small solid orb showing the first letter of [label] in the palette
 * secondary, for items without an image such as a saved dashboard.
 */
@Composable
fun MonogramOrb(label: String, modifier: Modifier = Modifier, size: OrbSize = OrbSize.Small) {
    val scheme = MaterialTheme.colorScheme
    val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "D"
    Box(
        modifier = modifier
            .size(size.diameter)
            .background(scheme.secondary, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = scheme.onSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.diameter.value * 0.46f).sp,
        )
    }
}

/**
 * The shared "nothing here" block: an orb inside two concentric hairline
 * rings, a heading, a centered message, and optional [actions].
 */
@Composable
fun ShellEmptyState(
    title: String,
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val roles = rememberShellRoles()
    val orb = OrbSize.Large.diameter
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ShellDimens.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(orb * 2.1f)
                .padding(bottom = ShellDimens.xxs),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(orb * 2.1f).border(1.dp, roles.hairline, CircleShape))
            Box(Modifier.size(orb * 1.5f).border(1.dp, roles.hairline, CircleShape))
            GlyphOrb(icon = icon, tint = MaterialTheme.colorScheme.primary, size = OrbSize.Large)
        }
        Text(
            text = title,
            style = ShellType.heading,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = message,
            style = ShellType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        if (actions != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ShellDimens.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ShellDimens.s),
                content = actions,
            )
        }
    }
}

/**
 * A single line text field on the quiet fill with a 14dp radius and a
 * primary ring while focused, the counterpart of the iOS Luminous field.
 * [label] is what accessibility services read. The keyboard action runs
 * [onImeAction] when set, otherwise moves to the next field for
 * [ImeAction.Next] and closes the keyboard for anything else.
 */
@Composable
fun ShellField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String = placeholder,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    autoCorrect: Boolean = true,
    onImeAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val ring by animateColorAsState(
        targetValue = if (focused) scheme.primary else scheme.primary.copy(alpha = 0f),
        label = "fieldRing",
    )
    val shape = RoundedCornerShape(ShellDimens.controlRadius)
    val style = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = if (autoCorrect) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = autoCorrect,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onAny = {
                when {
                    onImeAction != null -> onImeAction()
                    imeAction == ImeAction.Next -> focusManager.moveFocus(FocusDirection.Down)
                    else -> focusManager.clearFocus()
                }
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(roles.fillQuiet, shape)
                    .border(1.5.dp, ring, shape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style,
                        color = roles.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * A selectable glass card with a trailing radio check, used for setup
 * choices. [expanded] content, such as text fields, reveals under the
 * header while the card is selected. Selecting an unselected card plays the
 * selection haptic.
 */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    logo: Painter? = null,
    expanded: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(ShellDimens.cardRadius)
    val ring by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.primary.copy(alpha = 0f),
        label = "choiceRing",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(SurfaceLevel.Card, shape = shape)
            .border(1.5.dp, ring, shape)
            .padding(ShellDimens.m),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressFeedback(interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.RadioButton,
                    onClick = {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClick()
                    },
                )
                .clearAndSetSemantics {
                    contentDescription = if (subtitle != null) "$title, $subtitle" else title
                    role = Role.RadioButton
                    this.selected = selected
                },
            horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
            verticalAlignment = Alignment.Top,
        ) {
            if (logo != null) {
                LogoOrb(painter = logo, size = OrbSize.Medium)
            } else if (icon != null) {
                GlyphOrb(icon = icon, tint = scheme.primary, size = OrbSize.Medium)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = title, style = ShellType.heading, color = scheme.onSurface)
                if (subtitle != null) {
                    Text(text = subtitle, style = ShellType.meta, color = scheme.onSurfaceVariant)
                }
            }
            RadioCheck(selected)
        }
        if (expanded != null) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ShellDimens.s),
                    content = expanded,
                )
            }
        }
    }
}

/** The 24dp radio of a [ChoiceCard]: a solid primary check, or an empty ring. */
@Composable
private fun RadioCheck(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.primary.copy(alpha = 0f),
        label = "radioFill",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(fill, CircleShape)
            .then(
                if (selected) Modifier else Modifier.border(1.5.dp, scheme.primary.copy(alpha = DashAlpha.Hex30), CircleShape),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(15.dp))
        }
    }
}

/**
 * A floating glass panel pinned above the navigation bar and keyboard,
 * holding a screen's primary actions, the counterpart of the iOS
 * `actionDock`.
 */
@Composable
fun ActionDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = ShellDimens.s, end = ShellDimens.s, bottom = ShellDimens.xxs),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = ShellDimens.contentMaxWidth + ShellDimens.l)
                .fillMaxWidth()
                .glassSurface(SurfaceLevel.Floating, shape = RoundedCornerShape(ShellDimens.heroRadius))
                .padding(ShellDimens.m),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * The frame of a shell screen: a [LuminousCanvas], an inline top bar whose
 * [title] fades in once the hero scrolls away (none when [title] is null),
 * a scrolling column capped at [ShellDimens.contentMaxWidth] and centered on
 * wide screens, and an optional [dock] of actions.
 */
@Composable
fun ShellScreen(
    modifier: Modifier = Modifier,
    title: String? = null,
    canvas: CanvasStyle = CanvasStyle.Hero,
    drifts: Boolean = false,
    topPadding: Dp = ShellDimens.xl,
    scrollState: ScrollState = rememberScrollState(),
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LuminousCanvas(style = canvas, drifts = drifts)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = { if (title != null) ShellTopBar(title = title, scrollState = scrollState) },
            bottomBar = { if (dock != null) ActionDock(content = dock) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = ShellDimens.contentMaxWidth)
                        .fillMaxWidth()
                        .padding(
                            start = ShellDimens.inset,
                            end = ShellDimens.inset,
                            top = if (title != null) ShellDimens.xs else topPadding,
                            bottom = ShellDimens.xxl,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ShellDimens.l),
                    content = content,
                )
            }
        }
    }
}

/** How far the content scrolls before the inline bar title and backing appear. */
private val TopBarRevealOffset = 140.dp

/**
 * The inline bar of a shell screen: transparent over the hero, then the
 * canvas with a hairline and the centered [title] once the hero has
 * scrolled away.
 */
@Composable
private fun ShellTopBar(title: String, scrollState: ScrollState) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val threshold = with(LocalDensity.current) { TopBarRevealOffset.roundToPx() }
    val revealed by remember(threshold) { derivedStateOf { scrollState.value > threshold } }
    val reveal by animateFloatAsState(if (revealed) 1f else 0f, label = "barReveal")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(scheme.background.copy(alpha = 0.94f * reveal))
                drawRect(
                    color = roles.hairline.copy(alpha = roles.hairline.alpha * reveal),
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = ShellType.rowTitle,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = ShellDimens.xxl)
                .graphicsLayer { alpha = reveal }
                .then(if (revealed) Modifier.semantics { heading() } else Modifier.clearAndSetSemantics { }),
        )
    }
}

/**
 * The launch screen: the drifting canvas with the Mewdeko logo breathing in
 * its primary icon background. Still under reduced motion.
 */
@Composable
fun LaunchGlow(logo: Painter, modifier: Modifier = Modifier) {
    val reduced = rememberShellReducedMotion()
    val breath: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "launchBreath").animateFloat(
            initialValue = 0.85f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "launchBreathAlpha",
        )
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) { contentDescription = "Loading Mewdeko" },
        contentAlignment = Alignment.Center,
    ) {
        LuminousCanvas(drifts = true)
        LogoOrb(painter = logo, modifier = Modifier.graphicsLayer { alpha = breath.value })
    }
}

/**
 * A confirmation dialog for destructive actions in the shell language: the
 * raised canvas, a destructive tonal confirm, and a glass cancel.
 */
@Composable
fun ShellConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = ShellType.heading) },
        text = { Text(message, style = ShellType.meta) },
        confirmButton = {
            LuminousButton(
                text = confirmLabel,
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                variant = LuminousButtonVariant.Destructive,
            )
        },
        dismissButton = {
            LuminousButton(text = "Cancel", onClick = onDismiss, variant = LuminousButtonVariant.Glass)
        },
        shape = RoundedCornerShape(ShellDimens.heroRadius),
        containerColor = scheme.surfaceContainerHigh,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
    )
}
