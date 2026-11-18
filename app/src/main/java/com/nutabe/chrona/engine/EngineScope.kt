package com.nutabe.chrona.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob

/** Process-wide scope for engine work that must outlive the component that started it. */
object EngineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Runs one block at a time in submission order, for store changes that must not be reordered. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val serial = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
}
