package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

/** The built-in Kotlin tool implementations. Executed on Dispatchers.IO by the caller. */
class BuiltinTools(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val descriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            "http_fetch",
            "Fetch a URL via HTTP GET and return the response body (truncated to 8000 chars). Use for APIs and simple pages.",
            "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\",\"description\":\"Absolute http(s) URL\"}},\"required\":[\"url\"]}"
        ),
        ToolDescriptor(
            "web_read",
            "Fetch a URL and return readable text content (tags stripped, truncated). Better than http_fetch for articles.",
            "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"
        ),
        ToolDescriptor(
            "notify",
            "Post a local Android notification to the user with a title and body.",
            "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"body\":{\"type\":\"string\"}},\"required\":[\"title\",\"body\"]}"
        ),
        ToolDescriptor(
            "open_uri",
            "Open a URI on the phone (https link, app deep link). Ask-tier: shows the user what will open.",
            "{\"type\":\"object\",\"properties\":{\"uri\":{\"type\":\"string\"}},\"required\":[\"uri\"]}"
        ),
        ToolDescriptor(
            "memory_write",
            "Save a durable fact to this bot's long-term memory (bounded store). Actions: add, remove.",
            "{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\",\"enum\":[\"add\",\"remove\"]},\"content\":{\"type\":\"string\"}},\"required\":[\"action\",\"content\"]}"
        ),
        ToolDescriptor(
            "list_bots",
            "List the other bots on this device (names and roles only). Read-only roster.",
            "{\"type\":\"object\",\"properties\":{},\"required\":[]}"
        ),
        ToolDescriptor(
            "propose_bot",
            "DRAFT a new bot for the user to approve. Provide name, persona (job description), and avatar color index 0-5. The user MUST approve before the bot is created — never claim a bot was created.",
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"persona\":{\"type\":\"string\"},\"color\":{\"type\":\"integer\"}},\"required\":[\"name\",\"persona\"]}"
        )
    )

    @Serializable
    data class Proposal(val name: String, val persona: String, val color: Int = 0)

    /** Executes a call. Returns result; approval interception happens in the caller (ApprovalGate). */
    suspend fun execute(botId: String, call: ToolCallRequest, args: JsonObject): ToolCallResult {
        val start = System.currentTimeMillis()
        val (ok, output) = try {
            when (call.tool) {
                "http_fetch" -> fetch(str(args, "url"), stripTags = false)
                "web_read" -> fetch(str(args, "url"), stripTags = true)
                "notify" -> notifyBot(str(args, "title"), str(args, "body"))
                "open_uri" -> openUri(str(args, "uri"))
                "memory_write" -> memoryWrite(botId, str(args, "action"), str(args, "content"))
                "list_bots" -> listBots()
                "propose_bot" -> proposeBot(
                    str(args, "name"),
                    str(args, "persona"),
                    (args["color"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                )
                else -> false to "Unknown tool: ${call.tool}"
            }
        } catch (e: Exception) {
            false to ("Tool error: ${e.message}")
        }
        return ToolCallResult(
            callId = call.callId, tool = call.tool, ok = ok, output = output,
            durationMs = System.currentTimeMillis() - start,
            tier = ""
        )
    }

    private fun str(args: JsonObject, key: String): String =
        args[key]?.jsonPrimitive?.content ?: ""

    private suspend fun fetch(url: String, stripTags: Boolean): Pair<Boolean, String> {
        if (!url.startsWith("http")) return false to "URL must start with http(s)://"
        return withContextIO {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
        }
    }

    private fun notifyBot(title: String, body: String): Pair<Boolean, String> {
        // Phase 3 minimal: post via the app's notification channel through a pending intent-free builder.
        // Full per-bot channels land with Phase 4 routines.
        val nMgr = context.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nMgr.createNotificationChannel(
                android.app.NotificationChannel("ogabot_tools", "Bot actions", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val b = android.app.Notification.Builder(context, "ogabot_tools")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title.take(60))
            .setContentText(body.take(300))
        nMgr.notify(System.currentTimeMillis().toInt(), b.build())
        return true to "Notification posted."
    }

    private fun openUri(uri: String): Pair<Boolean, String> {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true to "Opened."
        } catch (e: Exception) {
            false to "Could not open: ${e.message}"
        }
    }

    private fun memoryWrite(botId: String, action: String, content: String): Pair<Boolean, String> {
        val store = MemoryStore(context)
        return when (action) {
            "add" -> {
                store.appendFact(botId, content)
                true to "Saved to memory. Facts now: ${store.loadFacts(botId).size}"
            }
            "remove" -> {
                val removed = store.removeFact(botId, content)
                if (removed) true to "Removed." else false to "No matching fact found."
            }
            else -> false to "Unknown action $action"
        }
    }

    private fun listBots(): Pair<Boolean, String> {
        val bots = BotRegistry.list(context)
        val lines = bots.map { "- ${it.name}: ${it.systemPrompt.take(80)}" }
        return true to if (lines.isEmpty()) "No other bots." else lines.joinToString("\n")
    }

    private var pendingProposal: Proposal? = null

    fun takePendingProposal(): Proposal? {
        val p = pendingProposal
        pendingProposal = null
        return p
    }

    private fun proposeBot(name: String, persona: String, color: Int): Pair<Boolean, String> {
        if (name.isBlank() || persona.isBlank()) return false to "name and persona are required."
        pendingProposal = Proposal(name.trim(), persona.trim(), color)
        val p = pendingProposal!!
        // The caller surfaces this via an approval card; the JSON here tells the model what happened.
        return true to json.encodeToString(
            mapOf(
                "status" to "PROPOSED — waiting for human approval",
                "name" to p.name,
                "persona" to p.persona,
                "color" to p.color,
                "note" to "The user will confirm. Do not repeat this proposal."
            )
        )
    }
}

/** Tiny IO dispatcher helper for expression-style suspend blocks. */
suspend fun <T> withContextIO(block: suspend () -> T): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
