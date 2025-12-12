package com.example.sonicwavev4.ui.music

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.domain.model.CloudMusicCategory
import com.example.sonicwavev4.domain.model.CloudMusicTrack
import com.example.sonicwavev4.musiclibrary.LocalPlaylist as LegacyLocalPlaylist
import com.example.sonicwavev4.repository.LocalPlaylist
import com.example.sonicwavev4.repository.LocalPlaylistRepository
import com.example.sonicwavev4.repository.MusicCategory
import com.example.sonicwavev4.repository.MusicCategoryRepository
import com.example.sonicwavev4.repository.MusicCategoryResult
import com.example.sonicwavev4.repository.MusicRepository
import com.example.sonicwavev4.repository.MusicRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicLibraryUiState(
    val allSongs: List<MusicItem> = emptyList(),
    val visibleSongs: List<MusicItem> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val selectedPlaylistId: String? = null,
    val showingLocalOnly: Boolean = false,
    val isLoadingPlaylists: Boolean = false,
    val playlistErrorMessage: String? = null,
    val isLoadingCategories: Boolean = false,
    val cloudCategories: List<MusicCategory> = emptyList(),
    val categoryErrorMessage: String? = null
)

class MusicLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = LocalPlaylistRepository(application.applicationContext)
    private val categoryRepository = MusicCategoryRepository()
    private val musicRepository: MusicRepository = MusicRepositoryImpl(application.applicationContext)

    private val _playlists = MutableStateFlow<List<LegacyLocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LegacyLocalPlaylist>> = _playlists.asStateFlow()

    private val _uiState = MutableStateFlow(
        MusicLibraryUiState(
            cloudCategories = defaultCategories()
        )
    )
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()

    private val _cloudCategories = MutableStateFlow<List<CloudMusicCategory>>(emptyList())
    val cloudCategories: StateFlow<List<CloudMusicCategory>> = _cloudCategories.asStateFlow()

    private val _selectedCloudCategoryId = MutableStateFlow<Long?>(null)
    val selectedCloudCategoryId: StateFlow<Long?> = _selectedCloudCategoryId.asStateFlow()

    private val _cloudTracks = MutableStateFlow<List<CloudMusicTrack>>(emptyList())
    val cloudTracks: StateFlow<List<CloudMusicTrack>> = _cloudTracks.asStateFlow()

    private val _isCloudLoading = MutableStateFlow(false)
    val isCloudLoading: StateFlow<Boolean> = _isCloudLoading.asStateFlow()

    private val _cloudError = MutableStateFlow<String?>(null)
    val cloudError: StateFlow<String?> = _cloudError.asStateFlow()

    init {
        loadPlaylists()
    }

    fun setAllSongs(songs: List<MusicItem>) {
        _uiState.update { it.copy(allSongs = sortSongs(songs)) }
        refreshVisibleSongs()
    }

    fun setShowingLocalOnly(enabled: Boolean) {
        _uiState.update { it.copy(showingLocalOnly = enabled) }
        refreshVisibleSongs()
    }

    fun selectPlaylist(playlistId: String?) {
        _uiState.update { it.copy(selectedPlaylistId = playlistId) }
        refreshVisibleSongs()
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            try {
                val updated = playlistRepository.createPlaylist(trimmed)
                updatePlaylistsState(updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(playlistErrorMessage = e.message) }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: MusicItem) {
        if (track.isDownloaded) return
        viewModelScope.launch {
            try {
                val updated = playlistRepository.addTrackToPlaylist(playlistId, track)
                updatePlaylistsState(updated)
                if (_uiState.value.selectedPlaylistId == playlistId) {
                    refreshVisibleSongs()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(playlistErrorMessage = e.message) }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, track: MusicItem) {
        viewModelScope.launch {
            try {
                val updated = playlistRepository.removeTrackFromPlaylist(playlistId, track)
                updatePlaylistsState(updated)
                if (_uiState.value.selectedPlaylistId == playlistId) {
                    refreshVisibleSongs()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(playlistErrorMessage = e.message) }
            }
        }
    }

    fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPlaylists = true, playlistErrorMessage = null) }
            try {
                val loaded = playlistRepository.loadPlaylists()
                updatePlaylistsState(loaded)
                _uiState.update { it.copy(isLoadingPlaylists = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingPlaylists = false, playlistErrorMessage = e.message)
                }
            }
        }
    }

    fun loadCloudMusicInitial() {
        viewModelScope.launch {
            if (_cloudCategories.value.isNotEmpty()) {
                return@launch
            }
            _isCloudLoading.value = true
            _cloudError.value = null
            try {
                val categories = musicRepository.getCloudCategories()
                _cloudCategories.value = categories
                val defaultCategoryId = categories.firstOrNull()?.id
                _selectedCloudCategoryId.value = defaultCategoryId
                val tracks = musicRepository.getCloudTracks(defaultCategoryId)
                _cloudTracks.value = tracks
            } catch (e: Exception) {
                _cloudError.value = e.message ?: "加载云端音乐失败"
            } finally {
                _isCloudLoading.value = false
            }
        }
    }

    fun onCloudCategorySelected(categoryId: Long?) {
        viewModelScope.launch {
            _selectedCloudCategoryId.value = categoryId
            _isCloudLoading.value = true
            _cloudError.value = null
            try {
                val tracks = musicRepository.getCloudTracks(categoryId)
                _cloudTracks.value = tracks
            } catch (e: Exception) {
                _cloudError.value = e.message ?: "加载云端音乐失败"
            } finally {
                _isCloudLoading.value = false
            }
        }
    }

    fun loadCloudCategories() {
        _uiState.update {
            it.copy(isLoadingCategories = true, categoryErrorMessage = null)
        }
        viewModelScope.launch {
            when (val result = categoryRepository.fetchCategories()) {
                is MusicCategoryResult.Success -> {
                    val list = result.categories.takeIf { it.isNotEmpty() } ?: defaultCategories()
                    _uiState.update {
                        it.copy(
                            isLoadingCategories = false,
                            cloudCategories = list,
                            categoryErrorMessage = null
                        )
                    }
                }

                is MusicCategoryResult.BusinessError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCategories = false,
                            cloudCategories = defaultCategories(),
                            categoryErrorMessage = result.message
                        )
                    }
                }

                is MusicCategoryResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoadingCategories = false,
                            cloudCategories = defaultCategories(),
                            categoryErrorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun updatePlaylistsState(playlists: List<LocalPlaylist>) {
        _uiState.update { it.copy(playlists = playlists) }
        _playlists.value = playlists.map { playlist ->
            LegacyLocalPlaylist(id = playlist.id, name = playlist.name)
        }
        refreshVisibleSongs()
    }

    private fun refreshVisibleSongs() {
        val state = _uiState.value
        val playlistId = state.selectedPlaylistId
        val filtered = when {
            state.showingLocalOnly && playlistId == null -> {
                state.allSongs.filter { !it.isDownloaded }
            }

            state.showingLocalOnly && playlistId != null -> {
                val playlist = state.playlists.firstOrNull { it.id == playlistId }
                if (playlist != null) {
                    val allowedUris = playlist.trackUris.toSet()
                    state.allSongs.filter { !it.isDownloaded && it.uri.toString() in allowedUris }
                } else {
                    emptyList()
                }
            }

            else -> {
                state.allSongs.filter { it.isDownloaded }
            }
        }

        _uiState.update { it.copy(visibleSongs = filtered) }
    }

    private fun sortSongs(songs: List<MusicItem>): List<MusicItem> {
        return songs.sortedByDescending { it.isDownloaded }
    }

    private fun defaultCategories(): List<MusicCategory> = listOf(
        MusicCategory(id = -1, code = "relax", name = "放松"),
        MusicCategory(id = -2, code = "focus", name = "专注"),
        MusicCategory(id = -3, code = "sleep", name = "睡眠"),
    )
}
