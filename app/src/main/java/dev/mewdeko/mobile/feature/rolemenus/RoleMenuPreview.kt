package dev.mewdeko.mobile.feature.rolemenus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.feature.embed.EmbedPreview

/**
 * How a role menu will look in Discord: its message (the saved one, or the
 * default list of options when it has none) above a mock dropdown or rows
 * of buttons. A paused menu greys the controls out and says so.
 */
@Composable
fun RoleMenuPreview(
    name: String,
    message: EmbedMessage,
    style: RoleMenuStyle,
    mode: RoleMenuMode,
    placeholder: String,
    options: List<RoleMenuPreviewOption>,
    paused: Boolean,
    modifier: Modifier = Modifier,
) {
    val effective = if (message.isEmpty) buildDefaultRoleMenuMessage(name, options) else message
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EmbedPreview(effective)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (paused) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (options.isNotEmpty()) {
                when (style) {
                    RoleMenuStyle.DROPDOWN -> MockDropdown(
                        hint = placeholder.trim().ifEmpty { RoleMenuLimits.defaultPlaceholder(mode) },
                        options = options,
                    )
                    RoleMenuStyle.BUTTONS -> MockButtons(options)
                }
            }
        }
        if (paused) {
            Text(
                text = "Paused: the dropdown or buttons are greyed out in Discord.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A Discord style dropdown showing [hint]; tapping it lists the options. */
@Composable
private fun MockDropdown(hint: String, options: List<RoleMenuPreviewOption>) {
    var expanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = { expanded = !expanded },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Hide options" else "Show options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    options.forEach { option ->
                        HorizontalDivider(color = primary.copy(alpha = DashAlpha.Hex15))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RoleMenuEmoji(option.emoji, size = 22.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label.ifEmpty { " " },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (option.description.isNotEmpty()) {
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Discord style buttons, five to a row, in each option's color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MockButtons(options: List<RoleMenuPreviewOption>) {
    options.chunked(RoleMenuLimits.ButtonsPerRow).forEach { row ->
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            row.forEach { option ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = option.buttonColor.color,
                    contentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoleMenuEmoji(option.emoji, size = 18.dp)
                        if (option.label.isNotEmpty()) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws an option emoji: custom "<:name:id>" text as its Discord image,
 * anything else as text. Draws nothing for a blank emoji.
 */
@Composable
internal fun RoleMenuEmoji(emoji: String, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val trimmed = emoji.trim()
    if (trimmed.isEmpty()) return
    val custom = CustomEmojiRef.parse(trimmed)
    if (custom != null) {
        AsyncImage(
            model = custom.url,
            contentDescription = custom.name,
            modifier = modifier.size(size),
        )
    } else {
        Text(text = trimmed, fontSize = (size.value * 0.8f).sp, modifier = modifier)
    }
}
