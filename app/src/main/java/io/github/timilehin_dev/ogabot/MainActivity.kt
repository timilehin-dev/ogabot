package io.github.timilehin_dev.ogabot

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.timilehin_dev.ogabot.core.BotPersona
import io.github.timilehin_dev.ogabot.core.BotRegistry
import io.github.timilehin_dev.ogabot.core.BotSeed
import io.github.timilehin_dev.ogabot.core.ChatMessage
import io.github.timilehin_dev.ogabot.core.ChatStore
import io.github.timilehin_dev.ogabot.core.MemoryStore
import io.github.timilehin_dev.ogabot.core.OpenAiClient
import io.github.timilehin_dev.ogabot.core.ProviderConfig
import io.github.timilehin_dev.ogabot.core.StreamEvent
import io.github.timilehin_dev.ogabot.core.VaultStore
import io.github.timilehin_dev.ogabot.ui.ChatScreen
import io.github.timilehin_dev.ogabot.ui.HomeScreen
import io.github.timilehin_dev.ogabot.ui.MemoryScreen
import io.github.timilehin_dev.ogabot.ui.OgabotTheme
import io.github.timilehin_dev.ogabot.ui.SetupScreen
import io.github.timilehin_dev.ogabot.ui.WizardScreen
import kotlinx.coroutines.launch

enum class Screen { HOME, CHAT, WIZARD, MEMORY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OgabotTheme {
                val vm: OgabotViewModel = viewModel()
                BackHandler(enabled = vm.screen != Screen.HOME) { vm.goBack() }
                when (vm.screen) {
                    Screen.HOME -> HomeScreen(vm)
                    Screen.CHAT -> ChatScreen(vm)
                    Screen.WIZARD -> WizardScreen(vm)
                    Screen.MEMORY -> MemoryScreen(vm)
                }
            }
        }
    }
}

class OgabotViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app
    private val store = ChatStore(app)
    private val memory = MemoryStore(app)

    // --- global ---
    var provider by mutableStateOf<ProviderConfig?>(VaultStore.loadProvider(app))
        private set
    var showSetup by mutableStateOf(false)
        private set

    // --- navigation ---
    var screen by mutableStateOf(Screen.HOME)
        private set
    var currentBot by mutableStateOf<BotPersona?>(null)
        private set

    // --- home state ---
    var bots by mutableStateOf(BotSeed.ensure(app).let { BotRegistry.list(app) })
        private set

    // --- chat state (per currentBot) ---
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var streamingText by mutableStateOf("")
        private set
    var isStreaming by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var lastUserText: String? = null

    // --- memory state ---
    var memoryDraft by mutableStateOf("")
        private set

    init {
        // auto-open Oga's chat if it's the only bot (feels like v0.1), else home
    }

    // navigation
    fun openBot(id: String) {
        currentBot = BotRegistry.get(context, id) ?: return
        messages = store.load(id)
        error = null
        streamingText = ""
        isStreaming = false
        screen = Screen.CHAT
    }

    fun goHome() {
        screen = Screen.HOME
        currentBot = null
        messages = emptyList()
        streamingText = ""
        isStreaming = false
        error = null
    }

    fun openWizard() { screen = Screen.WIZARD }

    fun openMemory() {
        memoryDraft = memory.loadProfile(currentBot?.id ?: return)
        screen = Screen.MEMORY
    }

    // wizard
    fun createBot(name: String, personaText: String, colorIndex: Int) {
        val p = BotRegistry.create(context, name, personaText, colorIndex)
        bots = BotRegistry.list(context)
        openBot(p.id)
    }

    // memory editor
    fun updateMemoryDraft(text: String) { memoryDraft = text }

    fun saveMemory() {
        val id = currentBot?.id ?: return
        memory.saveProfile(id, memoryDraft)
        goHome()
    }

    // setup
    fun saveProvider(cfg: ProviderConfig) {
        VaultStore.saveProvider(context, cfg)
        provider = cfg
        showSetup = false
    }

    fun openSetup() { showSetup = true }
    fun closeSetup() { showSetup = false }

    // chat
    fun send(text: String) {
        if (isStreaming || text.isBlank() || currentBot == null) return
        lastUserText = text.trim()
        messages = messages + ChatMessage("user", text.trim())
        store.save(currentBot!!.id, messages)
        runTurn()
    }

    fun retry() {
        if (isStreaming || lastUserText == null || currentBot == null) return
        runTurn()
    }

    fun dismissError() { error = null }

    private fun runTurn() {
        val cfg = provider ?: return
        val bot = currentBot ?: return
        error = null
        isStreaming = true
        streamingText = ""
        viewModelScope.launch {
            val system = bot.systemPrompt + memory.promptInjection(bot.id)
            val history = listOf(ChatMessage("system", system)) + messages
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
                store.save(bot.id, messages)
            }
            streamingText = ""
            isStreaming = false
            error = gotError
        }
    }
}
