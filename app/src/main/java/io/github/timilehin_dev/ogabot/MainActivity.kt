package io.github.timilehin_dev.ogabot

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.timilehin_dev.ogabot.ui.ChatScreen
import io.github.timilehin_dev.ogabot.ui.OgabotTheme
import io.github.timilehin_dev.ogabot.ui.SetupScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OgabotTheme {
                val vm: ChatViewModel = viewModel()
                var showSetup by remember { mutableStateOf(false) }
                when {
                    vm.provider == null -> SetupScreen(vm, onClose = {})
                    showSetup -> SetupScreen(vm, onClose = { showSetup = false })
                    else -> ChatScreen(vm, onOpenSettings = { showSetup = true })
                }
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
