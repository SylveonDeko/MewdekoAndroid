package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/** The dashboard's `%mc.*%` watch-embed placeholders, shown as a caption above each builder. */
private const val WatchPlaceholdersCaption =
    "Placeholders: %mc.server.name%, %mc.server.address%, %mc.server.port%, %mc.online%, " +
        "%mc.version%, %mc.latency%, %mc.motd%, %mc.players.online%, %mc.players.max%, " +
        "%mc.player.list%, %mc.map%, %mc.gamemode%, %mc.software%, %mc.plugins%"

/** The bridge-event placeholders shared by every event template. */
private const val EventPlaceholdersCaption =
    "Placeholders: %mc.player%, %mc.avatar%, %mc.uuid%, %mc.message%, %mc.death.message%, %mc.advancement%"

/** A template value of `"-"` is [EmbedMessage.serialize]'s empty sentinel; the bot wants `null`. */
private fun EmbedMessage.templateOrNull(): String? = serialize().takeUnless { it == "-" }

/** The custom embed the bot posts to the watch channel, in place of its default status embed. */
@Composable
fun MinecraftCustomEmbedCard(server: MinecraftServer, onSave: (String?) -> Unit) {
    SectionCard {
        SectionCardHeader("Custom watch embed", Icons.Default.Image)
        Text(
            text = "Leave empty to use the bot's default status embed. $WatchPlaceholdersCaption",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(
            message = EmbedMessage.parse(server.customEmbedTemplate),
            onMessageChange = { onSave(it.templateOrNull()) },
        )
    }
}

/** The alert posted when a watched server comes online or goes offline. */
@Composable
fun MinecraftAlertCard(
    title: String,
    icon: ImageVector,
    template: String?,
    onSave: (String?) -> Unit,
) {
    SectionCard {
        SectionCardHeader(title, icon)
        Text(
            text = "Leave empty to use the bot's default alert. $WatchPlaceholdersCaption",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(
            message = EmbedMessage.parse(template),
            onMessageChange = { onSave(it.templateOrNull()) },
        )
    }
}

/**
 * The companion plugin's bridge event messages: join, leave, chat, death, and
 * advancement embeds, plus the plain-text in-game format for Discord chat
 * relayed back into the server.
 *
 * Bundled behind one explicit Save so six related fields commit together as
 * one JSON blob, rather than five separate round trips.
 */
@Composable
fun MinecraftEventTemplatesCard(server: MinecraftServer, onSave: (McEventTemplates) -> Unit) {
    var draft by remember(server.name, server.eventTemplates) {
        mutableStateOf(McEventTemplates.parse(server.eventTemplates))
    }

    SectionCard {
        SectionCardHeader("Bridge event templates", Icons.Default.Chat)
        Text(
            text = "Requires the companion plugin. Leave a field empty for its default. $EventPlaceholdersCaption",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Player join (Discord)", style = MaterialTheme.typography.labelLarge)
        EmbedMessageEditor(
            message = EmbedMessage.parse(draft.joinDiscord),
            onMessageChange = { draft = draft.copy(joinDiscord = it.templateOrNull().orEmpty()) },
        )

        Text("Player leave (Discord)", style = MaterialTheme.typography.labelLarge)
        EmbedMessageEditor(
            message = EmbedMessage.parse(draft.leaveDiscord),
            onMessageChange = { draft = draft.copy(leaveDiscord = it.templateOrNull().orEmpty()) },
        )

        Text("Chat message (Discord)", style = MaterialTheme.typography.labelLarge)
        EmbedMessageEditor(
            message = EmbedMessage.parse(draft.chatDiscord),
            onMessageChange = { draft = draft.copy(chatDiscord = it.templateOrNull().orEmpty()) },
        )

        MewdekoTextField(
            value = draft.chatIngame,
            onValueChange = { draft = draft.copy(chatIngame = it) },
            label = "Chat from Discord (in-game format)",
            placeholder = "[Discord] %user%: %message%",
            supportingText = "Placeholders: %user%, %message%, %channel%. Use §-codes for MC colours.",
        )

        Text("Death message (Discord)", style = MaterialTheme.typography.labelLarge)
        EmbedMessageEditor(
            message = EmbedMessage.parse(draft.deathDiscord),
            onMessageChange = { draft = draft.copy(deathDiscord = it.templateOrNull().orEmpty()) },
        )

        Text("Advancement (Discord)", style = MaterialTheme.typography.labelLarge)
        EmbedMessageEditor(
            message = EmbedMessage.parse(draft.advancementDiscord),
            onMessageChange = { draft = draft.copy(advancementDiscord = it.templateOrNull().orEmpty()) },
        )

        Button(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save event templates")
        }
    }
}
