package com.nutabe.chrona.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WakeLockHolder.acquire(context)
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        Log.i(RingingService.TAG, "AlarmReceiver fired for alarm $alarmId")
        context.startForegroundService(RingingService.startIntent(context, alarmId))
    }
}
