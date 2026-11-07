package io.github.timilehin_dev.ogabot.core

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

@Serializable
data class Routine(
    val id: String,
    val botId: String,
    val name: String,
    /** Prompt executed on each run. */
    val task: String,
    /** Minutes-of-day based schedule: hour, minute, daysOfWeek (1=Mon..7=Sun; empty = every day). */
    val hour: Int,
    val minute: Int,
    val days: List<Int> = emptyList(),
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun scheduleLabel(): String {
        val hh = hour.toString().padStart(2, '0')
        val mm = minute.toString().padStart(2, '0')
        val time = "$hh:$mm"
        return when {
            days.isEmpty() -> "daily at $time"
            days.size == 7 -> "daily at $time"
            days.toList().sorted() == listOf(1, 2, 3, 4, 5) -> "weekdays at $time"
            days.toList().sorted() == listOf(6, 7) -> "weekends at $time"
            else -> {
                val names = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                days.sorted().mapNotNull { names[it] }.joinToString(",") + " at $time"
            }
        }
    }

    fun nextRunAfter(ts: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        // candidate: today at hour:minute
        fun candidate(dayOffset: Int): Long {
            val c = Calendar.getInstance().apply {
                timeInMillis = ts
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.timeInMillis
        }
        for (offset in 0..8) {
            val t = candidate(offset)
            if (t <= ts) continue
            if (days.isEmpty()) return t
            val dow = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)
            // Calendar: SUN=1..SAT=7 → convert to ISO (Mon=1..Sun=7)
            val iso = if (dow == Calendar.SUNDAY) 7 else dow - 1
            if (iso in days) return t
        }
        return ts + 7L * 24 * 3600 * 1000
    }
}

/** File-backed routine store: routines/<botId>.json (per-bot, like threads). */
class RoutineStore(context: Context) {
    private val dir = File(context.filesDir, "routines")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun list(botId: String): List<Routine> {
        val f = file(botId)
        if (!f.exists()) return emptyList()
        return try { json.decodeFromString<List<Routine>>(f.readText()) } catch (e: Exception) { emptyList() }
    }

    fun all(): List<Routine> {
        dir.mkdirs()
        return dir.listFiles()?.filter { it.extension == "json" }?.flatMap {
            try { json.decodeFromString<List<Routine>>(it.readText()) } catch (e: Exception) { emptyList() }
        } ?: emptyList()
    }

    fun save(botId: String, routines: List<Routine>) {
        dir.mkdirs()
        file(botId).writeText(json.encodeToString(routines))
    }

    fun add(routine: Routine): Boolean {
        val cur = list(routine.botId)
        if (cur.size >= 50) return false  // GrokBot cap
        if (cur.any { it.name.equals(routine.name, ignoreCase = true) }) return false
        save(routine.botId, cur + routine)
        return true
    }

    fun update(routine: Routine) {
        val cur = list(routine.botId).filter { it.id != routine.id }
        save(routine.botId, cur + routine)
    }

    fun remove(botId: String, id: String) {
        save(botId, list(botId).filter { it.id != id })
    }

    fun markRan(botId: String, id: String, at: Long) {
        val r = list(botId).firstOrNull { it.id == id } ?: return
        update(r.copy(lastRunAt = at))
    }

    private fun file(botId: String): File {
        dir.mkdirs()
        return File(dir, "$botId.json")
    }

    companion object {
        fun parseSchedule(text: String): Triple<Int, Int, List<Int>>? {
            // plain-language: "daily at 08:00", "weekdays at 8:30", "every day at 20:15", "mondays at 9:00"
            val t = text.lowercase()
            val timeRe = Regex("(\\d{1,2})[:.](\\d{2})")
            val m = timeRe.find(t) ?: return null
            val hour = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minute = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            val days = when {
                "weekday" in t || "workday" in t -> listOf(1, 2, 3, 4, 5)
                "weekend" in t -> listOf(6, 7)
                "daily" in t || "every day" in t || "every morning" in t || "every evening" in t -> emptyList()
                else -> {
                    val names = mapOf(
                        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
                        "friday" to 5, "saturday" to 6, "sunday" to 7
                    )
                    val found = names.filter { it.key in t }.values.toList()
                    if (found.isEmpty()) emptyList() else found
                }
            }
            return Triple(hour, minute, days)
        }
    }
}
