package io.github.timilehin_dev.ogabot

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.timilehin_dev.ogabot.core.BotRegistry
import io.github.timilehin_dev.ogabot.core.ChatMessage
import io.github.timilehin_dev.ogabot.core.ChatStore
import io.github.timilehin_dev.ogabot.core.MemoryStore
import io.github.timilehin_dev.ogabot.core.OpenAiClient
import io.github.timilehin_dev.ogabot.core.ProviderConfig
import io.github.timilehin_dev.ogabot.core.Routine
import io.github.timilehin_dev.ogabot.core.RoutineStore
import io.github.timilehin_dev.ogabot.core.StreamEvent
import io.github.timilehin_dev.ogabot.core.VaultStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Executes one routine run SYNCHRONOUSLY in a background worker:
 *  PHASE 1 (task turn): run the routine task with tools → result
 *  PHASE 2 (wake turn): commit "[routine fired]" event + result into the bot's thread so the
 *           bot wakes with the result and decides follow-ups (notify user, save memory...)
 * Tools here are executed directly (auto-tier for http/web/memory; ask-tier tools are SKIPPED
 * in background runs with an explanatory result — GrokBot: background actions stay safe).
 */
class RoutineRunWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val botId = inputData.getString(RoutineScheduler.EXTRA_BOT) ?: return Result.failure()
        val routineId = inputData.getString(RoutineScheduler.EXTRA_ROUTINE) ?: return Result.failure()
        val app = applicationContext

        val bot = BotRegistry.get(app, botId) ?: return Result.failure()
        val routine = RoutineStore(app).list(botId).firstOrNull { it.id == routineId } ?: return Result.failure()
        val cfg = VaultStore.loadProvider(app) ?: return Result.failure()

        runBlocking {
            val store = ChatStore(app)
            val memory = MemoryStore(app)
            val tools = BuiltinToolsCompat(app)

            // ---- PHASE 1: task turn ----
            val system = bot.systemPrompt +
                memory.promptInjection(botId) +
                "\n\nYou are running a SCHEDULED ROUTINE (the user is not watching live). " +
                "Complete the task using tools where needed. " +
                "Do NOT send messages to anyone, do NOT publish anything. " +
                "Prepare results and leave decisions to the user.\n" +
                tools.toolPromptBlock()

            val taskPrompt = "Routine '" + routine.name + "' fired at " +
                SimpleDateFormat("HH:mm", Locale.US).format(Date()) + ".\nTask: " + routine.task

            val phase1 = runTurnOnce(cfg, system, listOf(ChatMessage("user", taskPrompt)), tools, botId)
            val resultText = phase1.ifBlank { "(no output produced)" }

            // ---- persist run result + commit event into the bot's canonical thread ----
            val ts = System.currentTimeMillis()
            RoutineStore(app).markRan(botId, routineId, ts)
            val stamp = SimpleDateFormat("EEE HH:mm", Locale.US).format(Date(ts))
            store.save(botId, store.load(botId) + ChatMessage(
                "assistant",
                "\u26D3\uFE0F *Routine: " + routine.name + "* (" + routine.scheduleLabel() + ", ran " + stamp + ")\n\n" + resultText
            ))

            // ---- PHASE 2: wake turn — the bot sees its own result and decides what's next ----
            val wakeSystem = bot.systemPrompt +
                memory.promptInjection(botId) +
                "\n\nYour scheduled routine '" + routine.name + "' just completed. Its result is the last message above. " +
                "Decide what to do: if the user should know something now, call notify. Otherwise reply briefly " +
                "with what you did. Never send messages to third parties."
            val history = store.load(botId).takeLast(6)
            val wake = runTurnOnce(cfg, wakeSystem, history, tools, botId)

            // notification: surface the wake summary (or phase-1 result) with the bot's identity
            val summary = wake.ifBlank { resultText }.take(280)
            BotNotifier.notifyRoutine(app, bot.name, routine.name, summary)
        }
        return Result.success()
    }

    /** One full streaming turn with tool-loop support; returns the final text. */
    private fun runTurnOnce(
        cfg: ProviderConfig,
        system: String,
        history: List<ChatMessage>,
        tools: BuiltinToolsCompat,
        botId: String
    ): String {
        var latest: List<ChatMessage> = history
        var finalText = ""
        // up to 3 tool segments per routine turn (bounded autonomy)
        runBlocking {
            for (segment in 0 until 3) {
                val full = listOf(ChatMessage("system", system)) + latest
                val sb = StringBuilder()
                var gotError: String? = null
                OpenAiClient(cfg).streamChat(full).collect { ev ->
                    when (ev) {
                        is StreamEvent.Delta -> sb.append(ev.text)
                        is StreamEvent.Error -> gotError = ev.message
                        StreamEvent.Done -> {}
                    }
                }
                val text = sb.toString()
                val calls = extractCalls(text)
                if (calls.isEmpty()) {
                    finalText = text
                    break
                }
                // execute allowed tools (background: ask-tier SKIPPED) — results are plain strings
                val results = tools.executeAllowed(botId, calls)
                latest = latest + ChatMessage("user",
                    "[Tool results]\n" + results.joinToString("\n") { r -> r })
                if (results.isEmpty()) {
                    finalText = text
                    break
                }
            }
        }
        return finalText
    }

    private fun extractCalls(text: String): List<ToolCallRequestLite> {
        val cleaned = text.replace("```json", "```").replace("```", "\n")
        val start = cleaned.indexOf('[')
        if (start < 0) return emptyList()
        var depth = 0; var end = -1; var inString = false; var esc = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (esc) { esc = false; continue }
            when {
                c == '\\' && inString -> esc = true
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> { depth--; if (depth == 0) { end = i; break } }
            }
        }
        if (end < 0) return emptyList()
        return try {
            val arr = Json.parseToJsonElement(cleaned.substring(start, end + 1)) as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val name = obj["call"]?.toString()?.trim('"') ?: return@mapNotNull null
                ToolCallRequestLite(name, obj["args"]?.toString() ?: "{}")
            }
        } catch (e: Exception) { emptyList() }
    }
}

data class ToolCallRequestLite(val tool: String, val argsJson: String)
