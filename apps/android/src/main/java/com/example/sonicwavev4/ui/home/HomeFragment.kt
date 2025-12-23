package com.example.sonicwavev4.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonicwavev4.R
import com.example.sonicwavev4.SoftReduceTouchHost
import com.example.sonicwavev4.core.vibration.VibrationSessionIntent
import com.example.sonicwavev4.core.vibration.VibrationSessionUiState
import com.example.sonicwavev4.data.custompreset.CustomPresetRepositoryImpl
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.common.SessionControlUiMapper
import com.example.sonicwavev4.ui.customer.CustomerViewModel
import com.example.sonicwavev4.ui.login.LoginViewModel
import com.example.sonicwavev4.ui.persetmode.PersetmodeViewModel
import com.example.sonicwavev4.ui.persetmode.PersetmodeViewModelFactory
import com.example.sonicwavev4.ui.persetmode.PresetModeUiState
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private data class PhoneCombinedState(
        val manualSession: VibrationSessionUiState,
        val presetSession: VibrationSessionUiState,
        val presetUi: PresetModeUiState,
        val activeMode: HomeViewModel.ActiveMode
    )

    private companion object {
        const val PHONE_LONG_PRESS_DELAY_MS = 2000L
        const val PHONE_REPEAT_INTERVAL_MS = 150L
    }

    private data class KeypadViews(
        val root: View,
        val flow: View?,
        val numericButtons: List<Button>,
        val clearButton: View?,
        val enterButton: View?,
        val softResumeButton: View?
    )

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val isTablet by lazy { resources.getBoolean(R.bool.is_tablet) }
    private val isPhoneHome: Boolean
        get() = binding.btnWholeBody != null
    private fun hasRightPanel(): Boolean =
        requireActivity().findViewById<View?>(R.id.fragment_right_main) != null
    private fun isPhone(): Boolean = !hasRightPanel()
    // Phone-only: avoid caching view references across view recreation.
    private val expertModeButtons: List<Button>
        get() = listOfNotNull(
            binding.btnWholeBody,
            binding.btnVisionMode,
            binding.btnUpperHead,
            binding.btnChestAbdomen,
            binding.btnLowerLimb
        )
    private var cachedManualActive: Boolean = false
    private var cachedExpertActive: Boolean = false
    private var lastExpertActive: Boolean = false
    private var latestPresetSessionState: VibrationSessionUiState? = null
    private var updatingIntensityScaleFromState = false
    private var keypadViews: KeypadViews? = null
    private var keypadListenersBound = false

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

    private val presetViewModel: PersetmodeViewModel by viewModels {
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPhoneInputs()
        setupClickListeners()
        setupExpertIntensityScaleControls()
        setupExpertRepeatCountControls()
        if (isPhoneHome) {
            presetViewModel.ensureModeButtonsEnabled()
            observePhoneState()
            observePresetState()
        } else {
            observeUiState()
        }
        observeEvents()
        observeAccountPrivileges()
        observeSelectedCustomer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().window?.decorView?.setOnTouchListener(null)
        requireActivity().findViewById<View?>(android.R.id.content)?.setOnTouchListener(null)
        (requireActivity() as? SoftReduceTouchHost)?.setSoftReduceTouchListener(null)
        _binding = null
        keypadViews = null
        keypadListenersBound = false
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopSession()
        if (isPhoneHome) {
            presetViewModel.stopIfRunning()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.prepareHardwareForEntry()
        if (!isPhoneHome && isTablet) {
            (activity as? SoftReduceTouchHost)?.setSoftReduceTouchListener { ev ->
                handleSoftReduceTouch(ev)
            }
        } else {
            (activity as? SoftReduceTouchHost)?.setSoftReduceTouchListener(null)
        }
    }

    override fun onPause() {
        (activity as? SoftReduceTouchHost)?.setSoftReduceTouchListener(null)
        super.onPause()
    }

    private fun observeUiState() {
        if (isPhoneHome) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.uiState.collect {
                    renderState(it)
                }
            }
        }
    }

    private fun renderState(state: VibrationSessionUiState) {
        if (isTablet) {
            renderTabletState(state)
        } else {
            renderPhoneState(state)
        }
    }

    private fun renderTabletState(state: VibrationSessionUiState) {
        val startButton = binding.btnStartStop
        val stopButton = binding.btnStop
        val recoverButton = keypadViews?.softResumeButton

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

        applyStartStopState(state, startButton, stopButton, hideStop = false)
    }

    private fun renderPhoneState(state: VibrationSessionUiState) {
        val startButton = binding.btnStartStop
        val stopButton = binding.btnStop
        val recoverButton = keypadViews?.softResumeButton

        val activeMode = viewModel.activeMode.value
        val displayState = if (isPhoneHome && activeMode == HomeViewModel.ActiveMode.EXPERT) {
            latestPresetSessionState ?: state
        } else {
            state
        }

        binding.tvFrequencyValue.text = displayState.frequencyDisplay
        binding.tvIntensityValue.text = displayState.intensityDisplay
        binding.tvTimeValue.text = displayState.timeDisplay

        val runningOrPaused = displayState.isRunning || displayState.isPaused
        binding.tvTimeValue.isEnabled = !runningOrPaused

        updateHighlights(displayState.activeInputType)

        recoverButton?.visibility = View.GONE
        binding.layoutSoftPanelExpanded?.visibility = View.GONE
        binding.layoutSoftPanelCollapsed?.visibility = View.GONE

        val activeSessionState = if (isPhoneHome && activeMode == HomeViewModel.ActiveMode.EXPERT) {
            latestPresetSessionState ?: state
        } else {
            state
        }

        applyStartStopState(activeSessionState, startButton, stopButton, hideStop = false)
    }

    private fun renderPresetButtons(
        presetState: PresetModeUiState,
        buttonsEnabled: Boolean,
        activeMode: HomeViewModel.ActiveMode
    ) {
        if (!isPhoneHome) return
        val selectedTextColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        val defaultTextColor = ContextCompat.getColor(requireContext(), R.color.preset_mode_button_text_default)
        val disabledTextColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        expertModeButtons.forEachIndexed { index, button ->
            val selected = activeMode == HomeViewModel.ActiveMode.EXPERT && index == presetState.selectedModeIndex
            val targetTextColor = when {
                !buttonsEnabled -> disabledTextColor
                selected -> selectedTextColor
                else -> defaultTextColor
            }
            button.isEnabled = buttonsEnabled
            button.isSelected = selected
            button.backgroundTintList = null
            button.setTextColor(targetTextColor)
        }
    }

    private fun applyStartStopState(
        state: VibrationSessionUiState,
        startButton: Button?,
        stopButton: View?,
        hideStop: Boolean
    ) {
        val startUi = SessionControlUiMapper.primaryButtonUi(state)
        startButton?.setText(startUi.labelRes)
        startButton?.backgroundTintList = null
        startButton?.setBackgroundResource(startUi.backgroundRes)
        startButton?.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))

        val stopVisible = !hideStop && (state.isRunning || state.isPaused)
        stopButton?.visibility = if (stopVisible) View.VISIBLE else View.GONE
        stopButton?.isEnabled = stopVisible

        startButton?.isEnabled = if (state.isPaused) true else state.startButtonEnabled || state.isRunning
    }

    private fun observePhoneState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                combine(
                    viewModel.uiState,
                    presetViewModel.sessionUiState,
                    presetViewModel.uiState,
                    viewModel.activeMode
                ) { manualSession, presetSession, presetUi, activeMode ->
                    PhoneCombinedState(manualSession, presetSession, presetUi, activeMode)
                }.collect { (manualSession, presetSession, presetUi, activeMode) ->
                    val manualActive = manualSession.isRunning || manualSession.isPaused
                    val expertActive = presetSession.isRunning || presetSession.isPaused

                    cachedManualActive = manualActive
                    cachedExpertActive = expertActive
                    latestPresetSessionState = presetSession

                    val displayState = if (isPhoneKeypadVisible()) {
                        manualSession
                    } else {
                        when {
                            expertActive -> presetSession
                            manualActive -> manualSession
                            activeMode == HomeViewModel.ActiveMode.EXPERT -> presetSession
                            else -> manualSession
                        }
                    }

                    val runningOrPaused = displayState.isRunning || displayState.isPaused

                    binding.tvFrequencyValue.text = displayState.frequencyDisplay
                    binding.tvIntensityValue.text = displayState.intensityDisplay
                    binding.tvTimeValue.text = displayState.timeDisplay
                    binding.tvTimeValue.isEnabled = !runningOrPaused
                    updateHighlights(displayState.activeInputType)

                    val expertButtonsEnabled = presetUi.modeButtonsEnabled && !manualActive
                    val intensityScaleEnabled = !manualActive
                    renderPresetButtons(presetUi, expertButtonsEnabled, activeMode)
                    updateIntensityScaleUi(presetUi.intensityScalePct, intensityScaleEnabled)
                    updateRepeatCountUi(presetUi.repeatCount, expertButtonsEnabled)

                    val manualControlsEnabled = !expertActive
                    binding.tvFrequencyValue.isEnabled = manualControlsEnabled
                    binding.tvIntensityValue.isEnabled = manualControlsEnabled
                    binding.tvTimeValue.isEnabled = manualControlsEnabled && !manualSession.isRunning
                    binding.btnFrequencyDown?.isEnabled = manualControlsEnabled
                    binding.btnFrequencyUp?.isEnabled = manualControlsEnabled
                    binding.btnIntensityDown?.isEnabled = manualControlsEnabled
                    binding.btnIntensityUp?.isEnabled = manualControlsEnabled
                    binding.btnTimeDown?.isEnabled = manualControlsEnabled
                    binding.btnTimeUp?.isEnabled = manualControlsEnabled

                    if (expertActive && !lastExpertActive) {
                        showPhoneKeypad(false)
                    }
                    lastExpertActive = expertActive

                    applyStartStopState(displayState, binding.btnStartStop, binding.btnStop, hideStop = false)
                }
            }
        }
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

    private fun observePresetState() {
        if (!isPhoneHome) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    presetViewModel.events.collect { event ->
                        when (event) {
                            is UiEvent.ShowToast -> showToast(event.message)
                            is UiEvent.ShowError -> showToast(event.throwable.message ?: "Unexpected error")
                        }
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
                        if (isPhone() && state.isLoggedIn && isTestAccount && state.isOfflineMode) {
                            viewModel.applyPhoneOfflineDefaultsIfNeeded()
                        }
                        if (isPhoneHome) {
                            presetViewModel.updateAccountAccess(isTestAccount)
                            presetViewModel.setSessionActive(state.isLoggedIn)
                        }
                    }
                }
            }
        }
    }

    private fun observeSelectedCustomer() {
        if (!isPhoneHome) return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                customerViewModel.selectedCustomer.collect { presetViewModel.setActiveCustomer(it) }
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
        // Shared keypad wiring (phone + tablet) once keypad is inflated.
        if (!isPhoneHome) {
            ensureKeypadViews()
        } else if (binding.root.findViewById<View?>(R.id.layout_keypad_container) != null &&
            binding.root.findViewById<ViewStub?>(R.id.stub_keypad_container) == null
        ) {
            ensureKeypadViews()
        }
        setupKeypadListenersIfReady()

        if (isTablet) {
            setupTabletClickListeners()
        } else {
            setupPhoneClickListeners()
        }
    }

    private fun setupPhoneInputs() {
        if (isTablet) return

        setupPhoneDisplayInput(binding.tvFrequencyValue, "frequency")
        setupPhoneDisplayInput(binding.tvIntensityValue, "intensity")
        setupPhoneDisplayInput(binding.tvTimeValue, "time")
        setupPhoneKeypadDismissListener()
    }

    private fun setupPhoneDisplayInput(view: TextView?, inputType: String) {
        view ?: return
        view.setOnClickListener {
            if (!view.isEnabled) return@setOnClickListener
            viewModel.setActiveMode(HomeViewModel.ActiveMode.MANUAL)
            viewModel.handleIntent(VibrationSessionIntent.SelectInput(inputType))
            viewModel.playTapSound()
            showPhoneKeypad(true)
        }
    }

    private fun isPhoneKeypadVisible(): Boolean {
        return keypadViews?.root?.visibility == View.VISIBLE
    }

    private fun setupPhoneKeypadDismissListener() {
        binding.root.setOnTouchListener { _, event ->
            if (event.actionMasked != MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            if (!isPhoneKeypadVisible()) return@setOnTouchListener false
            if (isTouchInsideView(keypadViews?.root, event)) return@setOnTouchListener false
            if (isTouchInsideView(binding.tvFrequencyValue, event)) return@setOnTouchListener false
            if (isTouchInsideView(binding.tvIntensityValue, event)) return@setOnTouchListener false
            if (isTouchInsideView(binding.tvTimeValue, event)) return@setOnTouchListener false
            showPhoneKeypad(false)
            false
        }
    }

    private fun isTouchInsideView(view: View?, event: MotionEvent): Boolean {
        if (view == null || view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = event.rawX
        val y = event.rawY
        return x >= location[0] && x <= location[0] + view.width &&
            y >= location[1] && y <= location[1] + view.height
    }

    private fun showPhoneKeypad(show: Boolean) {
        if (show) {
            ensureKeypadViews()
            setupKeypadListenersIfReady()
        }
        val views = keypadViews ?: return
        val visibility = if (show) View.VISIBLE else View.GONE
        views.root.visibility = visibility
        listOfNotNull(views.flow)
            .plus(views.numericButtons)
            .plus(listOfNotNull(views.clearButton, views.enterButton))
            .forEach { it.visibility = visibility }
    }

    private fun ensureKeypadViews() {
        if (keypadViews != null) return
        val stub = binding.root.findViewById<ViewStub>(R.id.stub_keypad_container)
        val root = if (stub != null) {
            if (stub.parent != null) {
                stub.inflate()
            } else {
                binding.root.findViewById(R.id.layout_keypad_container)
            }
        } else {
            binding.root.findViewById(R.id.layout_keypad_container)
        } ?: return

        val numericButtons = listOf(
            R.id.btn_key_0, R.id.btn_key_1, R.id.btn_key_2, R.id.btn_key_3, R.id.btn_key_4,
            R.id.btn_key_5, R.id.btn_key_6, R.id.btn_key_7, R.id.btn_key_8, R.id.btn_key_9
        ).mapNotNull { id -> root.findViewById<Button?>(id) }

        keypadViews = KeypadViews(
            root = root,
            flow = root.findViewById(R.id.flow_keypad),
            numericButtons = numericButtons,
            clearButton = root.findViewById(R.id.btn_key_clear),
            enterButton = root.findViewById(R.id.btn_key_enter),
            softResumeButton = root.findViewById(R.id.btn_soft_resume_inline)
        )
    }

    private fun setupTabletClickListeners() {
        // --- Tablet-only: input selection ---
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

        // --- Tablet-only: +/- (support long-press repeat) ---
        configureTabletAdjustButton(
            button = binding.btnFrequencyUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnFrequencyDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(-1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnIntensityUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnIntensityDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(-1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnTimeUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustTime(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnTimeDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustTime(-1)) }
        )

        // --- Tablet-only: start/stop ---
        binding.btnStartStop?.setOnClickListener {
            val selectedCustomer = customerViewModel.selectedCustomer.value
            viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
            viewModel.playTapSound()
        }

        binding.btnStop?.setOnClickListener {
            if (isPhoneHome) {
                when {
                    cachedExpertActive || (viewModel.activeMode.value == HomeViewModel.ActiveMode.EXPERT && !cachedManualActive) -> {
                        presetViewModel.stopIfRunning()
                        presetViewModel.playTapSound()
                    }
                    cachedManualActive -> {
                        viewModel.stopSession()
                        viewModel.playTapSound()
                    }
                    else -> {
                        viewModel.stopSession()
                        viewModel.playTapSound()
                        presetViewModel.stopIfRunning()
                    }
                }
            } else {
                viewModel.stopSession()
                viewModel.playTapSound()
            }
        }

        keypadViews?.softResumeButton?.setOnClickListener {
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

    private fun setupPhoneClickListeners() {
        if (!isPhoneHome) {
            setupTabletClickListeners()
            return
        }

        expertModeButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                if (cachedExpertActive || cachedManualActive) {
                    Toast.makeText(requireContext(), "请先停止当前会话", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                showPhoneKeypad(false)
                viewModel.setActiveMode(HomeViewModel.ActiveMode.EXPERT)
                presetViewModel.selectMode(index)
                presetViewModel.playTapSound()
            }
        }

        binding.btnStop?.setOnClickListener {
            when {
                cachedExpertActive || (viewModel.activeMode.value == HomeViewModel.ActiveMode.EXPERT && !cachedManualActive) -> {
                    presetViewModel.stopIfRunning()
                    presetViewModel.playTapSound()
                    viewModel.setActiveMode(HomeViewModel.ActiveMode.MANUAL)
                }
                cachedManualActive -> {
                    viewModel.stopSession()
                    viewModel.playTapSound()
                }
                else -> {
                    viewModel.stopSession()
                    viewModel.playTapSound()
                    presetViewModel.stopIfRunning()
                    viewModel.setActiveMode(HomeViewModel.ActiveMode.MANUAL)
                }
            }
        }

        configureTabletAdjustButton(
            button = binding.btnFrequencyUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnFrequencyDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustFrequency(-1)) }
        )

        configureTabletAdjustButton(
            button = binding.btnIntensityUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnIntensityDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustIntensity(-1)) }
        )

        configureTabletAdjustButton(
            button = binding.btnTimeUp,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustTime(1)) }
        )
        configureTabletAdjustButton(
            button = binding.btnTimeDown,
            onSingleTap = { viewModel.handleIntent(VibrationSessionIntent.AdjustTime(-1)) }
        )

        binding.btnStartStop?.setOnClickListener {
            val selectedCustomer = customerViewModel.selectedCustomer.value
            if (isPhoneHome) {
                when {
                    cachedExpertActive -> {
                        presetViewModel.handleSessionIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
                        presetViewModel.playTapSound()
                    }
                    cachedManualActive -> {
                        viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
                        viewModel.playTapSound()
                    }
                    viewModel.activeMode.value == HomeViewModel.ActiveMode.EXPERT -> {
                        presetViewModel.handleSessionIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
                        presetViewModel.playTapSound()
                    }
                    else -> {
                        viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
                        viewModel.playTapSound()
                    }
                }
            } else {
                viewModel.handleIntent(VibrationSessionIntent.ToggleStartStop(selectedCustomer))
                viewModel.playTapSound()
            }
        }
    }

    private fun setupKeypadListenersIfReady() {
        val views = keypadViews ?: return
        if (keypadListenersBound) return
        keypadListenersBound = true

        // --- Shared keypad handlers (phone + tablet) ---
        val numericClickListener = View.OnClickListener { view ->
            if (viewModel.currentInputType.value.isNullOrEmpty()) return@OnClickListener
            viewModel.handleIntent(VibrationSessionIntent.AppendDigit((view as Button).text.toString()))
            viewModel.playTapSound()
        }
        views.numericButtons.forEach { it.setOnClickListener(numericClickListener) }

        views.clearButton?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.DeleteDigit)
            viewModel.playTapSound()
        }
        views.clearButton?.setOnLongClickListener {
            viewModel.handleIntent(VibrationSessionIntent.ClearCurrent)
            viewModel.playTapSound()
            true
        }

        views.enterButton?.setOnClickListener {
            viewModel.handleIntent(VibrationSessionIntent.CommitAndCycle)
            viewModel.playTapSound()
            // 手机端：回车只提交/切换输入，不再关闭 keypad（保留继续输入的体验）
        }
    }

    private fun setupExpertIntensityScaleControls() {
        if (!isPhoneHome) return
        val plus = binding.btnIntensityScalePlus
        val minus = binding.btnIntensityScaleMinus
        val input = binding.inputIntensityScale

        input?.doAfterTextChanged { text ->
            if (updatingIntensityScaleFromState) return@doAfterTextChanged
            val value = text?.toString()?.toIntOrNull()
            if (value == null) {
                val current = presetViewModel.uiState.value.intensityScalePct.toString()
                updatingIntensityScaleFromState = true
                input.setText(current)
                input.setSelection(current.length)
                updatingIntensityScaleFromState = false
                return@doAfterTextChanged
            }
            presetViewModel.setIntensityScalePct(value)
        }

        plus?.setOnClickListener { adjustIntensityScale(1) }
        minus?.setOnClickListener { adjustIntensityScale(-1) }
    }

    private fun setupExpertRepeatCountControls() {
        if (!isPhoneHome) return
        val plus = binding.btnRepeatCountPlus
        val minus = binding.btnRepeatCountMinus
        plus?.setOnClickListener { presetViewModel.incrementRepeatCount() }
        minus?.setOnClickListener { presetViewModel.decrementRepeatCount() }
    }

    private fun adjustIntensityScale(delta: Int) {
        val current = presetViewModel.uiState.value.intensityScalePct
        presetViewModel.setIntensityScalePct(current + delta)
    }

    private fun updateIntensityScaleUi(value: Int, enabled: Boolean) {
        val plus = binding.btnIntensityScalePlus
        val minus = binding.btnIntensityScaleMinus
        val input = binding.inputIntensityScale
        plus?.isEnabled = enabled
        minus?.isEnabled = enabled
        input?.isEnabled = enabled
        input ?: return
        val target = value.toString()
        if (input.text?.toString() == target) return
        updatingIntensityScaleFromState = true
        input.setText(target)
        input.setSelection(target.length)
        updatingIntensityScaleFromState = false
    }

    private fun updateRepeatCountUi(value: Int, enabled: Boolean) {
        val plus = binding.btnRepeatCountPlus
        val minus = binding.btnRepeatCountMinus
        val label = binding.tvRepeatCountValue
        plus?.isEnabled = enabled
        minus?.isEnabled = enabled
        label?.isEnabled = enabled
        label?.text = value.toString()
        label?.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (enabled) android.R.color.black else android.R.color.darker_gray
            )
        )
    }

    private fun configureTabletAdjustButton(
        button: View,
        onSingleTap: () -> Unit
    ) {
        // Tablet-only: allow long-press repeat without affecting phone logic.
        val handler = Handler(Looper.getMainLooper())
        var isRepeating = false
        var repeatRunnable: Runnable? = null

        val startRepeat = Runnable {
            isRepeating = true
            repeatRunnable = object : Runnable {
                override fun run() {
                    onSingleTap()
                    viewModel.playTapSound()
                    handler.postDelayed(this, PHONE_REPEAT_INTERVAL_MS)
                }
            }.also { handler.post(it) }
        }

        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isRepeating = false
                    handler.postDelayed(startRepeat, PHONE_LONG_PRESS_DELAY_MS)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(startRepeat)
                    repeatRunnable?.let { handler.removeCallbacks(it) }
                    if (event.actionMasked == MotionEvent.ACTION_UP && !isRepeating) {
                        onSingleTap()
                        viewModel.playTapSound()
                    }
                    isRepeating = false
                    true
                }
                else -> false
            }
        }
    }

    private fun showToast(message: String) {
        if (!isAdded) return
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
