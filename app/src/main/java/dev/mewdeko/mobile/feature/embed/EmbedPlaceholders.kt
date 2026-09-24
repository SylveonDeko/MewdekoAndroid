package dev.mewdeko.mobile.feature.embed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.theme.MonospaceStyle

/** One `%...%` placeholder the bot resolves when it sends a message. */
data class Placeholder(val category: String, val name: String, val description: String)

/** Mirrors the dashboard's placeholder list on the embed builder page. */
private val Placeholders = listOf(
    Placeholder("AFK", "%afk.message%", "The user's afk message"),
    Placeholder("AFK", "%afk.user%", "The user's name and discriminator"),
    Placeholder("AFK", "%afk.user.mention%", "The mention of the afk user"),
    Placeholder("AFK", "%afk.user.avatar%", "The avatar url of the user"),
    Placeholder("AFK", "%afk.user.id%", "The id of the afk user"),
    Placeholder("AFK", "%afk.triggeruser%", "The trigger user's username and discriminator"),
    Placeholder("AFK", "%afk.triggeruser.avatar%", "The trigger user's avatar"),
    Placeholder("AFK", "%afk.triggeruser.mention%", "The mention of the trigger user"),
    Placeholder("AFK", "%afk.triggeruser.id%", "The id of the trigger user"),
    Placeholder("AFK", "%afk.time%", "How long the user has been afk"),
    Placeholder("Suggestions", "%suggest.user%", "The full username of the suggesting user"),
    Placeholder("Suggestions", "%suggest.user.id%", "The id of the suggesting user"),
    Placeholder("Suggestions", "%suggest.message%", "The original suggestion"),
    Placeholder("Suggestions", "%suggest.number%", "The suggestion number that was updated"),
    Placeholder("Suggestions", "%suggest.user.name%", "The name of the suggesting user"),
    Placeholder("Suggestions", "%suggest.user.avatar%", "The avatar of the original suggester"),
    Placeholder("Suggestions", "%suggest.mod.user%", "The full username of who updated the suggestion"),
    Placeholder("Suggestions", "%suggest.mod.avatar%", "The pfp of who updated the suggestion"),
    Placeholder("Suggestions", "%suggest.mod.name%", "The name of who updated the suggestion"),
    Placeholder("Suggestions", "%suggest.mod.message%", "The reason the suggestion was updated"),
    Placeholder("User", "%user%", "Username of the user"),
    Placeholder("User", "%user.mention%", "Mention the user"),
    Placeholder("User", "%user.id%", "User ID"),
    Placeholder("User", "%user.avatar%", "User's avatar URL"),
    Placeholder("User", "%user.name%", "User's display name"),
    Placeholder("User", "%user.nick%", "User's nickname in the server"),
    Placeholder("Server", "%server%", "Server name"),
    Placeholder("Server", "%server.id%", "Server ID"),
    Placeholder("Server", "%server.members%", "Number of server members"),
    Placeholder("Server", "%server.owner%", "Server owner username"),
    Placeholder("Server", "%server.icon%", "Server icon URL"),
    Placeholder("Random", "%rng%", "Random number"),
    Placeholder("Random", "%rng(1,10)%", "Random number between 1 and 10"),
    Placeholder("Random", "%choose(a|b|c)%", "Choose randomly from options"),
    Placeholder("Random", "%target%", "Anything the user wrote after the trigger"),
    Placeholder("Random", "%img:stuff%", "An imgur.com search for 'stuff' (chat triggers only)"),
)

/**
 * A text field with a trailing button that appends a chosen `%placeholder%`
 * to its value.
 *
 * Appends to the end rather than at the cursor: Compose does not expose a
 * text field's live cursor position without extra state plumbing, and
 * appending is both simpler and predictable on a touch keyboard.
 */
@Composable
fun PlaceholderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    additionalPlaceholders: List<Placeholder> = emptyList(),
) {
    var picking by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
            MewdekoTextField(
                value = value,
                onValueChange = { new ->
                    onValueChange(if (maxLength != null) new.take(maxLength) else new)
                },
                label = label,
                placeholder = placeholder,
                singleLine = singleLine,
                minLines = minLines,
                supportingText = maxLength?.let { "${value.length}/$it" },
                isError = maxLength != null && value.length > maxLength,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { picking = true }) {
                Icon(Icons.Default.Percent, contentDescription = "Insert placeholder into $label")
            }
        }
    }

    if (picking) {
        PlaceholderPickerSheet(
            onDismiss = { picking = false },
            onPick = { chosen ->
                val combined = value + chosen
                onValueChange(if (maxLength != null) combined.take(maxLength) else combined)
                picking = false
            },
            additionalPlaceholders = additionalPlaceholders,
        )
    }
}

/** Full-height searchable list of every placeholder the bot understands. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderPickerSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    additionalPlaceholders: List<Placeholder> = emptyList(),
) {
    var query by remember { mutableStateOf("") }
    val allPlaceholders = remember(additionalPlaceholders) {
        (additionalPlaceholders + Placeholders).distinctBy { it.name }
    }
    val filtered = remember(query, allPlaceholders) {
        if (query.isBlank()) {
            allPlaceholders
        } else {
            allPlaceholders.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true)
            }
        }
    }

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "Insert a placeholder") {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search placeholders",
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filtered, key = { it.name }) { entry ->
                    Surface(
                        onClick = { onPick(entry.name) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(entry.name, style = MonospaceStyle)
                                Text(
                                    entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
