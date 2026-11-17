package com.nutabe.chrona.engine

import android.content.Context
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** What the UI talks to: pure [AlarmOps] on the [AlarmStore], then the resulting `AlarmManager` effects. */
class AlarmRepository(
    private val context: Context,
    private val store: AlarmStore,
    private val ops: AlarmOps = AlarmOps(NextTrigger(Clock.systemDefaultZone())),
    private val now: () -> Instant = Instant::now,
) {
    val data: StateFlow<StoreData> = store.state

    suspend fun save(draft: Alarm): AlarmOutcome = apply { ops.save(it, draft, now()) }

    suspend fun setEnabled(id: Long, enabled: Boolean): AlarmOutcome = apply { ops.setEnabled(it, id, enabled, now()) }

    suspend fun delete(id: Long): AlarmOutcome = apply { ops.delete(it, id) }

    /** Undo of [delete]. */
    suspend fun restore(alarm: Alarm): AlarmOutcome = apply { ops.restore(it, alarm, now()) }

    suspend fun setOnboardingDone(done: Boolean) {
        store.update { it.copy(settings = it.settings.copy(onboardingDone = done)) }
    }

    private suspend fun apply(operation: (StoreData) -> AlarmOutcome): AlarmOutcome {
        var outcome: AlarmOutcome = AlarmOutcome.NotFound
        store.update { current ->
            outcome = operation(current)
            (outcome as? AlarmOutcome.Applied)?.data ?: current
        }
        (outcome as? AlarmOutcome.Applied)?.effects?.forEach(::execute)
        return outcome
    }

    private fun execute(effect: AlarmEffect) = when (effect) {
        is AlarmEffect.Schedule -> Scheduler.schedule(context, effect.alarmId, effect.kind, effect.at)
        is AlarmEffect.Cancel -> Scheduler.cancel(context, effect.alarmId, effect.kind)
    }
}
