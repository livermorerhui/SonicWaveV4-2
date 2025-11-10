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
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.ui.common.UiEvent
import com.example.sonicwavev4.ui.user.UserViewModel
import com.example.sonicwavev4.utils.SessionManager
import java.util.Locale
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
        setupObservers()
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

    private fun setupObservers() {
        viewModel.frequency.observe(viewLifecycleOwner) { value ->
            updateFrequencyDisplay()
        }
        viewModel.intensity.observe(viewLifecycleOwner) { value ->
            updateIntensityDisplay()
        }
        viewModel.timeInMinutes.observe(viewLifecycleOwner) { updateTimeDisplay() }

        viewModel.currentInputType.observe(viewLifecycleOwner) { type ->
            updateHighlights(type)
            updateAllDisplays()
        }
        viewModel.inputBuffer.observe(viewLifecycleOwner) { updateAllDisplays() }
        viewModel.isEditing.observe(viewLifecycleOwner) { updateAllDisplays() } // Add this observer

        viewModel.isStarted.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnStartStop.text = if (isPlaying) getString(R.string.button_stop) else getString(R.string.button_start)
            binding.tvTimeValue.isEnabled = !isPlaying
            updateTimeDisplay()
        }

        viewModel.countdownSeconds.observe(viewLifecycleOwner) { seconds ->
            if (viewModel.isStarted.value == true) {
                val minutesPart = seconds / 60
                val secondsPart = seconds % 60
                binding.tvTimeValue.text = String.format(Locale.ROOT, "%02d:%02d", minutesPart, secondsPart)
            }
        }
        viewModel.startButtonEnabled.observe(viewLifecycleOwner) { isEnabled ->
            binding.btnStartStop.isEnabled = isEnabled
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

    // --- UI 更新逻辑 ---
    private fun updateAllDisplays(){
        updateFrequencyDisplay()
        updateIntensityDisplay()
        updateTimeDisplay()
    }

    private fun updateFrequencyDisplay() {
        val buffer = viewModel.inputBuffer.value
        val isInputActive = viewModel.currentInputType.value == "frequency"
        val isEditing = viewModel.isEditing.value ?: false

        if (isInputActive && (isEditing || !buffer.isNullOrEmpty())) {
            binding.tvFrequencyValue.text = getString(R.string.frequency_format, buffer?.toIntOrNull() ?: 0)
        } else {
            binding.tvFrequencyValue.text = getString(R.string.frequency_format, viewModel.frequency.value ?: 0)
        }
    }


    private fun updateIntensityDisplay() {
        val buffer = viewModel.inputBuffer.value
        val isInputActive = viewModel.currentInputType.value == "intensity"
        val isEditing = viewModel.isEditing.value ?: false

        if (isInputActive && (isEditing || !buffer.isNullOrEmpty())) {
            binding.tvIntensityValue.text = buffer?.ifEmpty { "0" } ?: "0"
        } else {
            binding.tvIntensityValue.text = (viewModel.intensity.value ?: 0).toString()
        }
    }


    private fun updateTimeDisplay() {
        if (viewModel.isStarted.value == true) return

        val buffer = viewModel.inputBuffer.value
        val isInputActive = viewModel.currentInputType.value == "time"
        val isEditing = viewModel.isEditing.value ?: false

        if (isInputActive && (isEditing || !buffer.isNullOrEmpty())) {
            binding.tvTimeValue.text = getString(R.string.time_minutes_format, buffer?.toIntOrNull() ?: 0)
        } else {
            binding.tvTimeValue.text = getString(R.string.time_minutes_format, viewModel.timeInMinutes.value ?: 0)
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
            viewModel.setCurrentInputType("frequency")
            viewModel.playTapSound()
        }
        binding.tvIntensityValue.setOnClickListener {
            viewModel.setCurrentInputType("intensity")
            viewModel.playTapSound()
        }
        binding.tvTimeValue.setOnClickListener {
            viewModel.setCurrentInputType("time")
            viewModel.playTapSound()
        }

        configureAdjustButton(
            button = binding.btnFrequencyUp,
            onSingleTap = { viewModel.incrementFrequency() },
            onAutoRepeat = { viewModel.adjustFrequency(10) }
        )
        configureAdjustButton(
            button = binding.btnFrequencyDown,
            onSingleTap = { viewModel.decrementFrequency() },
            onAutoRepeat = { viewModel.adjustFrequency(-10) }
        )
        configureAdjustButton(
            button = binding.btnIntensityUp,
            onSingleTap = { viewModel.incrementIntensity() },
            onAutoRepeat = { viewModel.adjustIntensity(10) }
        )
        configureAdjustButton(
            button = binding.btnIntensityDown,
            onSingleTap = { viewModel.decrementIntensity() },
            onAutoRepeat = { viewModel.adjustIntensity(-10) }
        )
        binding.btnTimeUp.setOnClickListener { viewModel.incrementTime(); viewModel.playTapSound() }
        binding.btnTimeDown.setOnClickListener { viewModel.decrementTime(); viewModel.playTapSound() }

        binding.btnStartStop.setOnClickListener {
            val selectedCustomer = userViewModel.selectedCustomer.value
            viewModel.startStopPlayback(selectedCustomer)
            viewModel.playTapSound()
        }
        binding.btnClear.setOnClickListener { viewModel.clearAll(); viewModel.playTapSound() }

        val numericClickListener = View.OnClickListener { view ->
            if (viewModel.currentInputType.value.isNullOrEmpty()) return@OnClickListener
            viewModel.appendToInputBuffer((view as Button).text.toString())
            viewModel.playTapSound()
        }
        listOf(binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3, binding.btnKey4,
            binding.btnKey5, binding.btnKey6, binding.btnKey7, binding.btnKey8, binding.btnKey9)
            .forEach { it.setOnClickListener(numericClickListener) }

        binding.btnKeyClear.setOnClickListener { viewModel.deleteLastFromInputBuffer(); viewModel.playTapSound() }
        binding.btnKeyClear.setOnLongClickListener { viewModel.clearCurrentParameter(); viewModel.playTapSound(); true }

        binding.btnKeyEnter.setOnClickListener { viewModel.commitAndCycleInputType(); viewModel.playTapSound() }

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
