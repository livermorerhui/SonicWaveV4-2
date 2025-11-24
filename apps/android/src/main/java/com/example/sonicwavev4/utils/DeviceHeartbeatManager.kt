package com.example.sonicwavev4.utils

import android.content.Context
import android.util.Log
import com.example.sonicwavev4.network.DeviceHeartbeatRequest
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object DeviceHeartbeatManager {

    private const val INTERVAL_MS = 15_000L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private lateinit var appContext: Context
    private lateinit var sessionManager: SessionManager

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        sessionManager = SessionManager(appContext)
        DeviceIdentityProvider.initialize(appContext)
    }

    fun start(context: Context) {
        initialize(context)
        if (OfflineTestModeManager.isOfflineMode()) {
            Log.d("DeviceHeartbeatManager", "Offline mode active, not starting device heartbeat.")
            stop()
            return
        }
        if (heartbeatJob?.isActive == true) {
            return
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    if (OfflineTestModeManager.isOfflineMode()) {
                        Log.d("DeviceHeartbeatManager", "Offline mode active, skipping device heartbeat.")
                        break
                    }
                    val hasUserSession = sessionManager.hasActiveSession() && sessionManager.fetchSessionId() != -1L
                    if (!hasUserSession && !OfflineTestModeManager.isOfflineMode()) {
                        sendHeartbeat()
                    }
                } catch (e: Exception) {
                    Log.w("DeviceHeartbeatManager", "Failed to send device heartbeat", e)
                }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun sendHeartbeat() {
        val profile = DeviceIdentityProvider.buildProfile()
        val request = DeviceHeartbeatRequest(
            deviceId = profile.deviceId,
            ipAddress = profile.localIpAddress,
            deviceModel = profile.deviceModel,
            osVersion = profile.osVersion,
            appVersion = profile.appVersion
        )
        RetrofitClient.api.sendDeviceHeartbeat(request)
    }
}
