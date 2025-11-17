package com.example.sonicwavev4.ui.custompreset

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.sonicwavev4.R
import com.example.sonicwavev4.data.custompreset.CustomPresetRepositoryImpl
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentCustomPresetBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.persetmode.CustomPresetUiModel
import com.example.sonicwavev4.ui.persetmode.PersetmodeViewModel
import com.example.sonicwavev4.ui.persetmode.PersetmodeViewModelFactory
import com.example.sonicwavev4.ui.persetmode.PresetCategory
import com.example.sonicwavev4.ui.persetmode.custom.CustomPresetAdapter
import com.example.sonicwavev4.ui.user.UserViewModel
import com.example.sonicwavev4.utils.SessionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CustomPresetFragment : Fragment() {

    private var _binding: FragmentCustomPresetBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    private val presetViewModel: PersetmodeViewModel by activityViewModels {
        val application = requireActivity().application
        val hardwareRepository = HomeHardwareRepository.getInstance(application)
        val sessionRepository = HomeSessionRepository(
            SessionManager(application.applicationContext),
            RetrofitClient.api
        )
        val customPresetRepository = CustomPresetRepositoryImpl.getInstance(application)
        PersetmodeViewModelFactory(
            application,
            hardwareRepository,
            sessionRepository,
            customPresetRepository
        )
    }

    private lateinit var presetAdapter: CustomPresetAdapter
    private var hasPendingReorder = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomPresetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPresetList()
        observePresetViewModel()
        observeUserSession()
        observeSelectedCustomer()
        setupEditorResultListener()

        binding.btnNewPreset.setOnClickListener {
            presetViewModel.clearCustomSelection()
            navigateToEditor(null)
        }
        binding.btnStartStop.setOnClickListener {
            val customer = userViewModel.selectedCustomer.value
            presetViewModel.toggleStartStop(customer)
        }
        presetViewModel.enterCustomMode()
    }

    override fun onResume() {
        super.onResume()
        presetViewModel.enterCustomMode()
        presetViewModel.prepareHardwareForEntry()
    }

    override fun onStop() {
        super.onStop()
        presetViewModel.stopIfRunning()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupPresetList() {
        presetAdapter = CustomPresetAdapter(
            onSelect = { selectPreset(it) },
            onEdit = { editPreset(it) },
            onDelete = { confirmDeletePreset(it) }
        )
        binding.listCustomPresets.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = presetAdapter
            setHasFixedSize(false)
        }
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
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
                val moved = presetAdapter.onItemMove(from, to)
                if (moved) {
                    hasPendingReorder = true
                }
                return moved
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (hasPendingReorder) {
                    hasPendingReorder = false
                    presetViewModel.reorderCustomPresets(presetAdapter.currentOrderIds())
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no swipe support
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(binding.listCustomPresets)
    }

    private fun observePresetViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    presetViewModel.uiState.collect { renderPresetState(it) }
                }
                launch {
                    presetViewModel.events.collect { handleEvent(it) }
                }
            }
        }
    }

    private fun observeUserSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    userViewModel.accountType.collect { type ->
                        val isTest = type?.equals("test", ignoreCase = true) == true
                        presetViewModel.updateAccountAccess(isTest)
                    }
                }
                launch {
                    userViewModel.isLoggedIn.collect { loggedIn ->
                        presetViewModel.setSessionActive(loggedIn)
                    }
                }
            }
        }
    }

    private fun observeSelectedCustomer() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                userViewModel.selectedCustomer.collect { presetViewModel.setActiveCustomer(it) }
            }
        }
    }

    private fun setupEditorResultListener() {
        parentFragmentManager.setFragmentResultListener(
            CustomPresetEditorFragment.RESULT_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val presetId = bundle.getString(CustomPresetEditorFragment.RESULT_PRESET_ID)
            if (!presetId.isNullOrEmpty()) {
                presetViewModel.focusOnCustomPreset(presetId)
                showToast("自设模式已保存")
            }
        }
    }

    private fun selectPreset(item: CustomPresetUiModel) {
        presetViewModel.selectCustomPreset(item.id)
    }

    private fun editPreset(item: CustomPresetUiModel) {
        presetViewModel.selectCustomPreset(item.id)
        navigateToEditor(item.id)
    }

    private fun confirmDeletePreset(item: CustomPresetUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除自设模式")
            .setMessage("确定要删除“${item.name}”吗？删除后无法恢复。")
            .setPositiveButton("删除") { _, _ ->
                presetViewModel.deleteCustomPreset(item.id)
                presetViewModel.clearCustomSelection()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun navigateToEditor(presetId: String?) {
        val dialog = CustomPresetEditorFragment.newInstance(presetId)
        dialog.show(parentFragmentManager, CustomPresetEditorFragment.TAG)
    }

    private fun renderPresetState(state: com.example.sonicwavev4.ui.persetmode.PresetModeUiState) {
        if (state.category != PresetCategory.CUSTOM) {
            presetViewModel.enterCustomMode()
        }
        presetAdapter.submitList(state.customPresets)
        binding.tvEmptyList.isVisible = state.customPresets.isEmpty()

        val selected = state.customPresets.firstOrNull { it.isSelected }
        binding.tvSelectedPresetName.text = selected?.name ?: "未选择自设模式"
        binding.tvSelectedSummary.text = selected?.summary ?: "请选择自设模式后再开始"

        binding.btnStartStop.text =
            if (state.isRunning) getString(R.string.button_stop) else getString(R.string.button_start)
        binding.btnStartStop.isEnabled = state.isStartEnabled
        binding.tvFrequencyValue.text = state.frequencyHz?.toString() ?: "--"
        binding.tvIntensityValue.text = state.intensity01V?.toString() ?: "--"
        binding.tvRemainingValue.text = formatAsMMSS(state.remainingSeconds)
    }

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.ShowToast -> showToast(event.message)
            is UiEvent.ShowError -> showToast(event.throwable.message ?: "Unexpected error")
        }
    }

    private fun showToast(message: String) {
        if (!isAdded) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun formatAsMMSS(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
