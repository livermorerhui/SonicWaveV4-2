package com.example.sonicwavev4.utils

import android.content.Context
import android.util.Log
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.*

object HeartbeatManager {

    private const val HEARTBEAT_INTERVAL_MS = 5000L // 5秒
    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context) {
        if (heartbeatJob?.isActive == true) {
            Log.d("HeartbeatManager", "Heartbeat is already running.")
            return
        }

        Log.d("HeartbeatManager", "Starting heartbeat...")
        val appContext = context.applicationContext
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    val response = RetrofitClient.getInstance(appContext).instance.sendHeartbeat()
                    if (!response.isSuccessful) {
                        Log.w("HeartbeatManager", "Failed to send heartbeat: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("HeartbeatManager", "Error sending heartbeat", e)
                }
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
