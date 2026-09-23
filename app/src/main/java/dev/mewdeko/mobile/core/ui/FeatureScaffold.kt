package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The standard shell for a feature screen.
 *
 * Wraps a Material 3 [Scaffold] with a collapsing top app bar, pull to
 * refresh, a snackbar host wired to [status], and the three load states, so
 * individual features only supply their content column.
 *
 * Passing [immersive] switches to a pinned transparent bar over edge-to-edge
 * content; in that mode the caller renders its own loading and error states.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScaffold(
    title: String,
    onBack: (() -> Unit)?,
    loadState: LoadState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: StatusMessage? = null,
    onStatusShown: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    immersive: ImmersiveBar? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(status) {
        val current = status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = current.text,
            duration = SnackbarDuration.Short,
        )
        onStatusShown()
    }

    if (immersive != null) {
        ImmersiveScaffold(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            loadState = loadState,
            modifier = modifier,
            snackbarHostState = snackbarHostState,
            onRefresh = onRefresh,
            actions = actions,
            floatingActionButton = floatingActionButton,
            immersive = immersive,
            content = content,
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .guildGlow()
                .imePadding(),
        ) {
            when {
                loadState.showsInitialSpinner -> LoadingState()
                loadState.showsError -> ErrorState(loadState.error.orEmpty(), onRetry)
                else -> RefreshableBody(
                    isRefreshing = loadState.isRefreshing,
                    onRefresh = onRefresh,
                    scrollable = scrollable,
                    content = content,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableBody(
    isRefreshing: Boolean,
    onRefresh: (() -> Unit)?,
    scrollable: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body = @Composable {
        if (scrollable) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(FeatureContentPadding)
                    .padding(bottom = FloatingActionClearance),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        } else {
            Column(modifier = Modifier.fillMaxSize(), content = content)
        }
    }

    if (onRefresh != null) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) { body() }
    } else {
        body()
    }
}

/**
 * Scroll-driven chrome state for a screen whose hero draws under the top app bar.
 *
 * Both lambdas are read only inside draw or graphics layer blocks, so
 * scrolling never recomposes the bar.
 */
@Stable
class ImmersiveBar(
    val containerFraction: () -> Float,
    val titleFraction: () -> Float,
)

/**
 * The immersive variant of [FeatureScaffold].
 *
 * The bar is pinned and transparent, filling to the neutral
 * `surfaceContainer` as [ImmersiveBar.containerFraction] rises, and the
 * content draws under both the status bar and the bar over the page's
 * [guildGlow]. At a fraction of one it is exactly the standard scrolled small
 * top app bar. The caller owns loading and error rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImmersiveScaffold(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    loadState: LoadState,
    modifier: Modifier,
    snackbarHostState: SnackbarHostState,
    onRefresh: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    floatingActionButton: @Composable () -> Unit,
    immersive: ImmersiveBar,
    content: @Composable ColumnScope.() -> Unit,
) {
    val barColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurface = MaterialTheme.colorScheme.onSurface

    Scaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawRect(barColor, alpha = immersive.containerFraction())
                    },
            ) {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.graphicsLayer { alpha = immersive.titleFraction() }) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            ImmersiveIconButton(
                                onClick = onBack,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                fraction = immersive.containerFraction,
                            )
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = onSurface,
                        titleContentColor = onSurface,
                        actionIconContentColor = onSurface,
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .guildGlow()
                .padding(bottom = padding.calculateBottomPadding())
                .imePadding(),
        ) {
            if (onRefresh != null) {
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = loadState.isRefreshing,
                    onRefresh = onRefresh,
                    state = refreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = loadState.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = padding.calculateTopPadding()),
                        )
                    },
                ) {
                    Column(modifier = Modifier.fillMaxSize(), content = content)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    }
}

/** Icon button with a translucent tonal disc that fades out as the immersive bar fills. */
@Composable
fun ImmersiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    fraction: () -> Float,
) {
    val backing = MaterialTheme.colorScheme.surfaceContainerHigh
    IconButton(
        onClick = onClick,
        modifier = Modifier.drawBehind {
            drawCircle(
                color = backing,
                radius = 20.dp.toPx(),
                alpha = 0.72f * (1f - fraction()),
            )
        },
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

/**
 * Trailing space so a floating action button never covers the last card.
 *
 * Scaffold does not reserve room for its own action button, and most feature
 * pages here have one.
 */
private val FloatingActionClearance = 80.dp

/**
 * The dashboard's page glow, drawn once behind a screen's content.
 *
 * Mirrors the web `radial-gradient(circle at top, start15 0%, mid10 50%,
 * end05 100%)`: a circle anchored at the top center running from the
 * gradient start at the `15` tint through the middle stop at `10` to the end
 * stop at `05`, which then holds for the rest of the page. It is the only
 * palette color on the neutral canvas. The light theme uses half the alpha.
 */
@Composable
fun Modifier.guildGlow(): Modifier {
    val palette = LocalGuildPalette.current
    val scale = if (isDarkScheme()) 1f else 0.5f
    val start = palette.gradientStart.color.copy(alpha = DashAlpha.Hex15 * scale)
    val middle = palette.gradientMid.color.copy(alpha = DashAlpha.Hex10 * scale)
    val end = palette.gradientEnd.color.copy(alpha = DashAlpha.Hex05 * scale)
    return drawWithCache {
        val glow = Brush.radialGradient(
            0f to start,
            0.5f to middle,
            1f to end,
            center = Offset(size.width / 2f, 0f),
            radius = GlowRadius.toPx(),
        )
        onDrawBehind { drawRect(glow) }
    }
}

/** How far the page glow reaches from the top center before it holds its edge tint. */
private val GlowRadius = 480.dp

/** The standard horizontal and vertical inset for feature content. */
val FeatureContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
