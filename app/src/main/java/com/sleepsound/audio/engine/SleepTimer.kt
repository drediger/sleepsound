package com.sleepsound.audio.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coroutine-backed sleep timer. [onExpire] runs on the scope's dispatcher.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val onExpire: () -> Unit,
) {
    private var job: Job? = null

    fun set(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        val durationMs = minutes * 60_000L
        job = scope.launch {
            delay(durationMs)
            onExpire()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
