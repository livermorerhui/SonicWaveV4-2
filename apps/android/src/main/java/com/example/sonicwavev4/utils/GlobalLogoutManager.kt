package com.example.sonicwavev4.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A singleton object to broadcast critical, app-wide events like a forced logout.
 */
object GlobalLogoutManager {
    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent = _logoutEvent.asSharedFlow()

    suspend fun logout() {
        _logoutEvent.emit(Unit)
    }
}
