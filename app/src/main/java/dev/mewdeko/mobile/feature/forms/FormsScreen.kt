package dev.mewdeko.mobile.feature.forms

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.net.InstantParser
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
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
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

/** The Material icon standing in for each question widget. */
private val FormQuestionType.icon: ImageVector
    get() = when (this) {
        FormQuestionType.SHORT_TEXT -> Icons.Default.ShortText
        FormQuestionType.LONG_TEXT -> Icons.Default.Notes
        FormQuestionType.MULTIPLE_CHOICE -> Icons.Default.RadioButtonChecked
        FormQuestionType.CHECKBOXES -> Icons.Default.CheckBox
        FormQuestionType.DROPDOWN -> Icons.Default.UnfoldMore
        FormQuestionType.NUMBER -> Icons.Default.Numbers
        FormQuestionType.EMAIL -> Icons.Default.Email
        FormQuestionType.URL -> Icons.Default.Link
    }

/** The Material icon for each detail section. */
private val FormSection.icon: ImageVector
    get() = when (this) {
        FormSection.SETTINGS -> Icons.Default.Settings
        FormSection.QUESTIONS -> Icons.Default.ListAlt
        FormSection.RESPONSES -> Icons.Default.Inbox
        FormSection.REVIEW -> Icons.Default.VerifiedUser
    }

/** Renders an ISO timestamp as a relative phrase, falling back to the raw text. */
private fun timestamp(raw: String?): String =
    raw?.let { InstantParser.parse(it)?.relativeToNow() ?: it }.orEmpty()

/** Member-facing forms: build them, collect answers, and review submissions. */
@Composable
fun FormsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: FormsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var pendingFormDelete by remember { mutableStateOf<FormDefinition?>(null) }
    var pendingQuestionDelete by remember { mutableStateOf<FormQuestion?>(null) }
    var editingQuestion by remember { mutableStateOf<FormQuestion?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }

    val selected = state.selected

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
                    onDelete = { pendingFormDelete = selected },
                )
            }
        },
        floatingActionButton = {
            when {
                selected == null -> ExtendedFloatingActionButton(
                    onClick = { newName = ""; creating = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New form") },
                )

                state.section == FormSection.SETTINGS && state.hasUnsavedForm ->
                    ExtendedFloatingActionButton(
                        onClick = viewModel::saveForm,
                        icon = { Icon(Icons.Default.Save, contentDescription = null) },
                        text = { Text("Save changes") },
                    )

                state.section == FormSection.QUESTIONS -> ExtendedFloatingActionButton(
                    onClick = {
                        editingQuestion = FormQuestion.blank(
                            formId = selected.id,
                            displayOrder = state.questions.size,
                        )
                        editingIsNew = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add question") },
                )
            }
        },
    ) {
        if (selected == null) {
            FormList(
                state = state,
                onOpen = viewModel::open,
                onPublish = viewModel::publish,
                onToggleActive = viewModel::toggleActive,
                onDuplicate = viewModel::duplicate,
                onDelete = { pendingFormDelete = it },
                onCreate = { newName = ""; creating = true },
            )
        } else {
            SectionTabs(
                tabs = state.sections.map { SectionTab(it.id, it.label, it.icon) },
                selectedId = state.section.id,
                onSelect = { id ->
                    FormSection.entries.firstOrNull { it.id == id }
                        ?.let(viewModel::setSection)
                },
            )
            when (state.section) {
                FormSection.SETTINGS -> FormSettingsSection(state, selected, viewModel::editForm)
                FormSection.QUESTIONS -> FormQuestionsSection(
                    state = state,
                    onEdit = { editingQuestion = it; editingIsNew = false },
                    onDelete = { pendingQuestionDelete = it },
                )

                FormSection.RESPONSES -> FormResponsesSection(
                    state = state,
                    onOpen = viewModel::openResponse,
                    onPage = viewModel::loadResponses,
                )

                FormSection.REVIEW -> FormReviewSection(
                    state = state,
                    onFilter = viewModel::setReviewFilter,
                    onApprove = viewModel::approve,
                    onReject = viewModel::reject,
                )
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New form") },
            text = {
                MewdekoTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = "Name",
                    placeholder = "Application",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { creating = false; viewModel.createForm(newName) },
                    enabled = newName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            },
        )
    }

    pendingFormDelete?.let { form ->
        ConfirmDialog(
            title = "Delete form?",
            message = "\"${form.name}\" and all ${form.responses} responses will be removed.",
            onConfirm = { pendingFormDelete = null; viewModel.deleteForm(form) },
            onDismiss = { pendingFormDelete = null },
        )
    }

    pendingQuestionDelete?.let { question ->
        ConfirmDialog(
            title = "Delete question?",
            message = "\"${question.questionText.ifEmpty { "Untitled" }}\" and every answer to it will be removed.",
            onConfirm = { pendingQuestionDelete = null; viewModel.deleteQuestion(question) },
            onDismiss = { pendingQuestionDelete = null },
        )
    }

    editingQuestion?.let { question ->
        QuestionEditorSheet(
            question = question,
            isNew = editingIsNew,
            roles = state.availableRoles.map { SelectorOption(it.id, it.name) },
            otherQuestions = state.questions.filter { it.id != question.id },
            onDismiss = { editingQuestion = null },
            onSave = {
                editingQuestion = null
                viewModel.saveQuestion(it, editingIsNew)
            },
        )
    }

    state.responseDetail?.let { detail ->
        ResponseDetailSheet(detail = detail, onDismiss = viewModel::closeResponse)
    }
}

@Composable
private fun FormList(
    state: FormsState,
    onOpen: (FormDefinition) -> Unit,
    onPublish: (FormDefinition) -> Unit,
    onToggleActive: (FormDefinition) -> Unit,
    onDuplicate: (FormDefinition) -> Unit,
    onDelete: (FormDefinition) -> Unit,
    onCreate: () -> Unit,
) {
    if (state.forms.isEmpty()) {
        SectionCard {
            EmptyState(message = "No forms yet.", icon = Icons.Default.Description)
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Create form", modifier = Modifier.padding(start = 8.dp))
            }
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
                    onDelete = { onDelete(form) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagChip("${form.responses} responses", icon = Icons.Default.Inbox)
                if (form.hasWorkflow) {
                    TagChip(form.type.label, icon = Icons.Default.VerifiedUser)
                    if (form.pending > 0) {
                        TagChip("${form.pending} pending", icon = Icons.Default.Alarm)
                    }
                }
                form.expiry?.let { TagChip("Expires ${timestamp(it)}", icon = Icons.Default.Alarm) }
            }
        }
    }
}

@Composable
private fun FormStatusPill(form: FormDefinition) {
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
    form: FormDefinition,
    onPublish: () -> Unit,
    onToggleActive: () -> Unit,
    onDuplicate: () -> Unit,
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
                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null) },
                    onClick = { open = false; onPublish() },
                )
            }
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

@Composable
private fun FormSettingsSection(
    state: FormsState,
    form: FormDefinition,
    onEdit: ((FormDefinition) -> FormDefinition) -> Unit,
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
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Article),
            options = FormType.entries.map { SelectorOption(it.raw.toString(), it.label) },
            placeholder = "Regular",
            selectedId = form.formType.toString(),
            onSelect = { value ->
                onEdit { it.copy(formType = value?.toIntOrNull() ?: 0) }
            },
            label = "Type",
        )
    }

    SectionCard {
        SectionCardHeader("Behavior", Icons.Default.Tune)
        SwitchRow(
            title = "Active",
            subtitle = "Visible to members",
            checked = form.isActive,
            onCheckedChange = { value -> onEdit { it.copy(isActive = value) } },
        )
        SwitchRow(
            title = "Allow multiple submissions",
            checked = form.allowMultipleSubmissions,
            onCheckedChange = { value -> onEdit { it.copy(allowMultipleSubmissions = value) } },
        )
        SwitchRow(
            title = "Allow anonymous",
            checked = form.allowAnonymous,
            onCheckedChange = { value -> onEdit { it.copy(allowAnonymous = value) } },
        )
        SwitchRow(
            title = "Require captcha",
            checked = form.requireCaptcha,
            onCheckedChange = { value -> onEdit { it.copy(requireCaptcha = value) } },
        )
        SwitchRow(
            title = "Allow external users",
            subtitle = "Accept submissions from outside the server",
            checked = form.allowExternalUsers,
            onCheckedChange = { value -> onEdit { it.copy(allowExternalUsers = value) } },
        )
        MewdekoTextField(
            value = form.maxResponses?.toString().orEmpty(),
            onValueChange = { value ->
                onEdit { it.copy(maxResponses = value.toIntOrNull()?.takeIf { max -> max > 0 }) }
            },
            label = "Max responses",
            placeholder = "Unlimited",
            numeric = true,
            supportingText = "Leave blank for no cap.",
        )
    }

    SectionCard {
        SectionCardHeader("Channels & roles", Icons.Default.Tag)
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
            placeholder = "No required role",
            selectedId = form.requiredRoleId,
            onSelect = { value -> onEdit { it.copy(requiredRoleId = value) } },
            label = "Required role",
        )
    }

    if (form.hasWorkflow) {
        SectionCard {
            SectionCardHeader("Workflow", Icons.Default.VerifiedUser)
            SwitchRow(
                title = "Require approval",
                subtitle = "Submissions wait for a reviewer",
                checked = form.requireApproval,
                onCheckedChange = { value -> onEdit { it.copy(requireApproval = value) } },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.CheckCircle),
                options = FormApprovalActionType.entries.map {
                    SelectorOption(it.raw.toString(), it.label)
                },
                placeholder = "No role changes",
                selectedId = form.approvalActionType.toString(),
                onSelect = { value ->
                    onEdit { it.copy(approvalActionType = value?.toIntOrNull() ?: 0) }
                },
                label = "On approval",
            )
            if (form.approvalActionType != FormApprovalActionType.NONE.raw) {
                DiscordSelector(
                    kind = SelectorKind.Role,
                    options = roleOptions,
                    placeholder = "Pick roles",
                    label = "Approval roles",
                    multiple = true,
                    selection = form.approvalRoles,
                    onSelectionChange = { values ->
                        onEdit { it.copy(approvalRoleIds = values.packIds()) }
                    },
                )
            }
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.PauseCircle),
                options = FormApprovalActionType.entries.map {
                    SelectorOption(it.raw.toString(), it.label)
                },
                placeholder = "No role changes",
                selectedId = form.rejectionActionType.toString(),
                onSelect = { value ->
                    onEdit { it.copy(rejectionActionType = value?.toIntOrNull() ?: 0) }
                },
                label = "On rejection",
            )
            if (form.rejectionActionType != FormApprovalActionType.NONE.raw) {
                DiscordSelector(
                    kind = SelectorKind.Role,
                    options = roleOptions,
                    placeholder = "Pick roles",
                    label = "Rejection roles",
                    multiple = true,
                    selection = form.rejectionRoles,
                    onSelectionChange = { values ->
                        onEdit { it.copy(rejectionRoleIds = values.packIds()) }
                    },
                )
            }
            MewdekoTextField(
                value = form.inviteMaxUses?.toString().orEmpty(),
                onValueChange = { value ->
                    onEdit { it.copy(inviteMaxUses = value.toIntOrNull()) }
                },
                label = "Invite uses",
                placeholder = "1",
                numeric = true,
                supportingText = "How many times an approval invite can be redeemed.",
            )
            MewdekoTextField(
                value = form.inviteMaxAge?.toString().orEmpty(),
                onValueChange = { value ->
                    onEdit { it.copy(inviteMaxAge = value.toIntOrNull()) }
                },
                label = "Invite lifetime (seconds)",
                placeholder = "Never expires",
                numeric = true,
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
private fun FormQuestionsSection(
    state: FormsState,
    onEdit: (FormQuestion) -> Unit,
    onDelete: (FormQuestion) -> Unit,
) {
    if (state.questions.isEmpty()) {
        SectionCard {
            EmptyState(
                message = if (state.questionsLoading) {
                    "Loading questions…"
                } else {
                    "No questions yet. Add one to start collecting answers."
                },
                icon = Icons.Default.ListAlt,
            )
        }
        return
    }

    state.questions.forEach { question ->
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
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(question) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit question")
                }
                IconButton(onClick = { onDelete(question) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete question",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            question.options?.takeIf { it.isNotEmpty() }?.forEach { option ->
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestionEditorSheet(
    question: FormQuestion,
    isNew: Boolean,
    roles: List<SelectorOption>,
    otherQuestions: List<FormQuestion>,
    onDismiss: () -> Unit,
    onSave: (FormQuestion) -> Unit,
) {
    var working by remember(question.id, isNew) { mutableStateOf(question) }

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
            Text(
                if (isNew) "Add question" else "Edit question",
                style = MaterialTheme.typography.titleMedium,
            )

            MewdekoTextField(
                value = working.questionText,
                onValueChange = { working = working.copy(questionText = it) },
                label = "Question text",
                singleLine = false,
                minLines = 2,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(working.type.icon),
                options = FormQuestionType.entries.map { SelectorOption(it.raw, it.label) },
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
                            onValueChange = {
                                working = working.copy(minLength = it.toIntOrNull())
                            },
                            label = "Min length",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                        MewdekoTextField(
                            value = working.maxLength?.toString().orEmpty(),
                            onValueChange = {
                                working = working.copy(maxLength = it.toIntOrNull())
                            },
                            label = "Max length",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (working.type.supportsOptions) {
                SectionCard {
                    SectionCardHeader("Options", Icons.Default.ListAlt)
                    working.options.orEmpty().forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MewdekoTextField(
                                value = option.optionText,
                                onValueChange = { text ->
                                    val updated = working.options.orEmpty().toMutableList()
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
                                    val updated = working.options.orEmpty().toMutableList()
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
                            val updated = working.options.orEmpty().toMutableList()
                            updated += FormQuestionOption(
                                questionId = working.id,
                                displayOrder = updated.size,
                            )
                            working = working.copy(options = updated)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Add option", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            SectionCard {
                SectionCardHeader("Conditional logic", Icons.Default.Tune)
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Tune),
                    options = FormConditionType.entries.map {
                        SelectorOption(
                            it.raw.toString(),
                            if (it == FormConditionType.QUESTION_BASED && working.conditionalParentQuestionId == null) {
                                "Always shown"
                            } else {
                                it.label
                            },
                        )
                    },
                    placeholder = "Always shown",
                    selectedId = working.conditionalType.toString(),
                    onSelect = { value ->
                        working = working.copy(conditionalType = value?.toIntOrNull() ?: 0)
                    },
                    label = "Trigger",
                )

                when (FormConditionType.from(working.conditionalType)) {
                    FormConditionType.QUESTION_BASED -> {
                        DiscordSelectorSingle(
                            kind = SelectorKind.Custom(Icons.Default.ListAlt),
                            options = otherQuestions.map {
                                SelectorOption(
                                    it.id.toString(),
                                    it.questionText.ifEmpty { "Untitled" },
                                )
                            },
                            placeholder = "Always shown",
                            selectedId = working.conditionalParentQuestionId?.toString(),
                            onSelect = { value ->
                                working = working.copy(
                                    conditionalParentQuestionId = value?.toIntOrNull()
                                )
                            },
                            label = "Depends on question",
                        )
                        if (working.conditionalParentQuestionId != null) {
                            DiscordSelectorSingle(
                                kind = SelectorKind.Custom(Icons.Default.Tune),
                                options = FormConditionalOperator.entries.map {
                                    SelectorOption(it.raw, it.label)
                                },
                                placeholder = "Equals",
                                selectedId = working.conditionalOperator
                                    ?: FormConditionalOperator.EQUALS.raw,
                                onSelect = { value ->
                                    working = working.copy(conditionalOperator = value)
                                },
                                label = "Operator",
                            )
                            MewdekoTextField(
                                value = working.conditionalExpectedValue.orEmpty(),
                                onValueChange = {
                                    working = working.copy(
                                        conditionalExpectedValue = it.takeIf { v -> v.isNotEmpty() }
                                    )
                                },
                                label = "Expected answer",
                            )
                        } else {
                            Text(
                                "Always shown when the form reaches this question.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    FormConditionType.DISCORD_ROLE -> DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roles,
                        placeholder = "Pick roles",
                        label = "Visible to roles",
                        multiple = true,
                        selection = working.conditionalRoleIds
                            ?.split(',', ' ')
                            ?.map { it.trim() }
                            ?.filter { it.isNotEmpty() }
                            .orEmpty(),
                        onSelectionChange = { values ->
                            working = working.copy(conditionalRoleIds = values.packIds())
                        },
                    )

                    FormConditionType.SERVER_TENURE -> {
                        MewdekoTextField(
                            value = working.conditionalDaysInServer?.toString().orEmpty(),
                            onValueChange = {
                                working = working.copy(conditionalDaysInServer = it.toIntOrNull())
                            },
                            label = "Min days in server",
                            numeric = true,
                        )
                        MewdekoTextField(
                            value = working.conditionalAccountAgeDays?.toString().orEmpty(),
                            onValueChange = {
                                working = working.copy(conditionalAccountAgeDays = it.toIntOrNull())
                            },
                            label = "Min account age (days)",
                            numeric = true,
                        )
                    }

                    FormConditionType.BOOST_STATUS -> {
                        SwitchRow(
                            title = "Requires server boost",
                            checked = working.conditionalRequiresBoost == true,
                            onCheckedChange = {
                                working = working.copy(conditionalRequiresBoost = it)
                            },
                        )
                        SwitchRow(
                            title = "Requires Nitro",
                            checked = working.conditionalRequiresNitro == true,
                            onCheckedChange = {
                                working = working.copy(conditionalRequiresNitro = it)
                            },
                        )
                    }

                    FormConditionType.PERMISSION,
                    FormConditionType.MULTIPLE_CONDITIONS,
                    -> Text(
                        "Configured on the web dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SwitchRow(
                    title = "Answer piping",
                    subtitle = "Allow later questions to reference this answer",
                    checked = working.enableAnswerPiping,
                    onCheckedChange = { working = working.copy(enableAnswerPiping = it) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(working) },
                    enabled = working.questionText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun FormResponsesSection(
    state: FormsState,
    onOpen: (FormResponseRecord) -> Unit,
    onPage: (Int) -> Unit,
) {
    val data = state.responses
    if (data == null || data.responses.isEmpty()) {
        SectionCard {
            EmptyState(message = "No responses yet.", icon = Icons.Default.Inbox)
        }
        return
    }

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Page ${data.page} of ${data.totalPages}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${data.totalCount} total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    data.responses.forEach { response ->
        SectionCard(modifier = Modifier.clickableRow { onOpen(response) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        response.username ?: "User ${response.userId}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Response #${response.id} • ${timestamp(response.submittedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { onPage(data.page - 1) },
            enabled = data.page > 1,
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = null)
            Text("Previous", modifier = Modifier.padding(start = 4.dp))
        }
        OutlinedButton(
            onClick = { onPage(data.page + 1) },
            enabled = data.page < data.totalPages,
        ) {
            Text("Next", modifier = Modifier.padding(end = 4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseDetailSheet(detail: FormResponseDetail, onDismiss: () -> Unit) {
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
            Text(
                "Response #${detail.response.id}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${detail.response.username ?: detail.response.userId} • ${timestamp(detail.response.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (detail.answers.isEmpty()) {
                EmptyState(message = "No answers recorded.", icon = Icons.Default.Inbox)
            }
            detail.answers.forEach { answer ->
                SectionCard {
                    Text(
                        answer.question?.questionText?.ifEmpty { "Question" } ?: "Question",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val values = answer.answerValues.orEmpty()
                    when {
                        values.isNotEmpty() -> values.forEach {
                            Text("• $it", style = MaterialTheme.typography.bodyMedium)
                        }

                        !answer.answerText.isNullOrEmpty() ->
                            Text(answer.answerText, style = MaterialTheme.typography.bodyMedium)

                        else -> Text(
                            "(empty)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormReviewSection(
    state: FormsState,
    onFilter: (ResponseStatus?) -> Unit,
    onApprove: (FormResponseWithWorkflow, String) -> Unit,
    onReject: (FormResponseWithWorkflow, String) -> Unit,
) {
    var actingId by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = state.reviewFilter == null,
            onClick = { onFilter(null) },
            label = { Text("All") },
        )
        ResponseStatus.entries.forEach { status ->
            FilterChip(
                selected = state.reviewFilter == status,
                onClick = { onFilter(status) },
                label = { Text(status.label) },
            )
        }
    }

    if (state.review.isEmpty()) {
        SectionCard {
            EmptyState(message = "Nothing waiting on review.", icon = Icons.Default.VerifiedUser)
        }
        return
    }

    state.review.forEach { entry ->
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.response.username ?: "User ${entry.response.userId}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ReviewStatusPill(entry.workflow.state)
            }
            Text(
                "Response #${entry.response.id} • ${timestamp(entry.response.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.workflow.reviewNotes?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            entry.workflow.inviteCode?.takeIf { it.isNotEmpty() }?.let {
                TagChip("Invite $it", icon = Icons.Default.Link)
            }

            val reviewable = entry.workflow.state == ResponseStatus.PENDING ||
                entry.workflow.state == ResponseStatus.UNDER_REVIEW
            if (reviewable) {
                if (actingId == entry.id) {
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
                            onClick = {
                                onApprove(entry, notes)
                                actingId = null
                                notes = ""
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Approve") }
                        OutlinedButton(
                            onClick = {
                                onReject(entry, notes)
                                actingId = null
                                notes = ""
                            },
                            enabled = notes.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) { Text("Reject") }
                        TextButton(onClick = { actingId = null; notes = "" }) { Text("Cancel") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { actingId = entry.id; notes = "" },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("Review", modifier = Modifier.padding(start = 8.dp))
                    }
                }
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
