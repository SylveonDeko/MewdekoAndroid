package dev.mewdeko.mobile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import kotlinx.coroutines.flow.first

/** One choice in a [SectionTabs] row. */
data class SectionTab(
    val id: String,
    val title: String,
    val icon: ImageVector? = null,
)

/** Which tier of navigation a [SectionTabs] row switches. */
enum class TabLevel {
    /** The top-level sections of a feature screen. */
    Primary,

    /** A sub-switcher inside a section, such as picking one embed of several. */
    Secondary,
}

/** Inset between the capsule track and its pills. */
private val TrackPadding = 4.dp

/** Gap between neighbouring pills. */
private val PillGap = 4.dp

/** Horizontal padding inside a pill. */
private val PillPadding = 14.dp

/** Icon size inside a pill. */
private val PillIcon = 16.dp

/** Gap between a pill's icon and its label. */
private val PillIconGap = 6.dp

/** Width of the fade drawn over a track edge that can still scroll. */
private val EdgeFade = 16.dp

/**
 * The app's only sub-setting switcher. Use it for every set of sections at
 * the top of a feature screen and for every smaller switch inside one, so
 * all of them look and behave the same.
 *
 * A capsule track holds one pill per tab. Labels are never shortened: when
 * every pill fits, the spare width is shared out so the row reads as one
 * segmented control; when they do not fit, the pills keep their natural
 * width and the row scrolls sideways, with a soft fade on any edge that
 * hides more pills. Picking a pill, or [selectedId] changing from outside,
 * scrolls that pill into view.
 *
 * [TabLevel.Primary] is for the feature's own sections. Use
 * [TabLevel.Secondary] for a switch nested inside a section: it is shorter,
 * uses smaller text, and drops the track border so it reads as subordinate.
 *
 * Do not use this to pick a setting's value (a style, a mode, a unit); that
 * is [EnumPicker]. Tabs change what the screen shows, not what gets saved.
 */
@Composable
fun SectionTabs(
    tabs: List<SectionTab>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    level: TabLevel = TabLevel.Primary,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = level == TabLevel.Secondary
    val pillHeight = if (secondary) 32.dp else 40.dp
    val textStyle = if (secondary) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }
    val selectedIndex = tabs.indexOfFirst { it.id == selectedId }
    val listState = rememberLazyListState()

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (secondary) null else BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widths = rememberPillWidths(
                tabs = tabs,
                style = textStyle,
                available = maxWidth - TrackPadding * 2,
            )
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(TrackPadding),
                horizontalArrangement = Arrangement.spacedBy(PillGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .edgeFades(listState)
                    .selectableGroup()
                    .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabs.size) },
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
                    TabPill(
                        tab = tab,
                        selected = index == selectedIndex,
                        index = index,
                        count = tabs.size,
                        height = pillHeight,
                        width = widths?.getOrNull(index),
                        textStyle = textStyle,
                        onClick = { onSelect(tab.id) },
                    )
                }
            }
        }
    }

    ScrollSelectedIntoView(listState = listState, selectedIndex = selectedIndex)
}

/**
 * Stretched pill widths when every pill fits in [available], sharing the
 * spare width out evenly; null when the pills overflow and should keep their
 * natural widths and scroll.
 */
@Composable
private fun rememberPillWidths(
    tabs: List<SectionTab>,
    style: TextStyle,
    available: Dp,
): List<Dp>? {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val natural = remember(tabs, style, density) {
        tabs.map { tab ->
            val text = measurer.measure(
                text = tab.title,
                style = style,
                maxLines = 1,
                softWrap = false,
            ).size.width
            val icon = if (tab.icon != null) PillIcon + PillIconGap else 0.dp
            with(density) { text.toDp() } + icon + PillPadding * 2 + 1.dp
        }
    }
    if (tabs.isEmpty()) return null
    val used = natural.fold(0.dp) { sum, width -> sum + width } + PillGap * (tabs.size - 1)
    if (used > available) return null
    val extra = (available - used) / tabs.size
    return natural.map { it + extra }
}

/** One pill in a [SectionTabs] track. */
@Composable
private fun TabPill(
    tab: SectionTab,
    selected: Boolean,
    index: Int,
    count: Int,
    height: Dp,
    width: Dp?,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val selectedInk = readableInk(primary, MaterialTheme.colorScheme.surfaceContainerHigh)
    val fill by animateColorAsState(
        if (selected) primary.copy(alpha = DashAlpha.Hex20) else Color.Transparent,
        label = "pillFill",
    )
    val outline by animateColorAsState(
        if (selected) primary.copy(alpha = DashAlpha.Hex30) else Color.Transparent,
        label = "pillBorder",
    )
    val ink by animateColorAsState(
        if (selected) selectedInk else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillInk",
    )

    Row(
        modifier = Modifier
            .height(height)
            .then(if (width != null) Modifier.width(width) else Modifier)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(1.dp, outline, CircleShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics {
                collectionItemInfo = CollectionItemInfo(
                    rowIndex = 0,
                    rowSpan = 1,
                    columnIndex = index,
                    columnSpan = 1,
                )
                stateDescription = if (selected) {
                    "Selected, ${index + 1} of $count"
                } else {
                    "${index + 1} of $count"
                }
            }
            .padding(horizontal = PillPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PillIconGap, Alignment.CenterHorizontally),
    ) {
        if (tab.icon != null) {
            Icon(tab.icon, contentDescription = null, tint = ink, modifier = Modifier.size(PillIcon))
        }
        Text(
            text = tab.title,
            style = textStyle,
            color = ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Fades whichever edge of a scrolling row still hides content, so the user
 * can tell there are more pills to swipe to.
 */
private fun Modifier.edgeFades(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = EdgeFade.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        val fadeLeft = if (rtl) state.canScrollForward else state.canScrollBackward
        val fadeRight = if (rtl) state.canScrollBackward else state.canScrollForward
        if (fadeLeft) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startX = 0f,
                    endX = fade,
                ),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (fadeRight) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startX = size.width - fade,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * Centers the selected pill whenever the selection changes. The first pass,
 * when the screen opens, jumps without animating.
 */
@Composable
private fun ScrollSelectedIntoView(listState: LazyListState, selectedIndex: Int) {
    val initial = remember { booleanArrayOf(true) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        val animate = !initial[0]
        initial[0] = false
        if (!listState.canScrollForward && !listState.canScrollBackward) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        if (item == null) {
            if (animate) listState.animateScrollToItem(selectedIndex) else listState.scrollToItem(selectedIndex)
            return@LaunchedEffect
        }
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
        val delta = item.offset + item.size / 2f - viewportCenter
        if (animate) listState.animateScrollBy(delta) else listState.scrollBy(delta)
    }
}

/** A search field styled to match the feature cards around it. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    )
}
