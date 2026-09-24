package dev.mewdeko.mobile.feature.embed

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.LocalSheetDismiss
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SwitchRow

/**
 * Creates, edits, and deletes "send as" personas.
 *
 * Opened from the Send tab's webhook options, mirroring the dashboard's
 * PersonaManager modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaManagerSheet(
    onDismiss: () -> Unit,
    viewModel: EmbedLibraryViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EmbedPersona?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<EmbedPersona?>(null) }

    MewdekoBottomSheet(onDismissRequest = onDismiss, showClose = false) {
        val dismissSheet = LocalSheetDismiss.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (creating || editing != null) {
                PersonaForm(
                    existing = editing,
                    onBack = { creating = false; editing = null },
                    onSave = { name, avatarUrl, avatarData, clearAvatar, shared ->
                        val target = editing
                        if (target == null) {
                            viewModel.createPersona(name, avatarUrl, avatarData, shared)
                        } else {
                            viewModel.updatePersona(target, name, avatarUrl, avatarData, clearAvatar)
                        }
                        creating = false
                        editing = null
                    },
                )
                return@Column
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Personas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = dismissSheet) { Text("Done") }
            }
            Text(
                "Post under a custom name and avatar instead of the bot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("New persona", modifier = Modifier.padding(start = 8.dp))
            }

            if (library.personas.isEmpty()) {
                EmptyState(message = "No personas yet.", icon = Icons.Default.Person)
            }

            library.personas.forEach { persona ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(url = persona.avatarUrl, contentDescription = persona.name, size = 36)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(persona.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (persona.isGuildShared) "Shared with this server" else "Personal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = persona }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit ${persona.name}")
                    }
                    IconButton(onClick = { pendingDelete = persona }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${persona.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete persona?",
            message = "\"${target.name}\" will no longer be available to send as.",
            onConfirm = { pendingDelete = null; viewModel.deletePersona(target) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** The create/edit form for one persona. */
@Composable
private fun PersonaForm(
    existing: EmbedPersona?,
    onBack: () -> Unit,
    onSave: (name: String, avatarUrl: String?, avatarData: String?, clearAvatar: Boolean, shared: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var avatarUrl by remember { mutableStateOf(existing?.avatarUrl.orEmpty()) }
    var avatarData by remember { mutableStateOf<String?>(null) }
    var clearAvatar by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(existing?.isGuildShared ?: false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(uri) ?: "image/png"
        avatarData = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        clearAvatar = false
    }

    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            if (existing == null) "New persona" else "Edit persona",
            style = MaterialTheme.typography.titleMedium,
        )
    }

    val previewUrl = when {
        clearAvatar -> null
        avatarData != null -> avatarData
        avatarUrl.isNotBlank() -> avatarUrl
        else -> existing?.avatarUrl
    }
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(url = previewUrl, contentDescription = "Avatar preview", size = 56)
        Column {
            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Upload image", modifier = Modifier.padding(start = 6.dp))
            }
            if (existing?.hasUploadedAvatar == true || existing?.avatarUrl?.isNotBlank() == true || avatarData != null) {
                TextButton(onClick = { avatarData = null; avatarUrl = ""; clearAvatar = true }) {
                    Text("Clear avatar")
                }
            }
        }
    }

    MewdekoTextField(
        value = name,
        onValueChange = { name = it },
        label = "Name",
        placeholder = "Announcement Bot",
        supportingText = "${name.length}/80",
        isError = name.length > 80,
    )

    MewdekoTextField(
        value = avatarUrl,
        onValueChange = { avatarUrl = it; avatarData = null; clearAvatar = false },
        label = "Avatar URL",
        placeholder = "Optional, links only",
        enabled = avatarData == null,
    )

    SwitchRow(
        title = "Share with this server",
        subtitle = "Anyone with dashboard access can send as this persona",
        checked = shared,
        onCheckedChange = { shared = it },
        enabled = existing == null,
    )

    Button(
        onClick = {
            onSave(name, avatarUrl.takeIf { it.isNotBlank() }, avatarData, clearAvatar, shared)
        },
        enabled = name.isNotBlank() && name.length <= 80,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Save") }
}
