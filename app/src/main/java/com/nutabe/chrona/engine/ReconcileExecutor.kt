package com.nutabe.chrona.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.launch

/** Applies the [Reconciler]'s actions to `AlarmManager`, the store and the notification shade. */
object ReconcileExecutor {
    private const val TAG = "ChronaReconcile"
    private const val MISSED_CHANNEL_ID = "missed"
    private const val MISSED_NOTIFICATION_BASE = 100

    fun launch(context: Context) {
        val appContext = context.applicationContext
        EngineScope.scope.launch { run(appContext) }
    }

    suspend fun run(context: Context) {
        val store = AlarmStore.get(context)
        val live = (RingingController.state.value as? RingingState.Ringing)?.alarmId
        val actions = Reconciler(NextTrigger(Clock.systemDefaultZone()))
            .reconcile(store.state.value, Instant.now(), live)
        Log.i(TAG, "reconcile: ${actions.size} actions $actions")
        actions.forEach { apply(context, store, it) }
    }

    private suspend fun apply(context: Context, store: AlarmStore, action: ReconcileAction) {
        when (action) {
            is ReconcileAction.Schedule -> {
                Scheduler.schedule(context, action.alarmId, action.kind, action.at)
                if (action.kind == TriggerKind.NEXT) {
                    store.update { data ->
                        data.copy(alarms = data.alarms.map {
                            if (it.id == action.alarmId) it.copy(nextTriggerAt = action.at) else it
                        })
                    }
                }
            }
            is ReconcileAction.PostMissed -> postMissed(context, store.state.value.alarms.firstOrNull { it.id == action.alarmId })
            is ReconcileAction.Disable -> store.update { data ->
                data.copy(alarms = data.alarms.map {
                    if (it.id == action.alarmId) it.copy(enabled = false, nextTriggerAt = null) else it
                })
            }
        }
    }

    private fun postMissed(context: Context, alarm: Alarm?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(MISSED_CHANNEL_ID, "Missed alarms", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val time = alarm?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty()
        manager.notify(
            MISSED_NOTIFICATION_BASE + (alarm?.id ?: 0L).toInt(),
            Notification.Builder(context, MISSED_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Missed alarm")
                .setContentText(listOfNotNull(time.ifBlank { null }, alarm?.label?.ifBlank { null }).joinToString(" · "))
                .build(),
        )
    }
}
