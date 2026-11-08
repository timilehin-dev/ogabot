package io.github.timilehin_dev.ogabot.core

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PyToolDef(
    val name: String,
    val description: String,
    val file: String,
    val params: String = "{}"
)

/**
 * Chaquopy-backed Python tool runtime.
 * - Bundled starter tools ship in assets/python/, copied to files/python/ on first run
 * - User/bot-created tools land in files/python/ too (Tool Lab, Phase 5)
 * - Registry: files/python_tools.json
 * - Contract: every tool module defines main(d: dict) -> str
 */
class PythonTools(private val context: Context) {

    private val dir = File(context.filesDir, "python")
    private val registryFile = File(context.filesDir, "python_tools.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    val starterTools: Map<String, String> = mapOf(
        "json_api" to """"
import json, requests

def main(d):
    url = d.get("url", "")
    if not url:
        return "ERROR: url required"
    r = requests.get(url, timeout=25, headers={"User-Agent": "Mozilla/5.0"})
    if r.status_code >= 400:
        return "ERROR: HTTP " + str(r.status_code)
    try:
        data = r.json()
        return json.dumps(data, indent=1)[:8000]
    except Exception:
        return r.text[:8000]
""".trimIndent(),
        "page_text" to """"
import requests
from bs4 import BeautifulSoup

def main(d):
    url = d.get("url", "")
    if not url:
        return "ERROR: url required"
    r = requests.get(url, timeout=25, headers={"User-Agent": "Mozilla/5.0"})
    if r.status_code >= 400:
        return "ERROR: HTTP " + str(r.status_code)
    soup = BeautifulSoup(r.text, "html.parser")
    for t in soup(["script", "style", "nav", "header", "footer"]):
        t.decompose()
    text = soup.get_text(" ", strip=True)
    title = soup.title.string.strip() if soup.title and soup.title.string else ""
    return (("[TITLE] " + title + "\\n\\n") if title else "") + text[:8000]
""".trimIndent(),
        "text_stats" to """"
def main(d):
    text = d.get("text", "")
    if not text:
        return "ERROR: text required"
    words = text.split()
    return "chars=%d words=%d lines=%d" % (len(text), len(words), text.count("\\n") + 1)
""".trimIndent()
    )

    fun ensureBootstrapped() {
        dir.mkdirs()
        val reg = if (registryFile.exists()) {
            try { json.decodeFromString<List<PyToolDef>>(registryFile.readText()) } catch (e: Exception) { emptyList() }
        } else emptyList()
        if (reg.isEmpty()) {
            val defs = starterTools.map { (name, code) ->
                File(dir, "$name.py").writeText(code)
                PyToolDef(
                    name = name,
                    description = when (name) {
                        "json_api" -> "Fetch a URL returning JSON and pretty-print the parsed structure (auto-tier)."
                        "page_text" -> "Fetch a web page and return readable text with title (auto-tier)."
                        else -> "Return character/word/line statistics for the given text (auto-tier)."
                    },
                    file = "$name.py",
                    params = when (name) {
                        "json_api" -> "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"
                        "page_text" -> "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"
                        else -> "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}"
                    }
                )
            }
            registryFile.writeText(json.encodeToString(defs))
        }
    }

    fun list(): List<PyToolDef> = ensureDir().let {
        try { json.decodeFromString<List<PyToolDef>>(registryFile.readText()) } catch (e: Exception) { emptyList() }
    }

    fun descriptors(): List<ToolDescriptor> = list().map {
        ToolDescriptor(it.name, it.description, it.params)
    }

    fun source(toolName: String): String? = File(dir, "$toolName.py").takeIf { it.exists() }?.readText()

    fun saveTool(def: PyToolDef, code: String): Boolean {
        dir.mkdirs()
        File(dir, def.file).writeText(code)
        val cur = list().filter { it.name != def.name }
        registryFile.writeText(json.encodeToString(cur + def))
        return true
    }

    fun removeTool(toolName: String): Boolean {
        val def = list().firstOrNull { it.name == toolName } ?: return false
        File(dir, def.file).delete()
        registryFile.writeText(json.encodeToString(list().filter { it.name != toolName }))
        return true
    }

    /** Execute with named kwargs; returns output text. Blocking — call from Dispatchers.IO. */
    fun execute(toolName: String, kwargs: Map<String, String>): Pair<Boolean, String> {
        return try {
            val py = Python.getInstance()
            // ensure our tool dir is importable
            val sys = py.getModule("sys")
            val pathList = sys.get("path")
            if (pathList != null && !pathList.toString().contains(dir.absolutePath)) {
                pathList.callAttr("append", dir.absolutePath)
            }
            val module = py.getModule(toolName)
            val builtins = py.getModule("builtins")
            val dict = builtins.callAttr("dict")
            kwargs.forEach { (k, v) -> dict.callAttr("__setitem__", k, v) }
            val result = module.callAttr("main", dict)
            true to (result.toString().take(8000))
        } catch (e: Exception) {
            false to ("python error: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun ensureDir(): File { dir.mkdirs(); return dir }
}
