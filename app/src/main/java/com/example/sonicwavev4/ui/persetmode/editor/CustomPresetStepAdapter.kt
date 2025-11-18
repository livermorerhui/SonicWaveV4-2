package com.example.sonicwavev4.ui.persetmode.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
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
    private val onItemMoved: (from: Int, to: Int) -> Unit,
    private val onFieldSelected: (Int, FieldType) -> Unit
) : RecyclerView.Adapter<CustomPresetStepAdapter.StepViewHolder>() {

    private val items: MutableList<CustomPresetStep> = mutableListOf()
    private var selectedField: SelectedField? = null

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

    fun setSelectedField(selected: SelectedField?) {
        val previous = selectedField
        selectedField = selected
        // Refresh only affected rows to avoid forcing a second tap.
        previous?.stepIndex?.takeIf { it in items.indices }?.let { notifyItemChanged(it) }
        selected?.stepIndex?.takeIf { it in items.indices }?.let {
            if (it != previous?.stepIndex) notifyItemChanged(it)
        }
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
            binding.etFrequency.isClickable = true
            binding.etIntensity.isClickable = true
            binding.etDuration.isClickable = true
            binding.etFrequency.isLongClickable = false
            binding.etIntensity.isLongClickable = false
            binding.etDuration.isLongClickable = false
            binding.etFrequency.setTextIsSelectable(false)
            binding.etIntensity.setTextIsSelectable(false)
            binding.etDuration.setTextIsSelectable(false)
            binding.etFrequency.setOnClickListener {
                onFieldSelected(bindingAdapterPosition, FieldType.FREQUENCY)
                onFrequencyFieldClicked(bindingAdapterPosition)
            }
            binding.etIntensity.setOnClickListener {
                onFieldSelected(bindingAdapterPosition, FieldType.INTENSITY)
                onIntensityFieldClicked(bindingAdapterPosition)
            }
            binding.etDuration.setOnClickListener {
                onFieldSelected(bindingAdapterPosition, FieldType.DURATION)
                onDurationFieldClicked(bindingAdapterPosition)
            }

            val isFreqSelected = selectedField?.stepIndex == bindingAdapterPosition && selectedField?.fieldType == FieldType.FREQUENCY
            val isIntSelected = selectedField?.stepIndex == bindingAdapterPosition && selectedField?.fieldType == FieldType.INTENSITY
            val isDurSelected = selectedField?.stepIndex == bindingAdapterPosition && selectedField?.fieldType == FieldType.DURATION
            binding.etFrequency.setBackgroundResource(if (isFreqSelected) R.drawable.rounded_display_highlight else R.drawable.rounded_display)
            binding.etIntensity.setBackgroundResource(if (isIntSelected) R.drawable.rounded_display_highlight else R.drawable.rounded_display)
            binding.etDuration.setBackgroundResource(if (isDurSelected) R.drawable.rounded_display_highlight else R.drawable.rounded_display)

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
                val updated = step.copy(durationSec = (step.durationSec - 1).coerceAtLeast(0))
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
