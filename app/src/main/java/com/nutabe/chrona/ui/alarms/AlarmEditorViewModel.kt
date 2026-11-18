package com.nutabe.chrona.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmOutcome
import com.nutabe.chrona.engine.AlarmProblem
import com.nutabe.chrona.engine.AlarmRepository
import com.nutabe.chrona.engine.AlarmValidation
import com.nutabe.chrona.engine.NextTrigger
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the editor screen shows. `ringsIn` previews the time to the next ring of the draft. */
data class AlarmEditorState(
    val draft: Alarm,
    val isNew: Boolean,
    val problems: Set<AlarmProblem> = emptySet(),
    val ringsIn: RingsIn? = null,
    val locked: Boolean = false,
) {
    val canSave: Boolean get() = problems.isEmpty() && !locked
}

fun editorState(draft: Alarm, isNew: Boolean, locked: Boolean, now: Instant, nextTrigger: NextTrigger): AlarmEditorState {
    val problems = AlarmValidation.validate(draft)
    val preview = if (problems.isEmpty() && draft.enabled) ringsIn(now, nextTrigger.next(draft, now)) else null
    return AlarmEditorState(draft, isNew, problems, preview, locked)
}

class AlarmEditorViewModel(
    private val repository: AlarmRepository,
    alarmId: Long,
    copyOf: Long = 0,
    private val nextTrigger: NextTrigger = NextTrigger(Clock.systemDefaultZone()),
) : ViewModel() {
    private val original: Alarm? = repository.data.value.alarms.firstOrNull { it.id == alarmId }
    private val isNew = original == null
    private val draft = MutableStateFlow(original ?: repository.data.value.alarms.firstOrNull { it.id == copyOf }?.let(::copyDraft) ?: Alarm())

    private val locked = original?.let {
        val data = repository.data.value
        data.isRingBusy(it.id) || it.snoozedUntil != null
    } ?: false

    val state: StateFlow<AlarmEditorState> = combine(draft, ticker()) { current, now ->
        editorState(current, isNew, locked, now, nextTrigger)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        editorState(draft.value, isNew, locked, Instant.now(), nextTrigger),
    )

    fun update(change: (Alarm) -> Alarm) {
        draft.update(change)
    }

    /** Saves the draft; returns the outcome, or null when the draft is invalid. */
    suspend fun save(): AlarmOutcome? =
        if (state.value.canSave) repository.save(draft.value.copy(enabled = true)) else null

    /** Deletes the alarm being edited; returns it so the caller can offer an undo. */
    suspend fun delete(): Alarm? {
        val id = original?.id ?: return null
        return (repository.delete(id) as? AlarmOutcome.Applied)?.alarm
    }
}

/** A new alarm with the settings of [source], without its schedule state. */
internal fun copyDraft(source: Alarm): Alarm =
    source.copy(id = 0, enabled = true, nextTriggerAt = null, snoozedUntil = null, snoozesUsed = 0)

private fun ticker(): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(30_000)
    }
}
