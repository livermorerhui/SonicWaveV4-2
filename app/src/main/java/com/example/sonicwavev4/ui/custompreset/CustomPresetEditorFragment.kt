package com.example.sonicwavev4.ui.custompreset

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sonicwavev4.R
import com.example.sonicwavev4.data.custompreset.CustomPresetRepositoryImpl
import com.example.sonicwavev4.databinding.FragmentCustomPresetEditorBinding
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetEditorViewModel
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetEditorViewModelFactory
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetStepAdapter
import com.example.sonicwavev4.ui.persetmode.editor.EditorEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CustomPresetEditorFragment : DialogFragment() {

    private var _binding: FragmentCustomPresetEditorBinding? = null
    private val binding get() = _binding!!

    private val editorViewModel: CustomPresetEditorViewModel by viewModels {
        val application = requireActivity().application
        val repository = CustomPresetRepositoryImpl.getInstance(application)
        CustomPresetEditorViewModelFactory(repository)
    }

    private lateinit var stepAdapter: CustomPresetStepAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomPresetEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecycler()
        setupInputs()
        observeViewModel()
        resolveInitialState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
        dialog?.setCanceledOnTouchOutside(false)
    }

    private fun setupRecycler() {
        stepAdapter = CustomPresetStepAdapter(
            onMoveUp = { editorViewModel.moveStepUp(it) },
            onMoveDown = { editorViewModel.moveStepDown(it) },
            onEdit = { editorViewModel.startEditingStep(it) },
            onDelete = { editorViewModel.deleteStep(it) }
        )
        binding.listSteps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stepAdapter
        }
    }

    private fun setupInputs() {
        binding.etName.addTextChangedListener { editorViewModel.setName(it?.toString().orEmpty()) }
        binding.etFrequency.addTextChangedListener { editorViewModel.setFrequencyInput(it?.toString().orEmpty()) }
        binding.etIntensity.addTextChangedListener { editorViewModel.setIntensityInput(it?.toString().orEmpty()) }
        binding.etDuration.addTextChangedListener { editorViewModel.setDurationInput(it?.toString().orEmpty()) }

        binding.btnAddOrUpdateStep.setOnClickListener { editorViewModel.addOrUpdateStep() }
        binding.btnSavePreset.setOnClickListener { editorViewModel.savePreset() }
        binding.btnCancel.setOnClickListener { confirmExit() }
        binding.viewScrim.setOnClickListener { confirmExit() }
        binding.cardContainer.setOnClickListener { /* consume to avoid scrim click */ }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    editorViewModel.uiState.collect { renderState(it) }
                }
                launch {
                    editorViewModel.events.collect { event ->
                        when (event) {
                            is EditorEvent.ShowMessage -> showToast(event.message)
                            is EditorEvent.Saved -> notifyResultAndExit(event.presetId)
                        }
                    }
                }
            }
        }
    }

    private fun resolveInitialState() {
        val presetId = arguments?.getString(ARG_PRESET_ID)
        if (presetId.isNullOrEmpty()) {
            editorViewModel.startNewPreset()
        } else {
            editorViewModel.loadPreset(presetId)
        }
    }

    private fun renderState(state: com.example.sonicwavev4.ui.persetmode.editor.EditorUiState) {
        val title = if (state.isEditingExisting) "编辑自设模式" else "新建自设模式"
        binding.tvEditorTitle.text = title

        updateField(binding.etName, state.name)
        updateField(binding.etFrequency, state.frequencyInput)
        updateField(binding.etIntensity, state.intensityInput)
        updateField(binding.etDuration, state.durationInput)

        stepAdapter.submitList(state.steps)
        binding.tvStepsEmpty.isVisible = state.steps.isEmpty()
        binding.btnAddOrUpdateStep.text = if (state.isEditingStep) "更新步骤" else "添加步骤"
        binding.btnSavePreset.isEnabled = state.canSave && !state.isSaving
        binding.btnAddOrUpdateStep.isEnabled = !state.isSaving
        binding.btnCancel.isEnabled = !state.isSaving
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("取消编辑")
            .setMessage("确定要退出吗？未保存的修改将会丢失。")
            .setPositiveButton("确定") { _, _ -> dismiss() }
            .setNegativeButton("继续编辑", null)
            .show()
    }

    private fun notifyResultAndExit(presetId: String) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(RESULT_PRESET_ID to presetId)
        )
        dismiss()
    }

    private fun showToast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateField(
        view: com.google.android.material.textfield.TextInputEditText,
        newValue: String
    ) {
        if (view.text?.toString() != newValue) {
            view.setText(newValue)
            view.setSelection(newValue.length)
        }
    }

    companion object {
        const val TAG = "CustomPresetEditorDialog"
        const val ARG_PRESET_ID = "presetId"
        const val RESULT_KEY = "customPresetEditorResult"
        const val RESULT_PRESET_ID = "resultPresetId"

        fun newInstance(presetId: String?): CustomPresetEditorFragment =
            CustomPresetEditorFragment().apply {
                arguments = Bundle().apply { putString(ARG_PRESET_ID, presetId) }
            }
    }
}
