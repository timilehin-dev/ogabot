package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull

/**
 * Termux deep-access bridge client (the "shared computer" layer).
 * Reuses the battle-tested zbridge pattern: localhost HTTP /run with X-Key auth.
 * When Termux is dead: clean failure — the app remains fully functional without it.
 */
class TermuxBridge(private val context: Context) {

    private val ports = listOf(8022, 8765, 9090, 18080)
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private fun key(): String {
        // bridge key matches the existing Termux-side bridge setup
        return "zafiro-bridge-2026"
    }

    /** Probe for a live bridge; returns base URL or null. */
    fun discover(): String? {
        for (port in ports) {
            val url = "http://127.0.0.1:$port/health"
            try {
                val req = okhttp3.Request.Builder().url(url)
                    .header("X-Key", key()).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) return "http://127.0.0.1:$port"
                }
            } catch (e: Exception) { /* try next */ }
        }
        return null
    }

    /** Run a shell command in Termux; returns output or a clean failure message. */
    fun run(command: String, timeoutSec: Int = 60): Pair<Boolean, String> {
        val base = discover() ?: return false to
            "Termux bridge not reachable. Open Termux (with the bridge running) and retry — everything else works without it."
        return try {
            val payload = "{\"cmd\": " + JsonPrimitive(command).toString() + "}"
            val body = okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(), payload)
            val req = okhttp3.Request.Builder().url("$base/run")
                .header("X-Key", key())
                .post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false to "bridge HTTP ${resp.code}"
                val text = resp.body?.string() ?: ""
                true to text.take(8000)
            }
        } catch (e: Exception) {
            false to ("bridge error: " + (e.message ?: "unknown"))
        }
    }
}
