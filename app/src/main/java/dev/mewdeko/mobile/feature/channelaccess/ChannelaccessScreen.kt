package dev.mewdeko.mobile.feature.channelaccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
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
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDateTime

private val Tabs = listOf(
    SectionTab("gates", "Gates", Icons.Default.Lock),
    SectionTab("applications", "Applications", Icons.Default.Inbox),
    SectionTab("blocked", "Blocked", Icons.Default.Block),
)

private val GrantModeOptions = AccessGrantMode.entries.map { SelectorOption(it.value.toString(), it.label) }
private val ExpiryOptions = AccessExpiryBehavior.entries.map { SelectorOption(it.value.toString(), it.label) }
private val StatusFilterOptions = listOf(SelectorOption(FilterAll, "Any status")) +
    AccessApplicationStatus.entries.map { SelectorOption(it.value.toString(), it.label) }

/** Colour used for approvals and approved applications. */
private val ApprovedGreen = Color(0xFF10B981)

/** A question removal waiting on confirmation. */
private data class PendingQuestionRemoval(
    val gate: ChannelAccessGate,
    val position: Int,
    val text: String,
)

/**
 * Channel Access: gates on locked channels with their voting rules and application forms, the
 * applications members submit, and the list of people barred from applying.
 */
@Composable
fun ChannelaccessScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ChannelAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ChannelAccessGate?>(null) }
    var pendingQuestion by remember { mutableStateOf<PendingQuestionRemoval?>(null) }

    FeatureScaffold(
        title = "Channel Access",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load(refreshing = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "gates" -> GatesSection(
                state = state,
                viewModel = viewModel,
                onDelete = { pendingDelete = it },
                onRemoveQuestion = { gate, position, text ->
                    pendingQuestion = PendingQuestionRemoval(gate, position, text)
                },
            )

            "applications" -> ApplicationsSection(state, viewModel)
            "blocked" -> BlockedSection(state, viewModel)
        }
    }

    pendingDelete?.let { gate ->
        ConfirmDialog(
            title = "Delete this gate?",
            message = "#${state.channelName(gate.channelId)} stops taking applications, and its questions, " +
                "applications and votes are deleted. Nobody loses access they already have.",
            confirmLabel = "Delete gate",
            onConfirm = {
                pendingDelete = null
                viewModel.deleteGate(gate)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingQuestion?.let { removal ->
        ConfirmDialog(
            title = "Remove this question?",
            message = "\"${removal.text}\" is taken off the application form. Answers already given stay on " +
                "their applications.",
            confirmLabel = "Remove",
            onConfirm = {
                pendingQuestion = null
                viewModel.removeQuestion(removal.gate, removal.position)
            },
            onDismiss = { pendingQuestion = null },
        )
    }
}

@Composable
private fun GatesSection(
    state: ChannelAccessState,
    viewModel: ChannelAccessViewModel,
    onDelete: (ChannelAccessGate) -> Unit,
    onRemoveQuestion: (ChannelAccessGate, Int, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Gates", state.gates.size.toString(), Modifier.weight(1f), icon = Icons.Default.Lock)
        StatTile("Open applications", state.pendingCount.toString(), Modifier.weight(1f), icon = Icons.Default.Inbox)
        StatTile("Blocked users", state.blacklist.size.toString(), Modifier.weight(1f), icon = Icons.Default.PersonOff)
    }

    SectionCard {
        SectionCardHeader("Open applications for a channel", Icons.Default.Add)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.ungatedChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Select a channel",
            label = "Locked channel",
            selectedId = state.newGateChannelId,
            onSelect = viewModel::setNewGateChannel,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Key),
            options = GrantModeOptions,
            placeholder = "Pick how access is granted",
            label = "How people get in",
            selectedId = state.newGateGrantMode.value.toString(),
            onSelect = { value ->
                viewModel.setNewGateGrantMode(AccessGrantMode.from(value?.toIntOrNull() ?: 0))
            },
        )
        if (state.newGateGrantMode == AccessGrantMode.ROLE) {
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.roles.map { SelectorOption(it.id, it.name) },
                placeholder = "Select a role",
                label = "Role granted on approval",
                selectedId = state.newGateRoleId,
                onSelect = viewModel::setNewGateRole,
            )
        }
        Button(
            onClick = viewModel::createGate,
            enabled = !state.isCreatingGate,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isCreatingGate) "Creating…" else "Create gate")
        }
    }

    if (state.gates.isEmpty()) {
        SectionCard {
            EmptyState(
                "No gates yet. Pick a locked channel above to start taking applications.",
                icon = Icons.Default.Lock,
            )
        }
    } else {
        state.gates.forEach { gate ->
            GateCard(
                gate = gate,
                state = state,
                viewModel = viewModel,
                onDelete = { onDelete(gate) },
                onRemoveQuestion = { position, text -> onRemoveQuestion(gate, position, text) },
            )
        }
    }
}

@Composable
private fun GateCard(
    gate: ChannelAccessGate,
    state: ChannelAccessState,
    viewModel: ChannelAccessViewModel,
    onDelete: () -> Unit,
    onRemoveQuestion: (Int, String) -> Unit,
) {
    val busy = gate.id in state.busyGateIds
    val expanded = state.expandedGateId == gate.id

    SectionCard {
        SectionCardHeader(
            title = "#${state.channelName(gate.channelId)}",
            icon = Icons.Default.Lock,
            trailing = {
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete gate",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
        Text(
            text = gateSummary(gate, state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        SwitchRow(
            title = if (gate.enabled) "Accepting applications" else "Closed",
            checked = gate.enabled,
            enabled = !busy,
            onCheckedChange = { viewModel.setEnabled(gate, it) },
        )
        TextButton(onClick = { viewModel.toggleGateExpanded(gate) }) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(if (expanded) "  Hide settings" else "  Settings")
        }
        if (expanded) {
            GateEditor(gate, state, viewModel, busy, onRemoveQuestion)
        }
    }
}

@Composable
private fun GateEditor(
    gate: ChannelAccessGate,
    state: ChannelAccessState,
    viewModel: ChannelAccessViewModel,
    busy: Boolean,
    onRemoveQuestion: (Int, String) -> Unit,
) {
    val roleOptions = state.roles.map { SelectorOption(it.id, it.name) }
    val channelOptions = state.channels.map { SelectorOption(it.id, it.name) }
    val displayedMode = if (gate.id in state.roleModePending) AccessGrantMode.ROLE else gate.grant

    HorizontalDivider()
    EditorHeading("Channels and roles", Icons.Default.Key)
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Key),
        options = GrantModeOptions,
        placeholder = "Pick how access is granted",
        label = "How people get in",
        selectedId = displayedMode.value.toString(),
        enabled = !busy,
        onSelect = { value -> viewModel.setGrantMode(gate, AccessGrantMode.from(value?.toIntOrNull() ?: 0)) },
    )
    if (gate.id in state.roleModePending) {
        HintText("Pick the access role below to switch this gate to role mode.")
    }
    DiscordSelectorSingle(
        kind = SelectorKind.Role,
        options = listOf(SelectorOption(NoneId, "No role, applicants are added directly")) + roleOptions,
        placeholder = "No role, applicants are added directly",
        label = "Access role",
        selectedId = if (gate.id in state.roleModePending) null else gate.accessRoleId.orNone(),
        enabled = !busy,
        onSelect = { viewModel.setAccessRole(gate, it) },
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Channel,
        options = listOf(SelectorOption(NoneId, "The gated channel itself")) + channelOptions,
        placeholder = "The gated channel itself",
        label = "Review channel",
        selectedId = gate.reviewChannelId.orNone(),
        enabled = !busy,
        onSelect = { viewModel.setReviewChannel(gate, it) },
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Channel,
        options = listOf(SelectorOption(NoneId, "No logging")) + channelOptions,
        placeholder = "No logging",
        label = "Log channel",
        selectedId = gate.logChannelId.orNone(),
        enabled = !busy,
        onSelect = { viewModel.setLogChannel(gate, it) },
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Role,
        options = listOf(SelectorOption(NoneId, "Everyone with the access role")) + roleOptions,
        placeholder = "Everyone with the access role",
        label = "Voter role",
        selectedId = gate.voterRoleId.orNone(),
        enabled = !busy,
        onSelect = { viewModel.setVoterRole(gate, it) },
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Role,
        options = listOf(SelectorOption(NoneId, "No ping")) + roleOptions,
        placeholder = "No ping",
        label = "Ping role on new applications",
        selectedId = gate.pingRoleId.orNone(),
        enabled = !busy,
        onSelect = { viewModel.setPingRole(gate, it) },
    )

    HorizontalDivider()
    EditorHeading("Voting and requirements", Icons.Default.HowToVote)
    val draft = state.numberDrafts[gate.id] ?: GateNumberDraft.from(gate)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Approvals needed", draft.requiredApprovals, Modifier.weight(1f)) { value ->
            viewModel.editNumbers(gate.id) { it.copy(requiredApprovals = value) }
        }
        NumberField("Denials needed", draft.requiredDenials, Modifier.weight(1f)) { value ->
            viewModel.editNumbers(gate.id) { it.copy(requiredDenials = value) }
        }
    }
    NumberField(
        label = "Voting window (hours)",
        value = draft.voteDurationHours,
        supportingText = "0 means no time limit",
    ) { value ->
        viewModel.editNumbers(gate.id) { it.copy(voteDurationHours = value) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Min account age (days)", draft.minAccountAgeDays, Modifier.weight(1f)) { value ->
            viewModel.editNumbers(gate.id) { it.copy(minAccountAgeDays = value) }
        }
        NumberField("Min time in server (days)", draft.minServerAgeDays, Modifier.weight(1f)) { value ->
            viewModel.editNumbers(gate.id) { it.copy(minServerAgeDays = value) }
        }
    }
    NumberField("Reapply cooldown (hours)", draft.reapplyCooldownHours) { value ->
        viewModel.editNumbers(gate.id) { it.copy(reapplyCooldownHours = value) }
    }
    Button(
        onClick = { viewModel.saveNumbers(gate) },
        enabled = !busy && viewModel.numbersChanged(gate, state.numberDrafts[gate.id]),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Save voting limits")
    }

    HorizontalDivider()
    EditorHeading("Behaviour", Icons.Default.Tune)
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.HourglassBottom),
        options = ExpiryOptions,
        placeholder = "Pick an outcome",
        label = "When the window closes",
        selectedId = gate.onExpiry.toString(),
        enabled = !busy,
        onSelect = { value ->
            value?.toIntOrNull()?.let { viewModel.setOnExpiry(gate, AccessExpiryBehavior.from(it)) }
        },
    )
    SwitchRow(
        title = "Offer an abstain button",
        checked = gate.allowAbstain,
        enabled = !busy,
        onCheckedChange = { viewModel.setAllowAbstain(gate, it) },
    )
    SwitchRow(
        title = "Hide the applicant until the vote closes",
        checked = gate.anonymousApplicant,
        enabled = !busy,
        onCheckedChange = { viewModel.setAnonymousApplicant(gate, it) },
    )
    SwitchRow(
        title = "Hide who voted which way",
        checked = gate.anonymousVotes,
        enabled = !busy,
        onCheckedChange = { viewModel.setAnonymousVotes(gate, it) },
    )
    SwitchRow(
        title = "DM the applicant on a decision",
        checked = gate.dmOnDecision,
        enabled = !busy,
        onCheckedChange = { viewModel.setDmOnDecision(gate, it) },
    )

    HorizontalDivider()
    EditorHeading("Application questions (${gate.questions.size}/$MaxQuestions)", Icons.Default.QuestionAnswer)
    if (gate.questions.isEmpty()) {
        HintText("No questions, so applicants apply with a single click.")
    } else {
        gate.questions.forEachIndexed { index, question ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${index + 1}. ${question.question}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val traits = listOfNotNull(
                        if (question.required) null else "Optional",
                        if (question.paragraph) "Multi-line" else "Single line",
                        question.placeholder?.takeIf { it.isNotBlank() }?.let { "Hint: $it" },
                    ).joinToString(" · ")
                    Text(
                        text = traits,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemoveQuestion(index + 1, question.question) }, enabled = !busy) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove question")
                }
            }
        }
    }
    if (gate.questions.size < MaxQuestions) {
        val question = state.questionDraft
        MewdekoTextField(
            value = question.question,
            onValueChange = { value -> viewModel.editQuestion { it.copy(question = value) } },
            label = "Question",
            placeholder = "What should applicants answer?",
            supportingText = "${question.question.length}/$MaxQuestionLength characters",
        )
        MewdekoTextField(
            value = question.placeholder,
            onValueChange = { value -> viewModel.editQuestion { it.copy(placeholder = value) } },
            label = "Placeholder",
            placeholder = "Optional",
        )
        SwitchRow(
            title = "Required",
            checked = question.required,
            onCheckedChange = { value -> viewModel.editQuestion { it.copy(required = value) } },
        )
        SwitchRow(
            title = "Multi-line answer",
            checked = question.paragraph,
            onCheckedChange = { value -> viewModel.editQuestion { it.copy(paragraph = value) } },
        )
        OutlinedButton(
            onClick = { viewModel.addQuestion(gate) },
            enabled = !busy && question.question.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Add question")
        }
    } else {
        HintText("Discord caps application forms at five questions.")
    }

    HorizontalDivider()
    EditorHeading("Apply panel", Icons.Default.Campaign)
    HintText(
        gate.panelChannelId?.takeIf { it.isNotEmpty() && it != NoneId }
            ?.let { "Last posted in #${state.channelName(it)}." }
            ?: "Not posted yet.",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Channel,
        options = channelOptions,
        placeholder = "Where should the button go?",
        label = "Panel channel",
        selectedId = state.panelTargets[gate.id],
        enabled = !busy,
        onSelect = { viewModel.setPanelTarget(gate.id, it) },
    )
    Button(
        onClick = { viewModel.postPanel(gate) },
        enabled = !busy && state.panelTargets[gate.id] != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Post panel")
    }
}

@Composable
private fun ApplicationsSection(state: ChannelAccessState, viewModel: ChannelAccessViewModel) {
    SectionCard {
        SectionCardHeader("Filters", Icons.Default.FilterList)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Lock),
            options = listOf(SelectorOption(FilterAll, "All gates")) +
                state.gates.map { SelectorOption(it.id.toString(), "#${state.channelName(it.channelId)}") },
            placeholder = "All gates",
            label = "Gate",
            selectedId = state.applicationGateFilter,
            onSelect = viewModel::setApplicationGateFilter,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.FilterList),
            options = StatusFilterOptions,
            placeholder = "Any status",
            label = "Status",
            selectedId = state.applicationStatusFilter,
            onSelect = viewModel::setApplicationStatusFilter,
        )
    }

    when {
        state.applicationsLoading && state.applications.isEmpty() -> SectionCard {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp),
            ) {
                CircularProgressIndicator()
            }
        }

        state.applicationsError != null && state.applications.isEmpty() -> SectionCard {
            EmptyState(state.applicationsError, icon = Icons.Default.Inbox)
            OutlinedButton(onClick = { viewModel.reloadApplications() }, modifier = Modifier.fillMaxWidth()) {
                Text("Try again")
            }
        }

        state.applications.isEmpty() -> SectionCard {
            EmptyState("Nothing to show for that filter.", icon = Icons.Default.Inbox)
        }

        else -> {
            if (state.applicationsLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.applications.forEach { application ->
                ApplicationCard(application, state, viewModel)
            }
        }
    }
}

@Composable
private fun ApplicationCard(
    application: ChannelAccessApplication,
    state: ChannelAccessState,
    viewModel: ChannelAccessViewModel,
) {
    val expanded = state.expandedApplicationId == application.id
    val detail = state.applicationDetails[application.id] ?: application
    val resolving = application.id in state.resolvingIds
    val name = application.username
        ?: application.userId.takeIf { it.isNotEmpty() && it != NoneId }
        ?: "Hidden"

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(url = application.avatarUrl, contentDescription = null, size = 36)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "#${application.id} · $name",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val closes = if (application.statusValue == AccessApplicationStatus.PENDING) {
                    application.expiresAt?.let { " · closes ${it.shortDateTime()}" }.orEmpty()
                } else {
                    ""
                }
                Text(
                    text = "#${state.channelName(application.channelId)} · opened " +
                        (application.createdAt?.shortDateTime() ?: "Unknown") + closes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(application.statusValue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TagChip("${application.approvals} approve", icon = Icons.Default.Check)
            TagChip("${application.denials} deny", icon = Icons.Default.Close)
            TagChip("${application.abstains} abstain")
        }
        TextButton(onClick = { viewModel.toggleApplicationExpanded(application) }) {
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(if (expanded) "  Hide" else "  Details")
        }

        if (expanded) {
            HorizontalDivider()
            if (detail.answers.isEmpty()) {
                HintText("No answers, this gate had no questions when they applied.")
            } else {
                detail.answers.forEach { answer ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            answer.question,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(answer.answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text("Votes", style = MaterialTheme.typography.titleSmall)
            val anonymousVotes = state.gates.firstOrNull { it.id == application.configId }?.anonymousVotes == true
            when {
                application.id in state.detailLoadingIds && detail.votes.isEmpty() ->
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                detail.votes.isNotEmpty() -> detail.votes.forEach { vote ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (icon, tint) = when (vote.vote) {
                            1 -> Icons.Default.Check to ApprovedGreen
                            -1 -> Icons.Default.Close to MaterialTheme.colorScheme.error
                            else -> Icons.Default.RemoveCircleOutline to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                        Text(
                            text = vote.username ?: vote.userId,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        vote.votedAt?.let {
                            Text(
                                it.shortDateTime(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                anonymousVotes -> HintText("Votes are anonymous on this gate.")
                else -> HintText("No votes yet.")
            }

            if (application.statusValue == AccessApplicationStatus.PENDING) {
                MewdekoTextField(
                    value = state.resolveReasons[application.id].orEmpty(),
                    onValueChange = { viewModel.setResolveReason(application.id, it) },
                    label = "Reason",
                    placeholder = "Optional",
                    enabled = !resolving,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.resolveApplication(application, approve = true) },
                        enabled = !resolving,
                        colors = ButtonDefaults.buttonColors(containerColor = ApprovedGreen),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Approve")
                    }
                    Button(
                        onClick = { viewModel.resolveApplication(application, approve = false) },
                        enabled = !resolving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Deny")
                    }
                }
            } else {
                val closedAt = detail.resolvedAt?.shortDateTime() ?: "Unknown"
                val reason = detail.resolutionReason?.takeIf { it.isNotBlank() }
                HintText(if (reason != null) "Closed $closedAt: $reason" else "Closed $closedAt.")
            }
        }
    }
}

@Composable
private fun BlockedSection(state: ChannelAccessState, viewModel: ChannelAccessViewModel) {
    SectionCard {
        SectionCardHeader("Block someone from applying", Icons.Default.Block)
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = state.members.map {
                SelectorOption(it.id, it.displayName.ifBlank { it.username }, subtitle = it.username)
            },
            placeholder = "Select a user",
            label = "User",
            selectedId = state.newBlockUserId,
            onSelect = viewModel::setNewBlockUser,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Lock),
            options = listOf(SelectorOption(FilterAll, "Every gate")) +
                state.gates.map { SelectorOption(it.id.toString(), "#${state.channelName(it.channelId)}") },
            placeholder = "Every gate",
            label = "Scope",
            selectedId = state.newBlockScope,
            onSelect = viewModel::setNewBlockScope,
        )
        MewdekoTextField(
            value = state.newBlockReason,
            onValueChange = viewModel::setNewBlockReason,
            label = "Reason",
            placeholder = "Optional",
        )
        Button(
            onClick = viewModel::addBlock,
            enabled = !state.isBlocking && state.newBlockUserId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isBlocking) "Blocking…" else "Block")
        }
    }

    SectionCard {
        SectionCardHeader("Blocked users (${state.blacklist.size})", Icons.Default.PersonOff)
        when {
            state.blacklistError != null && state.blacklist.isEmpty() -> {
                EmptyState(state.blacklistError, icon = Icons.Default.PersonOff)
                OutlinedButton(onClick = { viewModel.refreshBlacklist() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Try again")
                }
            }

            state.blacklist.isEmpty() -> EmptyState("Nobody is blocked from applying.", icon = Icons.Default.PersonOff)

            else -> state.blacklist.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                val member = state.members.firstOrNull { it.id == entry.userId }
                val name = entry.username
                    ?: member?.let { it.displayName.ifBlank { it.username } }
                    ?: entry.userId
                val details = listOfNotNull(
                    state.gateName(entry.configId),
                    entry.reason?.takeIf { it.isNotBlank() },
                    entry.addedAt?.let { "added ${it.shortDateTime()}" },
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.removeBlock(entry) },
                        enabled = entry.id !in state.unblockingIds,
                    ) {
                        Text("Unblock")
                    }
                }
            }
        }
    }
}

/** Sentinel option id meaning "unset"; the bot treats zero as clearing an optional channel or role. */
private const val NoneId = "0"

/** Maps an unset optional id to the [NoneId] option so the selector shows it as chosen. */
private fun String?.orNone(): String = this?.takeIf { it.isNotEmpty() } ?: NoneId

/** The one-line gate summary under its channel name. */
private fun gateSummary(gate: ChannelAccessGate, state: ChannelAccessState): String {
    val grant = if (gate.grant == AccessGrantMode.ROLE) {
        "Grants @${state.roleName(gate.accessRoleId)}"
    } else {
        "Adds people to the channel directly"
    }
    val window = if (gate.voteDurationHours > 0) "${gate.voteDurationHours}h window" else "no time limit"
    return listOf(
        grant,
        "${gate.requiredApprovals} to approve, ${gate.requiredDenials} to deny",
        window,
        "${gate.pendingApplications} open",
    ).joinToString(" · ")
}

@Composable
private fun EditorHeading(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    onValueChange: (String) -> Unit,
) {
    MewdekoTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
        label = label,
        numeric = true,
        supportingText = supportingText,
        modifier = modifier,
    )
}

@Composable
private fun StatusBadge(status: AccessApplicationStatus) {
    val color = when (status) {
        AccessApplicationStatus.APPROVED -> ApprovedGreen
        AccessApplicationStatus.DENIED -> MaterialTheme.colorScheme.error
        AccessApplicationStatus.PENDING -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
