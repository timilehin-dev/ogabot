package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.timilehin_dev.ogabot.OgabotViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(vm: OgabotViewModel) {
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
                title = {
                    Text(
                        (vm.currentBot?.name ?: "") + " \u00b7 memory",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
            Text(
                "This text is injected into every conversation with this bot. " +
                    "Write preferences, context and standing instructions here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text("\u2022 ", color = MaterialTheme.colorScheme.primary)
                Text(
                    "Example: I go by Timi. Keep replies under 5 lines unless I ask for depth.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = vm.memoryDraft,
                onValueChange = { vm.updateMemoryDraft(it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Button(
                    onClick = { vm.saveMemory() },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Save memory") }
            }
        }
    }
}
