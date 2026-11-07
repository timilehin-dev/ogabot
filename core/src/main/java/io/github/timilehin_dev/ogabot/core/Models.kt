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
data class StreamDelta(val content: String? = null)

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    object Done : StreamEvent()
}
