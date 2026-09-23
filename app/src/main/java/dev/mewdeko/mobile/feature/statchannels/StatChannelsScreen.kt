package dev.mewdeko.mobile.feature.statchannels

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.shortDate
import dev.mewdeko.mobile.util.shortDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Voice channels whose names carry live server statistics. */
@Composable
fun StatChannelsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StatChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var showDefaults by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StatChannel?>(null) }
    var editingTemplate by remember { mutableStateOf<StatChannel?>(null) }
    var editingDelivery by remember { mutableStateOf<StatChannel?>(null) }

    FeatureScaffold(
        title = "Stat Channels",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add channel") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Equalizer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Stat channels", "${state.channels.size}", Modifier.weight(1f))
                StatTile(
                    label = "Counters",
                    value = "${state.metadata?.statTypes?.size ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Each channel picks how it updates. Renaming is gentle on Discord's limits " +
                    "but caps at one update every 5 minutes; recreating refreshes far faster at " +
                    "the cost of a changing channel ID.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                TextButton(onClick = { showDefaults = true }) { Text("Defaults for new channels") }
                TextButton(onClick = { showCatalog = true }) { Text("Browse counters") }
            }
        }

        if (state.channels.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No stat channels configured yet.",
                    icon = Icons.Default.Equalizer,
                )
            }
        } else {
            state.channels.forEach { channel ->
                SectionCard {
                    SectionCardHeader(
                        title = channel.channelName,
                        icon = channel.icon(state.metadata),
                        trailing = {
                            IconButton(onClick = { pendingDelete = channel }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove stat channel",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip(channel.typeName ?: "Stat")
                        channel.currentValue?.let { TagChip("Now: $it") }
                        channel.roleName?.let { TagChip("@$it") }
                        channel.targetName?.let { TagChip(it) }
                        channel.goalTarget?.takeIf { it > 0 }?.let { TagChip("Goal $it") }
                        channel.countdownDate?.let { TagChip(it.shortDate()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip(channel.mechanism.label)
                        TagChip("Every ${channel.updateIntervalMinutes}m")
                        channel.styleName?.let { TagChip(it) }
                    }
                    channel.template?.takeIf { it.isNotBlank() }?.let { template ->
                        Text(
                            text = template,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editingTemplate = channel }) { Text("Template") }
                        TextButton(onClick = { editingDelivery = channel }) { Text("Style & updates") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddStatChannelDialog(
            metadata = state.metadata,
            settings = state.settings,
            voiceOptions = state.availableVoiceChannels.map { SelectorOption(it.id, it.name) },
            categoryOptions = state.availableCategories.map { SelectorOption(it.id, it.name) },
            roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) },
            countingOptions = state.countingChannels.map {
                SelectorOption(
                    id = it.channelId,
                    name = it.channelName ?: it.channelId,
                    subtitle = "At ${it.currentNumber}",
                )
            },
            minecraftOptions = state.minecraftServers.map {
                SelectorOption(
                    id = it.id.toString(),
                    name = it.name,
                    subtitle = it.address.takeIf { address -> address.isNotBlank() },
                )
            },
            preview = state.preview,
            previewPending = state.previewPending,
            minimumInterval = viewModel::minimumInterval,
            onPreviewInputsChanged = { definition, template, style, roleId, countdownDate, goal, targetId, counterName ->
                if (definition != null) {
                    viewModel.refreshPreview(
                        statType = definition.type,
                        template = template,
                        displayStyle = style,
                        roleId = roleId.takeIf { definition.needs == StatRequirement.ROLE },
                        countdownDate = countdownDate.takeIf { definition.needs == StatRequirement.DATE },
                        goalTarget = goal.takeIf { definition.needs == StatRequirement.GOAL },
                        targetId = targetId?.toLongOrNull(),
                        targetName = counterName.takeIf {
                            definition.needs == StatRequirement.COUNTER_NAME
                        },
                    )
                }
            },
            onDismiss = {
                showAdd = false
                viewModel.clearPreview()
            },
            onAdd = { channelId, categoryId, definition, template, style, mechanism, interval, roleId,
                countdownDate, goal, targetId, counterName ->
                viewModel.add(
                    channelId = channelId,
                    categoryId = categoryId,
                    statType = definition.type,
                    template = template,
                    displayStyle = style,
                    mechanism = mechanism,
                    intervalMinutes = interval,
                    roleId = roleId.takeIf { definition.needs == StatRequirement.ROLE },
                    countdownDate = countdownDate.takeIf { definition.needs == StatRequirement.DATE },
                    goalTarget = goal.takeIf { definition.needs == StatRequirement.GOAL },
                    targetId = targetId?.toLongOrNull(),
                    targetName = counterName.takeIf {
                        definition.needs == StatRequirement.COUNTER_NAME
                    },
                )
                showAdd = false
                viewModel.clearPreview()
            },
        )
    }

    if (showCatalog) {
        CounterCatalogDialog(metadata = state.metadata, onDismiss = { showCatalog = false })
    }

    if (showDefaults) {
        DefaultsDialog(
            metadata = state.metadata,
            settings = state.settings,
            minimumInterval = viewModel::minimumInterval,
            onDismiss = { showDefaults = false },
            onSave = {
                viewModel.updateSettings(it)
                showDefaults = false
            },
        )
    }

    editingTemplate?.let { channel ->
        val definition = state.metadata?.statTypes?.firstOrNull { it.type == channel.statType }
        var draft by remember(channel.channelId) { mutableStateOf(channel.template.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            title = { Text("Channel name template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = "Template",
                        placeholder = definition?.defaultTemplate ?: "Members: %count%",
                    )
                    PlaceholderChips(
                        placeholders = buildList {
                            addAll(state.metadata?.commonPlaceholders.orEmpty())
                            addAll(definition?.placeholders.orEmpty())
                        },
                        onInsert = { draft += it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTemplate(channel.channelId, draft)
                        editingTemplate = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingTemplate = null }) { Text("Cancel") }
            },
        )
    }

    editingDelivery?.let { channel ->
        val definition = state.metadata?.statTypes?.firstOrNull { it.type == channel.statType }
        var style by remember(channel.channelId) { mutableIntStateOf(channel.displayStyle) }
        var mechanism by remember(channel.channelId) { mutableStateOf(channel.mechanism) }
        var interval by remember(channel.channelId) {
            mutableIntStateOf(channel.updateIntervalMinutes)
        }
        val minimum = viewModel.minimumInterval(mechanism)

        AlertDialog(
            onDismissRequest = { editingDelivery = null },
            title = { Text("Style & updates") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StylePicker(state.metadata, style, definition) { style = it }
                    MechanismPicker(mechanism) {
                        mechanism = it
                        interval = interval.coerceAtLeast(viewModel.minimumInterval(it))
                    }
                    IntervalField(
                        interval = interval,
                        minimum = minimum,
                        realtimeHint = definition?.realtime == true,
                        onChange = { interval = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDelivery(channel.channelId, style, mechanism, interval)
                        editingDelivery = null
                    },
                    enabled = interval >= minimum,
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingDelivery = null }) { Text("Cancel") }
            },
        )
    }

    pendingDelete?.let { channel ->
        ConfirmDialog(
            title = "Remove stat channel?",
            message = "${channel.channelName} stops being renamed. The channel itself is kept.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(channel.channelId) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Shows the draft template rendered against live guild data, as the channel name will appear. */
@Composable
private fun PreviewRow(preview: String, pending: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = when {
                preview.isNotBlank() -> preview
                pending -> "Rendering..."
                else -> "Preview unavailable"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (preview.isNotBlank()) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Picks how the resolved number is rendered, showing each style's worked example. When
 * [definition] does not produce a plain number, notes that the style only affects `%count.raw%`.
 */
@Composable
private fun StylePicker(
    metadata: StatChannelMetadata?,
    selected: Int,
    definition: StatTypeDefinition? = null,
    onSelect: (Int) -> Unit,
) {
    val options = metadata?.displayStyles.orEmpty().map {
        SelectorOption(it.style.toString(), it.name, subtitle = it.example)
    }
    if (options.isEmpty()) return

    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Numbers),
        options = options,
        placeholder = "Pick a style",
        label = "Counter style",
        selectedId = selected.toString(),
        onSelect = { raw -> onSelect(raw?.toIntOrNull() ?: selected) },
    )
    if (definition != null && definition.valueKind != 0) {
        Text(
            text = "This counter produces ${definition.valueKindName.lowercase()} rather than a " +
                "number, so the style only affects %count.raw%.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Picks how updates reach Discord, surfacing the tradeoff for the chosen mechanism. */
@Composable
private fun MechanismPicker(
    selected: StatMechanism,
    onSelect: (StatMechanism) -> Unit,
) {
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.Rotate90DegreesCcw),
        options = StatMechanism.entries.map {
            SelectorOption(it.raw.toString(), it.label, subtitle = it.blurb)
        },
        placeholder = "Pick how updates are pushed",
        label = "Update mechanism",
        selectedId = selected.raw.toString(),
        onSelect = { raw -> onSelect(StatMechanism.from(raw?.toIntOrNull() ?: selected.raw)) },
    )
    selected.caution?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Chooses the refresh cadence in minutes, from what the chosen mechanism can sustain up to the
 * bot's cap of 1440 (one day). A plain number field, since a slider cannot address that range with
 * useful precision.
 */
@Composable
private fun IntervalField(
    interval: Int,
    minimum: Int,
    realtimeHint: Boolean = false,
    onChange: (Int) -> Unit,
) {
    var text by remember(interval) { mutableStateOf(interval.toString()) }
    MewdekoTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter(Char::isDigit).take(4)
            text = digits
            digits.toIntOrNull()?.let { onChange(it.coerceIn(1, MaxIntervalMinutes)) }
        },
        label = "Refresh every (minutes)",
        numeric = true,
        isError = interval < minimum,
        supportingText = buildString {
            append("Minimum $minimum minute${if (minimum == 1) "" else "s"} for this mechanism, up to $MaxIntervalMinutes.")
            if (realtimeHint) append(" This counter changes constantly, so a short interval is worth it.")
        },
    )
}

/** Tappable chips that insert a placeholder token at the end of the template on tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderChips(placeholders: List<String>, onInsert: (String) -> Unit) {
    if (placeholders.isEmpty()) {
        Text(
            text = "Use %count% for the current value.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        placeholders.forEach { placeholder ->
            TagChip(label = placeholder, onClick = { onInsert(placeholder) })
        }
    }
}

/**
 * Picks an exact target date and time for a countdown stat, via the platform date and time
 * pickers. Unlike a relative day slider, the target stays fixed once chosen.
 */
@Composable
private fun CountdownDateField(value: Instant?, onChange: (Instant?) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Target date",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val base = value?.let { ZonedDateTime.ofInstant(it, ZoneId.systemDefault()) }
                        ?: ZonedDateTime.now().plusDays(1)
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
                    text = value?.let { "  ${it.shortDateTime()} (${it.relativeToNow()})" } ?: "  Pick a date",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DefaultsDialog(
    metadata: StatChannelMetadata?,
    settings: StatChannelSettings,
    minimumInterval: (StatMechanism) -> Int,
    onDismiss: () -> Unit,
    onSave: (StatChannelSettings) -> Unit,
) {
    var style by remember { mutableIntStateOf(settings.defaultDisplayStyle) }
    var mechanism by remember { mutableStateOf(StatMechanism.from(settings.defaultMechanism)) }
    var interval by remember { mutableIntStateOf(settings.defaultIntervalMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Defaults for new channels") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Applies to stat channels created from now on. Existing channels keep " +
                        "their own settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StylePicker(metadata, style, onSelect = { style = it })
                MechanismPicker(mechanism) {
                    mechanism = it
                    interval = interval.coerceAtLeast(minimumInterval(it))
                }
                val defaultsMinimum = minimumInterval(mechanism)
                IntervalField(
                    interval = interval,
                    minimum = defaultsMinimum,
                    onChange = { interval = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        StatChannelSettings(
                            defaultMechanism = mechanism.raw,
                            defaultIntervalMinutes = interval,
                            defaultDisplayStyle = style,
                        )
                    )
                },
                enabled = interval >= minimumInterval(mechanism),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStatChannelDialog(
    metadata: StatChannelMetadata?,
    settings: StatChannelSettings,
    voiceOptions: List<SelectorOption>,
    categoryOptions: List<SelectorOption>,
    roleOptions: List<SelectorOption>,
    countingOptions: List<SelectorOption>,
    minecraftOptions: List<SelectorOption>,
    preview: String,
    previewPending: Boolean,
    minimumInterval: (StatMechanism) -> Int,
    onPreviewInputsChanged: (
        definition: StatTypeDefinition?,
        template: String,
        style: Int,
        roleId: String?,
        countdownDate: Instant?,
        goal: Int,
        targetId: String?,
        counterName: String,
    ) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (
        channelId: String,
        categoryId: String?,
        definition: StatTypeDefinition,
        template: String,
        style: Int,
        mechanism: StatMechanism,
        interval: Int,
        roleId: String?,
        countdownDate: Instant?,
        goal: Int,
        targetId: String?,
        counterName: String,
    ) -> Unit,
) {
    val definitions = metadata?.statTypes.orEmpty()
    var createNew by remember { mutableStateOf(true) }
    var channelId by remember { mutableStateOf<String?>(null) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var definition by remember(definitions) { mutableStateOf(definitions.firstOrNull()) }
    var template by remember(definitions) {
        mutableStateOf(definitions.firstOrNull()?.defaultTemplate.orEmpty())
    }
    var style by remember { mutableIntStateOf(settings.defaultDisplayStyle) }
    var mechanism by remember { mutableStateOf(StatMechanism.from(settings.defaultMechanism)) }
    var interval by remember { mutableIntStateOf(settings.defaultIntervalMinutes) }
    var roleId by remember { mutableStateOf<String?>(null) }
    var countdownDate by remember { mutableStateOf<Instant?>(null) }
    var goal by remember { mutableStateOf("100") }
    var counterName by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf<String?>(null) }

    val needs = definition?.needs ?: StatRequirement.NONE
    val minimum = minimumInterval(mechanism)

    LaunchedEffect(definition, template, style, roleId, countdownDate, goal, targetId, counterName) {
        onPreviewInputsChanged(
            definition,
            template,
            style,
            roleId,
            countdownDate,
            goal.toIntOrNull() ?: 0,
            targetId,
            counterName,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add stat channel") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = createNew,
                        onClick = { createNew = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Create new channel") }
                    SegmentedButton(
                        selected = !createNew,
                        onClick = { createNew = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Use existing channel") }
                }

                if (createNew) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Tag),
                        options = categoryOptions,
                        placeholder = "No category",
                        label = "Category (optional)",
                        selectedId = categoryId,
                        onSelect = { categoryId = it },
                    )
                } else {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.VolumeUp),
                        options = voiceOptions,
                        placeholder = "Pick a voice channel",
                        label = "Channel",
                        selectedId = channelId,
                        onSelect = { channelId = it },
                    )
                }

                if (definitions.isEmpty()) {
                    Text(
                        text = "Could not load the counter catalogue. Pull to refresh and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Equalizer),
                        options = definitions.map {
                            SelectorOption(
                                id = it.type.toString(),
                                name = "${it.category} · ${it.name}",
                                subtitle = it.example,
                            )
                        },
                        placeholder = "Pick a counter",
                        label = "Displays",
                        selectedId = definition?.type?.toString(),
                        onSelect = { raw ->
                            val picked = definitions.firstOrNull { it.type == raw?.toIntOrNull() }
                            if (picked != null) {
                                definition = picked
                                template = picked.defaultTemplate
                                style = picked.recommendedStyle
                                roleId = null
                                counterName = ""
                                targetId = null
                                countdownDate = null
                                if (picked.realtime && mechanism != StatMechanism.RENAME) {
                                    interval = minimumInterval(mechanism)
                                }
                            }
                        },
                    )
                }

                definition?.let {
                    Text(
                        text = "${it.description}\nExample: ${it.example}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                MewdekoTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = "Name template",
                )
                PlaceholderChips(
                    placeholders = buildList {
                        addAll(metadata?.commonPlaceholders.orEmpty())
                        addAll(definition?.placeholders.orEmpty())
                    },
                    onInsert = { template += it },
                )

                PreviewRow(preview = preview, pending = previewPending)

                StylePicker(metadata, style, definition) { style = it }
                MechanismPicker(mechanism) {
                    mechanism = it
                    interval = interval.coerceAtLeast(minimumInterval(it))
                }
                IntervalField(
                    interval = interval,
                    minimum = minimum,
                    realtimeHint = definition?.realtime == true,
                    onChange = { interval = it },
                )

                if (needs == StatRequirement.ROLE) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "Pick a role",
                        label = "Count members with role",
                        selectedId = roleId,
                        onSelect = { roleId = it },
                    )
                }
                if (needs == StatRequirement.DATE) {
                    CountdownDateField(value = countdownDate, onChange = { countdownDate = it })
                }
                if (needs == StatRequirement.GOAL) {
                    MewdekoTextField(
                        value = goal,
                        onValueChange = { goal = it.filter(Char::isDigit) },
                        label = "Member goal",
                        numeric = true,
                    )
                }
                if (needs == StatRequirement.COUNTER_NAME) {
                    MewdekoTextField(
                        value = counterName,
                        onValueChange = { counterName = it },
                        label = "Twitch counter name",
                        placeholder = "deaths",
                        supportingText = "The name of a counter your Twitch chat commands update.",
                    )
                }
                if (needs == StatRequirement.COUNTING_CHANNEL) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Tag),
                        options = countingOptions,
                        placeholder = "Pick a counting channel",
                        label = "Counting channel",
                        selectedId = targetId,
                        onSelect = { targetId = it },
                    )
                    if (countingOptions.isEmpty()) {
                        Text(
                            text = "No counting channels are set up in this server yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (needs == StatRequirement.MINECRAFT_SERVER) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Dns),
                        options = minecraftOptions,
                        placeholder = "Pick a Minecraft server",
                        label = "Minecraft server",
                        selectedId = targetId,
                        onSelect = { targetId = it },
                    )
                    if (minecraftOptions.isEmpty()) {
                        Text(
                            text = "No Minecraft servers are configured in this server yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val picked = definition ?: return@Button
                    onAdd(
                        if (createNew) "0" else channelId.orEmpty(),
                        if (createNew) categoryId else null,
                        picked,
                        template,
                        style,
                        mechanism,
                        interval,
                        roleId,
                        countdownDate,
                        goal.toIntOrNull() ?: 0,
                        targetId,
                        counterName,
                    )
                },
                enabled = (createNew || channelId != null) &&
                    definition != null &&
                    template.isNotBlank() &&
                    interval >= minimum &&
                    (needs != StatRequirement.ROLE || roleId != null) &&
                    (needs != StatRequirement.DATE || countdownDate != null) &&
                    (needs != StatRequirement.COUNTER_NAME || counterName.isNotBlank()) &&
                    (needs != StatRequirement.COUNTING_CHANNEL || targetId != null) &&
                    (needs != StatRequirement.MINECRAFT_SERVER || targetId != null),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Read-only reference: how each counter style renders, and every counter grouped by category. */
@Composable
private fun CounterCatalogDialog(metadata: StatChannelMetadata?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Counter catalogue") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (metadata == null) {
                    Text(
                        text = "Could not load the catalogue. Pull to refresh and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    return@Column
                }

                Text(
                    text = "Counter styles",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "How each style renders 1,234 against a target of 2,000.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                metadata.displayStyles.forEach { style ->
                    InfoRow(label = style.name, value = style.example)
                }

                Text(
                    text = "Available counters",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                metadata.statTypes.groupBy { it.category }.forEach { (category, definitions) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        definitions.forEach { definition ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = definition.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (definition.realtime) {
                                        TagChip("Live")
                                    }
                                }
                                Text(
                                    text = definition.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Example: ${definition.example}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val MaxIntervalMinutes = 1440
