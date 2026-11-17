package com.nutabe.chrona.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutabe.chrona.engine.AlarmRepository
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmListViewModel(private val repository: AlarmRepository) : ViewModel() {
    val state: StateFlow<AlarmListState> = combine(repository.data, ticker()) { data, now -> alarmListState(data, now) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            alarmListState(repository.data.value, Instant.now()),
        )

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }
}

/** Refreshes "rings in …" while the list is visible. */
private fun ticker(): Flow<Instant> = flow {
    while (true) {
        emit(Instant.now())
        delay(30_000)
    }
}
