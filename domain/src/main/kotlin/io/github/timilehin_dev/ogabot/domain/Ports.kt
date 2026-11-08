package io.github.timilehin_dev.ogabot.domain.port

import io.github.timilehin_dev.ogabot.domain.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The turn engine runs one agent turn: assemble context, stream the model,
 * dispatch tool calls through the gate, loop until done. Implemented in :agent.
 */
interface TurnEngine {
    fun runTurn(request: TurnRequest): Flow<TurnEvent>
}

data class TurnRequest(
    val bot: Bot,
    val history: List<Message>,
    val tools: List<ToolDescriptor>,
    val background: Boolean = false
)

interface ToolExecutor {
    suspend fun execute(call: ToolCall): ToolResult
}

/** A backend that provides tools: built-in Kotlin, Chaquopy Python, Termux, MCP. */
interface ToolProvider {
    fun descriptors(): List<ToolDescriptor>
    fun executor(): ToolExecutor
}

interface ApprovalGate {
    fun decide(botId: String, tool: String, argsJson: String, background: Boolean): Decision
    fun addRule(pattern: String, askFirst: Boolean)
    fun removeRule(index: Int)
    fun rules(): List<CustomRule>

    sealed class Decision {
        object Allow : Decision()
        object Deny : Decision()
        data class Ask(val background: Boolean) : Decision()
    }
}

@Serializable
data class CustomRule(val pattern: String, val askFirst: Boolean)

interface BotRegistry {
    fun list(): List<Bot>
    fun get(id: String): Bot?
    fun save(bot: Bot)
    fun delete(id: String)
    fun create(name: String, persona: String, colorIndex: Int): Bot
}

interface MemoryStore {
    fun load(botId: String): MemoryDoc?
    fun save(doc: MemoryDoc)
    /** The text injected into the system prompt every turn. */
    fun inject(botId: String): String
}

interface AuditSink {
    fun record(entry: AuditEntry)
    fun recent(limit: Int = 100): List<AuditEntry>
}

interface Scheduler {
    fun schedule(routine: Routine)
    fun cancel(routine: Routine)
    fun rescheduleAll()
}

/** Streams a model response. Implemented in :agent over OkHttp SSE. */
interface ModelProvider {
    fun stream(messages: List<Message>, tools: List<ToolDescriptor>): Flow<StreamEvent>
}

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    data class ToolCall(val call: ToolCall) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    object Done : StreamEvent()
}

interface CapabilityRegistry {
    fun capabilities(): List<PhoneCapability>
    fun state(id: String): CapabilityState
}

interface TermuxBackend {
    val state: TermuxState
    fun exec(command: String, timeoutSec: Int = 120): ExecResult
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String): Boolean
    fun startDaemon(name: String, command: String): Boolean
    fun stopDaemon(name: String): Boolean
}

enum class TermuxState { NOT_INSTALLED, INSTALLED, BOOTSTRAPPED, RUNNING }

data class ExecResult(val ok: Boolean, val output: String, val exitCode: Int = 0)

interface Vault {
    fun save(config: ProviderConfig)
    fun load(): ProviderConfig?
}

/**
 * Suspends the turn until the human answers an approval request. The ViewModel
 * shows the card and resumes this when the user taps Allow once / Always / Deny.
 */
interface ApprovalResponder {
    suspend fun request(request: ApprovalRequest): ApprovalResponse
}

enum class ApprovalResponse { ALLOW_ONCE, ALLOW_ALWAYS, DENY }
