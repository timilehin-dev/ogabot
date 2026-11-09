package io.github.timilehin_dev.ogabot.domain.entity

import kotlinx.serialization.Serializable

/**
 * A Bot is a profile: a durable AI teammate with its own identity, memory,
 * tools, and schedule. Everything a Bot is lives in bots/<id>/ on disk.
 */
@Serializable
data class Bot(
    val id: String,
    val name: String,
    val title: String = "",
    val colorIndex: Int = 0,
    val persona: String = "",
    val modelRef: String = "default",
    val temperature: Double = 0.7,
    val toolGrants: Map<String, String> = emptyMap(),
    val budget: Budget = Budget(),
    val devMode: Boolean = true,
    val createdAt: Long = 0L
)

@Serializable
data class Budget(
    val dailyTokenCap: Int = 200_000,
    val dailyFgsMinutesCap: Int = 120
)

enum class Role { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
data class Message(
    val role: String,
    val content: String,
    val timestamp: Long = 0L,
    val toolCallId: String? = null,
    val toolName: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    /** JSON-encoded arguments. */
    val arguments: String
)

@Serializable
data class ToolResult(
    val callId: String,
    val tool: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long = 0L,
    val tier: String = "auto"
)

@Serializable
data class Routine(
    val id: String,
    val botId: String,
    val name: String,
    val prompt: String,
    /** Cron expression or plain language ("every day at 08:00"). */
    val schedule: String,
    val timezone: String = "Africa/Lagos",
    val modelRef: String = "default",
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    val createdAt: Long = 0L
)

@Serializable
data class RoutineRun(
    val id: String,
    val routineId: String,
    val botId: String,
    val outcome: String, // success | failed | skipped
    val result: String = "",
    val error: String? = null,
    val timestamp: Long = 0L
)

@Serializable
data class ApprovalRequest(
    val id: String,
    val botId: String,
    val tool: String,
    val arguments: String,
    val tier: String,
    val background: Boolean = false,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L
)

@Serializable
data class Skill(
    val name: String,
    val description: String,
    val category: String = "general",
    val body: String = ""
)

@Serializable
data class MemoryDoc(
    val botId: String,
    val content: String,
    val updatedAt: Long = 0L
)

@Serializable
data class ProviderConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.7
)

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    /** JSON-schema string the model sees. */
    val parameters: String,
    val defaultTier: String = "ask"
)

enum class TurnOutcome { COMPLETED, FAILED, ABORTED, IDLE_TIMEOUT }

sealed class TurnEvent {
    data class Delta(val text: String) : TurnEvent()
    data class ToolCallEvent(val call: ToolCall) : TurnEvent()
    data class ToolResultValue(val result: ToolResult) : TurnEvent()
    data class NeedsApproval(val request: ApprovalRequest) : TurnEvent()
    data class Completed(val outcome: TurnOutcome) : TurnEvent()
    data class Failed(val error: String) : TurnEvent()
}

enum class CapabilityState { UNAVAILABLE, AVAILABLE, PERMITTED, ACTIVE }

@Serializable
data class PhoneCapability(
    val id: String,
    val name: String,
    val description: String
)

@Serializable
data class AuditEntry(
    val botId: String,
    val tool: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long,
    val tier: String,
    val timestamp: Long = 0L
)
