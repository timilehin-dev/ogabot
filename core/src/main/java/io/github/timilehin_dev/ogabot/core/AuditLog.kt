package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Append-only audit trail: audit/<yyyy-mm>.jsonl — every tool call, args, result, tier decision. */
class AuditLog(context: Context) {
    private val dir = File(context.filesDir, "audit")
    private val json = Json { ignoreUnknownKeys = true }

    fun record(entry: ToolCallResult, botId: String) {
        try {
            dir.mkdirs()
            val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
                .format(java.util.Date())
            val f = File(dir, "$month.jsonl")
            val row = json.encodeToString(
                AuditRow(
                    ts = System.currentTimeMillis(),
                    botId = botId,
                    callId = entry.callId,
                    tool = entry.tool,
                    ok = entry.ok,
                    output = entry.output.take(2000),
                    durationMs = entry.durationMs,
                    tier = entry.tier
                )
            )
            f.appendText(row + "\n")
        } catch (e: Exception) {
            // audit must never crash the turn
        }
    }

    fun recent(limit: Int = 100): List<AuditRow> {
        val all = dir.listFiles()?.filter { it.extension == "jsonl" }
            ?.sortedBy { it.name }?.takeLast(2) ?: return emptyList()
        return all.flatMap { f ->
            f.readLines().filter { it.isNotBlank() }.mapNotNull {
                try { json.decodeFromString<AuditRow>(it) } catch (e: Exception) { null }
            }
        }.takeLast(limit)
    }
}

@Serializable
data class AuditRow(
    val ts: Long,
    val botId: String,
    val callId: String,
    val tool: String,
    val ok: Boolean,
    val output: String,
    val durationMs: Long,
    val tier: String
)
