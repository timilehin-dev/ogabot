package io.github.timilehin_dev.ogabot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.OgabotViewModel
import io.github.timilehin_dev.ogabot.core.ProviderConfig

@Composable
fun SetupScreen(vm: OgabotViewModel, onClose: () -> Unit) {
    var endpoint by remember { mutableStateOf(vm.provider?.endpoint ?: "https://ollama.com/v1") }
    var model by remember { mutableStateOf(vm.provider?.model ?: "gemma4:31b") }
    var key by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(56.dp).background(AvatarBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Wake your first bot", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Connect any OpenAI-compatible provider. The key is encrypted on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )

        OutlinedTextField(
            value = endpoint, onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endpoint") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(vm.provider?.let { "API key (saved \u2014 type to replace)" } ?: "API key") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = { endpoint = "https://ollama.com/v1"; model = "gemma4:31b" }, label = { Text("Ollama cloud") })
            AssistChip(onClick = { endpoint = "https://api.openai.com/v1"; model = "gpt-4o-mini" }, label = { Text("OpenAI") })
            AssistChip(onClick = { endpoint = "https://openrouter.ai/api/v1"; model = "openai/gpt-4o-mini" }, label = { Text("OpenRouter") })
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val k = if (key.isBlank()) vm.provider?.apiKey ?: "" else key.trim()
                vm.saveProvider(ProviderConfig(endpoint.trim(), k, model.trim()))
                onClose()
            },
            enabled = endpoint.isNotBlank() && model.isNotBlank(),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (vm.provider == null) "Save & start chatting" else "Save changes")
        }
        if (vm.provider != null) {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
