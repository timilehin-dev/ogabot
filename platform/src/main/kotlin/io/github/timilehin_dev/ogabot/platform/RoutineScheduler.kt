package io.github.timilehin_dev.ogabot.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.timilehin_dev.ogabot.data.RoutineFileStore
import io.github.timilehin_dev.ogabot.domain.entity.Routine
import io.github.timilehin_dev.ogabot.domain.port.Scheduler
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Two-layer scheduler:
 *  A) AlarmManager exact alarms, re-armed on each fire and on boot (precision)
 *  B) WorkManager 15-minute heartbeat as a doze-safe safety net (catch-up-once)
 */
class RoutineScheduler(private val context: Context) : Scheduler {

    private val store = RoutineFileStore(context)

    override fun schedule(routine: Routine) {
        if (!routine.enabled) return
        val next = nextRunAfter(routine, System.currentTimeMillis()) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePendingIntent(routine)
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    override fun cancel(routine: Routine) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(routine))
        WorkManager.getInstance(context).cancelUniqueWork("routine_" + routine.id)
    }

    override fun rescheduleAll() {
        store.all().filter { it.enabled }.forEach { schedule(it) }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "ogabot_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RoutineHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun enqueueRun(botId: String, routineId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "run_" + routineId + "_" + System.currentTimeMillis(),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RoutineRunWorker>()
                .setInputData(workDataOf(RoutineAlarmReceiver.EXTRA_BOT to botId, RoutineAlarmReceiver.EXTRA_ROUTINE to routineId))
                .build()
        )
    }

    private fun firePendingIntent(routine: Routine): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            action = RoutineAlarmReceiver.ACTION_FIRE
            putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE, routine.id)
            putExtra(RoutineAlarmReceiver.EXTRA_BOT, routine.botId)
        }
        return PendingIntent.getBroadcast(
            context,
            routine.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** Next occurrence of a routine after [ts], honouring cron or plain-language schedules. */
        fun nextRunAfter(routine: Routine, ts: Long): Long? {
            val cal = Calendar.getInstance()
            for (offset in 0..8) {
                val c = Calendar.getInstance().apply {
                    timeInMillis = ts
                    add(Calendar.DAY_OF_YEAR, offset)
                }
                val parsed = ScheduleParser.parse(routine.schedule) ?: return null
                c.set(Calendar.HOUR_OF_DAY, parsed.hour)
                c.set(Calendar.MINUTE, parsed.minute)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                if (c.timeInMillis <= ts) continue
                if (parsed.days.isEmpty()) return c.timeInMillis
                val iso = ((c.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // SUN=1..SAT=7 -> Mon=1..Sun=7
                if (iso in parsed.days) return c.timeInMillis
            }
            return null
        }
    }
}

/** Parses cron expressions and plain-language schedules into (hour, minute, days). */
object ScheduleParser {
    data class Parsed(val hour: Int, val minute: Int, val days: List<Int>)

    private val dayNames = mapOf(
        "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
        "friday" to 5, "saturday" to 6, "sunday" to 7,
        "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7
    )

    fun parse(text: String): Parsed? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null

        // Cron: "0 8 * * 1-5"
        if (t.contains("*")) {
            val parts = t.split(Regex("\\s+"))
            if (parts.size >= 5) {
                val minute = parts[0].toIntOrNull() ?: return null
                val hour = parts[1].toIntOrNull() ?: return null
                val days = parseCronDays(parts[4])
                return Parsed(hour, minute, days)
            }
        }

        // Plain language: "every day at 08:00", "weekdays at 8:30", "mondays at 9:00"
        val timeRe = Regex("(\\d{1,2})[:.](\\d{2})")
        val m = timeRe.find(t) ?: return null
        val hour = m.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val minute = m.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        val days = when {
            "weekday" in t || "workday" in t -> listOf(1, 2, 3, 4, 5)
            "weekend" in t -> listOf(6, 7)
            "daily" in t || "every day" in t || "every morning" in t || "every evening" in t -> emptyList()
            else -> dayNames.filter { it.key in t }.values.toList()
        }
        return Parsed(hour, minute, days)
    }

    private fun parseCronDays(field: String): List<Int> {
        if (field == "*") return emptyList()
        return field.split(",").flatMap { part ->
            when {
                part.contains("-") -> {
                    val (a, b) = part.split("-")
                    val start = a.toIntOrNull() ?: 1
                    val end = b.toIntOrNull() ?: 7
                    (start..end).toList()
                }
                else -> listOfNotNull(part.toIntOrNull())
            }
        }.map { if (it == 0) 7 else it } // cron: 0=Sunday -> ISO 7
    }
}

class RoutineAlarmReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val routineId = intent.getStringExtra(EXTRA_ROUTINE) ?: return
        val botId = intent.getStringExtra(EXTRA_BOT) ?: return
        RoutineScheduler(context).enqueueRun(botId, routineId)
        RoutineFileStore(context).list(botId).firstOrNull { it.id == routineId }?.let {
            RoutineScheduler(context).schedule(it)
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.timilehin_dev.ogabot.FIRE_ROUTINE"
        const val EXTRA_ROUTINE = "routine_id"
        const val EXTRA_BOT = "bot_id"
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RoutineScheduler(context).rescheduleAll()
        }
    }
}

class RoutineHeartbeatWorker(context: Context, params: androidx.work.WorkerParameters) :
    androidx.work.Worker(context, params) {
    override fun doWork(): androidx.work.ListenableWorker.Result {
        val store = RoutineFileStore(applicationContext)
        val now = System.currentTimeMillis()
        store.all().filter { it.enabled }.forEach { r ->
            val due = RoutineScheduler.nextRunAfter(r, r.lastRunAt) ?: return@forEach
            if (due <= now) {
                RoutineScheduler(applicationContext).enqueueRun(r.botId, r.id)
                store.markRan(r.botId, r.id, now)
                RoutineScheduler(applicationContext).schedule(r)
            }
        }
        return androidx.work.ListenableWorker.Result.success()
    }
}
