package dev.mewdeko.mobile.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.app.hostOrSelf
import dev.mewdeko.mobile.core.store.ServerConfig
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.clickableRow

/**
 * First-run and switch-server screen.
 *
 * Lists saved servers so the user can switch hosts without losing per-server
 * credentials, plus an inline form for adding new dashboards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    savedServers: List<ServerConfig>,
    errorMessage: String?,
    isProbing: Boolean,
    onAddServer: (label: String, url: String) -> Unit,
    onSelectServer: (String) -> Unit,
    onForgetServer: (String) -> Unit,
) {
    var url by remember { mutableStateOf(if (savedServers.isEmpty()) OfficialDashboard else "") }
    var label by remember { mutableStateOf("") }
    var pendingRemoval by remember { mutableStateOf<ServerConfig?>(null) }

    val canSubmit = url.isNotBlank() && !isProbing

    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose Server") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (savedServers.isNotEmpty()) {
                SectionCard(contentPadding = 8) {
                    SectionCardHeader(
                        title = "Saved servers",
                        icon = Icons.Default.Dns,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    savedServers.forEach { server ->
                        ListItem(
                            headlineContent = { Text(server.label) },
                            supportingContent = {
                                Text(
                                    text = server.baseUrl,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Icon(Icons.Default.Dns, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { pendingRemoval = server }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Forget ${server.label}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            modifier = Modifier.clickableRow { onSelectServer(server.id) },
                        )
                    }
                }
            }

            SectionCard {
                SectionCardHeader(
                    title = if (savedServers.isEmpty()) "Dashboard URL" else "Add another",
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
                MewdekoTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "URL",
                    placeholder = OfficialDashboard,
                )
                if (savedServers.isEmpty() && url != OfficialDashboard) {
                    TextButton(onClick = { url = OfficialDashboard }) {
                        Text("Use the official dashboard")
                    }
                }
                MewdekoTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = "Label (optional)",
                )
                Text(
                    text = "Defaults to the official dashboard. Change it only if you run your " +
                        "own deployment; the app reads that host's public mobile config from " +
                        "/api/mobile/config.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { onAddServer(label, url) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(2.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(if (savedServers.isEmpty()) "Continue" else "Add server")
                    }
                }
            }
        }
    }

    pendingRemoval?.let { server ->
        ConfirmDialog(
            title = "Forget this server?",
            message = "Tokens and the cached profile for " +
                "${server.baseUrl.hostOrSelf()} will be deleted from this device.",
            confirmLabel = "Forget",
            onConfirm = { onForgetServer(server.id) },
            onDismiss = { pendingRemoval = null },
        )
    }
}

/** The hosted dashboard, used unless a selfhoster points somewhere else. */
private const val OfficialDashboard = "https://mewdeko.tech"
