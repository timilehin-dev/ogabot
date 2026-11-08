package io.github.timilehin_dev.ogabot.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Unified tool-call parser accepting BOTH formats:
 *  1. Native Hermes-style: <|tool_call_start|>[name(key='value', n=2)]<|tool_call_end|>
 *  2. JSON array: [{"call": "name", "args": {...}}]
 * Returns calls plus the prose left over after stripping the call syntax.
 */
object ToolCallParser {

    data class Parsed(val calls: List<ToolCallRequest>, val prose: String)

    private const val SQ: Char = 39.toChar()   // single quote
    private const val DQ: Char = 34.toChar()   // double quote
    private const val BS: Char = 92.toChar()   // backslash

    fun parse(text: String): Parsed {
        val native = parseNative(text)
        if (native != null) return native
        val jsonCalls = parseJsonArray(text)
        val prose = if (jsonCalls.isNotEmpty()) stripJsonArray(text) else text
        return Parsed(jsonCalls, prose.trim())
    }

    // ---------- native <|tool_call_start|> format ----------

    private val nativeRegex = Regex("<\\|?tool_call_start\\|?>([\\s\\S]*?)<\\|?tool_call_end\\|?>")

    fun parseNative(text: String): Parsed? {
        if (!text.contains("tool_call_start")) return null
        val calls = mutableListOf<ToolCallRequest>()
        var prose = text
        for (m in nativeRegex.findAll(text)) {
            val body = m.groupValues[1].trim()
            val inner = body.trim().removePrefix("[").removeSuffix("]").trim()
            val paren = inner.indexOf('(')
            if (paren <= 0) continue
            val name = inner.substring(0, paren).trim()
            val argsStr = inner.substring(paren + 1).trim().trimEnd(')')
            val args = parseArgsLoose(argsStr)
            calls += ToolCallRequest(
                callId = "c" + System.currentTimeMillis().toString(36) + "_" + calls.size,
                tool = name,
                argsJson = args.toString()
            )
            prose = prose.replace(m.value, " ")
        }
        if (calls.isEmpty()) return null
        return Parsed(calls, prose.trim())
    }

    /** Parse k=v, k2="x" python-ish arg lists into JSON. */
    private fun parseArgsLoose(s: String): JsonObject {
        val map = LinkedHashMap<String, JsonElement>()
        var i = 0
        while (i < s.length) {
            val eq = topLevelEq(s, i) ?: break
            val key = s.substring(i, eq).trim().trim(DQ, SQ)
            var j = eq + 1
            while (j < s.length && s[j] == ' ') j++
            val valuePair = readValue(s, j)
            val valueStr = valuePair.first
            if (key.isNotBlank() && valueStr != null) {
                map[key] = toJsonValue(valueStr)
                i = valuePair.second
            } else {
                i = eq + 1
            }
        }
        return JsonObject(map)
    }

    private fun topLevelEq(s: String, from: Int): Int? {
        var depth = 0
        var inString: Char? = null
        var i = from
        while (i < s.length) {
            val c = s[i]
            if (inString != null) {
                if (c == BS) { i += 2; continue }
                if (c == inString) inString = null
            } else when (c) {
                SQ, DQ -> inString = c
                '{', '[' -> depth++
                '}', ']' -> depth--
                '=' -> if (depth == 0 && i + 1 < s.length && s[i + 1] != '=') return i
            }
            i++
        }
        return null
    }

    private fun readValue(s: String, from: Int): Pair<String?, Int> {
        if (from >= s.length) return null to from
        val open = s[from]
        if (open == SQ || open == DQ) {
            val sb = StringBuilder()
            var i = from + 1
            while (i < s.length) {
                val c = s[i]
                if (c == BS && i + 1 < s.length) { sb.append(s[i + 1]); i += 2; continue }
                if (c == open) return sb.toString() to (i + 1)
                sb.append(c); i++
            }
            return sb.toString() to s.length
        }
        var depth = 0
        var i = from
        while (i < s.length) {
            val c = s[i]
            when {
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> depth--
                c == ',' && depth == 0 -> break
            }
            i++
        }
        return s.substring(from, i).trim() to i
    }

    private fun toJsonValue(raw: String): JsonElement {
        val t = raw.trim()
        if (t.startsWith("{") || t.startsWith("[")) {
            try { return Json.parseToJsonElement(t) } catch (e: Exception) { return JsonPrimitive(t) }
        }
        when (t) {
            "true" -> return JsonPrimitive(true)
            "false" -> return JsonPrimitive(false)
        }
        t.toLongOrNull()?.let { return JsonPrimitive(it) }
        t.toDoubleOrNull()?.let { return JsonPrimitive(it) }
        return JsonPrimitive(t)
    }

    // ---------- JSON-array format ----------

    fun parseJsonArray(text: String): List<ToolCallRequest> {
        val start = findArrayStart(text) ?: return emptyList()
        val end = findArrayEnd(text, start) ?: return emptyList()
        val candidate = text.substring(start, end + 1)
        return try {
            val arr = Json.parseToJsonElement(candidate) as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj["call"]?.toString()?.trim('"') ?: return@mapNotNull null
                val args = obj["args"]?.toString() ?: "{}"
                ToolCallRequest(
                    callId = "c" + System.currentTimeMillis().toString(36),
                    tool = name.trim(),
                    argsJson = args
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun stripJsonArray(text: String): String {
        val start = findArrayStart(text) ?: return text
        val end = findArrayEnd(text, start) ?: return text
        return (text.substring(0, start) + " " + text.substring(end + 1)).trim()
    }

    private fun findArrayStart(text: String): Int? {
        val idx = text.indexOf('[')
        return if (idx >= 0) idx else null
    }

    private fun findArrayEnd(text: String, start: Int): Int? {
        var depth = 0; var inString = false; var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) { escape = false; continue }
            when {
                c == BS && inString -> escape = true
                c == DQ -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }
}
