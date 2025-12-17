package com.example.sonicwavev4.ui.music

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.DownloadableFile
import com.example.sonicwavev4.MusicDownloadDialogFragment
import com.example.sonicwavev4.MusicDownloadEvent
import com.example.sonicwavev4.MusicDownloadEventBus
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.R
import com.example.sonicwavev4.ui.music.adapters.CloudCategoryListAdapter
import com.example.sonicwavev4.ui.music.adapters.DragPayload
import com.example.sonicwavev4.ui.music.adapters.MyPlaylistListAdapter
import com.example.sonicwavev4.ui.music.adapters.SongListAdapter
import com.example.sonicwavev4.ui.music.SongRowUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicDialogFragment : DialogFragment() {

    private lateinit var cloudAdapter: CloudCategoryListAdapter
    private lateinit var playlistAdapter: MyPlaylistListAdapter
    private lateinit var songAdapter: SongListAdapter
    private var lastCloudExpanded: Boolean? = null
    private var lastCloudCategories: List<com.example.sonicwavev4.domain.model.CloudMusicCategory>? = null
    private var lastPlaylists: List<com.example.sonicwavev4.repository.LocalPlaylist>? = null
    private var lastSongs: List<SongRowUi>? = null

    private val musicViewModel: MusicPlayerViewModel by activityViewModels()
    private val dialogViewModel: MusicDialogViewModel by activityViewModels()

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
        val cloudChildren: View = view.findViewById(R.id.llCloudChildren)
        val rvMyPlaylists: RecyclerView = view.findViewById(R.id.rvMyPlaylists)
        val rvSongs: RecyclerView = view.findViewById(R.id.rvSongs)
        val cloudGroup: View = view.findViewById(R.id.llCloudGroup)
        val myListGroup: View = view.findViewById(R.id.llMyListGroup)
        val myListChildren: View = view.findViewById(R.id.llMyListChildren)
        val rowCloudHeader: View = view.findViewById(R.id.rowCloudHeader)
        val rowLocalMusicHeader: View = view.findViewById(R.id.rowLocalMusicHeader)
        val rowMyListHeader: View = view.findViewById(R.id.rowMyListHeader)
        val tvMyListAdd: View = view.findViewById(R.id.tvMyListAdd)
        val etNewPlaylistName: EditText = view.findViewById(R.id.etNewPlaylistName)
        val root: View = view

        cloudAdapter = CloudCategoryListAdapter { category ->
            dialogViewModel.onCloudCategoryClicked(category.id)
        }
        playlistAdapter = MyPlaylistListAdapter { playlistId ->
            dialogViewModel.onPlaylistClicked(playlistId)
        }
        playlistAdapter.setOnSongDroppedListener { playlistId, payload ->
            handleSongDroppedOnPlaylist(playlistId, payload)
        }
        songAdapter = SongListAdapter(
            onSongClick = { row -> handleSongClick(row) },
            onDownloadClick = { row -> handleDownloadClick(row) },
            onSongLongPress = { row -> handleSongLongPress(row) }
        )

        rvCloudCategories.layoutManager = LinearLayoutManager(requireContext())
        rvCloudCategories.adapter = cloudAdapter

        rvMyPlaylists.layoutManager = LinearLayoutManager(requireContext())
        rvMyPlaylists.adapter = playlistAdapter

        rvSongs.layoutManager = LinearLayoutManager(requireContext())
        rvSongs.adapter = songAdapter

        cloudGroup.setOnClickListener { dialogViewModel.onCloudHeaderClicked() }
        rowLocalMusicHeader.setOnClickListener { dialogViewModel.onLocalHeaderClicked() }
        myListGroup.setOnClickListener { dialogViewModel.onMyListHeaderClicked() }

        tvMyListAdd.setOnClickListener {
            val state = dialogViewModel.uiState.value
            if (!state.myListExpanded) {
                dialogViewModel.onMyListHeaderClicked()
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

        dialogViewModel.onDialogShown()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dialogViewModel.uiState.collect { state ->
                        updateSectionSelectionUI(
                            state.selectedSection,
                            cloudGroup,
                            rowLocalMusicHeader,
                            myListGroup
                        )
                        if (lastCloudExpanded != state.cloudExpanded) {
                            updateCloudExpandedUI(state.cloudExpanded, cloudGroup, cloudChildren)
                            lastCloudExpanded = state.cloudExpanded
                        }
                        updateMyListExpandedUI(
                            state.myListExpanded,
                            myListGroup,
                            myListChildren,
                            etNewPlaylistName
                        )

                        if (lastCloudCategories != state.cloudCategories) {
                            cloudAdapter.submitList(state.cloudCategories)
                            lastCloudCategories = state.cloudCategories
                        }
                        cloudAdapter.isActive = state.selectedSection == MusicSection.CLOUD
                        cloudAdapter.selectedCategoryId = state.selectedCloudCategoryId

                        if (lastPlaylists != state.playlists) {
                            playlistAdapter.submitList(state.playlists)
                            lastPlaylists = state.playlists
                        }
                        playlistAdapter.isActive = state.selectedSection == MusicSection.MY_LIST
                        playlistAdapter.selectedPlaylistId = state.selectedPlaylistId

                        if (lastSongs != state.songs) {
                            songAdapter.submitList(state.songs)
                            lastSongs = state.songs
                        }
                    }
                }
                launch {
                    musicViewModel.currentTrack.collect { track ->
                        songAdapter.currentPlayingUriString = track?.uri?.toString()
                    }
                }
                launch {
                    MusicDownloadEventBus.events.collect { event ->
                        when (event) {
                            is MusicDownloadEvent.Success -> dialogViewModel.onDownloadCompleted(event.downloadUrl)
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
        dialogWindow.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialogWindow.setDimAmount(0.5f)
    }

    private fun finalizePlaylistName(editText: EditText) {
        val name = editText.text?.toString().orEmpty().trim()
        if (name.isNotEmpty()) {
            dialogViewModel.createPlaylist(name)
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

    private fun handleSongClick(row: SongRowUi) {
        val items = songAdapter.currentList.map { rowToMusicItem(it) }
        val clickedIndex = songAdapter.currentList.indexOfFirst { it.key == row.key }
        if (items.isEmpty()) return

        musicViewModel.setPlaylist(items)
        if (clickedIndex in items.indices) {
            musicViewModel.playAt(clickedIndex)
        } else {
            musicViewModel.playTrack(rowToMusicItem(row))
        }
    }

    private fun handleDownloadClick(row: SongRowUi) {
        if (!row.isRemote || row.isDownloaded) return
        val hostActivity = activity
        if (hostActivity is MusicDownloadDialogFragment.DownloadListener) {
            val uri = Uri.parse(row.playUriString)
            val trackId = dialogViewModel.getCloudTrackIdByUrl(row.playUriString) ?: -1L
            val fileName = uri.lastPathSegment ?: (row.title.ifBlank { "music" } + ".mp3")

            val downloadable = DownloadableFile(
                id = trackId,
                title = row.title,
                artist = row.artist,
                downloadUrl = uri.toString(),
                fileName = fileName,
                isDownloaded = false,
                isSelected = true
            )
            hostActivity.onDownloadSelected(listOf(downloadable))
        }
    }

    private fun handleSongLongPress(row: SongRowUi) {
        // Fallback long-press action without drag movement: remind user to drag.
        Toast.makeText(requireContext(), "长按后拖拽到「我的列表」歌单中进行添加", Toast.LENGTH_SHORT).show()
    }

    private fun handleSongDroppedOnPlaylist(playlistId: String, payload: DragPayload) {
        val state = dialogViewModel.uiState.value
        val playlistName = state.playlists.firstOrNull { it.id == playlistId }?.name ?: ""
        val row = payload.toSongRowUi()
        if (row.isRemote && (!row.isDownloaded || row.downloadedLocalUriString.isNullOrEmpty())) {
            Toast.makeText(requireContext(), "请先下载后再添加到歌单", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            dialogViewModel.addSongRowToPlaylist(playlistId, row)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "已添加到歌单：$playlistName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun DragPayload.toSongRowUi(): SongRowUi = SongRowUi(
        key = songKey,
        title = title,
        artist = artist,
        playUriString = playableUriString,
        isRemote = isRemote,
        isDownloaded = isDownloaded,
        downloadedLocalUriString = downloadedLocalUriString
    )

    private fun rowToMusicItem(row: SongRowUi): MusicItem {
        val uriString = row.downloadedLocalUriString ?: row.playUriString
        return MusicItem(
            title = row.title,
            artist = row.artist,
            uri = Uri.parse(uriString),
            isDownloaded = row.isDownloaded || !row.isRemote
        )
    }

    private fun updateSectionSelectionUI(
        selected: MusicSection,
        cloudView: View,
        localView: View,
        myListView: View
    ) {
        val context = cloudView.context
        val normal = context.getDrawable(R.drawable.bg_music_section_normal)
        val selectedDrawable = context.getDrawable(R.drawable.bg_music_section_selected)

        cloudView.background = if (selected == MusicSection.CLOUD) selectedDrawable else normal
        localView.background = if (selected == MusicSection.LOCAL) selectedDrawable else normal
        myListView.background = if (selected == MusicSection.MY_LIST) selectedDrawable else normal
    }

    private fun updateCloudExpandedUI(expanded: Boolean, group: View, children: View) {
        children.visibility = if (expanded) View.VISIBLE else View.GONE
        group.post { group.requestLayout() }
    }

    private fun updateMyListExpandedUI(
        expanded: Boolean,
        group: View,
        children: View,
        nameInput: EditText
    ) {
        children.visibility = if (expanded) View.VISIBLE else View.GONE
        if (!expanded && nameInput.visibility == View.VISIBLE) {
            nameInput.visibility = View.GONE
            hideKeyboard(nameInput)
        }
        group.post { group.requestLayout() }
    }
}
