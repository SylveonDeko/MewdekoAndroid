package dev.mewdeko.mobile.feature.forms

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.net.InstantParser
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.LocalSheetDismiss
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The Material icon standing in for each question widget. */
private val FormQuestionType.icon: ImageVector
    get() = when (this) {
        FormQuestionType.SHORT_TEXT -> Icons.AutoMirrored.Filled.ShortText
        FormQuestionType.LONG_TEXT -> Icons.AutoMirrored.Filled.Notes
        FormQuestionType.MULTIPLE_CHOICE -> Icons.Default.RadioButtonChecked
        FormQuestionType.CHECKBOXES -> Icons.Default.CheckBox
        FormQuestionType.DROPDOWN -> Icons.Default.UnfoldMore
        FormQuestionType.NUMBER -> Icons.Default.Numbers
        FormQuestionType.EMAIL -> Icons.Default.Email
        FormQuestionType.URL -> Icons.Default.Link
        FormQuestionType.SECTION_BREAK -> Icons.Default.EventBusy
    }

/** The Material icon for each detail section. */
private val FormSection.icon: ImageVector
    get() = when (this) {
        FormSection.SETTINGS -> Icons.Default.Settings
        FormSection.QUESTIONS -> Icons.AutoMirrored.Filled.ListAlt
        FormSection.RESPONSES -> Icons.Default.Inbox
        FormSection.VERSIONS -> Icons.Default.History
    }

/** Renders an ISO timestamp as a relative phrase, falling back to the raw text. */
private fun timestamp(raw: String?): String =
    raw?.let { InstantParser.parse(it)?.relativeToNow() ?: it }.orEmpty()

/** Flattens every mutual guild's custom emojis into picker options, labelled by their source guild. */
private fun emojiOptionsOf(guilds: List<FormEmojiGuildInfo>): List<SelectorOption> =
    guilds.flatMap { guildEmojis ->
        guildEmojis.emojis.map { emoji ->
            SelectorOption(id = emoji.formatted, name = ":${emoji.name}:", subtitle = guildEmojis.guild.name)
        }
    }

/** Member-facing forms: build them, collect answers, review submissions, and track history. */
@Composable
fun FormsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: FormsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(FormType.REGULAR) }
    var pendingFormDelete by remember { mutableStateOf<Form?>(null) }
    var pendingQuestionDelete by remember { mutableStateOf<Int?>(null) }
    var editingQuestionIndex by remember { mutableStateOf<Int?>(null) }
    var showGuildSettings by remember { mutableStateOf(false) }
    var pendingResponseDelete by remember { mutableStateOf<QueuedResponse?>(null) }
    var pendingVersionRestore by remember { mutableStateOf<FormVersion?>(null) }

    val selected = state.selected
    val emojiOptions = remember(state.availableEmojiGuilds) { emojiOptionsOf(state.availableEmojiGuilds) }

    /** The open form is an in-screen layer, so system back must close it first. */
    BackHandler(enabled = selected != null) { viewModel.closeDetail() }

    FeatureScaffold(
        title = selected?.name?.ifEmpty { "Form" } ?: "Forms",
        subtitle = selected?.let { guild.name.takeIf { name -> name.isNotEmpty() } }
            ?: guild.name.takeIf { it.isNotEmpty() },
        onBack = if (selected != null) viewModel::closeDetail else onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = {
            if (selected == null) viewModel.load(refreshing = true) else viewModel.setSection(state.section)
        },
        onRetry = { viewModel.load() },
        actions = {
            if (selected != null) {
                FormActionsMenu(
                    form = selected,
                    onPublish = { viewModel.publish(selected) },
                    onToggleActive = { viewModel.toggleActive(selected) },
                    onDuplicate = { viewModel.duplicate(selected) },
                    onShare = { viewModel.requestShareLink(selected) },
                    onPreview = {
                        scope.launch { viewModel.previewUrl(selected)?.let(uriHandler::openUri) }
                    },
                    onDelete = { pendingFormDelete = selected },
                )
            } else {
                IconButton(onClick = { showGuildSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Form defaults")
                }
            }
        },
        floatingActionButton = {
            when {
                selected == null -> NewItemFab(
                    label = "New form",
                    onClick = { newName = ""; newType = FormType.REGULAR; creating = true },
                )

                (state.section == FormSection.SETTINGS || state.section == FormSection.QUESTIONS) &&
                    state.hasUnsavedForm ->
                    ExtendedFloatingActionButton(
                        onClick = viewModel::saveForm,
                        icon = { Icon(Icons.Default.Save, contentDescription = null) },
                        text = { Text("Save changes") },
                    )
            }
        },
    ) {
        if (selected == null) {
            FormListSection(
                state = state,
                onOpen = viewModel::open,
                onPublish = viewModel::publish,
                onToggleActive = viewModel::toggleActive,
                onDuplicate = viewModel::duplicate,
                onShare = viewModel::requestShareLink,
                onPreview = { form ->
                    scope.launch { viewModel.previewUrl(form)?.let(uriHandler::openUri) }
                },
                onDelete = { pendingFormDelete = it },
                onCreate = { newName = ""; newType = FormType.REGULAR; creating = true },
            )
        } else {
            SectionTabs(
                tabs = FormSection.entries.map { SectionTab(it.id, it.label, it.icon) },
                selectedId = state.section.id,
                onSelect = { id ->
                    FormSection.entries.firstOrNull { it.id == id }?.let(viewModel::setSection)
                },
            )
            when (state.section) {
                FormSection.SETTINGS -> FormSettingsSection(
                    state = state,
                    form = selected,
                    onEdit = viewModel::editForm,
                    emojiOptions = emojiOptions,
                )
                FormSection.QUESTIONS -> FormQuestionsSection(
                    state = state,
                    onAddQuestion = { type ->
                        val index = viewModel.addQuestion(type)
                        if (index >= 0) editingQuestionIndex = index
                    },
                    onEdit = { editingQuestionIndex = it },
                    onDelete = { pendingQuestionDelete = it },
                    onDuplicate = viewModel::duplicateQuestionAt,
                    onMove = viewModel::moveQuestion,
                    onSetActivePage = viewModel::setActivePage,
                    onAddPage = viewModel::addPage,
                    onMovePage = viewModel::movePage,
                    onRemovePage = viewModel::removePage,
                    onAddHeading = viewModel::addHeadingToFirstPage,
                    onUpdateHeading = viewModel::updatePageHeading,
                )

                FormSection.RESPONSES -> FormResponsesSection(
                    form = selected,
                    state = state,
                    onFilter = viewModel::setResponseFilter,
                    onPage = { viewModel.loadResponses(page = it) },
                    onToggleExpand = viewModel::toggleResponseExpanded,
                    onLoadRevisions = viewModel::loadResponseRevisions,
                    onApprove = viewModel::approve,
                    onReject = viewModel::reject,
                    onDelete = { pendingResponseDelete = it },
                    onExport = {
                        scope.launch {
                            viewModel.exportResponsesCsv(selected)?.let { csv ->
                                shareCsv(context = context, csv = csv, formName = selected.name)
                            }
                        }
                    },
                )

                FormSection.VERSIONS -> FormVersionsSection(
                    state = state,
                    onLoad = viewModel::loadVersions,
                    onDiff = viewModel::loadVersionDiff,
                    onDismissDiff = viewModel::clearVersionDiff,
                    onRestore = { pendingVersionRestore = it },
                )
            }
        }
    }

    if (creating) {
        FormSheet(
            title = "New form",
            confirmLabel = "Create",
            confirmEnabled = newName.isNotBlank(),
            onConfirm = { creating = false; viewModel.createForm(newName, newType.raw) },
            onDismiss = { creating = false },
        ) {
            MewdekoTextField(
                value = newName,
                onValueChange = { newName = it },
                label = "Name",
                placeholder = "Application",
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Article),
                options = FormType.entries.map { SelectorOption(it.raw.toString(), it.label) },
                placeholder = "Regular",
                selectedId = newType.raw.toString(),
                onSelect = { value -> newType = FormType.from(value?.toIntOrNull() ?: 0) },
                label = "Type (cannot be changed later)",
            )
        }
    }

    pendingFormDelete?.let { form ->
        ConfirmDialog(
            title = "Delete form?",
            message = "\"${form.name}\" and all ${form.responses} responses will be removed.",
            onConfirm = { pendingFormDelete = null; viewModel.deleteForm(form) },
            onDismiss = { pendingFormDelete = null },
        )
    }

    pendingQuestionDelete?.let { index ->
        val question = state.questions.getOrNull(index)
        ConfirmDialog(
            title = if (question?.type == FormQuestionType.SECTION_BREAK) "Remove page break?" else "Delete question?",
            message = "\"${question?.questionText.orEmpty().ifEmpty { "Untitled" }}\" will be removed. Save to apply.",
            onConfirm = { pendingQuestionDelete = null; viewModel.removeQuestionAt(index) },
            onDismiss = { pendingQuestionDelete = null },
        )
    }

    pendingResponseDelete?.let { response ->
        ConfirmDialog(
            title = "Delete response?",
            message = "This response and its answers will be permanently removed.",
            onConfirm = { pendingResponseDelete = null; viewModel.deleteResponse(response) },
            onDismiss = { pendingResponseDelete = null },
        )
    }

    pendingVersionRestore?.let { version ->
        ConfirmDialog(
            title = "Restore version ${version.versionNumber}?",
            message = "The form's current settings and questions will be replaced with this version.",
            confirmLabel = "Restore",
            destructive = false,
            onConfirm = { pendingVersionRestore = null; viewModel.restoreVersion(version) },
            onDismiss = { pendingVersionRestore = null },
        )
    }

    if (showGuildSettings) {
        GuildSettingsSheet(
            emotes = state.guildReviewEmotes,
            emojiOptions = emojiOptions,
            onSave = { approve, reject -> viewModel.saveGuildReviewEmotes(approve, reject) },
            onDismiss = { showGuildSettings = false },
        )
    }

    state.shareLink?.let { link ->
        ShareLinkDialog(link = link, onDismiss = viewModel::clearShareLink)
    }

    editingQuestionIndex?.let { index ->
        val question = state.questions.getOrNull(index)
        if (question != null) {
            QuestionEditor(
                question = question,
                roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
                otherQuestions = state.questions.take(index)
                    .filter { it.type != FormQuestionType.SECTION_BREAK },
                onDismiss = { editingQuestionIndex = null },
                onSave = {
                    editingQuestionIndex = null
                    viewModel.replaceQuestionAt(index, it)
                },
            )
        }
    }
}

/**
 * Opens the system share sheet with the CSV as a real file attachment.
 *
 * Writing it into the app's cache and handing out a `content://` URI through [FileProvider] is
 * what lets a spreadsheet app open it as a spreadsheet, rather than as a chat message full of
 * commas; a receiving app can also truncate very large plain text, which a file does not risk.
 * This depends on a `<provider>` entry existing in the manifest (see this feature's sharedEdits);
 * until that lands, or on a device that otherwise refuses the authority, it falls back to sharing
 * the CSV as plain text exactly as before, so the export never simply does nothing.
 */
private fun shareCsv(context: Context, csv: String, formName: String) {
    val safeName = formName.trim().ifEmpty { "form" }.replace(Regex("[^A-Za-z0-9_-]+"), "_")
    val uri = runCatching {
        val dir = File(context.cacheDir, "form-exports").apply { mkdirs() }
        val file = File(dir, "$safeName-responses.csv")
        file.writeText(csv)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

    val intent = if (uri != null) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$formName responses")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "$formName responses")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
    }
    context.startActivity(Intent.createChooser(intent, "Export responses"))
}

@Composable
private fun FormListSection(
    state: FormsState,
    onOpen: (Form) -> Unit,
    onPublish: (Form) -> Unit,
    onToggleActive: (Form) -> Unit,
    onDuplicate: (Form) -> Unit,
    onShare: (Form) -> Unit,
    onPreview: (Form) -> Unit,
    onDelete: (Form) -> Unit,
    onCreate: () -> Unit,
) {
    if (state.forms.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No forms yet.",
                icon = Icons.Default.Description,
                actionLabel = "New form",
                onAction = onCreate,
            )
        }
        return
    }

    state.forms.forEach { form ->
        SectionCard(modifier = Modifier.clickableRow { onOpen(form) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(6.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        form.name.ifEmpty { "Untitled form" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    form.description?.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FormStatusPill(form)
                FormActionsMenu(
                    form = form,
                    onPublish = { onPublish(form) },
                    onToggleActive = { onToggleActive(form) },
                    onDuplicate = { onDuplicate(form) },
                    onShare = { onShare(form) },
                    onPreview = { onPreview(form) },
                    onDelete = { onDelete(form) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagChip("${form.responses} responses", icon = Icons.Default.Inbox)
                TagChip("${form.questions} questions", icon = Icons.AutoMirrored.Filled.ListAlt)
                if (form.reviewsResponses) {
                    TagChip(form.type.label, icon = Icons.Default.VerifiedUser)
                    if (form.pending > 0) {
                        TagChip("${form.pending} pending", icon = Icons.Default.Alarm)
                    }
                }
                form.maxResponses?.let { TagChip("Cap $it", icon = Icons.Default.EventBusy) }
                if (form.allowAnonymous) TagChip("Anonymous")
                if (form.requireCaptcha) TagChip("Captcha")
                if (form.allowMultipleSubmissions) TagChip("Multi-submit")
                if (form.submitChannelId != null) TagChip("Posts to channel", icon = Icons.Default.Tag)
                form.expiry?.let { TagChip("Closes ${timestamp(it)}", icon = Icons.Default.Alarm) }
            }
        }
    }
}

@Composable
private fun FormStatusPill(form: Form) {
    val label = when {
        form.isDraft -> "Draft"
        form.isActive -> "Active"
        else -> "Inactive"
    }
    val color = if (form.isActive && !form.isDraft) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FormActionsMenu(
    form: Form,
    onPublish: () -> Unit,
    onToggleActive: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Form actions")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (form.isDraft) {
                DropdownMenuItem(
                    text = { Text("Publish") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                    onClick = { open = false; onPublish() },
                )
            }
            DropdownMenuItem(
                text = { Text("Preview") },
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                onClick = { open = false; onPreview() },
            )
            DropdownMenuItem(
                text = { Text("Copy share link") },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { open = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text(if (form.isActive) "Deactivate" else "Activate") },
                leadingIcon = {
                    Icon(
                        if (form.isActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = null,
                    )
                },
                onClick = { open = false; onToggleActive() },
            )
            DropdownMenuItem(
                text = { Text("Duplicate") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = { open = false; onDuplicate() },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuildSettingsSheet(
    emotes: FormReviewEmotes,
    emojiOptions: List<SelectorOption>,
    onSave: (String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var approve by remember(emotes) { mutableStateOf(emotes.approveEmote.orEmpty()) }
    var reject by remember(emotes) { mutableStateOf(emotes.rejectEmote.orEmpty()) }

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "Form defaults") {
        val dismissSheet = LocalSheetDismiss.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "The review button emotes every form falls back to, unless it sets its own.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmoteField(
                label = "Approve",
                value = approve,
                emojiOptions = emojiOptions,
                placeholder = "✅",
                onValueChange = { approve = it },
            )
            EmoteField(
                label = "Reject",
                value = reject,
                emojiOptions = emojiOptions,
                placeholder = "❌",
                onValueChange = { reject = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { approve = ""; reject = "" }) { Text("Reset") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = dismissSheet, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(approve.trim().ifEmpty { null }, reject.trim().ifEmpty { null })
                        dismissSheet()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

/**
 * An emote picker: a selector over the guild's custom emojis, plus a fallback text field for a
 * plain unicode emoji or a pasted emoji code. Mirrors the reaction-emote picker on the giveaways
 * screen, so a form's review buttons are chosen the same way every other emote-bearing setting is.
 */
@Composable
private fun EmoteField(
    label: String,
    value: String,
    emojiOptions: List<SelectorOption>,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.EmojiEmotions),
        options = emojiOptions,
        placeholder = placeholder,
        selectedId = value.takeIf { it.isNotBlank() },
        onSelect = { onValueChange(it.orEmpty()) },
        label = "$label emote",
    )
    MewdekoTextField(
        value = value,
        onValueChange = onValueChange,
        label = "Or type an emoji or paste a custom emoji code",
        placeholder = placeholder,
    )
}

@Composable
private fun ShareLinkDialog(link: String, onDismiss: () -> Unit) {
    val clipboard = rememberTextClipboard()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share link") },
        text = {
            Text(link, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(
                onClick = { clipboard.copy(link); onDismiss() },
            ) { Text("Copy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun FormSettingsSection(
    state: FormsState,
    form: Form,
    onEdit: ((Form) -> Form) -> Unit,
    emojiOptions: List<SelectorOption>,
) {
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }
    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader("Identity", Icons.Default.Description)
        MewdekoTextField(
            value = form.name,
            onValueChange = { value -> onEdit { it.copy(name = value) } },
            label = "Name",
        )
        MewdekoTextField(
            value = form.description.orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(description = value.takeIf { text -> text.isNotEmpty() }) }
            },
            label = "Description",
            singleLine = false,
            minLines = 3,
        )
        InfoRow(label = "Type", value = form.type.label)
        SwitchRow(
            title = "Draft",
            subtitle = "Hidden until published",
            checked = form.isDraft,
            onCheckedChange = { value -> onEdit { it.copy(isDraft = value) } },
        )
        SwitchRow(
            title = "Accepting responses",
            subtitle = "Visible and open to members",
            checked = form.isActive,
            onCheckedChange = { value -> onEdit { it.copy(isActive = value) } },
        )
    }

    SectionCard {
        SectionCardHeader("Who can submit", Icons.Default.VerifiedUser)
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No required role",
            selectedId = form.requiredRoleId,
            onSelect = { value -> onEdit { it.copy(requiredRoleId = value) } },
            label = "Required role",
        )
        MewdekoTextField(
            value = form.minAccountAgeDays?.toString().orEmpty(),
            onValueChange = { value -> onEdit { it.copy(minAccountAgeDays = value.toIntOrNull()) } },
            label = "Minimum account age (days)",
            placeholder = "No minimum",
            numeric = true,
        )
        SwitchRow(
            title = "Allow outside users",
            subtitle = "Accept submissions from people outside the server",
            checked = form.allowExternalUsers,
            onCheckedChange = { value -> onEdit { it.copy(allowExternalUsers = value) } },
        )
        SwitchRow(
            title = "Allow anonymous",
            checked = form.allowAnonymous,
            onCheckedChange = { value -> onEdit { it.copy(allowAnonymous = value) } },
        )
    }

    SectionCard {
        SectionCardHeader("When it is open", Icons.Default.Schedule)
        DateTimeField(
            label = "Opens at",
            value = form.opensAt,
            onChange = { value -> onEdit { it.copy(opensAt = value) } },
        )
        DateTimeField(
            label = "Closes at",
            value = form.expiry,
            onChange = { value -> onEdit { it.copy(expiresAt = value) } },
        )
        MewdekoTextField(
            value = form.maxResponses?.toString().orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(maxResponses = value.toIntOrNull()?.takeIf { max -> max > 0 }) }
            },
            label = "Max responses",
            placeholder = "Unlimited",
            numeric = true,
        )
        SwitchRow(
            title = "Allow multiple submissions",
            checked = form.allowMultipleSubmissions,
            onCheckedChange = { value -> onEdit { it.copy(allowMultipleSubmissions = value) } },
        )
        SwitchRow(
            title = "Allow resubmit after rejection",
            checked = form.allowResubmitAfterRejection,
            onCheckedChange = { value -> onEdit { it.copy(allowResubmitAfterRejection = value) } },
        )
        SwitchRow(
            title = "Require captcha",
            checked = form.requireCaptcha,
            onCheckedChange = { value -> onEdit { it.copy(requireCaptcha = value) } },
        )
    }

    SectionCard {
        SectionCardHeader("Launch announcement", Icons.Default.NotificationsActive)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No announcement",
            selectedId = form.announceChannelId,
            onSelect = { value -> onEdit { it.copy(announceChannelId = value) } },
            label = "Announce channel",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No ping",
            selectedId = form.announceRoleId,
            onSelect = { value -> onEdit { it.copy(announceRoleId = value) } },
            label = "Announce ping role",
        )
        MewdekoTextField(
            value = form.announceMessage.orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(announceMessage = value.takeIf { text -> text.isNotEmpty() }) }
            },
            label = "Announcement text",
            singleLine = false,
            minLines = 2,
        )
        form.announcedAt?.let {
            InfoRow(label = "Announced at", value = timestamp(it))
        }
    }

    SectionCard {
        SectionCardHeader("When someone submits", Icons.Default.Tag)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channelOptions,
            placeholder = "No channel",
            selectedId = form.submitChannelId,
            onSelect = { value -> onEdit { it.copy(submitChannelId = value) } },
            label = "Submit channel",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No ping",
            selectedId = form.notifyRoleId,
            onSelect = { value -> onEdit { it.copy(notifyRoleId = value) } },
            label = "Ping role on submit",
        )
        DiscordSelector(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No roles",
            label = "Roles given on submit",
            multiple = true,
            selection = form.submitRoles,
            onSelectionChange = { values -> onEdit { it.copy(submitRoleIds = values.packIds()) } },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "No pending role",
            selectedId = form.pendingRoleId,
            onSelect = { value -> onEdit { it.copy(pendingRoleId = value) } },
            label = "Pending role while awaiting review",
        )
        MewdekoTextField(
            value = form.notificationWebhookUrl.orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(notificationWebhookUrl = value.takeIf { text -> text.isNotEmpty() }) }
            },
            label = "Notification webhook URL",
            placeholder = "https://discord.com/api/webhooks/...",
        )
    }

    SectionCard {
        SectionCardHeader("Reviewing", Icons.Default.VerifiedUser)
        SwitchRow(
            title = "Answers need approving",
            subtitle = "Submissions wait for a reviewer decision",
            checked = form.requireApproval,
            onCheckedChange = { value -> onEdit { it.copy(requireApproval = value) } },
        )
        if (form.reviewsResponses) {
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "Manage Server",
                selectedId = form.reviewerRoleId,
                onSelect = { value -> onEdit { it.copy(reviewerRoleId = value) } },
                label = "Reviewer role",
            )
            EmoteField(
                label = "Approve",
                value = form.approveEmote.orEmpty(),
                emojiOptions = emojiOptions,
                placeholder = state.guildReviewEmotes.approveEmote?.takeIf { it.isNotBlank() }
                    ?: "✅ server default",
                onValueChange = { value ->
                    onEdit { it.copy(approveEmote = value.takeIf { text -> text.isNotEmpty() }) }
                },
            )
            EmoteField(
                label = "Reject",
                value = form.rejectEmote.orEmpty(),
                emojiOptions = emojiOptions,
                placeholder = state.guildReviewEmotes.rejectEmote?.takeIf { it.isNotBlank() }
                    ?: "❌ server default",
                onValueChange = { value ->
                    onEdit { it.copy(rejectEmote = value.takeIf { text -> text.isNotEmpty() }) }
                },
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles",
                label = "Roles added on approval",
                multiple = true,
                selection = form.approvalAddRoles,
                onSelectionChange = { values -> onEdit { it.copy(approvalAddRoleIds = values.packIds()) } },
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles",
                label = "Roles removed on approval",
                multiple = true,
                selection = form.approvalRemoveRoles,
                onSelectionChange = { values -> onEdit { it.copy(approvalRemoveRoleIds = values.packIds()) } },
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles",
                label = "Roles added on rejection",
                multiple = true,
                selection = form.rejectionAddRoles,
                onSelectionChange = { values -> onEdit { it.copy(rejectionAddRoleIds = values.packIds()) } },
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles",
                label = "Roles removed on rejection",
                multiple = true,
                selection = form.rejectionRemoveRoles,
                onSelectionChange = { values -> onEdit { it.copy(rejectionRemoveRoleIds = values.packIds()) } },
            )
        }
    }

    if (form.type == FormType.BAN_APPEAL) {
        SectionCard {
            SectionCardHeader("Appeal limits", Icons.Default.Gavel)
            SwitchRow(
                title = "One rejection is final",
                subtitle = "A rejected appellant may never appeal again",
                checked = form.blockReappealAfterRejection,
                onCheckedChange = { value -> onEdit { it.copy(blockReappealAfterRejection = value) } },
            )
            MewdekoTextField(
                value = form.appealDelayDays?.toString().orEmpty(),
                onValueChange = { value -> onEdit { it.copy(appealDelayDays = value.toIntOrNull()) } },
                label = "Wait after ban (days)",
                placeholder = "None",
                numeric = true,
            )
            MewdekoTextField(
                value = form.maxAppealAttempts?.toString().orEmpty(),
                onValueChange = { value -> onEdit { it.copy(maxAppealAttempts = value.toIntOrNull()) } },
                label = "Max appeals",
                placeholder = "Unlimited",
                numeric = true,
            )
            MewdekoTextField(
                value = form.reappealCooldownDays?.toString().orEmpty(),
                onValueChange = { value -> onEdit { it.copy(reappealCooldownDays = value.toIntOrNull()) } },
                label = "Reappeal cooldown (days)",
                placeholder = "None",
                numeric = true,
            )
        }
    }

    if (form.type == FormType.JOIN_APPLICATION) {
        SectionCard {
            SectionCardHeader("Invite", Icons.Default.PersonAddAlt)
            MewdekoTextField(
                value = form.inviteMaxUses?.toString().orEmpty(),
                onValueChange = { value -> onEdit { it.copy(inviteMaxUses = value.toIntOrNull()) } },
                label = "Invite uses",
                placeholder = "1",
                numeric = true,
            )
            MewdekoTextField(
                value = form.inviteMaxAge?.toString().orEmpty(),
                onValueChange = { value -> onEdit { it.copy(inviteMaxAge = value.toIntOrNull()) } },
                label = "Invite lifetime (seconds)",
                placeholder = "Never expires",
                numeric = true,
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No roles",
                label = "Auto-approve roles on join",
                multiple = true,
                selection = form.autoApproveRoles,
                onSelectionChange = { values -> onEdit { it.copy(autoApproveRoleIds = values.packIds()) } },
            )
        }
    }

    SectionCard {
        SectionCardHeader("Success message", Icons.Default.CheckCircle)
        MewdekoTextField(
            value = form.successMessage.orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(successMessage = value.takeIf { text -> text.isNotEmpty() }) }
            },
            label = "Message",
            singleLine = false,
            minLines = 3,
            supportingText = "Shown after submission. Leave blank for the default.",
        )
    }
}

@Composable
private fun DateTimeField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val parsed = value?.let { InstantParser.parse(it) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val base = parsed?.let { ZonedDateTime.ofInstant(it, ZoneId.systemDefault()) }
                        ?: ZonedDateTime.now()
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val zoned = ZonedDateTime.of(
                                        year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault(),
                                    )
                                    val utc = LocalDateTime.ofInstant(zoned.toInstant(), ZoneOffset.UTC)
                                    onChange(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(utc))
                                },
                                base.hour,
                                base.minute,
                                false,
                            ).show()
                        },
                        base.year,
                        base.monthValue - 1,
                        base.dayOfMonth,
                    ).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(parsed?.relativeToNow() ?: "Not set")
            }
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear $label")
                }
            }
        }
    }
}

/**
 * The question builder for one page at a time.
 *
 * A page is the section break heading it (if any) plus every question up to the next one, matched
 * to the dashboard's `FormPageBar` and `formPages.ts`: a tab strip when there is more than one
 * page, the current page's heading (or a way to give the first page one), and controls to add a
 * question or a whole new page without ever having to save in between.
 */
@Composable
private fun FormQuestionsSection(
    state: FormsState,
    onAddQuestion: (FormQuestionType) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onMove: (Int, Boolean) -> Unit,
    onSetActivePage: (Int) -> Unit,
    onAddPage: () -> Unit,
    onMovePage: (Boolean) -> Unit,
    onRemovePage: () -> Unit,
    onAddHeading: () -> Unit,
    onUpdateHeading: (Int, (FormQuestion) -> FormQuestion) -> Unit,
) {
    val pages = remember(state.questions) { formPages(state.questions) }
    val activePage = state.activePage.coerceIn(0, maxOf(0, pages.lastIndex))
    /** Never null: [formPages] always returns at least one page, and [activePage] is clamped into range. */
    val currentPage = pages[activePage]

    if (pages.size > 1) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pages.forEachIndexed { index, _ ->
                FilterChip(
                    selected = index == activePage,
                    onClick = { onSetActivePage(index) },
                    label = { Text("${index + 1}. ${formPageLabel(pages, index)}") },
                )
            }
        }
    }

    val heading = currentPage.heading
    if (heading != null) {
        SectionCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Page ${activePage + 1} heading",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMovePage(false) }, enabled = activePage > 0) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Move page back")
                }
                IconButton(onClick = { onMovePage(true) }, enabled = activePage < pages.lastIndex) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Move page forward")
                }
                IconButton(onClick = onRemovePage) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove page",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            MewdekoTextField(
                value = heading.questionText,
                onValueChange = { value ->
                    onUpdateHeading(currentPage.headingIndex) { it.copy(questionText = value) }
                },
                label = "Page title",
            )
            MewdekoTextField(
                value = heading.placeholder.orEmpty(),
                onValueChange = { value ->
                    onUpdateHeading(currentPage.headingIndex) {
                        it.copy(placeholder = value.takeIf { text -> text.isNotEmpty() })
                    }
                },
                label = "Page description",
                singleLine = false,
                minLines = 2,
            )
        }
    } else {
        OutlinedButton(onClick = onAddHeading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add a heading to this page", modifier = Modifier.padding(start = 8.dp))
        }
    }

    var showTypeMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(onClick = { showTypeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Add question", modifier = Modifier.padding(start = 8.dp))
            }
            DropdownMenu(expanded = showTypeMenu, onDismissRequest = { showTypeMenu = false }) {
                FormQuestionType.entries.filter { it != FormQuestionType.SECTION_BREAK }.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        leadingIcon = { Icon(type.icon, contentDescription = null) },
                        onClick = { showTypeMenu = false; onAddQuestion(type) },
                    )
                }
            }
        }
        OutlinedButton(onClick = onAddPage, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Add page", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (state.questions.isEmpty()) {
        SectionCard {
            EmptyState(
                message = if (state.questionsLoading) {
                    "Loading questions…"
                } else {
                    "No questions yet. Add one to start collecting answers."
                },
                icon = Icons.AutoMirrored.Filled.ListAlt,
            )
        }
        return
    }

    val pageQuestionIndices = currentPage.questionIndices
    if (pageQuestionIndices.isEmpty()) {
        SectionCard {
            EmptyState(message = "No questions on this page yet.", icon = Icons.AutoMirrored.Filled.ListAlt)
        }
    }

    pageQuestionIndices.forEachIndexed { posInPage, flatIndex ->
        val question = state.questions[flatIndex]
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        question.type.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(6.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        question.questionText.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        buildString {
                            append(question.type.label)
                            if (question.isRequired) append(" • Required")
                            if (question.isConditional) append(" • Conditional")
                            if (question.isConditionallyRequired) append(" • Conditionally required")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            question.options.takeIf { it.isNotEmpty() }?.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        if (question.type == FormQuestionType.CHECKBOXES) {
                            Icons.Default.CheckBox
                        } else {
                            Icons.Default.RadioButtonChecked
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(option.optionText, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onMove(flatIndex, false) }, enabled = posInPage > 0) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(
                    onClick = { onMove(flatIndex, true) },
                    enabled = posInPage < pageQuestionIndices.lastIndex,
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = { onDuplicate(flatIndex) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                }
                IconButton(onClick = { onEdit(flatIndex) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit question")
                }
                IconButton(onClick = { onDelete(flatIndex) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete question",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/**
 * Full screen editor for one question or page break. Questions carry an
 * option list and conditional logic, so this is a pushed editor rather than
 * a sheet; a page break only has a title and description.
 */
@Composable
private fun QuestionEditor(
    question: FormQuestion,
    roles: List<SelectorOption>,
    otherQuestions: List<FormQuestion>,
    onDismiss: () -> Unit,
    onSave: (FormQuestion) -> Unit,
) {
    var working by remember(question) { mutableStateOf(question) }
    val isBreak = working.type == FormQuestionType.SECTION_BREAK

    FullScreenEditor(
        title = if (isBreak) "Edit page" else "Edit question",
        onClose = onDismiss,
        confirmLabel = "Done",
        confirmEnabled = isBreak || working.questionText.isNotBlank(),
        onConfirm = { onSave(working) },
        hasUnsavedChanges = working != question,
    ) {
        MewdekoTextField(
            value = working.questionText,
            onValueChange = { working = working.copy(questionText = it) },
            label = if (isBreak) "Page title" else "Question text",
            singleLine = false,
            minLines = if (isBreak) 1 else 2,
        )

        if (isBreak) {
            MewdekoTextField(
                value = working.placeholder.orEmpty(),
                onValueChange = {
                    working = working.copy(placeholder = it.takeIf { text -> text.isNotEmpty() })
                },
                label = "Page description",
                singleLine = false,
                minLines = 2,
            )
        } else {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(working.type.icon),
                options = FormQuestionType.entries.filter { it != FormQuestionType.SECTION_BREAK }
                    .map { SelectorOption(it.raw, it.label) },
                placeholder = "Short Text",
                selectedId = working.questionType,
                onSelect = { value ->
                    working = working.copy(questionType = value ?: FormQuestionType.SHORT_TEXT.raw)
                },
                label = "Type",
            )
            SwitchRow(
                title = "Required",
                checked = working.isRequired,
                onCheckedChange = { working = working.copy(isRequired = it) },
            )
            MewdekoTextField(
                value = working.placeholder.orEmpty(),
                onValueChange = {
                    working = working.copy(placeholder = it.takeIf { text -> text.isNotEmpty() })
                },
                label = "Placeholder",
                placeholder = "Optional",
            )
            MewdekoTextField(
                value = working.imageUrl.orEmpty(),
                onValueChange = { working = working.copy(imageUrl = it.takeIf { text -> text.isNotEmpty() }) },
                label = "Image URL",
                placeholder = "Shown above the question",
            )
            working.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                coil.compose.AsyncImage(
                    model = url,
                    contentDescription = "Question image preview",
                    modifier = Modifier.fillMaxWidth().size(120.dp),
                )
            }

            if (working.type.supportsValidation) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (working.type == FormQuestionType.NUMBER) {
                        MewdekoTextField(
                            value = working.minValue?.toString().orEmpty(),
                            onValueChange = { working = working.copy(minValue = it.toIntOrNull()) },
                            label = "Min",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        MewdekoTextField(
                            value = working.maxValue?.toString().orEmpty(),
                            onValueChange = { working = working.copy(maxValue = it.toIntOrNull()) },
                            label = "Max",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        MewdekoTextField(
                            value = working.minLength?.toString().orEmpty(),
                            onValueChange = { working = working.copy(minLength = it.toIntOrNull()) },
                            label = "Min length",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        MewdekoTextField(
                            value = working.maxLength?.toString().orEmpty(),
                            onValueChange = { working = working.copy(maxLength = it.toIntOrNull()) },
                            label = "Max length",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (working.type.supportsOptions) {
                SectionCard {
                    SectionCardHeader("Options", Icons.AutoMirrored.Filled.ListAlt)
                    working.options.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MewdekoTextField(
                                value = option.optionText,
                                onValueChange = { text ->
                                    val updated = working.options.toMutableList()
                                    updated[index] = option.copy(
                                        optionText = text,
                                        optionValue = text,
                                        displayOrder = index,
                                    )
                                    working = working.copy(options = updated)
                                },
                                label = "Option ${index + 1}",
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    val updated = working.options.toMutableList()
                                    updated.removeAt(index)
                                    working = working.copy(options = updated)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove option",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val updated = working.options.toMutableList()
                            updated += FormQuestionOption(questionId = working.id, displayOrder = updated.size)
                            working = working.copy(options = updated)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add option", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            ConditionalLogicCard(
                working = working,
                roles = roles,
                otherQuestions = otherQuestions,
                onChange = { working = it },
            )

            SectionCard {
                SectionCardHeader("Conditionally required", Icons.Default.Tune)
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ListAlt),
                    options = otherQuestions.map {
                        SelectorOption(it.id.toString(), it.questionText.ifEmpty { "Untitled" })
                    },
                    placeholder = "Not conditionally required",
                    selectedId = working.requiredWhenParentQuestionId?.toString(),
                    onSelect = { value ->
                        working = working.copy(requiredWhenParentQuestionId = value?.toIntOrNull())
                    },
                    label = "Depends on question",
                )
                if (working.requiredWhenParentQuestionId != null) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Tune),
                        options = FormConditionalOperator.entries.map { SelectorOption(it.raw, it.label) },
                        placeholder = "Equals",
                        selectedId = working.requiredWhenOperator ?: FormConditionalOperator.EQUALS.raw,
                        onSelect = { value -> working = working.copy(requiredWhenOperator = value) },
                        label = "Operator",
                    )
                    MewdekoTextField(
                        value = working.requiredWhenValue.orEmpty(),
                        onValueChange = {
                            working = working.copy(requiredWhenValue = it.takeIf { v -> v.isNotEmpty() })
                        },
                        label = "Expected answer",
                    )
                }
                SwitchRow(
                    title = "Answer piping",
                    subtitle = "Allow later questions to reference this answer",
                    checked = working.enableAnswerPiping,
                    onCheckedChange = { working = working.copy(enableAnswerPiping = it) },
                )
            }
        }
    }
}

@Composable
private fun ConditionalLogicCard(
    working: FormQuestion,
    roles: List<SelectorOption>,
    otherQuestions: List<FormQuestion>,
    onChange: (FormQuestion) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Conditional logic", Icons.Default.Tune)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Tune),
            options = FormConditionType.entries.map { SelectorOption(it.raw.toString(), it.label) },
            placeholder = "Always shown",
            selectedId = working.conditionalType.toString(),
            onSelect = { value -> onChange(working.copy(conditionalType = value?.toIntOrNull() ?: 0)) },
            label = "Trigger",
        )

        when (FormConditionType.from(working.conditionalType)) {
            FormConditionType.QUESTION_BASED -> {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ListAlt),
                    options = otherQuestions.map {
                        SelectorOption(it.id.toString(), it.questionText.ifEmpty { "Untitled" })
                    },
                    placeholder = "Always shown",
                    selectedId = working.conditionalParentQuestionId?.toString(),
                    onSelect = { value ->
                        onChange(working.copy(conditionalParentQuestionId = value?.toIntOrNull()))
                    },
                    label = "Depends on question",
                )
                if (working.conditionalParentQuestionId != null) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Tune),
                        options = FormConditionalOperator.entries.map { SelectorOption(it.raw, it.label) },
                        placeholder = "Equals",
                        selectedId = working.conditionalOperator ?: FormConditionalOperator.EQUALS.raw,
                        onSelect = { value -> onChange(working.copy(conditionalOperator = value)) },
                        label = "Operator",
                    )
                    MewdekoTextField(
                        value = working.conditionalExpectedValue.orEmpty(),
                        onValueChange = {
                            onChange(working.copy(conditionalExpectedValue = it.takeIf { v -> v.isNotEmpty() }))
                        },
                        label = "Expected answer",
                    )
                }
            }

            FormConditionType.DISCORD_ROLE -> {
                DiscordSelector(
                    kind = SelectorKind.Role,
                    options = roles,
                    placeholder = "Pick roles",
                    label = "Visible to roles",
                    multiple = true,
                    selection = working.visibleToRoles,
                    onSelectionChange = { values -> onChange(working.copy(conditionalRoleIds = values.packIds())) },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Tune),
                    options = FormRoleLogic.entries.map { SelectorOption(it.raw, it.label) },
                    placeholder = "Any of",
                    selectedId = working.conditionalRoleLogic ?: FormRoleLogic.ANY.raw,
                    onSelect = { value -> onChange(working.copy(conditionalRoleLogic = value)) },
                    label = "Match",
                )
            }

            FormConditionType.SERVER_TENURE -> {
                MewdekoTextField(
                    value = working.conditionalDaysInServer?.toString().orEmpty(),
                    onValueChange = { onChange(working.copy(conditionalDaysInServer = it.toIntOrNull())) },
                    label = "Min days in server",
                    numeric = true,
                )
                MewdekoTextField(
                    value = working.conditionalAccountAgeDays?.toString().orEmpty(),
                    onValueChange = { onChange(working.copy(conditionalAccountAgeDays = it.toIntOrNull())) },
                    label = "Min account age (days)",
                    numeric = true,
                )
            }

            FormConditionType.BOOST_STATUS -> {
                SwitchRow(
                    title = "Requires server boost",
                    checked = working.conditionalRequiresBoost == true,
                    onCheckedChange = { onChange(working.copy(conditionalRequiresBoost = it)) },
                )
                SwitchRow(
                    title = "Requires Nitro",
                    checked = working.conditionalRequiresNitro == true,
                    onCheckedChange = { onChange(working.copy(conditionalRequiresNitro = it)) },
                )
            }

            FormConditionType.PERMISSION -> {
                DiscordSelector(
                    kind = SelectorKind.Custom(Icons.Default.VerifiedUser),
                    options = FormPermissionFlags.map { SelectorOption(it.value.toString(), it.label) },
                    placeholder = "Pick permissions",
                    label = "Requires any of these permissions",
                    multiple = true,
                    selection = working.conditionalPermissionFlags.toPermissionSelection(),
                    onSelectionChange = { values ->
                        onChange(working.copy(conditionalPermissionFlags = values.toPermissionMask()))
                    },
                )
            }

            FormConditionType.MULTIPLE_CONDITIONS -> MultiConditionEditor(
                conditions = working.conditions,
                roles = roles,
                otherQuestions = otherQuestions,
                onChange = { onChange(working.copy(conditions = it)) },
            )
        }
    }
}

@Composable
private fun MultiConditionEditor(
    conditions: List<FormQuestionCondition>,
    roles: List<SelectorOption>,
    otherQuestions: List<FormQuestion>,
    onChange: (List<FormQuestionCondition>) -> Unit,
) {
    val groups = conditions.groupBy { it.conditionGroup }.toSortedMap()

    Text(
        "Any group makes the question visible. Every condition inside a group must agree, chained by its own AND/OR.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    groups.forEach { (group, groupConditions) ->
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Group ${group + 1}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(conditions.filterNot { it.conditionGroup == group }) }) {
                Icon(Icons.Default.Delete, contentDescription = "Remove group", tint = MaterialTheme.colorScheme.error)
            }
        }
        groupConditions.forEachIndexed { indexInGroup, condition ->
            val conditionIndex = conditions.indexOf(condition)
            if (indexInGroup > 0) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Tune),
                    options = FormConditionLogicType.entries.map { SelectorOption(it.raw, it.raw) },
                    placeholder = "AND",
                    selectedId = condition.logicType,
                    onSelect = { value ->
                        onChange(conditions.replaceAt(conditionIndex, condition.copy(logicType = value ?: "AND")))
                    },
                    label = null,
                )
            }
            SectionCard {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Tune),
                    options = FormConditionType.entries.filter { it != FormConditionType.MULTIPLE_CONDITIONS }
                        .map { SelectorOption(it.raw.toString(), it.label) },
                    placeholder = "Answer-based",
                    selectedId = condition.conditionType.toString(),
                    onSelect = { value ->
                        onChange(
                            conditions.replaceAt(
                                conditionIndex,
                                condition.copy(conditionType = value?.toIntOrNull() ?: 0),
                            )
                        )
                    },
                    label = "Type",
                )
                when (FormConditionType.from(condition.conditionType)) {
                    FormConditionType.QUESTION_BASED -> {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.ListAlt),
                            options = otherQuestions.map {
                                SelectorOption(it.id.toString(), it.questionText.ifEmpty { "Untitled" })
                            },
                            placeholder = "Pick a question",
                            selectedId = condition.targetQuestionId?.toString(),
                            onSelect = { value ->
                                onChange(
                                    conditions.replaceAt(
                                        conditionIndex,
                                        condition.copy(targetQuestionId = value?.toIntOrNull()),
                                    )
                                )
                            },
                            label = "Question",
                        )
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.Tune),
                            options = FormConditionalOperator.entries.map { SelectorOption(it.raw, it.label) },
                            placeholder = "Equals",
                            selectedId = condition.operator ?: FormConditionalOperator.EQUALS.raw,
                            onSelect = { value ->
                                onChange(conditions.replaceAt(conditionIndex, condition.copy(operator = value)))
                            },
                            label = "Operator",
                        )
                        MewdekoTextField(
                            value = condition.expectedValue.orEmpty(),
                            onValueChange = { value ->
                                onChange(
                                    conditions.replaceAt(
                                        conditionIndex,
                                        condition.copy(expectedValue = value.takeIf { it.isNotEmpty() }),
                                    )
                                )
                            },
                            label = "Expected answer",
                        )
                    }

                    FormConditionType.DISCORD_ROLE -> DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roles,
                        placeholder = "Pick roles",
                        label = "Has any of these roles",
                        multiple = true,
                        selection = condition.roles,
                        onSelectionChange = { values ->
                            onChange(
                                conditions.replaceAt(conditionIndex, condition.copy(targetRoleIds = values.packIds()))
                            )
                        },
                    )

                    FormConditionType.SERVER_TENURE -> MewdekoTextField(
                        value = condition.daysThreshold?.toString().orEmpty(),
                        onValueChange = { value ->
                            onChange(
                                conditions.replaceAt(
                                    conditionIndex,
                                    condition.copy(daysThreshold = value.toIntOrNull()),
                                )
                            )
                        },
                        label = "Min days",
                        numeric = true,
                    )

                    FormConditionType.BOOST_STATUS -> {
                        SwitchRow(
                            title = "Requires boost",
                            checked = condition.requiresBoost == true,
                            onCheckedChange = { value ->
                                onChange(
                                    conditions.replaceAt(conditionIndex, condition.copy(requiresBoost = value))
                                )
                            },
                        )
                        SwitchRow(
                            title = "Requires Nitro",
                            checked = condition.requiresNitro == true,
                            onCheckedChange = { value ->
                                onChange(
                                    conditions.replaceAt(conditionIndex, condition.copy(requiresNitro = value))
                                )
                            },
                        )
                    }

                    FormConditionType.PERMISSION -> DiscordSelector(
                        kind = SelectorKind.Custom(Icons.Default.VerifiedUser),
                        options = FormPermissionFlags.map { SelectorOption(it.value.toString(), it.label) },
                        placeholder = "Pick permissions",
                        label = "Requires any of these permissions",
                        multiple = true,
                        selection = condition.permissionFlags.toPermissionSelection(),
                        onSelectionChange = { values ->
                            onChange(
                                conditions.replaceAt(
                                    conditionIndex,
                                    condition.copy(permissionFlags = values.toPermissionMask()),
                                )
                            )
                        },
                    )

                    FormConditionType.MULTIPLE_CONDITIONS -> Unit
                }
                TextButton(
                    onClick = { onChange(conditions.filterIndexed { i, _ -> i != conditionIndex }) },
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Remove condition", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        OutlinedButton(
            onClick = {
                onChange(conditions + FormQuestionCondition(conditionGroup = group, logicType = "AND"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add condition to group ${group + 1}") }
    }

    OutlinedButton(
        onClick = {
            val nextGroup = (groups.keys.maxOrNull() ?: -1) + 1
            onChange(conditions + FormQuestionCondition(conditionGroup = nextGroup, logicType = "AND"))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Add condition group (OR)", modifier = Modifier.padding(start = 8.dp))
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { if (index in it.indices) it[index] = value }

private fun Long?.toPermissionSelection(): List<String> =
    (this ?: 0L).let { flags -> FormPermissionFlags.filter { flags and it.value != 0L }.map { it.value.toString() } }

private fun List<String>.toPermissionMask(): Long? =
    fold(0L) { acc, id -> acc or (id.toLongOrNull() ?: 0L) }.takeIf { it != 0L }

@Composable
private fun FormResponsesSection(
    form: Form,
    state: FormsState,
    onFilter: (ResponseStatus?) -> Unit,
    onPage: (Int) -> Unit,
    onToggleExpand: (QueuedResponse) -> Unit,
    onLoadRevisions: (QueuedResponse) -> Unit,
    onApprove: (QueuedResponse, String) -> Unit,
    onReject: (QueuedResponse, String) -> Unit,
    onDelete: (QueuedResponse) -> Unit,
    onExport: () -> Unit,
) {
    val data = state.responses

    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Export CSV", modifier = Modifier.padding(start = 8.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = state.responseFilter == null,
            onClick = { onFilter(null) },
            label = { Text("All${data?.let { " (${it.totalCount})" } ?: ""}") },
        )
        ResponseStatus.entries.forEach { status ->
            FilterChip(
                selected = state.responseFilter == status,
                onClick = { onFilter(status) },
                label = { Text("${status.label} (${data?.count(status) ?: 0})") },
            )
        }
    }

    if (data == null || data.responses.isEmpty()) {
        SectionCard {
            EmptyState(message = "No responses yet.", icon = Icons.Default.Inbox)
        }
        return
    }

    data.responses.forEach { response ->
        ResponseCard(
            form = form,
            response = response,
            expanded = state.expandedResponseId == response.response.id,
            revisions = state.responseRevisions[response.response.id],
            onToggleExpand = { onToggleExpand(response) },
            onLoadRevisions = { onLoadRevisions(response) },
            onApprove = onApprove,
            onReject = onReject,
            onDelete = { onDelete(response) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = { onPage(data.page - 1) }, enabled = data.page > 1) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null)
            Text("Previous", modifier = Modifier.padding(start = 4.dp))
        }
        Text("Page ${data.page} of ${data.totalPages}", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { onPage(data.page + 1) }, enabled = data.page < data.totalPages) {
            Text("Next", modifier = Modifier.padding(end = 4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ResponseCard(
    form: Form,
    response: QueuedResponse,
    expanded: Boolean,
    revisions: List<FormResponseRevision>?,
    onToggleExpand: () -> Unit,
    onLoadRevisions: () -> Unit,
    onApprove: (QueuedResponse, String) -> Unit,
    onReject: (QueuedResponse, String) -> Unit,
    onDelete: () -> Unit,
) {
    var notes by remember(response.response.id) { mutableStateOf("") }
    var reviewing by remember(response.response.id) { mutableStateOf(false) }

    SectionCard(modifier = Modifier.clickableRow { onToggleExpand() }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                response.response.username ?: "Anonymous",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (form.reviewsResponses) ReviewStatusPill(response.state)
        }
        Text(
            "Response #${response.response.id} • ${timestamp(response.response.submittedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (response.response.editedAt != null) TagChip("Edited")
            if (response.workflow?.dmFailed == true) TagChip("DM failed")
            if (response.revisionCount > 0) TagChip("${response.revisionCount} revisions")
            response.workflow?.inviteCode?.takeIf { it.isNotEmpty() }?.let {
                TagChip("Invite $it", icon = Icons.Default.Link)
            }
        }

        if (expanded) {
            response.answers.forEach { answer ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        answer.questionText?.ifEmpty { "Question" } ?: "Question",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        answer.display.ifEmpty { "(empty)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            response.workflow?.reviewNotes?.takeIf { it.isNotEmpty() }?.let {
                Text("Reviewer notes: $it", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedButton(onClick = onLoadRevisions, modifier = Modifier.fillMaxWidth()) {
                Text("View revision history")
            }
            revisions?.forEach { revision ->
                SectionCard {
                    Text(
                        "Edited ${timestamp(revision.createdAt)}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    revision.answers.forEach { answer ->
                        Text(
                            "${answer.questionText.orEmpty()}: ${
                                answer.answerDisplay ?: answer.answerValues?.joinToString(", ")
                                    ?: answer.answerText.orEmpty()
                            }",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (form.reviewsResponses && response.isReviewable) {
                if (reviewing) {
                    MewdekoTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Notes",
                        placeholder = "Required to reject",
                        singleLine = false,
                        minLines = 2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onApprove(response, notes); reviewing = false; notes = "" },
                            modifier = Modifier.weight(1f),
                        ) { Text("Approve") }
                        OutlinedButton(
                            onClick = { onReject(response, notes); reviewing = false; notes = "" },
                            enabled = notes.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Reject") }
                        TextButton(onClick = { reviewing = false; notes = "" }) { Text("Cancel") }
                    }
                } else {
                    OutlinedButton(onClick = { reviewing = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Review", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "Delete response",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewStatusPill(status: ResponseStatus) {
    val scheme = MaterialTheme.colorScheme
    val color = when (status) {
        ResponseStatus.PENDING -> scheme.tertiary
        ResponseStatus.UNDER_REVIEW -> scheme.primary
        ResponseStatus.APPROVED -> scheme.secondary
        ResponseStatus.REJECTED -> scheme.error
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.16f)) {
        Text(
            status.label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FormVersionsSection(
    state: FormsState,
    onLoad: () -> Unit,
    onDiff: (FormVersion) -> Unit,
    onDismissDiff: () -> Unit,
    onRestore: (FormVersion) -> Unit,
) {
    LaunchedEffect(Unit) { if (state.versions == null) onLoad() }

    val versions = state.versions
    if (versions == null || versions.versions.isEmpty()) {
        SectionCard {
            EmptyState(message = "No saved versions yet.", icon = Icons.Default.History)
        }
        return
    }

    Text(
        "Keeps the last ${versions.versionsKept} saves.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    versions.versions.forEach { version ->
        SectionCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Version ${version.versionNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${version.questionCount} questions • ${timestamp(version.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDiff(version) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Compare", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { onRestore(version) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Restore", modifier = Modifier.padding(start = 8.dp))
                }
            }
            val diff = state.versionDiff
            if (diff != null && diff.first == version.versionNumber) {
                val changes = diff.second
                if (changes.isEmpty()) {
                    Text(
                        "No changes from the version before it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    changes.forEach { change ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${change.kind}: ${change.section} • ${change.label}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (change.before != null) {
                                Text("Was: ${change.before}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (change.after != null) {
                                Text("Now: ${change.after}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismissDiff) { Text("Hide comparison") }
            }
        }
    }
}
