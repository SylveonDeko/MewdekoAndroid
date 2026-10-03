package dev.mewdeko.mobile.feature.achievements

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaGlyphTable
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.ReorderableColumn
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/** The selector kind for card pickers; each call site passes a Font Awesome glyph for the orb. */
private val CardSelector = SelectorKind.Custom(Icons.Default.Style)

/** Decodes PNG bytes once per array. */
@Composable
private fun rememberBitmap(bytes: ByteArray?): ImageBitmap? =
    remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }

/**
 * Card designs: saved designs with previews, which one is the server's default, and which categories and
 * achievements use a design of their own. Editing a design opens the designer.
 */
@Composable
internal fun CardsSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val card = state.card
    if (card == null) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var pendingDelete by remember { mutableStateOf<AchievementCardDesign?>(null) }

    SectionCard {
        SectionCardHeader(title = "Card designs", iconName = "id-card")
        Text(
            "The image attached to unlock messages and shown by achview. Every achievement uses the default, " +
                "${card.defaultName}, unless its category or itself picks another design.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DesignTile(
            title = "Built in",
            subtitle = "Mewdeko's design in your server's colors.",
            thumbnail = state.cardThumbnails["builtin"],
            isDefault = card.defaultId == null,
        ) {
            if (card.defaultId == null) {
                TonalButton("Edit", enabled = !state.cardsBusy) { viewModel.startNewDesign("Default", card.builtIn, makeDefault = true) }
            } else {
                TonalButton("Make default", enabled = !state.cardsBusy) { viewModel.setDefaultCard(null) }
                TonalButton("Start from it", enabled = !state.cardsBusy) { viewModel.startNewDesign(nextName(card), card.builtIn) }
            }
        }
        card.designs.forEach { design ->
            DesignTile(
                title = design.name,
                subtitle = card.usage(design.id),
                thumbnail = state.cardThumbnails[design.versionKey],
                isDefault = card.defaultId == design.id,
            ) {
                TonalButton("Edit") { viewModel.openDesigner(design.id) }
                if (card.defaultId != design.id) TonalButton("Make default", enabled = !state.cardsBusy) { viewModel.setDefaultCard(design.id) }
                TonalButton("Duplicate", enabled = !state.cardsBusy) {
                    viewModel.startNewDesign("${design.name} copy".take(card.limits.nameLength), design.template)
                }
                TonalButton("Delete", danger = true) { pendingDelete = design }
            }
        }
        TonalButton("New design", enabled = !state.cardsBusy, modifier = Modifier.fillMaxWidth()) {
            viewModel.startNewDesign(nextName(card), card.builtIn)
        }
    }

    if (card.designs.isNotEmpty()) {
        val designOptions = card.designs.map { SelectorOption(id = it.id.toString(), name = it.name) }
        val categories = state.catalog?.categories.orEmpty()
        val achievements = state.catalog?.achievements.orEmpty()
        val rules = categories.mapNotNull { c ->
            card.assignments.categories[c.key]?.let { CardRule(true, c.key, c.name, c.icon, c.iconUrl, it) }
        } + achievements.mapNotNull { a ->
            card.assignments.achievements[a.key]?.let { CardRule(false, a.key, a.name, a.icon, a.iconUrl, it) }
        }
        SectionCard {
            SectionCardHeader(title = "Different designs for some unlocks", glyph = FaGlyph.LayerGroup)
            Text(
                "Optional. Give a whole category or a single achievement its own design. " +
                    "An achievement's own design wins over its category's.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rules.isEmpty()) {
                Text(
                    "Every unlock uses ${card.defaultName}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08))
                        .padding(12.dp),
                )
            }
            rules.forEach { rule ->
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AchievementIconTile(rule.icon, state.iconImageUrl(rule.iconUrl), MaterialTheme.colorScheme.primary, size = 28, bare = true)
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (rule.category) "Every achievement in this category" else "Achievement",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { viewModel.assignCard(rule.category, rule.key, null) },
                            enabled = !state.cardsBusy,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(DangerRed.copy(alpha = DashAlpha.Hex15)),
                        ) { FaIcon(FaGlyph.Xmark, tint = DangerRed, size = 16.dp, contentDescription = "Use the default for ${rule.name}") }
                    }
                    DiscordSelectorSingle(
                        kind = CardSelector,
                        glyph = FaGlyph.IdCard,
                        options = designOptions,
                        placeholder = "Pick a design",
                        selectedId = rule.designId.toString(),
                        onSelect = { id -> id?.toIntOrNull()?.let { viewModel.assignCard(rule.category, rule.key, it) } },
                        label = "Design",
                    )
                }
            }
            var ruleTarget by remember { mutableStateOf<String?>(null) }
            var ruleDesign by remember { mutableStateOf<String?>(null) }
            DiscordSelectorSingle(
                kind = CardSelector,
                options = categories.filter { it.key !in card.assignments.categories }
                    .map { SelectorOption(id = "c:${it.key}", name = "${it.name} (whole category)") } +
                    achievements.filter { it.key !in card.assignments.achievements }
                        .map { SelectorOption(id = "a:${it.key}", name = it.name) },
                placeholder = "Pick a category or achievement",
                selectedId = ruleTarget,
                onSelect = { ruleTarget = it },
                label = "Use a design for",
                glyph = FaGlyph.Crown,
            )
            DiscordSelectorSingle(
                kind = CardSelector,
                glyph = FaGlyph.IdCard,
                options = designOptions,
                placeholder = "Pick a design",
                selectedId = ruleDesign,
                onSelect = { ruleDesign = it },
                label = "Design",
            )
            TonalButton(
                "Add",
                enabled = !state.cardsBusy && ruleTarget != null && ruleDesign != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val target = ruleTarget ?: return@TonalButton
                viewModel.assignCard(target.startsWith("c:"), target.drop(2), ruleDesign?.toIntOrNull())
                ruleTarget = null
                ruleDesign = null
            }
        }
    }

    pendingDelete?.let { design ->
        ConfirmDialog(
            title = "Delete ${design.name}?",
            message = "Categories and achievements using it go back to the server default. If it is the default, the built in design takes over.",
            confirmLabel = "Delete design",
            onConfirm = {
                viewModel.deleteCardDesign(design.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** A category or achievement that picks its own design. */
private data class CardRule(
    val category: Boolean,
    val key: String,
    val name: String,
    val icon: String?,
    val iconUrl: String?,
    val designId: Int,
)

private fun nextName(card: AchievementCardResponse): String {
    var n = card.designs.size + 1
    while (card.designs.any { it.name == "Design $n" }) n++
    return "Design $n"
}

/** A tonal button in the dashboard's style: primary at 20 with primary text. */
@Composable
private fun TonalButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tone = if (danger) DangerRed else MaterialTheme.colorScheme.primary
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = tone.copy(alpha = DashAlpha.Hex20),
            contentColor = tone,
        ),
    ) { Text(label) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesignTile(
    title: String,
    subtitle: String,
    thumbnail: ByteArray?,
    isDefault: Boolean,
    actions: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        val image = rememberBitmap(thumbnail)
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1280f / 500f).clip(RoundedCornerShape(12.dp))
                .background(primary.copy(alpha = DashAlpha.Hex05)),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) Image(image, contentDescription = "Preview of $title", modifier = Modifier.fillMaxSize())
            else CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (isDefault) StatePill("Default", tone = primary)
        }
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions()
        }
    }
}

/**
 * Edits one card design. The bot draws the preview, and handles over it move and resize elements; below it are
 * the layers and the properties of the selected element or of the card itself.
 */
@Composable
internal fun CardDesigner(state: AchievementsState, viewModel: AchievementsViewModel) {
    val card = state.card ?: return
    val target = state.designer ?: return
    var designId by remember(target.token) { mutableStateOf(target.id) }
    var makeDefault by remember(target.token) { mutableStateOf(target.makeDefault) }
    var draft by remember(target.token) { mutableStateOf(target.template) }
    var name by remember(target.token) { mutableStateOf(target.name) }
    var saved by remember(target.token) { mutableStateOf(target.template to target.name) }
    var selectedId by remember(target.token) { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var sampleKey by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<AchievementCardPreview?>(null) }
    val undo = remember(target.token) { mutableStateListOf<AchievementCardTemplate>() }
    val scope = rememberCoroutineScope()
    val palette = card.palette
    val achievements = state.catalog?.achievements.orEmpty()
    val sample = achievements.firstOrNull { it.key == sampleKey }
        ?: achievements.firstOrNull { it.metric > 0 && it.threshold > 0 && !it.hidden }
        ?: achievements.firstOrNull()
    val grade = if (locked) palette["muted"] ?: "#9ca3af"
    else state.catalog?.grades?.firstOrNull { it.value == sample?.grade }?.color ?: palette["primary"] ?: "#3b82f6"
    val dirty = designId == null || draft != saved.first || name.trim() != saved.second

    LaunchedEffect(draft, locked, sample?.key) {
        delay(160)
        viewModel.previewCard(draft, locked, sample?.key)?.let { preview = it }
    }

    /** Applies a change, keeping the previous design for undo. */
    fun edit(next: AchievementCardTemplate) {
        if (next == draft) return
        if (undo.size >= 60) undo.removeAt(0)
        undo.add(draft)
        draft = next
    }

    fun editElement(id: String, transform: (AchievementCardElement) -> AchievementCardElement) {
        edit(draft.copy(elements = draft.elements.map { if (it.id == id) transform(it) else it }))
    }

    FullScreenEditor(
        title = name.ifBlank { "Card design" },
        onClose = viewModel::closeDesigner,
        confirmLabel = "Save",
        confirmEnabled = dirty && name.isNotBlank() && !state.cardsBusy,
        onConfirm = {
            scope.launch {
                val trimmed = name.trim()
                val id = designId
                if (id != null) {
                    if (viewModel.updateCardDesign(id, trimmed, draft)) saved = draft to trimmed
                } else {
                    viewModel.createCardDesign(trimmed, draft, makeDefault)?.let { created ->
                        designId = created.id
                        makeDefault = false
                        saved = draft to trimmed
                    }
                }
            }
        },
        hasUnsavedChanges = dirty,
    ) {
        DesignCanvas(
            draft = draft,
            preview = preview,
            selectedId = selectedId,
            onSelect = { selectedId = it },
            onMove = { id, x, y -> draft = draft.copy(elements = draft.elements.map { if (it.id == id) it.copy(x = x, y = y) else it }) },
            onResize = { id, w, h -> draft = draft.copy(elements = draft.elements.map { if (it.id == id) it.copy(w = w, h = h) else it }) },
            onDragStart = { if (undo.size >= 60) undo.removeAt(0); undo.add(draft) },
        )

        SectionCard {
            SectionCardHeader(title = "Design", iconName = "id-card")
            MewdekoTextField(value = name, onValueChange = { name = it.take(card.limits.nameLength) }, label = "Name")
            EnumPicker(
                label = "Preview as",
                options = listOf(EnumOption(false, "Unlocked", "As it is announced"), EnumOption(true, "Locked", "With sample progress")),
                selected = locked,
                onSelect = { locked = it },
            )
            DiscordSelectorSingle(
                kind = CardSelector,
                options = achievements.map { SelectorOption(id = it.key, name = it.name) },
                placeholder = "Sample achievement",
                selectedId = sample?.key,
                onSelect = { sampleKey = it },
                label = "Sample achievement",
                glyph = FaGlyph.Crown,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton("Undo", enabled = undo.isNotEmpty()) { draft = undo.removeAt(undo.lastIndex) }
                TonalButton("Start over") { edit(card.builtIn); selectedId = null }
            }
        }

        LayersCard(
            draft = draft,
            selectedId = selectedId,
            maxElements = card.limits.maxElements,
            onSelect = { selectedId = it },
            onEdit = ::edit,
            builtIn = card.builtIn,
        )

        val element = draft.elements.firstOrNull { it.id == selectedId }
        if (element != null) {
            ElementProperties(element, draft, card, grade, state, viewModel) { transform -> editElement(element.id, transform) }
        } else {
            CardProperties(draft, card, grade, state, viewModel, ::edit)
        }
    }
}

/** The bot's drawing with a handle over each visible element. */
@Composable
private fun DesignCanvas(
    draft: AchievementCardTemplate,
    preview: AchievementCardPreview?,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Double, Double) -> Unit,
    onResize: (String, Double, Double) -> Unit,
    onDragStart: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val margin = preview?.margin ?: 40
    val outerW = (preview?.width ?: draft.width) + margin * 2
    val outerH = (preview?.height ?: draft.height) + margin * 2
    val image = rememberBitmap(preview?.bytes)
    val density = LocalDensity.current
    val current by rememberUpdatedState(draft)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(outerW.toFloat() / outerH).clip(RoundedCornerShape(12.dp))
            .background(primary.copy(alpha = DashAlpha.Hex05)).clickable { onSelect(null) },
    ) {
        val scale = with(density) { maxWidth.toPx() } / outerW
        if (image != null) Image(image, contentDescription = "Card preview", modifier = Modifier.fillMaxSize())
        else CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(24.dp))

        draft.elements.filter { it.visible }.forEach { element ->
            val laid = preview?.layout?.firstOrNull { it.id == element.id }
            val drawnAs = preview?.template?.elements?.firstOrNull { it.id == element.id }
            val x = if (laid != null && drawnAs != null) laid.x + element.x - drawnAs.x else element.x
            val y = if (laid != null && drawnAs != null) laid.y + element.y - drawnAs.y else element.y
            val w = if (laid != null && drawnAs != null) laid.w + element.w - drawnAs.w else element.w
            val h = if (laid != null && drawnAs != null) laid.h + element.h - drawnAs.h else element.h
            val active = element.id == selectedId
            val left = ((x + margin) * scale).roundToInt()
            val top = ((y + margin) * scale).roundToInt()
            val widthDp = with(density) { (w.coerceAtLeast(8.0) * scale).toFloat().toDp() }
            val heightDp = with(density) { (h.coerceAtLeast(8.0) * scale).toFloat().toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(left, top) }
                    .size(widthDp, heightDp)
                    .rotate(element.rotation.toFloat())
                    .border(if (active) 2.dp else 1.dp, if (active) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
                    .clickable { onSelect(element.id) }
                    .pointerInput(element.id, scale) {
                        var originX = 0.0
                        var originY = 0.0
                        var dx = 0.0
                        var dy = 0.0
                        detectDragGestures(
                            onDragStart = {
                                onSelect(element.id)
                                onDragStart()
                                val e = current.elements.firstOrNull { it.id == element.id }
                                originX = e?.x ?: 0.0
                                originY = e?.y ?: 0.0
                                dx = 0.0
                                dy = 0.0
                            },
                        ) { change, amount ->
                            change.consume()
                            dx += amount.x / scale
                            dy += amount.y / scale
                            onMove(element.id, snap(originX + dx), snap(originY + dy))
                        }
                    },
            )
            if (active) {
                val handle = 22.dp
                val handlePx = with(density) { handle.toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset((left + w * scale - handlePx / 2).roundToInt(), (top + h * scale - handlePx / 2).roundToInt()) }
                        .size(handle)
                        .clip(CircleShape)
                        .background(primary)
                        .pointerInput(element.id, scale) {
                            var originW = 0.0
                            var originH = 0.0
                            var dw = 0.0
                            var dh = 0.0
                            detectDragGestures(
                                onDragStart = {
                                    onDragStart()
                                    val e = current.elements.firstOrNull { it.id == element.id }
                                    originW = e?.w ?: 0.0
                                    originH = e?.h ?: 0.0
                                    dw = 0.0
                                    dh = 0.0
                                },
                            ) { change, amount ->
                                change.consume()
                                dw += amount.x / scale
                                dh += amount.y / scale
                                onResize(element.id, snap(originW + dw).coerceAtLeast(4.0), snap(originH + dh).coerceAtLeast(4.0))
                            }
                        },
                )
            }
        }
    }
}

private fun snap(value: Double): Double = Math.round(value / 4.0) * 4.0

/** The layer list, front first, with visibility, order, and adding elements. */
@Composable
private fun LayersCard(
    draft: AchievementCardTemplate,
    selectedId: String?,
    maxElements: Int,
    builtIn: AchievementCardTemplate,
    onSelect: (String?) -> Unit,
    onEdit: (AchievementCardTemplate) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    var adding by remember { mutableStateOf(false) }
    SectionCard {
        SectionCardHeader(title = "Layers", glyph = FaGlyph.LayerGroup)
        LayerRow("Card and background", "id-card", selectedId == null, visible = true, onClick = { onSelect(null) })
        Text("Top of the list is in front. Press and hold a layer, then drag it to reorder.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReorderableColumn(
            items = draft.elements.asReversed(),
            key = { it.id },
            onMove = { from, to ->
                val frontFirst = draft.elements.asReversed().toMutableList()
                frontFirst.add(to, frontFirst.removeAt(from))
                onEdit(draft.copy(elements = frontFirst.asReversed()))
            },
            spacing = 2.dp,
        ) { element, dragModifier, dragging ->
            LayerRow(
                title = element.displayName,
                glyph = CardKinds.glyph(element.type),
                active = selectedId == element.id || dragging,
                visible = element.visible,
                onClick = { onSelect(element.id) },
                modifier = dragModifier,
            ) {
                IconButton(onClick = {
                    onEdit(draft.copy(elements = draft.elements.map { if (it.id == element.id) it.copy(visible = !it.visible) else it }))
                }) { FaIcon(if (element.visible) FaGlyph.Eye else FaGlyph.EyeSlash, size = 16.dp, contentDescription = if (element.visible) "Hide" else "Show") }
            }
        }
        TonalButton("Add an element", modifier = Modifier.fillMaxWidth()) { adding = !adding }
        if (adding) {
            CardKinds.custom.forEach { type ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (draft.elements.size < maxElements) {
                                val element = CardKinds.newElement(type, draft)
                                onEdit(draft.copy(elements = draft.elements + element))
                                onSelect(element.id)
                            }
                            adding = false
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FaIcon(CardKinds.glyph(type), size = 16.dp, tint = primary)
                    Text(CardKinds.label(type))
                }
            }
        }
        if (selectedId != null) {
            val element = draft.elements.firstOrNull { it.id == selectedId }
            if (element != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (CardKinds.isBuiltIn(element.type)) {
                        TonalButton("Reset") {
                            builtIn.elements.firstOrNull { it.type == element.type }?.let { original ->
                                onEdit(draft.copy(elements = draft.elements.map { if (it.id == element.id) original else it }))
                            }
                        }
                    } else {
                        TonalButton("Duplicate") {
                            var n = 1
                            while (draft.elements.any { it.id == "${element.type}-$n" }) n++
                            val copy = element.copy(id = "${element.type}-$n", x = element.x + 16, y = element.y + 16)
                            val list = draft.elements.toMutableList()
                            list.add(draft.elements.indexOf(element) + 1, copy)
                            onEdit(draft.copy(elements = list))
                            onSelect(copy.id)
                        }
                        TonalButton("Delete", danger = true) {
                            onEdit(draft.copy(elements = draft.elements.filter { it.id != element.id }.map {
                                it.copy(
                                    followId = if (it.followId == element.id) "" else it.followId,
                                    besideId = if (it.besideId == element.id) "" else it.besideId,
                                )
                            }))
                            onSelect(null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerRow(
    title: String,
    glyph: String,
    active: Boolean,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .background(if (active) primary.copy(alpha = DashAlpha.Hex20) else androidx.compose.ui.graphics.Color.Transparent)
            .then(modifier)
            .clickable(onClick = onClick).padding(start = 12.dp).alpha(if (visible) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FaIcon(glyph, size = 16.dp, tint = primary)
        Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** The properties of the selected element. */
@Composable
private fun ElementProperties(
    element: AchievementCardElement,
    draft: AchievementCardTemplate,
    card: AchievementCardResponse,
    grade: String,
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    update: ((AchievementCardElement) -> AchievementCardElement) -> Unit,
) {
    val type = element.type
    val palette = card.palette
    SectionCard {
        SectionCardHeader(title = element.displayName, iconName = CardKinds.glyph(type))
        MewdekoTextField(value = element.name, onValueChange = { v -> update { it.copy(name = v.take(40)) } },
            label = "Layer name", placeholder = CardKinds.label(type))
        EnumPicker(
            label = "Show on",
            options = listOf(EnumOption("always", "Every card"), EnumOption("unlocked", "Unlocked"), EnumOption("locked", "Locked")),
            selected = element.show,
            onSelect = { v -> update { it.copy(show = v) } },
        )
        NumberPair("X", element.x, { v -> update { it.copy(x = v) } }, "Y", element.y, { v -> update { it.copy(y = v) } }, element.id)
        NumberPair("Width", element.w, { v -> update { it.copy(w = v.coerceAtLeast(1.0)) } },
            "Height", element.h, { v -> update { it.copy(h = v.coerceAtLeast(1.0)) } }, element.id)
        NumberField("Rotation", element.rotation, element.id) { v -> update { it.copy(rotation = v) } }
        LabeledSlider("Opacity ${(element.opacity * 100).roundToInt()}%", element.opacity, 0.0..1.0) { v -> update { it.copy(opacity = v) } }
        DiscordSelectorSingle(
            kind = CardSelector,
            glyph = FaGlyph.ArrowDown,
            options = listOf(SelectorOption(id = "", name = "Nothing")) + draft.elements
                .filter { it.id != element.id && it.type in CardKinds.text }
                .map { SelectorOption(id = it.id, name = it.displayName) },
            placeholder = "Nothing",
            selectedId = element.followId,
            onSelect = { v -> update { it.copy(followId = v.orEmpty()) } },
            label = "Moves down when this wraps",
        )
    }

    if (type == "text") {
        SectionCard {
            SectionCardHeader(title = "Text", iconName = "font")
            MewdekoTextField(value = element.text, onValueChange = { v -> update { it.copy(text = v.take(card.limits.maxText)) } },
                label = "Text", singleLine = false, minLines = 2)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                card.placeholders.forEach { placeholder ->
                    TextButton(onClick = {
                        update { it.copy(text = (it.text + (if (it.text.isEmpty() || it.text.endsWith(" ")) "" else " ") + placeholder).take(card.limits.maxText)) }
                    }) { Text(placeholder, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }

    if (type == "glyph") {
        SectionCard {
            SectionCardHeader(title = "Icon", glyph = FaGlyph.WandMagicSparkles)
            GlyphRow("Icon", element.glyph, emptyLabel = null, allowNone = false) { v -> update { it.copy(glyph = v) } }
            ColorRow("Color", element.color, palette, grade, false) { v -> update { it.copy(color = v) } }
            ColorRow("Back layer", element.color3, palette, grade, true) { v -> update { it.copy(color3 = v) } }
        }
    }

    if (type == "image") {
        SectionCard {
            SectionCardHeader(title = "Image", iconName = "image")
            ImageRow(element.url, card, state, viewModel) { v -> update { it.copy(url = v) } }
            EnumPicker(label = "Fit", options = listOf(EnumOption("cover", "Fill"), EnumOption("contain", "Fit inside")),
                selected = element.fit, onSelect = { v -> update { it.copy(fit = v) } })
        }
    }

    if (type in CardKinds.text || type in CardKinds.pill) {
        SectionCard {
            SectionCardHeader(title = "Type", iconName = "font")
            NumberPair("Size", element.fontSize, { v -> update { it.copy(fontSize = v.coerceIn(8.0, 200.0)) } },
                "Letter spacing", element.spacing, { v -> update { it.copy(spacing = v) } }, element.id)
            if (type in listOf("title", "description", "text")) {
                NumberPair("Line height", element.lineHeight, { v -> update { it.copy(lineHeight = v.coerceIn(0.8, 3.0)) } },
                    "Max lines", element.maxLines.toDouble(), { v -> update { it.copy(maxLines = v.roundToInt().coerceIn(1, 6)) } }, element.id)
            }
            EnumPicker(label = "Align",
                options = listOf(EnumOption("left", "Left"), EnumOption("center", "Center"), EnumOption("right", "Right")),
                selected = element.align, onSelect = { v -> update { it.copy(align = v) } })
            SwitchRow(title = "Bold", checked = element.bold, onCheckedChange = { v -> update { it.copy(bold = v) } })
            SwitchRow(title = "Capitals", checked = element.uppercase, onCheckedChange = { v -> update { it.copy(uppercase = v) } })
            ColorRow(if (type == "member") "Name color" else "Text color", element.color, palette, grade, false) { v -> update { it.copy(color = v) } }
            if (type == "member") {
                ColorRow("Server color", element.color2, palette, grade, false) { v -> update { it.copy(color2 = v) } }
            }
            if (type == "label" || type == "text" || type in CardKinds.pill) {
                val automatic = type in listOf("label", "grade", "category")
                GlyphRow("Icon beside the text", element.glyph, if (automatic) "Automatic" else "No icon", automatic) { v -> update { it.copy(glyph = v) } }
                ColorRow("Icon color", element.color2, palette, grade, false) { v -> update { it.copy(color2 = v) } }
                ColorRow("Icon back layer", element.color3, palette, grade, true) { v -> update { it.copy(color3 = v) } }
            }
        }
    }

    if (type in CardKinds.pill) {
        SectionCard {
            SectionCardHeader(title = "Badge", glyph = FaGlyph.Trophy)
            SwitchRow(title = "Shrink to the text", subtitle = "Sits at the alignment inside its box",
                checked = element.autoWidth, onCheckedChange = { v -> update { it.copy(autoWidth = v) } })
            DiscordSelectorSingle(
                kind = CardSelector,
                glyph = FaGlyph.Trophy,
                options = listOf(SelectorOption(id = "", name = "Nothing")) + draft.elements
                    .filter { it.id != element.id && it.type in CardKinds.pill }
                    .map { SelectorOption(id = it.id, name = it.displayName) },
                placeholder = "Nothing",
                selectedId = element.besideId,
                onSelect = { v -> update { it.copy(besideId = v.orEmpty()) } },
                label = "Sits beside",
            )
            if (element.besideId.isNotEmpty()) NumberField("Gap", element.gap, element.id) { v -> update { it.copy(gap = v) } }
        }
    }

    if (type == "icon") {
        SectionCard {
            SectionCardHeader(title = "Icon", glyph = FaGlyph.Crown)
            ColorRow("Icon color", element.color, palette, grade, false) { v -> update { it.copy(color = v) } }
            ColorRow("Icon back layer", element.color3, palette, grade, true) { v -> update { it.copy(color3 = v) } }
        }
    }

    if (type == "progress") {
        SectionCard {
            SectionCardHeader(title = "Bar", glyph = FaGlyph.ChartSimple)
            ColorRow("Bar", element.color, palette, grade, false) { v -> update { it.copy(color = v) } }
            ColorRow("Bar fades to", element.fill2, palette, grade, true) { v -> update { it.copy(fill2 = v) } }
            ColorRow("Track", element.fill, palette, grade, true) { v -> update { it.copy(fill = v) } }
            ColorRow("Count text", element.color2, palette, grade, true) { v -> update { it.copy(color2 = v) } }
            NumberField("Count text size", element.fontSize, element.id) { v -> update { it.copy(fontSize = v.coerceIn(8.0, 200.0)) } }
        }
    }

    if ((type in CardKinds.shape && type != "progress") || type == "image") {
        SectionCard {
            SectionCardHeader(title = "Shape", iconName = "square")
            if (type != "image") {
                ColorRow(if (type == "avatar") "Fill without an avatar" else "Fill", element.fill, palette, grade, true) { v -> update { it.copy(fill = v) } }
                if (element.fill.isNotEmpty() && type != "avatar") {
                    ColorRow("Fill fades to", element.fill2, palette, grade, true) { v -> update { it.copy(fill2 = v) } }
                    if (element.fill2.isNotEmpty()) NumberField("Fade angle", element.fillAngle, element.id) { v -> update { it.copy(fillAngle = v) } }
                }
            }
            ColorRow("Outline", element.stroke, palette, grade, true) { v -> update { it.copy(stroke = v) } }
            if (element.stroke.isNotEmpty()) NumberField("Outline width", element.strokeWidth, element.id) { v -> update { it.copy(strokeWidth = v.coerceIn(0.0, 40.0)) } }
            if (type != "ellipse") NumberField("Corner radius", element.radius, element.id) { v -> update { it.copy(radius = v.coerceAtLeast(0.0)) } }
            if (type == "rectangle" || type == "ellipse") {
                ColorRow("Shadow", element.shadowColor, palette, grade, true) { v ->
                    update { it.copy(shadowColor = v, shadowBlur = if (v.isNotEmpty() && it.shadowBlur == 0.0) 16.0 else it.shadowBlur) }
                }
                if (element.shadowColor.isNotEmpty()) {
                    NumberField("Shadow blur", element.shadowBlur, element.id) { v -> update { it.copy(shadowBlur = v.coerceIn(0.0, 60.0)) } }
                    NumberPair("Shadow X", element.shadowX, { v -> update { it.copy(shadowX = v) } },
                        "Shadow Y", element.shadowY, { v -> update { it.copy(shadowY = v) } }, element.id)
                }
            }
        }
    }
}

/** The card's size, border, shadow, and background. */
@Composable
private fun CardProperties(
    draft: AchievementCardTemplate,
    card: AchievementCardResponse,
    grade: String,
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    edit: (AchievementCardTemplate) -> Unit,
) {
    val palette = card.palette
    val limits = card.limits
    SectionCard {
        SectionCardHeader(title = "Card", iconName = "id-card")
        NumberPair("Width", draft.width.toDouble(), { v -> edit(draft.copy(width = v.roundToInt().coerceIn(limits.minWidth, limits.maxWidth))) },
            "Height", draft.height.toDouble(), { v -> edit(draft.copy(height = v.roundToInt().coerceIn(limits.minHeight, limits.maxHeight))) }, "card")
        NumberField("Corner radius", draft.radius, "card") { v -> edit(draft.copy(radius = v.coerceAtLeast(0.0))) }
        ColorRow("Border", draft.borderColor, palette, grade, true) { v -> edit(draft.copy(borderColor = v)) }
        if (draft.borderColor.isNotEmpty()) NumberField("Border width", draft.borderWidth, "card") { v -> edit(draft.copy(borderWidth = v.coerceIn(0.0, 20.0))) }
        SwitchRow(title = "Shadow under the card", checked = draft.shadow, onCheckedChange = { v -> edit(draft.copy(shadow = v)) })
    }
    val bg = draft.background
    SectionCard {
        SectionCardHeader(title = "Background", iconName = "image")
        EnumPicker(
            label = "Fill",
            options = listOf(
                EnumOption("palette", "Palette", "The dashboard's card look in the server's colors"),
                EnumOption("solid", "Solid"), EnumOption("gradient", "Gradient"), EnumOption("image", "Image"),
            ),
            selected = bg.kind,
            onSelect = { v -> edit(draft.copy(background = bg.copy(kind = v))) },
        )
        if (bg.kind == "solid" || bg.kind == "gradient") {
            ColorRow(if (bg.kind == "solid") "Color" else "From", bg.color, palette, grade, false) { v -> edit(draft.copy(background = bg.copy(color = v))) }
        }
        if (bg.kind == "gradient") {
            ColorRow("To", bg.color2, palette, grade, false) { v -> edit(draft.copy(background = bg.copy(color2 = v))) }
            NumberField("Angle", bg.angle, "card") { v -> edit(draft.copy(background = bg.copy(angle = v))) }
        }
        if (bg.kind == "image") {
            ImageRow(bg.url, card, state, viewModel) { v -> edit(draft.copy(background = bg.copy(url = v))) }
            EnumPicker(label = "Fit", options = listOf(EnumOption("cover", "Fill"), EnumOption("contain", "Fit inside")),
                selected = bg.fit, onSelect = { v -> edit(draft.copy(background = bg.copy(fit = v))) })
            LabeledSlider("Darken ${(bg.dim * 100).roundToInt()}%", bg.dim, 0.0..1.0) { v -> edit(draft.copy(background = bg.copy(dim = v))) }
        }
        if (bg.kind != "palette") {
            SwitchRow(title = "Palette wash on top", subtitle = "Tints it with the server icon's colors",
                checked = bg.wash, onCheckedChange = { v -> edit(draft.copy(background = bg.copy(wash = v))) })
        }
    }
}

/** A number field that keeps what is typed until it parses. */
@Composable
private fun NumberField(label: String, value: Double, key: String, modifier: Modifier = Modifier, onChange: (Double) -> Unit) {
    var text by remember(key, label) { mutableStateOf(format(value)) }
    LaunchedEffect(value) { if (text.toDoubleOrNull() != value) text = format(value) }
    MewdekoTextField(
        value = text,
        onValueChange = { v ->
            text = v
            v.toDoubleOrNull()?.let(onChange)
        },
        label = label,
        numeric = true,
        modifier = modifier,
    )
}

@Composable
private fun NumberPair(
    firstLabel: String, first: Double, onFirst: (Double) -> Unit,
    secondLabel: String, second: Double, onSecond: (Double) -> Unit,
    key: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumberField(firstLabel, first, key, Modifier.weight(1f), onFirst)
        NumberField(secondLabel, second, key, Modifier.weight(1f), onSecond)
    }
}

private fun format(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

@Composable
private fun LabeledSlider(label: String, value: Double, range: ClosedFloatingPointRange<Double>, onChange: (Double) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((Math.round(it * 20) / 20.0)) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        )
    }
}

/** Picks a card color token: palette swatches, the grade color, a custom hex, and opacity. */
@Composable
private fun ColorRow(title: String, value: String, palette: Map<String, String>, grade: String, allowNone: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val (base, alpha) = CardTokens.parse(value)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(5.dp))
                    .background(CardTokens.color(value, palette, grade) ?: androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(CardTokens.label(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardTokens.names.forEach { token ->
                    Row(
                        modifier = Modifier.clip(CircleShape)
                            .background(primary.copy(alpha = if (base == token) DashAlpha.Hex30 else DashAlpha.Hex10))
                            .clickable { onChange(CardTokens.build(token, alpha)) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(CardTokens.hexColor(if (token == "grade") grade else palette[token] ?: "#ffffff")))
                        Text(token, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (allowNone) {
                    Text(
                        "None",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(CircleShape)
                            .background(primary.copy(alpha = if (value.isEmpty()) DashAlpha.Hex30 else DashAlpha.Hex10))
                            .clickable { onChange("") }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
            if (value.isNotEmpty()) {
                var hex by remember(value) { mutableStateOf(if (base.startsWith("#")) base else "") }
                MewdekoTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v
                        if (Regex("^#[0-9a-fA-F]{6}$").matches(v)) onChange(CardTokens.build(v.lowercase(), alpha))
                    },
                    label = "Custom hex",
                    placeholder = "#5865f2",
                )
                LabeledSlider("Opacity ${Math.round(alpha / 2.55)}%", alpha / 255.0, 0.0..1.0) { v ->
                    onChange(CardTokens.build(base, (v * 255).roundToInt()))
                }
            }
        }
    }
}

/** Picks a Font Awesome glyph by name, with optional automatic and none choices. */
@Composable
private fun GlyphRow(title: String, value: String, emptyLabel: String?, allowNone: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val all = remember { FaGlyphTable.all(context) }
    val term = search.trim().lowercase()
    val glyphs = (if (term.isEmpty()) all else all.filter { g -> g.name.contains(term) || g.aliases.any { it.contains(term) } }).take(120)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (value.isNotEmpty() && value != "none") FaIcon(value, size = 16.dp, tint = primary)
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(
                    when {
                        value == "none" -> "None"
                        value.isEmpty() -> emptyLabel ?: "Pick an icon"
                        else -> value
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (emptyLabel != null) TonalButton(emptyLabel) { onChange(""); expanded = false }
                if (allowNone) TonalButton("None") { onChange("none"); expanded = false }
            }
            MewdekoTextField(value = search, onValueChange = { search = it }, label = "Search icons")
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier.fillMaxWidth().height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(glyphs, key = { it.name }) { glyph ->
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(primary.copy(alpha = if (value == glyph.name) DashAlpha.Hex30 else DashAlpha.Hex08))
                            .clickable { onChange(glyph.name); expanded = false },
                        contentAlignment = Alignment.Center,
                    ) { FaIcon(glyph.name, size = 18.dp, tint = primary, contentDescription = glyph.name) }
                }
            }
        }
    }
}

/** Picks an image for a card: one of the server's card images, a new upload, or an https link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageRow(value: String, card: AchievementCardResponse, state: AchievementsState, viewModel: AchievementsViewModel, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AchievementIconUpload?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val jpeg = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bitmap ->
                    val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                }
            }
            jpeg?.let { viewModel.uploadCardImage(it, "image/jpeg") }?.let { onChange(it.icon); expanded = false }
            uploading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FaIcon("image", size = 16.dp, tint = primary)
            Column(Modifier.weight(1f)) {
                Text("Image")
                Text(
                    when {
                        value.isEmpty() -> "No image"
                        value.startsWith("upload:") -> "Uploaded image"
                        else -> "Linked image"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.images.forEach { image ->
                    Box {
                        AsyncImage(
                            model = state.iconImageUrl(image.url),
                            contentDescription = "Card image ${image.id}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(8.dp))
                                .border(2.dp, if (value == image.icon) primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { onChange(image.icon); expanded = false },
                        )
                        IconButton(onClick = { pendingDelete = image }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            FaIcon(FaGlyph.Xmark, size = 12.dp, contentDescription = "Delete this image")
                        }
                    }
                }
            }
            TonalButton(if (uploading) "Uploading" else "Upload an image", enabled = !uploading, modifier = Modifier.fillMaxWidth()) {
                pickImage.launch("image/*")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MewdekoTextField(value = link, onValueChange = { link = it }, label = "Image link", placeholder = "https://",
                    modifier = Modifier.weight(1f))
                TonalButton("Use", enabled = link.startsWith("https://")) { onChange(link.trim()); link = ""; expanded = false }
            }
            if (value.isNotEmpty()) TonalButton("Remove image", danger = true) { onChange("") }
        }
    }
    pendingDelete?.let { image ->
        ConfirmDialog(
            title = "Delete this image?",
            message = "Every design using it loses the image.",
            confirmLabel = "Delete image",
            onConfirm = {
                if (value == image.icon) onChange("")
                viewModel.deleteCardImage(image)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
