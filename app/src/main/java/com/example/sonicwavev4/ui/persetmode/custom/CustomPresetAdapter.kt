package com.example.sonicwavev4.ui.persetmode.custom

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.ItemCustomPresetBinding
import com.example.sonicwavev4.ui.persetmode.CustomPresetUiModel
import com.google.android.material.color.MaterialColors

class CustomPresetAdapter(
    private val onSelect: (CustomPresetUiModel) -> Unit,
    private val onEdit: (CustomPresetUiModel) -> Unit,
    private val onDelete: (CustomPresetUiModel) -> Unit
) : RecyclerView.Adapter<CustomPresetAdapter.CustomPresetViewHolder>() {

    private val items: MutableList<CustomPresetUiModel> = mutableListOf()

    fun submitList(newItems: List<CustomPresetUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition == toPosition ||
            fromPosition !in items.indices ||
            toPosition !in items.indices
        ) return false
        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun currentOrderIds(): List<String> = items.map { it.id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomPresetViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCustomPresetBinding.inflate(inflater, parent, false)
        return CustomPresetViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: CustomPresetViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class CustomPresetViewHolder(
        private val binding: ItemCustomPresetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CustomPresetUiModel) {
            val highlightColor = MaterialColors.getColor(
                binding.root,
                com.google.android.material.R.attr.colorPrimary,
                ContextCompat.getColor(binding.root.context, R.color.purple_500)
            )
            val normalColor = ContextCompat.getColor(binding.root.context, android.R.color.transparent)
            binding.tvName.text = item.name
            binding.tvSummary.text = item.summary
            binding.layoutActions.isVisible = item.isSelected
            binding.root.strokeColor = if (item.isSelected) highlightColor else normalColor

            binding.root.setOnClickListener { onSelect(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
