package dev.mewdeko.mobile.feature.owner.docker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.OrbSize
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.ShellCallout
import dev.mewdeko.mobile.core.ui.ShellDimens
import dev.mewdeko.mobile.core.ui.ShellRoles
import dev.mewdeko.mobile.core.ui.ShellTone
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.TabLevel
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.guildWash
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.core.ui.rememberShellRoles
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import dev.mewdeko.mobile.core.ui.toneWash
import dev.mewdeko.mobile.core.ui.washed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** The page's own subtitle, the dashboard's `DashboardPageLayout` subtitle. */
private const val PAGE_SUBTITLE = "Containers and compose projects on the selected instance's host"

/** The log panel's fixed height. */
private val LogPanelHeight = 440.dp

/** The compose job panel's maximum height. */
private val JobPanelMaxHeight = 360.dp

/** How close to the end of the log counts as "at the bottom". */
private val BottomSlack = 40.dp

/** Terminal text: monospace at a size that fits a useful width on a phone. */
private val TerminalText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

/** A pending confirmation: the dashboard's `requestConfirmation` arguments. */
private data class DockerConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val tone: ShellTone,
    val onConfirm: () -> Unit,
)

/**
 * Containers and compose projects on the bot's host, mirroring the
 * dashboard's `/owner/docker`. Fleet level: everything acts on the selected
 * bot instance's host, except the Bots section, which asks every registered
 * instance about itself.
 */
@Composable
fun DockerScreen(
    onBack: () -> Unit,
    viewModel: DockerViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirm by remember { mutableStateOf<DockerConfirm?>(null) }
    val pageList = rememberLazyListState()

    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.runPolling() }
    }

    LaunchedEffect(state.selectedId) {
        if (state.selectedId == null) return@LaunchedEffect
        withFrameNanos { }
        val count = pageList.layoutInfo.totalItemsCount
        if (count > 0) pageList.animateScrollToItem(count - 1)
    }

    FeatureScaffold(
        title = "Docker",
        subtitle = state.instanceName,
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        scrollable = false,
    ) {
        LazyColumn(
            state = pageList,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = PAGE_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "daemon") {
                DaemonSection(state = state, onRefresh = viewModel::refreshOverview)
            }
            state.notice?.let { notice ->
                item(key = "notice-${notice.id}") {
                    NoticeBanner(notice = notice, onDismiss = viewModel::dismissNotice)
                }
            }
            item(key = "bots") {
                BotsSection(
                    state = state,
                    onCheck = viewModel::checkForUpdates,
                    onUpdateAll = {
                        val names = state.fleet.filter { it.info?.canUpdate == true }.map { it.instance.botName }
                        val list = if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
                        confirm = DockerConfirm(
                            title = "Update every bot?",
                            message = "Pull the newest image once and recreate every container in the fleet whose " +
                                "image changed$list. Each bot restarts in turn, including the one you are looking " +
                                "at, so this page loses contact for a moment.",
                            confirmLabel = "Update all",
                            tone = ShellTone.Negative,
                            onConfirm = viewModel::updateAll,
                        )
                    },
                    onLogs = viewModel::showFleetLogs,
                    onUpdate = { entry ->
                        val name = entry.instance.botName
                        val target = entry.info?.published?.gitSha ?: "the newest image"
                        confirm = DockerConfirm(
                            title = "Update $name?",
                            message = "Pull $target and recreate $name's container. The bot goes offline for the " +
                                "restart, usually a minute or two.",
                            confirmLabel = "Update",
                            tone = ShellTone.Caution,
                            onConfirm = { viewModel.updateBot(entry) },
                        )
                    },
                )
            }

            val overview = state.overview
            if (overview != null && overview.isAvailable) {
                item(key = "containers-header") {
                    DockerSectionHeader(
                        title = "Containers",
                        subtitle = "Grouped by compose project. Expand a project to sample its resource use.",
                        icon = Icons.Default.Dns,
                    )
                }
                val groups = overview.groups()
                if (groups.isEmpty()) {
                    item(key = "containers-empty") {
                        SectionCard { EmptyState("The daemon has no containers", icon = Icons.Default.Inventory2) }
                    }
                }
                items(groups, key = { "group-${it.key}" }) { group ->
                    GroupCard(
                        group = group,
                        state = state,
                        composeAvailable = overview.composeAvailable,
                        onToggle = { viewModel.toggleGroup(group.key) },
                        onCompose = { project, operation ->
                            val message = when (operation) {
                                DockerComposeOperation.Pull ->
                                    "Fetch the latest images for ${project.name} and rebuild anything built " +
                                        "locally. Nothing restarts until you run Up."

                                DockerComposeOperation.Up ->
                                    "Bring ${project.name} up. Containers whose image or config changed are " +
                                        "recreated; the rest are left alone."

                                DockerComposeOperation.Update ->
                                    "Fetch the latest images for ${project.name}, then bring it up so changed " +
                                        "containers are recreated."
                            }
                            confirm = DockerConfirm(
                                title = "Compose ${operation.path} on ${project.name}?",
                                message = message,
                                confirmLabel = "Run ${operation.path}",
                                tone = if (operation == DockerComposeOperation.Pull) ShellTone.Brand else ShellTone.Caution,
                                onConfirm = { viewModel.startCompose(project, operation) },
                            )
                        },
                        onSelect = viewModel::selectContainer,
                        onAction = { container, action ->
                            if (action == DockerContainerAction.Start) {
                                viewModel.runAction(container, action)
                            } else {
                                val verb = action.verb
                                val until = if (action == DockerContainerAction.Stop) {
                                    " until it is started again from elsewhere"
                                } else {
                                    " until it comes back"
                                }
                                val selfWarning = if (container.isSelf) {
                                    " This is the container the selected bot instance runs in, so the dashboard " +
                                        "will lose contact with it$until."
                                } else {
                                    ""
                                }
                                val inProject = container.composeProject?.let { " in $it" }.orEmpty()
                                confirm = DockerConfirm(
                                    title = "$verb ${container.name}?",
                                    message = "$verb the ${container.name} container$inProject.$selfWarning",
                                    confirmLabel = verb,
                                    tone = if (container.isSelf) ShellTone.Negative else ShellTone.Caution,
                                    onConfirm = { viewModel.runAction(container, action) },
                                )
                            }
                        },
                    )
                }
                if (state.activeJob != null || state.jobs.isNotEmpty()) {
                    item(key = "jobs") {
                        JobsSection(state = state, onSelectJob = viewModel::selectJob)
                    }
                }
                state.selected?.let { container ->
                    item(key = "log-${container.id}") {
                        LogViewer(
                            container = container,
                            state = state,
                            onLineCount = viewModel::setLineCount,
                            onSearch = viewModel::setSearch,
                            onToggleFollow = viewModel::toggleFollow,
                            onToggleWrap = viewModel::toggleWrap,
                            onReload = viewModel::loadTail,
                            onToggleStdout = viewModel::toggleStdout,
                            onToggleStderr = viewModel::toggleStderr,
                        )
                    }
                }
            }
        }
    }

    confirm?.let { request ->
        DockerConfirmDialog(request = request, onDismiss = { confirm = null })
    }
}

/** A section title with an icon badge, a subtitle, and optional actions beneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockerSectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val badge = RoundedCornerShape(10.dp)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(accent.copy(alpha = DashAlpha.Hex20), badge)
                    .border(1.dp, accent.copy(alpha = DashAlpha.Hex30), badge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actions != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { actions() }
        }
    }
}

/**
 * A capsule action on the dashboard's tonal recipe in any palette tone:
 * the tone at `20` with a `30` border, or at `40` with a solid border when
 * [active] marks a toggled state.
 */
@Composable
private fun ToneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    active: Boolean = false,
    icon: ImageVector? = null,
) {
    val ink = readableInk(tone, MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = CircleShape
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(tone.copy(alpha = if (active) DashAlpha.Hex40 else DashAlpha.Hex20), shape)
            .border(1.dp, if (active) tone else tone.copy(alpha = DashAlpha.Hex30), shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = tone, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
    }
}

/** A toggle chip in a palette tone, struck through when [strike] marks it off. */
@Composable
private fun ToneChip(
    text: String,
    tone: Color,
    active: Boolean,
    onClick: () -> Unit,
    strike: Boolean = false,
) {
    val ink = if (active) readableInk(tone, MaterialTheme.colorScheme.surfaceContainerLow) else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tone.copy(alpha = if (active) DashAlpha.Hex20 else DashAlpha.Hex08),
        border = BorderStroke(1.dp, tone.copy(alpha = if (active) DashAlpha.Hex40 else DashAlpha.Hex20)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** The confirmation modal, toned like the dashboard's danger, warning, and info variants. */
@Composable
private fun DockerConfirmDialog(request: DockerConfirm, onDismiss: () -> Unit) {
    val roles = rememberShellRoles()
    val scheme = MaterialTheme.colorScheme
    val tint = roles.tint(request.tone)
    val glyph = when (request.tone) {
        ShellTone.Negative -> Icons.Default.Error
        ShellTone.Caution -> Icons.Default.Warning
        else -> Icons.Default.Info
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { GlyphOrb(icon = glyph, tint = tint, size = OrbSize.Medium) },
        title = { Text(request.title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(request.message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            ToneButton(
                text = request.confirmLabel,
                onClick = {
                    request.onConfirm()
                    onDismiss()
                },
                tone = tint,
                active = true,
            )
        },
        dismissButton = { ToneButton(text = "Cancel", onClick = onDismiss, tone = scheme.primary) },
        shape = RoundedCornerShape(ShellDimens.heroRadius),
        containerColor = scheme.surfaceContainerHigh,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
    )
}

/** The Daemon section: engine facts, or why Docker is unavailable. */
@Composable
private fun DaemonSection(state: DockerState, onRefresh: () -> Unit) {
    val roles = rememberShellRoles()
    val overview = state.overview
    SectionCard {
        DockerSectionHeader(
            title = "Daemon",
            subtitle = overview?.endpoint ?: "Looking for a Docker socket on the host",
            icon = Icons.Default.Inventory2,
            actions = { ToneButton(text = "Refresh", onClick = onRefresh, icon = Icons.Default.Refresh) },
        )
        state.overviewError?.let { ShellCallout(text = it, tone = ShellTone.Negative) }
        when {
            overview == null && state.overviewError == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            overview == null -> Unit

            !overview.isAvailable -> {
                val title = if (overview.availability == DockerAvailability.NOT_CONFIGURED) {
                    "Docker is not configured on this host"
                } else {
                    "The Docker daemon could not be reached"
                }
                ToneCard(tone = roles.caution, icon = Icons.Default.Warning, title = title, body = overview.message)
            }

            else -> {
                val stopped = overview.stopped
                val tiles = listOf(
                    StatSpec(
                        label = "Engine",
                        value = overview.serverVersion ?: "-",
                        hint = listOfNotNull(overview.operatingSystem, overview.architecture).joinToString(" · "),
                        tone = MaterialTheme.colorScheme.primary,
                    ),
                    StatSpec("Running", overview.running.toString(), "${overview.containers.size} containers", roles.positive),
                    StatSpec(
                        label = "Stopped",
                        value = stopped.toString(),
                        hint = if (stopped > 0) "not running" else "all up",
                        tone = if (stopped > 0) roles.caution else roles.neutral,
                    ),
                    StatSpec(
                        label = "Images",
                        value = overview.images.toString(),
                        hint = if (overview.composeAvailable) "compose available" else "compose unavailable",
                        tone = MaterialTheme.colorScheme.secondary,
                    ),
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth >= 560.dp) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { spec -> DockerStatTile(spec, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One Daemon stat tile's content. */
@Immutable
private data class StatSpec(val label: String, val value: String, val hint: String, val tone: Color)

/** A stat tile with a label, a value in the tone, and a hint line. */
@Composable
private fun DockerStatTile(spec: StatSpec, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.medium
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Column(
        modifier = modifier
            .washed(base = base, wash = toneWash(spec.tone, 0.8f), shape = shape)
            .border(1.dp, spec.tone.copy(alpha = DashAlpha.Hex30), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = spec.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = spec.value,
            style = MaterialTheme.typography.titleLarge,
            color = readableInk(spec.tone, base),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = spec.hint.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A single hue card with an orb, a title, and a body, for warnings. */
@Composable
private fun ToneCard(tone: Color, icon: ImageVector, title: String, body: String?) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .washed(base = MaterialTheme.colorScheme.surfaceContainerLow, wash = toneWash(tone), shape = shape)
            .border(1.dp, tone.copy(alpha = DashAlpha.Hex30), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphOrb(icon = icon, tint = tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = readableInk(tone, MaterialTheme.colorScheme.surfaceContainerLow))
            if (!body.isNullOrBlank()) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** The action notice strip: green or red, dismissible, gone after six seconds. */
@Composable
private fun NoticeBanner(notice: DockerNotice, onDismiss: () -> Unit) {
    val roles = rememberShellRoles()
    val tone = if (notice.ok) roles.positive else roles.negative
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .washed(base = MaterialTheme.colorScheme.surfaceContainerLow, wash = toneWash(tone), shape = shape)
            .border(1.dp, tone.copy(alpha = DashAlpha.Hex30), shape)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (notice.ok) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = notice.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The Bots section: every registered instance, its build, and its update state. */
@Composable
private fun BotsSection(
    state: DockerState,
    onCheck: () -> Unit,
    onUpdateAll: () -> Unit,
    onLogs: (DockerFleetEntry) -> Unit,
    onUpdate: (DockerFleetEntry) -> Unit,
) {
    val roles = rememberShellRoles()
    val updates = state.fleetUpdatesAvailable
    val subtitle = if (updates > 0) {
        "$updates of ${state.fleet.size} instances have a newer build published"
    } else {
        "Every registered instance, the commit it runs, and whether a newer image is published"
    }
    SectionCard {
        DockerSectionHeader(
            title = "Bots",
            subtitle = subtitle,
            icon = Icons.Default.SmartToy,
            actions = {
                ToneButton(
                    text = "Check for updates",
                    onClick = onCheck,
                    enabled = !state.fleetChecking,
                    loading = state.fleetChecking,
                    icon = Icons.Default.Refresh,
                )
                ToneButton(
                    text = "Update all",
                    onClick = onUpdateAll,
                    tone = if (updates > 0) roles.caution else MaterialTheme.colorScheme.primary,
                    enabled = state.fleetCanUpdateAll && !state.updatingAll && !state.jobRunning,
                    loading = state.updatingAll,
                    active = updates > 0,
                    icon = Icons.Default.SystemUpdateAlt,
                )
            },
        )
        val selectedEntry = state.fleet.firstOrNull { it.instance.botId == state.selectedBotId }
        if (!state.fleetCanUpdateAll && !state.fleetLoading && selectedEntry?.loading != true) {
            Text(
                text = "The selected instance is not part of a compose fleet",
                style = MaterialTheme.typography.bodySmall,
                color = roles.textTertiary,
            )
        }
        when {
            state.fleetError != null -> ShellCallout(text = state.fleetError, tone = ShellTone.Negative)
            state.fleetLoading && state.fleet.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.fleet.isEmpty() -> EmptyState("No instances are registered", icon = Icons.Default.SmartToy)
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fleet.forEach { entry ->
                    FleetRow(
                        entry = entry,
                        selected = entry.instance.botId == state.selectedBotId,
                        updating = entry.instance.id in state.updatingInstances,
                        jobRunning = state.jobRunning,
                        roles = roles,
                        onLogs = { onLogs(entry) },
                        onUpdate = { onUpdate(entry) },
                    )
                }
            }
        }
    }
}

/** One fleet row: avatar, name, port, update pill, build details, and actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FleetRow(
    entry: DockerFleetEntry,
    selected: Boolean,
    updating: Boolean,
    jobRunning: Boolean,
    roles: ShellRoles,
    onLogs: () -> Unit,
    onUpdate: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val info = entry.info
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(primary.copy(alpha = if (selected) DashAlpha.Hex15 else DashAlpha.Hex05), shape)
            .border(1.dp, if (selected) primary.copy(alpha = DashAlpha.Hex40) else roles.hairline, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                url = entry.instance.avatarUrl,
                contentDescription = null,
                size = 36,
                fallbackText = entry.instance.botName,
            )
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.instance.botName.ifBlank { "Instance ${entry.instance.id}" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = ":${entry.instance.port}",
                    style = TerminalText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) StatePill(text = "selected", tone = primary)
                FleetStatusPill(entry = entry, roles = roles)
            }
        }
        if (info != null) {
            val details = buildList {
                add(info.gitSha?.take(7) ?: "v${info.botVersion}")
                info.buildDate?.let { add("built ${dockerAgo(it)}") }
                add("up ${dockerAgo(info.startedAt)}")
                val container = info.container
                add(if (container != null) "${container.name} ${container.status}".trim() else "not in a container")
            }
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val newest = info.published?.gitSha
            if (info.updateAvailable == true && newest != null) {
                Text(
                    text = "newest $newest pushed ${dockerAgo(info.published.publishedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = readableInk(roles.caution, MaterialTheme.colorScheme.surfaceContainerLow),
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToneButton(
                text = "Logs",
                onClick = onLogs,
                enabled = info?.container != null,
                icon = Icons.AutoMirrored.Filled.Article,
            )
            ToneButton(
                text = "Update",
                onClick = onUpdate,
                tone = if (info?.updateAvailable == true) roles.caution else primary,
                enabled = info?.canUpdate == true && !updating && !jobRunning,
                loading = updating,
                active = info?.updateAvailable == true,
                icon = Icons.Default.Upgrade,
            )
        }
        if (info != null && !info.canUpdate) {
            Text(
                text = info.updateBlockedReason ?: "This instance cannot be updated from the dashboard",
                style = MaterialTheme.typography.bodySmall,
                color = roles.textTertiary,
            )
        } else if (info == null && !entry.loading && entry.error == null) {
            Text(
                text = "Waiting for the bot to answer",
                style = MaterialTheme.typography.bodySmall,
                color = roles.textTertiary,
            )
        }
    }
}

/** The fleet row's pill: asking, the failure, or the update state. */
@Composable
private fun FleetStatusPill(entry: DockerFleetEntry, roles: ShellRoles) {
    val info = entry.info
    when {
        entry.loading -> StatePill(text = "asking...", tone = roles.neutral, icon = Icons.Default.HourglassEmpty)
        entry.error != null -> StatePill(text = entry.error.ifBlank { "Did not answer" }, tone = roles.negative, icon = Icons.Default.Error)
        info == null -> StatePill(text = "unknown", tone = roles.neutral, icon = Icons.AutoMirrored.Filled.HelpOutline)
        info.updateAvailable == true -> StatePill(
            text = "update to ${info.published?.gitSha ?: "newer"}",
            tone = roles.caution,
            icon = Icons.Default.ArrowUpward,
        )

        info.updateAvailable == false -> StatePill(text = "up to date", tone = roles.positive, icon = Icons.Default.CheckCircle)
        info.published?.error != null -> StatePill(text = "registry unreachable", tone = roles.neutral, icon = Icons.AutoMirrored.Filled.HelpOutline)
        else -> StatePill(text = "unknown", tone = roles.neutral, icon = Icons.AutoMirrored.Filled.HelpOutline)
    }
}

/** A compose project card (or the standalone bucket) with its containers when expanded. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupCard(
    group: DockerGroup,
    state: DockerState,
    composeAvailable: Boolean,
    onToggle: () -> Unit,
    onCompose: (DockerComposeProject, DockerComposeOperation) -> Unit,
    onSelect: (DockerContainerInfo) -> Unit,
    onAction: (DockerContainerInfo, DockerContainerAction) -> Unit,
) {
    val roles = rememberShellRoles()
    val expanded = group.key in state.expanded
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")
    val running = group.running
    val total = group.total
    val countTone = when {
        total > 0 && running == total -> roles.positive
        running == 0 -> roles.negative
        else -> roles.caution
    }
    val project = group.project
    GuildCard(modifier = Modifier.fillMaxWidth(), wash = guildWash(), border = guildBorder()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle, role = Role.Button)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.rotate(rotation),
                )
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    StatePill(text = "$running/$total up", tone = countTone)
                    if (group.containers.any { it.isSelf }) {
                        StatePill(text = "this bot", tone = MaterialTheme.colorScheme.primary, icon = Icons.Default.Star)
                    }
                }
            }
            project?.workingDir?.let {
                Text(
                    text = it,
                    style = TerminalText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (project != null && composeAvailable) {
            Box(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                if (project.operable) {
                    val enabled = state.startingJob == null && !state.jobRunning
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToneButton(
                            text = DockerComposeOperation.Pull.label,
                            onClick = { onCompose(project, DockerComposeOperation.Pull) },
                            enabled = enabled,
                            loading = state.startingJob == project.name,
                            icon = Icons.Default.CloudDownload,
                        )
                        ToneButton(
                            text = DockerComposeOperation.Up.label,
                            onClick = { onCompose(project, DockerComposeOperation.Up) },
                            enabled = enabled,
                            icon = Icons.Default.PlayArrow,
                        )
                        ToneButton(
                            text = DockerComposeOperation.Update.label,
                            onClick = { onCompose(project, DockerComposeOperation.Update) },
                            tone = roles.caution,
                            enabled = enabled,
                            icon = Icons.Default.Upgrade,
                        )
                    }
                } else {
                    Text(
                        text = "compose files not visible",
                        style = MaterialTheme.typography.bodySmall,
                        color = roles.textTertiary,
                    )
                }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                group.containers.forEach { container ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(roles.hairline),
                    )
                    ContainerRow(
                        container = container,
                        selected = container.id == state.selectedId,
                        stats = state.stats[container.id],
                        busy = state.busyContainers[container.id],
                        roles = roles,
                        onSelect = { onSelect(container) },
                        onAction = { onAction(container, it) },
                    )
                }
            }
        }
    }
}

/** One container: state, identity, resource sample, and actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContainerRow(
    container: DockerContainerInfo,
    selected: Boolean,
    stats: DockerContainerStats?,
    busy: DockerContainerAction?,
    roles: ShellRoles,
    onSelect: () -> Unit,
    onAction: (DockerContainerAction) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val stateTone = when (container.state) {
        "running" -> if (container.health == "unhealthy") roles.caution else roles.positive
        "restarting", "created", "paused" -> roles.caution
        else -> roles.negative
    }
    val stateLabel = if (container.isRunning && container.health != null) container.health else container.state
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) primary.copy(alpha = DashAlpha.Hex15) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(container.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            StatePill(text = stateLabel.ifBlank { "unknown" }, tone = stateTone)
            if (container.isSelf) StatePill(text = "this bot", tone = primary, icon = Icons.Default.Star)
        }
        Text(
            text = "${container.shortId} · ${container.image}",
            style = TerminalText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(container.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (container.ports.isNotEmpty()) {
            Text(container.ports.joinToString(", "), style = TerminalText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sample = when {
            !container.isRunning -> "created ${dockerAgo(container.createdAt)}"
            stats == null -> "sampling..."
            else -> {
                val memory = if (stats.memoryLimitBytes > 0) {
                    "${dockerBytes(stats.memoryBytes)} / ${dockerBytes(stats.memoryLimitBytes)}"
                } else {
                    dockerBytes(stats.memoryBytes)
                }
                "%.1f%% cpu".format(stats.cpuPercent) + " · $memory · ${stats.pids} pids · rx ${dockerBytes(stats.networkRxBytes)}"
            }
        }
        Text(sample, style = TerminalText, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToneButton(
                text = "Logs",
                onClick = onSelect,
                active = selected,
                icon = Icons.AutoMirrored.Filled.Article,
            )
            if (container.isRunning) {
                ToneButton(
                    text = "Restart",
                    onClick = { onAction(DockerContainerAction.Restart) },
                    enabled = busy == null,
                    loading = busy == DockerContainerAction.Restart,
                    icon = Icons.Default.RestartAlt,
                )
                ToneButton(
                    text = "Stop",
                    onClick = { onAction(DockerContainerAction.Stop) },
                    tone = roles.negative,
                    enabled = busy == null,
                    loading = busy == DockerContainerAction.Stop,
                    icon = Icons.Default.Stop,
                )
            } else {
                ToneButton(
                    text = "Start",
                    onClick = { onAction(DockerContainerAction.Start) },
                    tone = roles.positive,
                    enabled = busy == null,
                    loading = busy == DockerContainerAction.Start,
                    icon = Icons.Default.PlayArrow,
                )
            }
        }
    }
}

/** The Compose jobs section: recent job chips and the active job's terminal. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobsSection(state: DockerState, onSelectJob: (DockerJob) -> Unit) {
    val roles = rememberShellRoles()
    val colors = rememberAnsiColors()
    SectionCard {
        DockerSectionHeader(
            title = "Compose jobs",
            subtitle = "Pull, up and build runs started from here, with their output",
            icon = Icons.Default.AssignmentTurnedIn,
        )
        if (state.jobs.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.jobs.take(6).forEach { job ->
                    ToneChip(
                        text = "${job.project} ${job.operationLabel}",
                        tone = jobTone(job, roles),
                        active = job.id == state.activeJob?.id,
                        onClick = { onSelectJob(job) },
                    )
                }
            }
        }
        state.activeJob?.let { job -> JobPanel(job = job, roles = roles, colors = colors) }
    }
}

/** The palette tone for a job's status. */
private fun jobTone(job: DockerJob, roles: ShellRoles): Color = when (job.status) {
    DockerJobStatus.SUCCEEDED -> roles.positive
    DockerJobStatus.FAILED -> roles.negative
    else -> roles.caution
}

/** The terminal panel for one compose job, following its output to the bottom. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JobPanel(job: DockerJob, roles: ShellRoles, colors: AnsiColors) {
    val listState = rememberLazyListState()
    LaunchedEffect(job.id, job.output.size) {
        if (job.output.isNotEmpty()) listState.scrollToItem(job.output.lastIndex)
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(1.dp, roles.hairline, shape),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(job.project, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(job.operationLabel, style = TerminalText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatePill(text = job.statusLabel, tone = jobTone(job, roles))
            job.exitCode?.let { Text("exit $it", style = TerminalText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(
                text = "started ${dockerAgo(job.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(roles.hairline),
        )
        if (job.output.isEmpty()) {
            Text(
                text = "Waiting for output...",
                style = TerminalText,
                color = roles.textTertiary,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = JobPanelMaxHeight),
                contentPadding = PaddingValues(12.dp),
            ) {
                itemsIndexed(job.output) { _, line ->
                    AnsiText(raw = line, colors = colors, wrap = true)
                }
            }
        }
    }
}

/** The container log viewer: controls, filters, status, and the live panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogViewer(
    container: DockerContainerInfo,
    state: DockerState,
    onLineCount: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleWrap: () -> Unit,
    onReload: () -> Unit,
    onToggleStdout: () -> Unit,
    onToggleStderr: () -> Unit,
) {
    val roles = rememberShellRoles()
    val colors = rememberAnsiColors()
    val clipboard = rememberTextClipboard()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500.milliseconds)
            copied = false
        }
    }
    val lines = state.lines
    val visible = remember(lines, state.search, state.showStdout, state.showStderr) {
        val needle = state.search.trim().lowercase()
        lines.filter { line ->
            val streamShown = if (line.isError) state.showStderr else state.showStdout
            streamShown && (needle.isEmpty() || line.plain.lowercase().contains(needle))
        }
    }
    val errorCount = remember(lines) { lines.count { it.isError } }
    val hidden = lines.size - visible.size

    SectionCard {
        DockerSectionHeader(
            title = container.name,
            subtitle = "${container.image} · ${container.shortId}",
            icon = Icons.Default.Terminal,
        )
        SectionTabs(
            tabs = DockerLineOptions.map { (value, label) -> SectionTab(id = value.toString(), title = label) },
            selectedId = state.lineCount.toString(),
            onSelect = { id -> id.toIntOrNull()?.let(onLineCount) },
            level = TabLevel.Secondary,
        )
        SearchField(value = state.search, onValueChange = onSearch, placeholder = "Filter lines")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToneButton(
                text = if (state.follow) "Following" else "Follow",
                onClick = onToggleFollow,
                tone = if (state.follow) roles.positive else MaterialTheme.colorScheme.primary,
                active = state.follow,
                icon = if (state.follow) Icons.Default.Pause else Icons.Default.PlayArrow,
            )
            ToneButton(
                text = "Wrap",
                onClick = onToggleWrap,
                active = state.wrap,
                icon = Icons.AutoMirrored.Filled.WrapText,
            )
            ToneButton(
                text = if (copied) "Copied" else "Copy",
                onClick = {
                    clipboard.copy(visible.joinToString("\n") { it.plain })
                    copied = true
                },
                enabled = visible.isNotEmpty(),
                icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            )
            ToneButton(
                text = "Reload",
                onClick = onReload,
                loading = state.logLoading,
                icon = Icons.Default.Refresh,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToneChip(
                text = "stdout ${lines.size - errorCount}",
                tone = roles.positive,
                active = state.showStdout,
                strike = !state.showStdout,
                onClick = onToggleStdout,
            )
            ToneChip(
                text = "stderr $errorCount",
                tone = roles.negative,
                active = state.showStderr,
                strike = !state.showStderr,
                onClick = onToggleStderr,
            )
        }
        LogStatusBar(
            visible = visible.size,
            hidden = hidden,
            follow = state.follow,
            updated = state.lastUpdated?.let { dockerClock(it) },
            roles = roles,
        )
        state.logError?.let { ShellCallout(text = it, tone = ShellTone.Negative) }
        LogPanel(
            key = container.id,
            state = state,
            visible = visible,
            colors = colors,
            roles = roles,
        )
    }
}

/** The line count, the live marker, and the last update time. */
@Composable
private fun LogStatusBar(visible: Int, hidden: Int, follow: Boolean, updated: String?, roles: ShellRoles) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (hidden > 0) "$visible lines, $hidden hidden by filters" else "$visible lines",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (follow) {
            val pulse = rememberInfiniteTransition(label = "live")
            val alpha by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                label = "liveAlpha",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(roles.positive, CircleShape),
                )
                Text("live", style = MaterialTheme.typography.labelSmall, color = readableInk(roles.positive, MaterialTheme.colorScheme.surfaceContainerLow))
            }
        }
        if (updated != null) {
            Text("updated $updated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The scrolling log body. Follows new lines while the reader is at the
 * bottom; otherwise counts them on a floating jump button.
 */
@Composable
private fun LogPanel(
    key: String,
    state: DockerState,
    visible: List<DockerViewLine>,
    colors: AnsiColors,
    roles: ShellRoles,
) {
    val listState = remember(key) { LazyListState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slackPx = with(density) { BottomSlack.toPx() }
    val atBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 && (last.offset + last.size - info.viewportEndOffset) <= slackPx
        }
    }
    var pending by remember(key) { mutableIntStateOf(0) }
    var lastSeen by remember(key) { mutableLongStateOf(-1L) }
    val lastId = state.lines.lastOrNull()?.id ?: -1L

    LaunchedEffect(key, state.tailVersion) {
        lastSeen = state.lines.lastOrNull()?.id ?: -1L
        pending = 0
        if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    LaunchedEffect(key, lastId) {
        if (lastId <= lastSeen) return@LaunchedEffect
        val arrived = state.lines.count { it.id > lastSeen }
        lastSeen = lastId
        if (atBottom) {
            if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
        } else {
            pending += arrived
        }
    }
    LaunchedEffect(atBottom) {
        if (atBottom) pending = 0
    }

    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LogPanelHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, shape)
            .border(1.dp, roles.hairline, shape),
    ) {
        when {
            state.logLoading && state.lines.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.lines.isEmpty() -> PanelMessage("The log is empty", roles)
            visible.isEmpty() -> PanelMessage("No lines match the current filters", roles)
            state.wrap -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
            ) {
                items(visible, key = { it.id }) { line -> LogLine(line = line, colors = colors, roles = roles, wrap = true) }
            }

            else -> NoWrapLog(listState = listState, visible = visible, colors = colors, roles = roles)
        }

        if (!atBottom && visible.isNotEmpty()) {
            ToneButton(
                text = if (pending > 0) "$pending new" else "Bottom",
                onClick = {
                    scope.launch {
                        listState.scrollToItem(visible.lastIndex)
                        pending = 0
                    }
                },
                active = true,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
            )
        }
    }
}

/** Unwrapped lines in one shared horizontal scroll, as wide as the longest visible line. */
@Composable
private fun NoWrapLog(listState: LazyListState, visible: List<DockerViewLine>, colors: AnsiColors, roles: ShellRoles) {
    val measurer = rememberTextMeasurer()
    val charWidth = remember(measurer) { measurer.measure("0000000000", TerminalText).size.width / 10f }
    val longest = remember(visible) { (visible.maxOfOrNull { it.plain.length } ?: 0).coerceAtMost(4_000) }
    val density = LocalDensity.current
    val horizontal = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val contentWidth = with(density) { (longest * charWidth).toDp() } + 32.dp
        val width = max(maxWidth, contentWidth)
        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(horizontal),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(10.dp),
            ) {
                items(visible, key = { it.id }) { line -> LogLine(line = line, colors = colors, roles = roles, wrap = false) }
            }
        }
    }
}

/** A centered note inside the log panel. */
@Composable
private fun PanelMessage(text: String, roles: ShellRoles) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = roles.textTertiary)
    }
}

/** One log line; stderr lines carry a red rule on their leading edge. */
@Composable
private fun LogLine(line: DockerViewLine, colors: AnsiColors, roles: ShellRoles, wrap: Boolean) {
    val rule = roles.negative
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                if (line.isError) drawRect(color = rule, size = Size(2.dp.toPx(), size.height))
            }
            .padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
    ) {
        AnsiText(raw = line.raw, colors = colors, wrap = wrap)
    }
}

/** The 16 terminal color slots mapped onto palette roles. */
@Immutable
private class AnsiColors(val slots: List<Color>, val text: Color)

/**
 * Maps the standard terminal colors onto the palette: red, green, and
 * yellow are the semantic tones, blue, magenta, and cyan are the primary,
 * secondary, and accent, and the greys are the muted and body inks. Bright
 * variants lean toward the body ink.
 */
@Composable
private fun rememberAnsiColors(): AnsiColors {
    val roles = rememberShellRoles()
    val scheme = MaterialTheme.colorScheme
    return remember(roles, scheme.primary, scheme.secondary, scheme.tertiary, scheme.onSurface, scheme.onSurfaceVariant) {
        val base = listOf(
            scheme.onSurfaceVariant,
            roles.negative,
            roles.positive,
            roles.caution,
            scheme.primary,
            scheme.secondary,
            scheme.tertiary,
            scheme.onSurface,
        )
        val bright = base.mapIndexed { index, color ->
            when (index) {
                0 -> scheme.onSurfaceVariant
                7 -> scheme.onSurface
                else -> lerp(color, scheme.onSurface, 0.3f)
            }
        }
        AnsiColors(base + bright, scheme.onSurface)
    }
}

/** A line of terminal text with its ANSI styling rendered in palette colors. */
@Composable
private fun AnsiText(raw: String, colors: AnsiColors, wrap: Boolean) {
    val annotated = remember(raw, colors) { ansiAnnotated(raw, colors) }
    Text(
        text = annotated,
        style = TerminalText,
        color = colors.text,
        softWrap = wrap,
        maxLines = if (wrap) Int.MAX_VALUE else 1,
    )
}

/** Builds the styled string for [raw] from its ANSI runs. */
private fun ansiAnnotated(raw: String, colors: AnsiColors): AnnotatedString {
    val spans = parseAnsi(raw)
    if (spans.size == 1 && spans[0].color == null && spans[0].background == null && !spans[0].bold) {
        return AnnotatedString(spans[0].text)
    }
    return buildAnnotatedString {
        spans.forEach { span ->
            val ink = span.color?.let { colors.slots.getOrNull(it) }
            val style = SpanStyle(
                color = when {
                    ink == null && span.dim -> colors.text.copy(alpha = 0.6f)
                    ink == null -> Color.Unspecified
                    span.dim -> ink.copy(alpha = 0.6f)
                    else -> ink
                },
                background = span.background?.let { colors.slots.getOrNull(it)?.copy(alpha = DashAlpha.Hex30) } ?: Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            )
            withStyle(style) { append(span.text) }
        }
    }
}
