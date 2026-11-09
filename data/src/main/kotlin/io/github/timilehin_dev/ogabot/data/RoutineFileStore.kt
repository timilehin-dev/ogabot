package io.github.timilehin_dev.ogabot.data

import android.content.Context
import io.github.timilehin_dev.ogabot.domain.entity.Routine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** File-backed routine store: routines/<botId>.json. Routines are also human-readable Markdown. */
class RoutineFileStore(private val context: Context) {

    private val dir = File(context.filesDir, "routines")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun file(botId: String) = File(dir, "$botId.json")

    fun list(botId: String): List<Routine> {
        val f = file(botId)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<Routine>>(f.readText()) }.getOrNull() ?: emptyList()
    }

    fun all(): List<Routine> {
        dir.mkdirs()
        return dir.listFiles()?.filter { it.extension == "json" }?.flatMap {
            runCatching { json.decodeFromString<List<Routine>>(it.readText()) }.getOrNull() ?: emptyList()
        } ?: emptyList()
    }

    fun save(botId: String, routines: List<Routine>) {
        dir.mkdirs()
        file(botId).writeText(json.encodeToString(routines))
    }

    fun add(routine: Routine): Boolean {
        val cur = list(routine.botId)
        if (cur.size >= 50) return false
        if (cur.any { it.name.equals(routine.name, ignoreCase = true) }) return false
        save(routine.botId, cur + routine)
        return true
    }

    fun update(routine: Routine) {
        save(routine.botId, list(routine.botId).filter { it.id != routine.id } + routine)
    }

    fun remove(botId: String, id: String) {
        save(botId, list(botId).filter { it.id != id })
    }

    fun markRan(botId: String, id: String, at: Long) {
        val r = list(botId).firstOrNull { it.id == id } ?: return
        update(r.copy(lastRunAt = at))
    }

    /** Write the human-readable Markdown version alongside the JSON. */
    fun writeMarkdown(routine: Routine) {
        val md = File(File(context.filesDir, "bots/${routine.botId}/routines"), "${routine.id}.md")
        md.parentFile?.mkdirs()
        md.writeText(
            "---\nid: ${routine.id}\nname: ${routine.name}\nschedule: \"${routine.schedule}\"\n" +
                "timezone: ${routine.timezone}\nenabled: ${routine.enabled}\n---\n\n# ${routine.name}\n\n${routine.prompt}\n"
        )
    }
}
