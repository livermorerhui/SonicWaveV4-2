package com.example.sonicwavev4.ui.music.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.domain.model.CloudMusicCategory

class CloudCategoryListAdapter(
    private val onCategoryClick: (CloudMusicCategory) -> Unit
) : ListAdapter<CloudMusicCategory, CloudCategoryListAdapter.CategoryViewHolder>(DiffCallback) {

    var selectedCategoryId: Long? = null
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id == selectedCategoryId, isActive)
        holder.itemView.setOnClickListener {
            onCategoryClick(item)
        }
    }

    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: View = itemView.findViewById(R.id.categoryContainer)
        private val title: TextView = itemView.findViewById(R.id.tvCategoryName)

        fun bind(item: CloudMusicCategory, selected: Boolean, isActive: Boolean) {
            title.text = item.name
            val showSelected = selected && isActive
            container.setBackgroundResource(
                if (showSelected) R.drawable.bg_sidebar_child_selected else R.drawable.bg_sidebar_child_normal
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<CloudMusicCategory>() {
        override fun areItemsTheSame(
            oldItem: CloudMusicCategory,
            newItem: CloudMusicCategory
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: CloudMusicCategory,
            newItem: CloudMusicCategory
        ): Boolean = oldItem == newItem
    }
}
