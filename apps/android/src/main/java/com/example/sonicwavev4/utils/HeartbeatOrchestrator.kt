package com.example.sonicwavev4.utils

import android.content.Context

/**
 * Central coordinator ensuring that only one heartbeat stream is active at a time.
 * Device heartbeat runs when there is no active user heartbeat (e.g., logged out or
 * offline test mode). Once user heartbeat starts, it replaces device heartbeat until
 * the user signs out or the app leaves the foreground.
 */
object HeartbeatOrchestrator {

    private lateinit var appContext: Context
    private lateinit var sessionManager: SessionManager
    private var isAppInForeground: Boolean = false

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        sessionManager = SessionManager(appContext)
    }

    fun onAppForeground(context: Context) {
        initialize(context)
        isAppInForeground = true
        refreshHeartbeats()
    }

    fun onAppBackground() {
        if (!::appContext.isInitialized) return
        isAppInForeground = false
        stopAllHeartbeats()
    }

    fun onLogin(context: Context) {
        initialize(context)
        refreshHeartbeats()
    }

    fun onLogout(context: Context) {
        initialize(context)
        HeartbeatManager.stop()
        if (isAppInForeground) {
            DeviceHeartbeatManager.start(appContext)
        } else {
            DeviceHeartbeatManager.stop()
        }
    }

    fun refreshHeartbeats() {
        if (!::appContext.isInitialized) return
        if (!isAppInForeground) {
            stopAllHeartbeats()
            return
        }

        if (shouldSendUserHeartbeat()) {
            DeviceHeartbeatManager.stop()
            HeartbeatManager.start(appContext)
        } else {
            HeartbeatManager.stop()
            DeviceHeartbeatManager.start(appContext)
        }
    }

    private fun shouldSendUserHeartbeat(): Boolean {
        if (OfflineTestModeManager.isOfflineMode()) return false
        if (!sessionManager.hasActiveSession()) return false
        return sessionManager.fetchSessionId() != -1L
    }

    private fun stopAllHeartbeats() {
        HeartbeatManager.stop()
        DeviceHeartbeatManager.stop()
    }
}
