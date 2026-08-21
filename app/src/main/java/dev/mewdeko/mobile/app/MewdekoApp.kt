package dev.mewdeko.mobile.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.app.onboarding.InstancePickerScreen
import dev.mewdeko.mobile.app.onboarding.ServerSetupScreen
import dev.mewdeko.mobile.app.onboarding.ServerUnavailableScreen
import dev.mewdeko.mobile.app.onboarding.SignInScreen
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.navigation.MainShell

/**
 * Root composable.
 *
 * Selects the onboarding surface for the current [AppPhase], or hands off to
 * [MainShell] once a user and instance are resolved.
 */
@Composable
fun MewdekoApp(appViewModel: AppViewModel = hiltViewModel()) {
    val state by appViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    MewdekoTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val phase = state.phase) {
                is AppPhase.Launching -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is AppPhase.NeedsServer -> ServerSetupScreen(
                    savedServers = state.savedServers,
                    errorMessage = state.lastError,
                    isProbing = false,
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
                    serverLabel = state.serverConfig?.label,
                    errorMessage = state.lastError,
                    isAuthorizing = state.isSigningIn,
                    onSignIn = { appViewModel.signIn(context) },
                    onChooseServer = { appViewModel.goToServerPicker() },
                    onDemoCode = { appViewModel.signInWithDemoCode(it) },
                )

                is AppPhase.NeedsInstance -> InstancePickerScreen(
                    instances = phase.instances,
                    errorMessage = state.lastError,
                    onSelect = { appViewModel.selectInstance(it, phase.user) },
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
