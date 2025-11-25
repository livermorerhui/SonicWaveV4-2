package com.example.sonicwavev4.ui.persetmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import com.example.sonicwavev4.data.custompreset.CustomPresetRepositoryImpl
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentPersetmodeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.custompreset.CustomPresetEditorFragment
import com.example.sonicwavev4.ui.persetmode.PresetCategory.BUILT_IN
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PersetmodeFragment : Fragment() {

    private var _binding: FragmentPersetmodeBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: LoginViewModel by activityViewModels()
    private val customerViewModel: CustomerViewModel by activityViewModels()

    private val viewModel: PersetmodeViewModel by activityViewModels {
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

    private val modeButtons: List<Button> by lazy {
        listOf(
            binding.btnWholeBody,
            binding.btnVisionMode,
            binding.btnUpperHead,
            binding.btnChestAbdomen,
            binding.btnLowerLimb
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersetmodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
        observeUserSession()
        observeSelectedCustomerContext()
        observeEditButtonVisibility()
        viewModel.selectMode(0)
    }

    override fun onResume() {
        super.onResume()
        viewModel.prepareHardwareForEntry()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopIfRunning()
    }

    private fun setupListeners() {
        modeButtons.forEachIndexed { index, button ->
            button.setOnClickListener { viewModel.selectMode(index) }
        }
        binding.btnStartStop.setOnClickListener {
            val customer = customerViewModel.selectedCustomer.value
            viewModel.handleSessionIntent(VibrationSessionIntent.ToggleStartStop(customer))
        }
        binding.btnEditPreset.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val presetId = viewModel.cloneCurrentExpertToCustomPresetIfNeeded()
                if (presetId != null) {
                    openCustomPresetEditorFor(presetId)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.uiState, viewModel.sessionUiState) { preset, session ->
                        preset to session
                    }.collect { (preset, session) ->
                        renderState(preset, session)
                    }
                }
                launch {
                    viewModel.events.collect { handleEvent(it) }
                }
            }
        }
    }

    private fun observeUserSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    authViewModel.uiState.collect { state ->
                        val isTest = state.accountType?.equals("test", ignoreCase = true) == true
                        viewModel.updateAccountAccess(isTest)
                        viewModel.setSessionActive(state.isLoggedIn)
                    }
                }
            }
        }
    }

    private fun observeSelectedCustomerContext() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                customerViewModel.selectedCustomer.collect { viewModel.setActiveCustomer(it) }
            }
        }
    }

    private fun renderState(state: PresetModeUiState, sessionState: VibrationSessionUiState) {
        binding.btnStartStop.text =
            if (sessionState.isRunning) getString(R.string.button_stop) else getString(R.string.button_start)
        binding.btnStartStop.isEnabled = sessionState.startButtonEnabled

        val selectedTextColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        val defaultTextColor = ContextCompat.getColor(requireContext(), R.color.preset_mode_button_text_default)
        val disabledTextColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        modeButtons.forEachIndexed { index, button ->
            val selected = index == state.selectedModeIndex
            button.background = ContextCompat.getDrawable(
                requireContext(),
                if (selected) R.drawable.bg_preset_mode_button_selected else R.drawable.bg_preset_mode_button_default
            )
            ViewCompat.setBackgroundTintList(button, null)
            button.isEnabled = state.modeButtonsEnabled
            val targetTextColor = when {
                !state.modeButtonsEnabled -> disabledTextColor
                selected -> selectedTextColor
                else -> defaultTextColor
            }
            button.setTextColor(targetTextColor)
        }

        binding.imgHighlightWhole.isVisible = state.selectedModeIndex == 0
        binding.imgHighlightVision.isVisible = state.selectedModeIndex == 1
        binding.imgHighlightUpperHead.isVisible = state.selectedModeIndex == 2
        binding.imgHighlightChestAbdomen.isVisible = state.selectedModeIndex == 3
        binding.imgHighlightLowerLimb.isVisible = state.selectedModeIndex == 4

        binding.tvFrequencyValue.text = sessionState.frequencyDisplay
        binding.tvIntensityValue.text = sessionState.intensityDisplay
        binding.tvRemainingValue.text = sessionState.timeDisplay
    }

    private fun observeEditButtonVisibility() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                combine(
                    customerViewModel.selectedCustomer,
                    viewModel.uiState
                ) { selectedCustomer, state ->
                    val inCustomerDetail = selectedCustomer != null
                    val isExpertMode = state.category == BUILT_IN
                    inCustomerDetail && isExpertMode
                }.collect { shouldShow ->
                    binding.btnEditPreset.isVisible = shouldShow
                }
            }
        }
    }

    private fun openCustomPresetEditorFor(presetId: String) {
        val dialog = CustomPresetEditorFragment.newInstance(presetId)
        dialog.show(parentFragmentManager, CustomPresetEditorFragment.TAG)
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

}
