package io.github.timilehin_dev.ogabot

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import io.github.timilehin_dev.ogabot.ui.BotSettingsScreen
import io.github.timilehin_dev.ogabot.ui.ChatScreen
import io.github.timilehin_dev.ogabot.ui.HomeScreen
import io.github.timilehin_dev.ogabot.ui.MemoryScreen
import io.github.timilehin_dev.ogabot.ui.OgabotTheme
import io.github.timilehin_dev.ogabot.ui.SetupScreen
import io.github.timilehin_dev.ogabot.ui.WizardScreen
import kotlinx.coroutines.launch

enum class Screen { HOME, CHAT, WIZARD, MEMORY, BOT_SETTINGS, SETUP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OgabotTheme {
                val vm: OgabotViewModel = viewModel()
                if (vm.provider == null) {
                    // first-run onboarding: no way past it until a provider is connected
                    SetupScreen(vm = vm, isOnboarding = true, onClose = {})
                } else {
                    BackHandler(enabled = vm.canGoBack) { vm.back() }
                    when (vm.screen) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.CHAT -> ChatScreen(vm)
                        Screen.WIZARD -> WizardScreen(vm)
                        Screen.MEMORY -> MemoryScreen(vm)
                        Screen.BOT_SETTINGS -> BotSettingsScreen(vm)
                        Screen.SETUP -> SetupScreen(vm, isOnboarding = false, onClose = { vm.back() })
                    }
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

    // --- navigation stack ---
    private val stack = mutableStateListOf(Screen.HOME)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun goHome() {
        stack.clear(); stack.add(Screen.HOME)
        currentBot = null; messages = emptyList()
        streamingText = ""; isStreaming = false; error = null
    }

    // --- data ---
    var bots by mutableStateOf(BotSeed.ensure(app).let { BotRegistry.list(app) })
        private set
    var currentBot by mutableStateOf<BotPersona?>(null)
        private set

    // --- chat state ---
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

    // navigation actions
    fun openBot(id: String) {
        currentBot = BotRegistry.get(context, id) ?: return
        messages = store.load(id)
        error = null
        streamingText = ""
        isStreaming = false
        stack.add(Screen.CHAT)
    }

    fun openWizard() { stack.add(Screen.WIZARD) }

    fun openMemory() {
        memoryDraft = memory.loadProfile(currentBot?.id ?: return)
        stack.add(Screen.MEMORY)
    }

    fun openBotSettings() { stack.add(Screen.BOT_SETTINGS) }
    fun openSetup() { stack.add(Screen.SETUP) }

    // wizard
    fun createBot(name: String, personaText: String, colorIndex: Int) {
        val p = BotRegistry.create(context, name, personaText, colorIndex)
        bots = BotRegistry.list(context)
        goHome()
        openBot(p.id)
        // remove HOME+CHAT duplication: keep single CHAT on stack
        if (stack.size > 2) stack.removeAt(1)
    }

    // bot settings
    fun updateBot(name: String, personaText: String, colorIndex: Int) {
        val bot = currentBot ?: return
        val updated = bot.copy(name = name.trim(), systemPrompt = personaText.trim(), colorIndex = colorIndex)
        BotRegistry.save(context, updated)
        currentBot = updated
        bots = BotRegistry.list(context)
        back()
    }

    fun deleteBot() {
        val bot = currentBot ?: return
        BotRegistry.delete(context, bot.id)
        store.delete(bot.id)
        bots = BotRegistry.list(context)
        goHome()
    }

    // memory editor
    fun updateMemoryDraft(text: String) { memoryDraft = text }

    fun saveMemory() {
        val id = currentBot?.id ?: return
        memory.saveProfile(id, memoryDraft)
        back()
    }

    // setup
    fun saveProvider(cfg: ProviderConfig) {
        VaultStore.saveProvider(context, cfg)
        provider = cfg
    }

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
