package com.example.sonicwavev4.utils

import android.content.Context
import android.util.Log
import com.example.sonicwavev4.network.HeartbeatRequest
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.*

/**
 * Handles user/session heartbeat. When this is running it replaces device heartbeat;
 * device heartbeats only run when no user heartbeat is active (e.g., logged out or
 * offline test mode). User heartbeat still follows the existing online + logged-in
 * requirements.
 */
object HeartbeatManager {

    private const val HEARTBEAT_INTERVAL_MS = 15000L // [修改] 15秒
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context) {
        if (OfflineTestModeManager.isOfflineMode()) {
            Log.d("DEBUG_FLOW", "HeartbeatManager: offline test mode active, skipping heartbeat start.")
            return
        }
        Log.d("DEBUG_FLOW", "HeartbeatManager: start() CALLED.")
        if (heartbeatJob?.isActive == true) {
            Log.d("DEBUG_FLOW", "HeartbeatManager: Heartbeat is already running. Exiting.")
            return
        }

        Log.d("DEBUG_FLOW", "HeartbeatManager: Starting new heartbeat job...")
        val appContext = context.applicationContext
        val sessionManager = SessionManager(appContext)
        val deviceId = runCatching { DeviceIdentityProvider.getDeviceId() }.getOrNull()

        heartbeatJob = scope.launch {
            Log.d("DEBUG_FLOW", "HeartbeatManager: Coroutine launched. Entering while(isActive) loop.")
            while (isActive) {
                Log.d("DEBUG_FLOW", "HeartbeatManager: Loop iteration started.")
                try {
                    Log.d("DEBUG_FLOW", "HeartbeatManager: Fetching sessionId...")
                    val sessionId = sessionManager.fetchSessionId()
                    Log.d("DEBUG_FLOW", "HeartbeatManager: Fetched sessionId: $sessionId")
                    if (sessionId != -1L) {
                        val request = HeartbeatRequest(sessionId, deviceId)
                        Log.d("DEBUG_FLOW", "HeartbeatManager: Preparing to send heartbeat for session $sessionId.")
                        val response = RetrofitClient.api.sendHeartbeat(request)
                        Log.d("DEBUG_FLOW", "HeartbeatManager: sendHeartbeat call finished.")
                        if (!response.isSuccessful) {
                            Log.w("DEBUG_FLOW", "HeartbeatManager: sendHeartbeat FAILED. Code: ${response.code()}")
                        } else {
                            Log.d("DEBUG_FLOW", "HeartbeatManager: sendHeartbeat SUCCESSFUL for session $sessionId.")
                        }
                    } else {
                        Log.w("DEBUG_FLOW", "HeartbeatManager: Session ID is -1L. Stopping job.")
                        stop()
                    }
                } catch (e: Exception) {
                    Log.e("DEBUG_FLOW", "HeartbeatManager: EXCEPTION in loop.", e)
                }
                Log.d("DEBUG_FLOW", "HeartbeatManager: Loop iteration finished. Delaying for $HEARTBEAT_INTERVAL_MS ms.")
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        Log.d("HeartbeatManager", "Stopping heartbeat...")
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
