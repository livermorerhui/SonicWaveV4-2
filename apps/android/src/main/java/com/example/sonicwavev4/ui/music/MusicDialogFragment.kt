package com.example.sonicwavev4.ui.music

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.MusicItem
import com.example.sonicwavev4.R
import kotlinx.coroutines.launch

class MusicDialogFragment : DialogFragment() {

    private val categories = listOf("放松", "专注", "睡眠")

    private var selectedCategory = 0

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var songAdapter: SongAdapter

    private val musicViewModel: MusicPlayerViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_music_player, container, false)
        val rvCategories: RecyclerView = view.findViewById(R.id.rvCategories)
        val rvSongs: RecyclerView = view.findViewById(R.id.rvSongs)
        val btnClose: View = view.findViewById(R.id.btnCloseMusic)

        categoryAdapter = CategoryAdapter(categories) { position ->
            if (selectedCategory != position) {
                selectedCategory = position
                categoryAdapter.setSelected(position)
                updateSongs()
            }
        }
        songAdapter = SongAdapter { song ->
            musicViewModel.playTrack(song)
        }

        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoryAdapter
        }
        rvSongs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = songAdapter
        }

        categoryAdapter.setSelected(selectedCategory)
        updateSongs()

        btnClose.setOnClickListener { dismiss() }
        isCancelable = false

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    musicViewModel.playlist.collect { songs ->
                        songAdapter.submitList(songs)
                    }
                }
                launch {
                    musicViewModel.currentIndex.collect { index ->
                        songAdapter.setCurrentIndex(index)
                    }
                }
            }
        }

        return view
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

    private fun updateSongs() {
        // Categories are placeholders for now; all songs share the same playlist.
        songAdapter.submitList(musicViewModel.playlist.value)
    }

    private class CategoryAdapter(
        private val items: List<String>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

        private var selectedIndex = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category, parent, false)
            return CategoryViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
            holder.bind(items[position], position == selectedIndex)
            holder.itemView.setOnClickListener {
                onClick(position)
            }
        }

        fun setSelected(position: Int) {
            val previous = selectedIndex
            selectedIndex = position
            notifyItemChanged(previous)
            notifyItemChanged(selectedIndex)
        }

        class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: android.widget.TextView = itemView.findViewById(R.id.tvCategoryName)

            fun bind(name: String, selected: Boolean) {
                title.text = name
                title.setBackgroundColor(
                    if (selected) title.context.getColor(R.color.teal_200) else Color.TRANSPARENT
                )
            }
        }
    }

    private class SongAdapter(
        private val onSongClicked: (MusicItem) -> Unit
    ) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
        private val items = mutableListOf<MusicItem>()
        private var playingIndex: Int = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return SongViewHolder(view, onSongClicked)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(items[position], position == playingIndex)
        }

        fun submitList(data: List<MusicItem>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        fun setCurrentIndex(index: Int) {
            val previous = playingIndex
            playingIndex = index
            if (previous != -1) notifyItemChanged(previous)
            if (index != -1) notifyItemChanged(index)
        }

        class SongViewHolder(
            itemView: View,
            private val onSongClicked: (MusicItem) -> Unit
        ) : RecyclerView.ViewHolder(itemView) {
            private val title: android.widget.TextView = itemView.findViewById(R.id.tvSongTitle)
            private val defaultColor: Int = title.currentTextColor

            fun bind(item: MusicItem, playing: Boolean) {
                title.text = item.title
                title.setTextColor(
                    if (playing) title.context.getColor(R.color.teal_200) else defaultColor
                )
                itemView.setOnClickListener { onSongClicked(item) }
            }
        }
    }
}
