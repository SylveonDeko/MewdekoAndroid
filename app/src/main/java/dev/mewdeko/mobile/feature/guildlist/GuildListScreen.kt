package dev.mewdeko.mobile.feature.guildlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.LoadingState
import dev.mewdeko.mobile.core.ui.clickableRow

/** Lists the guilds the signed-in user can administer with this bot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildListScreen(
    userId: String,
    instanceName: String?,
    onOpenGuild: (Guild) -> Unit,
    viewModel: GuildListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text("Servers")
                        if (instanceName != null) {
                            Text(
                                text = instanceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.load.showsInitialSpinner -> LoadingState()
                state.load.showsError -> ErrorState(
                    message = state.load.error.orEmpty(),
                    onRetry = { viewModel.load() },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.load.isRefreshing,
                    onRefresh = { viewModel.load(refreshing = true) },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setQuery,
                                placeholder = { Text("Search servers") },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = {
                                    if (state.query.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                        }

                        items(state.visibleGuilds, key = { it.id }) { guild ->
                            GuildRow(guild = guild, onClick = { onOpenGuild(guild) })
                        }

                        if (state.visibleGuilds.isEmpty()) {
                            item {
                                EmptyState(
                                    message = if (state.query.isBlank()) {
                                        "No servers where you have admin access and this bot is present."
                                    } else {
                                        "No servers match \"${state.query}\"."
                                    },
                                    icon = Icons.Default.Dashboard,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuildRow(guild: Guild, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(guild.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = if (guild.owner) "Owner" else "Administrator",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Avatar(
                url = guild.iconUrl,
                contentDescription = guild.name,
                size = 44,
                fallbackIcon = Icons.Default.Shield,
            )
        },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickableRow(onClick),
    )
}
