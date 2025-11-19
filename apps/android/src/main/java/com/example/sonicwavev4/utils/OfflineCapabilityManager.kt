package com.example.sonicwavev4.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds whether the backend currently allows the client to enter offline mode.
 * This value is derived from server-synced feature flags and is separate from
 * the actual offline runtime state managed by [OfflineTestModeManager].
 */
object OfflineCapabilityManager {

    private val _isOfflineAllowed = MutableStateFlow(false)
    val isOfflineAllowed: StateFlow<Boolean> = _isOfflineAllowed.asStateFlow()

    fun initialize(isAllowed: Boolean) {
        _isOfflineAllowed.value = isAllowed
    }

    fun setOfflineAllowed(allowed: Boolean) {
        _isOfflineAllowed.value = allowed
    }

    fun isOfflineAllowed(): Boolean = _isOfflineAllowed.value
}
