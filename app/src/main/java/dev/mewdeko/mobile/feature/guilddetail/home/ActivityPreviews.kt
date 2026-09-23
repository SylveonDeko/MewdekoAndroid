package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.RoleGreet
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.ScallopShape
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.feature.giveaways.GiveawayRecord
import dev.mewdeko.mobile.feature.guilddetail.GuildOverviewState
import dev.mewdeko.mobile.feature.guilddetail.formatted
import dev.mewdeko.mobile.util.relativeToNow
import java.time.Instant

/** Whether the entertainment band has anything to show once loaded. */
fun EntertainmentData.hasContent(): Boolean =
    !giveaways.isNullOrEmpty() || (customVoiceChannels ?: 0) > 0

/**
 * The Entertainment chapter: running giveaways and custom voice rooms.
 *
 * The caller removes the band entirely once it has loaded with nothing to
 * show; until then it renders as a skeleton.
 */
@Composable
fun EntertainmentBand(
    data: EntertainmentData,
    loaded: Boolean,
    roles: HomeRoles,
    onOpenFeature: (String) -> Unit,
) {
    val role = roles.entertainment
    val badge = ScallopShape(8, 0.10f)
    if (!loaded) {
        SkeletonBand("Entertainment", Icons.Default.Celebration, role, badge, "giveaways", onOpenFeature)
        return
    }

    val active = data.giveaways.orEmpty()
    val voice = data.customVoiceChannels ?: 0
    val chips = buildList {
        if (voice > 0) {
            add(
                BandChip(
                    icon = Icons.Default.Mic,
                    value = voice.formatted(),
                    label = "Voice rooms",
                    featureId = "customvoice",
                )
            )
        }
        if (active.isNotEmpty()) {
            add(
                BandChip(
                    icon = Icons.Default.EmojiEvents,
                    value = active.sumOf { it.winners }.formatted(),
                    label = "Winners to draw",
                    featureId = "giveaways",
                )
            )
        }
    }

    CategoryBand(
        title = "Entertainment",
        icon = Icons.Default.Celebration,
        role = role,
        badgeShape = badge,
        seeAllId = "giveaways",
        headline = if (active.isNotEmpty()) active.size.toLong() else voice.toLong(),
        descriptor = if (active.isNotEmpty()) "giveaways running" else "voice rooms open",
        headlineLoading = false,
        onOpenFeature = onOpenFeature,
        chips = chips,
        chipsLoading = false,
        previews = {
            if (active.isNotEmpty()) {
                GiveawayListCard(
                    active = active,
                    onOpen = { onOpenFeature("giveaways") },
                    modifier = Modifier.homeInset(),
                )
            }
        },
    )
}

/**
 * Up to three running giveaways, soonest ending first, on the standard card
 * wash with a secondary hairline and solid secondary icons.
 */
@Composable
fun GiveawayListCard(active: List<GiveawayRecord>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val sorted = active.sortedBy { it.`when` ?: Instant.MAX }
    val now = Instant.now()
    val secondary = MaterialTheme.colorScheme.secondary
    GuildCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        border = guildBorder(secondary),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            sorted.take(3).forEach { giveaway ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = giveaway.item?.takeIf { it.isNotBlank() } ?: "Giveaway",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detail = buildString {
                            append(giveaway.winners.formatted())
                            append(if (giveaway.winners == 1) " winner" else " winners")
                            giveaway.`when`?.takeIf { it.isAfter(now) }?.let {
                                append(" · ends ").append(it.relativeToNow(now))
                            }
                        }
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (sorted.size > 3) {
                Text(
                    text = "+${sorted.size - 3} more",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * The Automation chapter: role greets, role states, greets, repeaters and
 * role assignment.
 *
 * Its action and settings data load the first time the band composes.
 */
@Composable
fun AutomationBand(
    overview: GuildOverviewState,
    actions: ActionsData,
    settings: SettingsData,
    actionsLoaded: Boolean,
    settingsLoaded: Boolean,
    roles: HomeRoles,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) {
        onEnsureLoaded(HomeSection.ACTIONS)
        onEnsureLoaded(HomeSection.SETTINGS)
    }
    val role = roles.automation
    val roleStats = overview.roleStats
    val chips = buildList {
        roleStats?.let { stats ->
            if (stats.totalRoles > 0) {
                add(
                    BandChip(
                        icon = Icons.AutoMirrored.Filled.Label,
                        value = stats.totalRoles.formatted(),
                        label = "Roles",
                        subtitle = "${stats.savedRoles.formatted()} saved",
                        featureId = "rolestates",
                    )
                )
            }
            if (stats.roleStates > 0) {
                add(
                    BandChip(
                        icon = Icons.Default.Sync,
                        value = stats.roleStates.formatted(),
                        label = "Role states",
                        featureId = "rolestates",
                    )
                )
            }
        }
        actions.multiGreets?.takeIf { it > 0 }?.let {
            add(BandChip(icon = Icons.Default.WavingHand, value = it.formatted(), label = "Greets", featureId = "multigreets"))
        }
        actions.repeaters?.takeIf { it > 0 }?.let {
            add(BandChip(icon = Icons.Default.Repeat, value = it.formatted(), label = "Repeaters", featureId = "repeaters"))
        }
        val humans = settings.autoAssignHumans ?: 0
        val bots = settings.autoAssignBots ?: 0
        if (humans + bots > 0) {
            add(
                BandChip(
                    icon = Icons.Default.PersonAdd,
                    value = humans.formatted(),
                    label = "Join roles",
                    subtitle = if (bots > 0) "+${bots.formatted()} for bots" else null,
                    featureId = "administration",
                )
            )
        }
        settings.selfAssignable?.takeIf { it > 0 }?.let {
            add(BandChip(icon = Icons.Default.TouchApp, value = it.formatted(), label = "Self-assign", featureId = "administration"))
        }
    }

    CategoryBand(
        title = "Automation",
        icon = Icons.Default.Bolt,
        role = role,
        badgeShape = ScallopShape(6, 0.12f),
        seeAllId = "rolegreets",
        headline = roleStats?.roleGreets?.toLong(),
        descriptor = "role greets active",
        headlineLoading = roleStats == null,
        onOpenFeature = onOpenFeature,
        chips = chips,
        chipsLoading = !actionsLoaded || !settingsLoaded || roleStats == null,
        previews = {
            val greets = overview.roleGreets.orEmpty()
            if (greets.isNotEmpty()) {
                RoleGreetListCard(
                    greets = greets,
                    roleNames = overview.roleNames,
                    roles = roles,
                    onOpen = { onOpenFeature("rolegreets") },
                    modifier = Modifier.homeInset(),
                )
            }
        },
    )
}

/**
 * Up to three role greets, active first, with role names and a paused
 * marker, on the standard card wash with an automation hairline.
 */
@Composable
fun RoleGreetListCard(
    greets: List<RoleGreet>,
    roleNames: Map<String, String>,
    roles: HomeRoles,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val role = roles.automation
    val ordered = greets.sortedBy { it.disabled == true }
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    GuildCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        border = guildBorder(role.color),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ordered.take(3).forEach { greet ->
                val paused = greet.disabled == true
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dot = if (paused) outline else role.color
                        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(dot) }
                        Text(
                            text = greet.roleId?.let { roleNames[it] } ?: "Deleted role",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (paused) {
                            Surface(
                                shape = CircleShape,
                                color = role.color.copy(alpha = DashAlpha.Hex20),
                                contentColor = role.onContainer,
                            ) {
                                Text(
                                    text = "Paused",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    greet.message?.trim()?.takeIf { it.isNotBlank() }?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (ordered.size > 3) {
                Text(
                    text = "+${ordered.size - 3} more",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
