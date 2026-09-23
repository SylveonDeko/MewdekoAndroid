package dev.mewdeko.mobile.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * A Material-style tonal role: an accent color, the content color that sits on
 * it, a tonal container, and the content color for that container.
 *
 * Mirrors the shape of the primary, secondary and tertiary roles in a
 * [androidx.compose.material3.ColorScheme], so a screen can carry extra
 * palette-derived roles without inventing a parallel naming scheme.
 */
@Immutable
data class ToneRole(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)
