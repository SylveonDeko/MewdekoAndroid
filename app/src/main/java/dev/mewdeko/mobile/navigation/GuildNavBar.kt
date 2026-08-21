package dev.mewdeko.mobile.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.NavBarLabelStyle
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.clickableRow

/**
 * One destination on the guild bottom bar.
 *
 * [featureId] is null for Home, which is the guild overview rather than a
 * feature page.
 */
private data class GuildTab(
    val label: String,
    val icon: ImageVector,
    val featureId: String?,
)

/**
 * The quick-access destinations shown in the bottom bar. The instance
 * selector lives in the guild app bar instead, since it switches context
 * rather than navigating, keeping this bar within Material's 3-5 destination
 * guidance.
 */
private val GuildTabs = listOf(
    GuildTab("Home", Icons.Default.Home, null),
    GuildTab("Settings", Icons.Default.Tune, "settings"),
    GuildTab("Music", Icons.Default.MusicNote, "music"),
    GuildTab("XP", Icons.Default.Star, "xp"),
)

/**
 * Persistent bottom navigation shown throughout a guild: four pinned
 * features plus a "More" entry that opens the full catalog.
 */
@Composable
fun GuildNavBar(
    activeFeatureId: String?,
    isOnGuildHome: Boolean,
    onSelectTab: (String?) -> Unit,
    onOpenMore: () -> Unit,
    moreIsOpen: Boolean,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    NavigationBar {
        GuildTabs.forEach { tab ->
            val selected = if (tab.featureId == null) {
                isOnGuildHome && !moreIsOpen
            } else {
                activeFeatureId == tab.featureId && !moreIsOpen
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab.featureId) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        tab.label,
                        style = NavBarLabelStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
            )
        }
        NavigationBarItem(
            selected = moreIsOpen ||
                (activeFeatureId != null && GuildTabs.none { it.featureId == activeFeatureId }),
            onClick = onOpenMore,
            icon = { Icon(Icons.Default.Apps, contentDescription = null) },
            label = { Text("More", style = NavBarLabelStyle) },
            alwaysShowLabel = true,
        )
    }
}

/**
 * The full feature catalog as a bottom sheet.
 *
 * The dashboard renders this as one flat grid of 41 tiles. Here the category
 * filter and search are kept, because scanning a flat 41-tile grid on a phone
 * is markedly slower than filtering first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureCatalogSheet(
    activeFeatureId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FeatureCategory?>(null) }

    val visible = remember(query, category) {
        NavigationCatalog.items
            .filter { item ->
                (category == null || item.category == category) &&
                    (query.isBlank() ||
                        item.label.contains(query, ignoreCase = true) ||
                        item.summary.contains(query, ignoreCase = true))
            }
            .sortedBy { it.label.lowercase() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Text(
                "All features",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search features",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("All") },
                )
                FeatureCategory.order.forEach { entry ->
                    FilterChip(
                        selected = category == entry,
                        onClick = { category = if (category == entry) null else entry },
                        label = { Text(entry.label) },
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(visible, key = { it.id }) { item ->
                    CatalogTile(
                        label = item.label,
                        icon = item.icon,
                        selected = item.id == activeFeatureId,
                        onClick = { onSelect(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier.clickableRow(onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
