package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.timilehin_dev.ogabot.ChatController
import io.github.timilehin_dev.ogabot.domain.entity.Bot
import io.github.timilehin_dev.ogabot.domain.entity.MemoryDoc
import io.github.timilehin_dev.ogabot.domain.entity.Routine

/**
 * Bot detail — Chat / Routines / Memory as tabs on one screen.
 * Three views of one bot (docs/APP_DESIGN.md §4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotDetailScreen(graph: AppGraph, botId: String, onBack: () -> Unit) {
    val bot = remember(botId) { graph.bots.get(botId) }
    if (bot == null) {
        onBack()
        return
    }
    var tab by remember { mutableIntStateOf(0) }
    val controller = remember(botId) { ChatController(graph, bot) }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Bg,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(32.dp).background(avatarBrushFor(bot.colorIndex), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    bot.name.take(1).uppercase().ifEmpty { "B" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(bot.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    bot.title.ifBlank { "on-device · always yours" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
                TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = MaterialTheme.colorScheme.primary) {
                    listOf("Chat", "Routines", "Memory").forEachIndexed { i, label ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> ChatContent(graph, bot, controller, Modifier.padding(padding))
            1 -> RoutinesTab(graph, bot, Modifier.padding(padding))
            else -> MemoryTab(graph, bot, Modifier.padding(padding))
        }
    }
}

// ------------------------------------------------------------------ routines

@Composable
private fun RoutinesTab(graph: AppGraph, bot: Bot, modifier: Modifier = Modifier) {
    var routines by remember(bot.id) { mutableStateOf(graph.routines.list(bot.id)) }
    var showCreate by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showCreate = !showCreate }) {
                Text(if (showCreate) "Close" else "+ Add routine")
            }
        }

        if (showCreate) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = task,
                        onValueChange = { task = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What should it do?") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = schedule,
                        onValueChange = { schedule = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("When? e.g. \"every day at 08:00\"") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(
                        onClick = {
                            val parsed = io.github.timilehin_dev.ogabot.platform.ScheduleParser.parse(schedule)
                            if (parsed == null) {
                                error = "Could not read that schedule. Try \"every day at 08:00\"."
                            } else if (task.isBlank()) {
                                error = "Describe what the routine should do."
                            } else {
                                val routine = Routine(
                                    id = "rt" + System.currentTimeMillis().toString(36),
                                    botId = bot.id,
                                    name = task.take(24),
                                    prompt = task.trim(),
                                    schedule = schedule.trim(),
                                    timezone = java.util.TimeZone.getDefault().id
                                )
                                if (graph.routines.add(routine)) {
                                    graph.routines.writeMarkdown(routine)
                                    graph.scheduler.schedule(routine)
                                    routines = graph.routines.list(bot.id)
                                    task = ""; schedule = ""; error = null; showCreate = false
                                } else {
                                    error = "Name already used or the 50-routine cap is reached."
                                }
                            }
                        },
                        shape = RoundedCornerShape(20.dp)
                    ) { Text("Create") }
                }
            }
        }

        if (routines.isEmpty()) {
            Text(
                "No routines yet. A routine runs this bot on a schedule and lands the result in its chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(routines, key = { it.id }) { r ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                r.schedule,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (r.lastRunAt > 0) {
                                Text(
                                    "last run " + java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US).format(java.util.Date(r.lastRunAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                r.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    graph.scheduler.cancel(r)
                                    graph.routines.remove(bot.id, r.id)
                                    routines = graph.routines.list(bot.id)
                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- memory

@Composable
private fun MemoryTab(graph: AppGraph, bot: Bot, modifier: Modifier = Modifier) {
    var draft by remember(bot.id) {
        mutableStateOf(graph.memory.load(bot.id)?.content ?: "")
    }
    val saved = remember(bot.id) { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "This text is injected into every conversation with ${bot.name}. Write preferences, context, and standing instructions here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "\u2022 Example: I go by Timi. Keep replies under 5 lines unless I ask for depth.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 2200) { draft = it; saved.value = false } },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            label = { Text("MEMORY.md · ${draft.length}/2200") }
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Button(
                onClick = {
                    graph.memory.save(MemoryDoc(bot.id, draft))
                    saved.value = true
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (saved.value) "Saved ✓" else "Save memory") }
        }
    }
}
