package dev.mewdeko.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.mewdeko.mobile.app.MewdekoApp
import dev.mewdeko.mobile.core.auth.DiscordOAuthFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single activity host.
 *
 * Also receives the OAuth redirect: the Custom Tab returns through the
 * `mewdeko-mobile://oauth/callback` deep link, which is forwarded to
 * [DiscordOAuthFlow] to resume the suspended sign-in.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var oauth: DiscordOAuthFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { MewdekoApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "mewdeko-mobile") return
        lifecycleScope.launch { oauth.handleCallback(uri) }
    }
}
