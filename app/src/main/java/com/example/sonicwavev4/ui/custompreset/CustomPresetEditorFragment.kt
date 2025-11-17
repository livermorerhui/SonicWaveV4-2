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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sonicwavev4.R
import com.example.sonicwavev4.data.custompreset.CustomPresetRepositoryImpl
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.databinding.FragmentCustomPresetEditorBinding
import com.example.sonicwavev4.ui.common.NumericKeypadDialogFragment
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetEditorViewModel
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetEditorViewModelFactory
import com.example.sonicwavev4.ui.persetmode.editor.CustomPresetStepAdapter
import com.example.sonicwavev4.ui.persetmode.editor.EditorEvent
import com.example.sonicwavev4.ui.user.UserViewModel
import com.example.sonicwavev4.ui.persetmode.editor.hasUnsavedChanges
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CustomPresetEditorFragment : DialogFragment() {

    private var _binding: FragmentCustomPresetEditorBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    private val editorViewModel: CustomPresetEditorViewModel by viewModels {
        val application = requireActivity().application
        val repository = CustomPresetRepositoryImpl.getInstance(application)
        val customerId = userViewModel.selectedCustomer.value?.id?.toLong()
        val hardwareRepo = HomeHardwareRepository.getInstance(application)
        CustomPresetEditorViewModelFactory(repository, customerId, hardwareRepo)
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
        editorViewModel.stopCurrentStepPlayback()
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
            onStepChanged = { index, step -> editorViewModel.updateStep(index, step) },
            onPlayClicked = { editorViewModel.onPlayStepClicked(it) },
            onMoveUpClicked = { editorViewModel.moveStepUp(it) },
            onMoveDownClicked = { editorViewModel.moveStepDown(it) },
            onDeleteClicked = { editorViewModel.deleteStep(editorViewModel.uiState.value.steps.getOrNull(it)?.id ?: return@CustomPresetStepAdapter) },
            onFrequencyFieldClicked = { showFrequencyKeypad(it) },
            onIntensityFieldClicked = { showIntensityKeypad(it) },
            onDurationFieldClicked = { showDurationKeypad(it) },
            onItemMoved = { from, to -> editorViewModel.onStepReordered(from, to) }
        )
        binding.listSteps.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = stepAdapter
        }
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                stepAdapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no-op
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.listSteps)
    }

    private fun setupInputs() {
        binding.etName.addTextChangedListener { editorViewModel.setName(it?.toString().orEmpty()) }
        binding.btnAddStep.setOnClickListener {
            editorViewModel.addStepInline()
            scrollToBottom()
        }
        binding.btnSavePreset.setOnClickListener { editorViewModel.savePreset() }
        binding.btnCancel.setOnClickListener { handleCancel() }
        binding.viewScrim.setOnClickListener { /* ignore scrim clicks to avoid accidental dismiss */ }
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
        val sortedSteps = state.steps.sortedBy { it.order }
        stepAdapter.submitList(sortedSteps)
        stepAdapter.playingStepIndex = state.playingStepIndex
        binding.tvStepsEmpty.isVisible = sortedSteps.isEmpty()
        binding.btnSavePreset.isEnabled = state.canSave && !state.isSaving
        binding.btnCancel.isEnabled = !state.isSaving
    }

    private fun handleCancel() {
        val state = editorViewModel.uiState.value
        if (!state.hasUnsavedChanges()) {
            dismiss()
            return
        }
        confirmExit()
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

    private fun scrollToBottom() {
        binding.listSteps.post {
            binding.listSteps.smoothScrollToPosition((binding.listSteps.adapter?.itemCount ?: 1) - 1)
        }
    }

    private fun showFrequencyKeypad(index: Int) {
        val step = editorViewModel.uiState.value.steps.getOrNull(index) ?: return
        val currentPlaying = editorViewModel.uiState.value.playingStepIndex
        if (currentPlaying != null && currentPlaying != index) {
            editorViewModel.stopCurrentStepPlayback()
        }
        val requestKey = "freq_step_$index"
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, bundle ->
            val value = bundle.getInt(NumericKeypadDialogFragment.RESULT_VALUE, step.frequencyHz)
            editorViewModel.updateStepFrequency(index, value)
        }
        NumericKeypadDialogFragment.newInstance(
            initialValue = step.frequencyHz,
            minValue = com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MIN_FREQUENCY_HZ,
            maxValue = if (com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MAX_FREQUENCY_HZ > 0)
                com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MAX_FREQUENCY_HZ else Int.MAX_VALUE,
            requestKey = requestKey
        ).show(parentFragmentManager, requestKey)
    }

    private fun showIntensityKeypad(index: Int) {
        val step = editorViewModel.uiState.value.steps.getOrNull(index) ?: return
        val currentPlaying = editorViewModel.uiState.value.playingStepIndex
        if (currentPlaying != null && currentPlaying != index) {
            editorViewModel.stopCurrentStepPlayback()
        }
        val requestKey = "intensity_step_$index"
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, bundle ->
            val value = bundle.getInt(NumericKeypadDialogFragment.RESULT_VALUE, step.intensity01V)
            editorViewModel.updateStepIntensity(index, value)
        }
        NumericKeypadDialogFragment.newInstance(
            initialValue = step.intensity01V,
            minValue = com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MIN_INTENSITY_01V,
            maxValue = com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MAX_INTENSITY_01V,
            requestKey = requestKey
        ).show(parentFragmentManager, requestKey)
    }

    private fun showDurationKeypad(index: Int) {
        val step = editorViewModel.uiState.value.steps.getOrNull(index) ?: return
        val requestKey = "duration_step_$index"
        parentFragmentManager.setFragmentResultListener(requestKey, viewLifecycleOwner) { _, bundle ->
            val value = bundle.getInt(NumericKeypadDialogFragment.RESULT_VALUE, step.durationSec)
            editorViewModel.updateStepDuration(index, value)
        }
        NumericKeypadDialogFragment.newInstance(
            initialValue = step.durationSec,
            minValue = com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MIN_DURATION_SEC,
            maxValue = if (com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MAX_DURATION_SEC > 0)
                com.example.sonicwavev4.data.custompreset.CustomPresetConstraints.MAX_DURATION_SEC else Int.MAX_VALUE,
            requestKey = requestKey
        ).show(parentFragmentManager, requestKey)
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
