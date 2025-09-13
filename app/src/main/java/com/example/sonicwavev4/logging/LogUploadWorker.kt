package com.example.sonicwavev4.logging

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sonicwavev4.network.RetrofitClient
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class LogUploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val logs = LogRepository.readLogs(applicationContext)

        if (logs.isEmpty()) {
            return Result.success()
        }

        return try {
            val gson = Gson()
            val jsonLogs = gson.toJson(logs)
            val requestBody = jsonLogs.toRequestBody("application/json".toMediaType())

            val response = RetrofitClient.api.createClientLogs(requestBody)

            if (response.isSuccessful) {
                LogRepository.clearLogs(applicationContext)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
