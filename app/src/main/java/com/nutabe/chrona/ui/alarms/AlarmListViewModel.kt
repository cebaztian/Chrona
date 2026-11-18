package com.nutabe.chrona.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmOutcome
import com.nutabe.chrona.engine.AlarmRepository
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
import kotlinx.coroutines.launch

class AlarmListViewModel(private val repository: AlarmRepository) : ViewModel() {
    private val selection = MutableStateFlow<Set<Long>?>(null)

    val state: StateFlow<AlarmListState> = combine(repository.data, ticker(), selection) { data, now, chosen ->
        alarmListState(data, now).withSelection(chosen)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            alarmListState(repository.data.value, Instant.now()),
        )

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun startSelecting(id: Long) {
        selection.value = setOf(id)
    }

    fun toggleSelected(id: Long) {
        selection.update { current ->
            val chosen = current.orEmpty()
            (if (id in chosen) chosen - id else chosen + id).takeIf { it.isNotEmpty() }
        }
    }

    fun cancelSelection() {
        selection.value = null
    }

    /** Deletes the selected alarms; returns those actually deleted, for the undo. */
    suspend fun deleteSelected(): List<Alarm> {
        val ids = state.value.selection.orEmpty()
        selection.value = null
        return ids.mapNotNull { delete(it) }
    }

    suspend fun delete(id: Long): Alarm? = (repository.delete(id) as? AlarmOutcome.Applied)?.alarm
}

/** Refreshes "rings in …" while the list is visible. */
private fun ticker(): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(30_000)
    }
}
