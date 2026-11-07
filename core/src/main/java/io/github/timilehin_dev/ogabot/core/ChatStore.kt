package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Per-bot conversation persistence: threads/<botId>.json (migrated from the Phase 1 single file). */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "threads")
    private val legacy = File(context.filesDir, "oga_thread.json")
    private val json = Json { prettyPrint = true }

    fun load(botId: String): List<ChatMessage> {
        dir.mkdirs()
        val f = File(dir, "$botId.json")
        if (!f.exists() && botId == "oga" && legacy.exists()) {
            // one-time migration of the Phase 1 thread
            val migrated = try { json.decodeFromString<List<ChatMessage>>(legacy.readText()) } catch (e: Exception) { emptyList() }
            f.writeText(json.encodeToString(migrated))
            legacy.delete()
        }
        return try { json.decodeFromString<List<ChatMessage>>(f.readText()) } catch (e: Exception) { emptyList() }
    }

    fun delete(botId: String) {
        File(dir, "$botId.json").delete()
    }

    fun save(botId: String, messages: List<ChatMessage>) {
        dir.mkdirs()
        try {
            File(dir, "$botId.json").writeText(json.encodeToString(messages))
        } catch (e: Exception) {
            // best-effort persistence
        }
    }
}
