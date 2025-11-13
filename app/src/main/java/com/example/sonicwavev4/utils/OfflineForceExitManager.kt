package com.example.sonicwavev4.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Emits a global countdown when the backend triggers a forced offline exit.
 * UI layers (e.g., MainActivity) can observe [countdownSeconds] to show dialogs.
 */
object OfflineForceExitManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _countdownSeconds = MutableStateFlow<Int?>(null)
    val countdownSeconds: StateFlow<Int?> = _countdownSeconds.asStateFlow()

    fun startCountdown(seconds: Int, onTimeout: suspend () -> Unit) {
        scope.coroutineContext.cancelChildren()
        if (seconds <= 0) {
            scope.launch { onTimeout() }
            return
        }
        scope.launch {
            var remaining = seconds
            _countdownSeconds.value = remaining
            while (remaining > 0) {
                delay(1000)
                remaining -= 1
                if (remaining > 0) {
                    _countdownSeconds.value = remaining
                } else {
                    _countdownSeconds.value = null
                }
            }
            onTimeout()
        }
    }

    fun cancelCountdown() {
        scope.coroutineContext.cancelChildren()
        _countdownSeconds.value = null
    }
}
