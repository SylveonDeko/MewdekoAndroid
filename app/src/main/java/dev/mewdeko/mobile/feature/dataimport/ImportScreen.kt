package dev.mewdeko.mobile.feature.dataimport

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.xp.XpCurveType
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Brings XP, levels, level roles and balances over from other bots. */
@Composable
fun ImportScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var undoTarget by remember { mutableStateOf<ImportHistoryEntry?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "file"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        viewModel.takeFile(name, bytes)
    }

    FeatureScaffold(
        title = "Import",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionCard {
            SectionCardHeader("Coming from another bot?", FaGlyph.Users)
            Text(
                text = "You see everything before it is written, and an import can be undone for 24 hours.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ImportSource.entries.forEach { source ->
                val selected = state.source == source
                ListItem(
                    headlineContent = { Text(source.label) },
                    supportingContent = { Text(source.brings) },
                    leadingContent = { GlyphOrb(source.glyph, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = if (selected) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable { viewModel.choose(source) },
                )
            }
        }

        state.source?.let { source ->
            SectionCard {
                SectionCardHeader(source.label, source.glyph)
                Text(
                    text = source.how,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (source.linkUrl != null && source.linkLabel != null) {
                    TextButton(onClick = { uriHandler.openUri(source.linkUrl) }, modifier = Modifier.heightIn(min = 44.dp)) {
                        Text(source.linkLabel)
                    }
                }
                when (source.input) {
                    ImportSource.Input.KEY -> OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = viewModel::setApiKey,
                        label = { Text("Key") },
                        placeholder = { Text("Paste the key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ImportSource.Input.FILE -> FilledTonalButton(
                        onClick = { picker.launch("*/*") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    ) {
                        Text(state.fileName ?: "Choose file")
                    }

                    ImportSource.Input.NONE -> Unit
                }
                FilledTonalButton(
                    onClick = viewModel::read,
                    enabled = state.canRead,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    if (state.isReading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(state.preview?.let { "${count(it.progress.toLong())} members so far" } ?: "Reading")
                    } else {
                        Text(if (state.preview == null) "Read data" else "Read again")
                    }
                }
            }
        }

        val preview = state.preview
        if (preview != null && !preview.isReading && preview.isSettings && state.settingsResult == null) {
            SettingsSections(state = state, preview = preview, viewModel = viewModel)
        } else if (preview != null && !preview.isReading && !preview.isSettings && state.result == null) {
            PreviewSections(state = state, preview = preview, viewModel = viewModel)
        }

        state.settingsResult?.let { settings ->
            SectionCard {
                SectionCardHeader("Imported", FaGlyph.CircleCheck)
                settings.sections.forEach { section ->
                    Text(
                        "${ImportSettingsSection.sectionTitle(section.section)}: " + if (section.failed) {
                            "could not be written"
                        } else {
                            "${section.written} written" + if (section.skipped > 0) ", ${section.skipped} skipped" else ""
                        },
                    )
                }
            }
        }

        state.result?.let { result ->
            SectionCard {
                SectionCardHeader("Imported", FaGlyph.CircleCheck)
                Text("Imported ${if (state.preview?.isCurrency == true) "balances" else "XP"} for ${count(result.members.toLong())} members.")
                if (result.skipped > 0) Text("Skipped ${count(result.skipped.toLong())} members.")
                if (result.roleRewards > 0) Text("Set ${result.roleRewards} level roles.")
                result.curveChanged?.let { Text("The XP curve is now ${XpCurveType.from(it).label}.") }
            }
        }

        SectionCard {
            SectionCardHeader("Past imports", FaGlyph.Clock)
            if (state.history.isEmpty()) {
                Text(
                    text = "Nothing has been imported into this server yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.history.forEach { entry ->
                val source = ImportSource.from(entry.source)
                ListItem(
                    headlineContent = {
                        Text(
                            if (entry.kind == 2) {
                                "${source?.label ?: "Import"}: ${count(entry.memberCount.toLong())} settings"
                            } else {
                                "${source?.label ?: "Import"}: ${if (entry.kind == 1) "balances" else "XP"} for ${count(entry.memberCount.toLong())} members"
                            },
                        )
                    },
                    supportingContent = {
                        Text(entry.undoneAt?.let { "Undone ${formatTime(it)}" } ?: formatTime(entry.dateAdded))
                    },
                    leadingContent = { GlyphOrb(source?.glyph ?: FaGlyph.File, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = if (entry.canUndo) {
                        {
                            FilledTonalButton(
                                onClick = { undoTarget = entry },
                                enabled = state.undoingId == null,
                                modifier = Modifier.heightIn(min = 44.dp),
                            ) {
                                if (state.undoingId == entry.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Undo")
                                }
                            }
                        }
                    } else {
                        null
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    undoTarget?.let { target ->
        ConfirmDialog(
            title = "Undo this import?",
            message = "Every member it changed goes back to what they had before. Anything they earned since the import is lost.",
            confirmLabel = "Undo import",
            onConfirm = {
                viewModel.undo(target)
                undoTarget = null
            },
            onDismiss = { undoTarget = null },
        )
    }
}

/** The sections of a settings import, each with a switch, and the import button. */
@Composable
private fun SettingsSections(state: ImportState, preview: ImportPreview, viewModel: ImportViewModel) {
    preview.sections.forEach { section ->
        SectionCard {
            SectionCardHeader(section.title, FaGlyph.Sliders)
            SwitchRow(
                title = "Bring this over",
                subtitle = if (section.count == 1) "1 item" else "${section.count} items",
                checked = section.key in state.chosenSections,
                onCheckedChange = { viewModel.setSection(section.key, it) },
            )
            section.details.take(12).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (section.details.size > 12) {
                Text(
                    "and ${section.details.size - 12} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    SectionCard {
        FilledTonalButton(
            onClick = viewModel::write,
            enabled = !state.isWriting && state.chosenSections.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            if (state.isWriting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Import settings")
            }
        }
        Text(
            text = "Reaction roles keep working on MEE6's messages, while button menus are posted again by Mewdeko. You can undo this for 24 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** What was read, the options for writing it, and the import button. */
@Composable
private fun PreviewSections(state: ImportState, preview: ImportPreview, viewModel: ImportViewModel) {
    SectionCard {
        SectionCardHeader("Found", FaGlyph.MagnifyingGlass)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(label = "Members", value = count(preview.memberCount.toLong()), modifier = Modifier.weight(1f))
            StatTile(
                label = if (preview.isCurrency) "Have a balance" else "Have XP",
                value = count(preview.existingCount.toLong()),
                modifier = Modifier.weight(1f),
            )
            if (!preview.isCurrency) {
                StatTile(label = "Level roles", value = state.readyRewardCount.toString(), modifier = Modifier.weight(1f))
            }
        }
    }

    if (preview.top.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Top members", FaGlyph.Trophy)
            preview.top.forEachIndexed { index, member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) {
                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Avatar(url = member.avatarUrl, contentDescription = null, size = 32)
                    Column(Modifier.weight(1f)) {
                        Text(member.name ?: member.userId, maxLines = 1)
                        Text(
                            text = if (preview.isCurrency) {
                                "${count(member.cash ?: 0)} cash, ${count(member.bank ?: 0)} bank"
                            } else {
                                listOfNotNull(member.level?.let { "Level $it" }, "${count(member.xp ?: 0)} XP").joinToString(", ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (!preview.isCurrency && preview.roleRewards.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Level roles", FaGlyph.Crown)
            preview.roleRewards.forEach { reward ->
                Text(
                    text = "Level ${reward.level}: ${if (reward.exists) reward.roleName ?: reward.roleId else "deleted role, skipped"}",
                    color = if (reward.exists) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    SectionCard {
        SectionCardHeader("Options", FaGlyph.Sliders)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.MergeType),
            options = ImportMergeMode.entries.map { SelectorOption(it.raw.toString(), it.label) },
            placeholder = ImportMergeMode.REPLACE.label,
            label = if (preview.isCurrency) "Balances members already have" else "XP members already have",
            selectedId = state.mergeMode.raw.toString(),
            onSelect = { raw ->
                viewModel.setMergeMode(ImportMergeMode.entries.firstOrNull { it.raw.toString() == raw } ?: ImportMergeMode.REPLACE)
            },
        )
        if (!preview.isCurrency) {
            preview.nativeCurve?.let { native ->
                val nativeName = XpCurveType.from(native).label
                val currentName = XpCurveType.from(preview.currentCurve).label
                SwitchRow(
                    title = "Use the $nativeName curve",
                    subtitle = if (state.useSourceCurve) {
                        "Every member keeps the exact same level and XP. This server switches from the $currentName curve."
                    } else {
                        "Members keep their levels and progress on the $currentName curve. Their XP numbers change."
                    },
                    checked = state.useSourceCurve,
                    onCheckedChange = viewModel::setUseSourceCurve,
                    glyph = FaGlyph.ChartSimple,
                )
            }
            if (state.readyRewardCount > 0) {
                SwitchRow(
                    title = "Import level roles",
                    subtitle = "Sets the same role for each level here. Existing roles on those levels are replaced.",
                    checked = state.importRoleRewards,
                    onCheckedChange = viewModel::setImportRoleRewards,
                    glyph = FaGlyph.Crown,
                )
            }
            SwitchRow(
                title = "Hand out level roles afterwards",
                subtitle = "Gives every imported member the roles for their level. Takes a while on big servers.",
                checked = state.syncRoles,
                onCheckedChange = viewModel::setSyncRoles,
                glyph = FaGlyph.Users,
            )
            MewdekoTextField(
                value = state.minimumLevel.toString(),
                onValueChange = { raw -> viewModel.setMinimumLevel(raw.filter { it.isDigit() }.take(4).toIntOrNull() ?: 0) },
                label = "Skip members below level",
                numeric = true,
            )
        }
        FilledTonalButton(
            onClick = viewModel::write,
            enabled = !state.isWriting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        ) {
            if (state.isWriting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Import ${count(preview.memberCount.toLong())} members")
            }
        }
        Text(
            text = "You can undo this for 24 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun count(value: Long): String = NumberFormat.getIntegerInstance().format(value)

/** Formats a UTC time from the bot, which arrives without a zone suffix, in the device's zone. */
private fun formatTime(value: String): String = runCatching {
    LocalDateTime.parse(value.removeSuffix("Z").substringBefore('+'))
        .atOffset(ZoneOffset.UTC)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
}.getOrDefault(value)
