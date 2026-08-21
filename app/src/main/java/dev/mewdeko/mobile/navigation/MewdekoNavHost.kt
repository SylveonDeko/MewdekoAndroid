package dev.mewdeko.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.feature.account.AccountScreen
import dev.mewdeko.mobile.feature.guilddetail.FeatureBrowserScreen
import dev.mewdeko.mobile.feature.guilddetail.GuildDetailScreen
import dev.mewdeko.mobile.feature.guildlist.GuildListScreen

/** Wires every destination in the signed-in graph. */
@Composable
fun MewdekoNavHost(
    navController: NavHostController,
    user: MobileUser,
    instance: MobileInstance?,
    onSwitchInstance: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val guildArgs = listOf(
        navArgument("guildId") { type = NavType.StringType },
        navArgument("guildName") { type = NavType.StringType },
        navArgument("guildIcon") { type = NavType.StringType },
    )

    NavHost(
        navController = navController,
        startDestination = Routes.GUILD_LIST,
        modifier = modifier,
    ) {
        composable(Routes.GUILD_LIST) {
            GuildListScreen(
                userId = user.id,
                instanceName = instance?.botName,
                onOpenGuild = { guild ->
                    navController.navigate(Routes.guildDetail(guild.id, guild.name, guild.iconUrl))
                },
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                user = user,
                instance = instance,
                onSwitchInstance = onSwitchInstance,
                onSwitchServer = onSwitchServer,
                onSignOut = onSignOut,
                onDeleteData = onDeleteData,
            )
        }

        composable(Routes.GUILD_DETAIL, arguments = guildArgs) { entry ->
            val args = entry.guildArgs()
            GuildDetailScreen(
                guild = args,
                userId = user.id,
                onBack = { navController.popBackStack() },
                onOpenFeature = { featureId ->
                    navController.navigate(
                        Routes.feature(args.id, args.name, args.iconUrl, featureId)
                    )
                },
                onOpenFeatureBrowser = {
                    navController.navigate(Routes.featureBrowser(args.id, args.name, args.iconUrl))
                },
            )
        }

        composable(Routes.FEATURE_BROWSER, arguments = guildArgs) { entry ->
            val args = entry.guildArgs()
            FeatureBrowserScreen(
                guild = args,
                onBack = { navController.popBackStack() },
                onOpenFeature = { featureId ->
                    navController.navigate(
                        Routes.feature(args.id, args.name, args.iconUrl, featureId)
                    )
                },
            )
        }

        composable(
            Routes.FEATURE,
            arguments = guildArgs + navArgument("featureId") { type = NavType.StringType },
        ) { entry ->
            val args = entry.guildArgs()
            FeatureRoute(
                featureId = entry.arguments?.getString("featureId").orEmpty(),
                guild = args,
                userId = user.id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun androidx.navigation.NavBackStackEntry.guildArgs(): GuildRouteArgs =
    GuildRouteArgs.from(
        id = arguments?.getString("guildId"),
        name = arguments?.getString("guildName"),
        icon = arguments?.getString("guildIcon"),
    )
