package com.example.sonicwavev4.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.user.UserViewModel
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserViewModel by activityViewModels()

    private val viewModel: HomeViewModel by viewModels {
        val application = requireActivity().application
        val hardwareRepository = HomeHardwareRepository.getInstance(application)
        val sessionRepository = HomeSessionRepository(
            SessionManager(application.applicationContext),
            RetrofitClient.api
        )
        HomeViewModelFactory(application, hardwareRepository, sessionRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        observeUiState()
        observeEvents()
        observeAccountPrivileges()
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
        viewModel.stopPlaybackIfRunning()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }
    }

    private fun renderState(state: VibrationSessionUiState) {
        binding.btnStartStop.text = if (state.isRunning) getString(R.string.button_stop) else getString(R.string.button_start)
        binding.btnStartStop.isEnabled = state.startButtonEnabled
        binding.tvTimeValue.isEnabled = !state.isRunning

        binding.tvFrequencyValue.text = state.frequencyDisplay
        binding.tvIntensityValue.text = state.intensityDisplay
        binding.tvTimeValue.text = state.timeDisplay

        updateHighlights(state.activeInputType)
    }

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is UiEvent.ShowToast -> showToast(event.message)
                        is UiEvent.ShowError -> showToast(event.throwable.message ?: "Unexpected error")
                    }
                }
            }
        }
    }

    private fun observeAccountPrivileges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    userViewModel.accountType.collect { accountType ->
                        val isTestAccount = accountType.equals("test", ignoreCase = true)
                        viewModel.updateAccountAccess(isTestAccount)
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

    private fun updateHighlights(activeType: String?) {
        val defaultBg = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_display)
        val highlightBg = ContextCompat.getDrawable(requireContext(), R.drawable.rounded_display_highlight)
        binding.tvFrequencyValue.background = if (activeType == "frequency") highlightBg else defaultBg
        binding.tvIntensityValue.background = if (activeType == "intensity") highlightBg else defaultBg
        binding.tvTimeValue.background = if (activeType == "time") highlightBg else defaultBg
    }

    // --- 点击事件监听器 ---
    private fun setupClickListeners() {
        binding.tvFrequencyValue.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SelectInput("frequency"))
            viewModel.playTapSound()
        }
        binding.tvIntensityValue.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SelectInput("intensity"))
            viewModel.playTapSound()
        }
        binding.tvTimeValue.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SelectInput("time"))
            viewModel.playTapSound()
        }

        configureAdjustButton(
            button = binding.btnFrequencyUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(1)) },
            onAutoRepeat = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(10)) }
        )
        configureAdjustButton(
            button = binding.btnFrequencyDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(-1)) },
            onAutoRepeat = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(-10)) }
        )
        configureAdjustButton(
            button = binding.btnIntensityUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(1)) },
            onAutoRepeat = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(10)) }
        )
        configureAdjustButton(
            button = binding.btnIntensityDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(-1)) },
            onAutoRepeat = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(-10)) }
        )
        binding.btnTimeUp.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.AdjustTime(1))
            viewModel.playTapSound()
        }
        binding.btnTimeDown.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.AdjustTime(-1))
            viewModel.playTapSound()
        }

        binding.btnStartStop.setOnClickListener {
            val selectedCustomer = userViewModel.selectedCustomer.value
            viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
            viewModel.playTapSound()
        }
        binding.btnClear.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.ClearAll)
            viewModel.playTapSound()
        }

        val numericClickListener = View.OnClickListener { view ->
            if (viewModel.currentInputType.value.isNullOrEmpty()) return@OnClickListener
            viewModel.handleIntent(VibrationSessionIntent.AppendDigit((view as Button).text.toString()))
            viewModel.playTapSound()
        }
        listOf(binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3, binding.btnKey4,
            binding.btnKey5, binding.btnKey6, binding.btnKey7, binding.btnKey8, binding.btnKey9)
            .forEach { it.setOnClickListener(numericClickListener) }

        binding.btnKeyClear.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.DeleteDigit)
            viewModel.playTapSound()
        }
        binding.btnKeyClear.setOnLongClickListener {
            viewModel.handleIntent(VibrationSessionIntent.ClearCurrent)
            viewModel.playTapSound()
            true
        }

        binding.btnKeyEnter.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.CommitAndCycle)
            viewModel.playTapSound()
        }

    }

    private fun configureAdjustButton(
        button: View,
        onSingleTap: () -> Unit,
        onAutoRepeat: () -> Unit
    ) {
        var repeatJob: Job? = null
        var autoModeStarted = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeatJob?.cancel()
                    autoModeStarted = false
                    repeatJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(3000)
                        autoModeStarted = true
                        while (true) {
                            onAutoRepeat()
                            viewModel.playTapSound()
                            delay(1000)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    repeatJob?.cancel()
                    repeatJob = null
                    if (!autoModeStarted) {
                        onSingleTap()
                        viewModel.playTapSound()
                    }
                    autoModeStarted = false
                }
            }
            true
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
