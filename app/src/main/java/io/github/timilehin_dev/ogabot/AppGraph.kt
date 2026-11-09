package io.github.timilehin_dev.ogabot

import android.content.Context
import io.github.timilehin_dev.ogabot.agent.AgentTurnEngine
import io.github.timilehin_dev.ogabot.agent.ApprovalGateImpl
import io.github.timilehin_dev.ogabot.data.AuditFileStore
import io.github.timilehin_dev.ogabot.data.BotFileStore
import io.github.timilehin_dev.ogabot.data.MemoryFileStore
import io.github.timilehin_dev.ogabot.data.RoutineFileStore
import io.github.timilehin_dev.ogabot.data.VaultStore
import io.github.timilehin_dev.ogabot.domain.entity.ProviderConfig
import io.github.timilehin_dev.ogabot.domain.port.ApprovalGate
import io.github.timilehin_dev.ogabot.domain.port.ApprovalResponder
import io.github.timilehin_dev.ogabot.domain.port.ApprovalResponse
import io.github.timilehin_dev.ogabot.domain.port.AuditSink
import io.github.timilehin_dev.ogabot.domain.port.BotRegistry
import io.github.timilehin_dev.ogabot.domain.port.MemoryStore
import io.github.timilehin_dev.ogabot.domain.port.ModelProvider
import io.github.timilehin_dev.ogabot.domain.port.Scheduler
import io.github.timilehin_dev.ogabot.domain.port.TermuxBackend
import io.github.timilehin_dev.ogabot.domain.port.ToolProvider
import io.github.timilehin_dev.ogabot.domain.port.TurnEngine
import io.github.timilehin_dev.ogabot.domain.port.Vault
import io.github.timilehin_dev.ogabot.platform.RoutineScheduler
import io.github.timilehin_dev.ogabot.platform.ServiceLocator
import io.github.timilehin_dev.ogabot.platform.TermuxBridge
import io.github.timilehin_dev.ogabot.tools.BuiltinTools
import io.github.timilehin_dev.ogabot.agent.OpenAiModelProvider
import java.io.File

/**
 * The dependency graph. One instance per process, created by the Application.
 * Everything is wired here — no DI framework, no magic.
 */
class AppGraph(context: Context) {

    val bots: BotRegistry = BotFileStore(context)
    val memory: MemoryStore = MemoryFileStore(context)
    val audit: AuditSink = AuditFileStore(context)
    val vault: Vault = VaultStore(context)
    val routines: RoutineFileStore = RoutineFileStore(context)
    val scheduler: Scheduler = RoutineScheduler(context)
    val termux: TermuxBackend = TermuxBridge(context)
    val builtinTools: ToolProvider = BuiltinTools(context, bots, memory)
    val gate: ApprovalGate = ApprovalGateImpl(File(context.filesDir, "custom_rules.json"))

    /** Set by the UI when an approval card is showing; defaults to allow-once. */
    var approvalResponder: ApprovalResponder = ApprovalResponder { ApprovalResponse.ALLOW_ONCE }

    val model: ModelProvider by lazy {
        val cfg = vault.load() ?: ProviderConfig("https://ollama.com/v1", "", "gemma4:31b")
        OpenAiModelProvider(cfg)
    }

    val engine: TurnEngine by lazy {
        AgentTurnEngine(model, gate, approvalResponder, listOf(builtinTools), audit)
    }

    val allTools: List<ToolProvider> get() = listOf(builtinTools)

    fun init() {
        ServiceLocator.turnEngine = engine
        ServiceLocator.bots = bots
        ServiceLocator.memory = memory
        ServiceLocator.vault = vault
        ServiceLocator.tools = allTools
        scheduler.rescheduleAll()
    }
}
