package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * The one way to start creating something on a list section.
 *
 * Every "new" action in the app follows the same rules, so a user who has
 * added one thing knows how to add anything:
 *
 * Placement. Pass this as the [FeatureScaffold] `floatingActionButton` on
 * sections that list items (triggers, giveaways, stat channels, polls). Do
 * not add a top bar plus button or a "Create" tab as well. A section shows
 * either this or its Save button, never both. The section's empty state
 * offers the same action through the same `onClick`.
 *
 * Collapsing. On lazy lists pass `expanded = listState.firstVisibleItemIndex == 0`
 * so the button shrinks to its icon once the user scrolls into the list and
 * stops covering rows.
 *
 * Presentation. What the button opens depends on the form:
 * - A short form, five inputs or fewer with no preview, no date or time
 *   picker, and no nested list editing, opens a [FormSheet].
 * - Anything longer opens a [FullScreenEditor].
 * Never open an `AlertDialog` form.
 *
 * [label] names the thing, such as "New trigger", and doubles as the
 * accessibility label when collapsed.
 */
@Composable
fun NewItemFab(
    label: String,
    onClick: () -> Unit,
    expanded: Boolean = true,
) {
    ExtendedFloatingActionButton(
        text = { Text(label, maxLines = 1) },
        icon = { Icon(Icons.Default.Add, contentDescription = if (expanded) null else label) },
        expanded = expanded,
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/**
 * A short create or edit form in a [MewdekoBottomSheet].
 *
 * Use it for forms of five inputs or fewer with no preview, no date or time
 * picker, and no nested list editing; anything larger belongs in a
 * [FullScreenEditor]. The inputs scroll under the title while the confirm
 * button stays pinned at the bottom and rides above the keyboard.
 *
 * [onConfirm] runs the action; the caller closes the sheet (usually by
 * clearing the state that shows it) once the action succeeds, so a failure
 * can keep the user's input in place. Dismissing by the close button, a
 * scrim tap, or back calls [onDismiss] and drops the input.
 */
@Composable
fun FormSheet(
    title: String,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    MewdekoBottomSheet(onDismissRequest = onDismiss, title = title) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A long create or edit form, full screen over the current page.
 *
 * Use it when a form has more than five inputs, a live preview, a date or
 * time picker, or a list the user edits inside it; shorter forms use a
 * [FormSheet]. The top bar has a close button, the [title], and the
 * [confirmLabel] action; the body scrolls and stays clear of the keyboard.
 *
 * Pass [hasUnsavedChanges] as true once the user has edited anything, and
 * closing (the X or system back) asks before discarding. [onConfirm] runs
 * the action; the caller closes the editor once it succeeds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenEditor(
    title: String,
    onClose: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    hasUnsavedChanges: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var confirmingDiscard by remember { mutableStateOf(false) }
    val requestClose = {
        if (hasUnsavedChanges) confirmingDiscard = true else onClose()
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        MatchDialogBarsToTheme()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = requestClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                            Text(confirmLabel, maxLines = 1)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }

        if (confirmingDiscard) {
            ConfirmDialog(
                title = "Discard changes?",
                message = "You have edits that have not been saved. Closing now throws them away.",
                confirmLabel = "Discard",
                onConfirm = onClose,
                onDismiss = { confirmingDiscard = false },
            )
        }
    }
}

/**
 * Sets the dialog window's status and navigation bar icons to suit the
 * theme, since an edge to edge dialog window does not inherit the
 * activity's setting.
 */
@Composable
private fun MatchDialogBarsToTheme() {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }
}
