package dev.mewdeko.mobile.core.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Closes the enclosing [MewdekoBottomSheet] with its slide-down animation,
 * then calls the sheet's `onDismissRequest`.
 *
 * Sheet content should call this for its own Done, Cancel, or pick-and-close
 * actions instead of flipping the caller's visibility flag directly, which
 * would drop the sheet without animating. Outside a sheet it does nothing.
 */
val LocalSheetDismiss = staticCompositionLocalOf<() -> Unit> { {} }

/** How far, in dp, the handle must be dragged before a release closes the sheet. */
private const val DismissDistanceDp = 56

/** Downward fling speed, in px per second, that closes the sheet regardless of distance. */
private const val DismissVelocity = 1200f

/**
 * The app's modal bottom sheet. Use it for every sheet instead of calling
 * Material's `ModalBottomSheet` directly.
 *
 * Material's sheet treats any downward drag anywhere on its surface, and any
 * leftover scroll from a list inside it, as a request to close. A user who
 * scrolls a grid or list back to the top and keeps going, or who drags in
 * the gap between two tiles, loses the whole sheet. This sheet turns those
 * surface gestures off: only a drag that starts on the handle at the top can
 * pull it down, and it closes once released past [DismissDistanceDp] or
 * flung faster than [DismissVelocity]. A tap on the scrim, system back, and
 * the header close button still dismiss it.
 *
 * The sheet always opens fully expanded. [title] renders a header row with
 * the title and, when [showClose] is set, a close button; with no title and
 * no close button the content starts right under the handle. Content that
 * needs to close the sheet itself reads [LocalSheetDismiss].
 *
 * Scrolling content should take `Modifier.weight(1f, fill = false)` so it
 * shrinks to the space left under the header instead of pushing footer
 * actions off screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MewdekoBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val dragOffset = remember { mutableFloatStateOf(0f) }
    val dismiss: () -> Unit = remember(sheetState, scope) {
        {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
                if (!sheetState.isVisible) latestDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = { SheetDragHandle(offset = dragOffset, onDismiss = dismiss) },
        modifier = modifier.graphicsLayer { translationY = dragOffset.floatValue },
    ) {
        CompositionLocalProvider(LocalSheetDismiss provides dismiss) {
            if (title != null || showClose) {
                SheetHeader(title = title, showClose = showClose, onClose = dismiss)
            }
            content()
        }
    }
}

/**
 * The only part of a [MewdekoBottomSheet] that drags it.
 *
 * A 48dp tall target around Material's handle pill. The drag offset is
 * applied to the whole sheet through a graphics layer so the sheet follows
 * the finger, and a release either closes the sheet or springs it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetDragHandle(offset: MutableFloatState, onDismiss: () -> Unit) {
    val threshold = with(LocalDensity.current) { DismissDistanceDp.dp.toPx() }
    val draggableState = rememberDraggableState { delta ->
        offset.floatValue = (offset.floatValue + delta).coerceAtLeast(0f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (offset.floatValue > threshold || velocity > DismissVelocity) {
                        onDismiss()
                    } else {
                        animate(initialValue = offset.floatValue, targetValue = 0f) { value, _ ->
                            offset.floatValue = value
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BottomSheetDefaults.DragHandle()
    }
}

/** Title and close button across the top of a [MewdekoBottomSheet]. */
@Composable
private fun SheetHeader(title: String?, showClose: Boolean, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        if (showClose) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
