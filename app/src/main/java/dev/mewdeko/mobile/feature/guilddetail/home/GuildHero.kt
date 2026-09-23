package dev.mewdeko.mobile.feature.guilddetail.home

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.mewdeko.mobile.core.model.BotStatus
import dev.mewdeko.mobile.core.model.GuildInfo
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ScallopShape
import dev.mewdeko.mobile.feature.guilddetail.GuildMemberStats
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** "Since" chip date format, month and year in the device zone. */
private val SinceFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())

/**
 * Hero measurements the top app bar reads while scrolling.
 *
 * Written only when a value moves by more than half a pixel, so layout passes
 * that do not change anything never invalidate the bar.
 */
@Stable
class HeroMetrics {
    private val bannerBottom = mutableFloatStateOf(0f)
    private val nameBottom = mutableFloatStateOf(0f)

    /** Bottom edge of the banner, relative to the top of the hero. */
    var bannerBottomPx: Float
        get() = bannerBottom.floatValue
        set(value) {
            if (abs(value - bannerBottom.floatValue) > 0.5f) bannerBottom.floatValue = value
        }

    /** Bottom edge of the guild name, relative to the top of the hero. */
    var nameBottomPx: Float
        get() = nameBottom.floatValue
        set(value) {
            if (abs(value - nameBottom.floatValue) > 0.5f) nameBottom.floatValue = value
        }
}

/** Holds the hero root's coordinates so children can be measured against it. */
private class HeroAnchor {
    /** The hero root's latest coordinates. */
    var coordinates: LayoutCoordinates? = null
}

/**
 * The edge-to-edge guild identity block that opens the home.
 *
 * The banner (or a palette gradient with the blurred icon when there is none)
 * runs under the status bar and the transparent top app bar, then melts into
 * the page. The icon overlaps the seam, followed by the name, the member line,
 * the description and a row of status chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuildHero(
    guild: GuildRouteArgs,
    info: GuildInfo?,
    memberStats: GuildMemberStats?,
    flow: List<HomeSeries.FlowDay>,
    bot: BotStatus?,
    profile: BotGuildProfile?,
    isOwner: Boolean,
    metrics: HeroMetrics,
    scrollOffset: () -> Int,
    reduced: Boolean,
    roles: HomeRoles,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bannerHeight = statusTop + 64.dp + 132.dp
    val anchor = remember { HeroAnchor() }
    val name = guild.name.ifEmpty { info?.name.orEmpty() }
    val iconUrl = guild.iconUrl?.takeIf { it.isNotBlank() } ?: info?.iconUrl
    val large = LocalDensity.current.fontScale >= 1.5f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { anchor.coordinates = it },
    ) {
        HeroBackdrop(
            iconUrl = iconUrl,
            bannerUrl = info?.bannerUrl?.takeIf { it.isNotBlank() },
            bannerHeight = bannerHeight,
            statusTop = statusTop,
            metrics = metrics,
            scrollOffset = scrollOffset,
            reduced = reduced,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = bannerHeight - 44.dp)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroIcon(
                url = iconUrl,
                name = name,
                metrics = metrics,
                scrollOffset = scrollOffset,
                reduced = reduced,
            )

            Text(
                text = name,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (large) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .semantics { heading() }
                    .onGloballyPositioned { coords ->
                        val root = anchor.coordinates?.takeIf { it.isAttached } ?: return@onGloballyPositioned
                        if (!coords.isAttached) return@onGloballyPositioned
                        metrics.nameBottomPx =
                            root.localPositionOf(coords, Offset.Zero).y + coords.size.height
                    },
            )

            MemberLine(
                total = (memberStats?.total ?: info?.memberCount)?.toLong(),
                flow = flow,
                roles = roles,
            )

            info?.description?.takeIf { it.isNotBlank() }?.let { description ->
                HeroDescription(description, reduced)
            }

            HeroChips(
                info = info,
                bot = bot,
                profile = profile,
                isOwner = isOwner,
                roles = roles,
                reduced = reduced,
            )
        }
    }
}

/**
 * The banner layer stack: palette field, blurred icon, banner image, then a
 * top scrim for the bar icons and a bottom scrim into the page background.
 */
@Composable
private fun HeroBackdrop(
    iconUrl: String?,
    bannerUrl: String?,
    bannerHeight: Dp,
    statusTop: Dp,
    metrics: HeroMetrics,
    scrollOffset: () -> Int,
    reduced: Boolean,
) {
    val context = LocalContext.current
    val background = MaterialTheme.colorScheme.background
    val gradient = LocalGuildPalette.current.gradient

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .clipToBounds()
            .onSizeChanged { metrics.bannerBottomPx = it.height.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { translationY = if (reduced) 0f else scrollOffset() * 0.5f },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.linearGradient(gradient)),
            )
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f,
                    modifier = Modifier
                        .matchParentSize()
                        .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(48.dp) else Modifier),
                )
            }
            if (bannerUrl != null) {
                AsyncImage(
                    model = remember(bannerUrl) {
                        ImageRequest.Builder(context).data(bannerUrl).crossfade(350).build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusTop + 80.dp)
                .background(
                    Brush.verticalGradient(listOf(background.copy(alpha = 0.6f), Color.Transparent)),
                ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        1f to background,
                    ),
                ),
        )
    }
}

/**
 * The guild icon in a scalloped "cookie" frame that overlaps the banner seam
 * and shrinks slightly as the page scrolls.
 */
@Composable
private fun HeroIcon(
    url: String?,
    name: String,
    metrics: HeroMetrics,
    scrollOffset: () -> Int,
    reduced: Boolean,
) {
    val cookie = ScallopShape(12, 0.06f)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                if (!reduced) {
                    val banner = metrics.bannerBottomPx
                    val fraction = if (banner > 0f) (scrollOffset() / banner).coerceIn(0f, 1f) else 0f
                    val scale = 1f - 0.25f * fraction
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 1f)
                }
            }
            .shadow(8.dp, CircleShape, ambientColor = primary, spotColor = primary)
            .clip(cookie)
            .background(MaterialTheme.colorScheme.background, cookie),
        contentAlignment = Alignment.Center,
    ) {
        Avatar(
            url = url,
            contentDescription = null,
            size = 88,
            shape = cookie,
            fallbackText = name,
        )
    }
}

/** Member count with the net change over the flow period, read as one phrase. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberLine(total: Long?, flow: List<HomeSeries.FlowDay>, roles: HomeRoles) {
    FlowRow(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterVertically),
            verticalAlignment = Alignment.Bottom,
        ) {
            AnimatedCount(
                value = total,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                compact = false,
            )
            Text(
                text = " members",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (flow.isNotEmpty()) {
            NetChip(
                net = flow.sumOf { it.net },
                days = flow.size,
                roles = roles,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** Net member change as a tonal pill with a direction arrow. */
@Composable
fun NetChip(net: Int, days: Int, roles: HomeRoles, modifier: Modifier = Modifier) {
    val role = if (net >= 0) roles.community else roles.safety
    val icon = when {
        net > 0 -> Icons.AutoMirrored.Filled.TrendingUp
        net < 0 -> Icons.AutoMirrored.Filled.TrendingDown
        else -> Icons.AutoMirrored.Filled.TrendingFlat
    }
    Surface(
        shape = CircleShape,
        color = role.container,
        contentColor = role.onContainer,
        modifier = modifier.semantics {
            contentDescription = "Net ${HomeSeries.signed(net)} members over $days days"
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = "${HomeSeries.signed(net)} in ${days}d",
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
        }
    }
}

/** The guild description, clamped to three lines and expandable when it overflows. */
@Composable
private fun HeroDescription(description: String, reduced: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    val tappable = overflows || expanded
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (reduced) Modifier else Modifier.animateContentSize(HomeMotion.spatialDefault()))
            .then(
                if (tappable) {
                    Modifier.clickable(
                        onClickLabel = if (expanded) "Show less" else "Show full description",
                    ) { expanded = !expanded }
                } else {
                    Modifier
                }
            ),
    )
}

/** Role, boost, age and bot health chips under the description. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroChips(
    info: GuildInfo?,
    bot: BotStatus?,
    profile: BotGuildProfile?,
    isOwner: Boolean,
    roles: HomeRoles,
    reduced: Boolean,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (info == null) {
            Box(Modifier.size(88.dp, 32.dp).skeleton(true, CircleShape))
            Box(Modifier.size(64.dp, 32.dp).skeleton(true, CircleShape))
        } else {
            if (isOwner) {
                HeroChip(
                    icon = Icons.Default.WorkspacePremium,
                    text = "Owner",
                    container = roles.community.container,
                    content = roles.community.onContainer,
                )
            } else {
                HeroChip(
                    icon = Icons.Default.AdminPanelSettings,
                    text = "Admin",
                    container = roles.community.container,
                    content = roles.community.onContainer,
                )
            }
            if (info.premiumTier > 0) {
                HeroChip(
                    icon = Icons.Default.AutoAwesome,
                    text = "Boost tier ${info.premiumTier}",
                    container = roles.entertainment.container,
                    content = roles.entertainment.onContainer,
                )
            }
            if (info.createdAt != Instant.EPOCH) {
                HeroChip(
                    icon = Icons.Default.CalendarMonth,
                    text = "Since ${SinceFormat.format(info.createdAt)}",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (bot != null) {
            BotPulseChip(bot = bot, profile = profile, roles = roles, reduced = reduced)
        }
    }
}

/** A tonal status pill with a leading icon. */
@Composable
fun HeroChip(icon: ImageVector, text: String, container: Color, content: Color) {
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The bot's avatar, a status dot that breathes while online, and its gateway
 * latency. Informational only, so it is not clickable.
 */
@Composable
fun BotPulseChip(bot: BotStatus, profile: BotGuildProfile?, roles: HomeRoles, reduced: Boolean) {
    val online = bot.botStatus.equals("online", ignoreCase = true)
    val healthy = bot.botLatency < 250
    val role = if (healthy) roles.community else roles.safety
    val dotColor = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val breath: State<Float> = if (online && !reduced) {
        rememberInfiniteTransition(label = "botPulse").animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "botPulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }
    val status = if (online) "online" else bot.botStatus.ifBlank { "offline" }.lowercase()

    Surface(
        shape = CircleShape,
        color = role.container,
        contentColor = role.onContainer,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Bot $status, latency ${bot.botLatency} milliseconds"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                url = profile?.avatarUrl?.takeIf { it.isNotBlank() } ?: bot.botAvatar,
                contentDescription = null,
                size = 18,
                fallbackIcon = Icons.Default.SmartToy,
            )
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = dotColor, alpha = breath.value)
            }
            Text(
                text = "${bot.botLatency} ms",
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
        }
    }
}
