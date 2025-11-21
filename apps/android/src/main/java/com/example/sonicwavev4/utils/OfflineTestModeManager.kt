package com.example.sonicwavev4.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the in-memory representation of whether the app is running in offline
 * test mode. Persistence is handled by SessionManager – this class simply
 * mirrors that state for interested UI components.
 */
object OfflineTestModeManager {

    private val _isOfflineTestMode = MutableStateFlow(false)
    val isOfflineTestMode: StateFlow<Boolean> = _isOfflineTestMode.asStateFlow()

    fun initialize(isOffline: Boolean) {
        _isOfflineTestMode.value = isOffline
    }

    fun setOfflineTestMode(enabled: Boolean) {
        _isOfflineTestMode.value = enabled
    }

    fun isOfflineMode(): Boolean = _isOfflineTestMode.value
}
