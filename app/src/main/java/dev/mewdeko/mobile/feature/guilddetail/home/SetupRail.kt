package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.dashedBorder
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.feature.guilddetail.GuildOverviewState
import dev.mewdeko.mobile.navigation.NavigationCatalog

/** A feature with nothing to show yet, and why. */
@Immutable
data class SetupEntry(val featureId: String, val reason: String)

/**
 * The features that have nothing to show, in a fixed order.
 *
 * An entry is only added once the data that owns it has loaded, so a slow
 * request never flashes a feature into the rail and back out.
 */
fun setupEntries(overview: GuildOverviewState, home: GuildHomeState): List<SetupEntry> = buildList {
    val community = home.community
    if (HomeSection.COMMUNITY in home.loaded) {
        if (community.xpTop.isEmpty()) add(SetupEntry("xp", "No XP earned yet"))
        if (community.messages?.enabled != true) add(SetupEntry("messagestats", "Tracking is off"))
        if (community.birthdaysToday.isEmpty() && community.birthdaysUpcoming.isEmpty()) {
            add(SetupEntry("birthday", "None this week"))
        }
        if (community.tickets == null) add(SetupEntry("tickets", "Not configured"))
        if (community.highlights.isEmpty()) add(SetupEntry("starboard", "No starred posts yet"))
        if (community.forms.isNullOrEmpty()) add(SetupEntry("forms", "No active forms"))
        if (community.counting.isNullOrEmpty()) add(SetupEntry("counting", "No counting channels"))
        if (community.patreonConnected != true) add(SetupEntry("patreon", "Not connected"))
    }
    if (HomeSection.ENTERTAINMENT in home.loaded) {
        val entertainment = home.entertainment
        if (entertainment.giveaways.isNullOrEmpty()) add(SetupEntry("giveaways", "None running"))
        if ((entertainment.customVoiceChannels ?: 0) == 0) add(SetupEntry("customvoice", "No voice rooms"))
    }
    if (overview.roleGreets?.isEmpty() == true) add(SetupEntry("rolegreets", "None configured"))
    if (HomeSection.ACTIONS in home.loaded) {
        if ((home.actions.multiGreets ?: 0) == 0) add(SetupEntry("multigreets", "None configured"))
        if ((home.actions.repeaters ?: 0) == 0) add(SetupEntry("repeaters", "None configured"))
    }
    if (HomeSection.SECURITY in home.loaded) {
        val protection = home.security.protection
        if (protection == null || protection.activeCount == 0) {
            add(SetupEntry("administration", "No protections on"))
        }
    }
}

/**
 * "More to set up": dashed chips for features with nothing to show yet,
 * each still one tap from its page. A horizontal rail on phones and a
 * vertical list at large font scales.
 */
@Composable
fun SetupRail(entries: List<SetupEntry>, onOpenFeature: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.homeInset()) {
            Text(
                text = "More to set up",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Nothing to show for these yet. Tap one to open it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLargeFont()) {
            Column(
                modifier = Modifier.homeInset(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEach { entry ->
                    SetupChip(
                        entry = entry,
                        fill = true,
                        onOpenFeature = onOpenFeature,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = HomeDimens.inset),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.featureId }) { entry ->
                    SetupChip(entry = entry, fill = false, onOpenFeature = onOpenFeature)
                }
            }
        }
    }
}

/**
 * One dashed "set up" chip for a feature, the dashboard's exact setup chip:
 * primary at the `10` tint, a dashed primary border at the `40` tint, and the
 * label and icon in solid primary.
 */
@Composable
fun SetupChip(
    entry: SetupEntry,
    fill: Boolean,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = NavigationCatalog.byId[entry.featureId] ?: return
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Surface(
        onClick = { onOpenFeature(entry.featureId) },
        shape = shape,
        color = primary.copy(alpha = DashAlpha.Hex10),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .dashedBorder(width = 1.dp, color = primary.copy(alpha = DashAlpha.Hex40), cornerRadius = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.label}, ${entry.reason}. Set up"
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(item.icon, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Column(modifier = if (fill) Modifier.weight(1f) else Modifier) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = readableInk(primary, MaterialTheme.colorScheme.background),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.reason,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Default.AddCircleOutline,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
