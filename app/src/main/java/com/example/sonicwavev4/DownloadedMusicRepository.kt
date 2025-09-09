package com.example.sonicwavev4

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class DownloadedMusicRepository(private val context: Context) {

    private val gson = Gson()
    private val downloadedMusicFile = File(context.filesDir, "downloaded_music.json")
    private val type = object : TypeToken<MutableList<DownloadedMusicItem>>() {}.type

    fun loadDownloadedMusic(): MutableList<DownloadedMusicItem> {
        if (!downloadedMusicFile.exists()) {
            return mutableListOf()
        }
        return try {
            FileReader(downloadedMusicFile).use { reader ->
                gson.fromJson(reader, type)
            } ?: mutableListOf()
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf() // Return empty list on error
        }
    }

    fun saveDownloadedMusic(musicList: List<DownloadedMusicItem>) {
        try {
            FileWriter(downloadedMusicFile).use { writer ->
                gson.toJson(musicList, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addDownloadedMusic(item: DownloadedMusicItem) {
        val currentList = loadDownloadedMusic()
        // Check if item already exists to prevent duplicates
        if (currentList.none { it.internalPath == item.internalPath }) {
            currentList.add(item)
            saveDownloadedMusic(currentList)
        }
    }
}