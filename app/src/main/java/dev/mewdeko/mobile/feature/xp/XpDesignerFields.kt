package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.mewdeko.mobile.core.ui.ColorPickerField
import dev.mewdeko.mobile.core.ui.SliderRow

/**
 * A text field that edits a local draft and hands it to [onCommit] only
 * when editing ends (the keyboard's Done action or focus leaving), so a
 * committed value is one undo entry rather than one per keystroke. The
 * draft resets whenever [value] changes from outside, such as a drag or an
 * undo.
 */
@Composable
fun XpCommitField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
    isError: Boolean = false,
    filter: (String) -> String = { it },
) {
    val focus = LocalFocusManager.current
    var draft by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val commit = {
        if (draft != value) {
            onCommit(draft)
            draft = value
        }
    }
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = filter(it) },
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused && !state.isFocused) commit()
                focused = state.isFocused
            },
    )
}

/** An integer [XpCommitField]; unparsable drafts are dropped. */
@Composable
fun XpIntField(
    value: Int,
    label: String,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
) {
    XpCommitField(
        value = value.toString(),
        label = label,
        onCommit = { text -> text.toIntOrNull()?.let(onCommit) },
        modifier = modifier,
        numeric = true,
        filter = { raw ->
            raw.filterIndexed { index, c -> c.isDigit() || (allowNegative && c == '-' && index == 0) }
        },
    )
}

/** A decimal [XpCommitField] for the custom element columns, shown without a trailing `.0`. */
@Composable
fun XpNumberField(
    value: Double,
    label: String,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = true,
) {
    val shown = if (value == Math.floor(value) && !value.isInfinite()) value.toLong().toString() else value.toString()
    XpCommitField(
        value = shown,
        label = label,
        onCommit = { text -> text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let(onCommit) },
        modifier = modifier,
        numeric = true,
        filter = { raw ->
            raw.filterIndexed { index, c -> c.isDigit() || c == '.' || (allowNegative && c == '-' && index == 0) }
        },
    )
}

/**
 * A slider whose whole drag is one undo entry: [onBegin] runs on the
 * first change of a drag (push the entry there), then every change is
 * staged live through [onChange].
 */
@Composable
fun XpUndoableSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onBegin: () -> Unit,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    var dragging by remember { mutableStateOf(false) }
    SliderRow(
        label = label,
        value = value,
        onValueChange = {
            if (!dragging) {
                dragging = true
                onBegin()
            }
            onChange(it)
        },
        valueRange = valueRange,
        steps = steps,
        valueLabel = valueLabel,
        onValueChangeFinished = { dragging = false },
    )
}

/** Which of the two colour conventions a [XpColorField] reads and writes. */
enum class XpColorFormat {
    /** Built-in columns: uppercase `AARRGGBB` without `#`, parsed by `SKColor.Parse`. */
    ARGB,

    /** Custom layer colours: `#RRGGBB`, with translucency from the layer's opacity. */
    CSS,
}

/**
 * A colour row for the card designer: [ColorPickerField] with the XP
 * storage conventions. [value] and the committed string are in [format],
 * uppercase `AARRGGBB` (or `RRGGBB`) without `#` for [XpColorFormat.ARGB]
 * and `#RRGGBB` for [XpColorFormat.CSS]. Only well-formed hex commits, so
 * the bot can always parse what is saved. [allowBlank] lets the colour be
 * cleared (a solid gradient end). The quick-pick row is hidden to keep the
 * designer panels compact; the picker sheet still offers the server and
 * Discord colours.
 */
@Composable
fun XpColorField(
    label: String,
    value: String,
    format: XpColorFormat,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowAlpha: Boolean = format == XpColorFormat.ARGB,
    allowBlank: Boolean = false,
) {
    val trimmed = value.trim().removePrefix("#")
    ColorPickerField(
        label = label,
        hex = if (trimmed.isEmpty()) "" else "#$trimmed",
        onHexChange = { hex ->
            when {
                hex.isEmpty() -> if (allowBlank) onCommit("")
                format == XpColorFormat.ARGB -> onCommit(hex.removePrefix("#").uppercase())
                else -> onCommit(hex.uppercase())
            }
        },
        modifier = modifier,
        presets = emptyList(),
        allowClear = allowBlank,
        allowAlpha = allowAlpha,
    )
}
