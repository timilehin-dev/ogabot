package io.github.timilehin_dev.ogabot.platform

import android.content.Context
import io.github.timilehin_dev.ogabot.domain.port.ExecResult
import io.github.timilehin_dev.ogabot.domain.port.TermuxBackend
import io.github.timilehin_dev.ogabot.domain.port.TermuxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Termux backend — tier 2 execution. Talks to a local HTTP bridge in Termux
 * (the zbridge pattern: /health, /run, /read, /write, /daemon).
 *
 * When Termux is absent, every call fails cleanly — the app is fully functional
 * without it (the builtin shell and Chaquopy Python cover the everyday).
 */
class TermuxBridge(private val context: Context) : TermuxBackend {

    private val ports = listOf(8022, 8765, 9090, 18080)
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private var cachedBase: String? = null

    override val state: TermuxState
        get() = when {
            !isTermuxInstalled() -> TermuxState.NOT_INSTALLED
            cachedBase != null -> TermuxState.RUNNING
            else -> TermuxState.INSTALLED
        }

    private fun isTermuxInstalled(): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    }.getOrDefault(false)

    private fun discover(): String? {
        cachedBase?.let { return it }
        for (port in ports) {
            val url = "http://127.0.0.1:$port/health"
            runCatching {
                val req = Request.Builder().url(url).header("X-Key", BRIDGE_KEY).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        cachedBase = "http://127.0.0.1:$port"
                        return cachedBase
                    }
                }
            }
        }
        return null
    }

    override suspend fun exec(command: String, timeoutSec: Int): ExecResult = withContext(Dispatchers.IO) {
        val base = discover() ?: return@withContext ExecResult(
            false,
            "Termux bridge not reachable. Open Termux (with the bridge running) and retry — everything else works without it."
        )
        runCatching {
            val payload = "{\"cmd\": " + kotlinx.serialization.json.JsonPrimitive(command) + ", \"timeout\": $timeoutSec}"
            val req = Request.Builder()
                .url("$base/run")
                .header("X-Key", BRIDGE_KEY)
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ExecResult(false, "bridge HTTP ${resp.code}")
                ExecResult(true, (resp.body?.string() ?: "").take(8000))
            }
        }.getOrElse { ExecResult(false, "bridge error: ${it.message}") }
    }

    override suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val base = discover() ?: return@withContext null
        runCatching {
            val req = Request.Builder().url("$base/read?path=" + java.net.URLEncoder.encode(path, "UTF-8"))
                .header("X-Key", BRIDGE_KEY).build()
            client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        }.getOrNull()
    }

    override suspend fun writeFile(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val base = discover() ?: return@withContext false
        runCatching {
            val payload = "{\"path\": " + kotlinx.serialization.json.JsonPrimitive(path) +
                ", \"content\": " + kotlinx.serialization.json.JsonPrimitive(content) + "}"
            val req = Request.Builder().url("$base/write").header("X-Key", BRIDGE_KEY)
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull())).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun startDaemon(name: String, command: String): Boolean = withContext(Dispatchers.IO) {
        val base = discover() ?: return@withContext false
        runCatching {
            val payload = "{\"name\": " + kotlinx.serialization.json.JsonPrimitive(name) +
                ", \"cmd\": " + kotlinx.serialization.json.JsonPrimitive(command) + "}"
            val req = Request.Builder().url("$base/daemon").header("X-Key", BRIDGE_KEY)
                .post(payload.toRequestBody("application/json".toMediaTypeOrNull())).build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun stopDaemon(name: String): Boolean = withContext(Dispatchers.IO) {
        val base = discover() ?: return@withContext false
        runCatching {
            val req = Request.Builder().url("$base/daemon?name=" + java.net.URLEncoder.encode(name, "UTF-8"))
                .header("X-Key", BRIDGE_KEY).delete().build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    companion object {
        /** Matches the Termux-side bridge setup. */
        const val BRIDGE_KEY = "zafiro-bridge-2026"
    }
}
