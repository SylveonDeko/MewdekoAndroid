package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.BotStatus
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.feature.guilddetail.formatted
import dev.mewdeko.mobile.util.relativeToNow
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * The bot's identity in this guild and its health: avatar with status dot,
 * name, versions, latency, bio, command and module counts, and when the page
 * last refreshed. Informational, so not clickable.
 */
@Composable
fun BotCard(
    bot: BotStatus?,
    profile: BotGuildProfile?,
    lastUpdated: Instant?,
    loading: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val online = bot?.botStatus?.equals("online", ignoreCase = true) == true
    val latency = bot?.botLatency ?: 0
    val healthy = latency < 250
    val updated by produceState<String?>(lastUpdated?.relativeToNow(), lastUpdated) {
        while (true) {
            value = lastUpdated?.relativeToNow()
            delay(30_000)
        }
    }
    val name = profile?.nickname?.takeIf { it.isNotBlank() } ?: bot?.botName.orEmpty()

    GuildCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Avatar(
                        url = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: bot?.botAvatar,
                        contentDescription = null,
                        size = 48,
                        fallbackIcon = Icons.Default.SmartToy,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .border(2.dp, scheme.surfaceContainerLow, CircleShape)
                            .background(if (online) scheme.primary else scheme.outline, CircleShape),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (loading) "Mewdeko" else name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.skeleton(loading),
                    )
                    Text(
                        text = if (bot == null) {
                            "v0.0.0 · Discord.Net 0.0.0"
                        } else {
                            "v${bot.botVersion} · Discord.Net ${bot.dNetVersion}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.skeleton(loading),
                    )
                }
                if (bot != null || loading) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            modifier = Modifier.skeleton(loading),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = latency.toString(),
                                style = MaterialTheme.typography.headlineSmall.tabular(),
                                color = if (healthy) scheme.primary else scheme.tertiary,
                            )
                            Text(
                                text = " ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (healthy) scheme.primary else scheme.tertiary, CircleShape),
                            )
                            Text(
                                text = if (healthy) "Healthy" else "Slow",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (bot != null || loading) {
                Text(
                    text = "${(bot?.commandsCount ?: 0).formatted()} commands · " +
                        "${(bot?.modulesCount ?: 0).formatted()} modules",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.skeleton(loading),
                )
                Text(
                    text = buildString {
                        append("Status: ").append(bot?.botStatus?.ifBlank { null } ?: "unknown")
                        updated?.let { append(" · Updated ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.skeleton(loading),
                )
            }
        }
    }
}
