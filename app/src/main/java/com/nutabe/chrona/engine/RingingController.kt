package com.nutabe.chrona.engine

import android.content.Context
import com.nutabe.chrona.mission.MissionView
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the ringing screen needs to know about the alarm that rings. [mission] is the one that will run. */
data class RingInfo(
    val alarmId: Long,
    val startedAt: Instant,
    val label: String,
    val mission: Mission,
    val fellBackToMath: Boolean,
    val canSnooze: Boolean,
    val snoozeMinutes: Int,
    val snoozesLeft: Int,
    val snoozeMax: Int,
)

/**
 * `Ringing` until the user starts the mission, then `MissionActive` (its view is `done` for a moment
 * before the service stops the ring), plus `Snoozed` between a snooze and the next ring.
 */
sealed interface RingingState {
    data object Idle : RingingState

    data class Ringing(val info: RingInfo) : RingingState

    data class MissionActive(val info: RingInfo, val view: MissionView) : RingingState

    data class Snoozed(val alarmId: Long, val until: Instant) : RingingState
}

val RingingState.liveAlarmId: Long?
    get() = when (this) {
        is RingingState.Ringing -> info.alarmId
        is RingingState.MissionActive -> info.alarmId
        else -> null
    }

/** The user's actions on a ring in progress; the service implements them and the activity calls them. */
interface RingActions {
    fun startMission()

    fun submitMath(answer: Int)

    fun touch()

    fun switchToMath()

    fun debugSteps(count: Int, intervalMs: Long)
}

/**
 * Process-wide ring state. The service writes it; the activity observes it and sends user actions
 * back, so the service stays the owner of the ring.
 */
object RingingController {
    private val mutableState = MutableStateFlow<RingingState>(RingingState.Idle)

    val state: StateFlow<RingingState> = mutableState.asStateFlow()

    @Volatile
    var actions: RingActions? = null

    fun publish(state: RingingState) {
        mutableState.value = state
    }

    fun onSnoozed(alarmId: Long, until: Instant) = publish(RingingState.Snoozed(alarmId, until))

    fun onIdle() = publish(RingingState.Idle)

    fun stop(context: Context) {
        context.startService(RingingService.stopIntent(context))
    }

    fun snooze(context: Context) {
        context.startService(RingingService.snoozeIntent(context))
    }
}
