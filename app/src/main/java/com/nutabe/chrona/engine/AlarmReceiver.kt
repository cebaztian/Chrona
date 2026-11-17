package com.nutabe.chrona.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Instant

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(Scheduler.EXTRA_ALARM_ID, -1L)
        val kind = runCatching { TriggerKind.valueOf(intent.getStringExtra(Scheduler.EXTRA_KIND).orEmpty()) }
            .getOrDefault(TriggerKind.NEXT)
        val data = AlarmStore.get(context).state.value
        val valid = FireGuard.isValid(kind, alarmId, data.alarms.firstOrNull { it.id == alarmId }, data.activeRing, Instant.now())
        Log.i(RingingService.TAG, "AlarmReceiver fired for alarm $alarmId kind=$kind valid=$valid")
        if (!valid) return
        WakeLockHolder.acquire(context)
        context.startForegroundService(RingingService.startIntent(context, alarmId))
    }
}
