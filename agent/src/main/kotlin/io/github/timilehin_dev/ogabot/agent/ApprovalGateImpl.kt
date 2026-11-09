package io.github.timilehin_dev.ogabot.agent

import io.github.timilehin_dev.ogabot.domain.entity.ApprovalRequest
import io.github.timilehin_dev.ogabot.domain.port.ApprovalGate
import io.github.timilehin_dev.ogabot.domain.port.CustomRule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * GrokBot-style Auto Review:
 *  - tier NEVER -> refused
 *  - tier ASK   -> approval card
 *  - tier AUTO  -> runs, still audited
 *  - Custom Rules in plain language; ask-first wins ties
 *  - Background cards expire after 30 minutes (action NOT taken)
 */
class ApprovalGateImpl(private val rulesFile: File) : ApprovalGate {

    private val json = Json { ignoreUnknownKeys = true }
    private val rules: MutableList<CustomRule> = load()

    private fun load(): MutableList<CustomRule> = runCatching {
        if (rulesFile.exists()) json.decodeFromString<List<CustomRule>>(rulesFile.readText()).toMutableList()
        else mutableListOf()
    }.getOrDefault(mutableListOf())

    private fun persist() {
        runCatching { rulesFile.parentFile?.mkdirs(); rulesFile.writeText(json.encodeToString(rules.toList())) }
    }

    override fun addRule(pattern: String, askFirst: Boolean) {
        rules.add(CustomRule(pattern.trim(), askFirst))
        val asks = rules.count { it.askFirst }
        val allows = rules.count { !it.askFirst }
        if (asks > 20) rules.removeAt(rules.indexOfFirst { it.askFirst })
        if (allows > 20) rules.removeAt(rules.indexOfFirst { !it.askFirst })
        persist()
    }

    override fun removeRule(index: Int) {
        if (index in rules.indices) {
            rules.removeAt(index)
            persist()
        }
    }

    override fun rules(): List<CustomRule> = rules.toList()

    override fun decide(botId: String, tool: String, argsJson: String, background: Boolean): ApprovalGate.Decision {
        if (askFirstMatch(tool, argsJson)) return ApprovalGate.Decision.Ask(background)
        return ApprovalGate.Decision.Allow
    }

    private fun askFirstMatch(tool: String, argsJson: String): Boolean =
        rules.filter { it.askFirst }.any { ruleMatches(it.pattern, tool, argsJson) }

    private fun ruleMatches(pattern: String, tool: String, argsJson: String): Boolean {
        if (pattern.isBlank()) return false
        val tokens = pattern.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 }
        if (tokens.isEmpty()) return false
        val hay = (tool + " " + argsJson).lowercase()
        return tokens.any { hay.contains(it) }
    }

    companion object {
        const val BACKGROUND_EXPIRY_MS: Long = 30 * 60 * 1000L
    }
}
