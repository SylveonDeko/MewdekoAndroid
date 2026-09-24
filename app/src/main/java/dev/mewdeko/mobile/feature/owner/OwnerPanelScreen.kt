package dev.mewdeko.mobile.feature.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.guildWash

/** Width at which the tool grid splits into two columns, the dashboard's `sm` breakpoint. */
private val TwoColumnWidth = 600.dp

/**
 * The owner panel home, mirroring the dashboard's `/owner` page: a hero
 * naming the selected bot, then one card per owner tool with its icon,
 * label, and description. Nothing here is scoped to a server.
 *
 * Rendered inside [OwnerGate], so it only composes for a confirmed owner.
 */
@Composable
fun OwnerPanelScreen(
    onBack: () -> Unit,
    onOpenPage: (OwnerPage) -> Unit,
    viewModel: OwnerAccessViewModel = hiltViewModel(),
) {
    val botName by viewModel.botName.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Owner Panel",
        subtitle = botName,
        onBack = onBack,
        loadState = LoadState(hasLoaded = true),
    ) {
        OwnerHero(botName = botName)
        OwnerToolGrid(pages = OwnerPage.panelOrder, onOpenPage = onOpenPage)
    }
}

/**
 * The panel header: a crown badge, the title, and which bot the tools act on.
 */
@Composable
private fun OwnerHero(botName: String?) {
    val palette = LocalGuildPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val badgeShape = RoundedCornerShape(12.dp)
    GuildCard(
        modifier = Modifier.fillMaxWidth(),
        wash = guildWash(strength = 1.25f),
        border = guildBorder(primary.copy(alpha = DashAlpha.Hex30)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(primary.copy(alpha = DashAlpha.Hex20), badgeShape)
                    .border(1.dp, primary.copy(alpha = DashAlpha.Hex30), badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Owner Panel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Fleet and host tools for ${botName ?: "the selected bot"}. " +
                        "Nothing here is scoped to a server.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted.color,
                )
            }
        }
    }
}

/**
 * The tool cards: one column on phones, two from [TwoColumnWidth] up, as the
 * dashboard's grid does.
 */
@Composable
private fun OwnerToolGrid(pages: List<OwnerPage>, onOpenPage: (OwnerPage) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= TwoColumnWidth) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pages.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { page ->
                        OwnerToolCard(
                            page = page,
                            onClick = { onOpenPage(page) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One owner tool: its icon badge, label, description, and a chevron. */
@Composable
private fun OwnerToolCard(page: OwnerPage, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = LocalGuildPalette.current.muted.color
    val badgeShape = RoundedCornerShape(12.dp)
    GuildCard(
        modifier = modifier.fillMaxWidth(),
        border = guildBorder(primary.copy(alpha = DashAlpha.Hex20)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(primary.copy(alpha = DashAlpha.Hex15), badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(page.icon, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = page.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = muted,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
        }
    }
}
