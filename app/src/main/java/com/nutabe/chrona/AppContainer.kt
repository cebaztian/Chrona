package com.nutabe.chrona

import android.content.Context
import com.nutabe.chrona.engine.Alarm
import com.nutabe.chrona.engine.AlarmRepository
import com.nutabe.chrona.engine.AlarmStore
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container (no DI framework). Nothing here touches storage until first use. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val repository: AlarmRepository by lazy { AlarmRepository(appContext, AlarmStore.get(appContext)) }

    /**
     * The alarms just deleted, waiting for the list to offer an undo. It is state, not an event, because
     * the list is not composed while the editor is on top and would miss the event.
     */
    val deletedAlarms = MutableStateFlow<List<Alarm>>(emptyList())
}
