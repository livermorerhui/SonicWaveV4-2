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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R

class MusicDialogFragment : DialogFragment() {

    private val categories = listOf("放松", "专注", "睡眠")
    private val songsByCategory = mapOf(
        "放松" to listOf("轻柔海浪", "森林细语", "午后咖啡"),
        "专注" to listOf("白噪音", "键盘雨", "专注脉冲"),
        "睡眠" to listOf("舒眠摇篮曲", "夜空之下", "星光低语")
    )

    private var selectedCategory = 0

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var songAdapter: SongAdapter

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
        songAdapter = SongAdapter()

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

        return view
    }

    override fun onStart() {
        super.onStart()
        val dialogWindow = dialog?.window ?: return
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        dialogWindow.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val width = (displayMetrics.widthPixels * 0.9).toInt()
        val height = (displayMetrics.heightPixels * 0.9).toInt()
        dialogWindow.setLayout(width, height)
        dialogWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun updateSongs() {
        val category = categories.getOrNull(selectedCategory)
        val songs = songsByCategory[category] ?: emptyList()
        songAdapter.submitList(songs)
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

    private class SongAdapter : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {
        private val items = mutableListOf<String>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_song, parent, false)
            return SongViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            holder.bind(items[position])
        }

        fun submitList(data: List<String>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val title: android.widget.TextView = itemView.findViewById(R.id.tvSongTitle)
            fun bind(name: String) {
                title.text = name
            }
        }
    }
}
