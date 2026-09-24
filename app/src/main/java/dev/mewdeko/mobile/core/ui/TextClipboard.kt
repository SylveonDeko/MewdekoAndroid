package dev.mewdeko.mobile.core.ui

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Plain text copy and paste over the suspending Compose [Clipboard], so
 * click handlers can stay synchronous. Work starts undispatched, so a copy
 * made right before a dialog dismisses itself still lands.
 */
class TextClipboard(private val clipboard: Clipboard, private val scope: CoroutineScope) {
    /** Places [text] on the system clipboard. */
    fun copy(text: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Mewdeko", text)))
        }
    }

    /** Reads the clipboard's first text item and passes it to [onText] when there is one. */
    fun paste(onText: (String) -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val clip = clipboard.getClipEntry()?.clipData ?: return@launch
            if (clip.itemCount == 0) return@launch
            clip.getItemAt(0).text?.toString()?.let(onText)
        }
    }
}

/** Remembers a [TextClipboard] bound to the current composition's clipboard and scope. */
@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { TextClipboard(clipboard, scope) }
}
