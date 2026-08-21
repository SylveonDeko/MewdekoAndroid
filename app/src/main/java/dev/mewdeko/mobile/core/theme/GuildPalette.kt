package dev.mewdeko.mobile.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
     * Container and "on" roles are derived by lightness so that contrast holds
     * in both themes rather than being hand-tuned per screen.
     */
    fun toColorScheme(dark: Boolean): ColorScheme {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val p = if (dark) primary.adjustedForDarkUi() else primary.adjustedForLightUi()
        val s = if (dark) secondary.adjustedForDarkUi() else secondary.adjustedForLightUi()
        val a = if (dark) accent.adjustedForDarkUi() else accent.adjustedForLightUi()

        return if (dark) {
            base.copy(
                primary = p.atLightness(0.72).color,
                onPrimary = p.toned(0.6, 0.14).color,
                primaryContainer = p.toned(0.5, 0.26).color,
                onPrimaryContainer = p.atLightness(0.9).color,
                secondary = s.atLightness(0.7).color,
                onSecondary = s.toned(0.5, 0.14).color,
                secondaryContainer = s.toned(0.4, 0.24).color,
                onSecondaryContainer = s.atLightness(0.9).color,
                tertiary = a.atLightness(0.72).color,
                onTertiary = a.toned(0.6, 0.14).color,
                tertiaryContainer = a.toned(0.5, 0.26).color,
                onTertiaryContainer = a.atLightness(0.9).color,
                background = p.toned(0.22, 0.07).color,
                onBackground = p.toned(0.06, 0.95).color,
                surface = p.toned(0.22, 0.08).color,
                onSurface = p.toned(0.06, 0.95).color,
                surfaceVariant = p.toned(0.18, 0.16).color,
                onSurfaceVariant = muted.color,
                surfaceContainerLowest = p.toned(0.24, 0.05).color,
                surfaceContainerLow = p.toned(0.22, 0.09).color,
                surfaceContainer = p.toned(0.2, 0.12).color,
                surfaceContainerHigh = p.toned(0.19, 0.15).color,
                surfaceContainerHighest = p.toned(0.18, 0.19).color,
                outline = p.toned(0.15, 0.45).color,
                outlineVariant = p.toned(0.15, 0.26).color,
                inverseSurface = p.toned(0.1, 0.9).color,
                inverseOnSurface = p.toned(0.2, 0.12).color,
                inversePrimary = p.atLightness(0.4).color,
            )
        } else {
            base.copy(
                primary = p.atLightness(0.42).color,
                onPrimary = Color.White,
                primaryContainer = p.toned(0.5, 0.88).color,
                onPrimaryContainer = p.atLightness(0.18).color,
                secondary = s.atLightness(0.42).color,
                onSecondary = Color.White,
                secondaryContainer = s.toned(0.4, 0.9).color,
                onSecondaryContainer = s.atLightness(0.18).color,
                tertiary = a.atLightness(0.42).color,
                onTertiary = Color.White,
                tertiaryContainer = a.toned(0.5, 0.9).color,
                onTertiaryContainer = a.atLightness(0.18).color,
                background = p.toned(0.28, 0.985).color,
                onBackground = p.toned(0.15, 0.1).color,
                surface = p.toned(0.28, 0.985).color,
                onSurface = p.toned(0.15, 0.1).color,
                surfaceVariant = p.toned(0.22, 0.92).color,
                onSurfaceVariant = p.toned(0.2, 0.35).color,
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = p.toned(0.3, 0.97).color,
                surfaceContainer = p.toned(0.3, 0.95).color,
                surfaceContainerHigh = p.toned(0.28, 0.92).color,
                surfaceContainerHighest = p.toned(0.26, 0.89).color,
                outline = p.toned(0.14, 0.5).color,
                outlineVariant = p.toned(0.18, 0.8).color,
                inverseSurface = p.toned(0.15, 0.2).color,
                inverseOnSurface = p.toned(0.2, 0.95).color,
                inversePrimary = p.atLightness(0.75).color,
            )
        }
    }

    companion object {
        /** The default palette used before any guild icon has been processed. */
        val Default = GuildPalette(
            primary = Rgb.fromHex("#3b82f6"),
            secondary = Rgb.fromHex("#8b5cf6"),
            accent = Rgb.fromHex("#ec4899"),
            muted = Rgb.fromHex("#9ca3af"),
            gradientStart = Rgb.fromHex("#3a86ff"),
            gradientMid = Rgb.fromHex("#8338ec"),
            gradientEnd = Rgb.fromHex("#ff006e"),
        )

        /** The background the palette derivation measures contrast against. */
        private val DerivationBackground = Rgb(0.07, 0.09, 0.16)

        /** Builds the nine-slot palette around a single dominant color. */
        fun deriveFrom(dominant: Rgb): GuildPalette {
            val boosted = dominant.adjustedForDarkUi()
            return GuildPalette(
                primary = boosted,
                secondary = boosted.rotated(30.0),
                accent = boosted.rotated(-150.0).boostedSaturation(),
                muted = boosted.softenedForReadability(DerivationBackground),
                gradientStart = boosted,
                gradientMid = boosted.rotated(-60.0),
                gradientEnd = boosted.rotated(-120.0),
            )
        }
    }
}
