package com.nutabe.chrona.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nutabe.chrona.MainActivity
import java.time.Instant

/** Wraps `AlarmManager.setAlarmClock`. Each (alarm id, kind) pair owns its own `PendingIntent`. */
object Scheduler {
    const val EXTRA_ALARM_ID = "alarmId"
    const val EXTRA_KIND = "kind"

    fun schedule(context: Context, alarmId: Long, kind: TriggerKind, at: Instant) {
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), show), fire(context, alarmId, kind))
    }

    fun cancel(context: Context, alarmId: Long, kind: TriggerKind) {
        context.getSystemService(AlarmManager::class.java).cancel(fire(context, alarmId, kind))
    }

    /** Distinct for every (alarm id, kind), so a snooze never replaces the next occurrence. */
    fun requestCode(alarmId: Long, kind: TriggerKind): Int = (alarmId * TriggerKind.entries.size + kind.ordinal).toInt()

    private fun fire(context: Context, alarmId: Long, kind: TriggerKind): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, kind),
            Intent(context, AlarmReceiver::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .putExtra(EXTRA_KIND, kind.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
