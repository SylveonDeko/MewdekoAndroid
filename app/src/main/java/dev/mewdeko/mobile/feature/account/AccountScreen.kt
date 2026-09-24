package dev.mewdeko.mobile.feature.account

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.auth.OwnerAccess
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSheet
import dev.mewdeko.mobile.core.ui.FeatureContentPadding
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.feature.guilddetail.home.EnteredKeys
import dev.mewdeko.mobile.feature.guilddetail.home.rememberReducedMotion
import dev.mewdeko.mobile.feature.guilddetail.home.riseOnce
import dev.mewdeko.mobile.feature.owner.OwnerAccessViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Me tab: the profile hero, a carousel of server orbs that picks the
 * server context, the per-server sections, then session and privacy.
 *
 * While on screen the app is themed from the user's avatar, as on iOS, so the
 * tab carries the user's own identity colors rather than the house default.
 */
@Composable
fun AccountScreen(
    user: MobileUser,
    instance: MobileInstance?,
    onSwitchInstance: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
    onOpenOwnerPanel: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
    ownerAccess: OwnerAccessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val access by ownerAccess.access.collectAsStateWithLifecycle()

    var pendingSignOut by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    var editingAfk by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduced = rememberReducedMotion()
    val anchors = remember { ScrollAnchors() }
    val entered = remember { EnteredKeys() }

    DisposableEffect(user.avatarUrl) {
        viewModel.applyUserPalette(user.avatarUrl)
        onDispose { viewModel.releaseUserPalette(user.avatarUrl) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onAppear()
    }

    val scrollToSession: () -> Unit = {
        val viewport = anchors.viewport?.takeIf { it.isAttached }
        val session = anchors.session?.takeIf { it.isAttached }
        if (viewport != null && session != null) {
            val margin = with(density) { 12.dp.toPx() }
            val delta = session.positionInRoot().y - viewport.positionInRoot().y - margin
            scope.launch { if (reduced) scroll.scrollBy(delta) else scroll.animateScrollBy(delta) }
        }
    }

    FeatureScaffold(
        title = "Me",
        onBack = null,
        loadState = LoadState(hasLoaded = true, isRefreshing = state.isRefreshing),
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = {
            viewModel.refresh()
            ownerAccess.refresh()
        },
        scrollable = false,
        actions = {
            IconButton(onClick = scrollToSession) {
                Icon(Icons.Default.Settings, contentDescription = "Session settings")
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { anchors.viewport = it }
                .verticalScroll(scroll)
                .padding(FeatureContentPadding)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            MeHero(user = user, instance = instance, reduced = reduced)

            MeGuildPicker(
                state = state,
                reduced = reduced,
                onSelect = viewModel::selectGuild,
                onRetry = viewModel::retryGuilds,
            )

            if (state.selectedGuild != null) {
                MeNumbersSection(state, Modifier.riseOnce("numbers", entered))
                MeChannelsSection(state, Modifier.riseOnce("channels", entered))
                MeAfkSection(
                    state = state,
                    onEdit = { editingAfk = true },
                    onClear = viewModel::clearAfk,
                    modifier = Modifier.riseOnce("afk", entered),
                )
                MeWatchingSection(
                    state = state,
                    onToggleHighlights = viewModel::setHighlightsEnabled,
                    onAddHighlight = viewModel::addHighlight,
                    onRemoveHighlight = viewModel::removeHighlight,
                    modifier = Modifier.riseOnce("watching", entered),
                )
                MeActivitySection(state, Modifier.riseOnce("activity", entered))
                MeProfileSection(state, Modifier.riseOnce("profile", entered))
                MePreferencesSection(
                    state = state,
                    actions = PreferenceActions(
                        levelUpPings = viewModel::toggleLevelUpPings,
                        pronouns = viewModel::togglePronouns,
                        guidedSetup = viewModel::toggleGuidedSetup,
                        greetDms = viewModel::toggleGreetDms,
                        stats = viewModel::toggleStats,
                        birthdayAnnouncements = viewModel::toggleBirthdayAnnouncements,
                    ),
                    modifier = Modifier.riseOnce("preferences", entered),
                )
            }

            if (access == OwnerAccess.Owner) {
                MeOwnerSection(
                    instance = instance,
                    onOpen = onOpenOwnerPanel,
                    modifier = Modifier.riseOnce("owner", entered),
                )
            }

            MeSessionSection(
                instance = instance,
                dashboardHost = state.dashboardHost,
                onSwitchInstance = onSwitchInstance,
                onSwitchServer = onSwitchServer,
                onSignOut = { pendingSignOut = true },
                modifier = Modifier.onGloballyPositioned { anchors.session = it },
            )

            MePrivacySection(
                onOpenPolicy = { uriHandler.openUri(PrivacyPolicyUrl) },
                onOpenTerms = { uriHandler.openUri(TermsUrl) },
                onDeleteData = { pendingDelete = true },
            )
        }
    }

    if (editingAfk) {
        AfkEditorSheet(
            initial = state.afk?.message.orEmpty(),
            onSave = viewModel::setAfk,
            onDismiss = { editingAfk = false },
        )
    }

    if (pendingDelete) {
        ConfirmDialog(
            title = "Delete your data?",
            message = "Your session is revoked on the dashboard and this device forgets your " +
                "tokens, profile, and saved server. Your Discord account is not affected.",
            confirmLabel = "Delete",
            onConfirm = { pendingDelete = false; onDeleteData() },
            onDismiss = { pendingDelete = false },
        )
    }

    if (pendingSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Your tokens for this dashboard will be removed from this device.",
            confirmLabel = "Sign out",
            onConfirm = onSignOut,
            onDismiss = { pendingSignOut = false },
        )
    }
}

/**
 * Layout handles for the settings action: the scroll viewport and the
 * session section. Plain fields, not state, so layout never recomposes.
 */
private class ScrollAnchors {
    /** The scrolling column's viewport. */
    var viewport: LayoutCoordinates? = null

    /** The session section. */
    var session: LayoutCoordinates? = null
}

/**
 * The profile hero: the avatar in a sweep ring of the palette gradient, the
 * display name, the username, and a chip naming the connected bot.
 */
@Composable
private fun MeHero(user: MobileUser, instance: MobileInstance?, reduced: Boolean) {
    val palette = LocalGuildPalette.current
    val ring = remember(palette) { Brush.sweepGradient(palette.gradient + palette.gradientStart.color) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(
            url = user.avatarUrl,
            contentDescription = user.displayName,
            size = 96,
            ring = BorderStroke(3.dp, ring),
            fallbackText = user.displayName,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Text(
            text = user.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (instance != null) {
            BotChip(instance, reduced, Modifier.padding(top = 4.dp))
        }
    }
}

/** A capsule with the bot's avatar, a breathing status dot, and its name. */
@Composable
private fun BotChip(instance: MobileInstance, reduced: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val breath = if (reduced) {
        remember { mutableStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "botDot").animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "botDotAlpha",
        )
    }
    Surface(
        shape = CircleShape,
        color = primary.copy(alpha = DashAlpha.Hex20),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = guildBorder(),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Connected to ${instance.botName}"
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Avatar(
                url = instance.avatarUrl,
                contentDescription = null,
                size = 18,
                fallbackIcon = Icons.Default.SmartToy,
                fallbackText = instance.botName,
            )
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = primary, alpha = breath.value)
            }
            Text(
                text = "Connected to ${instance.botName}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The server picker: an overline, a carousel of guild icon orbs where the
 * selected one grows and wears a primary ring, a trailing orb that opens a
 * searchable sheet of every guild, and the selected server's name.
 */
@Composable
private fun MeGuildPicker(
    state: MeState,
    reduced: Boolean,
    onSelect: (Snowflake) -> Unit,
    onRetry: () -> Unit,
) {
    val guilds = state.guilds
    var showingAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MeOverline("Server", modifier = Modifier.padding(horizontal = 2.dp))
        when {
            guilds.isNullOrEmpty() && state.guildsError != null -> GuildsError(state.guildsError, onRetry)
            guilds == null -> Row(
                modifier = Modifier.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Loading servers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            guilds.isEmpty() -> MeEmptyLine("No servers shared with this bot.")
            else -> {
                GuildOrbRow(
                    guilds = guilds,
                    selectedId = state.selectedGuildId,
                    reduced = reduced,
                    onSelect = onSelect,
                    onShowAll = { showingAll = true },
                )
                state.selectedGuild?.let { SelectedGuildLine(it, Modifier.padding(horizontal = 2.dp)) }
            }
        }
    }

    if (showingAll && !guilds.isNullOrEmpty()) {
        DiscordSelectorSheet(
            kind = SelectorKind.Custom(Icons.Default.Dns),
            options = guilds.map { guild ->
                SelectorOption(
                    id = guild.id,
                    name = guild.name,
                    subtitle = if (guild.owner) "Owner" else null,
                    imageUrl = guild.iconUrl,
                )
            },
            selection = listOfNotNull(state.selectedGuildId),
            onSelectionChange = { ids -> ids.firstOrNull()?.let(onSelect) },
            onDismiss = { showingAll = false },
        )
    }
}

/** The horizontal orb carousel, keeping the selected orb near the center. */
@Composable
private fun GuildOrbRow(
    guilds: List<Guild>,
    selectedId: Snowflake?,
    reduced: Boolean,
    onSelect: (Snowflake) -> Unit,
    onShowAll: () -> Unit,
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val selectedIndex = guilds.indexOfFirst { it.id == selectedId }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val visible = snapshotFlow { listState.layoutInfo.visibleItemsInfo.size }.first { it > 0 }
        val target = (selectedIndex - visible / 2).coerceAtLeast(0)
        if (reduced) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(guilds, key = { it.id }) { guild ->
            GuildOrb(
                guild = guild,
                selected = guild.id == selectedId,
                reduced = reduced,
                onClick = {
                    if (guild.id != selectedId) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(guild.id)
                    }
                },
            )
        }
        item(key = "all") {
            val primary = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .size(OrbSlot)
                    .padding(OrbInset)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = DashAlpha.Hex20))
                    .border(guildBorder(), CircleShape)
                    .clickable(onClickLabel = "All servers", role = Role.Button, onClick = onShowAll)
                    .semantics { contentDescription = "All servers" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = primary)
            }
        }
    }
}

/** One guild icon orb: grows and wears a primary ring while selected. */
@Composable
private fun GuildOrb(guild: Guild, selected: Boolean, reduced: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val scale by animateFloatAsState(
        targetValue = if (selected && !reduced) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "orbScale",
    )
    Box(
        modifier = Modifier
            .size(OrbSlot)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, if (selected) primary else Color.Transparent, CircleShape)
            .clip(CircleShape)
            .clickable(onClickLabel = "Show stats for ${guild.name}", role = Role.Tab, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = guild.name
            },
        contentAlignment = Alignment.Center,
    ) {
        Avatar(
            url = guild.iconUrl,
            contentDescription = null,
            size = 44,
            fallbackText = guild.name,
        )
    }
}

/** The outer slot of a guild orb: the 44dp icon plus room for its ring. */
private val OrbSlot = 52.dp

/** The gap between a guild orb's icon and its ring. */
private val OrbInset = 4.dp

/** A failed guild list, inline, with a retry action. */
@Composable
private fun GuildsError(message: String, onRetry: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = error.copy(alpha = DashAlpha.Hex10),
        border = guildBorder(error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = error, modifier = Modifier.size(18.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/**
 * "Owner": the way into the owner panel. Only composed once the selected bot
 * has confirmed the user owns it, so non-owners never see it.
 */
@Composable
private fun MeOwnerSection(
    instance: MobileInstance?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Owner", Icons.Default.WorkspacePremium, tint = primary)
        MeCard(tint = primary) {
            SessionRow(
                title = "Owner panel",
                subtitle = "Fleet and host tools for ${instance?.botName?.takeIf { it.isNotEmpty() } ?: "this bot"}",
                icon = Icons.Default.WorkspacePremium,
                tone = primary,
                onClick = onOpen,
            )
        }
    }
}

/** "Session": switch bot, switch dashboard, and sign out. */
@Composable
private fun MeSessionSection(
    instance: MobileInstance?,
    dashboardHost: String?,
    onSwitchInstance: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalGuildPalette.current.muted.color
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Session", Icons.Default.Settings, tint = muted)
        MeCard(tint = muted) {
            SessionRow(
                title = "Switch bot",
                subtitle = instance?.let { "Connected to ${it.botName}" },
                icon = Icons.Default.SwapHoriz,
                tone = scheme.primary,
                onClick = onSwitchInstance,
            )
            MeDivider(inset = SessionDividerInset)
            SessionRow(
                title = "Switch dashboard",
                subtitle = dashboardHost,
                icon = Icons.Default.Dns,
                tone = scheme.secondary,
                onClick = onSwitchServer,
            )
            MeDivider(inset = SessionDividerInset)
            SessionRow(
                title = "Sign out",
                subtitle = null,
                icon = Icons.AutoMirrored.Filled.Logout,
                tone = scheme.error,
                destructive = true,
                onClick = onSignOut,
            )
        }
    }
}

/** Privacy: the published policies and deleting the user's data. */
@Composable
private fun MePrivacySection(
    onOpenPolicy: () -> Unit,
    onOpenTerms: () -> Unit,
    onDeleteData: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalGuildPalette.current.muted.color
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Privacy", Icons.Default.Shield, tint = muted)
        MeCard(tint = muted) {
            Text(
                text = "Mewdeko stores your Discord id, username, and avatar so the dashboard " +
                    "can identify you, and keeps your sign-in tokens encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            MeDivider()
            SessionRow(
                title = "Privacy policy",
                subtitle = null,
                icon = Icons.Default.Policy,
                tone = scheme.primary,
                onClick = onOpenPolicy,
            )
            MeDivider(inset = SessionDividerInset)
            SessionRow(
                title = "Terms of service",
                subtitle = null,
                icon = Icons.Default.Gavel,
                tone = scheme.secondary,
                onClick = onOpenTerms,
            )
            MeDivider(inset = SessionDividerInset)
            SessionRow(
                title = "Delete my data",
                subtitle = "Revokes this session and forgets this device",
                icon = Icons.Default.DeleteForever,
                tone = scheme.error,
                destructive = true,
                onClick = onDeleteData,
            )
        }
    }
}

/**
 * One session action: a small orb in [tone], a title and optional caption,
 * and a chevron. [destructive] colors the title in the tone.
 */
@Composable
private fun SessionRow(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tone: Color,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphOrb(icon, tint = tone)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) readableInk(tone) else scheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.outline,
        )
    }
}

/** Divider inset past a session row's 28dp orb and its gap. */
private const val SessionDividerInset = 40

/** Where the published privacy policy lives. */
private const val PrivacyPolicyUrl = "https://mewdeko.tech/privacy"

/** Where the published terms of service live. */
private const val TermsUrl = "https://mewdeko.tech/terms"
