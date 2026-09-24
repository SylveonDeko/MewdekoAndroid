package dev.mewdeko.mobile.feature.statchannels

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Rotate90DegreesCcw
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
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

    var editorTarget by remember { mutableStateOf<StatChannelEditorTarget?>(null) }
    var showDefaults by remember { mutableStateOf(false) }
    var showCatalog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StatChannel?>(null) }

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
            NewItemFab(
                label = "New stat channel",
                onClick = { editorTarget = StatChannelEditorTarget.New },
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
                    actionLabel = "New stat channel",
                    onAction = { editorTarget = StatChannelEditorTarget.New },
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
                    TextButton(
                        onClick = { editorTarget = StatChannelEditorTarget.Existing(channel) },
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                }
            }
        }
    }

    editorTarget?.let { target ->
        StatChannelEditor(
            existing = (target as? StatChannelEditorTarget.Existing)?.channel,
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
            onDraftChanged = { draft ->
                if (draft != null) viewModel.refreshPreview(draft) else viewModel.clearPreview()
            },
            onDismiss = {
                editorTarget = null
                viewModel.clearPreview()
            },
            onSave = { draft ->
                when (target) {
                    StatChannelEditorTarget.New -> viewModel.add(draft)
                    is StatChannelEditorTarget.Existing -> viewModel.update(target.channel.channelId, draft)
                }
                editorTarget = null
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

/** What the stat channel editor is working on. Creating and editing share one editor. */
private sealed interface StatChannelEditorTarget {
    /** A new stat channel, opened from the New stat channel button or the empty state. */
    data object New : StatChannelEditorTarget

    /** An existing stat channel, opened from its Edit button. */
    data class Existing(val channel: StatChannel) : StatChannelEditorTarget
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

/**
 * The guild wide defaults for new stat channels. Three inputs and no preview, so it is a short
 * [FormSheet] rather than a dialog form.
 */
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

    FormSheet(
        title = "Defaults for new channels",
        confirmLabel = "Save",
        confirmEnabled = interval >= minimumInterval(mechanism),
        onConfirm = {
            onSave(
                StatChannelSettings(
                    defaultMechanism = mechanism.raw,
                    defaultIntervalMinutes = interval,
                    defaultDisplayStyle = style,
                )
            )
        },
        onDismiss = onDismiss,
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
        IntervalField(
            interval = interval,
            minimum = minimumInterval(mechanism),
            onChange = { interval = it },
        )
    }
}

/**
 * The full screen editor for a stat channel, shared by creating ([existing] null) and editing so
 * both look and behave the same. It has a live name preview and can need a date, so it is a
 * [FullScreenEditor] rather than a sheet.
 *
 * [onDraftChanged] receives the current draft (or null before a counter is chosen) whenever an
 * input that affects the preview changes. [onSave] receives the finished draft, with fields the
 * chosen counter does not use left null.
 */
@Composable
private fun StatChannelEditor(
    existing: StatChannel?,
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
    onDraftChanged: (StatChannelDraft?) -> Unit,
    onDismiss: () -> Unit,
    onSave: (StatChannelDraft) -> Unit,
) {
    val definitions = metadata?.statTypes.orEmpty()
    val initialDefinition = existing
        ?.let { channel -> definitions.firstOrNull { it.type == channel.statType } }
        ?: definitions.firstOrNull()

    var createNew by remember { mutableStateOf(existing == null) }
    var channelId by remember { mutableStateOf(existing?.channelId) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var definition by remember(definitions) { mutableStateOf(initialDefinition) }
    var template by remember(definitions) {
        mutableStateOf(existing?.template ?: initialDefinition?.defaultTemplate.orEmpty())
    }
    var style by remember {
        mutableIntStateOf(existing?.displayStyle ?: settings.defaultDisplayStyle)
    }
    var mechanism by remember {
        mutableStateOf(existing?.mechanism ?: StatMechanism.from(settings.defaultMechanism))
    }
    var interval by remember {
        mutableIntStateOf(existing?.updateIntervalMinutes ?: settings.defaultIntervalMinutes)
    }
    var roleId by remember { mutableStateOf(existing?.roleId) }
    var countdownDate by remember { mutableStateOf(existing?.countdownDate) }
    var goal by remember {
        mutableStateOf(existing?.goalTarget?.takeIf { it > 0 }?.toString() ?: "100")
    }
    var counterName by remember(definitions) {
        mutableStateOf(
            existing?.targetName
                ?.takeIf { initialDefinition?.needs == StatRequirement.COUNTER_NAME }
                .orEmpty()
        )
    }
    var targetId by remember { mutableStateOf(existing?.targetId) }

    val needs = definition?.needs ?: StatRequirement.NONE
    val minimum = minimumInterval(mechanism)

    val draft = definition?.let { picked ->
        StatChannelDraft(
            channelId = when {
                existing != null -> existing.channelId
                createNew -> "0"
                else -> channelId.orEmpty()
            },
            categoryId = categoryId.takeIf { existing == null && createNew },
            statType = picked.type,
            template = template,
            displayStyle = style,
            mechanism = mechanism,
            intervalMinutes = interval,
            roleId = roleId.takeIf { needs == StatRequirement.ROLE },
            countdownDate = countdownDate.takeIf { needs == StatRequirement.DATE },
            goalTarget = (goal.toIntOrNull() ?: 0).takeIf { needs == StatRequirement.GOAL },
            targetId = targetId?.toLongOrNull()?.takeIf {
                needs == StatRequirement.COUNTING_CHANNEL || needs == StatRequirement.MINECRAFT_SERVER
            },
            targetName = counterName.takeIf { needs == StatRequirement.COUNTER_NAME },
        )
    }
    val initialDraft = remember(definitions) { draft }

    LaunchedEffect(definition, template, style, roleId, countdownDate, goal, targetId, counterName) {
        onDraftChanged(draft)
    }

    FullScreenEditor(
        title = if (existing == null) "New stat channel" else "Edit stat channel",
        onClose = onDismiss,
        confirmLabel = if (existing == null) "Add" else "Save",
        confirmEnabled = draft != null &&
            (existing != null || createNew || channelId != null) &&
            template.isNotBlank() &&
            interval >= minimum &&
            (needs != StatRequirement.ROLE || roleId != null) &&
            (needs != StatRequirement.DATE || countdownDate != null) &&
            (needs != StatRequirement.COUNTER_NAME || counterName.isNotBlank()) &&
            (needs != StatRequirement.COUNTING_CHANNEL || targetId != null) &&
            (needs != StatRequirement.MINECRAFT_SERVER || targetId != null),
        onConfirm = { draft?.let(onSave) },
        hasUnsavedChanges = draft != initialDraft,
    ) {
        SectionCard {
            SectionCardHeader("Channel", Icons.AutoMirrored.Filled.VolumeUp)
            if (existing != null) {
                InfoRow(label = "Voice channel", value = existing.channelName)
            } else {
                EnumPicker(
                    label = "Channel",
                    options = listOf(
                        EnumOption(
                            value = true,
                            title = "Create new channel",
                            description = "The bot makes a fresh voice channel for this counter.",
                        ),
                        EnumOption(
                            value = false,
                            title = "Use existing channel",
                            description = "The bot takes over a voice channel you already have.",
                        ),
                    ),
                    selected = createNew,
                    onSelect = { createNew = it },
                )

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
            }
        }

        SectionCard {
            SectionCardHeader("Counter", Icons.Default.Equalizer)
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

        SectionCard {
            SectionCardHeader("Name", Icons.Default.Numbers)
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
        }

        SectionCard {
            SectionCardHeader("Updates", Icons.Default.Rotate90DegreesCcw)
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
        }
    }
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
