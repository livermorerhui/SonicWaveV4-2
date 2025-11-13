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
        val sessionManager = SessionManager(context.applicationContext)
        sync(sessionManager, force)
    }

    suspend fun sync(sessionManager: SessionManager, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSyncAtMs < MIN_INTERVAL_MS) {
            return
        }
        lastSyncAtMs = now
        try {
            val response = RetrofitClient.api.fetchFeatureFlags()
            if (response.isSuccessful) {
                val remoteAllowed = response.body()?.offlineModeEnabled ?: false
                applyRemoteState(sessionManager, remoteAllowed)
            } else {
                Log.w(TAG, "fetchFeatureFlags failed: code=${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync offline feature flag", e)
        }
    }

    private fun applyRemoteState(sessionManager: SessionManager, remoteAllowed: Boolean) {
        val localAllowed = sessionManager.isOfflineModeAllowed()
        if (localAllowed != remoteAllowed) {
            sessionManager.setOfflineModeAllowed(remoteAllowed)
        }
        OfflineCapabilityManager.setOfflineAllowed(sessionManager.isOfflineModeAllowed())
        if (!remoteAllowed && sessionManager.isOfflineTestMode()) {
            sessionManager.initiateLogout(LogoutReason.HardLogout)
        }
    }
}
