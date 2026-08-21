package dev.mewdeko.mobile.feature.guilddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureLinkCard
import dev.mewdeko.mobile.navigation.FeatureCategory
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.navigation.NavigationCatalog

/** Searchable catalog of every feature page available for a guild. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureBrowserScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<FeatureCategory?>(null) }

    val visible = remember(query, category) {
        NavigationCatalog.items.filter { item ->
            (category == null || item.category == category) &&
                (query.isBlank() ||
                    item.label.contains(query, ignoreCase = true) ||
                    item.summary.contains(query, ignoreCase = true))
        }.sortedBy { it.label.lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Features") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search features") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                }

                items(visible, key = { it.id }) { item ->
                    FeatureLinkCard(
                        title = item.label,
                        subtitle = item.summary,
                        icon = item.icon,
                        onClick = { onOpenFeature(item.id) },
                    )
                }

                if (visible.isEmpty()) {
                    item {
                        EmptyState(
                            message = "No features match \"$query\".",
                            icon = Icons.Default.Search,
                        )
                    }
                }
            }
        }
    }
}
