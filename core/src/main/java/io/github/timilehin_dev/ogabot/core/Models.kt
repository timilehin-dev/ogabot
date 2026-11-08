package io.github.timilehin_dev.ogabot.core

import kotlinx.serialization.Serializable

@Serializable
data class ProviderConfig(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.7
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis()
)

@Serializable
data class StreamMessage(val role: String, val content: String)

@Serializable
data class StreamRequest(
    val model: String,
    val messages: List<StreamMessage>,
    val stream: Boolean,
    val temperature: Double
)

@Serializable
data class StreamChunk(val choices: List<StreamChoice> = emptyList())

@Serializable
data class StreamChoice(val delta: StreamDelta = StreamDelta(), val finish_reason: String? = null)

@Serializable
data class StreamDelta(
    val content: String? = null,
    val tool_calls: List<ToolCallDelta> = emptyList()
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val function: FunctionDelta? = null
)

@Serializable
data class FunctionDelta(val name: String? = null, val arguments: String? = null)

/** OpenAI tools schema entry for the request. */
@Serializable
data class ToolSchemaEntry(
    val type: String = "function",
    val function: ToolFunctionDef
)

@Serializable
data class ToolFunctionDef(
    val name: String,
    val description: String,
    val parameters: String
)

@Serializable
data class StreamRequestWithTools(
    val model: String,
    val messages: List<StreamMessage>,
    val stream: Boolean,
    val tools: List<ToolSchemaEntry>? = null,
    val tool_choice: String = "auto"
)

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    /** A complete tool call assembled from streaming deltas (arguments JSON complete). */
    data class ToolCallEvt(val callId: String, val name: String, val argsJson: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    object Done : StreamEvent()
}
