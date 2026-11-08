package io.github.timilehin_dev.ogabot.agent

import io.github.timilehin_dev.ogabot.domain.entity.Message
import io.github.timilehin_dev.ogabot.domain.entity.ProviderConfig
import io.github.timilehin_dev.ogabot.domain.entity.ToolDescriptor
import io.github.timilehin_dev.ogabot.domain.port.ModelProvider
import io.github.timilehin_dev.ogabot.domain.port.StreamEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible streaming chat client. Works with OpenAI, OpenRouter,
 * Ollama, DeepSeek, Groq, or any `/chat/completions` endpoint.
 *
 * Handles both native function calling (structured `tool_calls` deltas) and
 * the wire shapes providers actually send — including the full tool-call
 * object in a single chunk.
 */
class OpenAiModelProvider(private val config: ProviderConfig) : ModelProvider {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDescriptor>
    ): Flow<StreamEvent> = callbackFlow<StreamEvent> {
        val body = buildRequest(messages, tools)
        val request = Request.Builder()
            .url(config.endpoint.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + config.apiKey)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val accumulator = ToolCallAccumulator()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == "[DONE]") {
                    accumulator.flush().forEach { trySend(StreamEvent.ToolCallEvent(it)) }
                    trySend(StreamEvent.Done)
                    close()
                    return
                }
                try {
                    val chunk = json.decodeFromString<Chunk>(data)
                    val choice = chunk.choices.firstOrNull() ?: return
                    choice.delta?.content?.let { if (it.isNotEmpty()) trySend(StreamEvent.Delta(it)) }
                    choice.delta?.toolCalls?.forEach { accumulator.accept(it) }
                    // Some providers send the complete call in `message` on a final chunk.
                    choice.message?.toolCalls?.forEach { accumulator.accept(it) }
                    if (choice.finishReason == "tool_calls" || choice.finishReason == "function_call") {
                        accumulator.flush().forEach { trySend(StreamEvent.ToolCallEvent(it)) }
                        trySend(StreamEvent.Done)
                        close()
                    }
                } catch (e: Exception) {
                    // Ignore malformed keep-alive / comment lines.
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = when {
                    response != null && response.code == 401 ->
                        "Authentication failed. Check the API key in Settings."
                    response != null && response.code == 404 ->
                        "Endpoint or model not found (404). Check endpoint URL and model name."
                    response != null && response.code == 429 ->
                        "Rate limited or quota exceeded (429). Try again shortly."
                    t is IOException ->
                        "Network error: " + (t.message ?: "no connection")
                    else ->
                        "Provider error: " + (t?.message ?: response?.message ?: "unknown")
                }
                trySend(StreamEvent.Error(msg))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                accumulator.flush().forEach { trySend(StreamEvent.ToolCallEvent(it)) }
                trySend(StreamEvent.Done)
                close()
            }
        }

        val eventSource = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    private fun buildRequest(messages: List<Message>, tools: List<ToolDescriptor>): String {
        val root = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            put("stream", true)
            put("temperature", config.temperature)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", Json.parseToJsonElement(t.parameters))
                            })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
        }
        return root.toString()
    }
}

@Serializable
data class Chunk(val choices: List<Choice> = emptyList())

@Serializable
data class Choice(
    val delta: Delta? = null,
    val message: MessageBlock? = null,
    val finishReason: String? = null
)

@Serializable
data class Delta(
    val content: String? = null,
    val toolCalls: List<ToolCallDelta> = emptyList()
)

@Serializable
data class MessageBlock(val toolCalls: List<ToolCallDelta> = emptyList())
