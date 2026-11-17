package com.nutabe.chrona.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for engine work that must outlive the component that started it. */
object EngineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
