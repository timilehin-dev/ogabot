package io.github.timilehin_dev.ogabot

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.timilehin_dev.ogabot.core.BotSeed
import io.github.timilehin_dev.ogabot.core.ChatMessage
import io.github.timilehin_dev.ogabot.core.ChatStore
import io.github.timilehin_dev.ogabot.core.OpenAiClient
import io.github.timilehin_dev.ogabot.core.ProviderConfig
import io.github.timilehin_dev.ogabot.core.StreamEvent
import io.github.timilehin_dev.ogabot.core.VaultStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: ChatViewModel = viewModel()
                if (vm.provider == null) SetupScreen(vm) else ChatScreen(vm)
            }
        }
    }
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val persona = BotSeed.ensure(app)
    private val store = ChatStore(app)

    var provider by mutableStateOf<ProviderConfig?>(VaultStore.loadProvider(app))
        private set
    var messages by mutableStateOf(store.load())
        private set
    var streamingText by mutableStateOf("")
        private set
    var isStreaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var lastUserText: String? = null

    fun saveProvider(cfg: ProviderConfig) {
        VaultStore.saveProvider(getApplication(), cfg)
        provider = cfg
    }

    fun send(text: String) {
        if (isStreaming || text.isBlank()) return
        lastUserText = text.trim()
        messages = messages + ChatMessage("user", text.trim())
        store.save(messages)
        runTurn()
    }

    fun retry() {
        if (isStreaming || lastUserText == null) return
        runTurn()
    }

    fun dismissError() {
        error = null
    }

    private fun runTurn() {
        val cfg = provider ?: return
        error = null
        isStreaming = true
        streamingText = ""
        viewModelScope.launch {
            val history = listOf(ChatMessage("system", persona.systemPrompt)) + messages
            var gotError: String? = null
            OpenAiClient(cfg).streamChat(history).collect { ev ->
                when (ev) {
                    is StreamEvent.Delta -> streamingText = streamingText + ev.text
                    is StreamEvent.Error -> gotError = ev.message
                    StreamEvent.Done -> {}
                }
            }
            if (streamingText.isNotBlank()) {
                messages = messages + ChatMessage("assistant", streamingText)
                store.save(messages)
            }
            streamingText = ""
            isStreaming = false
            error = gotError
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.messages.size, vm.streamingText) {
        val target = vm.messages.size - 1 + if (vm.isStreaming) 1 else 0
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(vm.persona.emoji + " " + vm.persona.name + " · Ogabot v0.1") })
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            ) {
                if (vm.messages.isEmpty() && !vm.isStreaming) {
                    item {
                        Text(
                            "Say hello to " + vm.persona.name + " \uD83D\uDC4B",
                            modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                items(vm.messages) { m -> MessageBubble(m.role, m.content) }
                if (vm.isStreaming) {
                    item { MessageBubble("assistant", vm.streamingText.ifEmpty { "…\u200E" }) }
                }
            }

            vm.error?.let { err ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "\u26A0\uFE0F " + err,
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { vm.retry() }) { Text("Retry") }
                    TextButton(onClick = { vm.dismissError() }) { Text("\u2715") }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message " + vm.persona.name + "\u2026") },
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { vm.send(input); input = "" },
                    enabled = !vm.isStreaming && input.isNotBlank()
                ) { Text("Send") }
            }
        }
    }
}

@Composable
fun MessageBubble(role: String, text: String) {
    val isUser = role == "user"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text,
                Modifier.padding(12.dp).widthIn(max = 300.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SetupScreen(vm: ChatViewModel) {
    var endpoint by remember { mutableStateOf("https://ollama.com/v1") }
    var model by remember { mutableStateOf("gemma4:31b") }
    var key by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Ogabot", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect a model provider to wake your first bot. The key is stored encrypted on this device.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = endpoint, onValueChange = { endpoint = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Endpoint") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(12.dp))
        Row {
            AssistChip(
                onClick = { endpoint = "https://ollama.com/v1"; model = "gemma4:31b" },
                label = { Text("Ollama cloud") }
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { endpoint = "https://api.openai.com/v1"; model = "gpt-4o-mini" },
                label = { Text("OpenAI") }
            )
            Spacer(Modifier.width(8.dp))
            AssistChip(
                onClick = { endpoint = "https://openrouter.ai/api/v1"; model = "openai/gpt-4o-mini" },
                label = { Text("OpenRouter") }
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                vm.saveProvider(
                    ProviderConfig(endpoint.trim(), key.trim(), model.trim())
                )
            },
            enabled = key.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save & start chatting") }
    }
}
