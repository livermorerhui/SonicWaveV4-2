package com.example.sonicwavev4.logging

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.utils.SessionManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val MAX_BATCH_ENTRIES = 50
private const val MAX_BATCH_BYTES = 80 * 1024 // 80KB per payload

class LogUploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        if (sessionManager.isOfflineTestMode()) {
            return Result.success()
        }
        val allLogs = LogRepository.readLogs(applicationContext)
        if (allLogs.isEmpty()) {
            return Result.success()
        }

        val gson = Gson()
        var processedCount = 0

        try {
            while (processedCount < allLogs.size) {
                val batch = mutableListOf<LogEntry>()
                var approximateBytes = 2 // for the surrounding []
                var index = processedCount

                while (index < allLogs.size && batch.size < MAX_BATCH_ENTRIES) {
                    val entry = allLogs[index]
                    val entryJson = gson.toJson(entry)
                    val entrySize = entryJson.toByteArray(Charsets.UTF_8).size + 1
                    if (batch.isNotEmpty() && approximateBytes + entrySize > MAX_BATCH_BYTES) {
                        break
                    }
                    batch.add(entry)
                    approximateBytes += entrySize
                    index++
                }

                if (batch.isEmpty()) {
                    // Single entry too large, still attempt to upload once after truncation rules
                    batch.add(allLogs[processedCount])
                    index = processedCount + 1
                }

                val requestBody = gson
                    .toJson(batch)
                    .toRequestBody("application/json".toMediaType())

                val response = RetrofitClient.api.createClientLogs(requestBody)

                if (response.isSuccessful) {
                    LogRepository.removeLogs(applicationContext, batch.size)
                    processedCount += batch.size
                } else {
                    // stop and retry later without losing already uploaded batches
                    return Result.retry()
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
