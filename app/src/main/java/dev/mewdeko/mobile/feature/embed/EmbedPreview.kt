package dev.mewdeko.mobile.feature.embed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.theme.Rgb

/**
 * Renders an [EmbedMessage] roughly as Discord would.
 *
 * Approximate by design: it exists so an admin can sanity-check a template
 * before saving, not to be pixel-accurate to the Discord client.
 */
@Composable
fun EmbedPreview(message: EmbedMessage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (message.content.isNotBlank()) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        message.embeds.forEach { embed -> EmbedCard(embed) }
        if (message.isEmpty) {
            Text(
                text = "Nothing to preview yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmbedCard(embed: EmbedSpec) {
    val accent = parseEmbedColor(embed.color) ?: MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!embed.author.isEmpty) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (embed.author.iconUrl.isNotEmpty()) {
                            AsyncImage(
                                model = embed.author.iconUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                        Text(
                            text = embed.author.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (embed.title.isNotEmpty()) {
                            Text(
                                text = embed.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (embed.url.isNotEmpty()) accent
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (embed.description.isNotEmpty()) {
                            Text(
                                text = embed.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (embed.thumbnailUrl.isNotEmpty()) {
                        AsyncImage(
                            model = embed.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }

                embed.fields.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { field ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = field.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = field.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (row.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }

                if (embed.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = embed.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }

                if (!embed.footer.isEmpty) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (embed.footer.iconUrl.isNotEmpty()) {
                            AsyncImage(
                                model = embed.footer.iconUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                        Text(
                            text = embed.footer.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses Discord-style embed colours, accepting `#RRGGBB`, bare `RRGGBB`, or a
 * stringified integer.
 */
fun parseEmbedColor(raw: String): Color? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("#") || (trimmed.length == 6 && trimmed.all { it.isHexDigit() })) {
        return Rgb.fromHex(trimmed).color
    }
    return trimmed.toIntOrNull()?.let { Rgb.fromArgb(it).color }
}

private fun Char.isHexDigit(): Boolean = isDigit() || lowercaseChar() in 'a'..'f'
