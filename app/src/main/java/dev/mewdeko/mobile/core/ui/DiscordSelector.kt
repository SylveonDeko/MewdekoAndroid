package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
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
 * custom enum-style picks. An [imageUrl], such as a guild icon or avatar,
 * leads the option's row in the sheet.
 */
data class SelectorOption(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val colorHex: Int? = null,
    val imageUrl: String? = null,
    val icon: ImageVector? = null,
)

/** The option's role color as a Compose color, or null when it has none. */
internal val SelectorOption.swatch: Color?
    get() = colorHex?.takeIf { it != 0 }?.let { Rgb.fromArgb(it).color }

/**
 * Searchable picker for a role, channel, or member.
 *
 * Use this for Discord entities, where the list can run to thousands and
 * needs search. For a fixed list of a few named choices (a mode, a unit, a
 * style) use [EnumPicker] instead, which shows every choice with its
 * description in place.
 *
 * With [multiple] set this is [MultiSelectDropdown]: the selection shows as
 * removable chips under the field. Otherwise it is a single field that opens
 * [DiscordSelectorSheet] and closes on the first pick.
 */
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
    if (multiple) {
        MultiSelectDropdown(
            kind = kind,
            options = options,
            selection = selection,
            onSelectionChange = onSelectionChange,
            label = label.orEmpty(),
            placeholder = placeholder,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val selected = remember(selection, options) {
        options.firstOrNull { it.id in selection }
    }

    SelectorField(
        kind = kind,
        label = label,
        summary = selected?.let { kind.prefix + it.name } ?: placeholder,
        showingPlaceholder = selected == null,
        enabled = enabled,
        onClick = { expanded = true },
        modifier = modifier,
        swatch = selected?.swatch,
    )

    if (expanded) {
        DiscordSelectorSheet(
            kind = kind,
            options = options,
            multiple = false,
            selection = selection,
            onSelectionChange = onSelectionChange,
            onDismiss = { expanded = false },
            title = label,
        )
    }
}

/**
 * The tappable field shared by [DiscordSelector] and [MultiSelectDropdown]:
 * an optional label above a row with the kind glyph, a one-line summary, and
 * an expand chevron.
 */
@Composable
internal fun SelectorField(
    kind: SelectorKind,
    label: String?,
    summary: String,
    showingPlaceholder: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    swatch: Color? = null,
) {
    val tone = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (swatch != null) {
                    Box(
                        modifier = Modifier.size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(shape = CircleShape, color = swatch, modifier = Modifier.size(10.dp)) {}
                    }
                } else {
                    Icon(
                        kind.icon,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (showingPlaceholder) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The searchable bottom sheet behind [DiscordSelector] and
 * [MultiSelectDropdown], for callers that open it from their own trigger
 * rather than a selector field.
 *
 * A single-select pick closes the sheet. Multi-select keeps it open with a
 * header showing the count, a Clear action, and a Done button. Options that
 * were already selected when the sheet opened are pinned to the top so the
 * current choice is visible without scrolling; they stay in place while the
 * user toggles, so rows never jump under a finger. Search appears once the
 * list is long enough to need it. [title] defaults to a prompt for the kind.
 */
@Composable
fun DiscordSelectorSheet(
    kind: SelectorKind,
    options: List<SelectorOption>,
    selection: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    multiple: Boolean = false,
    title: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val pinned = remember { selection.toSet() }
    val ordered = remember(options) { options.sortedBy { it.id !in pinned } }
    val filtered = remember(query, ordered) {
        if (query.isBlank()) ordered
        else ordered.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.subtitle?.contains(query, ignoreCase = true) == true
        }
    }

    MewdekoBottomSheet(
        onDismissRequest = onDismiss,
        title = title?.takeIf { it.isNotBlank() } ?: defaultSheetTitle(kind, multiple),
        showClose = !multiple,
    ) {
        val dismiss = LocalSheetDismiss.current
        Column(
            modifier = Modifier
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (multiple) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${selection.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onSelectionChange(emptyList()) },
                        enabled = selection.isNotEmpty(),
                    ) { Text("Clear") }
                    Button(onClick = dismiss) { Text("Done") }
                }
            }

            if (options.size > SearchThreshold || query.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(filtered, key = { it.id }) { option ->
                    val isSelected = option.id in selection
                    SelectorOptionRow(
                        kind = kind,
                        option = option,
                        selected = isSelected,
                        multiple = multiple,
                        onClick = {
                            if (multiple) {
                                onSelectionChange(
                                    if (isSelected) selection - option.id
                                    else selection + option.id
                                )
                            } else {
                                onSelectionChange(listOf(option.id))
                                dismiss()
                            }
                        },
                    )
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            if (options.isEmpty()) "Nothing to choose from yet." else "No matches.",
                            icon = Icons.Default.Search,
                        )
                    }
                }
            }
        }
    }
}

/** Lists shorter than this skip the search field; every row already fits on screen. */
private const val SearchThreshold = 8

/** The sheet title used when the caller supplies none. */
private fun defaultSheetTitle(kind: SelectorKind, multiple: Boolean): String = when (kind) {
    SelectorKind.Channel -> if (multiple) "Choose channels" else "Choose a channel"
    SelectorKind.Role -> if (multiple) "Choose roles" else "Choose a role"
    SelectorKind.User -> if (multiple) "Choose members" else "Choose a member"
    is SelectorKind.Custom -> if (multiple) "Choose options" else "Choose an option"
}

/** One option in [DiscordSelectorSheet], with a checkbox or radio trailing. */
@Composable
private fun SelectorOptionRow(
    kind: SelectorKind,
    option: SelectorOption,
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val interaction = if (multiple) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
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
            val swatch = option.swatch
            when {
                option.imageUrl != null -> Avatar(
                    url = option.imageUrl,
                    contentDescription = null,
                    size = 32,
                    fallbackText = option.name,
                )

                swatch != null -> Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = CircleShape, color = swatch, modifier = Modifier.size(14.dp)) {}
                }

                else -> Icon(option.icon ?: kind.icon, contentDescription = null, tint = primary)
            }
        },
        trailingContent = {
            if (multiple) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                RadioButton(selected = selected, onClick = null)
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) primary.copy(alpha = DashAlpha.Hex08) else Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .heightIn(min = 48.dp)
            .then(interaction),
    )
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
