package com.nutabe.chrona.engine

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface RingingState {
    data object Idle : RingingState
    data class Ringing(val alarmId: Long, val startedAt: Instant) : RingingState
    data class Snoozed(val alarmId: Long, val until: Instant) : RingingState
}

/**
 * Process-wide ring state. The service writes it; the activity observes it and sends user actions
 * back as intents, so the service stays the owner of the ring.
 */
object RingingController {
    private val mutableState = MutableStateFlow<RingingState>(RingingState.Idle)

    val state: StateFlow<RingingState> = mutableState.asStateFlow()

    fun onRinging(alarmId: Long, startedAt: Instant) {
        mutableState.value = RingingState.Ringing(alarmId, startedAt)
    }

    fun onSnoozed(alarmId: Long, until: Instant) {
        mutableState.value = RingingState.Snoozed(alarmId, until)
    }

    fun onIdle() {
        mutableState.value = RingingState.Idle
    }

    fun stop(context: Context) {
        context.startService(RingingService.stopIntent(context))
    }

    fun snooze(context: Context) {
        context.startService(RingingService.snoozeIntent(context))
    }
}
