package com.example.sonicwavev4.logging

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File

data class LogEntry(
    val log_level: String,
    val request_url: String,
    val request_method: String,
    val response_code: Int?,
    val is_successful: Boolean,
    val duration_ms: Long,
    val error_message: String?,
    val device_info: DeviceInfo
)

data class DeviceInfo(
    val model: String,
    val os_version: String
)

object LogRepository {

    private const val LOG_FILE_NAME = "app_logs.jsonl"
    private val gson = Gson()

    fun writeLog(context: Context, logEntry: LogEntry) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val logLine = gson.toJson(logEntry) + "\n"
        file.appendText(logLine)
    }

    fun readLogs(context: Context): List<LogEntry> {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) {
            return emptyList()
        }
        return file.readLines().mapNotNull {
            try {
                gson.fromJson(it, LogEntry::class.java)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

    fun clearLogs(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (file.exists()) {
            file.delete()
        }
    }
}
