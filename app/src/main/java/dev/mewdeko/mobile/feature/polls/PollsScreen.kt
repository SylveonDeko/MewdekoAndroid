package dev.mewdeko.mobile.feature.polls

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.shortDate
import dev.mewdeko.mobile.util.shortDateTime
import dev.mewdeko.mobile.util.withSeparators
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private val Tabs = listOf(
    SectionTab(PollsSection.POLLS, "Polls", Icons.Default.Poll),
    SectionTab(PollsSection.CREATE, "Create", Icons.Default.Add),
    SectionTab(PollsSection.SCHEDULED, "Scheduled", Icons.Default.Schedule),
    SectionTab(PollsSection.TEMPLATES, "Templates", Icons.Default.ContentCopy),
    SectionTab(PollsSection.ANALYTICS, "Analytics", Icons.Default.BarChart),
)

private val TypeOptions = PollType.pickerOrder.map { SelectorOption(it.value.toString(), it.label) }

/** Accent for active polls, matching the dashboard's green status dot. */
private val ActiveGreen = Color(0xFF10B981)

/** Create, schedule, template, review, and analyse polls. */
@Composable
fun PollsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: PollsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingClose by remember { mutableStateOf<Poll?>(null) }
    var pendingDelete by remember { mutableStateOf<Poll?>(null) }
    var pendingCancel by remember { mutableStateOf<ScheduledPoll?>(null) }
    var pendingTemplateDelete by remember { mutableStateOf<PollTemplate?>(null) }

    FeatureScaffold(
        title = "Polls",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = viewModel::startNewPoll) {
                Icon(Icons.Default.Add, contentDescription = "New poll")
            }
            IconButton(onClick = { viewModel.load(refreshing = true) }, enabled = !loadState.isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            PollsSection.POLLS -> PollListSection(
                state = state,
                viewModel = viewModel,
                onClose = { pendingClose = it },
                onDelete = { pendingDelete = it },
            )

            PollsSection.CREATE -> CreateSection(state, viewModel)
            PollsSection.SCHEDULED -> ScheduledSection(state, viewModel, onCancel = { pendingCancel = it })
            PollsSection.TEMPLATES -> TemplatesSection(state, viewModel, onDelete = { pendingTemplateDelete = it })
            PollsSection.ANALYTICS -> AnalyticsSection(state, viewModel)
        }
    }

    pendingClose?.let { poll ->
        ConfirmDialog(
            title = "Close poll?",
            message = "Close \"${poll.question}\"? Voting stops and final results are posted.",
            confirmLabel = "Close poll",
            destructive = false,
            onConfirm = { viewModel.closePoll(poll) },
            onDismiss = { pendingClose = null },
        )
    }

    pendingDelete?.let { poll ->
        ConfirmDialog(
            title = "Delete poll?",
            message = "Delete \"${poll.question}\" and all of its votes? The poll message is removed from Discord.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deletePoll(poll) },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingCancel?.let { item ->
        ConfirmDialog(
            title = "Cancel scheduled poll?",
            message = "Cancel the scheduled poll \"${item.question}\"? It will not be posted.",
            confirmLabel = "Cancel poll",
            onConfirm = { viewModel.cancelScheduled(item) },
            onDismiss = { pendingCancel = null },
        )
    }

    pendingTemplateDelete?.let { template ->
        ConfirmDialog(
            title = "Delete template?",
            message = "Delete template \"${template.name}\"?",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteTemplate(template) },
            onDismiss = { pendingTemplateDelete = null },
        )
    }
}

@Composable
private fun PollListSection(
    state: PollsState,
    viewModel: PollsViewModel,
    onClose: (Poll) -> Unit,
    onDelete: (Poll) -> Unit,
) {
    SectionCard {
        SectionCardHeader(
            title = "Polls (${state.polls.size})",
            icon = Icons.Default.Poll,
            trailing = if (state.isLoadingPolls) {
                { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
            } else {
                null
            },
        )
        SwitchRow(
            title = "Include closed polls",
            checked = state.includeInactive,
            onCheckedChange = viewModel::setIncludeInactive,
        )

        when {
            state.isLoadingPolls && state.polls.isEmpty() -> InlineLoading()
            state.pollsError != null && state.polls.isEmpty() ->
                InlineError(state.pollsError, onRetry = { viewModel.reloadPolls() })

            state.polls.isEmpty() -> EmptyState(
                "No polls yet. Create one from the Create tab.",
                icon = Icons.Default.Poll,
            )

            else -> {
                state.pollsError?.let { InlineError(it, onRetry = { viewModel.reloadPolls() }) }
                state.polls.forEach { poll ->
                    PollRow(
                        poll = poll,
                        detail = state.details[poll.id] ?: poll,
                        channelName = poll.channelName ?: state.channelName(poll.channelId),
                        expanded = state.expandedPollId == poll.id,
                        loadingDetail = state.loadingDetailId == poll.id,
                        busy = state.busyPollId == poll.id,
                        onToggle = { viewModel.togglePoll(poll) },
                        onClose = { onClose(poll) },
                        onDelete = { onDelete(poll) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PollRow(
    poll: Poll,
    detail: Poll,
    channelName: String,
    expanded: Boolean,
    loadingDetail: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    val total = detail.totalVotes
    val accent = if (poll.isActive) ActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableRow(onToggle)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    if (poll.isActive) Icons.Default.RadioButtonChecked else Icons.Default.CheckCircle,
                    contentDescription = if (poll.isActive) "Active" else "Closed",
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(poll.question, style = MaterialTheme.typography.titleSmall)
                    val meta = buildList {
                        add("#$channelName")
                        add(poll.pollType?.label ?: "Poll")
                        add(if (total == 1) "1 vote" else "${total.withSeparators()} votes")
                        add(
                            if (poll.isActive) timeLeft(poll.expiresAt)
                            else "Closed ${poll.closedAt?.shortDateTime().orEmpty()}".trim()
                        )
                        poll.creatorName?.takeIf { it.isNotBlank() }?.let { add("by $it") }
                    }
                    Text(
                        text = meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (loadingDetail) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    detail.options.sortedBy { it.index }.forEach { option ->
                        OptionBar(option = option, total = total)
                    }
                    detail.stats?.let { stats ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                label = "Unique voters",
                                value = stats.uniqueVoters.withSeparators(),
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Participation",
                                value = "${formatNumber(stats.participationRate)}%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                label = "Peak hour",
                                value = "${stats.peakVotingHour}:00 UTC",
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Created",
                                value = poll.createdAt?.shortDate() ?: "Unknown",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (poll.isActive) {
                            OutlinedButton(onClick = onClose, enabled = !busy) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Close poll")
                            }
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = !busy,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionBar(option: PollOption, total: Int) {
    val fraction = if (total > 0) option.voteCount.toFloat() / total else 0f
    val percent = (fraction * 100).roundToInt()
    val barColor = option.color.toComposeColor() ?: MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = listOfNotNull(option.emote?.takeIf { it.isNotBlank() }, option.text).joinToString(" "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${option.voteCount.withSeparators()}  ·  $percent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor),
            )
        }
    }
}

@Composable
private fun CreateSection(state: PollsState, viewModel: PollsViewModel) {
    val draft = state.draft
    val isYesNo = draft.type == PollType.YES_NO
    val isRoleRestricted = draft.type == PollType.ROLE_RESTRICTED

    SectionCard {
        SectionCardHeader("New poll", Icons.Default.HowToVote)
        MewdekoTextField(
            value = draft.question,
            onValueChange = viewModel::setQuestion,
            label = "Question",
            placeholder = "What should we do next?",
            singleLine = false,
            minLines = 2,
            supportingText = "${draft.question.length}/${PollDraft.MAX_QUESTION}",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Poll),
            options = TypeOptions,
            placeholder = PollType.SINGLE_CHOICE.label,
            label = "Poll type",
            selectedId = draft.type.value.toString(),
            onSelect = { id -> PollType.from(id?.toIntOrNull())?.let(viewModel::setType) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.availableChannels.map { SelectorOption(it.id, it.name) },
            placeholder = "Select channel",
            label = "Channel",
            selectedId = draft.channelId,
            onSelect = viewModel::setChannel,
        )
        if (isRoleRestricted) {
            DiscordSelector(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Select roles",
                label = "Roles allowed to vote",
                multiple = true,
                selection = draft.allowedRoles,
                onSelectionChange = viewModel::setAllowedRoles,
            )
        }
    }

    SectionCard {
        SectionCardHeader(
            title = "Options",
            icon = Icons.Default.Tune,
            trailing = if (!isYesNo) {
                {
                    Text(
                        "${draft.options.size}/${PollDraft.MAX_OPTIONS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        )
        if (isYesNo) {
            Text(
                text = "Yes / No polls always offer the options Yes and No.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            draft.options.forEachIndexed { index, text ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MewdekoTextField(
                        value = text,
                        onValueChange = { viewModel.setOption(index, it) },
                        label = "Option ${index + 1}",
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.removeOption(index) },
                        enabled = draft.options.size > PollDraft.MIN_OPTIONS,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove option ${index + 1}")
                    }
                }
            }
            if (draft.options.size < PollDraft.MAX_OPTIONS) {
                TextButton(onClick = viewModel::addOption) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Add option")
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Timing", Icons.Default.Timer)
        MewdekoTextField(
            value = draft.durationMinutes,
            onValueChange = viewModel::setDuration,
            label = "Duration (minutes)",
            placeholder = "Open until closed",
            numeric = true,
            supportingText = "1 to ${PollDraft.MAX_DURATION_MINUTES.withSeparators()} minutes. Leave empty to keep " +
                "the poll open until it is closed.",
        )
        ScheduleField(value = draft.scheduleFor, onChange = viewModel::setScheduleFor)
    }

    SectionCard {
        SectionCardHeader("Behaviour", Icons.Default.Tune)
        SwitchRow(
            title = "Allow changing votes",
            checked = draft.allowVoteChanges,
            onCheckedChange = viewModel::setAllowVoteChanges,
        )
        SwitchRow(
            title = "Show live results",
            checked = draft.showResults,
            onCheckedChange = viewModel::setShowResults,
        )
        SwitchRow(
            title = "Show progress bars",
            checked = draft.showProgressBars,
            onCheckedChange = viewModel::setShowProgressBars,
        )
    }

    SectionCard {
        SectionCardHeader("Template", Icons.Default.ContentCopy)
        SwitchRow(
            title = "Also save as a template",
            subtitle = "Reuse this question and its options later from the Templates tab",
            checked = draft.saveAsTemplate,
            onCheckedChange = viewModel::setSaveAsTemplate,
        )
        if (draft.saveAsTemplate) {
            MewdekoTextField(
                value = draft.templateName,
                onValueChange = viewModel::setTemplateName,
                label = "Template name",
            )
        }
    }

    state.draftError?.let { error ->
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = viewModel::resetDraft, enabled = !state.isSubmitting) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Reset")
        }
        Button(
            onClick = viewModel::submit,
            enabled = !state.isSubmitting,
            modifier = Modifier.weight(1f),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Icon(
                    if (draft.scheduleFor != null) Icons.Default.Event else Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(if (draft.scheduleFor != null) "  Schedule poll" else "  Post poll")
        }
    }
}

@Composable
private fun ScheduleField(value: Instant?, onChange: (Instant?) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Schedule for later",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val base = value?.let { ZonedDateTime.ofInstant(it, ZoneId.systemDefault()) }
                        ?: ZonedDateTime.now().plusHours(1)
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val zoned = ZonedDateTime.of(
                                        year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault(),
                                    )
                                    onChange(zoned.toInstant())
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
                Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = value?.let { "  ${it.shortDateTime()} (${it.relativeToNow()})" } ?: "  Post immediately",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (value != null) {
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear schedule")
                }
            }
        }
        Text(
            text = "Up to 30 days ahead. Leave unset to post right away.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduledSection(
    state: PollsState,
    viewModel: PollsViewModel,
    onCancel: (ScheduledPoll) -> Unit,
) {
    val pending = state.pendingScheduled
    SectionCard {
        SectionCardHeader("Scheduled polls (${pending.size})", Icons.Default.Schedule)
        when {
            state.scheduledError != null && pending.isEmpty() ->
                InlineError(state.scheduledError, onRetry = { viewModel.reloadScheduled() })

            pending.isEmpty() -> EmptyState(
                "Nothing scheduled. Set a time on the Create tab to queue a poll.",
                icon = Icons.Default.Schedule,
            )

            else -> {
                state.scheduledError?.let { InlineError(it, onRetry = { viewModel.reloadScheduled() }) }
                pending.sortedBy { it.scheduledFor ?: Instant.MAX }.forEach { item ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.question, style = MaterialTheme.typography.titleSmall)
                                val meta = listOfNotNull(
                                    "#${state.channelName(item.channelId)}",
                                    PollType.from(item.type)?.label ?: "Poll",
                                    item.scheduledFor?.let { "posts ${it.shortDateTime()} (${it.relativeToNow()})" },
                                    item.durationMinutes?.let { "runs ${it.withSeparators()} min" },
                                )
                                Text(
                                    text = meta.joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = { onCancel(item) },
                                enabled = state.cancellingScheduledId != item.id,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) { Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesSection(
    state: PollsState,
    viewModel: PollsViewModel,
    onDelete: (PollTemplate) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Templates (${state.templates.size})", Icons.Default.ContentCopy)
        when {
            state.templatesError != null && state.templates.isEmpty() ->
                InlineError(state.templatesError, onRetry = { viewModel.reloadTemplates() })

            state.templates.isEmpty() -> EmptyState(
                "No templates yet. Turn on \"Also save as a template\" when creating a poll to reuse it later.",
                icon = Icons.Default.ContentCopy,
            )

            else -> {
                state.templatesError?.let { InlineError(it, onRetry = { viewModel.reloadTemplates() }) }
                state.templates.forEach { template ->
                    val optionCount = remember(template.options) { template.optionTexts().size }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(template.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = template.question,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (optionCount == 1) "1 option" else "$optionCount options",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = { viewModel.useTemplate(template) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Use") }
                                IconButton(onClick = { onDelete(template) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete template ${template.name}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsSection(state: PollsState, viewModel: PollsViewModel) {
    val analytics = state.analytics
    when {
        analytics == null && state.isLoadingAnalytics -> SectionCard { InlineLoading() }
        analytics == null && state.analyticsError != null -> SectionCard {
            InlineError(state.analyticsError, onRetry = { viewModel.loadAnalytics() })
        }

        analytics == null -> SectionCard {
            EmptyState("Analytics appear once polls have been created.", icon = Icons.Default.QueryStats)
        }

        else -> {
            if (state.analyticsError != null) {
                SectionCard { InlineError(state.analyticsError, onRetry = { viewModel.loadAnalytics() }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Polls this month, ${analytics.activePolls.withSeparators()} active",
                    value = analytics.totalPolls.withSeparators(),
                    icon = Icons.Default.Poll,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Votes cast, last 30 days",
                    value = analytics.totalVotes.withSeparators(),
                    icon = Icons.Default.HowToVote,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Avg votes per poll",
                    value = formatNumber(analytics.averageVotesPerPoll),
                    icon = Icons.Default.BarChart,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Favourite type, most created",
                    value = if (analytics.totalPolls > 0) {
                        PollType.from(analytics.mostPopularPollType)?.label ?: "n/a"
                    } else {
                        "n/a"
                    },
                    icon = Icons.Default.QueryStats,
                    modifier = Modifier.weight(1f),
                )
            }
            PollsPerDayChart(analytics.pollsCreatedByDay)
        }
    }
}

@Composable
private fun PollsPerDayChart(byDay: Map<String, Int>) {
    if (byDay.isEmpty()) return
    val entries = remember(byDay) {
        byDay.entries
            .map { (key, count) -> (InstantParser.parse(key) ?: Instant.EPOCH) to count }
            .sortedBy { it.first }
    }
    val max = entries.maxOf { it.second }.coerceAtLeast(1)
    val busiest = entries.maxByOrNull { it.second }
    SectionCard {
        SectionCardHeader("Polls created per day", Icons.Default.BarChart)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics {
                    contentDescription = "Polls created per day: " + entries.joinToString(", ") { (day, count) ->
                        "${day.shortDate()} $count"
                    }
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEach { (_, count) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight((count.toFloat() / max).coerceIn(0.03f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entries.first().first.shortDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (entries.size > 1) {
                Text(
                    text = entries.last().first.shortDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        busiest?.let { (day, count) ->
            Text(
                text = "Busiest day: ${day.shortDate()} with ${count.withSeparators()} " +
                    if (count == 1) "poll" else "polls",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InlineLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun InlineError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}

/** Mirrors the dashboard's countdown wording for an active poll. */
private fun timeLeft(expiresAt: Instant?): String {
    if (expiresAt == null) return "No end time"
    val millis = expiresAt.toEpochMilli() - System.currentTimeMillis()
    if (millis <= 0) return "Ended"
    val minutes = (millis / 60_000.0).roundToInt()
    if (minutes < 60) return "${minutes}m left"
    val hours = (minutes / 60.0).roundToInt()
    if (hours < 48) return "${hours}h left"
    return "${(hours / 24.0).roundToInt()}d left"
}

/** Formats a number with at most two decimals and no trailing zeros. */
private fun formatNumber(value: Double): String {
    val rounded = (value * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else "%.2f".format(rounded).trimEnd('0').trimEnd('.')
}

/** Parses a `#RRGGBB` or `#AARRGGBB` string into a colour, or `null` when it is not one. */
private fun String?.toComposeColor(): Color? {
    val hex = this?.trim()?.removePrefix("#") ?: return null
    val raw = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000 or raw)
        8 -> Color(raw)
        else -> null
    }
}
