package com.example.sonicwavev4.utils

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class HeartbeatLifecycleObserver(
    private val appContext: Context,
    private val sessionManager: SessionManager
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        DeviceHeartbeatManager.start(appContext)
        if (!OfflineTestModeManager.isOfflineMode() &&
            sessionManager.hasActiveSession() &&
            sessionManager.fetchSessionId() != -1L
        ) {
            HeartbeatManager.start(appContext)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        HeartbeatManager.stop()
        DeviceHeartbeatManager.stop()
    }
}
