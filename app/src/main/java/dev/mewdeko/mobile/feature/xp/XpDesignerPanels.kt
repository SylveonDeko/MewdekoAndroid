package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import kotlin.math.hypot

/** Icon shown for each built-in rank card element in the layer list. */
internal val BuiltInElementIcons: Map<String, ImageVector> = mapOf(
    "user-icon" to Icons.Default.Person,
    "user-text" to Icons.AutoMirrored.Filled.ShortText,
    "progress-bar" to Icons.AutoMirrored.Filled.ShowChart,
    "guild-rank" to Icons.Default.EmojiEvents,
    "guild-level" to Icons.Default.Star,
    "time-on-level" to Icons.Default.Schedule,
    "awarded" to Icons.Default.CardGiftcard,
    "club-icon" to Icons.Default.Groups,
    "club-name" to Icons.Default.Groups,
)

/** Icon shown for each custom element type. */
internal fun customElementIcon(type: String): ImageVector = when (type) {
    "rectangle" -> Icons.Default.Widgets
    "ellipse" -> Icons.Default.Circle
    "line" -> Icons.Default.SwapHoriz
    "text" -> Icons.AutoMirrored.Filled.ShortText
    "image" -> Icons.Default.Image
    "progress" -> Icons.AutoMirrored.Filled.ShowChart
    else -> Icons.Default.Widgets
}

/** A small uppercase heading between groups inside the sheet. */
@Composable
private fun PanelHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** A muted explanatory line inside the sheet. */
@Composable
private fun PanelNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The Layers tab: add buttons, then every layer top-most first (custom
 * layers by z order, the built-ins in reverse draw order, and the dormant
 * club layers last), each with visibility, order, and delete or hide.
 */
@Composable
fun XpLayersPanel(state: XpState, viewModel: XpViewModel) {
    val controller = viewModel.designer
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val order = state.builtInOrder

    PanelHeading("Add layer")
    XpCustomElementType.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { type ->
                OutlinedButton(onClick = { viewModel.addCustomElement(type) }, modifier = Modifier.weight(1f)) {
                    Icon(customElementIcon(type.raw), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(type.title, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
                }
            }
        }
    }

    if (state.customElements.isNotEmpty()) {
        PanelHeading("Custom layers")
        val customs = state.customElements
        customs.indices.reversed().forEach { index ->
            val element = customs[index]
            LayerRow(
                icon = customElementIcon(element.type),
                label = element.label.ifBlank { element.type.replaceFirstChar { it.uppercase() } },
                subtitle = element.type.replaceFirstChar { it.uppercase() },
                visible = element.visible,
                selected = controller.selectedId == element.id,
                canMoveUp = index < customs.size - 1,
                canMoveDown = index > 0,
                onClick = {
                    controller.selectedId = element.id
                    controller.sheetTab = XpSheetTab.PROPERTIES
                },
                onToggleVisible = { viewModel.setElementVisible(element.id, !element.visible) },
                onMoveUp = { viewModel.moveLayer(element.id, 1) },
                onMoveDown = { viewModel.moveLayer(element.id, -1) },
                onDelete = { pendingDelete = element.id },
            )
        }
    }

    PanelHeading("Built-in layers")
    order.indices.reversed().forEach { index ->
        val id = order[index]
        val shown = state.template.isBuiltInShown(id)
        LayerRow(
            icon = BuiltInElementIcons[id] ?: Icons.Default.Widgets,
            label = BuiltInElementLabels[id] ?: id,
            subtitle = builtInSubtitle(state.template, id),
            visible = shown,
            selected = controller.selectedId == id,
            canMoveUp = index < order.size - 1,
            canMoveDown = index > 0,
            onClick = {
                controller.selectedId = id
                controller.sheetTab = XpSheetTab.PROPERTIES
            },
            onToggleVisible = { viewModel.setElementVisible(id, !shown) },
            onMoveUp = { viewModel.moveLayer(id, 1) },
            onMoveDown = { viewModel.moveLayer(id, -1) },
            onDelete = if (shown) ({ viewModel.setElementVisible(id, false) }) else null,
        )
    }

    PanelHeading("Not drawn by the bot yet")
    PanelNote("The template stores these, but the bot's renderer never draws them. They show only while editing.")
    listOf("club-name", "club-icon").forEach { id ->
        val shown = state.template.isBuiltInShown(id)
        LayerRow(
            icon = BuiltInElementIcons[id] ?: Icons.Default.Widgets,
            label = BuiltInElementLabels[id] ?: id,
            subtitle = "Inactive · " + builtInSubtitle(state.template, id),
            visible = shown,
            selected = controller.selectedId == id,
            canMoveUp = false,
            canMoveDown = false,
            inactive = true,
            onClick = {
                controller.selectedId = id
                controller.sheetTab = XpSheetTab.PROPERTIES
            },
            onToggleVisible = { viewModel.setElementVisible(id, !shown) },
            onMoveUp = {},
            onMoveDown = {},
            onDelete = null,
        )
    }

    pendingDelete?.let { id ->
        val element = state.customElements.firstOrNull { it.id == id }
        ConfirmDialog(
            title = "Delete layer?",
            message = "\"${element?.label.orEmpty().ifBlank { "This layer" }}\" will be removed from the card.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.removeCustomElement(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun builtInSubtitle(template: XpTemplate, id: String): String {
    if (id == "progress-bar") return "Progress bar"
    template.boxSpec(id)?.let { return "${it.width} x ${it.height} px" }
    template.textSpec(id)?.let { return "${it.fontSize} px" }
    return ""
}

@Composable
private fun LayerRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    visible: Boolean,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggleVisible: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: (() -> Unit)?,
    inactive: Boolean = false,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(primary.copy(alpha = if (selected) DashAlpha.Hex20 else DashAlpha.Hex08))
            .clickableRow(onClick)
            .alpha(if (!visible || inactive) 0.5f else 1f)
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleVisible) {
            Icon(
                if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (visible) "Hide $label" else "Show $label",
            )
        }
        if (!inactive) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Bring $label forward")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Send $label backward")
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remove $label", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** The Properties tab for the selected element, built-in or custom. */
@Composable
fun XpPropertiesPanel(state: XpState, viewModel: XpViewModel, selectedId: String) {
    val template = state.template
    when {
        selectedId == "progress-bar" -> BarProperties(template.templateBar, viewModel)
        template.textSpec(selectedId) != null -> TextProperties(selectedId, template, viewModel)
        template.boxSpec(selectedId) != null -> BoxProperties(selectedId, template, viewModel)
        else -> {
            val element = state.customElements.firstOrNull { it.id == selectedId }
            if (element == null) {
                PanelNote("That layer no longer exists.")
            } else {
                CustomProperties(element, viewModel)
            }
        }
    }
}

@Composable
private fun PositionRow(id: String, x: Int, y: Int, viewModel: XpViewModel, labelX: String = "X", labelY: String = "Y") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpIntField(x, labelX, { viewModel.commitElementPosition(id, it.toFloat(), y.toFloat()) }, Modifier.weight(1f))
        XpIntField(y, labelY, { viewModel.commitElementPosition(id, x.toFloat(), it.toFloat()) }, Modifier.weight(1f))
    }
}

@Composable
private fun TextProperties(id: String, template: XpTemplate, viewModel: XpViewModel) {
    val spec = template.textSpec(id) ?: return
    fun edit(transform: (XpTextSpec) -> XpTextSpec) = viewModel.editTemplateFieldUndoable { current ->
        val now = current.textSpec(id) ?: return@editTemplateFieldUndoable current
        current.withTextSpec(id, transform(now))
    }
    PanelHeading(BuiltInElementLabels[id] ?: id)
    when (id) {
        "club-name" -> PanelNote("Not drawn by the bot yet. Placement is saved so it is ready when it is.")
        "awarded" -> PanelNote("The bot draws this only when the member has bonus XP, as \"(+ 150)\".")
        "time-on-level" -> PanelNote("Drawn as how long ago the member levelled up, for example \"2 days ago\".")
    }
    SwitchRow(title = "Shown on card", checked = spec.shown, onCheckedChange = { viewModel.setElementVisible(id, it) })
    PanelHeading("Position")
    PositionRow(id, spec.x, spec.y, viewModel)
    XpIntField(spec.fontSize, "Font size", { size -> edit { it.copy(fontSize = size.coerceAtLeast(1)) } }, allowNegative = false)
    XpColorField(
        label = "Color (AARRGGBB)",
        value = spec.color,
        format = XpColorFormat.ARGB,
        onCommit = { hex -> edit { it.copy(color = hex) } },
    )
    if (id == "time-on-level") {
        PanelHeading("Advanced")
        XpCommitField(
            value = template.timeOnLevelFormat,
            label = "Format (legacy, unused by the bot)",
            onCommit = { format -> viewModel.editTemplateFieldUndoable { it.copy(timeOnLevelFormat = format) } },
        )
    }
}

@Composable
private fun BoxProperties(id: String, template: XpTemplate, viewModel: XpViewModel) {
    val spec = template.boxSpec(id) ?: return
    val controller = viewModel.designer
    fun edit(transform: (XpBoxSpec) -> XpBoxSpec) = viewModel.editTemplateFieldUndoable { current ->
        val now = current.boxSpec(id) ?: return@editTemplateFieldUndoable current
        current.withBoxSpec(id, transform(now))
    }
    PanelHeading(BuiltInElementLabels[id] ?: id)
    if (id == "club-icon") PanelNote("Not drawn by the bot yet. Placement is saved so it is ready when it is.")
    if (id == "user-icon") PanelNote("The member's avatar, stretched to this size with corners rounded by half the width.")
    SwitchRow(title = "Shown on card", checked = spec.shown, onCheckedChange = { viewModel.setElementVisible(id, it) })
    PanelHeading("Position")
    PositionRow(id, spec.x, spec.y, viewModel)
    PanelHeading("Size")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpIntField(spec.width, "Width", { w ->
            val width = w.coerceAtLeast(1)
            edit { if (controller.lockProportions) it.copy(width = width, height = width) else it.copy(width = width) }
        }, Modifier.weight(1f), allowNegative = false)
        XpIntField(spec.height, "Height", { h ->
            val height = h.coerceAtLeast(1)
            edit { if (controller.lockProportions) it.copy(width = height, height = height) else it.copy(height = height) }
        }, Modifier.weight(1f), allowNegative = false)
    }
    SwitchRow(
        title = "Lock proportions",
        subtitle = "Editing width or height sets both",
        checked = controller.lockProportions,
        onCheckedChange = { controller.lockProportions = it },
    )
}

@Composable
private fun BarProperties(bar: XpTemplateBar, viewModel: XpViewModel) {
    PanelHeading("XP Progress Bar")
    PanelNote("A-B is the bar's start edge; the bar grows from it by Length times the member's progress.")
    SwitchRow(
        title = "Shown on card",
        checked = bar.showBar,
        onCheckedChange = { viewModel.setElementVisible("progress-bar", it) },
    )
    PanelHeading("Point A")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpIntField(bar.barPointAx, "A X", { v -> viewModel.editTemplateBar { it.copy(barPointAx = v) } }, Modifier.weight(1f))
        XpIntField(bar.barPointAy, "A Y", { v -> viewModel.editTemplateBar { it.copy(barPointAy = v) } }, Modifier.weight(1f))
    }
    PanelHeading("Point B")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpIntField(bar.barPointBx, "B X", { v -> viewModel.editTemplateBar { it.copy(barPointBx = v) } }, Modifier.weight(1f))
        XpIntField(bar.barPointBy, "B Y", { v -> viewModel.editTemplateBar { it.copy(barPointBy = v) } }, Modifier.weight(1f))
    }
    InfoRow(
        label = "Thickness",
        value = "${Math.round(hypot((bar.barPointBx - bar.barPointAx).toDouble(), (bar.barPointBy - bar.barPointAy).toDouble()))} px",
    )
    XpIntField(bar.barLength, "Length", { v -> viewModel.editTemplateBar { it.copy(barLength = v) } }, allowNegative = false)
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ShowChart),
        options = listOf(
            SelectorOption("3", "Right"),
            SelectorOption("2", "Left"),
            SelectorOption("1", "Down"),
            SelectorOption("0", "Up"),
        ),
        placeholder = "Direction",
        label = "Fill direction",
        selectedId = bar.barDirection.coerceIn(0, 3).toString(),
        onSelect = { id -> id?.toIntOrNull()?.let { v -> viewModel.editTemplateBar { it.copy(barDirection = v) } } },
    )
    XpColorField(
        label = "Color (opacity comes from the slider)",
        value = bar.barColor,
        format = XpColorFormat.ARGB,
        allowAlpha = false,
        onCommit = { hex -> viewModel.editTemplateBar { it.copy(barColor = hex) } },
    )
    XpUndoableSlider(
        label = "Opacity",
        value = bar.barTransparency.coerceIn(0, 255).toFloat(),
        valueRange = 0f..255f,
        valueLabel = "${Math.round(bar.barTransparency.coerceIn(0, 255) / 255f * 100)}%",
        onBegin = viewModel::beginTemplateEdit,
        onChange = { v ->
            viewModel.editTemplate { it.copy(templateBar = it.templateBar.copy(barTransparency = Math.round(v))) }
        },
    )
}

@Composable
private fun CustomProperties(element: XpCustomElement, viewModel: XpViewModel) {
    val id = element.id
    var confirmDelete by remember { mutableStateOf(false) }
    fun edit(transform: (XpCustomElement) -> XpCustomElement) = viewModel.updateCustomElement(id, transform)
    val type = element.type

    PanelHeading(element.type.replaceFirstChar { it.uppercase() } + " layer")
    XpCommitField(value = element.label, label = "Label", onCommit = { v -> edit { it.copy(label = v) } })
    SwitchRow(title = "Visible", checked = element.visible, onCheckedChange = { viewModel.setElementVisible(id, it) })

    PanelHeading("Position and size")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpNumberField(element.x, "X", { viewModel.commitElementPosition(id, it.toFloat(), element.y.toFloat()) }, Modifier.weight(1f))
        XpNumberField(element.y, "Y", { viewModel.commitElementPosition(id, element.x.toFloat(), it.toFloat()) }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpNumberField(element.width, "Width", { v -> edit { it.copy(width = v) } }, Modifier.weight(1f))
        XpNumberField(element.height, "Height", { v -> edit { it.copy(height = v) } }, Modifier.weight(1f))
    }
    if (type == "line") PanelNote("A line runs from X, Y to X + Width, Y + Height.")

    when (type) {
        "text" -> {
            PanelHeading("Text")
            XpCommitField(
                value = element.text,
                label = "Text",
                onCommit = { v -> edit { it.copy(text = v) } },
                singleLine = false,
                minLines = 2,
                supportingText = XpPlaceholderHelp,
            )
            XpNumberField(element.fontSize, "Font size", { v -> edit { it.copy(fontSize = v.coerceAtLeast(1.0)) } }, allowNegative = false)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ShortText),
                options = listOf(
                    SelectorOption("left", "Left"),
                    SelectorOption("center", "Center"),
                    SelectorOption("right", "Right"),
                ),
                placeholder = "Alignment",
                label = "Alignment",
                selectedId = element.textAlign,
                onSelect = { v -> if (v != null) edit { it.copy(textAlign = v) } },
            )
        }
        "image" -> {
            PanelHeading("Image")
            XpCommitField(
                value = element.url,
                label = "Image URL",
                onCommit = { v -> edit { it.copy(url = v.trim()) } },
                isError = element.url.isNotBlank() && !isHttpUrl(element.url),
                supportingText = "An absolute http or https link. Stretched to the layer's size.",
            )
        }
        "progress" -> {
            PanelHeading("Progress")
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ShowChart),
                options = listOf(
                    SelectorOption("rounded", "Rounded"),
                    SelectorOption("segmented", "Segmented"),
                    SelectorOption("radial", "Radial"),
                ),
                placeholder = "Style",
                label = "Style",
                selectedId = element.progressStyle,
                onSelect = { v -> if (v != null) edit { it.copy(progressStyle = v) } },
            )
            if (element.progressStyle == "segmented") {
                XpIntField(element.segments, "Segments (2 to 50)", { v -> edit { it.copy(segments = v.coerceIn(2, 50)) } }, allowNegative = false)
            }
            XpColorField(
                label = "Track color",
                value = element.trackFill,
                format = XpColorFormat.CSS,
                onCommit = { hex -> edit { it.copy(trackFill = hex) } },
            )
        }
    }

    if (type != "image") {
        PanelHeading("Fill")
        XpColorField(
            label = when (type) {
                "text" -> "Text color"
                "line" -> "Line color"
                else -> "Fill color"
            },
            value = element.fill,
            format = XpColorFormat.CSS,
            onCommit = { hex -> edit { it.copy(fill = hex) } },
        )
        XpColorField(
            label = "Gradient end (optional)",
            value = element.gradientEnd,
            format = XpColorFormat.CSS,
            allowBlank = true,
            onCommit = { hex -> edit { it.copy(gradientEnd = hex) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            XpNumberField(element.gradientAngle, "Gradient angle", { v -> edit { it.copy(gradientAngle = v) } }, Modifier.weight(1f))
            TextButton(onClick = { edit { it.copy(gradientEnd = "") } }, enabled = element.gradientEnd.isNotBlank()) {
                Text("Use solid fill")
            }
        }
    }

    when (type) {
        "rectangle", "ellipse", "progress" -> {
            PanelHeading("Border")
            XpColorField(
                label = "Border color",
                value = element.stroke,
                format = XpColorFormat.CSS,
                onCommit = { hex -> edit { it.copy(stroke = hex) } },
            )
            XpNumberField(
                element.strokeWidth,
                if (type == "progress" && element.progressStyle == "radial") "Ring width" else "Border width",
                { v -> edit { it.copy(strokeWidth = v.coerceAtLeast(0.0)) } },
                allowNegative = false,
            )
        }
        "line" -> {
            PanelHeading("Line")
            XpNumberField(element.strokeWidth, "Thickness", { v -> edit { it.copy(strokeWidth = v.coerceAtLeast(0.0)) } }, allowNegative = false)
        }
    }
    if (type == "rectangle" || type == "progress") {
        XpNumberField(element.cornerRadius, "Corner radius", { v -> edit { it.copy(cornerRadius = v.coerceAtLeast(0.0)) } }, allowNegative = false)
    }

    PanelHeading("Shadow")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpNumberField(element.shadowBlur, "Blur", { v -> edit { it.copy(shadowBlur = v.coerceAtLeast(0.0)) } }, Modifier.weight(1f), allowNegative = false)
        XpNumberField(element.shadowX, "Offset X", { v -> edit { it.copy(shadowX = v) } }, Modifier.weight(1f))
        XpNumberField(element.shadowY, "Offset Y", { v -> edit { it.copy(shadowY = v) } }, Modifier.weight(1f))
    }

    PanelHeading("Transform")
    XpUndoableSlider(
        label = "Rotation",
        value = element.rotation.toFloat().coerceIn(-180f, 180f),
        valueRange = -180f..180f,
        valueLabel = "${Math.round(element.rotation)}°",
        onBegin = viewModel::beginTemplateEdit,
        onChange = { v -> viewModel.stageCustomElement(id) { it.copy(rotation = Math.round(v).toDouble()) } },
    )
    XpUndoableSlider(
        label = "Opacity",
        value = element.opacity.toFloat().coerceIn(0f, 1f),
        valueRange = 0f..1f,
        steps = 19,
        valueLabel = "${Math.round(element.opacity * 100)}%",
        onBegin = viewModel::beginTemplateEdit,
        onChange = { v -> viewModel.stageCustomElement(id) { it.copy(opacity = Math.round(v * 20) / 20.0) } },
    )

    PanelHeading("Align to card")
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        listOf(
            "left" to Icons.AutoMirrored.Filled.AlignHorizontalLeft,
            "center" to Icons.Default.AlignHorizontalCenter,
            "right" to Icons.AutoMirrored.Filled.AlignHorizontalRight,
            "top" to Icons.Default.AlignVerticalTop,
            "middle" to Icons.Default.AlignVerticalCenter,
            "bottom" to Icons.Default.AlignVerticalBottom,
        ).forEach { (edge, icon) ->
            IconButton(onClick = { viewModel.alignCustomElement(id, edge) }) {
                Icon(icon, contentDescription = "Align $edge", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { viewModel.duplicateCustomElement(id) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Duplicate", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Text("Delete", modifier = Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete layer?",
            message = "\"${element.label.ifBlank { "This layer" }}\" will be removed from the card.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.removeCustomElement(id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * The Tools tab: presets, JSON backup, canvas size, background URL, grid
 * and snap settings, view reset, discard, and reset to the bot defaults.
 */
@Composable
fun XpToolsPanel(state: XpState, viewModel: XpViewModel, backgroundFailed: Boolean) {
    val controller = viewModel.designer
    val clipboard = rememberTextClipboard()
    var pendingPreset by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDefaults by remember { mutableStateOf(false) }

    PanelHeading("Presets")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("minimal" to "Minimal", "glass" to "Glass", "gaming" to "Gaming").forEach { (id, label) ->
            OutlinedButton(onClick = { pendingPreset = id }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
            }
        }
    }

    PanelHeading("Backup")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { clipboard.copy(viewModel.exportTemplateJson()) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Copy JSON", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { showImport = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Import JSON", modifier = Modifier.padding(start = 6.dp))
        }
    }

    PanelHeading("Canvas size")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        XpIntField(state.template.outputSizeX, "Width", { w ->
            viewModel.setCanvasSize(w, state.template.outputSizeY)
        }, Modifier.weight(1f), allowNegative = false)
        XpIntField(state.template.outputSizeY, "Height", { h ->
            viewModel.setCanvasSize(state.template.outputSizeX, h)
        }, Modifier.weight(1f), allowNegative = false)
    }
    PanelNote("The bot renders at the background image's size.")

    PanelHeading("Background")
    XpCommitField(
        value = state.settings.customXpImageUrl,
        label = "Background image URL",
        onCommit = viewModel::setBackgroundUrl,
        isError = backgroundFailed,
        supportingText = if (backgroundFailed) {
            "That image could not be loaded."
        } else {
            "Leave empty to use the bot's default background."
        },
    )

    PanelHeading("Grid")
    XpUndoableSlider(
        label = "Grid size",
        value = controller.gridSize.toFloat(),
        valueRange = 5f..50f,
        steps = 8,
        valueLabel = "${controller.gridSize} px",
        onBegin = {},
        onChange = { controller.gridSize = (Math.round(it / 5f) * 5).coerceIn(5, 50) },
    )
    SwitchRow(title = "Show grid", checked = controller.showGrid, onCheckedChange = { controller.showGrid = it })
    SwitchRow(
        title = "Snap to grid",
        subtitle = "Positions round to the grid; otherwise to whole pixels",
        checked = controller.snapToGrid,
        onCheckedChange = { controller.snapToGrid = it },
    )
    SwitchRow(title = "Show rulers", checked = controller.showRulers, onCheckedChange = { controller.showRulers = it })
    SwitchRow(
        title = "Lock proportions",
        checked = controller.lockProportions,
        onCheckedChange = { controller.lockProportions = it },
    )

    PanelHeading("Reset")
    OutlinedButton(
        onClick = { controller.resetView(state.template.outputSizeX, state.template.outputSizeY) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Reset view") }
    OutlinedButton(
        onClick = { confirmDiscard = true },
        enabled = state.hasUnsavedTemplate,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Discard changes") }
    OutlinedButton(onClick = { confirmDefaults = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Reset to bot defaults")
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
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard card changes?",
            message = "Every unsaved change to the card, its layers, and its background is lost.",
            confirmLabel = "Discard",
            onConfirm = viewModel::resetTemplateChanges,
            onDismiss = { confirmDiscard = false },
        )
    }
    if (confirmDefaults) {
        ConfirmDialog(
            title = "Reset to bot defaults?",
            message = "Every built-in element returns to the bot's default placement and all custom layers are removed. You can undo this.",
            confirmLabel = "Reset",
            onConfirm = viewModel::resetToBotDefaults,
            onDismiss = { confirmDefaults = false },
        )
    }
    if (showImport) {
        XpImportDialog(viewModel = viewModel, onDismiss = { showImport = false })
    }
}

/** Pastes an exported card (the dashboard's object or a bare layer array) over the custom layers. */
@Composable
fun XpImportDialog(viewModel: XpViewModel, onDismiss: () -> Unit) {
    val clipboard = rememberTextClipboard()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import layers") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste a template exported from the dashboard's card editor, or from another guild.",
                    style = MaterialTheme.typography.bodySmall,
                )
                MewdekoTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    label = "JSON",
                    singleLine = false,
                    minLines = 4,
                    isError = error,
                    supportingText = if (error) "That wasn't a card export or a layer array." else null,
                )
                TextButton(onClick = { clipboard.paste { text = it } }) {
                    Text("Paste from clipboard")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (viewModel.importTemplateJson(text)) onDismiss() else error = true }) {
                Text("Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
