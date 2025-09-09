package com.example.sonicwavev4

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DownloadableFile(
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloadable_music, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = musicFiles[position]
        holder.title.text = file.fileName
        holder.checkBox.isChecked = file.isSelected

        if (file.isDownloaded) {
            holder.title.setTextColor(Color.GRAY)
            holder.checkBox.isEnabled = false
        } else {
            holder.title.setTextColor(Color.BLACK)
            holder.checkBox.isEnabled = true
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            file.isSelected = isChecked
        }
    }

    override fun getItemCount() = musicFiles.size

    fun getSelectedFiles(): List<String> {
        return musicFiles.filter { it.isSelected && !it.isDownloaded }.map { it.fileName }
    }
}