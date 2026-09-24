package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/**
 * The full screen form for registering a new server, opened from the
 * Servers section's New action. It can also wire up status watching and a
 * custom watch embed in the same submit, matching the dashboard's add form.
 */
@Composable
fun MinecraftAddServerEditor(
    channelOptions: List<SelectorOption>,
    onClose: () -> Unit,
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

    val edited = name.isNotBlank() || address.isNotBlank() || watchChannelId != null || !customEmbed.isEmpty

    FullScreenEditor(
        title = "Add server",
        onClose = onClose,
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank() && address.isNotBlank(),
        onConfirm = {
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
        },
        hasUnsavedChanges = edited,
    ) {
        SectionCard {
            SectionCardHeader("Server", Icons.Default.Dns)

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
            EnumPicker(
                label = "Server type",
                options = McServerType.entries.map { EnumOption(it, title = it.label, description = it.blurb) },
                selected = type,
                onSelect = { entry ->
                    type = entry
                    port = entry.defaultPort.toString()
                },
            )
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
            SectionCardHeader("Status watching (optional)", Icons.Default.Visibility)
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "No watch channel",
                label = "Post status in",
                selectedId = watchChannelId,
                onSelect = { watchChannelId = it },
            )
            if (watchChannelId != null) {
                EnumPicker(
                    label = "Watch mode",
                    options = McWatchMode.entries.map { EnumOption(it, title = it.label, description = it.blurb) },
                    selected = watchMode,
                    onSelect = { watchMode = it },
                )
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
            SectionCardHeader("Custom watch embed (optional)", Icons.Default.Image)
            Text(
                text = "Leave empty to use the bot's default status embed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbedMessageEditor(message = customEmbed, onMessageChange = { customEmbed = it })
        }
    }
}
