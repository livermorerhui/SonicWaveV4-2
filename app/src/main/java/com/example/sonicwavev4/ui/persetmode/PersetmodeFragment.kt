package com.example.sonicwavev4.ui.persetmode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentPersetmodeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.user.UserViewModel
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PersetmodeFragment : Fragment() {

    private var _binding: FragmentPersetmodeBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    private val viewModel: PersetmodeViewModel by viewModels {
        val application = requireActivity().application
        val hardwareRepository = HomeHardwareRepository.getInstance(application)
        val sessionRepository = HomeSessionRepository(
            SessionManager(application.applicationContext),
            RetrofitClient.api
        )
        PersetmodeViewModelFactory(application, hardwareRepository, sessionRepository)
    }

    private val modeButtons: List<Button> by lazy {
        listOf(
            binding.btnWholeBody,
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
            val customer = userViewModel.selectedCustomer.value
            viewModel.toggleStartStop(customer)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { renderState(it) }
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
                    userViewModel.accountType.collect { type ->
                        val isTest = type?.equals("test", ignoreCase = true) == true
                        viewModel.updateAccountAccess(isTest)
                    }
                }
                launch {
                    userViewModel.isLoggedIn.collect { loggedIn ->
                        viewModel.setSessionActive(loggedIn)
                    }
                }
            }
        }
    }

    private fun renderState(state: PresetModeUiState) {
        binding.btnStartStop.text = if (state.isRunning) getString(R.string.button_stop) else getString(R.string.button_start)
        binding.btnStartStop.isEnabled = state.isStartEnabled

        modeButtons.forEachIndexed { index, button ->
            val selected = index == state.selectedModeIndex
            button.isSelected = selected
            button.isEnabled = if (selected) true else state.modeButtonsEnabled
        }

        binding.imgHighlightWhole.isVisible = state.selectedModeIndex == 0
        binding.imgHighlightUpperHead.isVisible = state.selectedModeIndex == 1
        binding.imgHighlightChestAbdomen.isVisible = state.selectedModeIndex == 2
        binding.imgHighlightLowerLimb.isVisible = state.selectedModeIndex == 3

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
