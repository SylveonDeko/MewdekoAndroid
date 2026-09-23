package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.guildBorder

/** One shortcut destination. */
private data class Shortcut(val featureId: String, val label: String, val icon: ImageVector)

/**
 * The strip's destinations.
 *
 * None repeats the guild navigation bar, which already carries Settings,
 * Music and XP, and the feature browser stays in the top bar.
 */
private val Shortcuts = listOf(
    Shortcut("moderation", "Moderation", Icons.Default.Shield),
    Shortcut("administration", "Protection", Icons.Default.AdminPanelSettings),
    Shortcut("tickets", "Tickets", Icons.Default.ConfirmationNumber),
    Shortcut("logging", "Logging", Icons.AutoMirrored.Filled.ManageSearch),
    Shortcut("embedbuilder", "Embeds", Icons.Default.ViewAgenda),
)

/**
 * A row of five quick links under the hero.
 *
 * At large font scales it becomes a list card, since five labelled buttons no
 * longer fit across.
 */
@Composable
fun ShortcutStrip(onOpenFeature: (String) -> Unit, reduced: Boolean) {
    if (isLargeFont()) {
        GuildCard(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Shortcuts.forEach { shortcut ->
                    ListItem(
                        headlineContent = {
                            Text(shortcut.label, style = MaterialTheme.typography.bodyLarge)
                        },
                        leadingContent = {
                            Icon(
                                shortcut.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Button) { onOpenFeature(shortcut.featureId) },
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Shortcuts.forEach { shortcut ->
                ShortcutButton(
                    icon = shortcut.icon,
                    label = shortcut.label,
                    onClick = { onOpenFeature(shortcut.featureId) },
                    reduced = reduced,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A tonal circle that morphs to a rounded square while pressed, with its
 * label underneath. The circle is the dashboard icon background: secondary at
 * the `20` tint with a `30` border and a solid secondary icon. The label is
 * hidden from accessibility because the button already carries it.
 */
@Composable
fun ShortcutButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed && !reduced) 18.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1400f),
        label = "shortcutCorner",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(corner),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.secondary,
            border = guildBorder(MaterialTheme.colorScheme.secondary),
            interactionSource = interactionSource,
            modifier = Modifier
                .widthIn(max = 64.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}
