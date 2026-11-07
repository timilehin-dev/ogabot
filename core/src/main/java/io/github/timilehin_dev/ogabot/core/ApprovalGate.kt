package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * GrokBot-style Auto Review:
 *  - tier NEVER -> refused
 *  - tier ASK   -> approval card (Allow once / Always allow -> Custom Rule / Deny)
 *  - tier AUTO  -> runs, but still recorded in audit
 *  - Custom Rules in plain language: "when bot wants to X -> ask first" beats allow rules
 *  - Cards raised by background work expire after 30 minutes (action NOT taken)
 */
class ApprovalGate(context: Context) {

    @Serializable
    data class CustomRule(val pattern: String, val askFirst: Boolean)

    private val file = File(context.filesDir, "custom_rules.json")
    private val json = Json { ignoreUnknownKeys = true }

    val rules: MutableList<CustomRule> = load()

    private fun load(): MutableList<CustomRule> = try {
        if (file.exists()) json.decodeFromString<List<CustomRule>>(file.readText()).toMutableList()
        else mutableListOf()
    } catch (e: Exception) { mutableListOf() }

    fun save() {
        try { file.writeText(json.encodeToString(rules.toList())) } catch (e: Exception) {}
    }

    fun addRule(pattern: String, askFirst: Boolean) {
        rules.add(CustomRule(pattern.trim(), askFirst))
        val asks = rules.count { it.askFirst }
        val allows = rules.count { !it.askFirst }
        if (allows > 20) {
            val oldestAllow = rules.indexOfFirst { !it.askFirst }
            if (oldestAllow >= 0) rules.removeAt(oldestAllow)
        }
        if (asks > 20) {
            val oldestAsk = rules.indexOfFirst { it.askFirst }
            if (oldestAsk >= 0) rules.removeAt(oldestAsk)
        }
        save()
    }

    fun removeRule(index: Int) { if (index in rules.indices) { rules.removeAt(index); save() } }

    fun askFirstMatch(tool: String, argsJson: String): Boolean =
        rules.filter { it.askFirst }.any { ruleMatches(it.pattern, tool, argsJson) }

    fun autoAllowMatch(tool: String, argsJson: String): Boolean =
        rules.filter { !it.askFirst }.any { ruleMatches(it.pattern, tool, argsJson) }

    private fun ruleMatches(pattern: String, tool: String, argsJson: String): Boolean {
        if (pattern.isBlank()) return false
        val p = pattern.lowercase()
        val hay = (tool + " " + argsJson).lowercase()
        val tokens = p.split(Regex("[^a-z0-9]+")).filter { it.length > 3 }
        if (tokens.isEmpty()) return false
        return tokens.any { hay.contains(it) }
    }

    sealed class Decision {
        object Allow : Decision()
        object Deny : Decision()
        data class Ask(val background: Boolean) : Decision()
    }

    fun decide(botId: String, tool: String, argsJson: String, background: Boolean): Decision {
        if (askFirstMatch(tool, argsJson)) return Decision.Ask(background)
        return Decision.Allow
    }

    companion object {
        const val BACKGROUND_EXPIRY_MS: Long = 30 * 60 * 1000L
    }
}
