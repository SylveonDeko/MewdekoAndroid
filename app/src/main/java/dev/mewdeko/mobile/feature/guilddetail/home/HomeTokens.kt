package dev.mewdeko.mobile.feature.guilddetail.home

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.ToneRole
import dev.mewdeko.mobile.core.ui.EmphasizedDecelerateEasing
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** Spacing and sizing shared by every block of the guild home. */
object HomeDimens {
    /** Horizontal page inset. */
    val inset = 16.dp

    /** Gap between top-level items. */
    val sectionGap = 28.dp

    /** Gap between the header, headline, previews and chips of a band. */
    val bandInner = 14.dp

    /** Gap between grid cells. */
    val gridGap = 12.dp

    /** Inner padding of content cards. */
    val cardPadding = 16.dp

    /** Minimum height of a pulse tile. */
    val pulseMinHeight = 148.dp

    /** Height of the small visual at the foot of a pulse tile. */
    val pulseVisual = 40.dp

    /** Height of the member flow chart. */
    val flowChartHeight = 180.dp

    /** Minimum height of a band metric chip. */
    val chipMinHeight = 64.dp
}

/** Motion tokens for the guild home, following the Material 3 spring scheme. */
object HomeMotion {
    /** Fast spatial spring for small movements. */
    fun <T> spatialFast(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Default spatial spring. */
    fun <T> spatialDefault(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** Slow spatial spring for large movements. */
    fun <T> spatialSlow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)

    /** A playful spring with visible overshoot. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 300f)

    /** Non-spatial spring for color and alpha. */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** Material's emphasized decelerate curve. */
    val EmphasizedDecelerate: Easing = EmphasizedDecelerateEasing
}

/**
 * The four chapter colors of the guild home.
 *
 * Community is the primary set, entertainment the secondary set, automation
 * is derived from the palette's middle gradient stop, and safety is the
 * tertiary set, which the theme derives from the palette accent.
 */
@Immutable
data class HomeRoles(
    val community: ToneRole,
    val entertainment: ToneRole,
    val automation: ToneRole,
    val safety: ToneRole,
)

/** Resolves [HomeRoles] from the current theme and guild palette. */
@Composable
fun rememberHomeRoles(): HomeRoles {
    val scheme = MaterialTheme.colorScheme
    val palette = LocalGuildPalette.current
    val dark = scheme.background.luminance() < 0.5f
    val target = remember(palette, dark) { palette.toneRole(palette.gradientMid, dark) }
    val spec = tween<Color>(450)
    val automation = ToneRole(
        color = animateColorAsState(target.color, spec, label = "automationColor").value,
        onColor = animateColorAsState(target.onColor, spec, label = "automationOn").value,
        container = animateColorAsState(target.container, spec, label = "automationContainer").value,
        onContainer = animateColorAsState(target.onContainer, spec, label = "automationOnContainer").value,
    )
    return HomeRoles(
        community = ToneRole(
            scheme.primary,
            scheme.onPrimary,
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
        ),
        entertainment = ToneRole(
            scheme.secondary,
            scheme.onSecondary,
            scheme.secondaryContainer,
            scheme.onSecondaryContainer,
        ),
        automation = automation,
        safety = ToneRole(
            scheme.tertiary,
            scheme.onTertiary,
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
        ),
    )
}

/**
 * The role's own color when it holds 3:1 (WCAG large text) on the role's
 * container, otherwise the on-container color.
 *
 * Headline numbers, icons and sparklines on a tonal tile use this so they
 * read in the guild hue, while very light hues such as yellow on a pastel
 * container in the light theme fall back to the darker content color.
 */
fun ToneRole.emphasis(): Color = readableOn(container)

/**
 * The role's own color when it holds 3:1 against [surface], otherwise the
 * on-container color, which is always the high-contrast end of the role in
 * the current theme.
 */
fun ToneRole.readableOn(surface: Color): Color {
    val fg = color.luminance()
    val bg = surface.luminance()
    val ratio = (max(fg, bg) + 0.05f) / (min(fg, bg) + 0.05f)
    return if (ratio >= 3f) color else onContainer
}

/**
 * Whether the system has animations switched off.
 *
 * Reads the animator duration scale once per composition; a scale of zero
 * means the user asked for no motion.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Whether the font scale is large enough that grids should collapse to one column. */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= 1.5f

/** The font scale, capped at 1.3, for fixed heights that sit next to text. */
@Composable
fun fontScaleClamp(): Float = min(LocalDensity.current.fontScale, 1.3f)

/** Tabular figures, so counts do not jitter as they change. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/** The standard horizontal page inset. */
fun Modifier.homeInset(): Modifier = padding(horizontal = HomeDimens.inset)

/**
 * A number that counts to its value.
 *
 * On first show it counts up from zero once data arrives, and on later
 * changes it moves from the previous value; under reduced motion it snaps.
 * A null [value] renders [placeholder] under a skeleton, which keeps the
 * width stable while loading. Values above 99,999 are compacted when
 * [compact] is set.
 */
@Composable
fun AnimatedCount(
    value: Long?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    placeholder: String = "12,345",
) {
    val reduced = rememberReducedMotion()
    var settled by rememberSaveable { mutableStateOf(-1L) }
    val animated = remember { Animatable(if (settled >= 0) settled.toFloat() else 0f) }
    LaunchedEffect(value, reduced) {
        val target = value ?: return@LaunchedEffect
        if (reduced) {
            animated.snapTo(target.toFloat())
        } else {
            animated.animateTo(target.toFloat(), spring(stiffness = 200f))
        }
        settled = target
    }

    val shown: Long? = when {
        value == null -> null
        reduced || settled == value -> value
        else -> animated.value.roundToLong()
    }
    val text = when {
        shown == null -> placeholder
        compact && shown > 99_999 -> HomeSeries.compact(shown)
        else -> "%,d".format(shown)
    }
    Text(
        text = text,
        style = style.tabular(),
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.skeleton(visible = value == null),
    )
}

/**
 * Covers the content with a pulsing placeholder block while [visible].
 *
 * The block takes the content's size and [shape], so a placeholder string
 * keeps the final layout. It announces "Loading" instead of the placeholder.
 */
@Composable
fun Modifier.skeleton(visible: Boolean, shape: Shape = MaterialTheme.shapes.small): Modifier {
    if (!visible) return this
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    val reduced = rememberReducedMotion()
    val pulse: State<Float> = if (reduced) {
        remember { mutableFloatStateOf(0.8f) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "skeletonAlpha",
        )
    }
    return this
        .clearAndSetSemantics { contentDescription = "Loading" }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, color, alpha = pulse.value)
        }
}

/** Keys whose one-time entrance has already played on this screen. */
@Stable
class EnteredKeys {
    private val keys = HashSet<String>()

    /** Whether [key] has already entered. */
    operator fun contains(key: String): Boolean = key in keys

    /** Records that [key] has entered. */
    fun add(key: String) {
        keys.add(key)
    }
}

/**
 * Rises a block 16dp into place and fades it from 60% the first time [key]
 * appears, and never again, so scrolling back does not replay it.
 */
@Composable
fun Modifier.riseOnce(key: String, entered: EnteredKeys, delayMillis: Int = 0): Modifier {
    val reduced = rememberReducedMotion()
    val progress = remember(key) { Animatable(if (reduced || key in entered) 1f else 0f) }
    LaunchedEffect(key) {
        entered.add(key)
        if (progress.value < 1f) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 380f))
        }
    }
    val lift = with(LocalDensity.current) { 16.dp.toPx() }
    return graphicsLayer {
        val p = progress.value
        translationY = (1f - p) * lift
        alpha = 0.6f + 0.4f * p
    }
}

/** A section title row with an optional trailing element. */
@Composable
fun HomeSectionHeader(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}
