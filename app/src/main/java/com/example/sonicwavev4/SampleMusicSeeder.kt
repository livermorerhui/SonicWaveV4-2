package com.example.sonicwavev4

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object SampleMusicSeeder {
    private data class SampleAsset(
        val resId: Int,
        val title: String,
        val artist: String,
        val fileName: String
    )

    private val samples = listOf(
        SampleAsset(
            resId = R.raw.sample_brainwavr,
            title = "示例脑波",
            artist = "SonicWave",
            fileName = "sample_brainwavr.mp3"
        )
    )

    fun seedIfNeeded(context: Context, repository: DownloadedMusicRepository) {
        val samplesDir = File(context.filesDir, "sample_music").apply { mkdirs() }
        samples.forEach { asset ->
            val targetFile = File(samplesDir, asset.fileName)
            if (!targetFile.exists()) {
                runCatching {
                    context.resources.openRawResource(asset.resId).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }.onFailure { error ->
                    Log.e("SampleMusicSeeder", "Failed to copy sample ${asset.fileName}", error)
                }
            }
            if (targetFile.exists()) {
                repository.addDownloadedMusic(
                    DownloadedMusicItem(
                        fileName = asset.fileName,
                        title = asset.title,
                        artist = asset.artist,
                        internalPath = targetFile.absolutePath
                    )
                )
            }
        }
    }
}
