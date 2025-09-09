package com.example.sonicwavev4

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class MusicDownloader(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun downloadMusic(url: String, fileName: String): File? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.let { body ->
                    val musicFile = File(context.filesDir, fileName)
                    FileOutputStream(musicFile).use { outputStream ->
                        outputStream.write(body.bytes())
                    }
                    Log.d("MusicDownloader", "Music downloaded to: ${musicFile.absolutePath}")
                    return@withContext musicFile
                }
            } else {
                Log.e("MusicDownloader", "Download failed: ${response.code} ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("MusicDownloader", "Error downloading music: ${e.message}", e)
        }
        return@withContext null
    }
}