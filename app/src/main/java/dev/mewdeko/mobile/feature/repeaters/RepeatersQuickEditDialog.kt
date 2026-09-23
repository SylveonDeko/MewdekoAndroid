package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.MewdekoTextField

private val IntervalRegex = Regex("""^\d{1,2}:\d{2}:\d{2}$""")
private val StartTimeRegex = Regex("""^\d{1,2}:\d{2}$""")
private val MaxAgeRegex = Regex("""^(\d+\.)?\d{1,2}:\d{2}:\d{2}$""")

/** Single-property quick edit, mirroring the dashboard's interval / start time / threshold / expiry modal. */
@Composable
fun QuickEditDialog(
    repeater: RepeaterEntry,
    field: QuickEditField,
    onDismiss: () -> Unit,
    viewModel: RepeatersViewModel,
) {
    var interval by remember { mutableStateOf(repeater.interval) }
    var startTime by remember { mutableStateOf(repeater.startTimeOfDay.orEmpty()) }
    var threshold by remember { mutableStateOf(repeater.conversationThreshold.toString()) }
    var maxAge by remember { mutableStateOf(repeater.maxAge.orEmpty()) }
    var maxTriggers by remember { mutableStateOf(repeater.maxTriggers?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }

    val title = when (field) {
        QuickEditField.INTERVAL -> "Edit interval"
        QuickEditField.START_TIME -> "Edit start time"
        QuickEditField.THRESHOLD -> "Conversation threshold"
        QuickEditField.EXPIRY -> "Edit expiry"
    }

    fun trySave() {
        error = null
        when (field) {
            QuickEditField.INTERVAL -> {
                if (!IntervalRegex.matches(interval.trim())) {
                    error = "Interval must be in HH:MM:SS format."
                    return
                }
                viewModel.update(repeater.id, interval = interval.trim())
            }

            QuickEditField.START_TIME -> {
                val trimmed = startTime.trim()
                if (trimmed.isNotEmpty() && !StartTimeRegex.matches(trimmed)) {
                    error = "Start time must be in HH:MM format."
                    return
                }
                viewModel.update(repeater.id, startTimeOfDay = Patch.Value(trimmed.ifEmpty { null }))
            }

            QuickEditField.THRESHOLD -> {
                val value = threshold.trim().toIntOrNull()
                if (value == null || value < 1) {
                    error = "Threshold must be at least 1 message per minute."
                    return
                }
                viewModel.update(repeater.id, conversationThreshold = value)
            }

            QuickEditField.EXPIRY -> {
                val ageTrimmed = maxAge.trim()
                val triggersTrimmed = maxTriggers.trim()
                if (ageTrimmed.isNotEmpty() && !MaxAgeRegex.matches(ageTrimmed)) {
                    error = "Max age must look like 7.00:00:00 (days.hours:minutes:seconds)."
                    return
                }
                if (triggersTrimmed.isNotEmpty() && triggersTrimmed.toIntOrNull() == null) {
                    error = "Max triggers must be a whole number."
                    return
                }
                viewModel.update(
                    repeater.id,
                    maxAge = Patch.Value(ageTrimmed.ifEmpty { null }),
                    maxTriggers = Patch.Value(triggersTrimmed.toIntOrNull()),
                )
            }
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (field) {
                    QuickEditField.INTERVAL -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IntervalPresets.take(3).forEach { preset ->
                                TextButton(onClick = { interval = preset.value }) { Text(preset.label) }
                            }
                        }
                        MewdekoTextField(
                            value = interval,
                            onValueChange = { interval = it },
                            label = "Interval (HH:MM:SS)",
                            placeholder = "01:00:00",
                        )
                    }

                    QuickEditField.START_TIME -> MewdekoTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = "Start time (HH:MM)",
                        placeholder = "Leave empty to disable",
                    )

                    QuickEditField.THRESHOLD -> MewdekoTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = "Messages per minute",
                        numeric = true,
                    )

                    QuickEditField.EXPIRY -> {
                        MewdekoTextField(
                            value = maxAge,
                            onValueChange = { maxAge = it },
                            label = "Max age",
                            placeholder = "7.00:00:00 for 7 days",
                        )
                        MewdekoTextField(
                            value = maxTriggers,
                            onValueChange = { maxTriggers = it },
                            label = "Max displays",
                            placeholder = "Leave empty for unlimited",
                            numeric = true,
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = ::trySave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
