package com.example.sonicwavev4

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
        val checkBox: CheckBox = view.findViewById(R.id.music_checkbox)
        val title: TextView = view.findViewById(R.id.music_title_textview)
        val artist: TextView = view.findViewById(R.id.music_artist_textview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloadable_music, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = musicFiles[position]
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = file.isSelected
        holder.title.text = file.title.ifBlank { file.fileName }
        holder.artist.text = file.artist.ifBlank { "未知艺术家" }

        if (file.isDownloaded) {
            holder.title.setTextColor(Color.GRAY)
            holder.artist.setTextColor(Color.GRAY)
            holder.checkBox.isEnabled = false
        } else {
            holder.title.setTextColor(Color.BLACK)
            holder.artist.setTextColor(Color.DKGRAY)
            holder.checkBox.isEnabled = true
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            file.isSelected = isChecked
        }
        holder.itemView.setOnClickListener {
            if (!file.isDownloaded && holder.checkBox.isEnabled) {
                holder.checkBox.isChecked = !holder.checkBox.isChecked
            }
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
