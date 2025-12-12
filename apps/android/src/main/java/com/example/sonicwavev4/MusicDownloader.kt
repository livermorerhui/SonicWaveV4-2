package com.example.sonicwavev4

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed class MusicDownloadError {
    data class Network(val message: String) : MusicDownloadError()
    data class Http(val code: Int, val message: String) : MusicDownloadError()
    data class Io(val message: String) : MusicDownloadError()
}

class MusicDownloader(private val context: Context) {

    private val client = OkHttpClient()

    @Volatile
    var lastError: MusicDownloadError? = null
        private set

    suspend fun downloadMusic(
        url: String,
        fileName: String,
        accessToken: String? = null
    ): File? = withContext(Dispatchers.IO) {
        lastError = null
        val requestBuilder = Request.Builder().url(url)
        if (!accessToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body == null) {
                        lastError = MusicDownloadError.Io("Empty response body")
                        Log.e("MusicDownloader", "Download failed: empty body for $url")
                        return@withContext null
                    }
                    val musicFile = File(context.filesDir, fileName)
                    FileOutputStream(musicFile).use { outputStream ->
                        body.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d("MusicDownloader", "Music downloaded to: ${musicFile.absolutePath}")
                    return@withContext musicFile
                } else {
                    lastError = MusicDownloadError.Http(response.code, response.message)
                    Log.e(
                        "MusicDownloader",
                        "Download failed: ${response.code} ${response.message}"
                    )
                }
            }
        } catch (e: UnknownHostException) {
            lastError = MusicDownloadError.Network(e.message ?: "Network error")
            Log.e("MusicDownloader", "Network error while downloading $url", e)
        } catch (e: SocketTimeoutException) {
            lastError = MusicDownloadError.Network(e.message ?: "Timeout")
            Log.e("MusicDownloader", "Timeout while downloading $url", e)
        } catch (e: IOException) {
            lastError = MusicDownloadError.Io(e.message ?: "IO error")
            Log.e("MusicDownloader", "IO error while downloading $url", e)
        } catch (e: Exception) {
            lastError = MusicDownloadError.Io(e.message ?: "Unexpected download error")
            Log.e("MusicDownloader", "Unexpected error while downloading $url", e)
        }
        return@withContext null
    }
}
