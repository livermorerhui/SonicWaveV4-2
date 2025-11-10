package com.example.sonicwavev4.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TestToneSettings {
    private val _sineToneEnabled = MutableStateFlow(false)
    val sineToneEnabled: StateFlow<Boolean> = _sineToneEnabled.asStateFlow()

    fun setSineToneEnabled(enabled: Boolean) {
        _sineToneEnabled.value = enabled
    }
}
