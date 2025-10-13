package com.example.sonicwavev4.ui.home

import android.hardware.usb.UsbDevice
import android.os.Build
import android.media.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import com.example.sonicwavev4.utils.SessionManager
import androidx.fragment.app.activityViewModels
import com.example.sonicwavev4.ui.user.UserViewModel
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.sin
import java.util.concurrent.Executors
import cn.wch.ch341lib.CH341Manager
import cn.wch.ch341lib.exception.CH341LibException
import cn.wch.ch347lib.callback.IUsbStateChange
import cn.wch.ch347lib.exception.ChipException
import cn.wch.ch347lib.exception.NoPermissionException
import com.example.sonicwavev4.harddriver.Ad9833Controller
import com.example.sonicwavev4.harddriver.Mcp41010Controller

class HomeFragment : Fragment() {

    private lateinit var viewModel: HomeViewModel
    private val userViewModel: UserViewModel by activityViewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val ch341Manager: CH341Manager = CH341Manager.getInstance()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var usbDevice: UsbDevice? = null
    private var isDeviceOpen = false
    private val ad9833Controller = Ad9833Controller()
    private val mcpController = Mcp41010Controller()
    private var lastAppliedFrequency = Double.NaN
    private var lastAppliedIntensity = -1

    private val usbStateListener = object : IUsbStateChange {
        override fun usbDeviceDetach(device: UsbDevice?) {
            if (device != null && device == usbDevice) {
                requireActivity().runOnUiThread {
                    showToast("CH341 已断开")
                    releaseHardware()
                }
            }
        }

        override fun usbDeviceAttach(device: UsbDevice?) {
            requireActivity().runOnUiThread { openDeviceIfNeeded() }
        }

        override fun usbDevicePermission(usbDevice: UsbDevice?, granted: Boolean) {
            if (granted) {
                requireActivity().runOnUiThread { openDeviceIfNeeded() }
            } else {
                requireActivity().runOnUiThread { showToast("USB 权限被拒绝") }
            }
        }
    }

    private val emptyUsbStateListener = object : IUsbStateChange {
        override fun usbDeviceDetach(device: UsbDevice?) {}
        override fun usbDeviceAttach(device: UsbDevice?) {}
        override fun usbDevicePermission(usbDevice: UsbDevice?, granted: Boolean) {}
    }

    // 音频资源
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private lateinit var soundPool: SoundPool
    private var tapSoundId: Int = 0
    private var isSoundPoolReady = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 通过 ViewModelProvider 获取 ViewModel 实例
        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        setupClickListeners()
        setupObservers()
        initializeSoundPool()
        ch341Manager.setUsbStateListener(usbStateListener)
        openDeviceIfNeeded()
    }

    override fun onDestroyView() { super.onDestroyView(); stopTonePlayback(); _binding = null }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
        ch341Manager.setUsbStateListener(emptyUsbStateListener)
        releaseHardware()
        context?.let { ch341Manager.close(it) }
        ioExecutor.shutdownNow()
    }

    override fun onResume() {
        super.onResume()
        openDeviceIfNeeded()
    }
    private fun setupObservers() {
        viewModel.frequency.observe(viewLifecycleOwner) { value ->
            updateFrequencyDisplay()
            binding.root.post {
                val editing = viewModel.isEditing.value == true && viewModel.currentInputType.value == "frequency"
                if (!editing) {
                    applyFrequencyToHardware(value ?: 0)
                }
            }
        }
        viewModel.intensity.observe(viewLifecycleOwner) { value ->
            updateIntensityDisplay()
            binding.root.post {
                val editing = viewModel.isEditing.value == true && viewModel.currentInputType.value == "intensity"
                if (!editing) {
                    applyIntensityToHardware(value ?: 0)
                }
            }
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
            updateTimeDisplay()
            if (isPlaying) {
                sendStartCommand()
            } else {
                sendStopCommand()
            }
        }

        viewModel.countdownSeconds.observe(viewLifecycleOwner) { seconds ->
            if (viewModel.isStarted.value == true) {
                val minutesPart = seconds / 60
                val secondsPart = seconds % 60
                binding.tvTimeValue.text = String.format(Locale.ROOT, "%02d:%02d", minutesPart, secondsPart)
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
        binding.tvFrequencyValue.setOnClickListener { viewModel.setCurrentInputType("frequency"); playTapSound() }
        binding.tvIntensityValue.setOnClickListener { viewModel.setCurrentInputType("intensity"); playTapSound() }
        binding.tvTimeValue.setOnClickListener { viewModel.setCurrentInputType("time"); playTapSound() }

        // --- NEW: Simplified click listeners ---
        binding.btnFrequencyUp.setOnClickListener { viewModel.incrementFrequency(); playTapSound() }
        binding.btnFrequencyDown.setOnClickListener { viewModel.decrementFrequency(); playTapSound() }
        binding.btnIntensityUp.setOnClickListener { viewModel.incrementIntensity(); playTapSound() }
        binding.btnIntensityDown.setOnClickListener { viewModel.decrementIntensity(); playTapSound() }
        binding.btnTimeUp.setOnClickListener { viewModel.incrementTime(); playTapSound() }
        binding.btnTimeDown.setOnClickListener { viewModel.decrementTime(); playTapSound() }


        binding.btnStartStop.setOnClickListener { 
            val selectedCustomer = userViewModel.selectedCustomer.value
            viewModel.startStopPlayback(selectedCustomer)
            playTapSound() 
        }
        binding.btnClear.setOnClickListener { viewModel.clearAll(); playTapSound() }

        val numericClickListener = View.OnClickListener { view ->
            if (viewModel.currentInputType.value.isNullOrEmpty()) return@OnClickListener
            viewModel.appendToInputBuffer((view as Button).text.toString()); playTapSound()
        }
        listOf(binding.btnKey0, binding.btnKey1, binding.btnKey2, binding.btnKey3, binding.btnKey4,
            binding.btnKey5, binding.btnKey6, binding.btnKey7, binding.btnKey8, binding.btnKey9)
            .forEach { it.setOnClickListener(numericClickListener) }

        binding.btnKeyClear.setOnClickListener { viewModel.deleteLastFromInputBuffer(); playTapSound() }
        binding.btnKeyClear.setOnLongClickListener { viewModel.clearCurrentParameter(); playTapSound(); true }

        binding.btnKeyEnter.setOnClickListener { viewModel.commitAndCycleInputType(); playTapSound() }

    }

    // --- REMOVED: handleDeltaChange function is no longer needed ---

    // --- 其他辅助函数 ---
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build()
        tapSoundId = soundPool.load(context, R.raw.tap_sound, 1)
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) isSoundPoolReady = true }
    }
    private fun playTapSound() { if (isSoundPoolReady) soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f) }

private fun showToast(message: String) {
    activity?.runOnUiThread {
        val ctx = context?.applicationContext ?: return@runOnUiThread
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
}

private fun openDeviceIfNeeded() {
    if (isDeviceOpen) return
    val devices = try {
        ch341Manager.enumDevice()
    } catch (e: CH341LibException) {
        showToast("枚举设备失败: ${e.message}")
        return
    }
    if (devices.isEmpty()) {
        showToast("未找到 CH341 设备")
        return
    }
    if (devices.size > 1) {
        showToast("只支持连接一个 CH341 设备")
        return
    }
    val target = devices[0]
    try {
        if (ch341Manager.hasPermission(target)) {
            if (!ch341Manager.openDevice(target)) {
                showToast("打开 CH341 设备失败")
                return
            }
            onDeviceOpened(target)
        } else {
            ch341Manager.requestPermission(requireContext(), target)
        }
    } catch (e: CH341LibException) {
        showToast("操作 CH341 异常: ${e.message}")
    } catch (e: NoPermissionException) {
        showToast("无 USB 权限")
    } catch (e: ChipException) {
        showToast("芯片错误: ${e.message}")
    }
}

private fun onDeviceOpened(device: UsbDevice) {
    usbDevice = device
    isDeviceOpen = true
    initializeAd9833Hardware()
    initializeMcp41010Hardware()
}

private fun releaseHardware() {
    val device = usbDevice
    if (device != null && isDeviceOpen) {
        try {
            ch341Manager.closeDevice(device)
        } catch (_: Exception) { }
    }
    usbDevice = null
    isDeviceOpen = false
    try {
        ad9833Controller.detach()
    } catch (_: Exception) { }
    try {
        mcpController.detach()
    } catch (_: Exception) { }
    lastAppliedFrequency = Double.NaN
    lastAppliedIntensity = -1
}

private fun initializeAd9833Hardware() {
    val device = usbDevice ?: return
    ioExecutor.execute {
        try {
            ad9833Controller.attachDevice(device)
            ad9833Controller.setCsChannel(0)
            ad9833Controller.begin()
            ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, 0.0)
            ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0)
            ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
            lastAppliedFrequency = 0.0
            showToast("AD9833 已初始化")
        } catch (e: CH341LibException) {
            showToast("初始化 AD9833 失败: ${e.message}")
        }
    }
}

private fun initializeMcp41010Hardware() {
    val device = usbDevice ?: return
    ioExecutor.execute {
        try {
            mcpController.attachDevice(device)
            mcpController.setCsChannel(1)
            val value = viewModel.intensity.value ?: 0
            val clamped = value.coerceIn(0, 255)
            mcpController.writeValue(clamped)
            lastAppliedIntensity = clamped
            showToast("MCP41010 已初始化")
        } catch (e: CH341LibException) {
            showToast("初始化 MCP41010 失败: ${e.message}")
        }
    }
}

private fun applyFrequencyToHardware(freq: Int) {
    if (!isDeviceOpen || usbDevice == null) return
    val freqDouble = freq.toDouble()
    if (freqDouble == lastAppliedFrequency) return
    ioExecutor.execute {
        try {
            ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, freqDouble)
            ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0)
            lastAppliedFrequency = freqDouble
        } catch (e: CH341LibException) {
            showToast("设置频率失败: ${e.message}")
        }
    }
}

private fun applyIntensityToHardware(value: Int) {
    if (!isDeviceOpen || usbDevice == null) return
    val clamped = value.coerceIn(0, 255)
    if (clamped == lastAppliedIntensity) return
    ioExecutor.execute {
        try {
            mcpController.writeValue(clamped)
            lastAppliedIntensity = clamped
        } catch (e: CH341LibException) {
            showToast("设置幅度失败: ${e.message}")
        }
    }
}

private fun sendStartCommand() {
    if (!isDeviceOpen || usbDevice == null) return
    val freq = viewModel.frequency.value ?: 0
    applyFrequencyToHardware(freq)
    applyIntensityToHardware(viewModel.intensity.value ?: 0)
    ioExecutor.execute {
        try {
            ad9833Controller.setMode(Ad9833Controller.MODE_BITS_SINE)
        } catch (e: CH341LibException) {
            showToast("启动输出失败: ${e.message}")
        }
    }
}

private fun sendStopCommand() {
    if (!isDeviceOpen || usbDevice == null) return
    ioExecutor.execute {
        try {
            ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
        } catch (e: CH341LibException) {
            showToast("停止输出失败: ${e.message}")
        }
    }
}

    private fun startTonePlayback() {
        // --- FINAL SOLUTION: Best-effort audio focus request ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS) viewModel.forceStop() }
                .build()

            audioFocusRequest?.let {
                // MODIFIED: If focus request fails, just log a warning and continue.
                if (audioManager?.requestAudioFocus(it) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.w("HomeFragment", "Audio focus request failed, but continuing playback anyway.")
                    // We no longer call viewModel.forceStop() or return here.
                }
            }
        } else {
            @Suppress("DEPRECATION")
            // MODIFIED: If focus request fails, just log a warning and continue.
            if (audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) !=
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w("HomeFragment", "Audio focus request failed (legacy), but continuing playback anyway.")
                // We no longer call viewModel.forceStop() or return here.
            }
        }
        // --- END FINAL SOLUTION ---

        playbackJob = lifecycleScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val currentFrequency = viewModel.frequency.value ?: 0
            val currentIntensity = viewModel.intensity.value ?: 0
            val totalSeconds = (viewModel.timeInMinutes.value ?: 0) * 60
            val volume = if (currentIntensity > 100) 1f else currentIntensity / 100f

            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioTrack = AudioTrack.Builder()

                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            val samplesPerCycle = sampleRate.toDouble() / currentFrequency
            val waveBuffer = ShortArray(samplesPerCycle.toInt())
            for (i in waveBuffer.indices) {
                waveBuffer[i] = (sin(2 * Math.PI * i / samplesPerCycle) * Short.MAX_VALUE * volume).toInt().toShort()
            }

            var samplesGenerated = 0L
            val totalSamples = (sampleRate * totalSeconds).toLong()

            while (isActive && samplesGenerated < totalSamples) {
                if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val remainingSamples = totalSamples - samplesGenerated
                    val samplesToWrite = minOf(waveBuffer.size.toLong(), remainingSamples).toInt()
                    val result = audioTrack?.write(waveBuffer, 0, samplesToWrite) ?: -1
                    if (result > 0) {
                        samplesGenerated += result
                    } else {
                        break
                    }
                } else {
                    break
                }
            }
        }
    }





    private fun stopTonePlayback() {
        playbackJob?.cancel(); playbackJob = null
        audioTrack?.stop(); audioTrack?.release(); audioTrack = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) } } else { @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(null) }
        audioFocusRequest = null
    }
}
