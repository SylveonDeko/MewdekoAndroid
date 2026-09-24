package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha

/**
 * One choice in an [EnumPicker]: the stored [value], the [title] shown for
 * it, and an optional [description] and [icon].
 */
data class EnumOption<T>(
    val value: T,
    val title: String,
    val description: String? = null,
    val icon: ImageVector? = null,
)

/**
 * A dropdown for choosing one value from a short fixed list (a mode, a
 * style, a unit, a scope).
 *
 * Use it instead of weighted chip rows, segmented buttons, or tabs when the
 * choice is a setting's value. Those split the width evenly and cut off any
 * label longer than its share; this field is full width, labels wrap onto a
 * second line instead of being cut, and the menu lists every choice with its
 * [EnumOption.description] so the user can tell them apart before picking.
 * The selected option's description also shows under the field when
 * [showDescription] is set.
 *
 * For Discord entities (roles, channels, members) use [DiscordSelector];
 * for switching what a screen shows use [SectionTabs].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EnumPicker(
    label: String,
    options: List<EnumOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showDescription: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == selected }
    val primary = MaterialTheme.colorScheme.primary

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = current?.title.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = current?.icon?.let { icon ->
                { Icon(icon, contentDescription = null, tint = primary) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = current?.description
                ?.takeIf { showDescription }
                ?.let { { Text(it) } },
            singleLine = false,
            maxLines = 2,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isSelected = option.value == selected
                DropdownMenuItem(
                    text = {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Text(
                                text = option.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (option.description != null) {
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        when {
                            isSelected -> Icon(Icons.Default.Check, contentDescription = "Selected", tint = primary)
                            option.icon != null -> Icon(
                                option.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> Spacer(modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(option.value)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    modifier = if (isSelected) {
                        Modifier.background(primary.copy(alpha = DashAlpha.Hex15))
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
