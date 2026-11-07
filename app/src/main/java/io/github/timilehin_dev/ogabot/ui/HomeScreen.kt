package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.OgabotViewModel
import io.github.timilehin_dev.ogabot.core.BotPersona

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: OgabotViewModel) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text("Ogabot", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "your bots \u00b7 always on",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openSetup() }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.openWizard() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New bot")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.provider == null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { vm.openSetup() }
                ) {
                    Text(
                        "No model connected yet \u2014 tap to set up a provider.",
                        Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.bots) { bot -> BotRow(bot) { vm.openBot(bot.id) } }
            }
        }
    }
}

@Composable
fun BotRow(bot: BotPersona, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(avatarBrushFor(bot.colorIndex), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    bot.name.take(1).uppercase(),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(bot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    bot.systemPrompt.replace("\n", " ").take(72) + if (bot.systemPrompt.length > 72) "…" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
