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
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.domain.model.CloudMusicCategory
import com.example.sonicwavev4.DownloadableFile
import com.example.sonicwavev4.MainActivity
import com.example.sonicwavev4.MusicDownloadDialogFragment
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.R
import com.example.sonicwavev4.DownloadedMusicRepository
import com.example.sonicwavev4.MusicDownloadEvent
import com.example.sonicwavev4.MusicDownloadEventBus
import com.example.sonicwavev4.repository.LocalPlaylist
import kotlinx.coroutines.launch
import android.net.Uri

class MusicDialogFragment : DialogFragment() {

    private enum class MusicSection {
        CLOUD, LOCAL, MY_LIST
    }

    private var selectedSection: MusicSection = MusicSection.CLOUD
    private var isCloudExpanded = true
    private var isLocalExpanded = true
    private var isMyListExpanded = true

    private lateinit var cloudAdapter: CloudCategoryAdapter
    private lateinit var playlistAdapter: MyPlaylistAdapter
    private lateinit var songAdapter: SongAdapter
    private lateinit var songsRecyclerView: RecyclerView
    private var currentCloudSongs: List<MusicItem> = emptyList()

    private val musicViewModel: MusicPlayerViewModel by activityViewModels()
    private val musicLibraryViewModel: MusicLibraryViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?) =
        super.onCreateDialog(savedInstanceState).apply {
            setCanceledOnTouchOutside(true)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_music_player, container, false)
        val rvCloudCategories: RecyclerView = view.findViewById(R.id.rvCloudCategories)
        val rvMyPlaylists: RecyclerView = view.findViewById(R.id.rvMyPlaylists)
        val rvSongs: RecyclerView = view.findViewById(R.id.rvSongs)
        songsRecyclerView = rvSongs
        val myListGroup: View = view.findViewById(R.id.llMyListGroup)
        val myListChildren: View = view.findViewById(R.id.llMyListChildren)
        val cloudGroup: View = view.findViewById(R.id.llCloudGroup)
        val rowCloudHeader: View = view.findViewById(R.id.rowCloudHeader)
        val rowLocalMusicHeader: View = view.findViewById(R.id.rowLocalMusicHeader)
        val rowMyListHeader: View = view.findViewById(R.id.rowMyListHeader)
        val tvMyListAdd: View = view.findViewById(R.id.tvMyListAdd)
        val etNewPlaylistName: EditText = view.findViewById(R.id.etNewPlaylistName)
        val root: View = view

        cloudAdapter = CloudCategoryAdapter { category ->
            musicLibraryViewModel.setShowingLocalOnly(false)
            musicLibraryViewModel.selectPlaylist(null)
            playlistAdapter.setSelectedPlaylist(null)
            setSongsAdapter(songAdapter)
            musicLibraryViewModel.onCloudCategorySelected(category.id)
        }
        cloudAdapter.setSelectionCallback {
            selectedSection = MusicSection.CLOUD
            updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
        }
        playlistAdapter = MyPlaylistAdapter { playlistId ->
            musicLibraryViewModel.setShowingLocalOnly(true)
            musicLibraryViewModel.selectPlaylist(playlistId)
            cloudAdapter.setSelected(null)
            setSongsAdapter(songAdapter)
        }
        playlistAdapter.setSelectionCallback {
            selectedSection = MusicSection.MY_LIST
            updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
        }
        songAdapter = SongAdapter(
            onSongClicked = { song ->
                val showingLocalOnly = musicLibraryViewModel.uiState.value.showingLocalOnly
                if (showingLocalOnly) {
                    musicViewModel.playTrack(song)
                } else {
                    val playlist = currentCloudSongs.ifEmpty { listOf(song) }
                    musicViewModel.setPlaylist(playlist)
                    val targetIndex = playlist.indexOfFirst { it.uri == song.uri }
                    if (targetIndex >= 0) {
                        musicViewModel.playAt(targetIndex)
                    } else {
                        musicViewModel.playTrack(song)
                    }
                }
            },
            onDownloadClicked = { song ->
                val hostActivity = activity
                if (hostActivity is MusicDownloadDialogFragment.DownloadListener) {
                    val uri = song.uri
                    val scheme = uri.scheme ?: ""
                    val isRemote = scheme.equals("http", ignoreCase = true) ||
                        scheme.equals("https", ignoreCase = true)

                    if (!song.isDownloaded && isRemote) {
                        val trackId = musicLibraryViewModel.cloudTracks.value
                            .firstOrNull { it.fileUrl == uri.toString() }
                            ?.id ?: -1L
                        val fileName = uri.lastPathSegment
                            ?: (song.title.ifBlank { "music" } + ".mp3")

                        val downloadable = DownloadableFile(
                            id = trackId,
                            title = song.title,
                            artist = song.artist,
                            downloadUrl = uri.toString(),
                            fileName = fileName,
                            isDownloaded = false,
                            isSelected = true
                        )

                        hostActivity.onDownloadSelected(listOf(downloadable))
                    }
                }
            }
        )

        rvCloudCategories.layoutManager = LinearLayoutManager(requireContext())
        rvCloudCategories.adapter = cloudAdapter

        rvMyPlaylists.layoutManager = LinearLayoutManager(requireContext())
        rvMyPlaylists.adapter = playlistAdapter

        rvSongs.layoutManager = LinearLayoutManager(requireContext())
        rvSongs.adapter = songAdapter

        updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
        updateCloudExpandedUI(isCloudExpanded, rvCloudCategories)
        updateMyListExpandedUI(isMyListExpanded, myListChildren, etNewPlaylistName)

        cloudGroup.setOnClickListener {
            if (selectedSection != MusicSection.CLOUD) {
                selectedSection = MusicSection.CLOUD
                updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
                showCloudSection()
                isCloudExpanded = true
                updateCloudExpandedUI(isCloudExpanded, rvCloudCategories)
            } else {
                isCloudExpanded = !isCloudExpanded
                updateCloudExpandedUI(isCloudExpanded, rvCloudCategories)
            }
        }

        rowLocalMusicHeader.setOnClickListener {
            if (selectedSection != MusicSection.LOCAL) {
                selectedSection = MusicSection.LOCAL
                updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
                showLocalSection()
            } else {
                isLocalExpanded = !isLocalExpanded
            }
        }

        myListGroup.setOnClickListener {
            if (selectedSection != MusicSection.MY_LIST) {
                selectedSection = MusicSection.MY_LIST
                updateSectionSelectionUI(cloudGroup, rowLocalMusicHeader, myListGroup)
                showMyListSection()
                isMyListExpanded = true
                updateMyListExpandedUI(isMyListExpanded, myListChildren, etNewPlaylistName)
            } else {
                isMyListExpanded = !isMyListExpanded
                updateMyListExpandedUI(isMyListExpanded, myListChildren, etNewPlaylistName)
            }
        }

        tvMyListAdd.setOnClickListener {
            if (!isMyListExpanded) {
                isMyListExpanded = true
                updateMyListExpandedUI(isMyListExpanded, myListChildren, etNewPlaylistName)
            }
            if (etNewPlaylistName.visibility != View.VISIBLE) {
                etNewPlaylistName.visibility = View.VISIBLE
                etNewPlaylistName.text?.clear()
                etNewPlaylistName.requestFocus()
                showKeyboard(etNewPlaylistName)
            }
        }

        etNewPlaylistName.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                (actionId == EditorInfo.IME_NULL &&
                    event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN)
            if (isDoneAction) {
                finalizePlaylistName(etNewPlaylistName)
                true
            } else {
                false
            }
        }

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN &&
                etNewPlaylistName.visibility == View.VISIBLE
            ) {
                val location = IntArray(2)
                etNewPlaylistName.getLocationOnScreen(location)
                val left = location[0]
                val top = location[1]
                val right = left + etNewPlaylistName.width
                val bottom = top + etNewPlaylistName.height
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                val inside = x in left..right && y in top..bottom
                if (!inside) {
                    finalizePlaylistName(etNewPlaylistName)
                }
            }
            false
        }

        musicLibraryViewModel.loadCloudMusicInitial()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    musicViewModel.playlist.collect { songs ->
                        val nonCloudSongs = songs.filterNot {
                            val scheme = it.uri.scheme.orEmpty().lowercase()
                            scheme == "http" || scheme == "https"
                        }
                        if (nonCloudSongs.isNotEmpty() || songs.isEmpty()) {
                            musicLibraryViewModel.setAllSongs(nonCloudSongs)
                        }
                    }
                }
                launch {
                    musicViewModel.currentTrack.collect { track ->
                        songAdapter.setCurrentTrack(track)
                    }
                }
                launch {
                    musicLibraryViewModel.cloudCategories.collect { categories ->
                        cloudAdapter.submitList(
                            categories,
                            musicLibraryViewModel.selectedCloudCategoryId.value
                        )
                    }
                }
                launch {
                    musicLibraryViewModel.selectedCloudCategoryId.collect { selectedId ->
                        cloudAdapter.setSelected(selectedId)
                    }
                }
                launch {
                    musicLibraryViewModel.cloudTracks.collect { tracks ->
                        if (!musicLibraryViewModel.uiState.value.showingLocalOnly) {
                            setSongsAdapter(songAdapter)
                        }
                        val downloadedRepo = DownloadedMusicRepository(requireContext().applicationContext)
                        val downloadedMap = downloadedRepo.findByCloudTrackIds(tracks.map { it.id })
                        currentCloudSongs = tracks.map { track ->
                            MusicItem(
                                title = track.title,
                                artist = track.artist,
                                uri = Uri.parse(track.fileUrl),
                                isDownloaded = downloadedMap.containsKey(track.id)
                            )
                        }
                        songAdapter.submitList(currentCloudSongs)
                    }
                }
                launch {
                    musicLibraryViewModel.uiState.collect { state ->
                        if (state.showingLocalOnly) {
                            setSongsAdapter(songAdapter)
                            songAdapter.submitList(state.visibleSongs)
                        }
                        playlistAdapter.submitPlaylists(state.playlists)
                        playlistAdapter.setSelectedPlaylist(state.selectedPlaylistId)
                    }
                }
                launch {
                    MusicDownloadEventBus.events.collect { event ->
                        when (event) {
                            is MusicDownloadEvent.Success -> songAdapter.markAsDownloadedByUrl(event.downloadUrl)
                        }
                    }
                }
            }
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        val dialogWindow = dialog?.window ?: return
        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 3) / 4
        dialogWindow.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
        dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogWindow.setDimAmount(0.5f)
    }

    private fun finalizePlaylistName(editText: EditText) {
        val name = editText.text?.toString().orEmpty().trim()
        if (name.isNotEmpty()) {
            musicLibraryViewModel.createPlaylist(name)
        }
        editText.text?.clear()
        editText.visibility = View.GONE
        hideKeyboard(editText)
    }

    private fun showKeyboard(editText: EditText) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard(editText: EditText) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
    }

    private fun showSongContextMenu(song: MusicItem) {
        val state = musicLibraryViewModel.uiState.value
        val playlists = state.playlists
        if (playlists.isEmpty()) return

        val selectedPlaylistId = state.selectedPlaylistId
        val options = if (selectedPlaylistId != null) {
            arrayOf(
                getString(R.string.dialog_option_add_to_playlist),
                getString(R.string.dialog_option_remove_from_playlist)
            )
        } else {
            arrayOf(getString(R.string.dialog_option_add_to_playlist))
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_song_options_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddToPlaylistDialog(song)
                    1 -> {
                        val playlist = playlists.firstOrNull { it.id == selectedPlaylistId }
                        if (playlist != null) {
                            musicLibraryViewModel.removeTrackFromPlaylist(playlist.id, song)
                        }
                    }
                }
            }
            .show()
    }

    private fun showAddToPlaylistDialog(song: MusicItem) {
        val playlists = musicLibraryViewModel.uiState.value.playlists
        if (playlists.isEmpty()) return

        val names = playlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_add_to_my_list_title)
            .setMessage(R.string.dialog_add_to_my_list_message)
            .setItems(names) { _, index ->
                val target = playlists.getOrNull(index) ?: return@setItems
                musicLibraryViewModel.addTrackToPlaylist(target.id, song)
            }
            .setNegativeButton(R.string.dialog_add_to_my_list_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun setSongsAdapter(adapter: RecyclerView.Adapter<*>) {
        if (songsRecyclerView.adapter !== adapter) {
            songsRecyclerView.adapter = adapter
        }
    }

    private fun updateSectionSelectionUI(
        cloudView: View,
        localView: View,
        myListView: View
    ) {
        val context = cloudView.context
        val normal = context.getDrawable(R.drawable.bg_music_section_normal)
        val selected = context.getDrawable(R.drawable.bg_music_section_selected)

        cloudView.background = if (selectedSection == MusicSection.CLOUD) selected else normal
        localView.background = if (selectedSection == MusicSection.LOCAL) selected else normal
        myListView.background = if (selectedSection == MusicSection.MY_LIST) selected else normal
    }

    private fun updateCloudExpandedUI(expanded: Boolean, list: RecyclerView) {
        list.visibility = if (expanded) View.VISIBLE else View.GONE
    }

    private fun updateMyListExpandedUI(expanded: Boolean, children: View, nameInput: EditText) {
        children.visibility = if (expanded) View.VISIBLE else View.GONE
        if (!expanded && nameInput.visibility == View.VISIBLE) {
            nameInput.visibility = View.GONE
            hideKeyboard(nameInput)
        }
    }

    private fun showCloudSection() {
        musicLibraryViewModel.setShowingLocalOnly(false)
        musicLibraryViewModel.selectPlaylist(null)
        playlistAdapter.setSelectedPlaylist(null)
        setSongsAdapter(songAdapter)
    }

    private fun showLocalSection() {
        musicLibraryViewModel.setShowingLocalOnly(true)
        musicLibraryViewModel.selectPlaylist(null)
        cloudAdapter.setSelected(null)
        setSongsAdapter(songAdapter)
        playlistAdapter.setSelectedPlaylist(null)
    }

    private fun showMyListSection() {
        musicLibraryViewModel.setShowingLocalOnly(true)
        setSongsAdapter(songAdapter)
    }

    private class CloudCategoryAdapter(
        private val onClick: (CloudMusicCategory) -> Unit
    ) : RecyclerView.Adapter<CloudCategoryAdapter.CategoryViewHolder>() {

        private val items = mutableListOf<CloudMusicCategory>()
        private var selectedId: Long? = null
        private var selectionCallback: (() -> Unit)? = null

        fun setSelectionCallback(callback: () -> Unit) {
            selectionCallback = callback
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return CategoryViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item.name, item.id == selectedId)
            holder.itemView.setOnClickListener {
                if (selectedId != item.id) {
                    setSelected(item.id)
                    selectionCallback?.invoke()
                }
                selectionCallback?.invoke()
                onClick(item)
            }
        }

        fun submitList(data: List<CloudMusicCategory>, selected: Long?) {
            items.clear()
            items.addAll(data)
            selectedId = selected
            notifyDataSetChanged()
        }

        fun setSelected(id: Long?) {
            val previous = selectedId
            selectedId = id
            if (previous != null) {
                val prevIndex = items.indexOfFirst { it.id == previous }
                if (prevIndex != -1) notifyItemChanged(prevIndex)
            }
            if (selectedId != null) {
                val newIndex = items.indexOfFirst { it.id == selectedId }
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }

        class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.tvCategoryName)

            fun bind(name: String, selected: Boolean) {
                title.text = name
                itemView.setStartPaddingDp(16)
                title.setBackgroundColor(
                    if (selected) title.context.getColor(R.color.teal_200) else Color.TRANSPARENT
                )
            }
        }
    }

    private class MyPlaylistAdapter(
        private val onPlaylistSelected: (String?) -> Unit
    ) : RecyclerView.Adapter<MyPlaylistAdapter.PlaylistViewHolder>() {

        private val playlists = mutableListOf<LocalPlaylist>()
        private var selectedId: String? = null
        private var selectionCallback: (() -> Unit)? = null

        fun setSelectionCallback(callback: () -> Unit) {
            selectionCallback = callback
        }

        fun submitPlaylists(data: List<LocalPlaylist>) {
            playlists.clear()
            playlists.addAll(data)
            if (selectedId != null && playlists.none { it.id == selectedId }) {
                selectedId = null
            }
            notifyDataSetChanged()
        }

        fun setSelectedPlaylist(id: String?) {
            selectedId = id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return PlaylistViewHolder(view)
        }

        override fun getItemCount(): Int = playlists.size

        override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
            val playlist = playlists[position]
            holder.bind(playlist.name, playlist.id == selectedId)
            holder.itemView.setOnClickListener {
                if (selectedId != playlist.id) {
                    selectedId = playlist.id
                    notifyDataSetChanged()
                    selectionCallback?.invoke()
                }
                selectionCallback?.invoke()
                onPlaylistSelected(playlist.id)
            }
        }

        class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: TextView = itemView.findViewById(R.id.tvCategoryName)

            fun bind(name: String, selected: Boolean) {
                title.text = name
                itemView.setStartPaddingDp(16)
                title.setBackgroundColor(
                    if (selected) title.context.getColor(R.color.teal_200) else Color.TRANSPARENT
                )
            }
        }
    }

    private class SongAdapter(
        private val onSongClicked: (MusicItem) -> Unit,
        private val onDownloadClicked: (MusicItem) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

        private val items = mutableListOf<MusicItem>()
        private var playingTrack: MusicItem? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return SongViewHolder(view, onSongClicked, onDownloadClicked)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            val item = items[position]
            val isPlaying = item.uri == playingTrack?.uri
            holder.bind(item, isPlaying)
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

        fun markAsDownloaded(target: MusicItem) {
            val index = items.indexOfFirst { it.uri == target.uri }
            if (index != -1) {
                val old = items[index]
                items[index] = old.copy(isDownloaded = true)
                notifyItemChanged(index)
            }
        }

        fun markAsDownloadedByUrl(url: String) {
            val index = items.indexOfFirst { it.uri.toString() == url }
            if (index != -1) {
                val old = items[index]
                items[index] = old.copy(isDownloaded = true)
                notifyItemChanged(index)
            }
        }

        class SongViewHolder(
            itemView: View,
            private val onSongClicked: (MusicItem) -> Unit,
            private val onDownloadClicked: (MusicItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {

            private val title: TextView = itemView.findViewById(R.id.tvSongTitle)
            private val downloadIcon: ImageView = itemView.findViewById(R.id.ivDownloadStatus)

            private val downloadedColor: Int = title.context.getColor(R.color.black)
            private val pendingDownloadColor: Int = title.context.getColor(R.color.nav_item_inactive)
            private val normalBackground = itemView.context.getDrawable(R.drawable.bg_song_item)
            private val selectedBackground = itemView.context.getDrawable(R.drawable.bg_song_item_selected)

            fun bind(item: MusicItem, playing: Boolean) {
                title.text = item.title

                val scheme = item.uri.scheme ?: ""
                val isRemote = scheme.equals("http", ignoreCase = true) ||
                    scheme.equals("https", ignoreCase = true)

                val isDownloaded = item.isDownloaded || !isRemote

                val baseColor = if (isDownloaded) downloadedColor else pendingDownloadColor
                title.setTextColor(baseColor)

                itemView.background = if (playing) selectedBackground else normalBackground

                if (isRemote) {
                    downloadIcon.visibility = View.VISIBLE
                    if (isDownloaded) {
                        downloadIcon.setImageResource(R.drawable.ic_music_download_done)
                        downloadIcon.imageAlpha = 255
                        downloadIcon.setOnClickListener(null)
                    } else {
                        downloadIcon.setImageResource(R.drawable.ic_music_download)
                        downloadIcon.imageAlpha = 200
                        downloadIcon.setOnClickListener {
                            onDownloadClicked(item)
                        }
                    }
                } else {
                    downloadIcon.visibility = View.GONE
                    downloadIcon.setOnClickListener(null)
                }

                itemView.setOnClickListener { onSongClicked(item) }
            }
        }
    }
}

private fun View.setStartPaddingDp(dp: Int) {
    val density = resources.displayMetrics.density
    val px = (dp * density).toInt()
    setPadding(px, paddingTop, paddingRight, paddingBottom)
}
