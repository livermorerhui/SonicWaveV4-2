package com.example.sonicwavev4.ui.music

import android.app.Application
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.MusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MiniPlayerUiState(
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val hasPlaylist: Boolean
) {
    val remainingMs: Int get() = (durationMs - positionMs).coerceAtLeast(0)
}

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _playlist = MutableStateFlow<List<MusicItem>>(emptyList())
    val playlist: StateFlow<List<MusicItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicItem?>(null)
    val currentTrack: StateFlow<MusicItem?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    val miniPlayerUiState: StateFlow<MiniPlayerUiState>

    private val handler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return
            _position.value = player.currentPosition
            _duration.value = player.duration
            if (_isPlaying.value) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private var mediaPlayer: MediaPlayer? = null

    init {
        val initial = MiniPlayerUiState(
            title = "",
            isPlaying = false,
            positionMs = 0,
            durationMs = 0,
            hasPlaylist = false
        )

        miniPlayerUiState = combine(
            currentTrack,
            isPlaying,
            position,
            duration,
            playlist
        ) { track, playing, pos, dur, list ->
            val safeDur = dur.coerceAtLeast(0)
            val safePos = if (safeDur > 0) pos.coerceIn(0, safeDur) else 0
            MiniPlayerUiState(
                title = track?.title?.trim().orEmpty(),
                isPlaying = playing,
                positionMs = safePos,
                durationMs = safeDur,
                hasPlaylist = list.isNotEmpty()
            )
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), initial)
    }

    fun setPlaylist(list: List<MusicItem>) {
        _playlist.value = list
        if (_currentIndex.value !in list.indices) {
            _currentIndex.value = -1
            _currentTrack.value = null
        }
    }

    fun playAt(index: Int) {
        if (index !in _playlist.value.indices) return
        val target = _playlist.value[index]
        startPlayback(target, index)
    }

    fun playTrack(track: MusicItem) {
        val index = _playlist.value.indexOfFirst { it.uri == track.uri }
        if (index >= 0) {
            playAt(index)
        } else {
            val updated = _playlist.value + track
            _playlist.value = updated
            playAt(updated.lastIndex)
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        when {
            player?.isPlaying == true -> {
                player.pause()
                _isPlaying.value = false
                stopProgressUpdates()
            }
            player != null -> {
                player.start()
                _isPlaying.value = true
                startProgressUpdates()
            }
            _playlist.value.isNotEmpty() -> {
                val target = if (_currentIndex.value in _playlist.value.indices) {
                    _currentIndex.value
                } else {
                    0
                }
                playAt(target)
            }
        }
    }

    fun playNext() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val next = if (_currentIndex.value == -1) 0 else (_currentIndex.value + 1) % list.size
        playAt(next)
    }

    fun playPrevious() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val prev = if (_currentIndex.value <= 0) list.lastIndex else _currentIndex.value - 1
        playAt(prev)
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            val target = positionMs.coerceIn(0, player.duration)
            player.seekTo(target)
            _position.value = target
        }
    }

    private fun startPlayback(track: MusicItem, index: Int) {
        releasePlayer()
        val player = MediaPlayer()
        try {
            player.setDataSource(getApplication(), track.uri)
            player.setOnPreparedListener { prepared ->
                _duration.value = prepared.duration
                _position.value = prepared.currentPosition
                prepared.start()
                _isPlaying.value = true
                startProgressUpdates()
            }
            player.setOnCompletionListener {
                _isPlaying.value = false
                _position.value = 0
                stopProgressUpdates()
            }
            player.setOnErrorListener { mp, _, _ ->
                mp?.release()
                mediaPlayer = null
                _isPlaying.value = false
                _position.value = 0
                _duration.value = 0
                stopProgressUpdates()
                true
            }
            player.prepareAsync()
            mediaPlayer = player
            _currentIndex.value = index
            _currentTrack.value = track
        } catch (e: Exception) {
            player.release()
        }
    }

    private fun startProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgressUpdates() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        stopProgressUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
