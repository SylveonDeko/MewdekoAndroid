package dev.mewdeko.mobile.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.R
import dev.mewdeko.mobile.app.onboarding.InstancePickerScreen
import dev.mewdeko.mobile.app.onboarding.ServerSetupScreen
import dev.mewdeko.mobile.app.onboarding.ServerUnavailableScreen
import dev.mewdeko.mobile.app.onboarding.SignInScreen
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.LaunchGlow
import dev.mewdeko.mobile.navigation.MainShell

/**
 * Root composable.
 *
 * Selects the onboarding surface for the current [AppPhase], or hands off to
 * [MainShell] once a user and instance are resolved. The onboarding shell
 * sits outside any guild, so it is lit by the house palette, exactly like
 * the Servers grid.
 */
@Composable
fun MewdekoApp(appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MewdekoTheme(palette = GuildPalette.Default) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val phase = state.phase) {
                is AppPhase.Launching -> LaunchGlow(logo = painterResource(R.drawable.mewdeko_logo))

                is AppPhase.NeedsServer -> ServerSetupScreen(
                    savedServers = state.savedServers,
                    currentServerId = state.serverConfig?.id,
                    errorMessage = state.lastError,
                    isProbing = state.isProbing,
                    onAddServer = appViewModel::addServer,
                    onSelectServer = { appViewModel.switchToSavedServer(it) },
                    onForgetServer = { appViewModel.removeSavedServer(it) },
                )

                is AppPhase.ServerUnavailable -> ServerUnavailableScreen(
                    server = phase.server,
                    reason = phase.reason,
                    onRetry = { appViewModel.retryStoredServer() },
                    onChooseServer = { appViewModel.goToServerPicker() },
                    onForget = { appViewModel.removeSavedServer(phase.server.id) },
                )

                is AppPhase.NeedsSignIn -> SignInScreen(
                    server = state.serverConfig,
                    errorMessage = state.lastError,
                    isAuthorizing = state.isSigningIn,
                    onSignIn = { appViewModel.signIn(context) },
                    onChooseServer = { appViewModel.goToServerPicker() },
                    onDemoCode = { appViewModel.signInWithDemoCode(it) },
                )

                is AppPhase.NeedsInstance -> InstancePickerScreen(
                    instances = phase.instances,
                    errorMessage = state.lastError,
                    dashboardHost = state.serverConfig?.baseUrl?.hostOrSelf(),
                    onSelect = { appViewModel.selectInstance(it, phase.user) },
                    onRetry = { appViewModel.reloadInstances(phase.user) },
                    onChooseServer = { appViewModel.goToServerPicker() },
                    onSignOut = { appViewModel.signOut() },
                )

                is AppPhase.SignedIn -> MainShell(
                    user = phase.user,
                    instance = phase.instance,
                    onSwitchInstance = { appViewModel.switchInstance() },
                    onSwitchServer = { appViewModel.goToServerPicker() },
                    onSignOut = { appViewModel.signOut() },
                    onDeleteData = { appViewModel.deleteLocalAccountData() },
                )
            }
        }
    }
}
