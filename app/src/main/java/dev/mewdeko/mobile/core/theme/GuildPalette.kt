package dev.mewdeko.mobile.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The web dashboard's alpha steps.
 *
 * The dashboard writes alpha as a two digit hex suffix on a six digit color
 * (`{primary}20`), so each step is that hex byte over 255, not a percent:
 * `20` is 12.5 percent and `30` is 18.8 percent. Every palette tint on
 * Android uses these so the app matches the dashboard exactly.
 */
object DashAlpha {
    /** `05`: the far edge of the page glow. */
    const val Hex05 = 0x05 / 255f

    /** `08`: list rows and quiet row surfaces. */
    const val Hex08 = 0x08 / 255f

    /** `10`: card wash edges, the page glow middle, setup chips, chart grid lines. */
    const val Hex10 = 0x10 / 255f

    /** `15`: card wash middle, the page glow center, selected rows. */
    const val Hex15 = 0x15 / 255f

    /** `20`: badges, chips, icon backgrounds, tonal buttons. */
    const val Hex20 = 0x20 / 255f

    /** `30`: card, badge and button borders. */
    const val Hex30 = 0x30 / 255f

    /** `40`: the dashed setup chip border. */
    const val Hex40 = 0x40 / 255f
}

/**
 * A guild-derived seed palette.
 *
 * These nine slots seed a Material 3 [ColorScheme] via [toColorScheme] so
 * standard M3 components adopt the guild's identity without per-view color
 * plumbing; the gradient slots remain available for the decorative headers
 * that Material's roles do not cover.
 */
@Immutable
data class GuildPalette(
    val primary: Rgb,
    val secondary: Rgb,
    val accent: Rgb,
    val muted: Rgb,
    val gradientStart: Rgb,
    val gradientMid: Rgb,
    val gradientEnd: Rgb,
) {
    /** The decorative header gradient, brightest first. */
    val gradient: List<Color>
        get() = listOf(gradientStart.color, gradientMid.color, gradientEnd.color)

    /**
     * Projects this palette onto a full Material 3 color scheme.
     *
     * Mirrors the web dashboard: in the dark theme the page and card base is
     * the dashboard body's `#1a202c` ([SlatePage]), the lowest container is
     * the sidebar's `#121828` ([SlateSidebar]), and the higher containers are
     * lighter slate steps. None of these are tinted by the palette. The guild
     * shows up as solid accent ink (primary, secondary, tertiary) and as the
     * three container roles from [toneRole], which are the accent at the
     * dashboard's `20` tint composited over the surface.
     */
    fun toColorScheme(dark: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val p = if (dark) primary.adjustedForDarkUi() else primary.adjustedForLightUi()
        val s = if (dark) secondary.adjustedForDarkUi() else secondary.adjustedForLightUi()
        val a = if (dark) accent.adjustedForDarkUi() else accent.adjustedForLightUi()
        val primaryRole = toneRole(primary, dark)
        val secondaryRole = toneRole(secondary, dark)
        val tertiaryRole = toneRole(accent, dark)

        return if (dark) {
            base.copy(
                primary = primary.color,
                onPrimary = p.toned(0.6, 0.14).color,
                primaryContainer = primaryRole.container,
                onPrimaryContainer = primaryRole.onContainer,
                secondary = secondary.color,
                onSecondary = s.toned(0.5, 0.14).color,
                secondaryContainer = secondaryRole.container,
                onSecondaryContainer = secondaryRole.onContainer,
                tertiary = accent.color,
                onTertiary = a.toned(0.6, 0.14).color,
                tertiaryContainer = tertiaryRole.container,
                onTertiaryContainer = tertiaryRole.onContainer,
                onSurfaceVariant = muted.color,
                inversePrimary = p.atLightness(0.4).color,
                background = SlatePage,
                onBackground = Color(0xFFF1F5F9),
                surface = SlatePage,
                onSurface = Color(0xFFF1F5F9),
                surfaceVariant = Color(0xFF2C3446),
                surfaceDim = SlateSidebar,
                surfaceBright = Color(0xFF2C3446),
                surfaceContainerLowest = SlateSidebar,
                surfaceContainerLow = SlatePage,
                surfaceContainer = Color(0xFF1E2534),
                surfaceContainerHigh = Color(0xFF252D3D),
                surfaceContainerHighest = Color(0xFF2C3446),
                inverseSurface = Color(0xFFE2E8F0),
                inverseOnSurface = Color(0xFF1A202C),
                outline = Color(0xFF475569),
                outlineVariant = Color(0xFF334155),
            )
        } else {
            base.copy(
                primary = p.atLightness(0.42).color,
                onPrimary = Color.White,
                primaryContainer = primaryRole.container,
                onPrimaryContainer = primaryRole.onContainer,
                secondary = s.atLightness(0.42).color,
                onSecondary = Color.White,
                secondaryContainer = secondaryRole.container,
                onSecondaryContainer = secondaryRole.onContainer,
                tertiary = a.atLightness(0.42).color,
                onTertiary = Color.White,
                tertiaryContainer = tertiaryRole.container,
                onTertiaryContainer = tertiaryRole.onContainer,
                inversePrimary = p.atLightness(0.75).color,
                background = Color(0xFFF8FAFC),
                onBackground = Color(0xFF0F172A),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F172A),
                surfaceVariant = Color(0xFFE2E8F0),
                onSurfaceVariant = Color(0xFF475569),
                surfaceDim = Color(0xFFE2E8F0),
                surfaceBright = Color(0xFFFFFFFF),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF1F5F9),
                surfaceContainer = Color(0xFFE9EEF5),
                surfaceContainerHigh = Color(0xFFE2E8F0),
                surfaceContainerHighest = Color(0xFFD9E0EA),
                inverseSurface = Color(0xFF1E293B),
                inverseOnSurface = Color(0xFFF1F5F9),
                outline = Color(0xFF94A3B8),
                outlineVariant = Color(0xFFCBD5E1),
            )
        }
    }

    /**
     * Builds a Material-style tone role for a palette slot.
     *
     * The color is the solid accent ink. The container is that ink at the
     * dashboard's `20` tint ([DashAlpha.Hex20]) composited over the neutral
     * surface, the same fill as a dashboard badge or secondary button. The
     * "on container" color is the solid ink again, walked by [readableInk]
     * until it holds 4.5:1 on the container, so pale hues such as yellow get
     * a darker variant in the light theme.
     */
    fun toneRole(rgb: Rgb, dark: Boolean): ToneRole {
        val s = if (dark) rgb else rgb.adjustedForLightUi()
        val inkLightness = if (dark) rgb.hsl.third else 0.42
        val ink = if (dark) rgb else s.atLightness(inkLightness)
        val surface = if (dark) NeutralDarkSurface else NeutralLightSurface
        val container = surface.mixed(ink, DashAlpha.Hex20.toDouble())
        return ToneRole(
            color = ink.color,
            onColor = if (dark) s.toned(0.5, 0.14).color else Color.White,
            container = container.color,
            onContainer = readableInk(s, inkLightness, container, dark).color,
        )
    }

    /**
     * The solid ink of [seed] that reads on [container].
     *
     * Starts at [lightness] and steps away from the container (lighter in the
     * dark theme, darker in the light theme) until the WCAG contrast reaches
     * 4.5:1.
     */
    private fun readableInk(seed: Rgb, lightness: Double, container: Rgb, dark: Boolean): Rgb {
        val step = if (dark) 0.03 else -0.03
        val background = container.luminance
        var current = lightness
        repeat(14) {
            val candidate = seed.atLightness(current)
            if (Rgb.contrastRatio(candidate.luminance, background) >= 4.5) return candidate
            current = (current + step).coerceIn(0.05, 0.95)
        }
        return seed.atLightness(current)
    }

    companion object {
        /**
         * The dashboard's default palette (`DEFAULT_PALETTE` in `colorStore.ts`),
         * used before an icon has been processed and whenever extraction fails.
         */
        val Default: GuildPalette = DashboardColorStore.Default.toGuildPalette()

        /** The dashboard body background (`app.css`), the dark page and card base. */
        val SlatePage = Color(0xFF1A202C)

        /** The dashboard sidebar background (`colorStore.background`), the lowest dark container. */
        val SlateSidebar = Color(0xFF121828)

        /** The dark surface every dark tint composites over. */
        private val NeutralDarkSurface: Rgb = Rgb.fromHex("#1a202c")

        /** The light surface every light tint composites over. */
        private val NeutralLightSurface: Rgb = Rgb.fromHex("#ffffff")
    }
}
