package dev.mewdeko.mobile.feature.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/** The DM sent to a member when they are banned, built with the full embed editor. */
@Composable
fun BanMessageSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var banMessage by remember(state.banMessage) { mutableStateOf(EmbedMessage.parse(state.banMessage)) }

    SectionCard {
        SectionCardHeader("Ban DM message", Icons.Default.Mail)
        Text(
            "Sent to a member when they are banned. Supports the standard placeholders.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(message = banMessage, onMessageChange = { banMessage = it })
        Button(
            onClick = { viewModel.saveBanMessage(banMessage.serialize()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save ban message") }
    }
}

/** Server recovery: status, reveal/copy the key, set up recovery + 2FA keys, and clear. */
@Composable
fun ServerRecoverySection(state: AdministrationState, viewModel: AdministrationViewModel) {
    val status = state.serverRecovery
    var showKey by remember { mutableStateOf(false) }
    var settingUp by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val clipboard = rememberTextClipboard()

    SectionCard {
        SectionCardHeader("Server recovery", Icons.Default.Key)
        Text(
            "Regain control of the server if the owner account is lost or compromised.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoRow("Status", if (status.isSetup) "Configured" else "Not configured")

        if (status.isSetup) {
            InfoRow("Recovery key", if (showKey) status.recoveryKey.orEmpty() else "••••••••••••••••")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showKey = !showKey },
                    modifier = Modifier.weight(1f),
                ) { Text(if (showKey) "Hide key" else "Show key") }
                OutlinedButton(
                    onClick = {
                        status.recoveryKey?.let(clipboard::copy)
                    },
                    enabled = status.recoveryKey != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Copy key") }
            }
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove recovery setup") }
        } else if (!settingUp) {
            Button(onClick = { settingUp = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Set up recovery")
            }
        }

        if (settingUp) {
            var recoveryKey by remember { mutableStateOf(randomRecoveryKey()) }
            var twoFactorKey by remember { mutableStateOf("") }

            MewdekoTextField(value = recoveryKey, onValueChange = { recoveryKey = it }, label = "Recovery key")
            OutlinedButton(onClick = { recoveryKey = randomRecoveryKey() }) { Text("Generate") }
            MewdekoTextField(
                value = twoFactorKey,
                onValueChange = { twoFactorKey = it },
                label = "Two-factor key",
                placeholder = "A second secret only you know",
            )
            Button(
                onClick = {
                    viewModel.setupServerRecovery(recoveryKey.trim(), twoFactorKey.trim())
                    settingUp = false
                },
                enabled = recoveryKey.trim().length >= 16 && twoFactorKey.trim().length >= 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save recovery keys") }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Remove server recovery?",
            message = "The stored recovery and two-factor keys will be deleted. You can set new ones afterwards.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.clearServerRecovery() },
            onDismiss = { confirmClear = false },
        )
    }
}

private fun randomRecoveryKey(): String =
    (1..24).joinToString("") { (0..15).random().toString(16) }

/** Mass ban, mass rename, prune inactive members, and prune a channel to a message. */
@Composable
fun MassOperationsSection(state: AdministrationState, viewModel: AdministrationViewModel) {
    var massBanIds by remember { mutableStateOf("") }
    var massBanReason by remember { mutableStateOf("") }
    var renamePattern by remember { mutableStateOf("{username}") }
    var pruneDays by remember { mutableStateOf(7) }
    var pruneToChannel by remember { mutableStateOf<Snowflake?>(null) }
    var pruneToMessageId by remember { mutableStateOf("") }

    var confirmMassBan by remember { mutableStateOf(false) }
    var confirmRename by remember { mutableStateOf(false) }
    var confirmPrune by remember { mutableStateOf(false) }
    var confirmPruneTo by remember { mutableStateOf(false) }

    val parsedIds = remember(massBanIds) {
        massBanIds.split(',', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.toLongOrNull() != null }
    }

    SectionCard {
        SectionCardHeader("Mass ban", Icons.Default.Gavel)
        MewdekoTextField(
            value = massBanIds,
            onValueChange = { massBanIds = it },
            label = "User IDs",
            placeholder = "Comma or space separated",
            singleLine = false,
            minLines = 3,
            supportingText = "${parsedIds.size} valid IDs",
        )
        MewdekoTextField(
            value = massBanReason,
            onValueChange = { massBanReason = it },
            label = "Reason",
            placeholder = "Optional",
        )
        Button(
            onClick = { confirmMassBan = true },
            enabled = parsedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mass ban ${parsedIds.size} users") }
    }

    SectionCard {
        SectionCardHeader("Mass rename", Icons.Default.Refresh)
        Text(
            "Renames every member's nickname using {username} as a placeholder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(value = renamePattern, onValueChange = { renamePattern = it }, label = "Nickname pattern")
        Button(
            onClick = { confirmRename = true },
            enabled = renamePattern.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rename every member") }
    }

    SectionCard {
        SectionCardHeader("Prune inactive members", Icons.Default.Shield)
        SliderRow(
            label = "Inactive for",
            value = pruneDays.toFloat(),
            onValueChange = { pruneDays = it.toInt() },
            valueRange = 1f..30f,
            valueLabel = "$pruneDays days",
        )
        Button(
            onClick = { confirmPrune = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Prune inactive members") }
    }

    SectionCard {
        SectionCardHeader("Prune channel to a message", Icons.Default.ContentCut)
        Text(
            "Deletes every message posted after the given message in a channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Pick a channel",
            selectedId = pruneToChannel,
            onSelect = { pruneToChannel = it },
            label = "Channel",
        )
        MewdekoTextField(
            value = pruneToMessageId,
            onValueChange = { pruneToMessageId = it },
            label = "Message ID",
            numeric = true,
        )
        Button(
            onClick = { confirmPruneTo = true },
            enabled = pruneToChannel != null && pruneToMessageId.toLongOrNull() != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Prune channel") }
    }

    if (confirmMassBan) {
        ConfirmDialog(
            title = "Ban ${parsedIds.size} users?",
            message = "This bans every listed ID immediately and cannot be undone in bulk.",
            confirmLabel = "Mass ban",
            onConfirm = {
                confirmMassBan = false
                viewModel.massBan(parsedIds, massBanReason.trim().takeIf { it.isNotEmpty() })
                massBanIds = ""
                massBanReason = ""
            },
            onDismiss = { confirmMassBan = false },
        )
    }

    if (confirmRename) {
        ConfirmDialog(
            title = "Rename every member?",
            message = "This renames ALL users in the server using \"$renamePattern\".",
            confirmLabel = "Rename",
            onConfirm = {
                confirmRename = false
                viewModel.massRename(renamePattern)
            },
            onDismiss = { confirmRename = false },
        )
    }

    if (confirmPrune) {
        ConfirmDialog(
            title = "Prune inactive members?",
            message = "Removes members inactive for $pruneDays days from the server.",
            confirmLabel = "Prune",
            onConfirm = {
                confirmPrune = false
                viewModel.pruneInactiveMembers(pruneDays)
            },
            onDismiss = { confirmPrune = false },
        )
    }

    if (confirmPruneTo) {
        ConfirmDialog(
            title = "Prune messages?",
            message = "Deletes every message after the specified message in the selected channel.",
            confirmLabel = "Prune",
            onConfirm = {
                confirmPruneTo = false
                val channel = pruneToChannel
                val messageId = pruneToMessageId
                if (channel != null) viewModel.pruneChannelToMessage(channel, messageId)
            },
            onDismiss = { confirmPruneTo = false },
        )
    }
}
