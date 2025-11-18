package com.example.sonicwavev4.ui.persetmode.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep
import com.example.sonicwavev4.databinding.ItemCustomPresetStepBinding

class CustomPresetStepAdapter(
    private val onStepChanged: (Int, CustomPresetStep) -> Unit,
    private val onPlayClicked: (Int) -> Unit,
    private val onMoveUpClicked: (Int) -> Unit,
    private val onMoveDownClicked: (Int) -> Unit,
    private val onDeleteClicked: (Int) -> Unit,
    private val onFrequencyFieldClicked: (Int) -> Unit,
    private val onIntensityFieldClicked: (Int) -> Unit,
    private val onDurationFieldClicked: (Int) -> Unit,
    private val onItemMoved: (from: Int, to: Int) -> Unit
) : RecyclerView.Adapter<CustomPresetStepAdapter.StepViewHolder>() {

    private val items: MutableList<CustomPresetStep> = mutableListOf()

    fun submitList(newItems: List<CustomPresetStep>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
        onItemMoved(fromPosition, toPosition)
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

    var playingStepIndex: Int? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    inner class StepViewHolder(
        private val binding: ItemCustomPresetStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var freqWatcher: TextWatcher? = null
        private var intensityWatcher: TextWatcher? = null
        private var durationWatcher: TextWatcher? = null

        fun bind(step: CustomPresetStep, position: Int, lastIndex: Int) {
            binding.tvStepTitle.text = "步骤 ${position + 1}"

            setText(binding.etFrequency, step.frequencyHz.toString()) { new ->
                val updated = step.copy(frequencyHz = new.toIntOrNull() ?: step.frequencyHz)
                onStepChanged(bindingAdapterPosition, updated)
            }
            setText(binding.etIntensity, step.intensity01V.toString()) { new ->
                val updated = step.copy(intensity01V = new.toIntOrNull() ?: step.intensity01V)
                onStepChanged(bindingAdapterPosition, updated)
            }
            setText(binding.etDuration, step.durationSec.toString()) { new ->
                val updated = step.copy(durationSec = new.toIntOrNull() ?: step.durationSec)
                onStepChanged(bindingAdapterPosition, updated)
            }

            binding.etFrequency.showSoftInputOnFocus = false
            binding.etIntensity.showSoftInputOnFocus = false
            binding.etDuration.showSoftInputOnFocus = false
            binding.etFrequency.isFocusable = false
            binding.etFrequency.isFocusableInTouchMode = false
            binding.etIntensity.isFocusable = false
            binding.etIntensity.isFocusableInTouchMode = false
            binding.etDuration.isFocusable = false
            binding.etDuration.isFocusableInTouchMode = false
            binding.etFrequency.isLongClickable = false
            binding.etIntensity.isLongClickable = false
            binding.etDuration.isLongClickable = false
            binding.etFrequency.setTextIsSelectable(false)
            binding.etIntensity.setTextIsSelectable(false)
            binding.etDuration.setTextIsSelectable(false)
            binding.etFrequency.setOnClickListener { onFrequencyFieldClicked(bindingAdapterPosition) }
            binding.etIntensity.setOnClickListener { onIntensityFieldClicked(bindingAdapterPosition) }
            binding.etDuration.setOnClickListener { onDurationFieldClicked(bindingAdapterPosition) }

            binding.btnFrequencyPlus.setOnClickListener {
                val updated = step.copy(frequencyHz = step.frequencyHz + 1)
                onStepChanged(bindingAdapterPosition, updated)
            }
            binding.btnFrequencyMinus.setOnClickListener {
                val updated = step.copy(frequencyHz = (step.frequencyHz - 1).coerceAtLeast(0))
                onStepChanged(bindingAdapterPosition, updated)
            }
            binding.btnIntensityPlus.setOnClickListener {
                val updated = step.copy(intensity01V = step.intensity01V + 1)
                onStepChanged(bindingAdapterPosition, updated)
            }
            binding.btnIntensityMinus.setOnClickListener {
                val updated = step.copy(intensity01V = (step.intensity01V - 1).coerceAtLeast(0))
                onStepChanged(bindingAdapterPosition, updated)
            }
            binding.btnDurationPlus.setOnClickListener {
                val updated = step.copy(durationSec = step.durationSec + 1)
                onStepChanged(bindingAdapterPosition, updated)
            }
            binding.btnDurationMinus.setOnClickListener {
                val updated = step.copy(durationSec = (step.durationSec - 1).coerceAtLeast(1))
                onStepChanged(bindingAdapterPosition, updated)
            }

            val isPlaying = bindingAdapterPosition == playingStepIndex
            binding.btnPlayOrStop.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            binding.btnPlayOrStop.setOnClickListener { onPlayClicked(bindingAdapterPosition) }

            binding.btnMoveUp.isEnabled = position > 0
            binding.btnMoveDown.isEnabled = position < lastIndex
            binding.btnMoveUp.setOnClickListener { onMoveUpClicked(bindingAdapterPosition) }
            binding.btnMoveDown.setOnClickListener { onMoveDownClicked(bindingAdapterPosition) }
            binding.btnDelete.setOnClickListener { onDeleteClicked(bindingAdapterPosition) }
        }

        private fun setText(
            editText: android.widget.EditText,
            value: String,
            onChange: (String) -> Unit
        ) {
            val watcher = when (editText.id) {
                binding.etFrequency.id -> freqWatcher
                binding.etIntensity.id -> intensityWatcher
                else -> durationWatcher
            }
            watcher?.let { editText.removeTextChangedListener(it) }
            if (editText.text?.toString() != value) {
                editText.setText(value)
                editText.setSelection(value.length)
            }
            val newWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onChange(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            editText.addTextChangedListener(newWatcher)
            when (editText.id) {
                binding.etFrequency.id -> freqWatcher = newWatcher
                binding.etIntensity.id -> intensityWatcher = newWatcher
                else -> durationWatcher = newWatcher
            }
        }
    }
}
