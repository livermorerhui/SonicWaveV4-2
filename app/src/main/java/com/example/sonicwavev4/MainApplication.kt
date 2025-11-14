package com.example.sonicwavev4

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.wch.ch341lib.CH341Manager
import com.example.sonicwavev4.logging.LogUploadWorker
import com.example.sonicwavev4.network.OfflineControlWebSocket
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.utils.DeviceHeartbeatManager
import com.example.sonicwavev4.utils.DeviceIdentityProvider
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.OfflineCapabilityManager
import com.example.sonicwavev4.utils.OfflineModeRemoteSync
import com.example.sonicwavev4.utils.OfflineTestModeManager
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CH341Manager.getInstance().init(this)
        RetrofitClient.initialize(this)
        DeviceIdentityProvider.initialize(this)
        DeviceHeartbeatManager.start(this)
        val sessionManager = SessionManager(this)
        val isOffline = sessionManager.isOfflineTestMode()
        OfflineCapabilityManager.initialize(sessionManager.isOfflineModeAllowed())
        OfflineTestModeManager.initialize(isOffline)
        val hasSession = sessionManager.hasActiveSession() && sessionManager.fetchSessionId() != -1L
        if (!isOffline && hasSession) {
            HeartbeatManager.start(this)
        }
        if (isOffline) {
            WorkManager.getInstance(this).cancelUniqueWork("logUploadWork")
        } else {
            scheduleLogUpload()
        }
        OfflineControlWebSocket.initialize(this)
        applicationScope.launch {
            OfflineModeRemoteSync.sync(sessionManager, force = true)
        }
    }

    private fun scheduleLogUpload() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val logUploadWorkRequest = PeriodicWorkRequestBuilder<LogUploadWorker>(
            6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "logUploadWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            logUploadWorkRequest
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }
}
