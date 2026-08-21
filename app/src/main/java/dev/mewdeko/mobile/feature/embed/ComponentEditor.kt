package dev.mewdeko.mobile.feature.embed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.ComponentOption
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.MessageComponent
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow

/** The Discord button styles, in the order the dashboard lists them. */
private val ButtonStyles = listOf(
    1 to "Primary",
    2 to "Secondary",
    3 to "Success",
    4 to "Danger",
    MessageComponent.LinkStyle to "Link",
)

/**
 * Edits the buttons and select menus attached to a message.
 *
 * Components are stored flat with a row index, so this groups them into rows
 * for editing and writes them back flattened.
 */
@Composable
fun ComponentEditor(
    message: EmbedMessage,
    onMessageChange: (EmbedMessage) -> Unit,
) {
    val rows = message.rows

    fun replaceAll(updated: List<List<MessageComponent>>) {
        onMessageChange(
            message.copy(
                components = updated.flatMapIndexed { index, row ->
                    row.map { it.copy(row = index) }
                },
            )
        )
    }

    if (rows.isEmpty()) {
        SectionCard {
            SectionCardHeader("Components", Icons.Default.SmartButton)
            EmptyState(
                message = "No buttons or select menus yet.",
                icon = Icons.Default.SmartButton,
            )
            AddRowButtons(canAdd = true) { isSelect ->
                replaceAll(listOf(listOf(newComponent(isSelect))))
            }
        }
        return
    }

    rows.forEachIndexed { rowIndex, row ->
        val hasSelect = row.any { it.isSelect }
        SectionCard {
            SectionCardHeader(
                title = "Row ${rowIndex + 1}",
                icon = if (hasSelect) Icons.Default.UnfoldMore else Icons.Default.SmartButton,
                trailing = {
                    Row {
                        IconButton(
                            onClick = {
                                val moved = rows.toMutableList()
                                moved.add(rowIndex - 1, moved.removeAt(rowIndex))
                                replaceAll(moved)
                            },
                            enabled = rowIndex > 0,
                        ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(
                            onClick = {
                                val moved = rows.toMutableList()
                                moved.add(rowIndex + 1, moved.removeAt(rowIndex))
                                replaceAll(moved)
                            },
                            enabled = rowIndex < rows.size - 1,
                        ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down") }
                        IconButton(
                            onClick = {
                                replaceAll(rows.filterIndexed { i, _ -> i != rowIndex })
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete row",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )

            row.forEachIndexed { itemIndex, component ->
                ComponentCard(
                    component = component,
                    onChange = { updated ->
                        replaceAll(
                            rows.mapIndexed { i, r ->
                                if (i != rowIndex) r
                                else r.mapIndexed { j, c -> if (j == itemIndex) updated else c }
                            }
                        )
                    },
                    onRemove = {
                        val trimmed = rows.mapIndexed { i, r ->
                            if (i != rowIndex) r else r.filterIndexed { j, _ -> j != itemIndex }
                        }.filter { it.isNotEmpty() }
                        replaceAll(trimmed)
                    },
                )
            }

            if (!hasSelect && row.size < MessageComponent.MaxButtonsPerRow) {
                OutlinedButton(
                    onClick = {
                        replaceAll(
                            rows.mapIndexed { i, r ->
                                if (i != rowIndex) r else r + newComponent(isSelect = false)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Add button to row", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    SectionCard {
        AddRowButtons(canAdd = rows.size < MessageComponent.MaxRows) { isSelect ->
            replaceAll(rows + listOf(listOf(newComponent(isSelect))))
        }
        if (rows.size >= MessageComponent.MaxRows) {
            Text(
                "Discord allows at most ${MessageComponent.MaxRows} rows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddRowButtons(canAdd: Boolean, onAdd: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onAdd(false) },
            enabled = canAdd,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.SmartButton, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Button row", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(
            onClick = { onAdd(true) },
            enabled = canAdd,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.UnfoldMore, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Select menu", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ComponentCard(
    component: MessageComponent,
    onChange: (MessageComponent) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (component.isSelect) "Select menu" else "Button",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!component.isValid) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    ) {
                        Text(
                            "Incomplete",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove component",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            MewdekoTextField(
                value = component.displayName,
                onValueChange = { onChange(component.copy(displayName = it)) },
                label = if (component.isSelect) "Placeholder" else "Label",
            )
            MewdekoTextField(
                value = component.emoji,
                onValueChange = { onChange(component.copy(emoji = it)) },
                label = "Emoji",
                placeholder = "Optional",
            )

            if (component.isSelect) {
                SliderRow(
                    label = "Minimum choices",
                    value = component.minOptions.toFloat(),
                    onValueChange = { onChange(component.copy(minOptions = it.toInt())) },
                    valueRange = 0f..component.options.size.coerceAtLeast(1).toFloat(),
                )
                SliderRow(
                    label = "Maximum choices",
                    value = component.maxOptions.toFloat(),
                    onValueChange = { onChange(component.copy(maxOptions = it.toInt())) },
                    valueRange = 1f..component.options.size.coerceAtLeast(1).toFloat(),
                )
                OptionList(component, onChange)
            } else {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.SmartButton),
                    options = ButtonStyles.map { (raw, label) ->
                        SelectorOption(raw.toString(), label)
                    },
                    placeholder = "Primary",
                    selectedId = component.style.toString(),
                    onSelect = { onChange(component.copy(style = it?.toIntOrNull() ?: 1)) },
                    label = "Style",
                )
                if (component.isLink) {
                    MewdekoTextField(
                        value = component.url,
                        onValueChange = { onChange(component.copy(url = it)) },
                        label = "URL",
                        placeholder = "https://example.com",
                    )
                } else {
                    MewdekoTextField(
                        value = component.id.orEmpty(),
                        onValueChange = {
                            onChange(component.copy(id = it.takeIf { v -> v.isNotBlank() }))
                        },
                        label = "Custom id",
                        supportingText = "Matched by chat triggers when the button is pressed.",
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionList(
    component: MessageComponent,
    onChange: (MessageComponent) -> Unit,
) {
    Text(
        "Options (${component.options.size}/${MessageComponent.MaxOptions})",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    component.options.forEachIndexed { index, option ->
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Option ${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onChange(
                                component.copy(
                                    options = component.options
                                        .filterIndexed { i, _ -> i != index },
                                )
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove option",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                fun edit(transform: (ComponentOption) -> ComponentOption) = onChange(
                    component.copy(
                        options = component.options.mapIndexed { i, o ->
                            if (i == index) transform(o) else o
                        },
                    )
                )
                MewdekoTextField(
                    value = option.name,
                    onValueChange = { value -> edit { it.copy(name = value) } },
                    label = "Label",
                )
                MewdekoTextField(
                    value = option.description,
                    onValueChange = { value -> edit { it.copy(description = value) } },
                    label = "Description",
                )
                MewdekoTextField(
                    value = option.emoji,
                    onValueChange = { value -> edit { it.copy(emoji = value) } },
                    label = "Emoji",
                    placeholder = "Optional",
                )
            }
        }
    }
    if (component.options.size < MessageComponent.MaxOptions) {
        OutlinedButton(
            onClick = {
                onChange(
                    component.copy(
                        options = component.options + ComponentOption(
                            name = "Option ${component.options.size + 1}",
                        ),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add option", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** A blank component of the requested kind. */
private fun newComponent(isSelect: Boolean) = if (isSelect) {
    MessageComponent(isSelect = true, displayName = "Select an option")
} else {
    MessageComponent(displayName = "Button")
}
