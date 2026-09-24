package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/**
 * Full create/edit form for a repeater: channel and forum tag pickers, the
 * rich message builder, trigger settings, and every advanced option the
 * dashboard exposes. It is long and holds a message preview, so it opens as
 * a [FullScreenEditor] for both new and existing repeaters.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeaterEditor(
    state: RepeatersState,
    original: RepeaterEntry?,
    draft: RepeaterDraft,
    onDismiss: () -> Unit,
    onSave: (RepeaterDraft) -> Unit,
) {
    val isEdit = original != null
    var form by remember(original?.id) { mutableStateOf(draft) }
    val isForumSelected = state.isForumChannel(form.channelId)

    LaunchedEffect(form.channelId, isForumSelected) {
        if (!isEdit) {
            form = if (isForumSelected) {
                form.copy(threadAutoSticky = true)
            } else {
                form.copy(threadAutoSticky = false, threadOnlyMode = false)
            }
        }
        form = form.requiringThreadOnlyForImmediateForum(isForumSelected)
    }

    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name, "Text channel") } +
        state.forumChannels.map { SelectorOption(it.id, it.name, "Forum · ${it.tags.size} tags") }

    FullScreenEditor(
        title = if (isEdit) "Edit repeater" else "New repeater",
        onClose = onDismiss,
        confirmLabel = if (isEdit) "Save" else "Create",
        confirmEnabled = form.channelId.isNotBlank() && !form.message.isEmpty,
        onConfirm = { onSave(form) },
        hasUnsavedChanges = form.message != draft.message || form.channelId != draft.channelId ||
            form.interval != draft.interval || form.triggerMode != draft.triggerMode,
    ) {
        SectionCard {
            SectionCardHeader("Channel", Icons.Default.Tag)
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "Pick a channel",
                selectedId = form.channelId.ifEmpty { null },
                onSelect = { form = form.copy(channelId = it.orEmpty()) },
            )
            if (isForumSelected) {
                val tags = state.forumTags(form.channelId)
                if (tags.isNotEmpty()) {
                    Text("Required tags", style = MaterialTheme.typography.labelLarge)
                    tags.forEach { tag ->
                        TagCheckRow(
                            label = tag.name,
                            checked = tag.id in form.forumRequiredTags,
                            onCheckedChange = { checked ->
                                form = if (checked) {
                                    form.copy(
                                        forumRequiredTags = form.forumRequiredTags + tag.id,
                                        forumExcludedTags = form.forumExcludedTags - tag.id,
                                    )
                                } else {
                                    form.copy(forumRequiredTags = form.forumRequiredTags - tag.id)
                                }
                            },
                        )
                    }
                    Text("Excluded tags", style = MaterialTheme.typography.labelLarge)
                    tags.forEach { tag ->
                        TagCheckRow(
                            label = tag.name,
                            checked = tag.id in form.forumExcludedTags,
                            enabled = tag.id !in form.forumRequiredTags,
                            onCheckedChange = { checked ->
                                form = if (checked) {
                                    form.copy(
                                        forumExcludedTags = form.forumExcludedTags + tag.id,
                                        forumRequiredTags = form.forumRequiredTags - tag.id,
                                    )
                                } else {
                                    form.copy(forumExcludedTags = form.forumExcludedTags - tag.id)
                                }
                            },
                        )
                    }
                }
            }
        }

        SectionCard {
            SectionCardHeader("Message", Icons.Default.ChatBubble)
            EmbedMessageEditor(message = form.message, onMessageChange = { form = form.copy(message = it) })
        }

        SectionCard {
            SectionCardHeader("Trigger", Icons.Default.Bolt)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Bolt),
                options = StickyTriggerMode.entries.map { SelectorOption(it.raw.toString(), it.label, it.blurb) },
                placeholder = "Trigger mode",
                selectedId = form.triggerMode.raw.toString(),
                onSelect = {
                    val mode = StickyTriggerMode.from(it?.toIntOrNull() ?: 0)
                    form = form.copy(triggerMode = mode).requiringThreadOnlyForImmediateForum(isForumSelected)
                },
            )

            when {
                form.triggerMode == StickyTriggerMode.IMMEDIATE -> Text(
                    "Reposts instantly whenever a new message arrives. On forum channels this requires " +
                        "thread-only mode, enabled below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                form.triggerMode.usesActivitySettings -> {
                    MewdekoTextField(
                        value = form.activityThreshold.toString(),
                        onValueChange = { form = form.copy(activityThreshold = it.toIntOrNull() ?: form.activityThreshold) },
                        label = "Activity threshold (messages)",
                        numeric = true,
                    )
                    MewdekoTextField(
                        value = form.activityTimeWindow,
                        onValueChange = { form = form.copy(activityTimeWindow = it) },
                        label = "Activity time window (HH:MM:SS)",
                    )
                }

                else -> {
                    Text("Repeat every", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IntervalPresets.forEach { preset ->
                            TextButton(onClick = { form = form.copy(interval = preset.value) }) { Text(preset.label) }
                        }
                    }
                    MewdekoTextField(
                        value = form.interval,
                        onValueChange = { form = form.copy(interval = it) },
                        label = "Custom interval (HH:MM:SS, or D.HH:MM:SS)",
                    )
                }
            }

            MewdekoTextField(
                value = form.startTimeOfDay,
                onValueChange = { form = form.copy(startTimeOfDay = it) },
                label = "Start time of day (HH:MM)",
                placeholder = "Leave empty for no fixed start",
            )
        }

        SectionCard {
            SectionCardHeader("Advanced options", Icons.Default.Settings)

            SwitchRow(
                title = "Skip if unchanged",
                subtitle = "Don't repost if the message is already last in the channel",
                checked = form.noRedundant,
                onCheckedChange = { form = form.copy(noRedundant = it) },
            )
            SwitchRow(
                title = "Allow mentions",
                subtitle = "Allow @everyone and @here in this message",
                checked = form.allowMentions,
                onCheckedChange = { form = form.copy(allowMentions = it) },
            )
            SwitchRow(
                title = "Silent",
                subtitle = "Post without triggering notifications",
                checked = form.suppressNotifications,
                onCheckedChange = { form = form.copy(suppressNotifications = it) },
            )

            SliderRow(
                label = "Priority",
                value = form.priority.toFloat(),
                onValueChange = { form = form.copy(priority = it.toInt()) },
                valueRange = 0f..100f,
                valueLabel = "${form.priority}",
            )
            MewdekoTextField(
                value = form.queuePosition.toString(),
                onValueChange = { form = form.copy(queuePosition = it.toIntOrNull() ?: form.queuePosition) },
                label = "Queue position",
                numeric = true,
            )

            SwitchRow(
                title = "Conversation detection",
                subtitle = "Treat a burst of messages per minute as an active conversation",
                checked = form.conversationDetection,
                onCheckedChange = { form = form.copy(conversationDetection = it) },
            )
            if (form.conversationDetection) {
                MewdekoTextField(
                    value = form.conversationThreshold.toString(),
                    onValueChange = {
                        form = form.copy(conversationThreshold = it.toIntOrNull() ?: form.conversationThreshold)
                    },
                    label = "Conversation threshold (messages/min)",
                    numeric = true,
                )
            }

            if (isForumSelected) {
                SwitchRow(
                    title = "Auto-create in new threads",
                    checked = form.threadAutoSticky,
                    onCheckedChange = { form = form.copy(threadAutoSticky = it) },
                )
                SwitchRow(
                    title = "Thread-only mode",
                    subtitle = "Post only in threads, never the parent forum channel",
                    checked = form.threadOnlyMode,
                    onCheckedChange = { form = form.copy(threadOnlyMode = it) },
                )
            }
        }

        SectionCard {
            SectionCardHeader("Expiry", Icons.Default.EventBusy)
            MewdekoTextField(
                value = form.maxAge,
                onValueChange = { form = form.copy(maxAge = it) },
                label = "Auto-delete after",
                placeholder = "7.00:00:00 for 7 days",
            )
            MewdekoTextField(
                value = form.maxTriggers,
                onValueChange = { form = form.copy(maxTriggers = it) },
                label = "Max displays",
                placeholder = "Leave empty for unlimited",
                numeric = true,
            )
        }

        SectionCard {
            SectionCardHeader("Time schedule", Icons.Default.CalendarMonth)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.AccessTime),
                options = TimeSchedulePreset.entries.map { SelectorOption(it.raw, it.label) },
                placeholder = "No schedule",
                selectedId = form.timeSchedulePreset.raw,
                onSelect = { form = form.copy(timeSchedulePreset = TimeSchedulePreset.from(it)) },
            )
            if (form.timeSchedulePreset == TimeSchedulePreset.CUSTOM) {
                MewdekoTextField(
                    value = form.timeConditions,
                    onValueChange = { form = form.copy(timeConditions = it) },
                    label = "Custom time conditions (JSON)",
                    singleLine = false,
                    minLines = 3,
                    supportingText = "Requires a guild timezone to be set with the timezone command.",
                )
            }
        }
    }
}

/**
 * Forces thread-only mode on for a forum channel paired with [StickyTriggerMode.IMMEDIATE],
 * since the bot cannot post directly to a forum's parent channel and rejects the combination
 * otherwise.
 */
private fun RepeaterDraft.requiringThreadOnlyForImmediateForum(isForumSelected: Boolean): RepeaterDraft =
    if (isForumSelected && triggerMode == StickyTriggerMode.IMMEDIATE && !threadOnlyMode) {
        copy(threadOnlyMode = true)
    } else {
        this
    }

@Composable
private fun TagCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}
