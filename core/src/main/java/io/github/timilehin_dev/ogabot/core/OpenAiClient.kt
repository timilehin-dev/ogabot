package io.github.timilehin_dev.ogabot.core

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

/** Minimal OpenAI-compatible streaming chat client (works with Ollama, OpenAI, OpenRouter...). */
class OpenAiClient(private val cfg: ProviderConfig) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun streamChat(messages: List<ChatMessage>): Flow<StreamEvent> = callbackFlow {
        val body = json.encodeToString(
            StreamRequest(
                model = cfg.model,
                messages = messages.map { StreamMessage(it.role, it.content) },
                stream = true,
                temperature = cfg.temperature
            )
        )
        val request = Request.Builder()
            .url(cfg.endpoint.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer " + cfg.apiKey)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    trySend(StreamEvent.Done)
                    close()
                    return
                }
                try {
                    val chunk = json.decodeFromString<StreamChunk>(data)
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) trySend(StreamEvent.Delta(delta))
                } catch (e: Exception) {
                    // ignore malformed keep-alive lines
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
                trySend(StreamEvent.Done)
                close()
            }
        }

        val es = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { es.cancel() }
    }
}
