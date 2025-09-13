package com.example.sonicwavev4.network

import android.content.Context
import android.os.Build
import com.example.sonicwavev4.logging.DeviceInfo
import com.example.sonicwavev4.logging.LogEntry
import com.example.sonicwavev4.logging.LogRepository
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class NetworkLoggingInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        try {
            val response = chain.proceed(request)
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            val logEntry = LogEntry(
                log_level = if (response.isSuccessful) "INFO" else "ERROR",
                request_url = request.url.toString(),
                request_method = request.method,
                response_code = response.code,
                is_successful = response.isSuccessful,
                duration_ms = duration,
                error_message = if (!response.isSuccessful) response.message else null,
                device_info = getDeviceInfo()
            )
            LogRepository.writeLog(context, logEntry)

            return response
        } catch (e: IOException) {
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            val logEntry = LogEntry(
                log_level = "ERROR",
                request_url = request.url.toString(),
                request_method = request.method,
                response_code = null,
                is_successful = false,
                duration_ms = duration,
                error_message = e.message,
                device_info = getDeviceInfo()
            )
            LogRepository.writeLog(context, logEntry)

            throw e
        }
    }

    private fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            model = Build.MODEL,
            os_version = Build.VERSION.RELEASE
        )
    }
}
