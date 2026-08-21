package dev.mewdeko.mobile.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.feature.administration.AdministrationScreen
import dev.mewdeko.mobile.feature.afk.AfkScreen
import dev.mewdeko.mobile.feature.birthday.BirthdayScreen
import dev.mewdeko.mobile.feature.chatsaver.ChatSaverScreen
import dev.mewdeko.mobile.feature.chattriggers.ChatTriggersScreen
import dev.mewdeko.mobile.feature.confessions.ConfessionsScreen
import dev.mewdeko.mobile.feature.counting.CountingScreen
import dev.mewdeko.mobile.feature.customvoice.CustomVoiceScreen
import dev.mewdeko.mobile.feature.embed.EmbedBuilderScreen
import dev.mewdeko.mobile.feature.forms.FormsScreen
import dev.mewdeko.mobile.feature.feeds.FeedsScreen
import dev.mewdeko.mobile.feature.giveaways.GiveawaysScreen
import dev.mewdeko.mobile.feature.highlights.HighlightsScreen
import dev.mewdeko.mobile.feature.invites.InvitesScreen
import dev.mewdeko.mobile.feature.multigreets.MultiGreetsScreen
import dev.mewdeko.mobile.feature.logging.LoggingScreen
import dev.mewdeko.mobile.feature.messagestats.MessageStatsScreen
import dev.mewdeko.mobile.feature.repeaters.RepeatersScreen
import dev.mewdeko.mobile.feature.reputation.ReputationScreen
import dev.mewdeko.mobile.feature.rolegreets.RoleGreetsScreen
import dev.mewdeko.mobile.feature.patreon.PatreonScreen
import dev.mewdeko.mobile.feature.performance.PerformanceScreen
import dev.mewdeko.mobile.feature.minecraft.MinecraftScreen
import dev.mewdeko.mobile.feature.moderation.ModerationScreen
import dev.mewdeko.mobile.feature.music.MusicScreen
import dev.mewdeko.mobile.feature.rolestates.RoleStatesScreen
import dev.mewdeko.mobile.feature.settings.SettingsScreen
import dev.mewdeko.mobile.feature.starboard.StarboardScreen
import dev.mewdeko.mobile.feature.statchannels.StatChannelsScreen
import dev.mewdeko.mobile.feature.statusroles.StatusRolesScreen
import dev.mewdeko.mobile.feature.suggestions.SuggestionsScreen
import dev.mewdeko.mobile.feature.streams.StreamsScreen
import dev.mewdeko.mobile.feature.tickets.TicketsScreen
import dev.mewdeko.mobile.feature.todo.TodoScreen
import dev.mewdeko.mobile.feature.votes.VotesScreen
import dev.mewdeko.mobile.feature.xp.XpScreen
import dev.mewdeko.mobile.feature.palette.GuildPaletteViewModel

/**
 * Routes a catalog id to its native implementation. Every id in
 * [NavigationCatalog] has a branch here; adding a feature means adding one
 * more.
 */
@Composable
fun FeatureRoute(
    featureId: String,
    guild: GuildRouteArgs,
    userId: String,
    onBack: () -> Unit,
    paletteViewModel: GuildPaletteViewModel = hiltViewModel(),
) {
    val palette by paletteViewModel.palette.collectAsStateWithLifecycle(GuildPalette.Default)

    MewdekoTheme(palette = palette) {
        when (featureId) {
            "afk" -> AfkScreen(guild = guild, onBack = onBack)
            "birthday" -> BirthdayScreen(guild = guild, onBack = onBack)
            "moderation" -> ModerationScreen(guild = guild, onBack = onBack)
            "logging" -> LoggingScreen(guild = guild, onBack = onBack)
            "settings" -> SettingsScreen(guild = guild, onBack = onBack)
            "performance" -> PerformanceScreen(guild = guild, onBack = onBack)
            "feeds" -> FeedsScreen(guild = guild, onBack = onBack)
            "chatsaver" -> ChatSaverScreen(guild = guild, onBack = onBack)
            "rolegreets" -> RoleGreetsScreen(guild = guild, onBack = onBack)
            "messagestats" -> MessageStatsScreen(guild = guild, onBack = onBack)
            "invites" -> InvitesScreen(guild = guild, onBack = onBack)
            "giveaways" -> GiveawaysScreen(guild = guild, onBack = onBack)
            "multigreets" -> MultiGreetsScreen(guild = guild, onBack = onBack)
            "highlights" -> HighlightsScreen(guild = guild, onBack = onBack)
            "statusroles" -> StatusRolesScreen(guild = guild, onBack = onBack)
            "votes" -> VotesScreen(guild = guild, onBack = onBack)
            "statchannels" -> StatChannelsScreen(guild = guild, onBack = onBack)
            "rolestates" -> RoleStatesScreen(guild = guild, onBack = onBack)
            "confessions" -> ConfessionsScreen(guild = guild, onBack = onBack)
            "chat-triggers" -> ChatTriggersScreen(guild = guild, onBack = onBack)
            "repeaters" -> RepeatersScreen(guild = guild, onBack = onBack)
            "streams" -> StreamsScreen(guild = guild, onBack = onBack)
            "reputation" -> ReputationScreen(guild = guild, onBack = onBack)
            "customvoice" -> CustomVoiceScreen(guild = guild, onBack = onBack)
            "patreon" -> PatreonScreen(guild = guild, onBack = onBack)
            "suggestions" -> SuggestionsScreen(guild = guild, onBack = onBack)
            "starboard" -> StarboardScreen(guild = guild, onBack = onBack)
            "counting" -> CountingScreen(guild = guild, onBack = onBack)
            "todo" -> TodoScreen(guild = guild, onBack = onBack)
            "xp" -> XpScreen(guild = guild, onBack = onBack)
            "embedbuilder" -> EmbedBuilderScreen(guild = guild, onBack = onBack)
            "minecraft" -> MinecraftScreen(guild = guild, onBack = onBack)
            "music" -> MusicScreen(guild = guild, onBack = onBack)
            "forms" -> FormsScreen(guild = guild, onBack = onBack)
            "tickets" -> TicketsScreen(guild = guild, onBack = onBack)
            "administration" -> AdministrationScreen(guild = guild, onBack = onBack)

            /**
             * Every catalog feature above has a real screen. This only fires
             * for a route id that is not in the catalog at all, which means a
             * stale deep link.
             */
            else -> FeatureScaffold(
                title = "Unknown feature",
                onBack = onBack,
                loadState = LoadState(hasLoaded = true),
                scrollable = false,
            ) {
                EmptyState(
                    message = "\"$featureId\" is not a feature this build knows about.",
                    icon = Icons.Default.HelpOutline,
                )
            }
        }
    }
}
