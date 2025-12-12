package com.example.sonicwavev4.musiclibrary

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class LocalPlaylistRepository(private val context: Context) {

    private val gson = Gson()
    private val playlistsFile = File(context.filesDir, "local_playlists.json")
    private val type = object : TypeToken<MutableList<LocalPlaylist>>() {}.type

    fun loadPlaylists(): MutableList<LocalPlaylist> {
        if (!playlistsFile.exists()) {
            return mutableListOf()
        }
        return try {
            FileReader(playlistsFile).use { reader ->
                gson.fromJson<MutableList<LocalPlaylist>>(reader, type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun savePlaylists(playlists: List<LocalPlaylist>) {
        try {
            FileWriter(playlistsFile).use { writer ->
                gson.toJson(playlists, writer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addPlaylist(name: String): LocalPlaylist {
        val current = loadPlaylists()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return LocalPlaylist(id = "", name = "")
        }
        val newPlaylist = LocalPlaylist(
            id = generatePlaylistId(current),
            name = trimmed
        )
        current.add(newPlaylist)
        savePlaylists(current)
        return newPlaylist
    }

    private fun generatePlaylistId(existing: List<LocalPlaylist>): String {
        val base = System.currentTimeMillis().toString()
        var candidate = base
        var suffix = 0
        while (existing.any { it.id == candidate }) {
            suffix += 1
            candidate = "${base}_$suffix"
        }
        return candidate
    }
}
