package io.github.timilehin_dev.ogabot

import android.content.Context
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

/** Background-safe tool execution for routine runs (ask-tier tools are skipped, not blocked). */
class BuiltinToolsCompat(private val context: Context) {

    private val inner = io.github.timilehin_dev.ogabot.core.BuiltinTools(context)
    private val backgroundAllowed = setOf("http_fetch", "web_read", "memory_write", "list_bots")

    fun toolPromptBlock(): String =
        inner.descriptors.filter { it.name in backgroundAllowed }
            .joinToString("\n") { "- ${it.name}: ${it.description} | params: ${it.parameters}" } +
            "\n(Available in background runs: " + backgroundAllowed.joinToString(", ") + ")"

    suspend fun executeAllowed(botId: String, calls: List<ToolCallRequestLite>): List<String> {
        val out = mutableListOf<String>()
        for (call in calls) {
            if (call.tool !in backgroundAllowed) {
                out += "(" + call.tool + ") SKIPPED: not allowed in background runs."
                continue
            }
            val args = try {
                kotlinx.serialization.json.Json.parseToJsonElement(call.argsJson) as kotlinx.serialization.json.JsonObject
            } catch (e: Exception) { kotlinx.serialization.json.JsonObject(emptyMap()) }
            val r = inner.execute(
                botId,
                io.github.timilehin_dev.ogabot.core.ToolCallRequest(
                    callId = "bg" + System.currentTimeMillis().toString(36),
                    tool = call.tool,
                    argsJson = call.argsJson
                ),
                args
            )
            out += "(" + r.tool + ") " + (if (r.ok) "OK: " else "ERROR: ") + r.output
        }
        return out
    }
}

/** Per-bot notification identity for routine results. */
object BotNotifier {
    fun notifyRoutine(context: Context, botName: String, routineName: String, summary: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "bot_" + botName.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(channelId, botName, android.app.NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Routines and updates from " + botName }
            )
        }
        val b = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(botName + " \u00b7 " + routineName)
            .setContentText(summary)
            .setStyle(android.app.Notification.BigTextStyle().bigText(summary))
        nm.notify(routineName.hashCode(), b.build())
    }
}
