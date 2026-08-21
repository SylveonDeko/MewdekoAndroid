package dev.mewdeko.mobile.feature.embed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * The standalone embed builder.
 *
 * The composer itself is the same component feature pages open inline, so a
 * message built here, the saved library, and the send options behave
 * identically wherever the builder is reached from.
 */
@Composable
fun EmbedBuilderScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: EmbedLibraryViewModel = hiltViewModel(),
) {
    var message by remember { mutableStateOf(EmbedMessage()) }
    val status by viewModel.status.collectAsStateWithLifecycle()

    FeatureScaffold(
        title = "Embeds",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = LoadState(hasLoaded = true),
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = viewModel::refresh,
    ) {
        SectionCard {
            SectionCardHeader("Compose", Icons.Default.ViewAgenda)
            Text(
                text = "Build a message with embeds and components, save it for reuse, or send " +
                    "it straight to a channel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbedMessageEditor(message = message, onMessageChange = { message = it })
        }
    }
}
