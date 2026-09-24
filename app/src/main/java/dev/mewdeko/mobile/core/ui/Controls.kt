package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.theme.DashAlpha

/**
 * A labelled switch row.
 *
 * Uses the Material 3 [ListItem] so padding and typography match every
 * other row on the screen. The row sits on the dashboard's row surface, the
 * primary at the `08` tint, which deepens to the `15` selected tint while
 * the switch is on.
 *
 * Passing [icon] leads the row with a small [GlyphOrb] in the primary.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { GlyphOrb(it, tint = primary) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(
            containerColor = primary.copy(alpha = if (checked && enabled) DashAlpha.Hex15 else DashAlpha.Hex08),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickableRow { if (enabled) onCheckedChange(!checked) },
    )
}

/** A read-only key/value row used to present server-side state. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Single-line text field with the app's standard styling. */
@Composable
fun MewdekoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    numeric: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}

/** A labelled slider for bounded numeric settings. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: String = value.toInt().toString(),
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            },
            track = { state ->
                val fraction = ((state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start))
                    .coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            },
        )
    }
}

/**
 * Confirmation dialog for destructive actions.
 *
 * A Material 3 [AlertDialog] with the confirm action tinted as an error so
 * deletions read as destructive.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Avatar backed by Coil, with a glyph or initial placeholder.
 *
 * Circular by default. A [ring] draws around the image, which is inset by the
 * ring's width so the two never overlap. When there is no image and
 * [fallbackText] is set, the first letter of it replaces the glyph.
 */
@Composable
fun Avatar(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Int = 40,
    fallbackIcon: ImageVector = Icons.Default.Person,
    shape: Shape = CircleShape,
    ring: BorderStroke? = null,
    fallbackText: String? = null,
    fallbackUrl: String? = null,
) {
    /**
     * URLs that failed to load, so a dead link (an expired attachment, for
     * example) steps down to [fallbackUrl] and then to the initial or icon
     * instead of leaving the avatar blank.
     */
    var failed by remember(url, fallbackUrl) { mutableStateOf(emptySet<String>()) }
    val resolvedUrl = listOfNotNull(url, fallbackUrl)
        .firstOrNull { it.isNotEmpty() && it !in failed }
    Box(
        modifier = modifier
            .size(size.dp)
            .then(
                if (ring != null) {
                    Modifier
                        .border(ring, shape)
                        .padding(ring.width)
                } else {
                    Modifier
                }
            )
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = fallbackText?.trim()?.firstOrNull()?.uppercaseChar()
        when {
            resolvedUrl != null -> AsyncImage(
                model = resolvedUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { failed = failed + resolvedUrl },
                modifier = Modifier.matchParentSize(),
            )

            initial != null -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        if (contentDescription != null) this.contentDescription = contentDescription
                    },
                )
            }

            else -> Icon(
                fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.6).dp),
            )
        }
    }
}

/**
 * Small labelled chip used for tags and enum values.
 *
 * When [onClick] is null this renders as a non-interactive label instead of
 * an [AssistChip], so it never grows to Material's 48dp interactive touch
 * target. That keeps rows of metadata labels from squeezing a trailing
 * action column when several are shown at once.
 */
@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    maxLines: Int = if (onClick == null) 1 else Int.MAX_VALUE,
) {
    if (onClick != null) {
        AssistChip(
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = icon?.let {
                { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
            },
            modifier = modifier,
        )
    } else {
        val primary = MaterialTheme.colorScheme.primary
        Surface(
            modifier = modifier.heightIn(min = 32.dp),
            shape = RoundedCornerShape(8.dp),
            color = primary.copy(alpha = DashAlpha.Hex10),
            border = BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                icon?.let { Icon(it, contentDescription = null, tint = primary, modifier = Modifier.size(14.dp)) }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
