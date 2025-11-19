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
    private const val MAX_TEXT_LENGTH = 2000
    private const val MAX_URL_LENGTH = 512
    private val gson = Gson()

    private fun String.truncate(max: Int): String {
        return if (length <= max) this else take(max)
    }

    private fun sanitize(log: LogEntry): LogEntry {
        return log.copy(
            log_level = log.log_level.truncate(32),
            request_url = log.request_url.truncate(MAX_URL_LENGTH),
            request_method = log.request_method.truncate(16),
            error_message = log.error_message?.truncate(MAX_TEXT_LENGTH),
            device_info = log.device_info.copy(
                model = log.device_info.model.truncate(128),
                os_version = log.device_info.os_version.truncate(64)
            )
        )
    }

    fun writeLog(context: Context, logEntry: LogEntry) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        val sanitized = sanitize(logEntry)
        val logLine = gson.toJson(sanitized) + "\n"
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

    fun removeLogs(context: Context, count: Int) {
        if (count <= 0) return
        val file = File(context.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return
        val remaining = file.readLines().drop(count)
        if (remaining.isEmpty()) {
            file.delete()
        } else {
            file.writeText(remaining.joinToString(separator = "\n", postfix = "\n"))
        }
    }
}
