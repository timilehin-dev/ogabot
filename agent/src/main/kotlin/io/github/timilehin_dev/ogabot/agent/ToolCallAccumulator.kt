package io.github.timilehin_dev.ogabot.agent

import io.github.timilehin_dev.ogabot.domain.entity.ToolCall

/**
 * Accumulates streamed function-calling fragments across SSE chunks.
 *
 * Providers differ in how they send tool calls:
 *  - fragmented deltas: `delta.tool_calls[0].function.arguments` arrives in pieces
 *  - one full object:   `choices[0].message.tool_calls[0]` arrives complete
 *  - both:              some send the name early and the arguments late
 *
 * This accumulator handles all three. Call [flush] at end-of-stream (or on
 * `finish_reason == "tool_calls"`) to get the complete calls.
 */
class ToolCallAccumulator {

    private val names = HashMap<Int, String>()
    private val args = HashMap<Int, StringBuilder>()
    private val ids = HashMap<Int, String>()

    fun accept(delta: ToolCallDelta) {
        val idx = delta.index ?: 0
        delta.id?.let { ids[idx] = it }
        delta.function?.name?.let { names.putIfAbsent(idx, it) }
        delta.function?.arguments?.let { args.getOrPut(idx) { StringBuilder() }.append(it) }
    }

    /** Returns all accumulated calls and resets state. */
    fun flush(): List<ToolCall> = names.map { (idx, name) ->
        ToolCall(
            id = ids[idx] ?: "call_$idx",
            name = name,
            arguments = args[idx]?.toString()?.ifBlank { "{}" } ?: "{}"
        )
    }.also {
        names.clear()
        args.clear()
        ids.clear()
    }

    fun isEmpty(): Boolean = names.isEmpty()
}

/** Mirrors the OpenAI `tool_calls` delta wire shape. */
@kotlinx.serialization.Serializable
data class ToolCallDelta(
    val index: Int? = null,
    val id: String? = null,
    val function: FunctionDelta? = null
)

@kotlinx.serialization.Serializable
data class FunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)
