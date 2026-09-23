package dev.mewdeko.mobile.feature.guilddetail

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.ErrorState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.ImmersiveBar
import dev.mewdeko.mobile.core.ui.ImmersiveIconButton
import dev.mewdeko.mobile.feature.guilddetail.home.AutomationBand
import dev.mewdeko.mobile.feature.guilddetail.home.BotCard
import dev.mewdeko.mobile.feature.guilddetail.home.CommunityBand
import dev.mewdeko.mobile.feature.guilddetail.home.EnteredKeys
import dev.mewdeko.mobile.feature.guilddetail.home.EntertainmentBand
import dev.mewdeko.mobile.feature.guilddetail.home.GuildHero
import dev.mewdeko.mobile.feature.guilddetail.home.GuildHomeViewModel
import dev.mewdeko.mobile.feature.guilddetail.home.HeroMetrics
import dev.mewdeko.mobile.feature.guilddetail.home.HomeDimens
import dev.mewdeko.mobile.feature.guilddetail.home.HomeSection
import dev.mewdeko.mobile.feature.guilddetail.home.HomeSeries
import dev.mewdeko.mobile.feature.guilddetail.home.MemberFlowCard
import dev.mewdeko.mobile.feature.guilddetail.home.NowPlayingCard
import dev.mewdeko.mobile.feature.guilddetail.home.PulseGrid
import dev.mewdeko.mobile.feature.guilddetail.home.SafetyBand
import dev.mewdeko.mobile.feature.guilddetail.home.SetupRail
import dev.mewdeko.mobile.feature.guilddetail.home.ShortcutStrip
import dev.mewdeko.mobile.feature.guilddetail.home.hasContent
import dev.mewdeko.mobile.feature.guilddetail.home.rememberHomeRoles
import dev.mewdeko.mobile.feature.guilddetail.home.rememberReducedMotion
import dev.mewdeko.mobile.feature.guilddetail.home.riseOnce
import dev.mewdeko.mobile.feature.guilddetail.home.setupEntries
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * The guild home, a single scrolling "Pulse" dashboard.
 *
 * Re-themes the whole subtree with the guild's derived palette, so every
 * Material component below picks up the server's identity automatically.
 * The hero draws edge to edge under a pinned, transparent top app bar that
 * fills and takes over the title once the guild name scrolls beneath it.
 */
@Composable
fun GuildDetailScreen(
    guild: GuildRouteArgs,
    userId: String,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
    onOpenFeatureBrowser: () -> Unit,
    viewModel: GuildOverviewViewModel = hiltViewModel(),
    homeViewModel: GuildHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()

    MewdekoTheme(palette = palette) {
        val listState = rememberLazyListState()
        val heroMetrics = remember { HeroMetrics() }
        val entered = remember { EnteredKeys() }
        val density = LocalDensity.current
        val barPx = WindowInsets.statusBars.getTop(density) + with(density) { 64.dp.toPx() }
        val fadePx = with(density) { 48.dp.toPx() }
        val titleSpanPx = with(density) { 24.dp.toPx() }
        val immersive = remember(barPx, fadePx, titleSpanPx) {
            ImmersiveBar(
                containerFraction = {
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        val start = heroMetrics.bannerBottomPx - barPx - fadePx
                        ((listState.firstVisibleItemScrollOffset - start) / fadePx).coerceIn(0f, 1f)
                    }
                },
                titleFraction = {
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        val start = heroMetrics.nameBottomPx - barPx
                        ((listState.firstVisibleItemScrollOffset - start) / titleSpanPx).coerceIn(0f, 1f)
                    }
                },
            )
        }
        val scrollOffset = remember(listState) {
            { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 0 }
        }
        val roles = rememberHomeRoles()
        val reduced = rememberReducedMotion()
        val flow = remember(state.joinStats, state.leaveStats) {
            HomeSeries.flow(state.joinStats, state.leaveStats)
        }
        val setup = remember(state, homeState) { setupEntries(state, homeState) }
        val onRefresh: () -> Unit = {
            viewModel.load(refreshing = true)
            homeViewModel.refresh()
        }
        val isOwner = state.info?.ownerId?.let { it.isNotEmpty() && it == userId } == true
        val loaded = homeState.loaded
        val music = homeState.entertainment.music

        FeatureScaffold(
            title = guild.name.ifEmpty { state.info?.name.orEmpty() },
            subtitle = (state.memberStats?.total ?: state.info?.memberCount)?.let { "${it.formatted()} members" },
            onBack = onBack,
            loadState = loadState,
            onRefresh = onRefresh,
            onRetry = { viewModel.load() },
            actions = {
                ImmersiveIconButton(
                    onClick = onOpenFeatureBrowser,
                    icon = Icons.Default.Apps,
                    contentDescription = "All features",
                    fraction = immersive.containerFraction,
                )
            },
            scrollable = false,
            immersive = immersive,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Refresh") {
                                onRefresh()
                                true
                            },
                        )
                    },
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "hero", contentType = "hero") {
                    GuildHero(
                        guild = guild,
                        info = state.info,
                        memberStats = state.memberStats,
                        flow = flow,
                        bot = state.bot,
                        profile = homeState.profile,
                        isOwner = isOwner,
                        metrics = heroMetrics,
                        scrollOffset = scrollOffset,
                        reduced = reduced,
                        roles = roles,
                    )
                }

                if (loadState.showsError) {
                    homeItem("error", entered, reduced, topGap = 16.dp) {
                        ErrorState(loadState.error.orEmpty(), onRetry = { viewModel.load() })
                    }
                }

                homeItem("shortcuts", entered, reduced, topGap = 20.dp) {
                    ShortcutStrip(onOpenFeature = onOpenFeature, reduced = reduced)
                }

                if (music?.currentTrack != null) {
                    homeItem("nowPlaying", entered, reduced) {
                        NowPlayingCard(music = music, reduced = reduced, onOpen = { onOpenFeature("music") })
                    }
                }

                homeItem("pulse", entered, reduced) {
                    PulseGrid(
                        info = state.info,
                        memberStats = state.memberStats,
                        roleStats = state.roleStats,
                        flow = flow,
                        community = homeState.community,
                        communityLoaded = HomeSection.COMMUNITY in loaded,
                        security = homeState.security,
                        securityLoaded = HomeSection.SECURITY in loaded,
                        roles = roles,
                        reduced = reduced,
                        entered = entered,
                        onOpenFeature = onOpenFeature,
                    )
                }

                homeItem("flow", entered, reduced) {
                    MemberFlowCard(
                        joinStats = state.joinStats,
                        leaveStats = state.leaveStats,
                        flow = flow,
                        loading = !loadState.hasLoaded && state.joinStats == null && state.leaveStats == null,
                        roles = roles,
                        reduced = reduced,
                        onOpenDetails = { onOpenFeature("serverstats") },
                    )
                }

                homeItem("band-community", entered, reduced, inset = false) {
                    CommunityBand(
                        overview = state,
                        community = homeState.community,
                        loaded = HomeSection.COMMUNITY in loaded,
                        roles = roles,
                        reduced = reduced,
                        onOpenFeature = onOpenFeature,
                    )
                }

                val entertainmentLoaded = HomeSection.ENTERTAINMENT in loaded
                if (!entertainmentLoaded || homeState.entertainment.hasContent()) {
                    homeItem("band-entertainment", entered, reduced, inset = false) {
                        EntertainmentBand(
                            data = homeState.entertainment,
                            loaded = entertainmentLoaded,
                            roles = roles,
                            onOpenFeature = onOpenFeature,
                        )
                    }
                }

                homeItem("band-automation", entered, reduced, inset = false) {
                    AutomationBand(
                        overview = state,
                        actions = homeState.actions,
                        settings = homeState.settings,
                        actionsLoaded = HomeSection.ACTIONS in loaded,
                        settingsLoaded = HomeSection.SETTINGS in loaded,
                        roles = roles,
                        onEnsureLoaded = homeViewModel::ensureLoaded,
                        onOpenFeature = onOpenFeature,
                    )
                }

                homeItem("band-safety", entered, reduced, inset = false) {
                    SafetyBand(
                        security = homeState.security,
                        loaded = HomeSection.SECURITY in loaded,
                        roles = roles,
                        reduced = reduced,
                        onOpenFeature = onOpenFeature,
                    )
                }

                if (setup.isNotEmpty()) {
                    homeItem("setup", entered, reduced, inset = false) {
                        SetupRail(entries = setup, onOpenFeature = onOpenFeature)
                    }
                }

                if (state.bot != null || homeState.profile != null || !loadState.hasLoaded) {
                    homeItem("bot", entered, reduced) {
                        BotCard(
                            bot = state.bot,
                            profile = homeState.profile,
                            lastUpdated = state.lastUpdated,
                            loading = !loadState.hasLoaded && state.bot == null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adds one top-level home block.
 *
 * Spacing is explicit per item rather than a list arrangement so the hero can
 * sit flush. Each block is capped at 840dp and centred on wide screens, rises
 * in once, and fades when it appears or disappears. With [inset] false the
 * block handles its own horizontal padding, so rails can run edge to edge.
 */
private fun LazyListScope.homeItem(
    key: String,
    entered: EnteredKeys,
    reduced: Boolean,
    contentType: String = key,
    topGap: Dp = HomeDimens.sectionGap,
    inset: Boolean = true,
    content: @Composable () -> Unit,
) {
    item(key = key, contentType = contentType) {
        Box(
            modifier = Modifier
                .animateItem(
                    fadeInSpec = spring(stiffness = 1600f),
                    placementSpec = if (reduced) null else spring<IntOffset>(dampingRatio = 0.9f, stiffness = 700f),
                    fadeOutSpec = spring(stiffness = 1600f),
                )
                .fillMaxWidth()
                .padding(top = topGap),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .riseOnce(key, entered)
                    .then(if (inset) Modifier.padding(horizontal = HomeDimens.inset) else Modifier),
            ) {
                content()
            }
        }
    }
}

/** Formats a count with thousands separators. */
fun Int.formatted(): String = "%,d".format(this)

/** Formats a long count with thousands separators. */
fun Long.formatted(): String = "%,d".format(this)
