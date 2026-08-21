package dev.mewdeko.mobile.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.clickableRow

/** Lets the user pick which bot instance on the dashboard to manage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstancePickerScreen(
    instances: List<MobileInstance>,
    errorMessage: String?,
    onSelect: (MobileInstance) -> Unit,
    onChooseServer: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Choose Bot") }) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(contentPadding = 8) {
                SectionCardHeader(
                    title = "Bot instances",
                    icon = Icons.Default.SmartToy,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                if (instances.isEmpty()) {
                    EmptyState(
                        message = errorMessage ?: "No active bot instances on this dashboard.",
                        icon = Icons.Default.SmartToy,
                    )
                } else {
                    instances.forEach { instance ->
                        ListItem(
                            headlineContent = { Text(instance.botName) },
                            supportingContent = { Text(instance.botId) },
                            leadingContent = {
                                Avatar(
                                    url = instance.avatarUrl,
                                    contentDescription = instance.botName,
                                    fallbackIcon = Icons.Default.SmartToy,
                                )
                            },
                            modifier = Modifier.clickableRow { onSelect(instance) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onChooseServer) { Text("Choose a different server") }
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
    }
}
