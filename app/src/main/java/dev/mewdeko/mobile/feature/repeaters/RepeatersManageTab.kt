package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.util.relativeToNow

/** Manage tab: bulk selection, and one card per repeater with every inline control. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatersManageTab(
    state: RepeatersState,
    viewModel: RepeatersViewModel,
    onEdit: (RepeaterEntry) -> Unit,
    onDelete: (RepeaterEntry) -> Unit,
    onQuickEdit: (RepeaterEntry, QuickEditField) -> Unit,
) {
    if (state.repeaters.isEmpty()) {
        SectionCard {
            EmptyState(message = "No repeaters configured yet.", icon = Icons.Default.Repeat)
        }
        return
    }

    SectionCard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = viewModel::selectAll) {
                Text("Select all (${state.repeaters.size})")
            }
            if (state.selectedIds.isNotEmpty()) {
                TextButton(onClick = viewModel::clearSelection) {
                    Text("Clear (${state.selectedIds.size})")
                }
            }
        }
        if (state.selectedIds.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.bulkToggle(true) }) { Text("Enable selected") }
                TextButton(onClick = { viewModel.bulkToggle(false) }) { Text("Disable selected") }
            }
        }
    }

    state.repeaters.forEach { repeater ->
        key(repeater.id) {
            RepeaterCard(
                repeater = repeater,
                state = state,
                selected = repeater.id in state.selectedIds,
                onSelectedChange = { viewModel.setSelected(repeater.id, it) },
                onEdit = { onEdit(repeater) },
                onDelete = { onDelete(repeater) },
                onToggleEnabled = { viewModel.update(repeater.id, isEnabled = it) },
                onTriggerNow = { viewModel.triggerNow(repeater.id) },
                onMoveUp = { viewModel.moveUp(repeater.id) },
                onMoveDown = { viewModel.moveDown(repeater.id) },
                onSetPriority = { viewModel.update(repeater.id, priority = it) },
                onQuickEdit = { field -> onQuickEdit(repeater, field) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeaterCard(
    repeater: RepeaterEntry,
    state: RepeatersState,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTriggerNow: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSetPriority: (Int) -> Unit,
    onQuickEdit: (QuickEditField) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Column(Modifier.weight(1f)) {
                Text("#${state.channelName(repeater.channelId)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = repeater.trigger.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onTriggerNow, enabled = repeater.isEnabled) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Post now")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete repeater", tint = MaterialTheme.colorScheme.error)
            }
        }

        MessagePreview(repeater.message)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (repeater.trigger) {
                StickyTriggerMode.IMMEDIATE -> TagChip("Reposts instantly", icon = Icons.Default.Bolt)
                else -> {
                    TagChip("Every ${formatIntervalReadable(repeater.interval)}", icon = Icons.Default.AccessTime)
                    repeater.startTimeOfDay?.let { TagChip("Starts $it") }
                    repeater.nextExecution?.let { TagChip("Next ${it.relativeToNow()}") }
                }
            }
            if (repeater.trigger.usesActivitySettings) {
                TagChip("Threshold ${repeater.activityThreshold} / ${formatIntervalReadable(repeater.activityTimeWindow)}")
            }
            if (repeater.conversationDetection) {
                TagChip("Conversation ${repeater.conversationThreshold}/min", icon = Icons.Default.Groups)
            }
            TagChip("Priority ${repeater.priority}")
            TagChip("Queue #${repeater.queuePosition}")
            TagChip("${repeater.displayCount} posts")
            when {
                repeater.maxAge != null && repeater.maxTriggers != null ->
                    TagChip("Expires ${repeater.maxAge} / ${repeater.maxTriggers} posts", icon = Icons.Default.EventBusy)
                repeater.maxAge != null -> TagChip("Expires after ${repeater.maxAge}", icon = Icons.Default.EventBusy)
                repeater.maxTriggers != null -> TagChip("Expires after ${repeater.maxTriggers} posts", icon = Icons.Default.EventBusy)
            }
        }

        if (repeater.requiresTimezone) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(
                    "This repeater has a time schedule but the server has no timezone set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (repeater.threadAutoSticky) TagChip("Thread auto-sticky", icon = Icons.Default.PushPin)
            if (repeater.threadOnlyMode) TagChip("Thread only", icon = Icons.Default.Forum)
            if (!repeater.timeConditions.isNullOrBlank()) TagChip("Time scheduled", icon = Icons.Default.CalendarMonth)
            if (!repeater.forumTagConditions.isNullOrBlank()) TagChip("Forum tags", icon = Icons.Default.Tag)
            if (repeater.noRedundant) TagChip("Skip if unchanged", icon = Icons.Default.Check)
            if (repeater.suppressNotifications) TagChip("Silent")
        }

        SwitchRow(title = "Enabled", checked = repeater.isEnabled, onCheckedChange = onToggleEnabled)

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Priority:", style = MaterialTheme.typography.bodySmall)
            listOf(10, 50, 90).forEach { value ->
                TextButton(onClick = { onSetPriority(value) }) { Text("$value") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onEdit) { Text("Edit") }
            IconButton(onClick = onMoveUp, enabled = repeater.queuePosition > 1) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip("Interval", icon = Icons.Default.AccessTime, onClick = { onQuickEdit(QuickEditField.INTERVAL) })
            TagChip("Start time", icon = Icons.Default.AccessTime, onClick = { onQuickEdit(QuickEditField.START_TIME) })
            if (repeater.conversationDetection) {
                TagChip("Conversation", icon = Icons.Default.Groups, onClick = { onQuickEdit(QuickEditField.THRESHOLD) })
            }
            TagChip("Expiry", icon = Icons.Default.EventBusy, onClick = { onQuickEdit(QuickEditField.EXPIRY) })
        }
    }
}

@Composable
private fun MessagePreview(rawMessage: String) {
    val parsed = EmbedMessage.parse(rawMessage)
    if (parsed.isEmpty) {
        Text(
            "No message configured",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
        return
    }
    Column {
        if (parsed.content.isNotBlank()) {
            Text(parsed.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        if (parsed.embeds.isNotEmpty() || parsed.components.isNotEmpty()) {
            val parts = buildList {
                if (parsed.embeds.isNotEmpty()) add("${parsed.embeds.size} embed(s)")
                if (parsed.components.isNotEmpty()) add("${parsed.components.size} component(s)")
            }
            Text(
                parts.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
