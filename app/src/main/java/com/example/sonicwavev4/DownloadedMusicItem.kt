package com.example.sonicwavev4

import android.net.Uri
import com.example.sonicwavev4.MusicItem

data class DownloadedMusicItem(
    val fileName: String,
    val title: String,
    val artist: String,
    val internalPath: String // Absolute path to the file in internal storage
) {
    // Helper to convert to MusicItem for display
    fun toMusicItem(): MusicItem {
        return MusicItem(title, artist, Uri.parse(internalPath))
    }
}