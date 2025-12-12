package com.example.sonicwavev4.repository

import android.content.Context
import com.example.sonicwavev4.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * LocalPlaylist represents a local-only playlist owned by the user.
 * Each playlist has:
 *  - id: stable UUID
 *  - name: user-visible name
 *  - trackUris: list of MusicItem.uri.toString() values
 */
data class LocalPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trackUris: List<String> = emptyList()
)

class LocalPlaylistRepository(
    private val context: Context
) {

    private val fileName = "playlists.json"

    private fun resolveFile(): File {
        return File(context.filesDir, fileName)
    }

    suspend fun loadPlaylists(): List<LocalPlaylist> = withContext(Dispatchers.IO) {
        val file = resolveFile()
        if (!file.exists()) return@withContext emptyList<LocalPlaylist>()
        val text = file.readText()
        if (text.isBlank()) return@withContext emptyList<LocalPlaylist>()

        try {
            val arr = JSONArray(text)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        LocalPlaylist(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            trackUris = obj.optJSONArray("trackUris")?.let { tracks ->
                                buildList {
                                    for (j in 0 until tracks.length()) {
                                        add(tracks.getString(j))
                                    }
                                }
                            } ?: emptyList()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun savePlaylists(playlists: List<LocalPlaylist>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            val trackArr = JSONArray()
            playlist.trackUris.forEach { trackArr.put(it) }
            obj.put("trackUris", trackArr)
            arr.put(obj)
        }
        resolveFile().writeText(arr.toString())
    }

    suspend fun createPlaylist(name: String): List<LocalPlaylist> {
        val current = loadPlaylists()
        val newPlaylist = LocalPlaylist(name = name.trim())
        val updated = current + newPlaylist
        savePlaylists(updated)
        return updated
    }

    suspend fun renamePlaylist(id: String, newName: String): List<LocalPlaylist> {
        val current = loadPlaylists()
        val updated = current.map {
            if (it.id == id) it.copy(name = newName.trim()) else it
        }
        savePlaylists(updated)
        return updated
    }

    suspend fun deletePlaylist(id: String): List<LocalPlaylist> {
        val current = loadPlaylists()
        val updated = current.filterNot { it.id == id }
        savePlaylists(updated)
        return updated
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: MusicItem): List<LocalPlaylist> {
        val current = loadPlaylists()
        val targetUri = track.uri.toString()
        val updated = current.map { playlist ->
            if (playlist.id == playlistId) {
                if (playlist.trackUris.contains(targetUri)) {
                    playlist
                } else {
                    playlist.copy(trackUris = playlist.trackUris + targetUri)
                }
            } else {
                playlist
            }
        }
        savePlaylists(updated)
        return updated
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, track: MusicItem): List<LocalPlaylist> {
        val current = loadPlaylists()
        val targetUri = track.uri.toString()
        val updated = current.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(trackUris = playlist.trackUris.filterNot { it == targetUri })
            } else {
                playlist
            }
        }
        savePlaylists(updated)
        return updated
    }
}
