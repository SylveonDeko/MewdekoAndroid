package dev.mewdeko.mobile.feature.guildlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.guildGlow
import dev.mewdeko.mobile.feature.guilddetail.home.EnteredKeys
import dev.mewdeko.mobile.feature.guilddetail.home.isLargeFont
import dev.mewdeko.mobile.feature.guilddetail.home.riseOnce
import dev.mewdeko.mobile.feature.guilddetail.home.tabular
import kotlin.math.min

/** The number of breathing placeholder tiles shown during the first load. */
private const val SkeletonTileCount = 6

/** The per-tile delay of the staggered rise-in, in milliseconds. */
private const val RiseStaggerMillis = 35

/** The most tiles that wait on the stagger; later ones rise with the last. */
private const val RiseStaggerCap = 10

/**
 * Lists the guilds the signed-in user can administer with this bot.
 *
 * The first screen after sign in, lit by the house palette just as iOS
 * scopes it: a summary line, a "Jump back in" card for the last opened
 * server, then every server as a tile glowing with its own blurred icon or
 * banner. Search narrows the grid to the matches; pull to refresh keeps the
 * current tiles on screen while it reloads.
 */
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
    val uriHandler = LocalUriHandler.current
    val open: (Guild) -> Unit = { guild ->
        viewModel.recordOpened(guild)
        onOpenGuild(guild)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onScreenShown() }

    MewdekoTheme(palette = GuildPalette.Default) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
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
                    actions = {
                        state.inviteUrl?.let { url ->
                            IconButton(onClick = { uriHandler.openUri(url) }) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "Add Mewdeko to another server")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .guildGlow(),
            ) {
                when {
                    state.load.showsInitialSpinner -> SkeletonGrid()
                    state.load.showsError -> ErrorState(
                        message = state.load.error.orEmpty(),
                        onRetry = { viewModel.load() },
                    )

                    else -> PullToRefreshBox(
                        isRefreshing = state.load.isRefreshing,
                        onRefresh = { viewModel.load(refreshing = true) },
                    ) {
                        LoadedGrid(
                            state = state,
                            onQueryChange = viewModel::setQuery,
                            onOpen = open,
                        )
                    }
                }
            }
        }
    }
}

/** The number of grid columns: two, or one at large font scales. */
@Composable
private fun columnCount(): Int = if (isLargeFont()) 1 else 2

/** A grid item that spans every column. */
private fun LazyGridScope.fullWidth(key: String, content: @Composable LazyGridItemScope.() -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, content = content)
}

/** The search field, summary, "Jump back in" card and tile grid for a loaded list. */
@Composable
private fun LoadedGrid(
    state: GuildListState,
    onQueryChange: (String) -> Unit,
    onOpen: (Guild) -> Unit,
) {
    val columns = columnCount()
    val entered = remember { EnteredKeys() }
    val gridState = rememberLazyGridState()
    val visible = state.visibleGuilds
    val recent = state.recentGuild

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.guilds.isEmpty()) {
            fullWidth("empty") {
                val uriHandler = LocalUriHandler.current
                EmptyState(
                    message = "Mewdeko isn't in any server you manage yet. Add it to a server with " +
                        "Manage Server permission, then pull down to refresh.",
                    icon = Icons.Default.Dashboard,
                    actionLabel = state.inviteUrl?.let { "Add Mewdeko to a server" },
                    onAction = state.inviteUrl?.let { url -> { uriHandler.openUri(url) } },
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            return@LazyVerticalGrid
        }

        fullWidth("search") {
            SearchField(query = state.query, onQueryChange = onQueryChange)
        }

        if (!state.isSearching) {
            fullWidth("summary") {
                Summary(total = state.guilds.size, owned = state.ownedCount)
            }
            if (recent != null) {
                fullWidth("recent") {
                    JumpBackInCard(
                        guild = recent,
                        onClick = { onOpen(recent) },
                        modifier = Modifier
                            .animateItem()
                            .riseOnce("recent:${recent.id}", entered),
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            fullWidth("nomatches") {
                EmptyState(
                    message = "No matches. No server names contain “${state.trimmedQuery}”.",
                    icon = Icons.Default.SearchOff,
                    modifier = Modifier.padding(top = 32.dp),
                )
            }
        } else {
            fullWidth("header") {
                GridHeader(
                    title = if (state.isSearching) "Results" else "All servers",
                    count = visible.size,
                )
            }
            itemsIndexed(visible, key = { _, guild -> "guild:${guild.id}" }) { index, guild ->
                GuildTile(
                    guild = guild,
                    singleColumn = columns == 1,
                    onClick = { onOpen(guild) },
                    modifier = Modifier
                        .animateItem()
                        .riseOnce(
                            key = "guild:${guild.id}",
                            entered = entered,
                            delayMillis = min(index, RiseStaggerCap) * RiseStaggerMillis,
                        ),
                )
            }
        }
    }
}

/** Six breathing placeholder tiles while the first load runs. */
@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount()),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(SkeletonTileCount) { index ->
            GuildSkeletonTile(announce = index == 0)
        }
    }
}

/** The search field that narrows the grid by server name. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search servers") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = primary,
            unfocusedBorderColor = primary.copy(alpha = DashAlpha.Hex30),
            focusedLeadingIconColor = primary,
            cursorColor = primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "12 servers, 3 owned" with the counts in bold tabular numerals. */
@Composable
private fun Summary(total: Int, owned: Int) {
    val noun = if (total == 1) "server" else "servers"
    val numeral = SpanStyle(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        color = MaterialTheme.colorScheme.onSurface,
    )
    val text = buildAnnotatedString {
        withStyle(numeral) { append("%,d".format(total)) }
        append(" $noun, ")
        withStyle(numeral) { append("%,d".format(owned)) }
        append(" owned")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$total $noun, $owned owned"
        },
    )
}

/** The heading above the grid with its tile count. */
@Composable
private fun GridHeader(title: String, count: Int) {
    val noun = if (count == 1) "server" else "servers"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(top = 4.dp)
            .clearAndSetSemantics {
                heading()
                contentDescription = "$title, $count $noun"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "%,d".format(count),
            style = MaterialTheme.typography.titleMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
