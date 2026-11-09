package io.github.timilehin_dev.ogabot.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.timilehin_dev.ogabot.domain.entity.ToolCall
import io.github.timilehin_dev.ogabot.domain.entity.ToolDescriptor
import io.github.timilehin_dev.ogabot.domain.entity.ToolResult
import io.github.timilehin_dev.ogabot.domain.port.BotRegistry
import io.github.timilehin_dev.ogabot.domain.port.MemoryStore
import io.github.timilehin_dev.ogabot.domain.port.ToolExecutor
import io.github.timilehin_dev.ogabot.domain.port.ToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tier 0 execution backend: the builtin Android shell plus the native Kotlin
 * tools. Always available — no Termux, no install.
 */
class BuiltinTools(
    private val context: Context,
    private val bots: BotRegistry,
    private val memory: MemoryStore
) : ToolProvider {

    private val json = Json { ignoreUnknownKeys = true }

    override fun descriptors(): List<ToolDescriptor> = listOf(
        ToolDescriptor(
            "shell",
            "Run a shell command on the device (/system/bin/sh). Android-native commands (am, pm, content, dumpsys, getprop, settings, logcat), file ops, and system introspection. Runs as the app, inside its sandbox.",
            """{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run"}},"required":["command"]}""",
            defaultTier = "ask"
        ),
        ToolDescriptor(
            "http_fetch",
            "Fetch a URL over HTTP GET and return the response body (truncated to 8000 chars). Use for APIs and simple pages.",
            """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
            defaultTier = "auto"
        ),
        ToolDescriptor(
            "web_read",
            "Fetch a URL and return readable text content (tags stripped, truncated). Better than http_fetch for articles.",
            """{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""",
            defaultTier = "auto"
        ),
        ToolDescriptor(
            "notify",
            "Post a local Android notification to the user with a title and body.",
            """{"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"}},"required":["title","body"]}""",
            defaultTier = "ask"
        ),
        ToolDescriptor(
            "open_uri",
            "Open a URI on the phone (https link, app deep link).",
            """{"type":"object","properties":{"uri":{"type":"string"}},"required":["uri"]}""",
            defaultTier = "ask"
        ),
        ToolDescriptor(
            "memory_write",
            "Save or remove a durable fact in this bot's long-term memory (bounded store).",
            """{"type":"object","properties":{"action":{"type":"string","enum":["add","remove"]},"content":{"type":"string"}},"required":["action","content"]}""",
            defaultTier = "auto"
        ),
        ToolDescriptor(
            "list_bots",
            "List the other bots on this device (names and roles only). Read-only roster.",
            """{"type":"object","properties":{},"required":[]}""",
            defaultTier = "auto"
        ),
        ToolDescriptor(
            "runtime_status",
            "Report which execution backends are available: builtin shell, builtin Python (Chaquopy), and Termux.",
            """{"type":"object","properties":{},"required":[]}""",
            defaultTier = "auto"
        )
    )

    override fun executor(): ToolExecutor = ToolExecutor { call -> execute(call) }

    private suspend fun execute(call: ToolCall): ToolResult {
        val start = System.currentTimeMillis()
        val args = runCatching { json.parseToJsonElement(call.arguments) as JsonObject }
            .getOrElse { JsonObject(emptyMap()) }

        val (ok, output) = try {
            when (call.name) {
                "shell" -> runShell(str(args, "command"))
                "http_fetch" -> fetch(str(args, "url"), stripTags = false)
                "web_read" -> fetch(str(args, "url"), stripTags = true)
                "notify" -> notify(str(args, "title"), str(args, "body"))
                "open_uri" -> openUri(str(args, "uri"))
                "memory_write" -> memoryWrite(call, args)
                "list_bots" -> listBots()
                "runtime_status" -> runtimeStatus()
                else -> false to "Unknown tool: ${call.name}"
            }
        } catch (e: Exception) {
            false to "Tool error: ${e.message}"
        }

        return ToolResult(
            callId = call.id,
            tool = call.name,
            ok = ok,
            output = output,
            durationMs = System.currentTimeMillis() - start,
            tier = ""
        )
    }

    // ------------------------------------------------------------------ shell

    private fun runShell(command: String, timeoutSec: Int = 60): Pair<Boolean, String> {
        if (command.isBlank()) return false to "command is required"
        return try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().readText()
            val finished = proc.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                false to (output.take(8000) + "\n(timeout after ${timeoutSec}s)")
            } else {
                (proc.exitValue() == 0) to output.take(8000)
            }
        } catch (e: Exception) {
            false to "shell error: ${e.message}"
        }
    }

    // ------------------------------------------------------------------ http

    private suspend fun fetch(url: String, stripTags: Boolean): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (!url.startsWith("http")) return@withContext false to "URL must start with http(s)://"
            runCatching {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val req = okhttp3.Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use false to "HTTP ${resp.code}"
                    var body = resp.body?.string() ?: ""
                    if (stripTags) {
                        body = body
                            .replace(Regex("(?is)<(script|style).*?</\\1>"), " ")
                            .replace(Regex("(?s)<[^>]+>"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                    true to body.take(8000)
                }
            }.getOrElse { false to "fetch error: ${it.message}" }
        }

    // ------------------------------------------------------------- android

    private fun notify(title: String, body: String): Pair<Boolean, String> = runCatching {
        val channelId = "ogabot_tools"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(channelId, "Bot actions", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(60))
            .setContentText(body.take(300))
        nm.notify(System.currentTimeMillis().toInt(), builder.build())
        true to "Notification posted."
    }.getOrElse { false to "notify error: ${it.message}" }

    private fun openUri(uri: String): Pair<Boolean, String> = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true to "Opened."
    }.getOrElse { false to "Could not open: ${it.message}" }

    // --------------------------------------------------------------- tools

    private fun memoryWrite(call: ToolCall, args: JsonObject): Pair<Boolean, String> {
        val action = str(args, "action")
        val content = str(args, "content")
        val botId = currentBotId(call)
        return when (action) {
            "add" -> {
                val existing = memory.load(botId)?.content?.trim().orEmpty()
                val merged = if (existing.isEmpty()) content.trim() else existing + "\n" + content.trim()
                memory.save(io.github.timilehin_dev.ogabot.domain.entity.MemoryDoc(botId, merged.take(2200)))
                true to "Saved to memory. Length: ${merged.take(2200).length} chars."
            }
            "remove" -> {
                val existing = memory.load(botId)?.content?.trim().orEmpty()
                val updated = existing.lineSequence()
                    .filterNot { it.contains(content.trim(), ignoreCase = true) }
                    .joinToString("\n")
                memory.save(io.github.timilehin_dev.ogabot.domain.entity.MemoryDoc(botId, updated))
                true to "Removed matching memory."
            }
            else -> false to "Unknown action: $action"
        }
    }

    private fun listBots(): Pair<Boolean, String> {
        val lines = bots.list().joinToString("\n") { "- ${it.name}: ${it.persona.take(80)}" }
        return true to if (lines.isEmpty()) "No other bots." else lines
    }

    private fun runtimeStatus(): Pair<Boolean, String> {
        val shell = runShell("echo ok").first
        val python = runCatching {
            com.chaquo.python.Python.getInstance().getModule("sys").get("version").toString().isNotEmpty()
        }.getOrDefault(false)
        return true to "Builtin shell: ${if (shell) "available" else "unavailable"}\n" +
            "Builtin Python (Chaquopy): ${if (python) "available" else "unavailable"}\n" +
            "Termux: check with the termux_status tool"
    }

    // -------------------------------------------------------------- helpers

    private fun currentBotId(call: ToolCall): String =
        call.arguments.let { runCatching { (json.parseToJsonElement(it) as JsonObject)["botId"]?.jsonPrimitive?.content }.getOrNull() }
            ?: "oga"

    private fun str(args: JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.content ?: ""
}
