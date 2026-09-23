package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader

/**
 * RCON console: pick a server with RCON enabled, then send commands and see
 * a scrolling history of their responses, with Minecraft `§`-code colours
 * rendered where the server returns them.
 */
@Composable
fun MinecraftConsoleTab(
    servers: List<MinecraftServer>,
    state: MinecraftState,
    onSelectServer: (String) -> Unit,
    onSend: (String) -> Unit,
) {
    val rconServers = servers.filter { it.rconEnabled }

    if (rconServers.isEmpty()) {
        SectionCard {
            EmptyState(
                "No servers have RCON enabled. Turn on RCON from the Servers tab to use the console.",
                icon = Icons.Default.Terminal,
            )
        }
        return
    }

    SectionCard(contentPadding = 12) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rconServers.forEach { server ->
                FilterChip(
                    selected = server.name == state.consoleServer,
                    onClick = { onSelectServer(server.name) },
                    label = { Text(server.name) },
                )
            }
        }
    }

    val serverName = state.consoleServer
    if (serverName == null) {
        SectionCard { EmptyState("Select a server to open the console.", icon = Icons.Default.Terminal) }
        return
    }

    var command by remember(serverName) { mutableStateOf("") }

    SectionCard {
        SectionCardHeader("RCON ($serverName)", Icons.Default.Terminal)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.consoleHistory.isEmpty()) {
                Text(
                    text = "Type a command below and send it...",
                    style = MonospaceStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.consoleHistory.forEach { entry ->
                Column {
                    Text(text = "$ ${entry.command}", style = MonospaceStyle)
                    val body = when {
                        entry.success && !entry.rawResponse.isNullOrEmpty() ->
                            mcColorText(entry.rawResponse)

                        else -> buildAnnotatedString {
                            append(entry.response ?: "(no output)")
                        }
                    }
                    Text(
                        text = body,
                        style = MonospaceStyle,
                        color = if (entry.success) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MewdekoTextField(
                value = command,
                onValueChange = { command = it },
                label = "Command",
                placeholder = "list",
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (command.isNotBlank() && !state.consoleSending) {
                        onSend(command.trim())
                        command = ""
                    }
                },
                enabled = command.isNotBlank() && !state.consoleSending,
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send command")
            }
        }
    }
}
