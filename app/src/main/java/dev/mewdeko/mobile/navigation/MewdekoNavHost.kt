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
import dev.mewdeko.mobile.feature.owner.OwnerGate
import dev.mewdeko.mobile.feature.owner.OwnerPanelScreen
import dev.mewdeko.mobile.feature.owner.analytics.OwnerAnalyticsScreen
import dev.mewdeko.mobile.feature.owner.bothells.BotHellsScreen
import dev.mewdeko.mobile.feature.owner.docker.DockerScreen
import dev.mewdeko.mobile.feature.owner.leavefeedback.LeaveFeedbackScreen
import dev.mewdeko.mobile.feature.owner.processlogs.ProcessLogsScreen
import dev.mewdeko.mobile.feature.performance.PerformanceScreen

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
                onOpenOwnerPanel = { navController.navigate(Routes.OWNER_PANEL) { launchSingleTop = true } },
            )
        }

        val leaveOwnerArea: () -> Unit = {
            if (!navController.popBackStack(Routes.ACCOUNT, inclusive = false)) {
                navController.popBackStack()
            }
        }
        val popOwnerPage: () -> Unit = { navController.popBackStack() }

        composable(Routes.OWNER_PANEL) {
            OwnerGate(onDenied = leaveOwnerArea) {
                OwnerPanelScreen(
                    onBack = popOwnerPage,
                    onOpenPage = { page ->
                        navController.navigate(page.route) { launchSingleTop = true }
                    },
                )
            }
        }

        composable(Routes.OWNER_DOCKER) {
            OwnerGate(onDenied = leaveOwnerArea) { DockerScreen(onBack = popOwnerPage) }
        }

        composable(Routes.OWNER_BOT_HELLS) {
            OwnerGate(onDenied = leaveOwnerArea) { BotHellsScreen(onBack = popOwnerPage) }
        }

        composable(Routes.OWNER_LEAVE_FEEDBACK) {
            OwnerGate(onDenied = leaveOwnerArea) { LeaveFeedbackScreen(onBack = popOwnerPage) }
        }

        composable(Routes.OWNER_ANALYTICS) {
            OwnerGate(onDenied = leaveOwnerArea) { OwnerAnalyticsScreen(onBack = popOwnerPage) }
        }

        composable(Routes.OWNER_PERFORMANCE) {
            OwnerGate(onDenied = leaveOwnerArea) { PerformanceScreen(onBack = popOwnerPage) }
        }

        composable(Routes.OWNER_PROCESS_LOGS) {
            OwnerGate(onDenied = leaveOwnerArea) { ProcessLogsScreen(onBack = popOwnerPage) }
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
                onOpenFeatureBrowser = { category ->
                    navController.navigate(Routes.featureBrowser(args.id, args.name, args.iconUrl, category?.name))
                },
            )
        }

        composable(
            Routes.FEATURE_BROWSER,
            arguments = guildArgs + navArgument("category") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ) { entry ->
            val args = entry.guildArgs()
            val category = entry.arguments?.getString("category")
                ?.takeIf { it != "-" }
                ?.let { raw -> FeatureCategory.entries.firstOrNull { it.name == raw } }
            FeatureBrowserScreen(
                initialCategory = category,
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
