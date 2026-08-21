package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The standard shell for a feature screen.
 *
 * Wraps a Material 3 [Scaffold] with a collapsing top app bar, pull to
 * refresh, a snackbar host wired to [status], and the three load states, so
 * individual features only supply their content column.
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    LaunchedEffect(status) {
        val current = status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = current.text,
            duration = SnackbarDuration.Short,
        )
        onStatusShown()
    }

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
 * Trailing space so a floating action button never covers the last card.
 *
 * Scaffold does not reserve room for its own action button, and most feature
 * pages here have one.
 */
private val FloatingActionClearance = 80.dp

/** The standard horizontal and vertical inset for feature content. */
val FeatureContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
