package com.example.sonicwavev4.ui.persetmode.editor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.databinding.ItemCustomPresetStepBinding

class CustomPresetStepAdapter(
    private val onMoveUp: (String) -> Unit,
    private val onMoveDown: (String) -> Unit,
    private val onEdit: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<CustomPresetStepAdapter.StepViewHolder>() {

    private val items: MutableList<CustomPresetStep> = mutableListOf()

    fun submitList(newItems: List<CustomPresetStep>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCustomPresetStepBinding.inflate(inflater, parent, false)
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(items[position], position, items.lastIndex)
    }

    override fun getItemCount(): Int = items.size

    inner class StepViewHolder(
        private val binding: ItemCustomPresetStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: CustomPresetStep, position: Int, lastIndex: Int) {
            binding.tvStepTitle.text = "步骤 ${position + 1}"
            binding.tvStepDetail.text =
                "频率 ${step.frequencyHz}Hz · 强度 ${step.intensity01V} · 时长 ${step.durationSec}秒"
            binding.btnMoveUp.isEnabled = position > 0
            binding.btnMoveDown.isEnabled = position < lastIndex

            binding.btnMoveUp.setOnClickListener { onMoveUp(step.id) }
            binding.btnMoveDown.setOnClickListener { onMoveDown(step.id) }
            binding.btnEdit.setOnClickListener { onEdit(step.id) }
            binding.btnDelete.setOnClickListener { onDelete(step.id) }
        }
    }
}
