package io.github.timilehin_dev.ogabot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.timilehin_dev.ogabot.core.Routine
import io.github.timilehin_dev.ogabot.core.RoutineStore
import java.util.concurrent.TimeUnit

/**
 * Phase 4 scheduler — fully in-app, no Termux.
 * Layer A: AlarmManager exact alarms re-armed on boot + after each fire (precision).
 * Layer B: WorkManager 15-min heartbeat as a doze-safe safety net that catches anything the
 *          alarm layer missed (catch-up-once semantics live in RoutineWorker).
 */
object RoutineScheduler {

    const val ACTION_FIRE = "io.github.timilehin_dev.ogabot.FIRE_ROUTINE"
    const val EXTRA_ROUTINE = "routine_id"
    const val EXTRA_BOT = "bot_id"

    fun rescheduleAll(context: Context) {
        val store = RoutineStore(context)
        val all = store.all().filter { it.enabled }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // cancel all our alarms first (simple approach: we track nothing; alarms are exact and re-set)
        all.forEach { scheduleExact(context, it) }
        // safety-net heartbeat
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "ogabot_heartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RoutineHeartbeatWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun scheduleExact(context: Context, routine: Routine) {
        if (!routine.enabled) return
        val next = routine.nextRunAfter(System.currentTimeMillis())
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePendingIntent(context, routine)
        // exact on API 30 (no permission needed below 31); inexact fallback elsewhere
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun cancel(context: Context, routine: Routine) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(context, routine))
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("routine_" + routine.id)
    }

    private fun firePendingIntent(context: Context, routine: Routine): PendingIntent {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ROUTINE, routine.id)
            putExtra(EXTRA_BOT, routine.botId)
        }
        return PendingIntent.getBroadcast(
            context,
            routine.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Queue the run as expedited one-time work (runs promptly, doze-tolerant, retryable). */
    fun enqueueRun(context: Context, botId: String, routineId: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "run_" + routineId + "_" + System.currentTimeMillis(),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RoutineRunWorker>()
                .setInputData(workDataOf(EXTRA_BOT to botId, EXTRA_ROUTINE to routineId))
                .build()
        )
    }
}

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineScheduler.ACTION_FIRE) return
        val routineId = intent.getStringExtra(RoutineScheduler.EXTRA_ROUTINE) ?: return
        val botId = intent.getStringExtra(RoutineScheduler.EXTRA_BOT) ?: return
        val store = RoutineStore(context)
        val routine = store.list(botId).firstOrNull { it.id == routineId } ?: return
        RoutineScheduler.enqueueRun(context, botId, routineId)
        // re-arm for the next occurrence
        RoutineScheduler.scheduleExact(context, routine)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            RoutineScheduler.rescheduleAll(context)
        }
    }
}

/** 15-min safety net: fire anything whose scheduled time passed while alarms missed it. */
class RoutineHeartbeatWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val store = RoutineStore(applicationContext)
        val now = System.currentTimeMillis()
        store.all().filter { it.enabled }.forEach { r ->
            val due = r.nextRunAfter(r.lastRunAt)
            if (due <= now) {
                RoutineScheduler.enqueueRun(applicationContext, r.botId, r.id)
                store.markRan(r.botId, r.id, now)
                RoutineScheduler.scheduleExact(applicationContext, r)
            }
        }
        return Result.success()
    }
}
