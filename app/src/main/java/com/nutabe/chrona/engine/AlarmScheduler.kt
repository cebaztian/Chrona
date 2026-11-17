package com.nutabe.chrona.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nutabe.chrona.MainActivity

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarmId"

    private const val KIND_NEXT = 1

    fun scheduleAt(context: Context, triggerAtMillis: Long, alarmId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val fire = PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, KIND_NEXT),
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, show), fire)
    }

    private fun requestCode(alarmId: Long, kind: Int): Int = (alarmId * 10 + kind).toInt()
}
