package dev.mewdeko.mobile.feature.currency

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab(CurrencySection.ANALYTICS.id, CurrencySection.ANALYTICS.title, Icons.Default.Insights),
    SectionTab(CurrencySection.CONFIG.id, CurrencySection.CONFIG.title, Icons.Default.Tune),
    SectionTab(CurrencySection.SHOP.id, CurrencySection.SHOP.title, Icons.Default.Storefront),
    SectionTab(CurrencySection.LEADERBOARD.id, CurrencySection.LEADERBOARD.title, Icons.Default.Leaderboard),
)

/** Guild economy: analytics, tuning, the shop, and member balances. */
@Composable
fun CurrencyScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: CurrencyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ShopItem?>(null) }

    val section = CurrencySection.from(state.section)

    FeatureScaffold(
        title = "Currency",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load(refreshing = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
        floatingActionButton = {
            when {
                section == CurrencySection.CONFIG && state.hasUnsavedChanges -> ExtendedFloatingActionButton(
                    onClick = { if (!state.isSaving) viewModel.saveConfig() },
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save settings") },
                )

                section == CurrencySection.SHOP -> NewItemFab(
                    label = "Add item",
                    onClick = { viewModel.openShopEditor(null) },
                )
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = section.id, onSelect = viewModel::setSection)

        when (section) {
            CurrencySection.ANALYTICS -> AnalyticsSection(state, viewModel)
            CurrencySection.CONFIG -> ConfigSection(state, viewModel, onReset = { pendingReset = true })
            CurrencySection.SHOP -> ShopSection(state, viewModel, onDelete = { pendingDelete = it })
            CurrencySection.LEADERBOARD -> LeaderboardSection(state, viewModel)
        }
    }

    state.shopDraft?.let { draft ->
        FullScreenEditor(
            title = draft.originalName?.let { "Edit $it" } ?: "New shop item",
            onClose = viewModel::closeShopEditor,
            confirmLabel = when {
                state.shopSaving -> "Saving…"
                draft.isEditing -> "Save"
                else -> "Add"
            },
            confirmEnabled = !state.shopSaving && draft.name.isNotBlank(),
            onConfirm = { viewModel.saveShopDraft() },
            hasUnsavedChanges = !draft.isEditing && draft != ShopItemDraft(),
        ) {
            ShopEditor(draft = draft, state = state, viewModel = viewModel)
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "Reset economy settings?",
            message = "Every betting, earning, bank, transfer, robbery, and streak setting returns to its default.",
            confirmLabel = "Reset",
            onConfirm = viewModel::resetConfig,
            onDismiss = { pendingReset = false },
        )
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.name}?",
            message = "Everyone who owns one loses it. This cannot be undone.",
            onConfirm = { viewModel.deleteShopItem(item) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ColumnScope.AnalyticsSection(state: CurrencyState, viewModel: CurrencyViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnalyticsWindows.forEach { days ->
            FilterChip(
                selected = state.windowDays == days,
                onClick = { viewModel.setWindow(days) },
                label = { Text(if (days == 365) "1y" else "${days}d") },
            )
        }
    }
    if (state.analyticsLoading) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    val analytics = state.analytics
    if (analytics == null) {
        SectionCard {
            if (state.analyticsFailed) {
                RetryNote("Failed to load analytics.", onRetry = viewModel::reloadAnalytics)
            } else {
                EmptyState("Nobody on this server holds any currency yet.", icon = Icons.Default.Paid)
            }
        }
        return
    }
    val snapshot = analytics.snapshot

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EconomyStat(
            label = "Money supply",
            value = snapshot.moneySupply.withSeparators(),
            detail = "${snapshot.holders.withSeparators()} holders",
            modifier = Modifier.weight(1f),
        )
        EconomyStat(
            label = "Net change",
            value = snapshot.netChange.signed(),
            detail = "over ${analytics.windowDays} days",
            valueColor = when {
                snapshot.netChange > 0 -> MaterialTheme.colorScheme.primary
                snapshot.netChange < 0 -> MaterialTheme.colorScheme.error
                else -> null
            },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EconomyStat(
            label = "Gini",
            value = "%.3f".format(snapshot.gini),
            detail = "top 10% hold ${snapshot.topTenPercentShare.percent()}",
            modifier = Modifier.weight(1f),
        )
        EconomyStat(
            label = "Median holding",
            value = snapshot.median.withSeparators(),
            detail = "mean ${snapshot.mean.withSeparators()}",
            modifier = Modifier.weight(1f),
        )
    }

    SectionCard {
        SectionCardHeader("Money supply over time", Icons.Default.Insights)
        Caption("Currency created minus currency destroyed. A line that only climbs means your faucets outpace your sinks.")
        if (analytics.supplyHistory.isEmpty()) {
            EmptyState("No ledger activity in this window.")
        } else {
            SupplyChart(analytics.supplyHistory)
        }
    }

    SectionCard {
        SectionCardHeader("Where the money sits", Icons.Default.PieChart)
        val total = snapshot.moneySupply
        val walletShare = if (total > 0) snapshot.inWallets.toFloat() / total.toFloat() else 0f
        RatioBar(fraction = walletShare, color = MaterialTheme.colorScheme.primary, height = 10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot("Wallets ${snapshot.inWallets.withSeparators()}", MaterialTheme.colorScheme.primary)
            Spacer8()
            Caption("(robbable, spendable)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot("Banked ${snapshot.inBanks.withSeparators()}", MaterialTheme.colorScheme.surfaceContainerHighest)
            Spacer8()
            Caption("(safe, idle)")
        }
    }

    val faucets = analytics.flow.filter { it.net > 0 }.sortedByDescending { it.net }
    val sinks = analytics.flow.filter { it.net <= 0 }.sortedBy { it.net }
    val largest = (analytics.flow.maxOfOrNull { kotlin.math.abs(it.net) } ?: 0L).coerceAtLeast(1L)

    FlowPanel(
        title = "Faucets",
        note = "Currency entering circulation",
        buckets = faucets,
        largest = largest,
        color = MaterialTheme.colorScheme.primary,
        icon = Icons.Default.ArrowUpward,
    )
    FlowPanel(
        title = "Sinks",
        note = "Currency leaving circulation",
        buckets = sinks,
        largest = largest,
        color = MaterialTheme.colorScheme.error,
        icon = Icons.Default.ArrowDownward,
    )

    if (analytics.transferTax > 0) {
        SectionCard {
            SectionCardHeader("Transfer tax", Icons.Default.SwapHoriz)
            Text(
                text = "${analytics.transferTax.withSeparators()} destroyed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Caption(
                "Destroyed as transfer tax over this window. Tax has no ledger row of its own, so it is " +
                    "derived from the gap between what senders paid and what recipients received."
            )
        }
    }

    SectionCard {
        SectionCardHeader("Game performance", Icons.Default.Casino)
        Caption(
            "RTP is what players actually got back per unit wagered. Above 100% means the game is " +
                "printing currency and wants its payout multiplier lowered."
        )
        if (analytics.games.isEmpty()) {
            EmptyState("No games played in this window.", icon = Icons.Default.Casino)
        } else {
            analytics.games.sortedByDescending { it.wagered }.forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider()
                GameRow(game)
            }
        }
    }
}

@Composable
private fun FlowPanel(
    title: String,
    note: String,
    buckets: List<FlowBucket>,
    largest: Long,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    SectionCard {
        SectionCardHeader(title, icon, tint = color)
        Caption(note)
        if (buckets.isEmpty()) {
            EmptyState("Nothing recorded.")
        } else {
            buckets.forEach { bucket ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bucket.category,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = bucket.net.signed(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                        )
                    }
                    RatioBar(
                        fraction = kotlin.math.abs(bucket.net).toFloat() / largest.toFloat(),
                        color = color,
                    )
                    Caption(
                        "${bucket.entries.withSeparators()} entries, " +
                            "${bucket.`in`.withSeparators()} in, ${bucket.out.withSeparators()} out"
                    )
                }
            }
        }
    }
}

@Composable
private fun GameRow(game: GamePerformance) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = game.game,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "RTP ${game.actualRtp.percent()}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (game.actualRtp > 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        InfoRow("Wagered", game.wagered.withSeparators())
        InfoRow("Returned", game.returned.withSeparators())
        InfoRow(
            label = "House take",
            value = game.houseTake.signed(),
            valueColor = if (game.houseTake < 0) MaterialTheme.colorScheme.error else null,
        )
        InfoRow("Plays", game.plays.withSeparators())
        InfoRow("Players", game.players.withSeparators())
    }
}

@Composable
private fun ColumnScope.ConfigSection(
    state: CurrencyState,
    viewModel: CurrencyViewModel,
    onReset: () -> Unit,
) {
    val config = state.config
    if (config == null) {
        SectionCard {
            if (state.configFailed) {
                RetryNote("Could not load economy settings.", onRetry = { viewModel.load(refreshing = true) })
            } else {
                EmptyState("Could not load economy settings.", icon = Icons.Default.Tune)
            }
        }
        return
    }
    val edit = viewModel::editConfig

    SectionCard {
        SectionCardHeader("Betting", Icons.Default.Casino)
        SwitchRow(
            title = "Enable gambling",
            subtitle = "Turns every wagering game on or off",
            checked = config.gamblingEnabled,
            onCheckedChange = { value -> edit { it.copy(gamblingEnabled = value) } },
        )
        CurrencyNumberField("Minimum bet", config.minBet, { value -> edit { it.copy(minBet = value) } }, min = 1)
        CurrencyNumberField(
            label = "Maximum bet",
            value = config.maxBet,
            onValueChange = { value -> edit { it.copy(maxBet = value) } },
            hint = "0 for unlimited. A ceiling stops one lucky run ending the economy.",
        )
        CurrencyDecimalField(
            label = "Payout multiplier",
            value = config.payoutMultiplier,
            onValueChange = { value -> edit { it.copy(payoutMultiplier = value) } },
            min = 0.1,
            max = 5.0,
            hint = "Scales winnings only, never the returned stake. Below 1.0 widens the house edge.",
        )
        CurrencyIntField(
            label = "Game cooldown (seconds)",
            value = config.gameCooldownSeconds,
            onValueChange = { value -> edit { it.copy(gameCooldownSeconds = value) } },
            hint = cooldownLabel(config.gameCooldownSeconds),
            max = 86400,
        )
        CurrencyNumberField(
            label = "Daily loss limit",
            value = config.lossLimitPerDay,
            onValueChange = { value -> edit { it.copy(lossLimitPerDay = value) } },
            hint = "0 to disable. Cuts a user off after losing this much in 24h.",
        )
    }

    SectionCard {
        SectionCardHeader("Earning", Icons.Default.Work)
        SwitchRow(
            title = "Enable work",
            subtitle = "The low-risk, steady earning command",
            checked = config.workEnabled,
            onCheckedChange = { value -> edit { it.copy(workEnabled = value) } },
        )
        CurrencyNumberField("Work minimum", config.workMinReward, { value -> edit { it.copy(workMinReward = value) } })
        CurrencyNumberField("Work maximum", config.workMaxReward, { value -> edit { it.copy(workMaxReward = value) } })
        CurrencyIntField(
            label = "Work cooldown (seconds)",
            value = config.workCooldownSeconds,
            onValueChange = { value -> edit { it.copy(workCooldownSeconds = value) } },
            hint = cooldownLabel(config.workCooldownSeconds),
            max = 86400,
        )
        HorizontalDivider()
        SwitchRow(
            title = "Enable crime",
            subtitle = "Pays better than work but fails often enough to stay behind it on average",
            checked = config.crimeEnabled,
            onCheckedChange = { value -> edit { it.copy(crimeEnabled = value) } },
        )
        CurrencyNumberField("Crime minimum", config.crimeMinReward, { value -> edit { it.copy(crimeMinReward = value) } })
        CurrencyNumberField("Crime maximum", config.crimeMaxReward, { value -> edit { it.copy(crimeMaxReward = value) } })
        CurrencyIntField(
            label = "Crime success chance (%)",
            value = config.crimeSuccessChance,
            onValueChange = { value -> edit { it.copy(crimeSuccessChance = value) } },
            max = 100,
        )
        CurrencyNumberField("Crime fine minimum", config.crimeFineMin, { value -> edit { it.copy(crimeFineMin = value) } })
        CurrencyNumberField("Crime fine maximum", config.crimeFineMax, { value -> edit { it.copy(crimeFineMax = value) } })
        CurrencyIntField(
            label = "Crime cooldown (seconds)",
            value = config.crimeCooldownSeconds,
            onValueChange = { value -> edit { it.copy(crimeCooldownSeconds = value) } },
            hint = cooldownLabel(config.crimeCooldownSeconds),
            max = 86400,
        )
    }

    SectionCard {
        SectionCardHeader("Bank", Icons.Default.AccountBalance)
        Caption("Banked currency cannot be robbed. Interest is a faucet, so it is off by default.")
        SwitchRow(
            title = "Enable bank",
            subtitle = "Lets users move currency out of reach of robbery",
            checked = config.bankEnabled,
            onCheckedChange = { value -> edit { it.copy(bankEnabled = value) } },
        )
        CurrencyNumberField(
            label = "Bank capacity",
            value = config.bankCapacity,
            onValueChange = { value -> edit { it.copy(bankCapacity = value) } },
            hint = "0 for unlimited",
        )
        CurrencyDecimalField(
            label = "Interest (%)",
            value = config.bankInterestPercent,
            onValueChange = { value -> edit { it.copy(bankInterestPercent = value) } },
            min = 0.0,
            max = 100.0,
            hint = "Paid per interval on the banked balance",
        )
        CurrencyIntField(
            label = "Interest interval (hours)",
            value = config.bankInterestHours,
            onValueChange = { value -> edit { it.copy(bankInterestHours = value) } },
            min = 1,
            max = 720,
        )
    }

    SectionCard {
        SectionCardHeader("Transfers", Icons.Default.SwapHoriz)
        Caption("Transfer tax is a sink: the taxed portion is destroyed rather than moved.")
        SwitchRow(
            title = "Enable transfers",
            subtitle = "Lets users pay each other",
            checked = config.payEnabled,
            onCheckedChange = { value -> edit { it.copy(payEnabled = value) } },
        )
        CurrencyIntField(
            label = "Transfer tax (%)",
            value = config.payTaxPercent,
            onValueChange = { value -> edit { it.copy(payTaxPercent = value) } },
            max = 100,
        )
        CurrencyIntField(
            label = "Transfer cooldown (seconds)",
            value = config.payCooldownSeconds,
            onValueChange = { value -> edit { it.copy(payCooldownSeconds = value) } },
            hint = cooldownLabel(config.payCooldownSeconds),
            max = 86400,
        )
        CurrencyNumberField("Minimum transfer", config.payMinimum, { value -> edit { it.copy(payMinimum = value) } }, min = 1)
    }

    SectionCard {
        SectionCardHeader("Robbery", Icons.Default.Gavel)
        Caption("Off by default because it is disruptive in servers that did not opt into it. Only wallets can be robbed.")
        SwitchRow(
            title = "Enable robbery",
            subtitle = "Lets users steal from each other's wallets",
            checked = config.robEnabled,
            onCheckedChange = { value -> edit { it.copy(robEnabled = value) } },
        )
        CurrencyIntField(
            label = "Success chance (%)",
            value = config.robSuccessChance,
            onValueChange = { value -> edit { it.copy(robSuccessChance = value) } },
            max = 100,
        )
        CurrencyIntField(
            label = "Maximum steal (%)",
            value = config.robMaxStealPercent,
            onValueChange = { value -> edit { it.copy(robMaxStealPercent = value) } },
            hint = "Share of the target's wallet a success takes",
            min = 1,
            max = 100,
        )
        CurrencyIntField(
            label = "Failure fine (%)",
            value = config.robFinePercent,
            onValueChange = { value -> edit { it.copy(robFinePercent = value) } },
            hint = "Share of the robber's wallet destroyed on failure",
            max = 100,
        )
        CurrencyNumberField(
            label = "Protected below",
            value = config.robMinimumWallet,
            onValueChange = { value -> edit { it.copy(robMinimumWallet = value) } },
            hint = "Targets holding less than this cannot be robbed",
        )
        CurrencyIntField(
            label = "Robbery cooldown (seconds)",
            value = config.robCooldownSeconds,
            onValueChange = { value -> edit { it.copy(robCooldownSeconds = value) } },
            hint = cooldownLabel(config.robCooldownSeconds),
            max = 86400,
        )
    }

    SectionCard {
        SectionCardHeader("Daily reward streaks", Icons.Default.LocalFireDepartment)
        SwitchRow(
            title = "Enable streaks",
            subtitle = "Consecutive daily claims earn an escalating bonus",
            checked = config.dailyStreakEnabled,
            onCheckedChange = { value -> edit { it.copy(dailyStreakEnabled = value) } },
        )
        CurrencyNumberField(
            label = "Bonus per day",
            value = config.dailyStreakBonus,
            onValueChange = { value -> edit { it.copy(dailyStreakBonus = value) } },
            hint = "Added per consecutive day claimed",
        )
        CurrencyNumberField(
            label = "Maximum bonus",
            value = config.dailyStreakMaxBonus,
            onValueChange = { value -> edit { it.copy(dailyStreakMaxBonus = value) } },
            hint = "0 for uncapped",
        )
    }

    SectionCard {
        SectionCardHeader("Defaults", Icons.Default.Restore)
        Caption("Restore every economy setting to the bot's defaults.")
        OutlinedButton(
            onClick = onReset,
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Restore, contentDescription = null)
            Spacer8()
            Text("Reset to defaults")
        }
    }
}

@Composable
private fun ColumnScope.ShopSection(
    state: CurrencyState,
    viewModel: CurrencyViewModel,
    onDelete: (ShopItem) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Shop items", Icons.Default.Storefront)
        Caption("The shop is the economy's main sink. Without one, balances only ever accumulate.")
        when {
            state.shopItems.isEmpty() && state.shopFailed ->
                RetryNote("Failed to load the shop.", onRetry = viewModel::reloadShop)

            state.shopItems.isEmpty() ->
                EmptyState(
                    message = "No shop items yet. Add one to give currency somewhere to go.",
                    icon = Icons.Default.Storefront,
                    actionLabel = "Add item",
                    onAction = { viewModel.openShopEditor(null) },
                )
        }
    }

    state.shopItems.forEach { item ->
        ShopItemCard(
            item = item,
            onEdit = { viewModel.openShopEditor(item) },
            onToggle = { viewModel.toggleShopItem(item) },
            onDelete = { onDelete(item) },
        )
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.price.withSeparators(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TagChip(if (item.type == ShopItemType.COLLECTIBLE) "Item" else item.type.label)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!item.enabled) TagChip("Hidden", icon = Icons.Default.VisibilityOff)
            if (item.consumable) TagChip("Consumable")
            TagChip(if (item.stock < 0) "Unlimited stock" else if (item.stock == 0) "Sold out" else "${item.stock} in stock")
            if (item.maxPerUser > 0) TagChip("Limit ${item.maxPerUser} per person")
        }
        item.description?.takeIf { it.isNotBlank() }?.let { Caption(it) }
        item.roleName?.let { InfoRow("Grants", "@$it") }
        item.requiredRoleName?.let { InfoRow("Requires", "@$it") }
        InfoRow("Owned", item.owned.withSeparators())
        InfoRow("Spent", item.revenue.withSeparators())
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer8()
                Text("Edit")
            }
            TextButton(onClick = onToggle) {
                Icon(
                    if (item.enabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                )
                Spacer8()
                Text(if (item.enabled) "Hide" else "Show")
            }
            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer8()
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * The body of the shop item editor, shown inside a [FullScreenEditor] whose
 * top bar holds Add or Save and Close.
 */
@Composable
private fun ShopEditor(draft: ShopItemDraft, state: CurrencyState, viewModel: CurrencyViewModel) {
    val edit = viewModel::editShopDraft
    val roleOptions = state.roles.map { SelectorOption(it.id, it.name) }

    SectionCard {
        SectionCardHeader(title = "Item details", icon = Icons.Default.Inventory2)
        MewdekoTextField(
            value = draft.name,
            onValueChange = { value -> edit { it.copy(name = value) } },
            label = "Name",
            isError = draft.name.isBlank(),
        )
        CurrencyNumberField(
            label = "Price",
            value = draft.price,
            onValueChange = { value -> edit { it.copy(price = value) } },
        )
        MewdekoTextField(
            value = draft.description,
            onValueChange = { value -> edit { it.copy(description = value) } },
            label = "Description",
            singleLine = false,
            minLines = 2,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Category),
            options = ShopItemType.entries.map { SelectorOption(it.raw.toString(), it.label, subtitle = it.blurb) },
            placeholder = "Collectible",
            label = "Type",
            selectedId = draft.type.raw.toString(),
            onSelect = { id ->
                val type = id?.toIntOrNull()?.let { raw -> ShopItemType.from(raw) } ?: ShopItemType.COLLECTIBLE
                edit { it.copy(type = type) }
            },
        )
        if (draft.type == ShopItemType.ROLE) {
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "Pick a role",
                label = "Role granted",
                selectedId = draft.roleId,
                onSelect = { id -> edit { it.copy(roleId = id) } },
            )
        }
        if (draft.type == ShopItemType.TEXT) {
            MewdekoTextField(
                value = draft.textContent,
                onValueChange = { value -> edit { it.copy(textContent = value) } },
                label = "Delivered text",
                singleLine = false,
                minLines = 3,
                supportingText = "Sent to the buyer by DM",
            )
        }
        SwitchRow(
            title = "Unlimited stock",
            subtitle = "Turn off to set how many remain. 0 means sold out.",
            checked = draft.stock < 0,
            onCheckedChange = { unlimited -> edit { it.copy(stock = if (unlimited) -1 else 0) } },
        )
        if (draft.stock >= 0) {
            CurrencyIntField(
                label = "Stock",
                value = draft.stock,
                onValueChange = { value -> edit { it.copy(stock = value) } },
            )
        }
        CurrencyIntField(
            label = "Limit per user",
            value = draft.maxPerUser,
            onValueChange = { value -> edit { it.copy(maxPerUser = value) } },
            hint = "0 for unlimited",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "Anyone can buy",
            label = "Required role",
            selectedId = draft.requiredRoleId,
            onSelect = { id -> edit { it.copy(requiredRoleId = id) } },
        )
        CurrencyIntField(
            label = "Sort order",
            value = draft.sortOrder,
            onValueChange = { value -> edit { it.copy(sortOrder = value) } },
            hint = "Lower numbers are listed first",
            min = Int.MIN_VALUE,
            allowNegative = true,
        )
        SwitchRow(
            title = "Consumable",
            subtitle = "Can be used up after purchase",
            checked = draft.consumable,
            onCheckedChange = { value -> edit { it.copy(consumable = value) } },
        )
        SwitchRow(
            title = "Visible in shop",
            subtitle = "Members can see and buy this item",
            checked = draft.enabled,
            onCheckedChange = { value -> edit { it.copy(enabled = value) } },
        )
    }
}

@Composable
private fun ColumnScope.LeaderboardSection(state: CurrencyState, viewModel: CurrencyViewModel) {
    val memberOptions = remember(state.members) {
        state.members.map { member ->
            SelectorOption(
                id = member.id,
                name = member.label(),
                subtitle = member.username.takeIf { it.isNotBlank() && it != member.label() }?.let { "@$it" },
            )
        }
    }
    val namesById = remember(state.members) { state.members.associate { it.id to it.label() } }

    SectionCard {
        SectionCardHeader("Adjust a balance", Icons.Default.AccountBalanceWallet)
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = memberOptions,
            placeholder = "Select a member",
            label = "Member",
            selectedId = state.adjustUserId,
            onSelect = viewModel::setAdjustUser,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !state.adjustRemoves,
                onClick = { viewModel.setAdjustRemoves(false) },
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            )
            FilterChip(
                selected = state.adjustRemoves,
                onClick = { viewModel.setAdjustRemoves(true) },
                label = { Text("Remove") },
                leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
            )
        }
        CurrencyNumberField(
            label = "Amount",
            value = state.adjustAmount,
            onValueChange = viewModel::setAdjustAmount,
            hint = if (state.adjustRemoves) "Removed from the member's wallet" else "Added to the member's wallet",
        )
        MewdekoTextField(
            value = state.adjustReason,
            onValueChange = viewModel::setAdjustReason,
            label = "Reason",
            placeholder = "Recorded on the ledger",
        )
        Button(
            onClick = { viewModel.adjustBalance() },
            enabled = !state.isAdjusting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.isAdjusting -> "Applying…"
                    state.adjustRemoves -> "Remove currency"
                    else -> "Add currency"
                }
            )
        }
    }

    SectionCard {
        SectionCardHeader(
            title = "Richest members",
            icon = Icons.Default.Leaderboard,
            trailing = {
                if (state.leaderboardTotal > 0) {
                    Text(
                        text = "${state.leaderboardTotal.withSeparators()} holders",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (state.leaderboardSupply > 0) {
            InfoRow("Total supply", state.leaderboardSupply.withSeparators())
        }
        if (state.leaderboardLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        when {
            state.leaderboard.isEmpty() && state.leaderboardFailed ->
                RetryNote("Failed to load the leaderboard.", onRetry = { viewModel.loadLeaderboardPage(state.leaderboardPage) })

            state.leaderboard.isEmpty() ->
                EmptyState("Nobody holds any currency yet.", icon = Icons.Default.Paid)

            else -> state.leaderboard.forEach { entry ->
                LeaderboardRow(entry, fallbackName = namesById[entry.userId])
            }
        }
        if (state.leaderboardTotal > CurrencyLeaderboardPageSize) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.loadLeaderboardPage(state.leaderboardPage - 1) },
                    enabled = state.leaderboardPage > 0 && !state.leaderboardLoading,
                ) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                    Text("Prev")
                }
                Text(
                    text = "Page ${state.leaderboardPage + 1} of ${state.leaderboardPageCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(
                    onClick = { viewModel.loadLeaderboardPage(state.leaderboardPage + 1) },
                    enabled = state.leaderboardPage + 1 < state.leaderboardPageCount && !state.leaderboardLoading,
                ) {
                    Text("Next")
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: CurrencyLeaderboardEntry, fallbackName: String?) {
    val name = entry.username?.takeIf { it.isNotBlank() } ?: fallbackName ?: "Unknown user"
    ListItem(
        headlineContent = {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    text = "Wallet ${entry.wallet.withSeparators()}, bank ${entry.bank.withSeparators()}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RatioBar(
                    fraction = entry.shareOfSupply.toFloat(),
                    color = MaterialTheme.colorScheme.primary,
                    height = 4,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${entry.rank}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(36.dp),
                )
                Avatar(url = entry.avatarUrl, contentDescription = name, size = 36)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.netWorth.withSeparators(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.shareOfSupply.percent(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
}

@Composable
private fun RetryNote(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}
