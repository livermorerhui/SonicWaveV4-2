package com.example.sonicwavev4.ui.music

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.DownloadedMusicItem
import com.example.sonicwavev4.DownloadedMusicRepository
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.domain.model.CloudMusicCategory
import com.example.sonicwavev4.repository.LocalPlaylist
import com.example.sonicwavev4.repository.LocalPlaylistRepository
import com.example.sonicwavev4.repository.MusicRepository
import com.example.sonicwavev4.repository.MusicRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CLOUD_CACHE_TTL_MS = 5 * 60 * 1000L

enum class MusicSection {
    CLOUD, LOCAL, MY_LIST
}

data class CloudTrackUi(
    val id: Long,
    val title: String,
    val artist: String,
    val categoryId: Long?,
    val fileUrl: String,
    val isDownloaded: Boolean,
    val downloadedLocalUriString: String?
)

data class SongRowUi(
    val key: String,
    val title: String,
    val artist: String,
    val playUriString: String,
    val isRemote: Boolean,
    val isDownloaded: Boolean,
    val downloadedLocalUriString: String?
)

data class MusicDialogUiState(
    val selectedSection: MusicSection = MusicSection.CLOUD,
    val cloudExpanded: Boolean = true,
    val myListExpanded: Boolean = true,
    val selectedCloudCategoryId: Long? = null,
    val selectedPlaylistId: String? = null,
    val cloudCategories: List<CloudMusicCategory> = emptyList(),
    val cloudLoading: Boolean = false,
    val cloudError: String? = null,
    val playlists: List<LocalPlaylist> = emptyList(),
    val songs: List<SongRowUi> = emptyList(),
    val libraryIndex: Map<String, MusicItem> = emptyMap(),
    val cachedCloudTracksByCategory: Map<Long?, List<CloudTrackUi>> = emptyMap(),
    val cachedAtMsByCategory: Map<Long?, Long> = emptyMap()
)

class MusicDialogViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val musicRepository: MusicRepository = MusicRepositoryImpl(appContext)
    private val downloadedRepository = DownloadedMusicRepository(appContext)
    private val playlistRepository = LocalPlaylistRepository(appContext)

    private val _uiState = MutableStateFlow(MusicDialogUiState())
    val uiState: StateFlow<MusicDialogUiState> = _uiState.asStateFlow()

    fun onDialogShown() {
        viewModelScope.launch {
            loadPlaylists()
        }
        viewModelScope.launch {
            loadLibraryIndex()
        }
        viewModelScope.launch {
            loadCloudCategoriesIfNeeded()
        }
    }

    fun onCloudHeaderClicked() {
        _uiState.update { state ->
            val newSelected = MusicSection.CLOUD
            val nextExpanded = if (state.selectedSection == MusicSection.CLOUD) {
                !state.cloudExpanded
            } else {
                state.cloudExpanded
            }
            val updated = state.copy(
                selectedSection = newSelected,
                cloudExpanded = nextExpanded,
                selectedCloudCategoryId = null,
                selectedPlaylistId = null
            )
            updated.copy(songs = computeSongs(updated))
        }
    }

    fun onLocalHeaderClicked() {
        _uiState.update { state ->
            val updated = state.copy(
                selectedSection = MusicSection.LOCAL,
                selectedPlaylistId = null
            )
            updated.copy(songs = computeSongs(updated))
        }
    }

    fun onMyListHeaderClicked() {
        _uiState.update { state ->
            val nextExpanded = if (state.selectedSection == MusicSection.MY_LIST) {
                !state.myListExpanded
            } else {
                state.myListExpanded
            }
            val updated = state.copy(
                selectedSection = MusicSection.MY_LIST,
                myListExpanded = nextExpanded,
                selectedPlaylistId = null
            )
            updated.copy(songs = computeSongs(updated))
        }
    }

    fun onCloudCategoryClicked(categoryId: Long?) {
        val now = System.currentTimeMillis()
        val current = _uiState.value
        val cachedAt = current.cachedAtMsByCategory[categoryId]
        val isStale = cachedAt == null || now - cachedAt > CLOUD_CACHE_TTL_MS

        _uiState.update { state ->
            val updated = state.copy(
                selectedSection = MusicSection.CLOUD,
                selectedCloudCategoryId = categoryId,
                selectedPlaylistId = null
            )
            updated.copy(songs = computeSongs(updated))
        }

        if (isStale) {
            refreshCloudTracks(categoryId)
        }
    }

    fun onPlaylistClicked(playlistId: String?) {
        _uiState.update { state ->
            val updated = state.copy(
                selectedSection = MusicSection.MY_LIST,
                selectedPlaylistId = playlistId,
                selectedCloudCategoryId = null
            )
            updated.copy(songs = computeSongs(updated))
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val updated = playlistRepository.createPlaylist(trimmed)
            _uiState.update { state ->
                val next = state.copy(
                    playlists = updated,
                    myListExpanded = true
                )
                next.copy(
                    songs = computeSongs(next),
                    selectedPlaylistId = next.selectedPlaylistId?.takeIf { id ->
                        updated.any { it.id == id }
                    }
                )
            }
        }
    }

    fun onDownloadCompleted(downloadUrl: String) {
        val currentState = _uiState.value
        val matching = currentState.cachedCloudTracksByCategory
            .mapValues { entry -> entry.value.filter { it.fileUrl == downloadUrl } }
            .filterValues { it.isNotEmpty() }

        if (matching.isEmpty()) return

        viewModelScope.launch {
            val updatedTracksByCategory = currentState.cachedCloudTracksByCategory.toMutableMap()
            val updatedCacheTimes = currentState.cachedAtMsByCategory.toMutableMap()
            val updatedLibrary = currentState.libraryIndex.toMutableMap()

            matching.forEach { (categoryId, tracks) ->
                val refreshedList = tracks.mapNotNull { track ->
                    val downloadedItem = downloadedRepository.findByCloudTrackId(track.id)
                    val musicItem = downloadedItem?.toMusicItem()
                    if (downloadedItem != null && musicItem != null) {
                        updatedLibrary[musicItem.uri.toString()] = musicItem
                        track.copy(
                            isDownloaded = true,
                            downloadedLocalUriString = localUriString(downloadedItem)
                        )
                    } else {
                        null
                    }
                }
                val originalList = updatedTracksByCategory[categoryId] ?: emptyList()
                val merged = originalList.map { original ->
                    refreshedList.firstOrNull { it.id == original.id } ?: original
                }
                updatedTracksByCategory[categoryId] = merged
                updatedCacheTimes[categoryId] = System.currentTimeMillis()
            }

            val nextState = _uiState.value.copy(
                cachedCloudTracksByCategory = updatedTracksByCategory,
                cachedAtMsByCategory = updatedCacheTimes,
                libraryIndex = updatedLibrary
            )
            _uiState.value = nextState.copy(songs = computeSongs(nextState))
        }
    }

    fun getCloudTrackIdByUrl(url: String): Long? {
        val cached = _uiState.value.cachedCloudTracksByCategory.values.flatten()
        return cached.firstOrNull { it.fileUrl == url }?.id
    }

    suspend fun addSongRowToPlaylist(playlistId: String, row: SongRowUi) {
        val targetUriString = when {
            row.isRemote && row.isDownloaded -> row.downloadedLocalUriString
            row.isRemote -> null
            else -> row.playUriString
        } ?: return

        val musicItem = MusicItem(
            title = row.title,
            artist = row.artist,
            uri = Uri.parse(targetUriString),
            isDownloaded = row.isDownloaded
        )

        val updatedPlaylists = withContext(Dispatchers.IO) {
            playlistRepository.addTrackToPlaylist(playlistId, musicItem)
        }

        _uiState.update { state ->
            val updatedLibrary = state.libraryIndex.toMutableMap()
            updatedLibrary[targetUriString] = musicItem
            val updatedState = state.copy(
                playlists = updatedPlaylists,
                selectedSection = MusicSection.MY_LIST,
                selectedPlaylistId = playlistId,
                myListExpanded = true,
                libraryIndex = updatedLibrary
            )
            updatedState.copy(songs = computeSongs(updatedState))
        }
    }

    suspend fun removeSongRowFromPlaylist(playlistId: String, row: SongRowUi) {
        val targetUriString = row.downloadedLocalUriString ?: row.playUriString
        val musicItem = MusicItem(
            title = row.title,
            artist = row.artist,
            uri = Uri.parse(targetUriString),
            isDownloaded = row.isDownloaded
        )

        val updatedPlaylists = withContext(Dispatchers.IO) {
            playlistRepository.removeTrackFromPlaylist(playlistId, musicItem)
        }

        _uiState.update { state ->
            val updatedState = state.copy(
                playlists = updatedPlaylists,
                selectedSection = MusicSection.MY_LIST,
                selectedPlaylistId = playlistId,
                myListExpanded = true
            )
            updatedState.copy(songs = computeSongs(updatedState))
        }
    }

    private fun refreshCloudTracks(categoryId: Long?) {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudLoading = true, cloudError = null) }
            try {
                val tracks = musicRepository.getCloudTracks(categoryId)
                val downloadedMap = downloadedRepository.findByCloudTrackIds(tracks.map { it.id })
                val cloudTracks = tracks.map { track ->
                    val downloaded = downloadedMap[track.id]
                    CloudTrackUi(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        categoryId = categoryId,
                        fileUrl = track.fileUrl,
                        isDownloaded = downloaded != null,
                        downloadedLocalUriString = downloaded?.let { localUriString(it) }
                    )
                }
                _uiState.update { state ->
                    val updatedCache = state.cachedCloudTracksByCategory.toMutableMap()
                    updatedCache[categoryId] = cloudTracks
                    val updatedTimes = state.cachedAtMsByCategory.toMutableMap()
                    updatedTimes[categoryId] = System.currentTimeMillis()
                    val updatedState = state.copy(
                        cachedCloudTracksByCategory = updatedCache,
                        cachedAtMsByCategory = updatedTimes,
                        cloudLoading = false,
                        cloudError = null
                    )
                    updatedState.copy(songs = computeSongs(updatedState))
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        cloudLoading = false,
                        cloudError = e.message ?: "加载云端音乐失败"
                    )
                }
            }
        }
    }

    private suspend fun loadPlaylists() {
        val playlists = withContext(Dispatchers.IO) {
            playlistRepository.loadPlaylists()
        }
        _uiState.update { state ->
            val selectedPlaylistId = state.selectedPlaylistId?.takeIf { id ->
                playlists.any { it.id == id }
            }
            val updated = state.copy(
                playlists = playlists,
                selectedPlaylistId = selectedPlaylistId
            )
            updated.copy(songs = computeSongs(updated))
        }
    }

    private suspend fun loadLibraryIndex() {
        val index = withContext(Dispatchers.IO) {
            buildLibraryIndex(appContext.contentResolver, downloadedRepository)
        }
        _uiState.update { state ->
            val updated = state.copy(libraryIndex = index)
            updated.copy(songs = computeSongs(updated))
        }
    }

    private suspend fun loadCloudCategoriesIfNeeded() {
        val current = _uiState.value
        if (current.cloudCategories.isNotEmpty()) return
        try {
            val categories = withContext(Dispatchers.IO) {
                musicRepository.getCloudCategories()
            }
            val defaultCategory = current.selectedCloudCategoryId ?: categories.firstOrNull()?.id
            _uiState.update { state ->
                state.copy(
                    cloudCategories = categories,
                    selectedCloudCategoryId = defaultCategory
                )
            }
            onCloudCategoryClicked(defaultCategory)
        } catch (e: Exception) {
            _uiState.update { state ->
                state.copy(cloudError = e.message ?: "加载云端音乐失败")
            }
        }
    }

    private fun computeSongs(state: MusicDialogUiState): List<SongRowUi> {
        return when (state.selectedSection) {
            MusicSection.CLOUD -> {
                val tracks = state.cachedCloudTracksByCategory[state.selectedCloudCategoryId].orEmpty()
                tracks.map { track ->
                    SongRowUi(
                        key = "cloud:${track.id}",
                        title = track.title,
                        artist = track.artist,
                        playUriString = track.fileUrl,
                        isRemote = true,
                        isDownloaded = track.isDownloaded,
                        downloadedLocalUriString = track.downloadedLocalUriString
                    )
                }
            }

            MusicSection.LOCAL -> {
                state.libraryIndex.values
                    .filter { item ->
                        val scheme = item.uri.scheme.orEmpty().lowercase()
                        val isRemote = scheme == "http" || scheme == "https"
                        !isRemote && !item.isDownloaded
                    }
                    .map { item ->
                        val uriString = item.uri.toString()
                        SongRowUi(
                            key = "local:$uriString",
                            title = item.title,
                            artist = item.artist,
                            playUriString = uriString,
                            isRemote = false,
                            isDownloaded = item.isDownloaded,
                            downloadedLocalUriString = null
                        )
                    }
            }

            MusicSection.MY_LIST -> {
                val playlistId = state.selectedPlaylistId ?: return emptyList()
                val playlist = state.playlists.firstOrNull { it.id == playlistId } ?: return emptyList()
                playlist.trackUris.mapNotNull { uriString ->
                    val item = state.libraryIndex[uriString] ?: return@mapNotNull null
                    val scheme = item.uri.scheme.orEmpty().lowercase()
                    val isRemote = scheme == "http" || scheme == "https"
                    SongRowUi(
                        key = "local:$uriString",
                        title = item.title,
                        artist = item.artist,
                        playUriString = item.uri.toString(),
                        isRemote = isRemote,
                        isDownloaded = item.isDownloaded,
                        downloadedLocalUriString = null
                    )
                }
            }
        }
    }
}

private suspend fun buildLibraryIndex(
    contentResolver: ContentResolver,
    downloadedRepository: DownloadedMusicRepository
): Map<String, MusicItem> = withContext(Dispatchers.IO) {
    val result = mutableMapOf<String, MusicItem>()

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DATA
    )

    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val title = cursor.getString(titleColumn)
            val artist = cursor.getString(artistColumn)
            val contentUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
            } else {
                cursor.getString(dataColumn).toUri()
            }

            if (title != null && artist != null && !title.startsWith(".")) {
                val item = MusicItem(title, artist, contentUri, isDownloaded = false)
                result[contentUri.toString()] = item
            }
        }
    }

    val downloadedMusic = downloadedRepository.loadDownloadedMusic()
    val validDownloads = mutableListOf<DownloadedMusicItem>()
    downloadedMusic.forEach { downloadedItem ->
        val localItem = downloadedItem.toMusicItem()
        if (localItem != null) {
            result[localItem.uri.toString()] = localItem
            validDownloads.add(downloadedItem)
        }
    }
    if (validDownloads.size != downloadedMusic.size) {
        downloadedRepository.saveDownloadedMusic(validDownloads)
    }

    result.toMap()
}

private fun localUriString(downloadedItem: DownloadedMusicItem): String? {
    val file = File(downloadedItem.internalPath)
    return if (file.exists()) Uri.fromFile(file).toString() else null
}
