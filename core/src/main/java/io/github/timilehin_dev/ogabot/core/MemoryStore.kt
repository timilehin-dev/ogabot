package io.github.timilehin_dev.ogabot.core

import android.content.Context
import java.io.File

/** Per-bot long-term memory: memory/profile.md (human+bot editable) and memory/facts.jsonl. */
class MemoryStore(context: Context) {
    private val root = File(context.filesDir, "bots")

    private fun profileFile(botId: String): File =
        File(File(File(root, botId), "memory"), "profile.md")

    private fun factsFile(botId: String): File =
        File(File(File(root, botId), "memory"), "facts.jsonl")

    fun loadProfile(botId: String): String =
        profileFile(botId).takeIf { it.exists() }?.readText() ?: ""

    fun saveProfile(botId: String, text: String) {
        profileFile(botId).apply { parentFile?.mkdirs() }.writeText(text)
    }

    fun loadFacts(botId: String): List<String> =
        factsFile(botId).takeIf { it.exists() }
            ?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    fun appendFact(botId: String, fact: String) {
        factsFile(botId).apply { parentFile?.mkdirs() }.appendText(fact.trim() + "\n")
    }

    fun removeFact(botId: String, match: String): Boolean {
        val f = factsFile(botId)
        if (!f.exists()) return false
        val lines = f.readLines().toMutableList()
        val it2 = lines.firstOrNull { it.contains(match.trim(), ignoreCase = true) } ?: return false
        lines.remove(it2)
        f.writeText(lines.filter { it.isNotBlank() }.joinToString("\n") + (if (lines.isEmpty()) "" else "\n"))
        return true
    }

    /** Memory injected into the system prompt every turn. */
    fun promptInjection(botId: String): String {
        val profile = loadProfile(botId)
        val facts = loadFacts(botId)
        if (profile.isBlank() && facts.isEmpty()) return ""
        val sb = StringBuilder("\n\n[Long-term memory about the user — keep this in mind]\n")
        if (profile.isNotBlank()) sb.append(profile.trim()).append("\n")
        if (facts.isNotEmpty()) facts.forEach { sb.append("- ").append(it).append("\n") }
        return sb.toString()
    }
}
