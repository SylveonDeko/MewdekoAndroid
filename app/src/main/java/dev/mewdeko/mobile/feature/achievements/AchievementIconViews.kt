package dev.mewdeko.mobile.feature.achievements

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaGlyphTable
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.TabLevel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/** An icon value usable where only glyph names fit, such as section headers: images become a picture glyph. */
internal fun headerIcon(icon: String): String = if (icon.startsWith("fa:")) icon else "fa:image"

/**
 * An achievement or category icon: its Font Awesome glyph, or its image for server emojis, linked images,
 * and uploads. On a tile tinted with [color], or bare beside text.
 */
@Composable
internal fun AchievementIconTile(icon: String?, imageUrl: String?, color: Color, size: Int = 36, bare: Boolean = false) {
    val shape = RoundedCornerShape((size * 0.28f).dp)
    val tile = if (bare) Modifier.size(size.dp) else Modifier
        .size(size.dp)
        .background(color.copy(alpha = DashAlpha.Hex20), shape)
        .border(1.dp, color.copy(alpha = DashAlpha.Hex40), shape)
    Box(modifier = tile, contentAlignment = Alignment.Center) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size((if (bare) size else (size * 0.62f).toInt()).dp),
            )
        } else {
            FaIcon(icon ?: "trophy", size = (if (bare) size * 0.9f else size * 0.42f).dp, tint = color)
        }
    }
}

/** A form row showing an icon with a button that opens the picker. */
@Composable
internal fun AchievementIconField(
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    icon: String?,
    onChange: (String?) -> Unit,
    defaultIcon: String?,
    defaultImageUrl: String?,
    defaultLabel: String,
    color: Color,
) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AchievementIconTile(
            icon = icon ?: defaultIcon,
            imageUrl = if (icon == null) defaultImageUrl else state.draftIconUrl(icon),
            color = color,
            size = 44,
        )
        Column(Modifier.weight(1f)) {
            Text("Icon", style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    icon == null -> "Uses $defaultLabel"
                    icon.startsWith("fa:") -> icon.removePrefix("fa:")
                    icon.startsWith("upload:") -> "Uploaded image"
                    icon.startsWith("<") -> "Server emoji"
                    else -> "Linked image"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        OutlinedButton(onClick = { picking = true }) { Text("Change") }
    }
    if (picking) {
        AchievementIconPicker(
            state = state,
            viewModel = viewModel,
            icon = icon,
            defaultLabel = defaultLabel,
            color = color,
            onPick = {
                onChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** The icon picker: Font Awesome icons, the server's emojis, or an image by link or upload. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AchievementIconPicker(
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    icon: String?,
    defaultLabel: String,
    color: Color,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("icons") }
    var search by remember { mutableStateOf("") }
    var emojiSearch by remember { mutableStateOf("") }
    var emojiKind by remember { mutableStateOf("all") }
    var link by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AchievementIconUpload?>(null) }
    val glyphs = remember { FaGlyphTable.all(context) }
    val shown = remember(search, glyphs) {
        val term = search.trim().lowercase()
        if (term.isEmpty()) glyphs else glyphs.filter { g -> g.name.contains(term) || g.aliases.any { it.contains(term) } }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val png = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bitmap ->
                    val scale = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }
            }
            val upload = png?.let { viewModel.uploadIcon(it, "image/png") }
            uploading = false
            if (upload != null) onPick(upload.icon)
        }
    }

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "Icon") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            SectionTabs(
                tabs = listOf(
                    SectionTab("icons", "Icons", glyph = FaGlyph.Star),
                    SectionTab("emojis", "Emojis", glyph = FaGlyph.FaceSmile),
                    SectionTab("image", "Image", glyph = FaGlyph.Folder),
                ),
                selectedId = source,
                onSelect = { source = it },
                level = TabLevel.Secondary,
            )
            if (icon != null) {
                TextButton(onClick = { onPick(null) }) { Text("Use $defaultLabel") }
            }
            when (source) {
                "icons" -> {
                    SearchField(value = search, onValueChange = { search = it }, placeholder = "Search icons, like trophy or star",
                        fontAwesome = true)
                    PickerGrid {
                        items(shown, key = { it.name }) { glyph ->
                            val stored = "fa:${glyph.name}"
                            PickerCell(picked = icon == stored, label = glyph.name, color = color, onClick = { onPick(stored) }) {
                                FaIcon(glyph.codepoint, size = 20.dp,
                                    tint = if (icon == stored) color else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                "emojis" -> {
                    val emojis = state.lookups?.emojis.orEmpty()
                    if (emojis.isEmpty()) {
                        Text("This server has no emojis of its own.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SearchField(value = emojiSearch, onValueChange = { emojiSearch = it }, placeholder = "Search emojis by name",
                            fontAwesome = true)
                        SectionTabs(
                            tabs = listOf(
                                SectionTab("all", "All"),
                                SectionTab("still", "Still"),
                                SectionTab("animated", "Animated"),
                            ),
                            selectedId = emojiKind,
                            onSelect = { emojiKind = it },
                            level = TabLevel.Secondary,
                        )
                        val term = emojiSearch.trim().lowercase()
                        val shownEmojis = emojis.filter { emoji ->
                            val animated = emoji.formatted.startsWith("<a:")
                            (emojiKind == "all" || (emojiKind == "animated") == animated) &&
                                (term.isEmpty() || emoji.name.lowercase().contains(term))
                        }
                        if (shownEmojis.isEmpty()) {
                            Text("No emojis match.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PickerGrid {
                            items(shownEmojis, key = { it.id }) { emoji ->
                                PickerCell(picked = icon == emoji.formatted, label = emoji.name, color = color,
                                    onClick = { onPick(emoji.formatted) }) {
                                    AsyncImage(model = emoji.url, contentDescription = null, modifier = Modifier.size(30.dp))
                                }
                            }
                        }
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MewdekoTextField(value = link, onValueChange = { link = it.trim() }, label = "Image link",
                            placeholder = "https://example.com/icon.png", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { onPick(link) }, enabled = link.startsWith("https://")) { Text("Use") }
                    }
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        enabled = !uploading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        FaIcon(FaGlyph.CirclePlus, size = 16.dp)
                        Text(if (uploading) "  Uploading" else "  Upload an image")
                    }
                    val uploads = state.catalog?.uploads.orEmpty()
                    if (uploads.isNotEmpty()) {
                        PickerGrid {
                            items(uploads, key = { it.id }) { upload ->
                                PickerCell(
                                    picked = icon == upload.icon,
                                    label = "Uploaded image ${upload.id}",
                                    color = color,
                                    onClick = { onPick(upload.icon) },
                                    onLongClick = { pendingDelete = upload },
                                ) {
                                    AsyncImage(model = state.iconImageUrl(upload.url), contentDescription = null,
                                        modifier = Modifier.size(34.dp))
                                }
                            }
                        }
                        Text("Press and hold an image to delete it.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    pendingDelete?.let { upload ->
        ConfirmDialog(
            title = "Delete this image?",
            message = "Achievements and categories using it go back to their default icon.",
            confirmLabel = "Delete image",
            onConfirm = {
                pendingDelete = null
                if (icon == upload.icon) onPick(null)
                viewModel.deleteIcon(upload)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PickerGrid(content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(56.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerCell(
    picked: Boolean,
    label: String,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(if (picked) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08), shape)
            .border(1.5.dp, if (picked) color else Color.Transparent, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = label
                selected = picked
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Make or rename a server category. */
@Composable
internal fun CategoryForm(
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    category: AchievementCategoryItem?,
    onSave: (String, String?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var icon by remember { mutableStateOf(category?.icon?.takeIf { it != "fa:folder" }) }
    var description by remember { mutableStateOf(category?.description.orEmpty()) }
    FormSheet(
        title = if (category == null) "New category" else "Edit category",
        confirmLabel = if (category == null) "Add" else "Save",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(name, icon, description) },
        onDismiss = onDismiss,
    ) {
        MewdekoTextField(value = name, onValueChange = { name = it.take(80) }, label = "Name", placeholder = "Events")
        MewdekoTextField(value = description, onValueChange = { description = it.take(200) }, label = "Description",
            placeholder = "What goes in it", singleLine = false, minLines = 2)
        AchievementIconField(
            state = state,
            viewModel = viewModel,
            icon = icon,
            onChange = { icon = it },
            defaultIcon = "fa:folder",
            defaultImageUrl = null,
            defaultLabel = "the folder icon",
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
