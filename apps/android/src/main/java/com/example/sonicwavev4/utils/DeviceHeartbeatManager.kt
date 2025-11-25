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

/**
 * Device/App heartbeat only runs when there is no active user heartbeat. Once a
 * user session is online and user heartbeat is running, it replaces device
 * heartbeat. Device heartbeat can continue in offline mode to indicate the app
 * is alive even without a user session.
 */

    private const val INTERVAL_MS = 15_000L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        DeviceIdentityProvider.initialize(appContext)
    }

    fun start(context: Context) {
        initialize(context)
        if (heartbeatJob?.isActive == true) {
            return
        }
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    sendHeartbeat()
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
