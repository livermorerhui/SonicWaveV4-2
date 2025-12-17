package com.example.sonicwavev4.ui.music.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.repository.LocalPlaylist

class MyPlaylistListAdapter(
    private val onPlaylistClick: (String) -> Unit
) : ListAdapter<LocalPlaylist, MyPlaylistListAdapter.PlaylistViewHolder>(DiffCallback) {

    var selectedPlaylistId: String? = null
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }
    var isActive: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }
    private var onSongDropped: ((String, DragPayload) -> Unit)? = null

    fun setOnSongDroppedListener(listener: (playlistId: String, payload: DragPayload) -> Unit) {
        onSongDropped = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val item = getItem(position)
        val isSelected = item.id == selectedPlaylistId
        val showSelected = isActive && isSelected
        holder.bind(item, showSelected)
        holder.itemView.setOnClickListener { onPlaylistClick(item.id) }
        holder.itemView.setOnDragListener { v, event ->
            val payload = event.localState as? DragPayload ?: return@setOnDragListener false
            when (event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> true
                android.view.DragEvent.ACTION_DRAG_ENTERED -> {
                    val res = if (showSelected) {
                        R.drawable.bg_playlist_drop_target_selected
                    } else {
                        R.drawable.bg_playlist_drop_target
                    }
                    v.background = ContextCompat.getDrawable(v.context, res)
                    true
                }

                android.view.DragEvent.ACTION_DRAG_EXITED -> {
                    holder.applyBaseBackground(showSelected)
                    true
                }

                android.view.DragEvent.ACTION_DROP -> {
                    holder.applyBaseBackground(showSelected)
                    onSongDropped?.invoke(item.id, payload)
                    true
                }

                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    holder.applyBaseBackground(showSelected)
                    true
                }

                else -> false
            }
        }
    }

    class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.categoryContainer)
        private val title: TextView = itemView.findViewById(R.id.tvCategoryName)

        fun bind(item: LocalPlaylist, selected: Boolean) {
            title.text = item.name
            applyBaseBackground(selected)
        }

        fun applyBaseBackground(selected: Boolean) {
            val res = if (selected) R.drawable.bg_sidebar_child_selected else R.drawable.bg_sidebar_child_normal
            container.background = ContextCompat.getDrawable(container.context, res)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<LocalPlaylist>() {
        override fun areItemsTheSame(oldItem: LocalPlaylist, newItem: LocalPlaylist): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LocalPlaylist, newItem: LocalPlaylist): Boolean =
            oldItem == newItem
    }
}
