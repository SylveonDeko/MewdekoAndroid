package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A column whose rows reorder by pressing and holding, then dragging. The lifted row follows the finger and
 * swaps with a neighbor once it passes that neighbor's middle; letting go reports the move.
 *
 * @param items The rows in their current order.
 * @param key A stable key per row.
 * @param onMove Called once on release with the row's old and new index, when it moved.
 * @param spacing Space between rows.
 * @param itemContent Draws a row. Apply the given modifier to whatever starts the drag, such as the whole row
 *     or a grip handle; the flag is true while that row is lifted.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    itemContent: @Composable (item: T, dragModifier: Modifier, dragging: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var order by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var startIndex by remember { mutableStateOf(-1) }
    val currentItems by rememberUpdatedState(items)
    val currentOnMove by rememberUpdatedState(onMove)

    LaunchedEffect(items) {
        if (draggingKey == null) order = items
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val dragging = draggingKey == itemKey
                val dragModifier = Modifier.pointerInput(itemKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            draggingKey = itemKey
                            startIndex = currentItems.indexOfFirst { key(it) == itemKey }
                            offset = 0f
                        },
                        onDragEnd = {
                            val to = order.indexOfFirst { key(it) == itemKey }
                            val from = startIndex
                            draggingKey = null
                            offset = 0f
                            if (from >= 0 && to >= 0 && from != to) currentOnMove(from, to)
                        },
                        onDragCancel = {
                            draggingKey = null
                            offset = 0f
                            order = currentItems
                        },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val index = order.indexOfFirst { key(it) == itemKey }
                        if (index < 0) return@detectDragGesturesAfterLongPress
                        val next = order.getOrNull(index + 1)
                        val previous = order.getOrNull(index - 1)
                        val nextHeight = next?.let { heights[key(it)] }?.toFloat()
                        val previousHeight = previous?.let { heights[key(it)] }?.toFloat()
                        if (next != null && nextHeight != null && offset > nextHeight / 2 + spacingPx / 2) {
                            order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                            offset -= nextHeight + spacingPx
                        } else if (previous != null && previousHeight != null && offset < -(previousHeight / 2 + spacingPx / 2)) {
                            order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                            offset += previousHeight + spacingPx
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .onSizeChanged { heights[itemKey] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) offset else 0f
                            scaleX = if (dragging) 1.02f else 1f
                            scaleY = if (dragging) 1.02f else 1f
                            shadowElevation = if (dragging) 12f else 0f
                        },
                ) {
                    itemContent(item, dragModifier, dragging)
                }
            }
        }
    }
}
