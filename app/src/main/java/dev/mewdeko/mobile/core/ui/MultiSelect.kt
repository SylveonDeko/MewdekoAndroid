package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha

/**
 * A compact multi-select for roles, channels, or members.
 *
 * Use this anywhere a setting takes several Discord entities (roles to
 * ignore, channels to watch, members to exclude). It replaces inline
 * checkbox lists, which stretch the page to the length of the guild's role
 * or channel list: the field stays one row tall, the choices live in a
 * searchable [DiscordSelectorSheet], and the current selection shows as
 * removable chips under the field.
 *
 * Each chip removes its id when tapped. Ids no longer in [options] (a
 * deleted role, say) still render, as `Unknown (id)`, so a stale id can be
 * removed. Past [maxVisibleChips] the rest collapse into a `+N more` chip
 * that opens the sheet. [destructive] tints the field and chips with the
 * error color, for lists such as roles to strip.
 *
 * View models that save one id at a time can turn the new list into adds
 * and removes with [diffSelection].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiSelectDropdown(
    kind: SelectorKind,
    options: List<SelectorOption>,
    selection: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    maxVisibleChips: Int = 12,
) {
    var expanded by remember { mutableStateOf(false) }
    val byId = remember(options) { options.associateBy { it.id } }
    val first = selection.firstOrNull()
    val summary = when {
        first == null -> placeholder.ifBlank { "None" }
        selection.size == 1 -> byId[first]?.let { kind.prefix + it.name } ?: unknownLabel(first)
        else -> "${selection.size} selected"
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectorField(
            kind = kind,
            label = label,
            summary = summary,
            showingPlaceholder = first == null,
            enabled = enabled,
            destructive = destructive,
            onClick = { expanded = true },
            swatch = if (selection.size == 1 && first != null) byId[first]?.swatch else null,
        )

        if (selection.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                selection.take(maxVisibleChips).forEach { id ->
                    SelectionChip(
                        kind = kind,
                        id = id,
                        option = byId[id],
                        enabled = enabled,
                        destructive = destructive,
                        onRemove = { onSelectionChange(selection - id) },
                    )
                }
                val hidden = selection.size - maxVisibleChips
                if (hidden > 0) {
                    AssistChip(
                        onClick = { expanded = true },
                        enabled = enabled,
                        label = { Text("+$hidden more") },
                    )
                }
            }
        }
    }

    if (expanded) {
        DiscordSelectorSheet(
            kind = kind,
            options = options,
            selection = selection,
            onSelectionChange = onSelectionChange,
            onDismiss = { expanded = false },
            multiple = true,
            title = label,
        )
    }
}

/** One removable chip under a [MultiSelectDropdown]. */
@Composable
private fun SelectionChip(
    kind: SelectorKind,
    id: String,
    option: SelectorOption?,
    enabled: Boolean,
    destructive: Boolean,
    onRemove: () -> Unit,
) {
    val tone = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val name = option?.let { kind.prefix + it.name } ?: unknownLabel(id)
    val swatch = option?.swatch
    InputChip(
        selected = true,
        onClick = onRemove,
        enabled = enabled,
        label = {
            Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = swatch?.let {
            {
                Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                    Surface(shape = CircleShape, color = it, modifier = Modifier.size(8.dp)) {}
                }
            }
        },
        avatar = option?.imageUrl?.let { url ->
            {
                Avatar(
                    url = url,
                    contentDescription = null,
                    size = 20,
                    fallbackText = option.name,
                )
            }
        },
        trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $name",
                modifier = Modifier.size(16.dp),
            )
        },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = tone.copy(alpha = DashAlpha.Hex20),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLeadingIconColor = tone,
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = enabled,
            selected = true,
            selectedBorderColor = tone.copy(alpha = DashAlpha.Hex30),
            selectedBorderWidth = 1.dp,
        ),
    )
}

/** The chip and summary text for an id that is not among the options. */
private fun unknownLabel(id: String): String = "Unknown ($id)"

/**
 * Splits a selection change into the ids that were added and the ids that
 * were removed, in that order, for view models whose API toggles one id at
 * a time.
 */
fun diffSelection(old: List<String>, new: List<String>): Pair<List<String>, List<String>> {
    val oldSet = old.toSet()
    val newSet = new.toSet()
    return new.filter { it !in oldSet }.distinct() to old.filter { it !in newSet }.distinct()
}
