package com.example.sonicwavev4

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class MusicDownloadEvent {
    data class Success(val downloadUrl: String) : MusicDownloadEvent()
}

object MusicDownloadEventBus {
    // Buffer so tryEmit works off main without suspension
    private val _events = MutableSharedFlow<MusicDownloadEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: MusicDownloadEvent) {
        _events.tryEmit(event)
    }
}
