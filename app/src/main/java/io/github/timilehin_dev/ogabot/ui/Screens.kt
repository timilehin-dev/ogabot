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

/**
 * Placeholder screens for the Sprint 0 skeleton. Each is replaced by the real
 * implementation in its own sprint — see docs/APP_DESIGN.md §5.
 */
@Composable
fun BotsScreen() {
    CenteredLabel("Bots", "Your teammates live here.")
}

@Composable
fun ActivityScreen() {
    CenteredLabel("Activity", "Every action, on record.")
}

@Composable
fun SettingsScreen() {
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
