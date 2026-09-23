package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.TagChip

/**
 * The players currently online, each with an add/remove-from-whitelist
 * toggle when the server has RCON enabled.
 */
@Composable
fun MinecraftOnlinePlayersCard(
    players: List<String>,
    rconEnabled: Boolean,
    whitelist: List<String>,
    onAddToWhitelist: (String) -> Unit,
    onRemoveFromWhitelist: (String) -> Unit,
) {
    if (players.isEmpty()) return

    SectionCard(contentPadding = 12) {
        SectionCardHeader("Online players (${players.size})", Icons.Default.ListAlt)
        players.forEach { player ->
            ListItem(
                headlineContent = { Text(player) },
                trailingContent = if (!rconEnabled) {
                    null
                } else if (player in whitelist) {
                    {
                        IconButton(onClick = { onRemoveFromWhitelist(player) }) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = "Remove $player from whitelist",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    {
                        IconButton(onClick = { onAddToWhitelist(player) }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add $player to whitelist")
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** RCON's `whitelist list`, with add-by-name and per-player removal. */
@Composable
fun MinecraftWhitelistCard(
    whitelist: List<String>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    SectionCard {
        SectionCardHeader(
            "Whitelist",
            Icons.Default.ListAlt,
            trailing = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh whitelist")
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = name,
                onValueChange = { name = it },
                label = "Player name",
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onAdd(name.trim()); name = "" },
                enabled = name.isNotBlank() && !loading,
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add to whitelist")
            }
        }

        when {
            whitelist.isNotEmpty() -> whitelist.forEach { player ->
                ListItem(
                    headlineContent = { Text(player) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(player) }) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = "Remove $player from whitelist",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            !loading -> EmptyState("No players on the whitelist, or whitelist is disabled on the server.")
        }
    }
}

/** Plugins reported by the server's query response. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinecraftPluginsCard(plugins: List<String>) {
    if (plugins.isEmpty()) return

    SectionCard(contentPadding = 12) {
        SectionCardHeader("Plugins (${plugins.size})", Icons.Default.Extension)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            plugins.forEach { TagChip(it) }
        }
    }
}
