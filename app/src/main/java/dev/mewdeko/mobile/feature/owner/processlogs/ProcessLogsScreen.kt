package dev.mewdeko.mobile.feature.owner.processlogs

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.LuminousButton
import dev.mewdeko.mobile.core.ui.LuminousButtonVariant
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.ShellRoles
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.rememberShellRoles
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Read and follow the pm2 logs on the bot's host, mirroring the dashboard's
 * `/owner/process-logs`. Fleet level: acts on the selected bot instance.
 *
 * The page lists every pm2 process, then shows the selected one's stdout or
 * stderr in a monospaced terminal panel that follows new lines, with a text
 * filter, level chips, wrapping, copy, and saving the visible lines to a file
 * (the dashboard's download route only accepts a browser session).
 */
@Composable
fun ProcessLogsScreen(
    onBack: () -> Unit,
    viewModel: ProcessLogsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    LifecycleStartEffect(viewModel) {
        viewModel.setActive(true)
        onStopOrDispose { viewModel.setActive(false) }
    }

    FeatureScaffold(
        title = "Process Logs",
        subtitle = viewModel.botName,
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        Text(
            text = "Read and follow the pm2 logs of every process on the bot's host",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessesSection(
            state = state,
            onRefresh = viewModel::refreshProcesses,
            onSelect = viewModel::selectProcess,
        )
        state.selected?.let { process ->
            LogSection(process = process, state = state, viewModel = viewModel)
        }
    }
}

/** A section title with the dashboard's subtitle under it and an optional trailing action. */
@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    monospaceSubtitle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionCardHeader(title = title, icon = icon, trailing = trailing)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospaceSubtitle) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The process list: loading, error, empty, the log directory warning, and the card grid. */
@Composable
private fun ProcessesSection(
    state: ProcessLogsState,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val roles = rememberShellRoles()
    SectionCard {
        SectionTitle(
            title = "Processes",
            subtitle = "What pm2 is running on the selected instance's host",
            icon = Icons.Default.Dns,
            trailing = {
                LuminousButton(
                    text = "Refresh",
                    onClick = onRefresh,
                    variant = LuminousButtonVariant.Glass,
                    compact = true,
                    icon = Icons.Default.Refresh,
                )
            },
        )

        val list = state.processList
        when {
            state.processesLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.processesError != null -> Banner(
                text = state.processesError,
                tone = roles.negative,
                icon = Icons.Default.Error,
            )

            list == null || list.processes.isEmpty() -> EmptyState(
                message = list?.message ?: "pm2 has no processes registered",
                icon = Icons.Default.Dns,
            )

            else -> {
                if (list.source == Pm2ListSource.LOG_DIRECTORY && !list.message.isNullOrBlank()) {
                    Banner(text = list.message, tone = roles.caution, icon = Icons.Default.Warning)
                }
                ProcessGrid(
                    processes = list.processes,
                    selectedPmId = state.selectedPmId,
                    now = state.processesAt,
                    onSelect = onSelect,
                )
            }
        }
    }
}

/** A tinted notice row: [tone] at the `15` tint with the glyph and text in the tone. */
@Composable
private fun Banner(text: String, tone: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = DashAlpha.Hex15), shape)
            .border(1.dp, tone.copy(alpha = DashAlpha.Hex30), shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = dev.mewdeko.mobile.core.ui.readableInk(tone),
            modifier = Modifier.weight(1f),
        )
    }
}

/** One card per process, one column on phones, two from 600dp and three from 840dp. */
@Composable
private fun ProcessGrid(
    processes: List<Pm2ProcessInfo>,
    selectedPmId: Int?,
    now: Long,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 840.dp -> 3
            maxWidth >= 600.dp -> 2
            else -> 1
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            processes.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { process ->
                        ProcessCard(
                            process = process,
                            selected = process.pmId == selectedPmId,
                            now = now,
                            onClick = { onSelect(process.pmId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The pill tone for a pm2 status, matching the dashboard's ok, warn, crit and muted. */
private fun statusTone(status: String, roles: ShellRoles): Color = when (status) {
    "online" -> roles.positive
    "launching", "stopping", "waiting restart" -> roles.caution
    "errored", "stopped" -> roles.negative
    else -> roles.neutral
}

/** A tappable process card: name, badges, and the id, uptime, usage and log size facts. */
@Composable
private fun ProcessCard(
    process: Pm2ProcessInfo,
    selected: Boolean,
    now: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val roles = rememberShellRoles()
    val muted = scheme.onSurfaceVariant
    val shape = RoundedCornerShape(16.dp)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = scheme.primary.copy(alpha = if (selected) DashAlpha.Hex15 else DashAlpha.Hex08),
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.primary.copy(alpha = DashAlpha.Hex20)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = process.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (process.isSelf) {
                        StatePill(text = "this bot", tone = roles.positive, icon = Icons.Default.Star)
                    }
                }
                StatePill(text = process.status, tone = statusTone(process.status, roles))
            }

            Fact(
                label = "pm2 id",
                value = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                        append(if (process.pmId >= 0) process.pmId.toString() else "-")
                    }
                    process.pid?.let { pid ->
                        withStyle(SpanStyle(color = muted)) { append(" pid $pid") }
                    }
                },
            )
            Fact(
                label = "Uptime",
                value = buildAnnotatedString {
                    append(process.uptime(now))
                    if (process.restarts > 0) {
                        withStyle(SpanStyle(color = muted)) { append(" (${formatCount(process.restarts)} restarts)") }
                    }
                },
            )
            Fact(
                label = "Usage",
                value = buildAnnotatedString {
                    append(process.cpu?.let { String.format(java.util.Locale.US, "%.1f%% cpu", it) } ?: "-")
                    process.memoryBytes?.let { bytes ->
                        withStyle(SpanStyle(color = muted)) { append(" / ") }
                        append(formatBytes(bytes))
                    }
                },
            )
            Fact(
                label = "Logs",
                value = buildAnnotatedString {
                    append("out ${formatBytes(process.outLogBytes)}")
                    withStyle(SpanStyle(color = muted)) { append(" / ") }
                    append("err ${formatBytes(process.errorLogBytes)}")
                },
            )
        }
    }
}

/** One label and value line of a process card. */
@Composable
private fun Fact(label: String, value: AnnotatedString) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The palette color a level is drawn in, from the roles of the theme in scope. */
private fun levelTone(level: LogLevel, roles: ShellRoles, tertiary: Color): Color = when (level) {
    LogLevel.VRB -> roles.neutral
    LogLevel.DBG -> roles.brand
    LogLevel.INF -> roles.positive
    LogLevel.WRN -> roles.caution
    LogLevel.ERR -> roles.negative
    LogLevel.FTL -> tertiary
}

/** The selected process's log: pickers, toolbar, level chips, and the terminal panel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogSection(
    process: Pm2ProcessInfo,
    state: ProcessLogsState,
    viewModel: ProcessLogsViewModel,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val roles = rememberShellRoles()
    val tertiary = MaterialTheme.colorScheme.tertiary

    val visible = remember(state.lines, state.search, state.hiddenLevels) {
        val needle = state.search.trim()
        state.lines.filter { line ->
            val hiddenByLevel = line.effectiveLevel != null && line.effectiveLevel in state.hiddenLevels
            !hiddenByLevel && (needle.isEmpty() || line.plain.contains(needle, ignoreCase = true))
        }
    }
    val counts = remember(state.lines) {
        val result = IntArray(LogLevel.entries.size)
        state.lines.forEach { line -> line.level?.let { result[it.ordinal]++ } }
        result
    }
    val latestVisible by rememberUpdatedState(visible)
    var copied by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = latestVisible.joinToString("\n") { it.plain }
        val resolver = context.contentResolver
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri) ?: error("No output stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                viewModel.showLogError("The log could not be saved")
            }
        }
    }

    SectionCard {
        SectionTitle(
            title = process.name,
            subtitle = state.chunk?.path ?: process.logPath(state.stream) ?: "No log file yet",
            icon = Icons.Default.Terminal,
            monospaceSubtitle = true,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EnumPicker(
                label = "Stream",
                options = Pm2Stream.entries.map { EnumOption(it, it.label) },
                selected = state.stream,
                onSelect = viewModel::setStream,
                showDescription = false,
                modifier = Modifier.weight(1f),
            )
            EnumPicker(
                label = "Lines",
                options = Pm2LineOptions.map { (value, label) -> EnumOption(value, label) },
                selected = state.lineCount,
                onSelect = viewModel::setLineCount,
                showDescription = false,
                modifier = Modifier.weight(1f),
            )
        }

        SearchField(
            value = state.search,
            onValueChange = viewModel::setSearch,
            placeholder = "Filter lines",
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LuminousButton(
                text = if (state.follow) "Following" else "Follow",
                onClick = viewModel::toggleFollow,
                variant = if (state.follow) LuminousButtonVariant.Prominent else LuminousButtonVariant.Glass,
                compact = true,
                icon = if (state.follow) Icons.Default.Pause else Icons.Default.PlayArrow,
                modifier = Modifier.semantics { stateDescription = if (state.follow) "On" else "Off" },
            )
            LuminousButton(
                text = "Wrap",
                onClick = viewModel::toggleWrap,
                variant = if (state.wrap) LuminousButtonVariant.Prominent else LuminousButtonVariant.Glass,
                compact = true,
                icon = Icons.AutoMirrored.Filled.WrapText,
                modifier = Modifier.semantics { stateDescription = if (state.wrap) "On" else "Off" },
            )
            LuminousButton(
                text = if (copied) "Copied" else "Copy",
                onClick = {
                    val text = visible.joinToString("\n") { it.plain }
                    scope.launch {
                        try {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("${process.name} log", text)))
                            copied = true
                            delay(1_500.milliseconds)
                            copied = false
                        } catch (c: CancellationException) {
                            throw c
                        } catch (_: Throwable) {
                            viewModel.showLogError("The system refused clipboard access")
                        }
                    }
                },
                variant = LuminousButtonVariant.Glass,
                compact = true,
                enabled = visible.isNotEmpty(),
                icon = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
            )
            LuminousButton(
                text = "Reload",
                onClick = viewModel::reloadTail,
                variant = LuminousButtonVariant.Glass,
                compact = true,
                icon = Icons.Default.Refresh,
                spinIcon = state.logLoading,
            )
            LuminousButton(
                text = "Save ${formatCount(visible.size)} lines",
                onClick = { saveLauncher.launch("${process.name}-${state.stream.query}.log") },
                variant = LuminousButtonVariant.Glass,
                compact = true,
                enabled = visible.isNotEmpty(),
                icon = Icons.Default.SaveAlt,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogLevel.entries.forEach { level ->
                LevelChip(
                    level = level,
                    count = counts[level.ordinal],
                    hidden = level in state.hiddenLevels,
                    tone = levelTone(level, roles, tertiary),
                    onClick = { viewModel.toggleLevel(level) },
                )
            }
            if (state.hiddenLevels.isNotEmpty()) {
                val primary = MaterialTheme.colorScheme.primary
                Surface(
                    onClick = viewModel::showAllLevels,
                    shape = CircleShape,
                    color = primary.copy(alpha = DashAlpha.Hex15),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Show all", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        LogPanel(state = state, visible = visible, onAtBottom = viewModel::setAtBottom, onJumped = viewModel::jumpedToBottom)
    }
}

/** A level filter chip with its tagged line count; hidden levels fade and strike through. */
@Composable
private fun LevelChip(
    level: LogLevel,
    count: Int,
    hidden: Boolean,
    tone: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = tone.copy(alpha = if (hidden) DashAlpha.Hex10 else DashAlpha.Hex20),
        contentColor = tone,
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = Modifier
            .heightIn(min = 32.dp)
            .alpha(if (hidden) 0.45f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = "${level.fullName}: ${formatCount(count)} lines, tap to ${if (hidden) "show" else "hide"}"
                stateDescription = if (hidden) "Hidden" else "Shown"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val decoration = if (hidden) TextDecoration.LineThrough else TextDecoration.None
            Text(
                text = level.name,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                textDecoration = decoration,
                color = dev.mewdeko.mobile.core.ui.readableInk(tone),
            )
            Text(
                text = formatCount(count),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                textDecoration = decoration,
                color = dev.mewdeko.mobile.core.ui.readableInk(tone).copy(alpha = 0.8f),
            )
        }
    }
}

/** The log's monospaced text style: 12sp with the dashboard's 1.55 line height. */
private val LogTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.6.sp,
)

/**
 * The terminal panel. Always drawn in the palette's dark scheme so the
 * log's own terminal colors stay readable, whatever the app theme.
 */
@Composable
private fun LogPanel(
    state: ProcessLogsState,
    visible: List<LogLine>,
    onAtBottom: (Boolean) -> Unit,
    onJumped: () -> Unit,
) {
    val palette = LocalGuildPalette.current
    val dark = remember(palette) { palette.toColorScheme(dark = true) }
    MaterialTheme(colorScheme = dark, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        val scheme = MaterialTheme.colorScheme
        val roles = rememberShellRoles()
        val shape = RoundedCornerShape(14.dp)
        val density = LocalDensity.current
        val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
        val panelHeight = (windowHeight - 300.dp).coerceIn(320.dp, 900.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceContainerLowest)
                .border(1.dp, scheme.primary.copy(alpha = DashAlpha.Hex30), shape),
        ) {
            StatusBar(state = state, visibleCount = visible.size, roles = roles)

            state.logError?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(roles.negative.copy(alpha = DashAlpha.Hex15))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = roles.negative, modifier = Modifier.size(16.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = roles.negative)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight),
            ) {
                when {
                    state.logLoading && state.lines.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    visible.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (state.lines.isEmpty()) "The log is empty" else "No lines match the current filters",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    else -> LogLines(
                        state = state,
                        visible = visible,
                        roles = roles,
                        onAtBottom = onAtBottom,
                        onJumped = onJumped,
                    )
                }
            }
        }
    }
}

/** The status bar over the log: counts, size on disk, read notices, the live dot, and the update time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusBar(state: ProcessLogsState, visibleCount: Int, roles: ShellRoles) {
    val scheme = MaterialTheme.colorScheme
    val hidden = state.lines.size - visibleCount
    val style = MaterialTheme.typography.labelSmall
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.primary.copy(alpha = DashAlpha.Hex08))
            .drawBehind {
                drawRect(
                    color = scheme.primary.copy(alpha = DashAlpha.Hex20),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildString {
                append("${formatCount(visibleCount)} lines")
                if (hidden > 0) append(", ${formatCount(hidden)} hidden by filters")
            },
            style = style,
            color = scheme.onSurfaceVariant,
        )
        state.chunk?.let { chunk ->
            Text("${formatBytes(chunk.fileSize)} on disk", style = style, color = scheme.onSurfaceVariant)
            if (chunk.truncated) {
                Text("older lines skipped past the 1 MB read limit", style = style, color = roles.caution)
            }
            if (chunk.rotated) {
                Text("file was rotated, showing the new tail", style = style, color = roles.caution)
            }
        }
        if (state.follow) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pulse by rememberInfiniteTransition(label = "livePulse").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "livePulseAlpha",
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(pulse)
                        .background(roles.positive, CircleShape),
                )
                Text("live", style = style, color = scheme.onSurfaceVariant)
            }
        }
        state.lastUpdated?.let { at ->
            val time = remember(at) { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(at)) }
            Text("updated $time", style = style, color = scheme.onSurfaceVariant)
        }
    }
}

/** Builds a line's styled text over the panel's default [ink]. */
private fun LogLine.annotated(ink: Color): AnnotatedString {
    val runs = spans ?: return AnnotatedString(plain)
    return buildAnnotatedString {
        runs.forEach { span ->
            val color = if (span.color.isSpecified) span.color else ink
            withStyle(
                SpanStyle(
                    color = if (span.dim) color.copy(alpha = color.alpha * 0.6f) else color,
                    background = span.background,
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = if (span.underline) TextDecoration.Underline else null,
                ),
            ) {
                append(span.text)
            }
        }
    }
}

/**
 * The lazy list of visible lines. Unwrapped lines scroll sideways together;
 * the list follows new lines while it rests at the bottom and offers a jump
 * button otherwise.
 */
@Composable
private fun LogLines(
    state: ProcessLogsState,
    visible: List<LogLine>,
    roles: ShellRoles,
    onAtBottom: (Boolean) -> Unit,
    onJumped: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = scheme.onSurface.copy(alpha = 0.85f)
    val tertiary = scheme.tertiary
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val latestVisible by rememberUpdatedState(visible)
    val thresholdPx = with(density) { 40.dp.toPx() }

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset + thresholdPx
        }
    }
    LaunchedEffect(atBottom) { onAtBottom(atBottom) }
    LaunchedEffect(state.scrollToken) {
        val lastIndex = latestVisible.lastIndex
        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
    }

    val measurer = rememberTextMeasurer()
    val charWidth = remember(measurer) { measurer.measure("0", LogTextStyle).size.width.coerceAtLeast(1) }
    val longest = remember(visible) { visible.maxOfOrNull { it.plain.length } ?: 0 }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportPx = constraints.maxWidth
        val gutterPx = with(density) { 28.dp.roundToPx() }
        val contentPx = (longest.toLong() * charWidth + gutterPx).coerceAtMost(MAX_CONTENT_PX.toLong()).toInt()
        val widthPx = if (state.wrap) viewportPx else maxOf(viewportPx, contentPx)
        val horizontal = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (state.wrap) Modifier else Modifier.horizontalScroll(horizontal)),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(with(density) { widthPx.toDp() })
                    .fillMaxHeight()
                    .semantics { contentDescription = "Log output" },
            ) {
                items(visible, key = { it.id }) { line ->
                    val border = line.effectiveLevel?.let { levelTone(it, roles, tertiary) } ?: Color.Transparent
                    val text = remember(line.id, ink) { line.annotated(ink) }
                    Text(
                        text = text,
                        style = LogTextStyle,
                        color = ink,
                        softWrap = state.wrap,
                        maxLines = if (state.wrap) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (line.level == null) 0.85f else 1f)
                            .drawBehind {
                                drawRect(color = border, size = Size(3.dp.toPx(), size.height))
                            }
                            .padding(start = 13.dp, end = 12.dp),
                    )
                }
            }
        }

        if (!atBottom && state.lines.isNotEmpty()) {
            LuminousButton(
                text = if (state.pendingNew > 0) "${formatCount(state.pendingNew)} new" else "Bottom",
                onClick = {
                    scope.launch {
                        val lastIndex = latestVisible.lastIndex
                        if (lastIndex >= 0) listState.scrollToItem(lastIndex)
                        onJumped()
                    }
                },
                compact = true,
                icon = Icons.Default.ArrowDownward,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        }
    }
}

/** The widest an unwrapped log may lay out, inside Compose's constraint range for a bounded height. */
private const val MAX_CONTENT_PX = 200_000
