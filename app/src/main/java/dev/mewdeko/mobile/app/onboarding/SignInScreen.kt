package dev.mewdeko.mobile.app.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.R
import dev.mewdeko.mobile.core.ui.MewdekoTextField

/** Opens Discord so the user can authorize Mewdeko Mobile. */
@Composable
fun SignInScreen(
    serverLabel: String?,
    errorMessage: String?,
    isAuthorizing: Boolean,
    onSignIn: () -> Unit,
    onChooseServer: () -> Unit,
    onDemoCode: (String) -> Unit,
) {
    var showDemoEntry by remember { mutableStateOf(false) }
    var demoCode by remember { mutableStateOf("") }

    val scheme = MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(scheme.primary.copy(alpha = 0.23f), Color.Transparent),
                        radius = 900f,
                    ),
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp, vertical = 36.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = scheme.surfaceContainerHigh,
                    shadowElevation = 10.dp,
                    modifier = Modifier.size(116.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.mewdeko_logo),
                        contentDescription = "Mewdeko",
                        modifier = Modifier.padding(9.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "MEWDEKO MOBILE",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Your server, wherever you are.",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Manage the parts of your Discord community that need your attention.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        if (serverLabel != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "DASHBOARD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = serverLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurface,
                                )
                            }
                        }

                        Text(
                            text = "Sign in to continue",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "Discord opens securely so you can choose the account you want to use.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.error,
                            )
                        }

                        Button(
                            onClick = onSignIn,
                            enabled = !isAuthorizing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF5865F2),
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isAuthorizing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Text("Open Discord to sign in")
                            }
                        }
                    }
                }

                TextButton(onClick = onChooseServer) { Text("Use a different dashboard") }

                if (showDemoEntry) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp),
                        ) {
                            MewdekoTextField(
                                value = demoCode,
                                onValueChange = { demoCode = it },
                                label = "Demo code",
                            )
                            Button(
                                onClick = { onDemoCode(demoCode) },
                                enabled = demoCode.isNotBlank() && !isAuthorizing,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Open the demo") }
                        }
                    }
                } else {
                    TextButton(onClick = { showDemoEntry = true }) {
                        Text("Have a demo code?")
                    }
                }
            }
        }
    }
}
