package dev.mewdeko.mobile.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.theme.GuildColorViewModel
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.theme.NavBarLabelStyle
import dev.mewdeko.mobile.core.ui.Avatar

/**
 * The signed-in shell.
 *
 * The bottom bar is context-aware: at the top level it switches between the
 * server list and the account tab, and once a guild is open it becomes that
 * guild's quick-access bar.
 */
@Composable
fun MainShell(
    user: MobileUser,
    instance: MobileInstance?,
    onSwitchInstance: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
    colorViewModel: GuildColorViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var moreIsOpen by remember { mutableStateOf(false) }

    val guild = backStackEntry?.guildArgsOrNull()
    val activeFeatureId = backStackEntry?.arguments?.getString("featureId")
    val isOnGuildHome = currentRoute == Routes.GUILD_DETAIL
    val showsTopLevelBar = currentRoute == Routes.GUILD_LIST || currentRoute == Routes.ACCOUNT
    val palette by colorViewModel.palette.collectAsStateWithLifecycle()

    MewdekoTheme(palette = palette) {
        Scaffold(
            bottomBar = {
                when {
                    showsTopLevelBar -> TopLevelBar(
                        currentRoute = currentRoute,
                        user = user,
                        onSelect = navController::switchTab,
                    )

                    guild != null -> GuildNavBar(
                        activeFeatureId = activeFeatureId,
                        isOnGuildHome = isOnGuildHome,
                        moreIsOpen = moreIsOpen,
                        onSelectTab = { featureId ->
                            navController.openGuildTab(guild, featureId)
                        },
                        onOpenMore = { moreIsOpen = true },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            /*
             * Each destination hosts its own Scaffold and top app bar, which apply
             * the status bar inset themselves. Consuming system bars here as well
             * would push every screen down by that inset twice.
             */
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            MewdekoNavHost(
                navController = navController,
                user = user,
                instance = instance,
                onSwitchInstance = onSwitchInstance,
                onSwitchServer = onSwitchServer,
                onSignOut = onSignOut,
                onDeleteData = onDeleteData,
                /*
                 * Reserve the bar's space, then mark it consumed. Each destination
                 * hosts its own Scaffold defaulting to the system bar insets, and
                 * without this it would add the navigation bar inset a second time
                 * underneath a bar that already accounts for it.
                 */
                modifier = Modifier
                    .padding(bottom = padding.calculateBottomPadding())
                    .consumeWindowInsets(
                        PaddingValues(bottom = padding.calculateBottomPadding())
                    ),
            )
        }

        if (moreIsOpen && guild != null) {
            FeatureCatalogSheet(
                activeFeatureId = activeFeatureId,
                onSelect = { featureId ->
                    moreIsOpen = false
                    navController.openGuildTab(guild, featureId)
                },
                onDismiss = { moreIsOpen = false },
            )
        }
    }
}

@Composable
private fun TopLevelBar(
    currentRoute: String?,
    user: MobileUser,
    onSelect: (String) -> Unit,
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.GUILD_LIST,
            onClick = { onSelect(Routes.GUILD_LIST) },
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Servers", style = NavBarLabelStyle) },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ACCOUNT,
            onClick = { onSelect(Routes.ACCOUNT) },
            icon = {
                Avatar(
                    url = user.avatarUrl,
                    contentDescription = null,
                    size = 24,
                    fallbackIcon = Icons.Default.Person,
                )
            },
            label = { Text("Me", style = NavBarLabelStyle) },
        )
    }
}

/**
 * Moves to one of the guild's quick-access destinations.
 *
 * Every feature page shares one destination id and differs only by argument,
 * so `launchSingleTop` would treat a switch between two of them as already
 * current and drop it. Popping back to the guild first keeps each tab a single
 * entry above home, and makes back from any tab land on the overview.
 */
private fun NavHostController.openGuildTab(guild: GuildRouteArgs, featureId: String?) {
    popBackStack(Routes.GUILD_DETAIL, inclusive = false)
    if (featureId == null) return
    navigate(Routes.feature(guild.id, guild.name, guild.iconUrl, featureId))
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The guild carried by this entry, when it is a guild-scoped destination. */
private fun NavBackStackEntry.guildArgsOrNull(): GuildRouteArgs? {
    val id = arguments?.getString("guildId") ?: return null
    return GuildRouteArgs.from(
        id = id,
        name = arguments?.getString("guildName"),
        icon = arguments?.getString("guildIcon"),
    )
}
