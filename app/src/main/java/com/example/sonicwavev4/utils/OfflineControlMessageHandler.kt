package com.example.sonicwavev4.utils

import android.content.Context
import android.util.Log
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.OfflineCapabilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

object OfflineControlMessageHandler {

    private const val TAG = "OfflineControlHandler"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var sessionManager: SessionManager? = null

    fun initialize(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        sessionManager = SessionManager(applicationContext)
    }

    fun handleRawMessage(raw: String) {
        val context = appContext ?: return
        try {
            val json = JSONObject(raw)
            if (json.optString("type") != "offline_mode") {
                return
            }
            when (json.optString("action")) {
                "enable" -> handleEnable(true)
                "disable" -> handleEnable(false)
                "force_exit" -> {
                    val payload = json.optJSONObject("payload")
                    val countdown = payload?.optInt("countdownSec", 0) ?: 0
                    handleForceExit(countdown)
                }
                else -> Log.w(TAG, "Unknown offline control action: ${json.optString("action")}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse offline control message", e)
        }
    }

    private fun handleEnable(enabled: Boolean) {
        val manager = sessionManager ?: return
        manager.setOfflineModeAllowed(enabled)
        OfflineCapabilityManager.setOfflineAllowed(manager.isOfflineModeAllowed())
        if (enabled) {
            OfflineForceExitManager.cancelCountdown()
        } else {
            OfflineForceExitManager.cancelCountdown()
            // Normal disable no longer forces logout; device退出由用户手动触发
        }
    }

    private fun handleForceExit(countdownSec: Int) {
        val manager = sessionManager ?: return
        val logoutBlock: suspend () -> Unit = {
            manager.setOfflineModeAllowed(false)
            OfflineCapabilityManager.setOfflineAllowed(false)
            manager.initiateLogout(LogoutReason.HardLogout)
        }
        if (!OfflineTestModeManager.isOfflineMode()) {
            scope.launch { logoutBlock() }
            return
        }
        if (countdownSec <= 0) {
            scope.launch {
                logoutBlock()
            }
            return
        }
        OfflineForceExitManager.startCountdown(countdownSec) {
            logoutBlock()
        }
    }
}
