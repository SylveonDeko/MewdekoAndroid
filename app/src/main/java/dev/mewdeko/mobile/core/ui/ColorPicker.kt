package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import kotlin.math.roundToInt

/**
 * Discord's brand colors plus the classic embed colors, the default quick
 * picks under a [ColorPickerField].
 */
val EmbedColorPresets: List<String> = listOf(
    "#5865F2",
    "#57F287",
    "#FEE75C",
    "#EB459E",
    "#ED4245",
    "#9B59B6",
    "#1ABC9C",
    "#E67E22",
    "#3498DB",
    "#95A5A6",
)

/**
 * Formats [color] as uppercase `#RRGGBB`, or `#AARRGGBB` with
 * [includeAlpha] (alpha first, the order Android and the bot's SkiaSharp
 * read).
 */
fun colorToHex(color: Color, includeAlpha: Boolean = false): String {
    fun channel(value: Float) = (value * 255).roundToInt().coerceIn(0, 255)
        .toString(16).padStart(2, '0')
    val rgb = channel(color.red) + channel(color.green) + channel(color.blue)
    return "#" + (if (includeAlpha) channel(color.alpha) + rgb else rgb).uppercase()
}

/**
 * Parses `#RGB`, `#RRGGBB`, or `#AARRGGBB` (the `#` is optional). Returns
 * null for anything else, so a bad value is never mistaken for black.
 */
fun hexToColorOrNull(hex: String): Color? {
    val digits = hex.trim().removePrefix("#")
    if (digits.isEmpty() || !digits.all { it.isHexDigit() }) return null
    val expanded = when (digits.length) {
        3 -> "FF" + digits.map { "$it$it" }.joinToString("")
        6 -> "FF$digits"
        8 -> digits
        else -> return null
    }
    val argb = expanded.toLong(16)
    return Color(argb.toInt())
}

/** Whether [this] is `0-9`, `a-f`, or `A-F`. */
private fun Char.isHexDigit(): Boolean = isDigit() || lowercaseChar() in 'a'..'f'

/**
 * A color setting: a live swatch, a hex field, and a row of quick picks.
 *
 * Use it for every color the user sets (embed colors, role colors, card
 * colors). Tapping the swatch, or the palette button at the end of the
 * quick picks, opens [ColorPickerSheet] for free choice by hue, saturation
 * and brightness.
 *
 * [hex] is `#RRGGBB` (or `#AARRGGBB` with [allowAlpha]), or empty for no
 * color. The field accepts three or six hex digits, or eight with
 * [allowAlpha], with or without `#`. A full-length value applies while
 * typing; a short form applies when the user presses Done or leaves the
 * field. [onHexChange] only ever receives valid, uppercase values, or an
 * empty string when [allowClear] lets the user clear the color. Pass an
 * empty [presets] list to hide the quick picks.
 */
@Composable
fun ColorPickerField(
    label: String,
    hex: String,
    onHexChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<String> = EmbedColorPresets,
    allowClear: Boolean = true,
    allowAlpha: Boolean = false,
) {
    val focus = LocalFocusManager.current
    val current = hexToColorOrNull(hex)
    val maxDigits = if (allowAlpha) 8 else 6
    var picking by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(hex.trim().removePrefix("#").uppercase()) }

    LaunchedEffect(hex) {
        if (hexToColorOrNull(draft) != current) draft = hex.trim().removePrefix("#").uppercase()
    }

    fun acceptedLength(length: Int) = length == 3 || length == 6 || (allowAlpha && length == 8)

    fun commit(final: Boolean) {
        val digits = draft
        if (digits.isEmpty()) {
            if (final && allowClear && hex.isNotBlank()) onHexChange("")
            return
        }
        val ready = digits.length == maxDigits || (final && acceptedLength(digits.length))
        val parsed = hexToColorOrNull(digits) ?: return
        if (ready && parsed != current) onHexChange(colorToHex(parsed, includeAlpha = digits.length == 8))
    }

    val draftValid = draft.isEmpty() || (acceptedLength(draft.length) && hexToColorOrNull(draft) != null)
    val showError = !draftValid && !focused

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ColorSwatch(
                color = current,
                size = 44.dp,
                onClick = { picking = true },
                description = "$label, ${current?.let { colorToHex(it, allowAlpha && it.alpha < 1f) } ?: "none"}. Open color picker",
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { raw ->
                    draft = raw.removePrefix("#")
                        .filter { it.isHexDigit() }
                        .uppercase()
                        .take(maxDigits)
                    commit(final = false)
                },
                label = { Text(label) },
                prefix = { Text("#") },
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    {
                        Text(
                            if (allowAlpha) "Use 6 or 8 hex digits, like 5865F2"
                            else "Use 6 hex digits, like 5865F2",
                        )
                    }
                } else {
                    null
                },
                trailingIcon = if (allowClear && draft.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            draft = ""
                            if (hex.isNotBlank()) onHexChange("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear color")
                        }
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    commit(final = true)
                    focus.clearFocus()
                }),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) commit(final = true)
                        focused = state.isFocused
                    },
            )
        }

        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presets.forEach { preset ->
                    val color = hexToColorOrNull(preset) ?: return@forEach
                    PresetDot(
                        color = color,
                        label = preset.uppercase(),
                        selected = current != null && colorToHex(current) == colorToHex(color),
                        onClick = { onHexChange(colorToHex(color, includeAlpha = false)) },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Open color picker", role = Role.Button) { picking = true }
                        .padding(4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex40), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "More colors",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (picking) {
        ColorPickerSheet(
            initial = current ?: LocalGuildPalette.current.primary.color,
            allowAlpha = allowAlpha,
            presets = presets.ifEmpty { EmbedColorPresets },
            onApply = { color -> onHexChange(colorToHex(color, includeAlpha = allowAlpha && color.alpha < 1f)) },
            onDismiss = { picking = false },
        )
    }
}

/**
 * A free color chooser in a [MewdekoBottomSheet].
 *
 * A saturation and brightness square, a hue bar, and (with [allowAlpha]) an
 * opacity bar, all draggable and tappable. The current and new colors sit
 * side by side (tap the current one to go back to it), a hex field stays in
 * step with the drag, and two rows of quick picks offer the server's own
 * colors and [presets]. The choice reaches [onApply] only when the user
 * taps Apply; Cancel, back, or a scrim tap leave the value as it was.
 *
 * Most screens want [ColorPickerField], which opens this for them.
 */
@Composable
fun ColorPickerSheet(
    initial: Color,
    allowAlpha: Boolean,
    onApply: (Color) -> Unit,
    onDismiss: () -> Unit,
    presets: List<String> = EmbedColorPresets,
) {
    val start = remember { hsvOf(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    var alpha by remember { mutableFloatStateOf(if (allowAlpha) initial.alpha else 1f) }
    val color = Color.hsv(hue, saturation, brightness, alpha)

    val setColor: (Color) -> Unit = { next ->
        val hsv = hsvOf(next)
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) saturation = hsv[1]
        brightness = hsv[2]
        if (allowAlpha) alpha = next.alpha
    }

    val palette = LocalGuildPalette.current
    val serverColors = remember(palette) {
        listOf(
            palette.primary.color,
            palette.secondary.color,
            palette.accent.color,
            palette.gradientStart.color,
            palette.gradientMid.color,
            palette.gradientEnd.color,
        ).distinctBy { colorToHex(it) }
    }
    val presetColors = remember(presets) {
        (presets.mapNotNull { hexToColorOrNull(it) } + Color.White + Color.Black).distinctBy { colorToHex(it) }
    }

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "Pick a color", showClose = false) {
        val dismiss = LocalSheetDismiss.current
        Column(modifier = Modifier.imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { s, v ->
                        saturation = s
                        brightness = v
                    },
                )
                HueBar(hue = hue, onHueChange = { hue = it })
                if (allowAlpha) {
                    AlphaBar(color = color.copy(alpha = 1f), alpha = alpha, onAlphaChange = { alpha = it })
                }
                CompareSwatches(before = initial, after = color, onRevert = { setColor(initial) })
                SheetHexField(color = color, allowAlpha = allowAlpha, onColor = setColor)
                SwatchRow(title = "Server colors", colors = serverColors, current = color, onPick = setColor)
                SwatchRow(title = "Presets", colors = presetColors, current = color, onPick = setColor)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = dismiss) { Text("Cancel") }
                Button(onClick = {
                    onApply(color)
                    dismiss()
                }) { Text("Apply") }
            }
        }
    }
}

/** Hue in degrees, then saturation and value in 0 to 1, of [color]. */
private fun hsvOf(color: Color): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(color.copy(alpha = 1f).toArgb(), it) }

/** The saturation (across) and brightness (up) square for the current hue. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    val pure = Color.hsv(hue, 1f, 1f)
    val thumb = Color.hsv(hue, saturation, brightness)
    val latestChange by rememberUpdatedState(onChange)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = "Saturation and brightness"
                stateDescription = "Saturation ${(saturation * 100).roundToInt()} percent, " +
                    "brightness ${(brightness * 100).roundToInt()} percent"
            }
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    val s = (position.x / size.width).coerceIn(0f, 1f)
                    val v = 1f - (position.y / size.height).coerceIn(0f, 1f)
                    latestChange(s, v)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            }
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val center = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawThumb(center = center, radius = 10.dp.toPx(), fill = thumb)
            },
    )
}

/** The rainbow bar that sets the hue. */
@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    SliderBar(
        fraction = hue / 360f,
        onFraction = { onHueChange(it * 360f) },
        thumbColor = Color.hsv(hue, 1f, 1f),
        description = "Hue",
        valueText = "${hue.roundToInt()} degrees",
        brush = { Brush.horizontalGradient(HueStops) },
        checkered = false,
    )
}

/** The bar that sets opacity, drawn over a checkerboard. */
@Composable
private fun AlphaBar(color: Color, alpha: Float, onAlphaChange: (Float) -> Unit) {
    SliderBar(
        fraction = alpha,
        onFraction = onAlphaChange,
        thumbColor = color.copy(alpha = alpha),
        description = "Opacity",
        valueText = "${(alpha * 100).roundToInt()} percent",
        brush = { Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color)) },
        checkered = true,
    )
}

/** Hue stops every 60 degrees, red back round to red. */
private val HueStops: List<Color> = (0..6).map { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) }

/**
 * A horizontal gradient bar with a round thumb, used for hue and opacity.
 * Tapping jumps the thumb; dragging follows the finger. Accessibility
 * services can set it directly.
 */
@Composable
private fun SliderBar(
    fraction: Float,
    onFraction: (Float) -> Unit,
    thumbColor: Color,
    description: String,
    valueText: String,
    brush: () -> Brush,
    checkered: Boolean,
) {
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val dark = MaterialTheme.colorScheme.surfaceContainerLow
    val latestFraction by rememberUpdatedState(onFraction)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .semantics {
                contentDescription = description
                stateDescription = valueText
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    onFraction(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                fun update(position: Offset) {
                    val radius = size.height / 2f
                    val track = (size.width - radius * 2).coerceAtLeast(1f)
                    latestFraction(((position.x - radius) / track).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            }
            .then(if (checkered) Modifier.checkerboard(light, dark) else Modifier)
            .drawBehind {
                drawRect(brush())
                val radius = size.height / 2f
                val x = radius + (size.width - radius * 2) * fraction.coerceIn(0f, 1f)
                drawThumb(center = Offset(x, radius), radius = radius - 3.dp.toPx(), fill = thumbColor)
            },
    )
}

/** Draws a picker thumb: the color, a white ring, and a faint dark outer ring. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThumb(
    center: Offset,
    radius: Float,
    fill: Color,
) {
    drawCircle(color = fill, radius = radius, center = center)
    drawCircle(color = Color.White, radius = radius, center = center, style = Stroke(width = 3.dp.toPx()))
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = radius + 2.dp.toPx(),
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )
}

/** The starting color and the new one, side by side. Tapping the first restores it. */
@Composable
private fun CompareSwatches(before: Color, after: Color, onRevert: () -> Unit) {
    val light = MaterialTheme.colorScheme.surfaceContainerHighest
    val dark = MaterialTheme.colorScheme.surfaceContainerLow
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex30), RoundedCornerShape(10.dp)),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .checkerboard(light, dark)
                    .background(before)
                    .clickable(onClickLabel = "Go back to the current color", role = Role.Button, onClick = onRevert),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .checkerboard(light, dark)
                    .background(after),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Current",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "New",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The picker's hex field. It follows the drag while not focused, and while
 * the user types it applies each complete value.
 */
@Composable
private fun SheetHexField(color: Color, allowAlpha: Boolean, onColor: (Color) -> Unit) {
    val focus = LocalFocusManager.current
    val maxDigits = if (allowAlpha) 8 else 6
    var focused by remember { mutableStateOf(false) }
    val shown = colorToHex(color, includeAlpha = allowAlpha).removePrefix("#")
    var draft by remember { mutableStateOf(shown) }
    LaunchedEffect(shown, focused) { if (!focused) draft = shown }
    val valid = hexToColorOrNull(draft) != null &&
        (draft.length == 3 || draft.length == 6 || (allowAlpha && draft.length == 8))

    OutlinedTextField(
        value = draft,
        onValueChange = { raw ->
            draft = raw.removePrefix("#").filter { it.isHexDigit() }.uppercase().take(maxDigits)
            if (draft.length == 6 || draft.length == maxDigits) hexToColorOrNull(draft)?.let(onColor)
        },
        label = { Text("Hex") },
        prefix = { Text("#") },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        singleLine = true,
        isError = !valid && !focused,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            if (valid) hexToColorOrNull(draft)?.let(onColor)
            focus.clearFocus()
        }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused && !state.isFocused && valid) hexToColorOrNull(draft)?.let(onColor)
                focused = state.isFocused
            },
    )
}

/** A titled row of tappable color dots. */
@Composable
private fun SwatchRow(title: String, colors: List<Color>, current: Color, onPick: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val currentHex = colorToHex(current)
            colors.forEach { color ->
                val hex = colorToHex(color)
                PresetDot(
                    color = color,
                    label = hex,
                    selected = hex == currentHex,
                    onClick = { onPick(color) },
                )
            }
        }
    }
}

/** One quick-pick color: a 32dp dot in a 40dp target, ringed when selected. */
@Composable
private fun PresetDot(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "Color $label"
                this.selected = selected
            }
            .padding(if (selected) 5.dp else 4.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/**
 * A rounded color swatch over a checkerboard, so an empty or translucent
 * color still reads as a swatch.
 */
@Composable
private fun ColorSwatch(
    color: Color?,
    size: Dp,
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .checkerboard(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .background(color ?: Color.Transparent)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex40), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
}

/** Paints a two-tone checkerboard behind the content, the usual stand-in for transparency. */
private fun Modifier.checkerboard(light: Color, dark: Color, cell: Dp = 6.dp): Modifier = drawBehind {
    val step = cell.toPx()
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else step
        while (x < size.width) {
            drawRect(dark, topLeft = Offset(x, y), size = Size(step, step))
            x += step * 2
        }
        y += step
        row++
    }
}
