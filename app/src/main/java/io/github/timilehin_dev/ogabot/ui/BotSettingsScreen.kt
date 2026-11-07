package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import io.github.timilehin_dev.ogabot.OgabotViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSettingsScreen(vm: OgabotViewModel) {
    val bot = vm.currentBot
    if (bot == null) {
        vm.back()
        return
    }

    var name by remember { mutableStateOf(bot.name) }
    var persona by remember { mutableStateOf(bot.systemPrompt) }
    var colorIndex by remember { mutableStateOf(bot.colorIndex) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                title = { Text("Bot settings", style = MaterialTheme.typography.titleLarge) }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(avatarBrushFor(colorIndex), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).uppercase().ifEmpty { "B" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    Text(name.ifEmpty { "Unnamed bot" }, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "on-device \u00b7 always yours",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = persona, onValueChange = { persona = it },
                modifier = Modifier.fillMaxWidth().height(130.dp),
                label = { Text("Persona / job description") },
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors
            )
            Spacer(Modifier.height(18.dp))
            Text("Avatar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotGradients.forEachIndexed { i, g ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(avatarBrushFor(i), CircleShape)
                            .border(
                                width = if (i == colorIndex) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                                indication = null
                            ) { colorIndex = i }
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = { vm.updateBot(name, persona, colorIndex) },
                enabled = name.isNotBlank() && persona.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Save changes") }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { vm.openMemory() }, modifier = Modifier.fillMaxWidth()) {
                Text("Open memory editor")
            }

            Spacer(Modifier.height(30.dp))
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete bot", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete " + bot.name + "?") },
            text = { Text("Its conversation and memory will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteBot() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
