package com.nutabe.chrona.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch

/** Reconciles after boot (including locked boot), time and time-zone changes, and app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("ChronaReconcile", "BootReceiver: ${intent.action}")
        val pending = goAsync()
        val appContext = context.applicationContext
        val clockChanged = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        if (clockChanged) java.util.TimeZone.setDefault(null)
        EngineScope.scope.launch {
            try {
                ReconcileExecutor.run(appContext, recomputeRepeating = clockChanged)
            } finally {
                pending.finish()
            }
        }
    }
}
