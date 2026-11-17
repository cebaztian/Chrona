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
        EngineScope.scope.launch {
            try {
                ReconcileExecutor.run(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
