package com.example.sonicwavev4

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DownloadableFile(
    val id: Long,
    val title: String,
    val artist: String,
    val downloadUrl: String,
    val fileName: String,
    var isDownloaded: Boolean,
    var isSelected: Boolean
)

class DownloadableMusicAdapter(
    private val musicFiles: MutableList<DownloadableFile>
) : RecyclerView.Adapter<DownloadableMusicAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.music_title_textview)
        val artist: TextView = view.findViewById(R.id.music_artist_textview)
        val statusIcon: ImageView = view.findViewById(R.id.music_download_status_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloadable_music, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = musicFiles[position]
        holder.title.text = file.title.ifBlank { file.fileName }
        holder.artist.text = file.artist.ifBlank { "未知艺术家" }

        holder.itemView.setOnClickListener(null)
        holder.statusIcon.setOnClickListener(null)

        if (file.isDownloaded) {
            holder.title.setTextColor(Color.BLACK)
            holder.artist.setTextColor(Color.BLACK)
            holder.statusIcon.setImageResource(android.R.drawable.checkbox_on_background)
            holder.statusIcon.isEnabled = false
        } else {
            val isSelected = file.isSelected

            val textColor = if (isSelected) Color.BLACK else Color.GRAY
            holder.title.setTextColor(textColor)
            holder.artist.setTextColor(textColor)

            holder.statusIcon.setImageResource(android.R.drawable.stat_sys_download)
            holder.statusIcon.isEnabled = true

            val toggleSelection: (View) -> Unit = {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val current = musicFiles[adapterPosition]
                    if (!current.isDownloaded) {
                        current.isSelected = !current.isSelected
                        notifyItemChanged(adapterPosition)
                    }
                }
            }

            holder.itemView.setOnClickListener(toggleSelection)
            holder.statusIcon.setOnClickListener(toggleSelection)
        }
    }

    override fun getItemCount() = musicFiles.size

    fun updateData(newFiles: List<DownloadableFile>) {
        musicFiles.clear()
        musicFiles.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): List<DownloadableFile> {
        return musicFiles.filter { it.isSelected && !it.isDownloaded }
    }
}
