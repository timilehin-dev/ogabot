package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.AppGraph
import io.github.timilehin_dev.ogabot.domain.entity.Bot

/**
 * Home — the Bot roster. GrokBot's primary object is the Bot, so the roster is
 * the home screen; the "Needs you" section at the top is the triage surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(graph: AppGraph, onOpenBot: (String) -> Unit) {
    var bots by remember { mutableStateOf(graph.bots.list()) }
    var showWizard by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text("Ogabot", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "your bots · always on",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showWizard = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New bot")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bots.isEmpty()) {
                item { EmptyRoster() }
            } else {
                items(bots, key = { it.id }) { bot -> BotRow(bot) { onOpenBot(bot.id) } }
            }
        }
    }

    if (showWizard) {
        NewBotDialog(
            onDismiss = { showWizard = false },
            onCreate = { name, persona, color ->
                val bot = graph.bots.create(name, persona, color)
                bots = graph.bots.list()
                showWizard = false
                onOpenBot(bot.id)
            }
        )
    }
}

@Composable
private fun EmptyRoster() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(72.dp).background(AvatarBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("No bots yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Create your first teammate. Give it a name and a job.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BotRow(bot: Bot, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(avatarBrushFor(bot.colorIndex), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    bot.name.take(1).uppercase().ifEmpty { "B" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(bot.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    bot.persona.replace("\n", " ").take(72) + if (bot.persona.length > 72) "\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Box(Modifier.size(10.dp).background(Success, CircleShape))
        }
    }
}

@Composable
private fun NewBotDialog(onDismiss: () -> Unit, onCreate: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var persona by remember { mutableStateOf("") }
    var colorIndex by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLow,
        title = { Text("New bot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = persona,
                    onValueChange = { persona = it },
                    label = { Text("Job description") },
                    shape = RoundedCornerShape(16.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotGradients.forEachIndexed { i, _ ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(avatarBrushFor(i), CircleShape)
                                .clickable { colorIndex = i }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, persona, colorIndex) },
                enabled = name.isNotBlank() && persona.isNotBlank()
            ) { Text("Create bot") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
