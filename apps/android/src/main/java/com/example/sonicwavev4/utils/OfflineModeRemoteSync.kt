package com.example.sonicwavev4.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.sonicwavev4.network.RetrofitClient

object OfflineModeRemoteSync {

    private const val TAG = "OfflineModeRemoteSync"
    private const val MIN_INTERVAL_MS = 10_000L
    @Volatile private var lastSyncAtMs: Long = 0L

    suspend fun sync(context: Context, force: Boolean = false) {
        DeviceIdentityProvider.initialize(context.applicationContext)
        val sessionManager = SessionManager(context.applicationContext)
        sync(sessionManager, force)
    }

    suspend fun sync(sessionManager: SessionManager, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSyncAtMs < MIN_INTERVAL_MS) {
            return
        }
        lastSyncAtMs = now
        val deviceId = runCatching { DeviceIdentityProvider.getDeviceId() }.getOrNull()
        try {
            val response = RetrofitClient.api.fetchFeatureFlags(deviceId)
            if (response.isSuccessful) {
                val body = response.body()
                val remoteAllowed = body?.offlineModeEnabled ?: false
                applyRemoteState(
                    sessionManager = sessionManager,
                    remoteAllowed = remoteAllowed,
                    deviceAllowed = body?.deviceOfflineAllowed
                )
            } else {
                Log.w(TAG, "fetchFeatureFlags failed: code=${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync offline feature flag", e)
        }
    }

    private fun applyRemoteState(sessionManager: SessionManager, remoteAllowed: Boolean, deviceAllowed: Boolean?) {
        val targetAllowed = deviceAllowed ?: remoteAllowed
        val localAllowed = sessionManager.isOfflineModeAllowed()
        if (localAllowed != targetAllowed) {
            sessionManager.setOfflineModeAllowed(targetAllowed)
        }
        OfflineCapabilityManager.setOfflineAllowed(sessionManager.isOfflineModeAllowed())
        if (!targetAllowed && sessionManager.isOfflineTestMode()) {
            sessionManager.initiateLogout(LogoutReason.HardLogout)
        }
    }
}
