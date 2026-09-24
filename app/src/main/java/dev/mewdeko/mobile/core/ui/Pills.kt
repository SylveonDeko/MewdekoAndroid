package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha

/**
 * A compact state capsule: [tone] at the dashboard's `20` tint with a `30`
 * border, an optional leading glyph in the solid tone, and bold label text
 * in the tone when it reads on the surface.
 */
@Composable
fun StatePill(
    text: String,
    tone: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = tone.copy(alpha = DashAlpha.Hex20),
        contentColor = readableInk(tone),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier.heightIn(min = 24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(13.dp))
            }
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

/** The three sizes of [GlyphOrb]. */
enum class OrbSize(val diameter: Dp, val glyph: Dp) {
    /** Section headers and toggle rows. */
    Small(28.dp, 15.dp),

    /** Row leaders. */
    Medium(36.dp, 19.dp),

    /** Feature state, such as the AFK card, and empty states. */
    Large(56.dp, 28.dp),

    /** Shell heroes: sign in, setup, the bot picker, and the offline screen. */
    ExtraLarge(88.dp, 40.dp),
}

/**
 * A circular glyph badge: [tint] at the dashboard's `20` tint with a `30`
 * border and the glyph in the solid tint.
 */
@Composable
fun GlyphOrb(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: OrbSize = OrbSize.Small,
) {
    Box(
        modifier = modifier
            .size(size.diameter)
            .background(tint.copy(alpha = DashAlpha.Hex20), CircleShape)
            .border(1.dp, tint.copy(alpha = DashAlpha.Hex30), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size.glyph))
    }
}
