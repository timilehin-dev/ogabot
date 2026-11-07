package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Phase 1 persistence: one JSON snapshot per seeded bot thread. Room index lands in Phase 2. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "oga_thread.json")
    private val json = Json { prettyPrint = true }

    fun load(): List<ChatMessage> = try {
        if (file.exists()) json.decodeFromString<List<ChatMessage>>(file.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(messages: List<ChatMessage>) {
        try {
            file.writeText(json.encodeToString(messages))
        } catch (e: Exception) {
            // best-effort persistence; never crash the chat over a write failure
        }
    }
}
