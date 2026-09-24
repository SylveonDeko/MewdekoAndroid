package dev.mewdeko.mobile.feature.embed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedField
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.EmbedSpec
import dev.mewdeko.mobile.core.model.normalizedEmbedHex
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ColorPickerField
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
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
import dev.mewdeko.mobile.core.ui.TabLevel
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

private const val MAX_EMBEDS = 10
private const val MAX_FIELDS = 25

/** How many edits the undo/redo history keeps, matching the dashboard's cap. */
private const val MAX_HISTORY = 50

/** How long editing pauses before a snapshot is pushed onto the history. */
private const val HISTORY_DEBOUNCE_MS = 350L

/**
 * Compact trigger that summarises an [EmbedMessage] and opens the full editor.
 *
 * Feature screens embed this inline; the editing surface itself lives in a
 * full-height modal sheet so it does not compete with the settings around it.
 *
 * Pass [allowComponents] as false for messages whose buttons and dropdowns
 * the feature builds itself: the editor hides its buttons tab and saves the
 * message without any. Pass [allowSend] as false to hide the send tab.
 */
@Composable
fun EmbedMessageEditor(
    message: EmbedMessage,
    onMessageChange: (EmbedMessage) -> Unit,
    modifier: Modifier = Modifier,
    additionalPlaceholders: List<Placeholder> = emptyList(),
    allowComponents: Boolean = true,
    allowSend: Boolean = true,
) {
    var editing by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val blurb = if (allowComponents) "Rich embeds, buttons, and select menus." else "Text and rich embeds."

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = { editing = true },
            shape = MaterialTheme.shapes.large,
            color = primary.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.padding(top = 2.dp).size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Message", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (message.isEmpty) {
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .border(
                                    BorderStroke(1.dp, primary.copy(alpha = 0.2f)),
                                    MaterialTheme.shapes.medium,
                                )
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No message set. The bot's default is used.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth(),
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) { EmbedPreview(message) }
                        }
                        TextButton(
                            onClick = { onMessageChange(EmbedMessage()) },
                            modifier = Modifier.padding(top = 4.dp),
                        ) { Text("Reset") }
                    }
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editing) {
        EmbedEditorSheet(
            initial = message,
            onDismiss = { editing = false },
            onSave = {
                onMessageChange(it)
                editing = false
            },
            additionalPlaceholders = additionalPlaceholders,
            allowComponents = allowComponents,
            allowSend = allowSend,
        )
    }
}

/**
 * The full composer. With [allowComponents] false the buttons tab is hidden
 * and any buttons or dropdowns are dropped from the draft; with [allowSend]
 * false the send tab is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbedEditorSheet(
    initial: EmbedMessage,
    onDismiss: () -> Unit,
    onSave: (EmbedMessage) -> Unit,
    additionalPlaceholders: List<Placeholder> = emptyList(),
    allowComponents: Boolean = true,
    allowSend: Boolean = true,
) {
    val tabs = remember(allowComponents, allowSend) {
        ComposerTabs.filter { (allowComponents || it.id != "components") && (allowSend || it.id != "send") }
    }
    val strip: (EmbedMessage) -> EmbedMessage = { if (allowComponents) it else it.copy(components = emptyList()) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedEmbed by remember { mutableIntStateOf(0) }
    var confirmingClear by remember { mutableStateOf(false) }
    val gradient = LocalGuildPalette.current.gradient

    var history by remember { mutableStateOf(listOf(strip(initial))) }
    var historyIndex by remember { mutableIntStateOf(0) }
    var pendingDraft by remember { mutableStateOf(strip(initial)) }
    var restoringHistory by remember { mutableStateOf(false) }
    val draft = pendingDraft

    fun setDraft(new: EmbedMessage) {
        restoringHistory = false
        pendingDraft = strip(new)
    }

    fun jumpTo(index: Int) {
        restoringHistory = true
        historyIndex = index
        pendingDraft = history[index]
    }

    LaunchedEffect(pendingDraft) {
        if (restoringHistory) return@LaunchedEffect
        delay(HISTORY_DEBOUNCE_MS.milliseconds)
        if (pendingDraft.serialize() == history[historyIndex].serialize()) return@LaunchedEffect
        val next = (history.take(historyIndex + 1) + pendingDraft).takeLast(MAX_HISTORY)
        history = next
        historyIndex = next.lastIndex
    }

    MewdekoBottomSheet(onDismissRequest = onDismiss, showClose = false) {
        val dismissSheet = LocalSheetDismiss.current
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(gradient.map { it.copy(alpha = 0.12f) }))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Message", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (allowComponents) "Rich embeds, buttons, and select menus." else "Text and rich embeds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { jumpTo(historyIndex - 1) }, enabled = historyIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { jumpTo(historyIndex + 1) },
                        enabled = historyIndex < history.lastIndex,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { jumpTo(0) }, enabled = historyIndex > 0) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Cancel edits")
                    }
                    IconButton(onClick = { confirmingClear = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear all",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    TextButton(onClick = dismissSheet) { Text("Cancel") }
                    Button(onClick = { onSave(strip(draft)) }, modifier = Modifier.weight(1f)) {
                        Text("Save")
                    }
                }
            }

            if (confirmingClear) {
                ConfirmDialog(
                    title = "Clear everything?",
                    message = if (allowComponents) {
                        "This removes the content, every embed, and every component."
                    } else {
                        "This removes the content and every embed."
                    },
                    confirmLabel = "Clear all",
                    onConfirm = {
                        setDraft(EmbedMessage())
                        selectedEmbed = 0
                        confirmingClear = false
                    },
                    onDismiss = { confirmingClear = false },
                )
            }

            SectionTabs(
                tabs = tabs,
                selectedId = tabs[tab].id,
                onSelect = { id -> tab = tabs.indexOfFirst { it.id == id } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (tabs[tab].id) {
                    "templates" -> {
                        TemplatesPanel(
                            onApply = { spec ->
                                val embeds = if (draft.embeds.isEmpty()) {
                                    listOf(spec)
                                } else {
                                    draft.embeds.toMutableList().apply { set(0, spec) }
                                }
                                setDraft(draft.copy(embeds = embeds))
                                selectedEmbed = 0
                                tab = tabs.indexOfFirst { it.id == "editor" }
                            },
                            onStartFromScratch = {
                                setDraft(EmbedMessage())
                                selectedEmbed = 0
                                tab = tabs.indexOfFirst { it.id == "editor" }
                            },
                        )
                        return@Column
                    }

                    "components" -> {
                        ComponentEditor(message = draft, onMessageChange = { setDraft(it) })
                        return@Column
                    }

                    "preview" -> {
                        SectionCard {
                            SectionCardHeader("Live preview", Icons.Default.Visibility)
                            EmbedPreview(draft)
                        }
                        ValidationPanel(draft)
                        return@Column
                    }

                    "json" -> {
                        JsonPanel(draft = draft, onDraftChange = { setDraft(it) })
                        return@Column
                    }

                    "saved" -> {
                        SavedPanel(draft = draft, onLoad = { setDraft(it) })
                        return@Column
                    }

                    "send" -> {
                        SendPanel(draft = draft)
                        return@Column
                    }
                }

                SectionCard {
                    SectionCardHeader("Message content", Icons.Default.Edit)
                    PlaceholderField(
                        value = draft.content,
                        onValueChange = { setDraft(draft.copy(content = it)) },
                        label = "Content",
                        singleLine = false,
                        minLines = 3,
                        maxLength = Limits.CONTENT,
                        additionalPlaceholders = additionalPlaceholders,
                    )
                    Text(
                        text = "Plain text shown above the embed. Discord renders Markdown and " +
                            "mentions here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SectionCard {
                    SectionCardHeader(
                        title = "Embeds",
                        icon = Icons.Default.Image,
                        trailing = { Text("${draft.embeds.size} / $MAX_EMBEDS") },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (draft.embeds.isNotEmpty()) {
                            SectionTabs(
                                tabs = draft.embeds.indices.map { SectionTab("embed-$it", "Embed ${it + 1}") },
                                selectedId = "embed-${selectedEmbed.coerceIn(0, draft.embeds.lastIndex)}",
                                onSelect = { id -> selectedEmbed = id.removePrefix("embed-").toIntOrNull() ?: 0 },
                                level = TabLevel.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (draft.embeds.size < MAX_EMBEDS) {
                            AssistChip(
                                onClick = {
                                    val updated = draft.embeds + EmbedSpec.Blank
                                    setDraft(draft.copy(embeds = updated))
                                    selectedEmbed = updated.lastIndex
                                },
                                label = { Text(if (draft.embeds.isEmpty()) "Add embed" else "Add") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            )
                        }
                    }

                    val index = selectedEmbed.coerceIn(0, (draft.embeds.size - 1).coerceAtLeast(0))
                    val embed = draft.embeds.getOrNull(index)
                    if (embed != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (draft.embeds.size >= MAX_EMBEDS) return@OutlinedButton
                                    val copy = embed.copy()
                                    val updated = draft.embeds.toMutableList()
                                        .apply { add(index + 1, copy) }
                                    setDraft(draft.copy(embeds = updated))
                                    selectedEmbed = index + 1
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Duplicate", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = {
                                    val updated = draft.embeds.filterIndexed { i, _ -> i != index }
                                    setDraft(draft.copy(embeds = updated))
                                    selectedEmbed = (index - 1).coerceAtLeast(0)
                                },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Remove", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }

                val index = selectedEmbed.coerceIn(0, (draft.embeds.size - 1).coerceAtLeast(0))
                draft.embeds.getOrNull(index)?.let { embed ->
                    EmbedSpecEditor(
                        embed = embed,
                        onChange = { updated ->
                            setDraft(
                                draft.copy(
                                    embeds = draft.embeds.toMutableList().apply { set(index, updated) },
                                )
                            )
                        },
                        additionalPlaceholders = additionalPlaceholders,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbedSpecEditor(
    embed: EmbedSpec,
    onChange: (EmbedSpec) -> Unit,
    additionalPlaceholders: List<Placeholder> = emptyList(),
) {
    SectionCard {
        SectionCardHeader("Body", Icons.Default.Edit)
        PlaceholderField(
            value = embed.title,
            onValueChange = { onChange(embed.copy(title = it)) },
            label = "Title",
            maxLength = Limits.TITLE,
            additionalPlaceholders = additionalPlaceholders,
        )
        PlaceholderField(
            value = embed.description,
            onValueChange = { onChange(embed.copy(description = it)) },
            label = "Description",
            singleLine = false,
            minLines = 3,
            maxLength = Limits.DESCRIPTION,
            additionalPlaceholders = additionalPlaceholders,
        )
        MewdekoTextField(
            value = embed.url,
            onValueChange = { onChange(embed.copy(url = it)) },
            label = "Title URL",
        )
        ColorPickerField(
            label = "Color",
            hex = normalizedEmbedHex(embed.color).orEmpty(),
            onHexChange = { onChange(embed.copy(color = it)) },
        )
        Text(
            text = "Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedSpecPreview(embed)
    }

    SectionCard {
        SectionCardHeader("Author", Icons.Default.Person)
        PlaceholderField(
            value = embed.author.name,
            onValueChange = { onChange(embed.copy(author = embed.author.copy(name = it))) },
            label = "Name",
            maxLength = Limits.AUTHOR,
            additionalPlaceholders = additionalPlaceholders,
        )
        MewdekoTextField(
            value = embed.author.url,
            onValueChange = { onChange(embed.copy(author = embed.author.copy(url = it))) },
            label = "URL",
        )
        MewdekoTextField(
            value = embed.author.iconUrl,
            onValueChange = { onChange(embed.copy(author = embed.author.copy(iconUrl = it))) },
            label = "Icon URL",
        )
    }

    SectionCard {
        SectionCardHeader("Images", Icons.Default.Image)
        MewdekoTextField(
            value = embed.thumbnailUrl,
            onValueChange = { onChange(embed.withThumbnail(it)) },
            label = "Thumbnail URL",
        )
        MewdekoTextField(
            value = embed.imageUrl,
            onValueChange = { onChange(embed.withImage(it)) },
            label = "Image URL",
        )
    }

    SectionCard {
        SectionCardHeader("Footer", Icons.Default.Edit)
        PlaceholderField(
            value = embed.footer.text,
            onValueChange = { onChange(embed.copy(footer = embed.footer.copy(text = it))) },
            label = "Text",
            maxLength = Limits.FOOTER,
            additionalPlaceholders = additionalPlaceholders,
        )
        MewdekoTextField(
            value = embed.footer.iconUrl,
            onValueChange = { onChange(embed.copy(footer = embed.footer.copy(iconUrl = it))) },
            label = "Icon URL",
        )
    }

    SectionCard {
        SectionCardHeader(
            title = "Fields",
            icon = Icons.Default.Add,
            trailing = { Text("${embed.fields.size} / $MAX_FIELDS") },
        )
        embed.fields.forEachIndexed { index, field ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Field ${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onChange(
                                    embed.copy(
                                        fields = embed.fields.filterIndexed { i, _ -> i != index },
                                    )
                                )
                            },
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove field ${index + 1}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    PlaceholderField(
                        value = field.name,
                        onValueChange = { value ->
                            onChange(embed.replaceField(index, field.copy(name = value)))
                        },
                        label = "Name",
                        maxLength = Limits.FIELD_NAME,
                        additionalPlaceholders = additionalPlaceholders,
                    )
                    PlaceholderField(
                        value = field.value,
                        onValueChange = { value ->
                            onChange(embed.replaceField(index, field.copy(value = value)))
                        },
                        label = "Value",
                        singleLine = false,
                        minLines = 2,
                        maxLength = Limits.FIELD_VALUE,
                        additionalPlaceholders = additionalPlaceholders,
                    )
                    SwitchRow(
                        title = "Inline",
                        checked = field.inline,
                        onCheckedChange = { value ->
                            onChange(embed.replaceField(index, field.copy(inline = value)))
                        },
                    )
                }
            }
        }

        if (embed.fields.size < MAX_FIELDS) {
            OutlinedButton(
                onClick = { onChange(embed.copy(fields = embed.fields + EmbedField())) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Add field", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun EmbedSpec.replaceField(index: Int, field: EmbedField): EmbedSpec =
    copy(fields = fields.toMutableList().apply { set(index, field) })

/** The composer's tabs. */
private val ComposerTabs = listOf(
    SectionTab("templates", "Templates", Icons.Default.AutoAwesome),
    SectionTab("editor", "Editor", Icons.Default.Edit),
    SectionTab("components", "Components", Icons.Default.SmartButton),
    SectionTab("preview", "Preview", Icons.Default.Visibility),
    SectionTab("json", "JSON", Icons.Default.DataObject),
    SectionTab("saved", "Saved", Icons.Default.Bookmarks),
    SectionTab("send", "Send", Icons.AutoMirrored.Filled.Send),
)

/** Reports how the composed message measures against Discord's limits. */
@Composable
private fun ValidationPanel(draft: EmbedMessage) {
    val issues = remember(draft) { draft.validate() }
    SectionCard {
        SectionCardHeader("Validation", Icons.AutoMirrored.Filled.Rule)
        if (issues.isEmpty()) {
            Text(
                "Ready to send.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            return@SectionCard
        }
        issues.forEach { issue ->
            val color = if (issue.level == IssueLevel.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    if (issue.level == IssueLevel.ERROR) {
                        Icons.Default.ErrorOutline
                    } else {
                        Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp),
                )
                Text(issue.message, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

/** Shows the serialised payload and accepts a pasted one. */
@Composable
private fun JsonPanel(draft: EmbedMessage, onDraftChange: (EmbedMessage) -> Unit) {
    val clipboard = rememberTextClipboard()
    val serialized = remember(draft) { draft.serialize() }
    var pasted by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    SectionCard {
        SectionCardHeader("Payload", Icons.Default.DataObject)
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text(
                    text = serialized,
                    style = MonospaceStyle,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        OutlinedButton(
            onClick = { clipboard.copy(serialized) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Copy", modifier = Modifier.padding(start = 8.dp))
        }
    }

    SectionCard {
        SectionCardHeader("Import", Icons.Default.Upload)
        MewdekoTextField(
            value = pasted,
            onValueChange = { pasted = it; parseError = null },
            label = "Paste JSON",
            singleLine = false,
            minLines = 4,
            isError = parseError != null,
            supportingText = parseError,
        )
        Button(
            onClick = {
                val trimmed = pasted.trim()
                val isValidObject = trimmed.startsWith("{") &&
                    runCatching { MewdekoJson.parseToJsonElement(trimmed) }
                        .getOrNull() is JsonObject
                if (isValidObject) {
                    onDraftChange(EmbedMessage.parse(pasted))
                    pasted = ""
                    parseError = null
                } else {
                    parseError = "That isn't valid JSON."
                }
            },
            enabled = pasted.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Replace message") }
    }
}

/** Loads, stores, and removes named embeds. */
@Composable
private fun SavedPanel(
    draft: EmbedMessage,
    onLoad: (EmbedMessage) -> Unit,
    viewModel: EmbedLibraryViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedEmbed?>(null) }

    SectionCard {
        SectionCardHeader("Save current", Icons.Default.Bookmarks)
        MewdekoTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "Welcome message",
        )
        SwitchRow(
            title = "Share with this server",
            subtitle = "Anyone with dashboard access can use it",
            checked = shared,
            onCheckedChange = { shared = it },
        )
        Button(
            onClick = {
                viewModel.save(name, draft.serialize(), shared)
                name = ""
            },
            enabled = name.isNotBlank() && !draft.isEmpty,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }

    SectionCard {
        SectionCardHeader("Saved embeds", Icons.Default.Bookmarks)
        if (library.allSaved.isEmpty()) {
            EmptyState(message = "Nothing saved yet.", icon = Icons.Default.Bookmarks)
        }
        library.allSaved.forEach { saved ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(saved.displayName, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (saved.isGuildShared) "Shared with server" else "Personal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onLoad(EmbedMessage.parse(saved.jsonCode)) }) {
                    Text("Load")
                }
                IconButton(onClick = { pendingDelete = saved }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete saved embed",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete saved embed?",
            message = "\"${target.displayName}\" will be removed.",
            onConfirm = { pendingDelete = null; viewModel.delete(target) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Delivers the composed message to a channel, optionally through a webhook. */
@Composable
private fun SendPanel(
    draft: EmbedMessage,
    viewModel: EmbedLibraryViewModel = hiltViewModel(),
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var channelId by remember { mutableStateOf<String?>(null) }
    var useWebhook by remember { mutableStateOf(false) }
    var personaId by remember { mutableStateOf<String?>(null) }
    var webhookName by remember { mutableStateOf("") }
    var webhookAvatar by remember { mutableStateOf("") }
    var showPersonaManager by remember { mutableStateOf(false) }
    var confirmingSend by remember { mutableStateOf(false) }

    val blocking = remember(draft) { draft.validate().count { it.level == IssueLevel.ERROR } }
    val selectedChannel = remember(channelId, library.channels) {
        library.channels.firstOrNull { it.id == channelId }
    }
    val hasEmbedContent = remember(draft) {
        draft.embeds.any { it.title.isNotEmpty() || it.description.isNotEmpty() || it.fields.isNotEmpty() }
    }
    val embedPermissionBlock = remember(selectedChannel, hasEmbedContent) {
        when {
            !hasEmbedContent || selectedChannel == null -> null
            !selectedChannel.canEmbed -> "You do not have the Embed Links permission in this channel."
            !selectedChannel.botCanEmbed -> "The bot does not have the Embed Links permission in this channel."
            else -> null
        }
    }
    val webhookAvailable = selectedChannel?.webhookUsable == true

    LaunchedEffect(webhookAvailable) {
        if (!webhookAvailable) useWebhook = false
    }

    SectionCard {
        SectionCardHeader("Destination", Icons.AutoMirrored.Filled.Send)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = library.usableChannels.map {
                SelectorOption(it.id, it.name, it.categoryName)
            },
            placeholder = "Pick a channel",
            selectedId = channelId,
            onSelect = { channelId = it },
            label = "Channel",
        )
        selectedChannel?.let { channel ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Your permissions in #${channel.name}",
                    style = MaterialTheme.typography.labelLarge,
                )
                InfoRow("Send messages", if (channel.canSend) "Yes" else "No")
                InfoRow("Send embeds", if (channel.canEmbed) "Yes" else "No")
                InfoRow("Mention everyone and roles", if (channel.canMentionEveryone) "Yes" else "No")
                InfoRow("Manage webhooks", if (channel.canUseWebhooks) "Yes" else "No")
            }
            if (!channel.canMentionEveryone) {
                Text(
                    "Everyone, here, and role mentions will be stripped from this message.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            embedPermissionBlock?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (selectedChannel != null) {
        SectionCard {
            SectionCardHeader("Send as", Icons.Default.Person)
            SwitchRow(
                title = "Send through a webhook",
                subtitle = "Posts under a custom name and avatar instead of the bot",
                checked = useWebhook,
                onCheckedChange = { useWebhook = it },
                enabled = webhookAvailable,
            )
            if (!webhookAvailable) {
                Text(
                    if (selectedChannel.canUseWebhooks) {
                        "The bot needs the Manage Webhooks permission in this channel."
                    } else {
                        "You need the Manage Webhooks permission in this channel."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (useWebhook) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Send as",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPersonaManager = true }) { Text("Manage personas") }
                }
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Person),
                    options = library.personas.map {
                        SelectorOption(it.id.toString(), it.name)
                    },
                    placeholder = "Custom name and avatar",
                    selectedId = personaId,
                    onSelect = { personaId = it },
                    label = "Persona",
                )
                if (personaId == null) {
                    MewdekoTextField(
                        value = webhookName,
                        onValueChange = { webhookName = it },
                        label = "Username",
                        placeholder = "Optional",
                    )
                    MewdekoTextField(
                        value = webhookAvatar,
                        onValueChange = { webhookAvatar = it },
                        label = "Avatar URL",
                        placeholder = "Optional, links only. Save a persona to upload an image.",
                    )
                }
            }
        }
    }

    SectionCard {
        if (blocking > 0) {
            Text(
                "$blocking problem${if (blocking == 1) "" else "s"} must be fixed before sending. " +
                    "See the Preview tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            onClick = { confirmingSend = true },
            enabled = channelId != null && blocking == 0 && embedPermissionBlock == null && !library.isSending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                if (library.isSending) "Sending…" else "Send message",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        library.lastSend?.let { result ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Sent to #${result.channelName}" +
                        (result.identityLabel?.let { " as \"$it\"" }
                            ?: if (result.sentViaWebhook) " via webhook" else " as the bot"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (result.mentionsSuppressed) {
                    Text(
                        "Mentions were stripped because you lack Mention Everyone in this channel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (result.messageLink.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { uriHandler.openUri(result.messageLink) },
                    ) {
                        Text(
                            "Jump to message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }

    val unavailable = library.channels.filterNot { it.isUsable }
    if (unavailable.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Unavailable channels (${unavailable.size})", Icons.Default.Info)
            unavailable.forEach { channel ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "#${channel.name}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        channel.blockedReason.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showPersonaManager) {
        PersonaManagerSheet(onDismiss = { showPersonaManager = false }, viewModel = viewModel)
    }

    if (confirmingSend) {
        ConfirmDialog(
            title = "Send this message?",
            message = "It will be posted to #${selectedChannel?.name.orEmpty()} right away.",
            confirmLabel = "Send",
            destructive = false,
            onConfirm = {
                channelId?.let {
                    viewModel.send(
                        channelId = it,
                        jsonCode = draft.serialize(),
                        useWebhook = useWebhook,
                        personaId = personaId?.toIntOrNull(),
                        webhookUsername = webhookName,
                        webhookAvatarUrl = webhookAvatar,
                    )
                }
            },
            onDismiss = { confirmingSend = false },
        )
    }
}

/**
 * A labelled embed composer for a stored message field.
 *
 * Feature settings hold these as raw strings that may be plain text or an
 * embed payload, so this parses on the way in and serialises on the way out.
 */
@Composable
fun LabelledEmbedField(
    label: String,
    raw: String,
    onRawChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = remember(raw) { EmbedMessage.parse(raw) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EmbedMessageEditor(
            message = message,
            onMessageChange = { onRawChange(it.serialize()) },
        )
    }
}
