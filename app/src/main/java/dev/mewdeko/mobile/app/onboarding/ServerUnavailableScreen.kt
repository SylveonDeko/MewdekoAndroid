package dev.mewdeko.mobile.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.app.hostOrSelf
import dev.mewdeko.mobile.core.store.ServerConfig
import dev.mewdeko.mobile.core.ui.CanvasStyle
import dev.mewdeko.mobile.core.ui.LuminousButton
import dev.mewdeko.mobile.core.ui.LuminousButtonVariant
import dev.mewdeko.mobile.core.ui.ShellConfirmDialog
import dev.mewdeko.mobile.core.ui.ShellDimens
import dev.mewdeko.mobile.core.ui.ShellHero
import dev.mewdeko.mobile.core.ui.ShellScreen
import dev.mewdeko.mobile.core.ui.ShellTextAction
import dev.mewdeko.mobile.core.ui.ShellTone
import dev.mewdeko.mobile.core.ui.ShellType
import dev.mewdeko.mobile.core.ui.SurfaceLevel
import dev.mewdeko.mobile.core.ui.glassSurface
import dev.mewdeko.mobile.core.ui.rememberShellRoles

/**
 * Shown when a saved dashboard is configured but its mobile config cannot
 * be reached during launch, matching the iOS offline screen.
 *
 * Drawn on the alert canvas, lit by the semantic red, with a pulsing offline
 * orb, the address in a selectable chip, the reason, and a floating dock to
 * retry or pick another dashboard. A quiet action under the reason forgets
 * the dashboard after confirming.
 */
@Composable
fun ServerUnavailableScreen(
    server: ServerConfig,
    reason: String,
    onRetry: () -> Unit,
    onChooseServer: () -> Unit,
    onForget: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    var isRetrying by remember { mutableStateOf(false) }
    var confirmingForget by remember { mutableStateOf(false) }

    ShellScreen(
        title = "Offline",
        canvas = CanvasStyle.Alert,
        dock = {
            LuminousButton(
                text = if (isRetrying) "Retrying" else "Retry",
                onClick = {
                    isRetrying = true
                    onRetry()
                },
                enabled = !isRetrying,
                icon = Icons.Default.Refresh,
                spinIcon = isRetrying,
                fullWidth = true,
            )
            LuminousButton(
                text = "Use a different dashboard",
                onClick = onChooseServer,
                variant = LuminousButtonVariant.Glass,
                fullWidth = true,
            )
        },
    ) {
        ShellHero(
            icon = Icons.Default.WifiOff,
            overline = "Offline",
            title = "Can't reach your dashboard",
            tone = ShellTone.Negative,
            pulsing = true,
        )

        Column(verticalArrangement = Arrangement.spacedBy(ShellDimens.s)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassSurface(
                        SurfaceLevel.Card,
                        tint = roles.negative,
                        shape = RoundedCornerShape(ShellDimens.controlRadius),
                    )
                    .padding(horizontal = ShellDimens.m, vertical = ShellDimens.s)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Dashboard address, ${server.baseUrl}"
                    },
                horizontalArrangement = Arrangement.spacedBy(ShellDimens.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.baseUrl,
                        style = ShellType.caption.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = reason,
                    style = ShellType.meta,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        ShellTextAction(
            text = "Forget this dashboard",
            onClick = { confirmingForget = true },
            tone = ShellTone.Negative,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (confirmingForget) {
        ShellConfirmDialog(
            title = "Forget this dashboard?",
            message = "Tokens and the cached profile for " +
                "${server.baseUrl.hostOrSelf()} will be deleted from this device.",
            confirmLabel = "Forget ${server.label}",
            onConfirm = onForget,
            onDismiss = { confirmingForget = false },
        )
    }
}
