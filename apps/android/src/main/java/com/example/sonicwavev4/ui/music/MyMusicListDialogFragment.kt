package com.example.sonicwavev4.ui.music

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.R
import com.example.sonicwavev4.musiclibrary.LocalPlaylist
import kotlinx.coroutines.launch

class MyMusicListDialogFragment : DialogFragment() {

    private val musicViewModel: MusicPlayerViewModel by activityViewModels()
    private val libraryViewModel: MusicLibraryViewModel by activityViewModels()

    private lateinit var playlistAdapter: LocalPlaylistAdapter
    private lateinit var songAdapter: LocalSongAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        super.onCreateDialog(savedInstanceState).apply {
            setOnKeyListener { _, keyCode, _ ->
                keyCode == KeyEvent.KEYCODE_BACK
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_my_music_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rvPlaylists: RecyclerView = view.findViewById(R.id.rvMyPlaylists)
        val rvLocalSongs: RecyclerView = view.findViewById(R.id.rvLocalSongs)
        val btnCreatePlaylist: View = view.findViewById(R.id.btnCreatePlaylist)
        val btnClose: View = view.findViewById(R.id.btnCloseMyList)
        val etNewPlaylist: EditText = view.findViewById(R.id.etNewPlaylistName)
        val root: View = view.findViewById(R.id.rootMyListDialog)

        playlistAdapter = LocalPlaylistAdapter { /* playlist selection placeholder */ }
        songAdapter = LocalSongAdapter { song ->
            musicViewModel.playTrack(song)
        }

        rvPlaylists.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playlistAdapter
        }
        rvLocalSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }

        btnCreatePlaylist.setOnClickListener {
            if (etNewPlaylist.visibility != View.VISIBLE) {
                etNewPlaylist.visibility = View.VISIBLE
                etNewPlaylist.text?.clear()
                etNewPlaylist.requestFocus()
                showKeyboard(etNewPlaylist)
            }
        }

        btnClose.setOnClickListener { dismiss() }

        etNewPlaylist.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                (actionId == EditorInfo.IME_NULL &&
                    event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            if (isDoneAction) {
                val name = etNewPlaylist.text?.toString().orEmpty().trim()
                if (name.isNotEmpty()) {
                    libraryViewModel.createPlaylist(name)
                }
                etNewPlaylist.text?.clear()
                etNewPlaylist.visibility = View.GONE
                hideKeyboard(etNewPlaylist)
                true
            } else {
                false
            }
        }

        root.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN &&
                etNewPlaylist.visibility == View.VISIBLE
            ) {
                val location = IntArray(2)
                etNewPlaylist.getLocationOnScreen(location)
                val left = location[0]
                val top = location[1]
                val right = left + etNewPlaylist.width
                val bottom = top + etNewPlaylist.height
                val x = motionEvent.rawX.toInt()
                val y = motionEvent.rawY.toInt()
                val inside = x in left..right && y in top..bottom
                if (!inside) {
                    etNewPlaylist.text?.clear()
                    etNewPlaylist.visibility = View.GONE
                    hideKeyboard(etNewPlaylist)
                }
            }
            false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.playlists.collect { playlists ->
                        playlistAdapter.submitPlaylists(playlists)
                    }
                }
                launch {
                    musicViewModel.playlist.collect { tracks ->
                        val localOnly = tracks.filter { !it.isDownloaded }
                        songAdapter.submitList(localOnly)
                    }
                }
                launch {
                    musicViewModel.currentTrack.collect { track ->
                        songAdapter.setCurrentTrack(track)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialogWindow = dialog?.window ?: return
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        dialogWindow.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val width = (displayMetrics.widthPixels * 0.8).toInt()
        val height = (displayMetrics.heightPixels * 0.8).toInt()
        dialogWindow.setLayout(width, height)
        dialogWindow.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogWindow.setDimAmount(0.5f)
    }

    private fun showKeyboard(editText: EditText) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private class LocalPlaylistAdapter(
        private val onPlaylistSelected: (String?) -> Unit
    ) : RecyclerView.Adapter<LocalPlaylistAdapter.PlaylistViewHolder>() {

        private val playlists = mutableListOf<LocalPlaylist>()
        private var selectedId: String? = null

        fun submitPlaylists(data: List<LocalPlaylist>) {
            playlists.clear()
            playlists.addAll(data)
            notifyDataSetChanged()
            if (selectedId == null) {
                onPlaylistSelected(null)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return PlaylistViewHolder(view)
        }

        override fun getItemCount(): Int = playlists.size + 1

        override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
            if (position == 0) {
                val isSelected = selectedId == null
                holder.bind("全部本地音乐", isSelected)
                holder.itemView.setOnClickListener {
                    if (selectedId != null) {
                        selectedId = null
                        notifyDataSetChanged()
                        onPlaylistSelected(null)
                    }
                }
            } else {
                val playlist = playlists[position - 1]
                val isSelected = playlist.id == selectedId
                holder.bind(playlist.name, isSelected)
                holder.itemView.setOnClickListener {
                    if (selectedId != playlist.id) {
                        selectedId = playlist.id
                        notifyDataSetChanged()
                        onPlaylistSelected(playlist.id)
                    }
                }
            }
        }

        class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.tvCategoryName)

            fun bind(name: String, selected: Boolean) {
                title.text = name
                title.setBackgroundColor(
                    if (selected) title.context.getColor(R.color.teal_200) else Color.TRANSPARENT
                )
            }
        }
    }

    private class LocalSongAdapter(
        private val onSongClicked: (MusicItem) -> Unit
    ) : RecyclerView.Adapter<LocalSongAdapter.SongViewHolder>() {

        private val items = mutableListOf<MusicItem>()
        private var playingTrack: MusicItem? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return SongViewHolder(view, onSongClicked)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(items[position], items[position].uri == playingTrack?.uri)
        }

        fun submitList(data: List<MusicItem>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        fun setCurrentTrack(track: MusicItem?) {
            val previousIndex = playingTrack?.let { previous ->
                items.indexOfFirst { it.uri == previous.uri }
            } ?: -1
            playingTrack = track
            val newIndex = track?.let { current ->
                items.indexOfFirst { it.uri == current.uri }
            } ?: -1
            if (previousIndex != -1) notifyItemChanged(previousIndex)
            if (newIndex != -1 && newIndex != previousIndex) notifyItemChanged(newIndex)
        }

        class SongViewHolder(
            itemView: View,
            private val onSongClicked: (MusicItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.tvSongTitle)
            private val downloadedColor: Int = title.context.getColor(R.color.black)
            private val pendingDownloadColor: Int = title.context.getColor(R.color.nav_item_inactive)
            private val playingColor: Int = title.context.getColor(R.color.teal_200)

            fun bind(item: MusicItem, playing: Boolean) {
                title.text = item.title
                val baseColor = if (item.isDownloaded) downloadedColor else pendingDownloadColor
                title.setTextColor(if (playing) playingColor else baseColor)
                itemView.setOnClickListener { onSongClicked(item) }
            }
        }
    }
}
