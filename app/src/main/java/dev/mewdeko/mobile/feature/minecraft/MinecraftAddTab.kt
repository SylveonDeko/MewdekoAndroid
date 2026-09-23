package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/**
 * Registers a new server, and optionally wires up status watching and a
 * custom watch embed in the same submit, matching the dashboard's add form.
 */
@Composable
fun MinecraftAddTab(
    channelOptions: List<SelectorOption>,
    onAdd: (
        name: String,
        address: String,
        port: Int,
        type: McServerType,
        queryPort: Int,
        watchChannelId: String?,
        watchInterval: Int,
        watchMode: Int,
        customEmbedTemplate: String?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(McServerType.JAVA) }
    var port by remember { mutableStateOf(McServerType.JAVA.defaultPort.toString()) }
    var queryPort by remember { mutableStateOf("0") }
    var watchChannelId by remember { mutableStateOf<String?>(null) }
    var watchInterval by remember { mutableStateOf(5) }
    var watchMode by remember { mutableStateOf(McWatchMode.EMBED) }
    var customEmbed by remember { mutableStateOf(EmbedMessage()) }

    SectionCard {
        SectionCardHeader("Add Minecraft server", Icons.Default.Add)

        MewdekoTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "survival",
        )
        MewdekoTextField(
            value = address,
            onValueChange = { address = it },
            label = "Address",
            placeholder = "mc.example.com",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            McServerType.entries.forEach { entry ->
                FilterChip(
                    selected = type == entry,
                    onClick = { type = entry; port = entry.defaultPort.toString() },
                    label = { Text(entry.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MewdekoTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit) },
            label = "Port",
            numeric = true,
        )
        MewdekoTextField(
            value = queryPort,
            onValueChange = { queryPort = it.filter(Char::isDigit) },
            label = "Query port (optional)",
            numeric = true,
            supportingText = "Leave at 0 unless the server enables the query protocol.",
        )
    }

    SectionCard {
        SectionCardHeader("Status watching (optional)", Icons.Default.Add)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No watch channel",
            label = "Post status in",
            selectedId = watchChannelId,
            onSelect = { watchChannelId = it },
        )
        if (watchChannelId != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                McWatchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = watchMode == mode,
                        onClick = { watchMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
            SliderRow(
                label = "Refresh interval",
                value = watchInterval.toFloat(),
                onValueChange = { watchInterval = it.toInt() },
                valueRange = 1f..60f,
                valueLabel = "${watchInterval}m",
            )
        }
    }

    SectionCard {
        SectionCardHeader("Custom watch embed (optional)", Icons.Default.Add)
        Text(
            text = "Leave empty to use the bot's default status embed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(message = customEmbed, onMessageChange = { customEmbed = it })
    }

    Button(
        onClick = {
            onAdd(
                name.trim(),
                address.trim(),
                port.toIntOrNull() ?: type.defaultPort,
                type,
                queryPort.toIntOrNull() ?: 0,
                watchChannelId,
                watchInterval,
                watchMode.raw,
                customEmbed.serialize().takeUnless { it == "-" },
            )
            name = ""
            address = ""
            type = McServerType.JAVA
            port = McServerType.JAVA.defaultPort.toString()
            queryPort = "0"
            watchChannelId = null
            watchInterval = 5
            watchMode = McWatchMode.EMBED
            customEmbed = EmbedMessage()
        },
        enabled = name.isNotBlank() && address.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add server") }
}
