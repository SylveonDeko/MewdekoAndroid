package dev.mewdeko.mobile.feature.repeaters

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.BarChart),
    SectionTab("manage", "Manage", Icons.AutoMirrored.Filled.ListAlt),
)

/** Which single property a [QuickEditDialog] is editing. */
enum class QuickEditField { INTERVAL, START_TIME, THRESHOLD, EXPIRY }

/** Recurring and sticky messages: overview stats, a manage list, and a rich create/edit form. */
@Composable
fun RepeatersScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RepeatersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var section by remember { mutableStateOf("overview") }
    var formTarget by remember { mutableStateOf<RepeaterEntry?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RepeaterEntry?>(null) }
    var quickEdit by remember { mutableStateOf<Pair<RepeaterEntry, QuickEditField>?>(null) }

    FeatureScaffold(
        title = "Repeaters",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (section == "manage") {
                NewItemFab(label = "New repeater", onClick = { showCreate = true })
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = section, onSelect = { section = it })

        when (section) {
            "overview" -> RepeatersOverviewTab(state = state)
            else -> RepeatersManageTab(
                state = state,
                viewModel = viewModel,
                onEdit = { formTarget = it },
                onDelete = { pendingDelete = it },
                onQuickEdit = { repeater, field -> quickEdit = repeater to field },
                onNew = { showCreate = true },
            )
        }
    }

    if (showCreate) {
        RepeaterEditor(
            state = state,
            original = null,
            draft = RepeaterDraft(),
            onDismiss = { showCreate = false },
            onSave = { draft ->
                viewModel.create(draft)
                showCreate = false
            },
        )
    }

    formTarget?.let { repeater ->
        RepeaterEditor(
            state = state,
            original = repeater,
            draft = RepeaterDraft.from(repeater),
            onDismiss = { formTarget = null },
            onSave = { draft ->
                viewModel.saveEdit(repeater, draft)
                formTarget = null
            },
        )
    }

    pendingDelete?.let { repeater ->
        ConfirmDialog(
            title = "Delete repeater?",
            message = "The repeater in #${state.channelName(repeater.channelId)} stops posting. This cannot be undone.",
            onConfirm = { viewModel.remove(repeater.id) },
            onDismiss = { pendingDelete = null },
        )
    }

    quickEdit?.let { (repeater, field) ->
        QuickEditDialog(
            repeater = repeater,
            field = field,
            onDismiss = { quickEdit = null },
            viewModel = viewModel,
        )
    }
}
