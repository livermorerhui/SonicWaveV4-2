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

class MusicDownloader(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun downloadMusic(
        url: String,
        fileName: String,
        accessToken: String? = null
    ): File? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        if (!accessToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
        val request = requestBuilder.build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@withContext null
                    val musicFile = File(context.filesDir, fileName)
                    FileOutputStream(musicFile).use { outputStream ->
                        body.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Log.d("MusicDownloader", "Music downloaded to: ${musicFile.absolutePath}")
                    return@withContext musicFile
                } else {
                    Log.e(
                        "MusicDownloader",
                        "Download failed: ${response.code} ${response.message}"
                    )
                }
            }
        } catch (e: IOException) {
            Log.e("MusicDownloader", "Error downloading music: ${e.message}", e)
        }
        return@withContext null
    }
}
