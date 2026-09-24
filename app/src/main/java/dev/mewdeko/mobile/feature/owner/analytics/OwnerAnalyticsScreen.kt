package dev.mewdeko.mobile.feature.owner.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.rememberTextClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** How often the refresh tick fires while the screen is in the foreground. */
private const val RefreshIntervalMillis = 30_000L

/** The longest custom window the filter bar accepts, as on the dashboard. */
private const val MaxCustomMillis = 31L * 86_400_000L

/** The selector id standing for "every bot" or "every shard". */
private const val AllOption = "__all__"

/**
 * Fleet telemetry, commands, events, errors, growth and alerts, mirroring
 * the dashboard's `/owner/analytics`. Fleet level: acts on the selected bot
 * instance.
 *
 * A global filter bar sits above the twelve tabs and only the active tab is
 * composed. Every widget reloads when a filter changes and on the refresh
 * tick, which fires every 30 seconds while the screen is resumed, on the
 * refresh button, and on pull to refresh.
 */
@Composable
fun OwnerAnalyticsScreen(
    onBack: () -> Unit,
    viewModel: OwnerAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(RefreshIntervalMillis)
                viewModel.refreshNow()
            }
        }
    }

    val env = AnalyticsEnv(
        vm = viewModel,
        filters = state.filters,
        tick = state.tick,
        registry = state.registry,
    )

    CompositionLocalProvider(LocalAnalytics provides env) {
        FeatureScaffold(
            title = "Analytics",
            subtitle = viewModel.botName ?: "Fleet telemetry, commands, events, errors, growth and alerts",
            onBack = onBack,
            loadState = loadState,
            status = status,
            onStatusShown = viewModel::clearStatus,
            onRefresh = { viewModel.load(refreshing = true) },
            onRetry = { viewModel.load() },
        ) {
            FilterBar(
                state = state,
                onUpdate = viewModel::updateFilters,
                onRefresh = viewModel::refreshNow,
                onShareLink = viewModel::shareLink,
            )
            SectionTabs(
                tabs = OwnerAnalyticsTabs,
                selectedId = state.section.id,
                onSelect = { id -> viewModel.setSection(OwnerAnalyticsSection.fromId(id)) },
            )
            OwnerAnalyticsSectionBody(state)
        }
    }
}

/**
 * The dashboard's filter bar: range chips with a custom UTC window, the
 * compare switch, bot and shard pickers, the guild id field, and the last
 * update time with refresh and copy link.
 */
@Composable
private fun FilterBar(
    state: OwnerAnalyticsState,
    onUpdate: ((AnalyticsFilters) -> AnalyticsFilters) -> Unit,
    onRefresh: () -> Unit,
    onShareLink: suspend () -> String?,
) {
    val filters = state.filters
    val colors = rememberAnalyticsPalette()
    val clipboard = rememberTextClipboard()
    val scope = rememberCoroutineScope()

    var customOpen by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf("") }
    var customTo by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf("") }
    var guildInput by remember(filters.guild) { mutableStateOf(filters.guild) }
    var guildInvalid by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(guildInvalid) {
        if (guildInvalid) {
            delay(1200)
            guildInvalid = false
        }
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    fun openCustom() {
        val now = Instant.now()
        val start = utcParse(filters.from) ?: now.minusMillis((filters.rangeSeconds() * 1000).toLong())
        val end = utcParse(filters.to) ?: now
        customFrom = rangeInputText(start)
        customTo = rangeInputText(end)
        customError = ""
        customOpen = true
    }

    fun applyCustom() {
        val start = parseRangeInput(customFrom)
        val end = parseRangeInput(customTo)
        customError = when {
            start == null || end == null -> "Both dates required"
            !end.isAfter(start) -> "End before start"
            end.toEpochMilli() - start.toEpochMilli() > MaxCustomMillis -> "Max 31 days"
            else -> ""
        }
        if (customError.isEmpty() && start != null && end != null) {
            customOpen = false
            onUpdate { it.copy(from = start.toString(), to = end.toString()) }
        }
    }

    fun applyGuild() {
        val id = guildInput.trim()
        if (id.isNotEmpty() && !isGuildId(id)) {
            guildInvalid = true
            return
        }
        onUpdate { it.copy(guild = id) }
    }

    SectionCard(contentPadding = 12) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnalyticsRanges.forEach { (id, _) ->
                FilterChipPill(
                    text = id,
                    selected = !filters.isCustom && filters.range == id,
                    onClick = {
                        customOpen = false
                        onUpdate { it.copy(range = id, from = "", to = "") }
                    },
                )
            }
            FilterChipPill(
                text = null,
                selected = filters.isCustom,
                onClick = { if (customOpen) customOpen = false else openCustom() },
                icon = { tint ->
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "Custom range",
                        tint = tint,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        if (filters.isCustom && !customOpen) {
            Text(
                text = "${stamp(filters.from)} → ${stamp(filters.to)} UTC",
                style = MonospaceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (customOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MewdekoTextField(
                    value = customFrom,
                    onValueChange = { customFrom = it },
                    label = "From (UTC)",
                    placeholder = "YYYY-MM-DD HH:MM",
                )
                MewdekoTextField(
                    value = customTo,
                    onValueChange = { customTo = it },
                    label = "To (UTC)",
                    placeholder = "YYYY-MM-DD HH:MM",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = ::applyCustom) { Text("Apply") }
                    TextButton(onClick = { customOpen = false }) { Text("Cancel") }
                    if (customError.isNotEmpty()) {
                        Text(customError, style = MaterialTheme.typography.labelMedium, color = colors.crit)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = filters.compare,
                onCheckedChange = { checked -> onUpdate { it.copy(compare = checked) } },
            )
            Text(
                text = "compare",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${clockOf(state.updatedAt)} UTC",
                style = MonospaceStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(
                onClick = {
                    scope.launch {
                        val link = onShareLink() ?: return@launch
                        clipboard.copy(link)
                        copied = true
                    }
                },
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.Link,
                    contentDescription = "Copy link",
                    tint = if (copied) colors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val botOptions = buildList {
                add(SelectorOption(AllOption, "all bots"))
                state.bots.forEach { add(SelectorOption(it.id, it.name, subtitle = it.id)) }
                if (filters.bot.isNotEmpty() && state.bots.none { it.id == filters.bot }) {
                    add(SelectorOption(filters.bot, filters.bot))
                }
            }
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.SmartToy),
                options = botOptions,
                placeholder = "all bots",
                selectedId = filters.bot.ifEmpty { AllOption },
                onSelect = { id -> onUpdate { it.copy(bot = if (id == null || id == AllOption) "" else id) } },
                label = "Bot",
                modifier = Modifier.weight(1f),
            )
            val shardOptions = buildList {
                add(SelectorOption(AllOption, "all shards"))
                state.shards.forEach { add(SelectorOption(it, "shard $it")) }
                if (filters.shard.isNotEmpty() && filters.shard !in state.shards) {
                    add(SelectorOption(filters.shard, "shard ${filters.shard}"))
                }
            }
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Dns),
                options = shardOptions,
                placeholder = "all shards",
                selectedId = filters.shard.ifEmpty { AllOption },
                onSelect = { id -> onUpdate { it.copy(shard = if (id == null || id == AllOption) "" else id) } },
                label = "Shard",
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = guildInput,
            onValueChange = { guildInput = it.filter(Char::isDigit) },
            label = { Text("Guild id") },
            placeholder = { Text("guild id") },
            singleLine = true,
            isError = guildInvalid,
            textStyle = MonospaceStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { applyGuild() }),
            trailingIcon = {
                if (filters.guild.isNotEmpty() || guildInput.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            guildInput = ""
                            onUpdate { it.copy(guild = "") }
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear guild")
                    }
                }
            },
            supportingText = if (guildInvalid) {
                { Text("A guild id is 15 to 20 digits") }
            } else {
                null
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A range chip in the dashboard's chip style: the `30` tint when selected, the `08` tint otherwise. */
@Composable
private fun FilterChipPill(
    text: String?,
    selected: Boolean,
    onClick: () -> Unit,
    icon: (@Composable (androidx.compose.ui.graphics.Color) -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = primary.copy(alpha = if (selected) DashAlpha.Hex30 else DashAlpha.Hex08),
        border = BorderStroke(1.dp, primary.copy(alpha = if (selected) DashAlpha.Hex40 else DashAlpha.Hex20)),
        modifier = Modifier.heightIn(min = 36.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke(ink)
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = ink,
                )
            }
        }
    }
}
