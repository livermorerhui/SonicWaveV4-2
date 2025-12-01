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
import com.example.sonicwavev4.SoftReduceTouchHost
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: LoginViewModel by activityViewModels()
    private val customerViewModel: CustomerViewModel by activityViewModels()

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

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window?.decorView?.setOnTouchListener(null)
        requireActivity().findViewById<View?>(android.R.id.content)?.setOnTouchListener(null)
        (requireActivity() as? SoftReduceTouchHost)?.setSoftReduceTouchListener(null)
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopSession()
    }

    override fun onResume() {
        super.onResume()
        viewModel.prepareHardwareForEntry()
        (activity as? SoftReduceTouchHost)?.setSoftReduceTouchListener { ev ->
            handleSoftReduceTouch(ev)
        }
    }

    override fun onPause() {
        (activity as? SoftReduceTouchHost)?.setSoftReduceTouchListener(null)
        super.onPause()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect { renderState(it) }
            }
        }
    }

    private fun renderState(state: VibrationSessionUiState) {
        val startButton = binding.btnStartStop
        val stopButton = binding.btnStop
        val recoverButton = binding.btnSoftResumeInline

        binding.tvTimeValue.isEnabled = !state.isRunning

        binding.tvFrequencyValue.text = state.frequencyDisplay
        binding.tvIntensityValue.text = state.intensityDisplay
        binding.tvTimeValue.text = state.timeDisplay

        updateHighlights(state.activeInputType)

        val expandedPanel = binding.layoutSoftPanelExpanded
        val collapsedPanel = binding.layoutSoftPanelCollapsed

        expandedPanel?.visibility = View.GONE
        collapsedPanel?.visibility = View.GONE

        recoverButton?.visibility =
            if (state.softReductionActive || (state.isRunning && state.intensityValue <= 20)) View.VISIBLE else View.GONE

        val startLabel = when {
            state.isPaused -> "继续"
            state.isRunning -> "暂停"
            else -> getString(R.string.button_start)
        }
        startButton?.text = startLabel

        val startBgRes = when {
            state.isPaused -> R.drawable.bg_jixu_green // “继续”状态：绿色
            state.isRunning -> R.drawable.bg_button_yellow // “暂停”状态：黄色
            else -> R.drawable.bg_home_start_button // 未运行：绿色
        }
        startButton?.backgroundTintList = null
        startButton?.setBackgroundResource(startBgRes)
        startButton?.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))

        val stopVisible = state.isRunning || state.isPaused
        stopButton?.visibility = if (stopVisible) View.VISIBLE else View.GONE
        stopButton?.isEnabled = stopVisible

        startButton?.isEnabled = if (state.isPaused) true else state.startButtonEnabled || state.isRunning
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
                    authViewModel.uiState.collect { state ->
                        val isTestAccount = state.accountType?.equals("test", ignoreCase = true) == true
                        viewModel.updateAccountAccess(isTestAccount)
                        viewModel.setSessionActive(state.isLoggedIn)
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
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(1)) }
        )
        configureAdjustButton(
            button = binding.btnFrequencyDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(-1)) }
        )
        configureAdjustButton(
            button = binding.btnIntensityUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(1)) }
        )
        configureAdjustButton(
            button = binding.btnIntensityDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(-1)) }
        )
        binding.btnTimeUp.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.AdjustTime(1))
            viewModel.playTapSound()
        }
        binding.btnTimeDown.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.AdjustTime(-1))
            viewModel.playTapSound()
        }

        binding.btnStartStop?.setOnClickListener {
            val selectedCustomer = customerViewModel.selectedCustomer.value
            viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
            viewModel.playTapSound()
        }
        binding.btnStop?.setOnClickListener {
            viewModel.stopSession()
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

        binding.btnSoftResumeInline?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SoftReductionResumeClicked)
            viewModel.playTapSound()
        }

        binding.btnSoftStopExpanded?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SoftReductionStopClicked)
            viewModel.playTapSound()
        }
        binding.btnSoftResumeExpanded?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SoftReductionResumeClicked)
            viewModel.playTapSound()
        }
        binding.btnSoftStopCollapsed?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SoftReductionStopClicked)
            viewModel.playTapSound()
        }
        binding.btnSoftResumeCollapsed?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.SoftReductionResumeClicked)
            viewModel.playTapSound()
        }
    }

    private fun configureAdjustButton(
        button: View,
        onSingleTap: () -> Unit
    ) {
        button.setOnClickListener {
            onSingleTap()
            viewModel.playTapSound()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun handleSoftReduceTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) {
            return false
        }
        val state = viewModel.uiState.value ?: return false
        if (!state.isRunning) {
            return false
        }

        if (!state.softReductionActive) {
            viewModel.handleIntent(VibrationSessionIntent.SoftReduceFromTap)
        }

        return false
    }
}
