package dev.mewdeko.mobile.feature.owner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.auth.OwnerAccess
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.LoadingState

/**
 * Wraps every owner destination: themes it with the owner palette and only
 * composes [content] once the selected bot has confirmed the user owns it.
 *
 * While the answer is pending a spinner holds the screen, so nothing owner
 * related renders early. A negative answer calls [onDenied] once, which the
 * navigation host uses to leave the owner area.
 */
@Composable
fun OwnerGate(
    onDenied: () -> Unit,
    viewModel: OwnerAccessViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()
    val latestOnDenied by rememberUpdatedState(onDenied)

    MewdekoTheme(palette = palette) {
        when (access) {
            OwnerAccess.Owner -> content()

            OwnerAccess.Unknown -> Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LoadingState()
            }

            OwnerAccess.NotOwner -> {
                LaunchedEffect(Unit) { latestOnDenied() }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {}
            }
        }
    }
}
