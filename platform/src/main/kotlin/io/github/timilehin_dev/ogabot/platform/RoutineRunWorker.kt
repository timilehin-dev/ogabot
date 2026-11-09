package io.github.timilehin_dev.ogabot.platform

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.timilehin_dev.ogabot.data.RoutineFileStore
import io.github.timilehin_dev.ogabot.domain.entity.Message
import io.github.timilehin_dev.ogabot.domain.entity.TurnEvent
import io.github.timilehin_dev.ogabot.domain.port.TurnRequest
import kotlinx.coroutines.runBlocking

/**
 * Runs one routine: a single turn with the routine's prompt, then commits the
 * result into the bot's thread and posts a notification.
 *
 * Phase 2 (the wake turn, where the bot decides what to do with the result)
 * lands in a later sprint; for now the result is committed and surfaced.
 */
class RoutineRunWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val botId = inputData.getString(RoutineAlarmReceiver.EXTRA_BOT) ?: return Result.failure()
        val routineId = inputData.getString(RoutineAlarmReceiver.EXTRA_ROUTINE) ?: return Result.failure()

        val engine = ServiceLocator.turnEngine ?: return Result.failure()
        val bots = ServiceLocator.bots ?: return Result.failure()
        val tools = ServiceLocator.tools ?: emptyList()
        val bot = bots.get(botId) ?: return Result.failure()

        val store = RoutineFileStore(applicationContext)
        val routine = store.list(botId).firstOrNull { it.id == routineId } ?: return Result.failure()

        val result = runBlocking {
            val sb = StringBuilder()
            engine.runTurn(
                TurnRequest(
                    bot = bot,
                    history = listOf(
                        Message(
                            role = "user",
                            content = "Routine '${routine.name}' fired.\n\n${routine.prompt}"
                        )
                    ),
                    tools = tools.flatMap { it.descriptors() },
                    background = true
                )
            ).collect { ev -> if (ev is TurnEvent.Delta) sb.append(ev.text) }
            sb.toString().ifBlank { "(no output produced)" }
        }

        store.markRan(botId, routineId, System.currentTimeMillis())
        store.writeMarkdown(routine)

        BotNotifier.notifyRoutine(applicationContext, bot.name, routine.name, result)
        return Result.success()
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
                    .apply { description = "Routines and updates from $botName" }
            )
        }
        val b = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$botName \u00b7 $routineName")
            .setContentText(summary.take(280))
            .setStyle(android.app.Notification.BigTextStyle().bigText(summary))
        nm.notify(routineName.hashCode(), b.build())
    }
}
