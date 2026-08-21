package dev.mewdeko.mobile.core.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Whether the guild-derived palette or the system Material You palette wins. */
enum class ThemeSource {
    /** Colours follow the active guild's icon. */
    GUILD,

    /** Colours follow the device wallpaper (Android 12+), guild palette otherwise. */
    SYSTEM,
}

/** The guild palette in scope, for decorative surfaces Material roles do not cover. */
val LocalGuildPalette = staticCompositionLocalOf { GuildPalette.Default }

/**
 * Applies the Mewdeko Material 3 theme.
 *
 * Colour transitions are animated per role, with a 450ms ease, so switching
 * guilds cross-fades the whole UI.
 */
@Composable
fun MewdekoTheme(
    palette: GuildPalette = GuildPalette.Default,
    source: ThemeSource = ThemeSource.GUILD,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val target = when {
        source == ThemeSource.SYSTEM && supportsDynamic ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> palette.toColorScheme(darkTheme)
    }

    val animated = target.animated()

    CompositionLocalProvider(LocalGuildPalette provides palette) {
        MaterialTheme(
            colorScheme = animated,
            typography = MewdekoTypography,
            shapes = MewdekoShapes,
            content = content,
        )
    }
}

@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = animateColorAsState(primary, tween(450), label = "primary").value,
    onPrimary = animateColorAsState(onPrimary, tween(450), label = "onPrimary").value,
    primaryContainer = animateColorAsState(primaryContainer, tween(450), label = "primaryContainer").value,
    onPrimaryContainer = animateColorAsState(onPrimaryContainer, tween(450), label = "onPrimaryContainer").value,
    secondary = animateColorAsState(secondary, tween(450), label = "secondary").value,
    onSecondary = animateColorAsState(onSecondary, tween(450), label = "onSecondary").value,
    secondaryContainer = animateColorAsState(secondaryContainer, tween(450), label = "secondaryContainer").value,
    onSecondaryContainer = animateColorAsState(onSecondaryContainer, tween(450), label = "onSecondaryContainer").value,
    tertiary = animateColorAsState(tertiary, tween(450), label = "tertiary").value,
    onTertiary = animateColorAsState(onTertiary, tween(450), label = "onTertiary").value,
    tertiaryContainer = animateColorAsState(tertiaryContainer, tween(450), label = "tertiaryContainer").value,
    onTertiaryContainer = animateColorAsState(onTertiaryContainer, tween(450), label = "onTertiaryContainer").value,
    background = animateColorAsState(background, tween(450), label = "background").value,
    onBackground = animateColorAsState(onBackground, tween(450), label = "onBackground").value,
    surface = animateColorAsState(surface, tween(450), label = "surface").value,
    onSurface = animateColorAsState(onSurface, tween(450), label = "onSurface").value,
    surfaceVariant = animateColorAsState(surfaceVariant, tween(450), label = "surfaceVariant").value,
    onSurfaceVariant = animateColorAsState(onSurfaceVariant, tween(450), label = "onSurfaceVariant").value,
    surfaceContainerLowest = animateColorAsState(surfaceContainerLowest, tween(450), label = "sc0").value,
    surfaceContainerLow = animateColorAsState(surfaceContainerLow, tween(450), label = "sc1").value,
    surfaceContainer = animateColorAsState(surfaceContainer, tween(450), label = "sc2").value,
    surfaceContainerHigh = animateColorAsState(surfaceContainerHigh, tween(450), label = "sc3").value,
    surfaceContainerHighest = animateColorAsState(surfaceContainerHighest, tween(450), label = "sc4").value,
    outline = animateColorAsState(outline, tween(450), label = "outline").value,
    outlineVariant = animateColorAsState(outlineVariant, tween(450), label = "outlineVariant").value,
)
