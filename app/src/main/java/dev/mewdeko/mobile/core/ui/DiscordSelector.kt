package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.Rgb

/** Selector type; drives the prefix, default icon, and accent. */
sealed class SelectorKind(val icon: ImageVector, val prefix: String) {
    /** A guild text or voice channel. */
    data object Channel : SelectorKind(Icons.Default.Tag, "#")

    /** A guild role. */
    data object Role : SelectorKind(Icons.Default.Person, "@")

    /** A guild member. */
    data object User : SelectorKind(Icons.Default.Person, "")

    /** A plain enum-style list with a caller-supplied glyph. */
    data class Custom(val glyph: ImageVector) : SelectorKind(glyph, "")
}

/**
 * Generic option used by [DiscordSelector]; covers channels, roles, users, and
 * custom enum-style picks.
 */
data class SelectorOption(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val colorHex: Int? = null,
)

/**
 * Searchable option picker for choosing a role, channel, user, or timezone.
 *
 * Presented as a Material 3 modal bottom sheet with a lazy list, so guilds
 * with thousands of members stay responsive. Supports single and multi-select.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSelector(
    kind: SelectorKind,
    options: List<SelectorOption>,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    multiple: Boolean = false,
    selection: List<String> = emptyList(),
    onSelectionChange: (List<String>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val selected = remember(selection, options) {
        options.filter { it.id in selection }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    kind.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        selected.isEmpty() -> Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        selected.size == 1 -> Text(
                            text = kind.prefix + selected.first().name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        else -> Text(
                            text = "${selected.size} selected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (multiple && selected.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                selected.take(4).forEach { option ->
                    InputChip(
                        selected = true,
                        onClick = { onSelectionChange(selection - option.id) },
                        label = {
                            Text(
                                text = kind.prefix + option.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
        }
    }

    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val filtered = remember(query, options) {
            if (query.isBlank()) options
            else options.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.subtitle?.contains(query, ignoreCase = true) == true
            }
        }

        ModalBottomSheet(
            onDismissRequest = { expanded = false; query = "" },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.imePadding().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(filtered, key = { it.id }) { option ->
                        val isSelected = option.id in selection
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = kind.prefix + option.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = option.subtitle?.let {
                                { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            },
                            leadingContent = {
                                val swatch = option.colorHex
                                    ?.takeIf { it != 0 }
                                    ?.let { Rgb.fromArgb(it).color }
                                if (swatch != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = swatch,
                                        modifier = Modifier.size(14.dp),
                                    ) {}
                                } else {
                                    Icon(kind.icon, contentDescription = null)
                                }
                            },
                            trailingContent = {
                                if (multiple) {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                } else {
                                    RadioButton(selected = isSelected, onClick = null)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow {
                                    if (multiple) {
                                        onSelectionChange(
                                            if (isSelected) selection - option.id
                                            else selection + option.id
                                        )
                                    } else {
                                        onSelectionChange(listOf(option.id))
                                        expanded = false
                                        query = ""
                                    }
                                },
                        )
                    }

                    if (filtered.isEmpty()) {
                        item { EmptyState("No matches.", icon = Icons.Default.Search) }
                    }
                }

                if (multiple) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = { expanded = false; query = "" }) {
                            Icon(Icons.Default.Check, contentDescription = "Done")
                        }
                    }
                }
            }
        }
    }
}

/** Convenience wrapper for the common single-select case. */
@Composable
fun DiscordSelectorSingle(
    kind: SelectorKind,
    options: List<SelectorOption>,
    placeholder: String,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    DiscordSelector(
        kind = kind,
        options = options,
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        enabled = enabled,
        multiple = false,
        selection = listOfNotNull(selectedId),
        onSelectionChange = { onSelect(it.firstOrNull()) },
    )
}
