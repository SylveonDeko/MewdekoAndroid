package dev.mewdeko.mobile.feature.administration

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.SwitchRow
import kotlinx.coroutines.launch

/** Ignored-channels management for anti-spam. The bot only exposes a toggle, not a list. */
@Composable
fun AntiSpamIgnoredChannelsCard(state: AdministrationState, viewModel: AdministrationViewModel) {
    if (state.protection?.antiSpam?.enabled != true) return
    var expanded by remember { mutableStateOf(false) }
    var channelId by remember { mutableStateOf<String?>(null) }

    SectionCard {
        SectionCardHeader(
            "Anti-spam ignored channels",
            Icons.Default.Shield,
            trailing = {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Done" else "Manage")
                }
            },
        )
        if (expanded) {
            Text(
                "Toggles whether a channel is exempt from anti-spam. Tap a channel to add it, tap again to remove it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a channel",
                selectedId = channelId,
                onSelect = { channelId = it },
            )
            Button(
                onClick = { channelId?.let(viewModel::toggleAntiSpamIgnoredChannel) },
                enabled = channelId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Toggle ignored") }
        }
    }
}

/** Anti-mass-post card: read-only summary, quick toggle, and a full editor. */
@Composable
fun AntiMassPostCard(state: AdministrationState, viewModel: AdministrationViewModel, onQuickToggle: () -> Unit) {
    val summary = state.protection?.antiMassPost ?: return
    var editing by remember { mutableStateOf(false) }

    ProtectionCard(
        title = "Anti-mass-post",
        enabled = summary.enabled,
        onEdit = { editing = true },
        onQuickToggle = onQuickToggle,
    ) {
        Text(
            "${summary.channelThreshold} channels in ${summary.timeWindowSeconds}s, " +
                "${AntiPunishmentAction.from(summary.action).label}. Caught ${summary.counter}.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (editing) {
        var enabled by remember(summary) { mutableStateOf(summary.enabled) }
        var channelThreshold by remember(summary) { mutableStateOf(summary.channelThreshold) }
        var timeWindow by remember(summary) { mutableStateOf(summary.timeWindowSeconds) }
        var checkLinksOnly by remember(summary) { mutableStateOf(summary.checkLinksOnly) }
        var action by remember(summary) { mutableStateOf(AntiPunishmentAction.from(summary.action)) }
        var punishDuration by remember(summary) { mutableStateOf(summary.punishDuration) }

        EditorSheet(title = "Anti-mass-post", onDismiss = { editing = false }) {
            SwitchRow("Enabled", enabled, { enabled = it })
            SliderRow("Channel threshold", channelThreshold.toFloat(), { channelThreshold = it.toInt() }, 2f..20f)
            SliderRow(
                "Time window", timeWindow.toFloat(), { timeWindow = it.toInt() }, 10f..600f,
                valueLabel = "${timeWindow}s",
            )
            SwitchRow("Only track messages with links", checkLinksOnly, { checkLinksOnly = it })
            ActionPicker(action) { action = it }
            SliderRow(
                "Punish duration", punishDuration.toFloat(), { punishDuration = it.toInt() }, 0f..1440f,
                valueLabel = "${punishDuration}m",
            )
            SaveCancelRow(
                onCancel = { editing = false },
                onSave = {
                    editing = false
                    viewModel.saveAntiMassPost(
                        enabled, channelThreshold, timeWindow, checkLinksOnly, action.raw, punishDuration,
                    )
                },
            )
        }
    }
}

/** Anti-pattern card: behaviour-analysis summary, quick toggle, editor, and regex pattern management. */
@Composable
fun AntiPatternCard(state: AdministrationState, viewModel: AdministrationViewModel, onQuickToggle: () -> Unit) {
    val summary = state.protection?.antiPattern ?: return
    var editing by remember { mutableStateOf(false) }
    var managingPatterns by remember { mutableStateOf(false) }

    ProtectionCard(
        title = "Anti-pattern",
        enabled = summary.enabled,
        onEdit = { editing = true },
        onQuickToggle = onQuickToggle,
    ) {
        Text(
            "Score ${summary.minimumScore}+, ${AntiPunishmentAction.from(summary.action).label}, " +
                "${summary.patternCount} patterns, caught ${summary.counter}.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { managingPatterns = true }) { Text("Manage patterns") }
    }

    if (editing) {
        var enabled by remember(summary) { mutableStateOf(summary.enabled) }
        var action by remember(summary) { mutableStateOf(AntiPunishmentAction.from(summary.action)) }
        var punishDuration by remember(summary) { mutableStateOf(summary.punishDuration) }
        var minimumScore by remember(summary) { mutableStateOf(summary.minimumScore) }
        var checkAccountAge by remember(summary) { mutableStateOf(summary.checkAccountAge) }
        var maxAccountAgeMonths by remember(summary) { mutableStateOf(summary.maxAccountAgeMonths) }
        var checkJoinTiming by remember(summary) { mutableStateOf(summary.checkJoinTiming) }
        var maxJoinHours by remember(summary) { mutableStateOf(summary.maxJoinHours) }
        var checkBatchCreation by remember(summary) { mutableStateOf(summary.checkBatchCreation) }
        var checkOfflineStatus by remember(summary) { mutableStateOf(summary.checkOfflineStatus) }
        var checkNewAccounts by remember(summary) { mutableStateOf(summary.checkNewAccounts) }
        var newAccountDays by remember(summary) { mutableStateOf(summary.newAccountDays) }

        EditorSheet(title = "Anti-pattern", onDismiss = { editing = false }) {
            SwitchRow("Enabled", enabled, { enabled = it })
            ActionPicker(action) { action = it }
            SliderRow(
                "Punish duration", punishDuration.toFloat(), { punishDuration = it.toInt() }, 0f..1440f,
                valueLabel = "${punishDuration}m",
            )
            SliderRow("Minimum score", minimumScore.toFloat(), { minimumScore = it.toInt() }, 1f..100f)
            SwitchRow("Check account age", checkAccountAge, { checkAccountAge = it })
            if (checkAccountAge) {
                SliderRow(
                    "Max account age", maxAccountAgeMonths.toFloat(), { maxAccountAgeMonths = it.toInt() },
                    1f..120f, valueLabel = "${maxAccountAgeMonths}mo",
                )
            }
            SwitchRow("Check join timing", checkJoinTiming, { checkJoinTiming = it })
            if (checkJoinTiming) {
                SliderRow(
                    "Max join hours", maxJoinHours.toFloat(), { maxJoinHours = it.toDouble() }, 1f..168f,
                    valueLabel = "${maxJoinHours}h",
                )
            }
            SwitchRow("Check batch account creation", checkBatchCreation, { checkBatchCreation = it })
            SwitchRow("Check offline status", checkOfflineStatus, { checkOfflineStatus = it })
            SwitchRow("Flag very new accounts", checkNewAccounts, { checkNewAccounts = it })
            if (checkNewAccounts) {
                SliderRow(
                    "New account days", newAccountDays.toFloat(), { newAccountDays = it.toInt() }, 1f..30f,
                )
            }
            SaveCancelRow(
                onCancel = { editing = false },
                onSave = {
                    editing = false
                    viewModel.saveAntiPattern(
                        enabled, action.raw, punishDuration, checkAccountAge, maxAccountAgeMonths,
                        checkJoinTiming, maxJoinHours, checkBatchCreation, checkOfflineStatus,
                        checkNewAccounts, newAccountDays, minimumScore,
                    )
                },
            )
        }
    }

    if (managingPatterns) {
        var pattern by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var checkUsername by remember { mutableStateOf(true) }
        var checkDisplayName by remember { mutableStateOf(true) }

        EditorSheet(title = "Anti-pattern regexes", onDismiss = { managingPatterns = false }) {
            MewdekoTextField(value = name, onValueChange = { name = it }, label = "Name")
            MewdekoTextField(
                value = pattern, onValueChange = { pattern = it }, label = "Regex pattern",
                placeholder = "^[a-z]+[0-9]{4,}\$",
            )
            SwitchRow("Check username", checkUsername, { checkUsername = it })
            SwitchRow("Check display name", checkDisplayName, { checkDisplayName = it })
            Button(
                onClick = {
                    viewModel.addAntiPatternPattern(pattern, name, checkUsername, checkDisplayName)
                    pattern = ""
                    name = ""
                },
                enabled = pattern.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add pattern") }

            if (state.antiPatternPatterns.isEmpty()) {
                EmptyState("No patterns yet.")
            } else {
                state.antiPatternPatterns.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.name ?: "Unnamed") },
                        supportingContent = { Text(entry.pattern, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            OutlinedButton(onClick = { viewModel.removeAntiPatternPattern(entry.id) }) {
                                Text("Remove")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

/** Anti-post-channel (honeypot) card: summary, quick toggle, editor, and channel/role/user lists. */
@Composable
fun AntiPostChannelCard(state: AdministrationState, viewModel: AdministrationViewModel, onQuickToggle: () -> Unit) {
    val summary = state.protection?.antiPostChannel ?: return
    var editing by remember { mutableStateOf(false) }

    ProtectionCard(
        title = "Anti-post-channel (honeypot)",
        enabled = summary.enabled,
        onEdit = { editing = true },
        onQuickToggle = onQuickToggle,
    ) {
        Text(
            "${summary.channelCount} honeypot channels, ${AntiPunishmentAction.from(summary.action).label}. " +
                "Caught ${summary.counter}.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (editing) {
        var enabled by remember(summary) { mutableStateOf(summary.enabled) }
        var action by remember(summary) { mutableStateOf(AntiPunishmentAction.from(summary.action)) }
        var punishDuration by remember(summary) { mutableStateOf(summary.punishDuration) }
        var deleteMessages by remember(summary) { mutableStateOf(summary.deleteMessages) }
        var notifyUser by remember(summary) { mutableStateOf(summary.notifyUser) }
        var ignoreBots by remember(summary) { mutableStateOf(summary.ignoreBots) }
        var honeypotChannels by remember(summary) { mutableStateOf(summary.channels) }
        var ignoredRoles by remember(summary) { mutableStateOf(summary.ignoredRoles) }
        var newIgnoredUser by remember { mutableStateOf("") }

        EditorSheet(title = "Anti-post-channel", onDismiss = { editing = false }) {
            SwitchRow("Enabled", enabled, { enabled = it })
            ActionPicker(action) { action = it }
            SliderRow(
                "Punish duration", punishDuration.toFloat(), { punishDuration = it.toInt() }, 0f..1440f,
                valueLabel = "${punishDuration}m",
            )
            SwitchRow("Delete messages", deleteMessages, { deleteMessages = it })
            SwitchRow("DM the user", notifyUser, { notifyUser = it })
            SwitchRow("Ignore bots", ignoreBots, { ignoreBots = it })

            Text("Honeypot channels", style = MaterialTheme.typography.titleSmall)
            DiscordSelector(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick channels",
                multiple = true,
                selection = honeypotChannels,
                onSelectionChange = { honeypotChannels = it },
            )
            Button(
                onClick = {
                    val added = honeypotChannels - summary.channels.toSet()
                    val removed = summary.channels - honeypotChannels.toSet()
                    added.forEach(viewModel::addHoneypotChannel)
                    removed.forEach(viewModel::removeHoneypotChannel)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save channels") }

            Text("Ignored roles", style = MaterialTheme.typography.titleSmall)
            DiscordSelector(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick roles to exempt",
                multiple = true,
                selection = ignoredRoles,
                onSelectionChange = { ignoredRoles = it },
            )
            Button(
                onClick = {
                    val added = ignoredRoles - summary.ignoredRoles.toSet()
                    val removed = summary.ignoredRoles - ignoredRoles.toSet()
                    (added + removed).forEach(viewModel::toggleHoneypotIgnoredRole)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save ignored roles") }

            Text("Ignored users", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MewdekoTextField(
                    value = newIgnoredUser, onValueChange = { newIgnoredUser = it },
                    label = "User ID", numeric = true, modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Button(
                    onClick = { viewModel.toggleHoneypotIgnoredUser(newIgnoredUser); newIgnoredUser = "" },
                    enabled = newIgnoredUser.toLongOrNull() != null,
                ) { Text("Add") }
            }
            summary.ignoredUsers.forEach { userId ->
                ListItem(
                    headlineContent = { Text(userId) },
                    trailingContent = {
                        OutlinedButton(onClick = { viewModel.toggleHoneypotIgnoredUser(userId) }) {
                            Text("Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SaveCancelRow(
                onCancel = { editing = false },
                onSave = {
                    editing = false
                    viewModel.saveAntiPostChannel(enabled, action.raw, punishDuration, deleteMessages, notifyUser, ignoreBots)
                },
            )
        }
    }
}

/** Anti-image-hash card: summary, quick toggle, editor, preset list, blocked images, and the block-a-new-image flow. */
@Composable
fun AntiImageHashCard(state: AdministrationState, viewModel: AdministrationViewModel, onQuickToggle: () -> Unit) {
    val summary = state.imageHash
    var editing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ProtectionCard(
        title = "Anti-image-hash",
        enabled = summary.enabled,
        onEdit = { editing = true },
        onQuickToggle = onQuickToggle,
    ) {
        Text(
            "${summary.hashCount} blocked images, tolerance ${summary.hashThreshold}, caught ${summary.counter}.",
            style = MaterialTheme.typography.bodySmall,
        )

        SwitchRow(
            title = "Block known scam images",
            subtitle = "${summary.presetCount} shipped images" +
                if (summary.usePresetList && summary.presetTriggers > 0) ", caught ${summary.presetTriggers}" else "",
            checked = summary.usePresetList,
            onCheckedChange = { viewModel.togglePresetScamImages() },
        )

        var pendingBase64 by remember { mutableStateOf<String?>(null) }
        var pendingUrl by remember { mutableStateOf("") }
        var pendingHash by remember { mutableStateOf<String?>(null) }
        var pendingReliable by remember { mutableStateOf(true) }
        var pendingName by remember { mutableStateOf("") }
        var pendingAction by remember { mutableStateOf<String?>(null) }
        var hashing by remember { mutableStateOf(false) }
        var addError by remember { mutableStateOf<String?>(null) }

        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            pendingBase64 = "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
            pendingUrl = ""
            scope.launch {
                hashing = true
                addError = null
                val result = viewModel.computeImageHash(pendingBase64, null)
                hashing = false
                pendingHash = result?.hash
                pendingReliable = result?.reliable ?: false
                if (result?.hash == null) addError = "Could not read that image."
                else if (!pendingReliable) addError = "This image is too plain to identify reliably."
            }
        }

        Text("Block a new image", style = MaterialTheme.typography.titleSmall)
        Button(onClick = { pickImage.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Text("Upload an image")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MewdekoTextField(
                value = pendingUrl,
                onValueChange = { pendingUrl = it; pendingBase64 = null; pendingHash = null },
                label = "...or paste an image URL",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Button(
                onClick = {
                    scope.launch {
                        hashing = true
                        addError = null
                        val result = viewModel.computeImageHash(null, pendingUrl.trim())
                        hashing = false
                        pendingHash = result?.hash
                        pendingReliable = result?.reliable ?: false
                        if (result?.hash == null) addError = "Could not read that image."
                        else if (!pendingReliable) addError = "This image is too plain to identify reliably."
                    }
                },
                enabled = !hashing && pendingUrl.isNotBlank(),
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
            }
        }
        addError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (pendingHash != null && pendingReliable) {
            Text(
                "Hash: $pendingHash", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MewdekoTextField(value = pendingName, onValueChange = { pendingName = it }, label = "Label")
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Gavel),
                options = AntiPunishmentAction.entries.map { SelectorOption(it.raw.toString(), it.label) },
                placeholder = "Use the default action",
                selectedId = pendingAction,
                onSelect = { pendingAction = it },
                label = "Action for this image",
            )
            Button(
                onClick = {
                    viewModel.blockImage(
                        pendingHash!!, pendingUrl.trim().ifBlank { null }, pendingName,
                        pendingAction?.toIntOrNull(),
                    )
                    pendingHash = null
                    pendingBase64 = null
                    pendingUrl = ""
                    pendingName = ""
                    pendingAction = null
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Block image") }
        }

        Text("Blocked images", style = MaterialTheme.typography.titleSmall)
        if (state.bannedImageHashes.isEmpty()) {
            EmptyState("No images blocked yet.", icon = Icons.Default.Image)
        } else {
            state.bannedImageHashes.forEach { entry ->
                ListItem(
                    headlineContent = { Text(entry.name ?: "Unnamed") },
                    supportingContent = { Text("${entry.hitCount} hits · ${entry.hash}", style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        OutlinedButton(onClick = { viewModel.removeBannedImageHash(entry.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (editing) {
        var enabled by remember(summary) { mutableStateOf(summary.enabled) }
        var action by remember(summary) { mutableStateOf(AntiPunishmentAction.from(summary.action)) }
        var punishDuration by remember(summary) { mutableStateOf(summary.punishDuration) }
        var hashThreshold by remember(summary) { mutableStateOf(summary.hashThreshold) }
        var deleteMessages by remember(summary) { mutableStateOf(summary.deleteMessages) }
        var notifyUser by remember(summary) { mutableStateOf(summary.notifyUser) }
        var checkEmbeds by remember(summary) { mutableStateOf(summary.checkEmbeds) }
        var ignoreBots by remember(summary) { mutableStateOf(summary.ignoreBots) }
        var checkBorders by remember(summary) { mutableStateOf(summary.checkBorders) }

        EditorSheet(title = "Anti-image-hash", onDismiss = { editing = false }) {
            SwitchRow("Enabled", enabled, { enabled = it })
            ActionPicker(action) { action = it }
            SliderRow(
                "Punish duration", punishDuration.toFloat(), { punishDuration = it.toInt() }, 0f..1440f,
                valueLabel = "${punishDuration}m",
            )
            SliderRow("Match tolerance", hashThreshold.toFloat(), { hashThreshold = it.toInt() }, 0f..64f)
            SwitchRow("Delete the message", deleteMessages, { deleteMessages = it })
            SwitchRow("DM the user", notifyUser, { notifyUser = it })
            SwitchRow("Check embedded images", checkEmbeds, { checkEmbeds = it })
            SwitchRow("Ignore bots", ignoreBots, { ignoreBots = it })
            SwitchRow("Catch bordered copies", checkBorders, { checkBorders = it })
            SaveCancelRow(
                onCancel = { editing = false },
                onSave = {
                    editing = false
                    viewModel.saveAntiImageHash(
                        enabled, action.raw, punishDuration, hashThreshold, deleteMessages,
                        notifyUser, checkEmbeds, ignoreBots, checkBorders,
                    )
                },
            )
        }
    }
}

/** Shared punishment action picker used by the advanced protection editors. */
@Composable
private fun ActionPicker(action: AntiPunishmentAction, onChange: (AntiPunishmentAction) -> Unit) {
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Gavel),
        options = AntiPunishmentAction.entries.map { SelectorOption(it.raw.toString(), it.label) },
        placeholder = "Action",
        selectedId = action.raw.toString(),
        onSelect = { onChange(AntiPunishmentAction.from(it?.toIntOrNull() ?: 0)) },
        label = "Action",
    )
}

/** Shared modal bottom sheet shell used by every protection module's full editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Shared cancel/save button row for the editor sheets. */
@Composable
fun SaveCancelRow(onCancel: () -> Unit, onSave: () -> Unit, saveLabel: String = "Save") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text(saveLabel) }
    }
}
