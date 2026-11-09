package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.timilehin_dev.ogabot.AppGraph

/**
 * Activity — the audit log. Every tool call, on record.
 * (BotsScreen is the home surface; see BotsScreen.kt.)
 */
@Composable
fun ActivityScreen(graph: AppGraph) {
    val entries = graph.audit.recent(20)
    if (entries.isEmpty()) {
        CenteredLabel("Activity", "Every action, on record.")
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Activity", style = MaterialTheme.typography.headlineSmall)
            entries.forEach { e ->
                Text(
                    "${e.tool} · ${if (e.ok) "OK" else "FAIL"} · ${e.tier}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(graph: AppGraph) {
    CenteredLabel("Settings", "Providers, connectors, permissions.")
}

@Composable
private fun CenteredLabel(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
