package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.SwitchRow

/** Icon shown for each built-in rank card element in the layer list. */
private val BuiltInElementIcons: Map<String, ImageVector> = mapOf(
    "user-icon" to Icons.Default.Person,
    "user-text" to Icons.Default.ShortText,
    "progress-bar" to Icons.Default.ShowChart,
    "guild-rank" to Icons.Default.EmojiEvents,
    "guild-level" to Icons.Default.Star,
    "time-on-level" to Icons.Default.Schedule,
    "awarded" to Icons.Default.CardGiftcard,
)

/** Icon shown for each custom element type. */
private fun customElementIcon(type: String): ImageVector = when (type) {
    "rectangle" -> Icons.Default.Widgets
    "ellipse" -> Icons.Default.Circle
    "line" -> Icons.Default.SwapHoriz
    "text" -> Icons.Default.ShortText
    "image" -> Icons.Default.Image
    "progress" -> Icons.Default.ShowChart
    else -> Icons.Default.Widgets
}

/**
 * The rank card template tab: live preview, built-in element placement,
 * custom layers, presets, and JSON import/export.
 */
@Composable
fun XpTemplateTab(state: XpState, viewModel: XpViewModel) {
    var editingBuiltIn by remember { mutableStateOf<String?>(null) }
    var editingCustomId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCustomId by remember { mutableStateOf<String?>(null) }
    var pendingPreset by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    SectionCard {
        SectionCardHeader("Rank card preview", Icons.Default.Image)
        XpCardPreview(
            template = state.template,
            customElements = state.customElements,
            builtInOrder = state.builtInOrder,
            backgroundUrl = state.settings.customXpImageUrl.takeIf { it.isNotBlank() },
            viewer = state.viewerPreview,
        )
        Text(
            text = "${state.template.outputSizeX}×${state.template.outputSizeY}px" +
                if (state.settings.customXpImageUrl.isBlank()) " · default background" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard {
        SectionCardHeader("Card size", Icons.Default.Widgets)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = state.template.outputSizeX.toString(),
                onValueChange = { value ->
                    viewModel.editTemplate { it.copy(outputSizeX = value.filter(Char::isDigit).toIntOrNull() ?: it.outputSizeX) }
                },
                label = "Width",
                numeric = true,
                modifier = Modifier.weight(1f),
            )
            MewdekoTextField(
                value = state.template.outputSizeY.toString(),
                onValueChange = { value ->
                    viewModel.editTemplate { it.copy(outputSizeY = value.filter(Char::isDigit).toIntOrNull() ?: it.outputSizeY) }
                },
                label = "Height",
                numeric = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::undoTemplate,
                enabled = state.templateUndoStack.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null)
                Text("Undo", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = viewModel::redoTemplate,
                enabled = state.templateRedoStack.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null)
                Text("Redo", modifier = Modifier.padding(start = 6.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::resetTemplateChanges,
                enabled = state.hasUnsavedTemplate,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Text("Reset", modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (state.hasUnsavedTemplate) {
            Button(onClick = viewModel::saveTemplate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("Save template changes", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    SectionCard {
        SectionCardHeader("Built-in elements", Icons.Default.Layers)
        Text(
            text = "Order controls what draws on top; custom layers always draw above these.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.builtInOrder.asReversed().forEach { id ->
            val index = state.builtInOrder.indexOf(id)
            val visible = when (id) {
                "user-icon" -> state.template.templateUser.showIcon
                "user-text" -> state.template.templateUser.showText
                "progress-bar" -> state.template.templateBar.showBar
                "guild-rank" -> state.template.templateGuild.showGuildRank
                "guild-level" -> state.template.templateGuild.showGuildLevel
                "time-on-level" -> state.template.showTimeOnLevel
                "awarded" -> state.template.showAwarded
                else -> true
            }
            ListItem(
                leadingContent = {
                    Icon(BuiltInElementIcons[id] ?: Icons.Default.Widgets, contentDescription = null)
                },
                headlineContent = { Text(BuiltInElementLabels[id] ?: id) },
                supportingContent = { Text(if (visible) "Shown" else "Hidden") },
                trailingContent = {
                    Row {
                        IconButton(
                            onClick = { viewModel.moveBuiltInElement(id, -1) },
                            enabled = index > 0,
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move forward") }
                        IconButton(
                            onClick = { viewModel.moveBuiltInElement(id, 1) },
                            enabled = index < state.builtInOrder.size - 1,
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move backward") }
                        IconButton(onClick = { editingBuiltIn = id }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${BuiltInElementLabels[id]}")
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    SectionCard {
        SectionCardHeader("Custom layers", Icons.Default.Palette)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                XpCustomElementType.RECTANGLE,
                XpCustomElementType.ELLIPSE,
                XpCustomElementType.LINE,
            ).forEach { type ->
                OutlinedButton(onClick = { viewModel.addCustomElement(type) }, modifier = Modifier.weight(1f)) {
                    Icon(customElementIcon(type.raw), contentDescription = null)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                XpCustomElementType.TEXT,
                XpCustomElementType.IMAGE,
                XpCustomElementType.PROGRESS,
            ).forEach { type ->
                OutlinedButton(onClick = { viewModel.addCustomElement(type) }, modifier = Modifier.weight(1f)) {
                    Icon(customElementIcon(type.raw), contentDescription = null)
                }
            }
        }
        if (state.customElements.isEmpty()) {
            EmptyState("No custom layers yet. Add one above.", icon = Icons.Default.Palette)
        } else {
            state.customElements.sortedByDescending { it.zIndex }.forEach { element ->
                val index = state.customElements.indexOfFirst { it.id == element.id }
                Column {
                    ListItem(
                        leadingContent = { Icon(customElementIcon(element.type), contentDescription = null) },
                        headlineContent = { Text(element.label.ifBlank { element.type }) },
                        supportingContent = { Text(if (element.visible) element.type else "${element.type} · hidden") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingCustomId = element.id }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit layer")
                                }
                                IconButton(onClick = { pendingDeleteCustomId = element.id }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete layer",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.updateCustomElement(element.id) { it.copy(visible = !it.visible) }
                            },
                        ) {
                            Icon(
                                if (element.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle visibility",
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveCustomElement(element.id, 1) },
                            enabled = index < state.customElements.size - 1,
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move forward") }
                        IconButton(
                            onClick = { viewModel.moveCustomElement(element.id, -1) },
                            enabled = index > 0,
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move backward") }
                        IconButton(onClick = { viewModel.duplicateCustomElement(element.id) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate layer")
                        }
                    }
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Presets and backup", Icons.Default.AutoAwesome)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("minimal" to "Minimal", "glass" to "Glass", "gaming" to "Gaming").forEach { (id, label) ->
                OutlinedButton(onClick = { pendingPreset = id }, modifier = Modifier.weight(1f)) {
                    Text(label)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(viewModel.exportTemplateJson())) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Text("Copy JSON", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = { showImportDialog = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("Import JSON", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }

    editingBuiltIn?.let { id ->
        BuiltInElementDialog(id = id, state = state, viewModel = viewModel, onDismiss = { editingBuiltIn = null })
    }

    editingCustomId?.let { id ->
        val element = state.customElements.firstOrNull { it.id == id }
        if (element != null) {
            CustomElementDialog(
                element = element,
                cardWidth = state.template.outputSizeX,
                cardHeight = state.template.outputSizeY,
                onDismiss = { editingCustomId = null },
                onSave = { updated -> viewModel.updateCustomElement(id) { updated } },
            )
        }
    }

    pendingDeleteCustomId?.let { id ->
        val element = state.customElements.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "Delete layer?",
            message = "\"${element?.label.orEmpty().ifBlank { "This layer" }}\" will be removed from the card.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.removeCustomElement(id) },
            onDismiss = { pendingDeleteCustomId = null },
        )
    }

    pendingPreset?.let { name ->
        ConfirmDialog(
            title = "Apply preset?",
            message = "This replaces every custom layer on the card with the $name preset.",
            confirmLabel = "Apply",
            destructive = state.customElements.isNotEmpty(),
            onConfirm = { viewModel.applyTemplatePreset(name) },
            onDismiss = { pendingPreset = null },
        )
    }

    if (showImportDialog) {
        var text by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import layers") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste a template exported from the dashboard's card editor, or from " +
                            "another guild.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    MewdekoTextField(
                        value = text,
                        onValueChange = { text = it; error = false },
                        label = "JSON",
                        singleLine = false,
                        minLines = 4,
                        isError = error,
                        supportingText = if (error) "That wasn't a valid layer JSON array." else null,
                    )
                    TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) {
                        Text("Paste from clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (viewModel.importTemplateJson(text)) {
                            showImportDialog = false
                        } else {
                            error = true
                        }
                    },
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BuiltInElementDialog(
    id: String,
    state: XpState,
    viewModel: XpViewModel,
    onDismiss: () -> Unit,
) {
    when (id) {
        "user-icon" -> {
            val current = state.template.templateUser
            var x by remember { mutableStateOf(current.iconX.toString()) }
            var y by remember { mutableStateOf(current.iconY.toString()) }
            var w by remember { mutableStateOf(current.iconSizeX.toString()) }
            var h by remember { mutableStateOf(current.iconSizeY.toString()) }
            var show by remember { mutableStateOf(current.showIcon) }
            PositionDialog(
                title = "User avatar",
                onDismiss = onDismiss,
                show = show,
                onShowChange = { show = it },
                fields = listOf("X" to x, "Y" to y, "Width" to w, "Height" to h),
                onFieldChange = { index, value ->
                    when (index) {
                        0 -> x = value
                        1 -> y = value
                        2 -> w = value
                        3 -> h = value
                    }
                },
                onSave = {
                    viewModel.editTemplateUser {
                        it.copy(
                            iconX = x.toIntOrNull() ?: it.iconX,
                            iconY = y.toIntOrNull() ?: it.iconY,
                            iconSizeX = w.toIntOrNull() ?: it.iconSizeX,
                            iconSizeY = h.toIntOrNull() ?: it.iconSizeY,
                            showIcon = show,
                        )
                    }
                },
            )
        }

        "user-text" -> {
            val current = state.template.templateUser
            TextElementDialog(
                title = "Username",
                x = current.textX,
                y = current.textY,
                fontSize = current.fontSize,
                colorHex = current.textColor,
                show = current.showText,
                onDismiss = onDismiss,
                onSave = { x, y, size, color, show ->
                    viewModel.editTemplateUser {
                        it.copy(textX = x, textY = y, fontSize = size, textColor = color, showText = show)
                    }
                },
            )
        }

        "guild-rank" -> {
            val current = state.template.templateGuild
            TextElementDialog(
                title = "Guild rank",
                x = current.guildRankX,
                y = current.guildRankY,
                fontSize = current.guildRankFontSize,
                colorHex = current.guildRankColor,
                show = current.showGuildRank,
                onDismiss = onDismiss,
                onSave = { x, y, size, color, show ->
                    viewModel.editTemplateGuild {
                        it.copy(
                            guildRankX = x, guildRankY = y, guildRankFontSize = size,
                            guildRankColor = color, showGuildRank = show,
                        )
                    }
                },
            )
        }

        "guild-level" -> {
            val current = state.template.templateGuild
            TextElementDialog(
                title = "Guild level",
                x = current.guildLevelX,
                y = current.guildLevelY,
                fontSize = current.guildLevelFontSize,
                colorHex = current.guildLevelColor,
                show = current.showGuildLevel,
                onDismiss = onDismiss,
                onSave = { x, y, size, color, show ->
                    viewModel.editTemplateGuild {
                        it.copy(
                            guildLevelX = x, guildLevelY = y, guildLevelFontSize = size,
                            guildLevelColor = color, showGuildLevel = show,
                        )
                    }
                },
            )
        }

        "time-on-level" -> {
            val current = state.template
            TextElementDialog(
                title = "Time on level",
                x = current.timeOnLevelX,
                y = current.timeOnLevelY,
                fontSize = current.timeOnLevelFontSize,
                colorHex = current.timeOnLevelColor,
                show = current.showTimeOnLevel,
                onDismiss = onDismiss,
                onSave = { x, y, size, color, show ->
                    viewModel.editTemplateFieldUndoable {
                        it.copy(
                            timeOnLevelX = x, timeOnLevelY = y, timeOnLevelFontSize = size,
                            timeOnLevelColor = color, showTimeOnLevel = show,
                        )
                    }
                },
            )
        }

        "awarded" -> {
            val current = state.template
            TextElementDialog(
                title = "Awarded XP",
                x = current.awardedX,
                y = current.awardedY,
                fontSize = current.awardedFontSize,
                colorHex = current.awardedColor,
                show = current.showAwarded,
                onDismiss = onDismiss,
                onSave = { x, y, size, color, show ->
                    viewModel.editTemplateFieldUndoable {
                        it.copy(
                            awardedX = x, awardedY = y, awardedFontSize = size,
                            awardedColor = color, showAwarded = show,
                        )
                    }
                },
            )
        }

        "progress-bar" -> {
            val current = state.template.templateBar
            var ax by remember { mutableStateOf(current.barPointAx.toString()) }
            var ay by remember { mutableStateOf(current.barPointAy.toString()) }
            var bx by remember { mutableStateOf(current.barPointBx.toString()) }
            var by by remember { mutableStateOf(current.barPointBy.toString()) }
            var length by remember { mutableStateOf(current.barLength.toString()) }
            var transparency by remember { mutableStateOf(current.barTransparency.toFloat()) }
            var color by remember { mutableStateOf(current.barColor) }
            var direction by remember { mutableStateOf(current.barDirection.toString()) }
            var show by remember { mutableStateOf(current.showBar) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("XP progress bar") },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SwitchRow(title = "Shown on card", checked = show, onCheckedChange = { show = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MewdekoTextField(ax, { ax = it.filter(Char::isDigit) }, "Point A X", numeric = true, modifier = Modifier.weight(1f))
                            MewdekoTextField(ay, { ay = it.filter(Char::isDigit) }, "Point A Y", numeric = true, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MewdekoTextField(bx, { bx = it.filter(Char::isDigit) }, "Point B X", numeric = true, modifier = Modifier.weight(1f))
                            MewdekoTextField(by, { by = it.filter(Char::isDigit) }, "Point B Y", numeric = true, modifier = Modifier.weight(1f))
                        }
                        MewdekoTextField(length, { length = it.filter(Char::isDigit) }, "Length", numeric = true)
                        SliderRow(
                            label = "Transparency",
                            value = transparency,
                            onValueChange = { transparency = it },
                            valueRange = 0f..255f,
                            valueLabel = "${transparency.toInt()}",
                        )
                        MewdekoTextField(color, { color = it }, "Bar color (AARRGGBB)")
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.ShowChart),
                            options = listOf(
                                SelectorOption("0", "Up"), SelectorOption("1", "Down"),
                                SelectorOption("2", "Left"), SelectorOption("3", "Right"),
                            ),
                            placeholder = "Direction",
                            label = "Fill direction",
                            selectedId = direction,
                            onSelect = { direction = it ?: direction },
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.editTemplateBar {
                                it.copy(
                                    barPointAx = ax.toIntOrNull() ?: it.barPointAx,
                                    barPointAy = ay.toIntOrNull() ?: it.barPointAy,
                                    barPointBx = bx.toIntOrNull() ?: it.barPointBx,
                                    barPointBy = by.toIntOrNull() ?: it.barPointBy,
                                    barLength = length.toIntOrNull() ?: it.barLength,
                                    barTransparency = transparency.toInt(),
                                    barColor = color,
                                    barDirection = direction.toIntOrNull() ?: it.barDirection,
                                    showBar = show,
                                )
                            }
                            onDismiss()
                        },
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun PositionDialog(
    title: String,
    show: Boolean,
    onShowChange: (Boolean) -> Unit,
    fields: List<Pair<String, String>>,
    onFieldChange: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwitchRow(title = "Shown on card", checked = show, onCheckedChange = onShowChange)
                fields.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (label, value) ->
                            val index = fields.indexOfFirst { it.first == label }
                            MewdekoTextField(
                                value = value,
                                onValueChange = { onFieldChange(index, it.filter(Char::isDigit)) },
                                label = label,
                                numeric = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextElementDialog(
    title: String,
    x: Int,
    y: Int,
    fontSize: Int,
    colorHex: String,
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: (x: Int, y: Int, fontSize: Int, colorHex: String, show: Boolean) -> Unit,
) {
    var xField by remember { mutableStateOf(x.toString()) }
    var yField by remember { mutableStateOf(y.toString()) }
    var sizeField by remember { mutableStateOf(fontSize.toString()) }
    var colorField by remember { mutableStateOf(colorHex) }
    var showField by remember { mutableStateOf(show) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwitchRow(title = "Shown on card", checked = showField, onCheckedChange = { showField = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(xField, { xField = it.filter(Char::isDigit) }, "X", numeric = true, modifier = Modifier.weight(1f))
                    MewdekoTextField(yField, { yField = it.filter(Char::isDigit) }, "Y", numeric = true, modifier = Modifier.weight(1f))
                }
                MewdekoTextField(sizeField, { sizeField = it.filter(Char::isDigit) }, "Font size", numeric = true)
                MewdekoTextField(colorField, { colorField = it }, "Color (AARRGGBB)")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        xField.toIntOrNull() ?: x,
                        yField.toIntOrNull() ?: y,
                        sizeField.toIntOrNull() ?: fontSize,
                        colorField,
                        showField,
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomElementDialog(
    element: XpCustomElement,
    cardWidth: Int,
    cardHeight: Int,
    onDismiss: () -> Unit,
    onSave: (XpCustomElement) -> Unit,
) {
    var label by remember { mutableStateOf(element.label) }
    var x by remember { mutableStateOf(element.x.toInt().toString()) }
    var y by remember { mutableStateOf(element.y.toInt().toString()) }
    var width by remember { mutableStateOf(element.width.toInt().toString()) }
    var height by remember { mutableStateOf(element.height.toInt().toString()) }
    var rotation by remember { mutableStateOf(element.rotation.toFloat()) }
    var opacity by remember { mutableStateOf(element.opacity.toFloat()) }
    var cornerRadius by remember { mutableStateOf(element.cornerRadius.toInt().toString()) }
    var fill by remember { mutableStateOf(element.fill) }
    var stroke by remember { mutableStateOf(element.stroke) }
    var strokeWidth by remember { mutableStateOf(element.strokeWidth.toInt().toString()) }
    var text by remember { mutableStateOf(element.text) }
    var fontSize by remember { mutableStateOf(element.fontSize.toInt().toString()) }
    var textAlign by remember { mutableStateOf(element.textAlign) }
    var url by remember { mutableStateOf(element.url) }
    var trackFill by remember { mutableStateOf(element.trackFill) }
    var progressStyle by remember { mutableStateOf(element.progressStyle) }
    var segments by remember { mutableStateOf(element.segments.toString()) }
    var gradientEnd by remember { mutableStateOf(element.gradientEnd) }
    var gradientAngle by remember { mutableStateOf(element.gradientAngle.toInt().toString()) }
    var shadowBlur by remember { mutableStateOf(element.shadowBlur.toInt().toString()) }
    var shadowX by remember { mutableStateOf(element.shadowX.toInt().toString()) }
    var shadowY by remember { mutableStateOf(element.shadowY.toInt().toString()) }

    /** Snaps the draft X/Y so the layer sits against the given edge of the card. */
    fun alignTo(edge: String) {
        val w = width.toDoubleOrNull() ?: element.width
        val h = height.toDoubleOrNull() ?: element.height
        when (edge) {
            "left" -> x = "0"
            "center" -> x = ((cardWidth - w) / 2).toInt().toString()
            "right" -> x = (cardWidth - w).toInt().toString()
            "top" -> y = "0"
            "middle" -> y = ((cardHeight - h) / 2).toInt().toString()
            "bottom" -> y = (cardHeight - h).toInt().toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit layer") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MewdekoTextField(label, { label = it }, "Label")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(x, { x = it.filter { c -> c.isDigit() || c == '-' } }, "X", numeric = true, modifier = Modifier.weight(1f))
                    MewdekoTextField(y, { y = it.filter { c -> c.isDigit() || c == '-' } }, "Y", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(width, { width = it.filter(Char::isDigit) }, "Width", numeric = true, modifier = Modifier.weight(1f))
                    MewdekoTextField(height, { height = it.filter(Char::isDigit) }, "Height", numeric = true, modifier = Modifier.weight(1f))
                }
                SliderRow(
                    label = "Opacity",
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f,
                    valueLabel = "${(opacity * 100).toInt()}%",
                )
                SliderRow(
                    label = "Rotation",
                    value = rotation,
                    onValueChange = { rotation = it },
                    valueRange = -180f..180f,
                    valueLabel = "${rotation.toInt()}°",
                )

                if (element.type == "text") {
                    MewdekoTextField(text, { text = it }, "Text", singleLine = false, minLines = 2,
                        supportingText = "Supports %xp.user%, %xp.level.current%, %xp.rank%, %xp.progress%, and similar placeholders.")
                    MewdekoTextField(fontSize, { fontSize = it.filter(Char::isDigit) }, "Font size", numeric = true)
                    MewdekoTextField(fill, { fill = it }, "Text color (#RRGGBB or #RRGGBBAA)")
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.ShortText),
                        options = listOf(
                            SelectorOption("left", "Left"),
                            SelectorOption("center", "Center"),
                            SelectorOption("right", "Right"),
                        ),
                        placeholder = "Alignment",
                        label = "Text alignment",
                        selectedId = textAlign,
                        onSelect = { textAlign = it ?: textAlign },
                    )
                }

                if (element.type == "image") {
                    MewdekoTextField(url, { url = it }, "Image URL")
                    MewdekoTextField(cornerRadius, { cornerRadius = it.filter(Char::isDigit) }, "Corner radius", numeric = true)
                }

                if (element.type == "progress") {
                    MewdekoTextField(fill, { fill = it }, "Fill color (#RRGGBB or #RRGGBBAA)")
                    MewdekoTextField(trackFill, { trackFill = it }, "Track color (#RRGGBB or #RRGGBBAA)")
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.ShowChart),
                        options = listOf(
                            SelectorOption("rounded", "Rounded"),
                            SelectorOption("segmented", "Segmented"),
                            SelectorOption("radial", "Radial"),
                        ),
                        placeholder = "Style",
                        label = "Progress style",
                        selectedId = progressStyle,
                        onSelect = { progressStyle = it ?: progressStyle },
                    )
                    if (progressStyle == "segmented") {
                        MewdekoTextField(segments, { segments = it.filter(Char::isDigit) }, "Segments", numeric = true)
                    }
                    MewdekoTextField(cornerRadius, { cornerRadius = it.filter(Char::isDigit) }, "Corner radius", numeric = true)
                }

                if (element.type == "rectangle" || element.type == "ellipse") {
                    MewdekoTextField(fill, { fill = it }, "Fill color (#RRGGBB or #RRGGBBAA)")
                    if (element.type == "rectangle") {
                        MewdekoTextField(cornerRadius, { cornerRadius = it.filter(Char::isDigit) }, "Corner radius", numeric = true)
                    }
                    MewdekoTextField(stroke, { stroke = it }, "Stroke color (#RRGGBB or #RRGGBBAA)")
                    MewdekoTextField(strokeWidth, { strokeWidth = it.filter(Char::isDigit) }, "Stroke width", numeric = true)
                }

                if (element.type == "line") {
                    MewdekoTextField(fill, { fill = it }, "Line color (#RRGGBB or #RRGGBBAA)")
                    MewdekoTextField(strokeWidth, { strokeWidth = it.filter(Char::isDigit) }, "Thickness", numeric = true)
                }

                if (element.type != "image") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MewdekoTextField(
                            gradientEnd, { gradientEnd = it }, "Gradient end (#RRGGBB, optional)",
                            modifier = Modifier.weight(1f),
                        )
                        MewdekoTextField(
                            gradientAngle, { gradientAngle = it.filter { c -> c.isDigit() || c == '-' } },
                            "Angle", numeric = true, modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(onClick = { gradientEnd = "" }) { Text("Use solid fill") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(
                        shadowBlur, { shadowBlur = it.filter(Char::isDigit) }, "Shadow blur",
                        numeric = true, modifier = Modifier.weight(1f),
                    )
                    MewdekoTextField(
                        shadowX, { shadowX = it.filter { c -> c.isDigit() || c == '-' } }, "Offset X",
                        numeric = true, modifier = Modifier.weight(1f),
                    )
                    MewdekoTextField(
                        shadowY, { shadowY = it.filter { c -> c.isDigit() || c == '-' } }, "Offset Y",
                        numeric = true, modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Align to card",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        "left" to Icons.Default.AlignHorizontalLeft,
                        "center" to Icons.Default.AlignHorizontalCenter,
                        "right" to Icons.Default.AlignHorizontalRight,
                        "top" to Icons.Default.AlignVerticalTop,
                        "middle" to Icons.Default.AlignVerticalCenter,
                        "bottom" to Icons.Default.AlignVerticalBottom,
                    ).forEach { (edge, icon) ->
                        IconButton(onClick = { alignTo(edge) }) {
                            Icon(icon, contentDescription = "Align $edge")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        element.copy(
                            label = label,
                            x = x.toDoubleOrNull() ?: element.x,
                            y = y.toDoubleOrNull() ?: element.y,
                            width = width.toDoubleOrNull() ?: element.width,
                            height = height.toDoubleOrNull() ?: element.height,
                            rotation = rotation.toDouble(),
                            opacity = opacity.toDouble(),
                            cornerRadius = cornerRadius.toDoubleOrNull() ?: element.cornerRadius,
                            fill = fill,
                            stroke = stroke,
                            strokeWidth = strokeWidth.toDoubleOrNull() ?: element.strokeWidth,
                            text = text,
                            fontSize = fontSize.toDoubleOrNull() ?: element.fontSize,
                            textAlign = textAlign,
                            url = url,
                            trackFill = trackFill,
                            progressStyle = progressStyle,
                            segments = segments.toIntOrNull() ?: element.segments,
                            gradientEnd = gradientEnd,
                            gradientAngle = gradientAngle.toDoubleOrNull() ?: element.gradientAngle,
                            shadowBlur = shadowBlur.toDoubleOrNull() ?: element.shadowBlur,
                            shadowX = shadowX.toDoubleOrNull() ?: element.shadowX,
                            shadowY = shadowY.toDoubleOrNull() ?: element.shadowY,
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
