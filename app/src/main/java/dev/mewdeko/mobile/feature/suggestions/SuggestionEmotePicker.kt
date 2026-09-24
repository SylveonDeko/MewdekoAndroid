package dev.mewdeko.mobile.feature.suggestions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.ui.LocalSheetDismiss
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet

/**
 * Renders [value] the way the picker's chip should read: the guild emote's
 * `:name:` when it matches one of [guildEmotes], otherwise the raw value
 * (a typed unicode emoji).
 */
private fun emoteChipLabel(value: String, guildEmotes: List<SuggestionEmojiInfo>): String =
    guildEmotes.firstOrNull { it.mention == value }?.let { ":${it.name}:" } ?: value

/**
 * Picker for suggestion vote or button emotes.
 *
 * Combines the guild's custom emotes (rendered as their Discord mention
 * form, matching what the bot's suggestion service expects) with free-typed
 * unicode emoji, capped at [max] selections. Selections beyond one are comma
 * joined by the caller, matching the bot's `suggestEmotes` format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionEmotePicker(
    label: String,
    selected: List<String>,
    guildEmotes: List<SuggestionEmojiInfo>,
    onSelectedChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    max: Int = 1,
    supportingText: String? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }

    fun choose(value: String) {
        val next = when {
            max <= 1 -> listOf(value)
            value in selected -> selected - value
            else -> (selected + value).distinct().take(max)
        }
        onSelectedChange(next)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            selected.forEach { value ->
                InputChip(
                    selected = true,
                    onClick = { sheetOpen = true },
                    label = { Text(emoteChipLabel(value, guildEmotes)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { onSelectedChange(selected - value) },
                            modifier = Modifier.size(18.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                )
            }
            if (selected.size < max) {
                AssistChip(
                    onClick = { sheetOpen = true },
                    label = { Text("Add emote") },
                    leadingIcon = {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
        }
        supportingText?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (sheetOpen) {
        MewdekoBottomSheet(
            onDismissRequest = { sheetOpen = false; typed = "" },
            title = label,
        ) {
            val dismissSheet = LocalSheetDismiss.current
            Column(
                modifier = Modifier
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        placeholder = { Text("Type or paste an emoji") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = typed.isNotBlank() && (max <= 1 || selected.size < max),
                        onClick = {
                            choose(typed.trim())
                            dismissSheet()
                        },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Add typed emoji")
                    }
                }

                if (guildEmotes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Server emotes",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(guildEmotes.chunked(6)) { row ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                row.forEach { emote ->
                                    val isSelected = emote.mention in selected
                                    Surface(
                                        onClick = {
                                            choose(emote.mention)
                                            if (max <= 1) dismissSheet()
                                        },
                                        shape = CircleShape,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            AsyncImage(
                                                model = emote.url,
                                                contentDescription = emote.name,
                                                modifier = Modifier.size(26.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Default.EmojiEmotions,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
