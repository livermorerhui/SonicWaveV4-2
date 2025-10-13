package com.example.sonicwavev4

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cn.wch.ch341lib.CH341Manager
import com.example.sonicwavev4.logging.LogUploadWorker
import com.example.sonicwavev4.network.RetrofitClient
import java.util.concurrent.TimeUnit

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CH341Manager.getInstance().init(this)
        RetrofitClient.initialize(this)
        scheduleLogUpload()
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
}
