package io.github.timilehin_dev.ogabot.core

import kotlinx.serialization.Serializable

/** How a tool is gated for a bot. */
enum class Tier { AUTO, ASK, NEVER;

    companion object {
        fun parse(s: String?): Tier = when (s?.lowercase()) {
            "auto" -> AUTO
            "never" -> NEVER
            else -> ASK
        }
    }
}

@Serializable
data class ToolDescriptor(
    val name: String,
    val description: String,
    /** JSON-schema-ish parameter map the LLM sees. */
    val parameters: String
)

/** A tool call the model asked for — pending execution. */
@Serializable
data class ToolCallRequest(
    val callId: String,
    val tool: String,
    val argsJson: String
)

/** Outcome of one executed tool call. */
@Serializable
data class ToolCallResult(
    val callId: String,
    val tool: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long,
    val tier: String
)

/** Per-bot tool grants: tool name -> tier. Missing tool = default ASK. */
@Serializable
data class ToolGrants(val tiers: Map<String, String> = emptyMap()) {
    fun tierFor(tool: String): Tier = Tier.parse(tiers[tool])

    companion object {
        val DEFAULTS = ToolGrants(
            mapOf(
                "http_fetch" to "auto",
                "web_read" to "auto",
                "notify" to "ask",
                "open_uri" to "ask",
                "memory_write" to "auto",
                "list_bots" to "auto",
                "propose_bot" to "ask",
                "shell" to "ask",
                "schedule_routine" to "auto",
                "list_routines" to "auto"
            )
        )
    }
}
