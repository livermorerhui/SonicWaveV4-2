package com.example.sonicwavev4.ui.persetmode.editor

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.databinding.ItemCustomPresetStepBinding
import com.example.sonicwavev4.databinding.ItemCustomPresetStepEditBinding

data class StepDisplay(
    val id: String,
    val title: String,
    val detail: String,
    val isEditing: Boolean,
    val isNew: Boolean
)

class CustomPresetStepAdapter(
    private val onMoveUp: (String) -> Unit,
    private val onMoveDown: (String) -> Unit,
    private val onEdit: (String) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onInputChanged: (String?, String?, String?) -> Unit,
    private val onSave: () -> Unit,
    private val onCancel: () -> Unit,
    private val onScrolledToBottomRequest: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items: MutableList<StepDisplay> = mutableListOf()
    private var frequencyInput: String = ""
    private var intensityInput: String = ""
    private var durationInput: String = ""

    fun submit(
        newItems: List<StepDisplay>,
        frequencyInput: String,
        intensityInput: String,
        durationInput: String
    ) {
        items.clear()
        items.addAll(newItems)
        this.frequencyInput = frequencyInput
        this.intensityInput = intensityInput
        this.durationInput = durationInput
        notifyDataSetChanged()
        if (newItems.any { it.isEditing && it.isNew }) {
            onScrolledToBottomRequest()
        }
    }

    override fun getItemViewType(position: Int): Int =
        if (items[position].isEditing) VIEW_TYPE_EDIT else VIEW_TYPE_VIEW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_EDIT) {
            val binding = ItemCustomPresetStepEditBinding.inflate(inflater, parent, false)
            EditViewHolder(binding)
        } else {
            val binding = ItemCustomPresetStepBinding.inflate(inflater, parent, false)
            ViewViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ViewViewHolder -> holder.bind(item, position, items.lastIndex)
            is EditViewHolder -> holder.bind(item, frequencyInput, intensityInput, durationInput)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewViewHolder(
        private val binding: ItemCustomPresetStepBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StepDisplay, position: Int, lastIndex: Int) {
            binding.tvStepTitle.text = item.title
            binding.tvStepDetail.text = item.detail
            binding.btnMoveUp.isEnabled = position > 0
            binding.btnMoveDown.isEnabled = position < lastIndex

            binding.btnMoveUp.setOnClickListener { onMoveUp(item.id) }
            binding.btnMoveDown.setOnClickListener { onMoveDown(item.id) }
            binding.btnEdit.setOnClickListener { onEdit(item.id) }
            binding.btnDelete.setOnClickListener { onDelete(item.id) }
        }
    }

    inner class EditViewHolder(
        private val binding: ItemCustomPresetStepEditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var freqWatcher: TextWatcher? = null
        private var intensityWatcher: TextWatcher? = null
        private var durationWatcher: TextWatcher? = null

        fun bind(item: StepDisplay, freq: String, intensity: String, duration: String) {
            binding.tvStepTitle.text = item.title

            setText(binding.etFrequency, freq) { new -> onInputChanged(new, null, null) }
            setText(binding.etIntensity, intensity) { new -> onInputChanged(null, new, null) }
            setText(binding.etDuration, duration) { new -> onInputChanged(null, null, new) }

            binding.btnSaveStep.setOnClickListener { onSave() }
            binding.btnCancelStep.setOnClickListener { onCancel() }
        }

        private fun setText(
            editText: com.google.android.material.textfield.TextInputEditText,
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

    companion object {
        private const val VIEW_TYPE_VIEW = 0
        private const val VIEW_TYPE_EDIT = 1
    }
}
