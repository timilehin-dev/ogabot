package io.github.timilehin_dev.ogabot.data

import android.content.Context
import io.github.timilehin_dev.ogabot.domain.entity.Message
import io.github.timilehin_dev.ogabot.domain.port.ChatStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Per-bot conversation persistence: threads/<botId>.json. */
class ChatFileStore(private val context: Context) : ChatStore {

    private val dir = File(context.filesDir, "threads")
    private val legacy = File(context.filesDir, "oga_thread.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(botId: String) = File(dir, "$botId.json")

    override fun load(botId: String): List<Message> {
        dir.mkdirs()
        val f = file(botId)
        if (!f.exists() && botId == "oga" && legacy.exists()) {
            // one-time migration of a pre-multi-bot thread
            val migrated = runCatching { json.decodeFromString<List<Message>>(legacy.readText()) }.getOrDefault(emptyList())
            f.writeText(json.encodeToString(migrated))
            legacy.delete()
        }
        return runCatching { json.decodeFromString<List<Message>>(f.readText()) }.getOrDefault(emptyList())
    }

    override fun save(botId: String, messages: List<Message>) {
        dir.mkdirs()
        runCatching { file(botId).writeText(json.encodeToString(messages)) }
    }

    override fun delete(botId: String) {
        file(botId).delete()
    }
}
