package io.github.timilehin_dev.ogabot.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.AppGraph
import io.github.timilehin_dev.ogabot.agent.OpenAiModelProvider
import io.github.timilehin_dev.ogabot.domain.entity.Message
import io.github.timilehin_dev.ogabot.domain.entity.ProviderConfig
import io.github.timilehin_dev.ogabot.domain.port.StreamEvent
import kotlinx.coroutines.launch

private data class Preset(val label: String, val endpoint: String, val model: String)

private val PRESETS = listOf(
    Preset("Ollama cloud", "https://ollama.com/v1", "gemma4:31b"),
    Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    Preset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    Preset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat")
)

/**
 * Provider setup — used for first-run onboarding and later from Settings.
 * The key is encrypted in the Android Keystore via VaultStore; it never touches
 * disk in the clear.
 */
@Composable
fun SetupScreen(graph: AppGraph, isOnboarding: Boolean, onDone: () -> Unit) {
    val existing = remember { graph.vault.load() }
    var endpoint by remember { mutableStateOf(existing?.endpoint ?: "https://ollama.com/v1") }
    var model by remember { mutableStateOf(existing?.model ?: "gemma4:31b") }
    var key by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(if (isOnboarding) 64.dp else 48.dp).background(AvatarBrush, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (isOnboarding) "Welcome to Ogabot" else "Model provider",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isOnboarding)
                "Connect a model provider to wake your bots. Any OpenAI-compatible endpoint works; the key is encrypted on this device."
            else
                "Applies to every bot. The key is encrypted on this device.",
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
            value = endpoint,
            onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Endpoint") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(if (existing != null) "API key (saved — type to replace)" else "API key") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PRESETS.forEach { p ->
                AssistChip(
                    onClick = { endpoint = p.endpoint; model = p.model },
                    label = { Text(p.label) }
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        testResult?.let { res ->
            Text(
                res,
                style = MaterialTheme.typography.bodySmall,
                color = if (testOk) Success else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        Button(
            onClick = {
                val k = key.trim().ifBlank { existing?.apiKey ?: "" }
                graph.vault.save(ProviderConfig(endpoint.trim(), k, model.trim()))
                onDone()
            },
            enabled = endpoint.isNotBlank() && model.isNotBlank() && (key.isNotBlank() || existing != null),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (isOnboarding) "Start chatting" else "Save changes")
        }

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = {
                testing = true
                testResult = null
                testOk = false
                scope.launch {
                    val cfg = ProviderConfig(endpoint.trim(), key.trim(), model.trim())
                    val sb = StringBuilder()
                    var failed: String? = null
                    OpenAiModelProvider(cfg)
                        .stream(listOf(Message("user", "Reply with exactly: OK")), emptyList())
                        .collect { ev ->
                            when (ev) {
                                is StreamEvent.Delta -> sb.append(ev.text)
                                is StreamEvent.Error -> failed = ev.message
                                StreamEvent.Done -> {}
                                is StreamEvent.ToolCallEvent -> {}
                            }
                        }
                    testOk = failed == null
                    testResult = failed ?: "Connected — model replied: ${sb.toString().trim().take(80)}"
                    testing = false
                }
            },
            enabled = !testing && endpoint.isNotBlank() && model.isNotBlank() && key.isNotBlank()
        ) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Test connection")
        }

        if (!isOnboarding && existing != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
