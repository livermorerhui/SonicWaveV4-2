package com.example.sonicwavev4.ui.home

import android.os.Build
import android.media.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.sonicwavev4.R
import com.example.sonicwavev4.databinding.FragmentHomeBinding
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.sin

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        initializeSoundPool()
        audioManager = context?.getSystemService(AudioManager::class.java)
        setupClickListeners()
        setupObservers()
    }

    override fun onDestroyView() { super.onDestroyView(); stopTonePlayback(); _binding = null }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
    }
    private fun setupObservers() {
        viewModel.frequency.observe(viewLifecycleOwner) { updateFrequencyDisplay() }
        viewModel.intensity.observe(viewLifecycleOwner) { updateIntensityDisplay() }
        viewModel.timeInMinutes.observe(viewLifecycleOwner) { updateTimeDisplay() }

        viewModel.currentInputType.observe(viewLifecycleOwner) { type ->
            updateHighlights(type)
            updateAllDisplays()
        }
        viewModel.inputBuffer.observe(viewLifecycleOwner) { updateAllDisplays() }

        viewModel.isStarted.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnStartStop.text = if (isPlaying) getString(R.string.button_stop) else getString(R.string.button_start)
            updateTimeDisplay()
            if (isPlaying) startTonePlayback() else stopTonePlayback()
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
        if (viewModel.currentInputType.value == "frequency" && buffer?.isNotEmpty() == true) {
            binding.tvFrequencyValue.text = getString(R.string.frequency_format, buffer.toIntOrNull() ?: 0)
        } else {
            binding.tvFrequencyValue.text = getString(R.string.frequency_format, viewModel.frequency.value ?: 0)
        }
    }


    private fun updateIntensityDisplay() {
        val buffer = viewModel.inputBuffer.value
        if (viewModel.currentInputType.value == "intensity" && buffer?.isNotEmpty() == true) {
            binding.tvIntensityValue.text = buffer
        } else {
            binding.tvIntensityValue.text = (viewModel.intensity.value ?: 0).toString()
        }
    }


    private fun updateTimeDisplay() {
        if (viewModel.isStarted.value == true) return

        val buffer = viewModel.inputBuffer.value
        if (viewModel.currentInputType.value == "time" && buffer?.isNotEmpty() == true) {
            binding.tvTimeValue.text = getString(R.string.time_minutes_format, buffer.toIntOrNull() ?: 0)
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
        binding.tvFrequencyValue.setOnClickListener { viewModel.setCurrentInputType("frequency") }
        binding.tvIntensityValue.setOnClickListener { viewModel.setCurrentInputType("intensity") }
        binding.tvTimeValue.setOnClickListener { viewModel.setCurrentInputType("time") }

        binding.btnFrequencyUp.setOnClickListener { handleDeltaChange("frequency", 1) }
        binding.btnFrequencyDown.setOnClickListener { handleDeltaChange("frequency", -1) }
        binding.btnIntensityUp.setOnClickListener { handleDeltaChange("intensity", 1) }
        binding.btnIntensityDown.setOnClickListener { handleDeltaChange("intensity", -1) }
        binding.btnTimeUp.setOnClickListener { handleDeltaChange("time", 1) }
        binding.btnTimeDown.setOnClickListener { handleDeltaChange("time", -1) }

        binding.btnStartStop.setOnClickListener { viewModel.startStopPlayback(); playTapSound() }
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

    private fun handleDeltaChange(type: String, delta: Int) {
        viewModel.setCurrentInputType(type)
        when (type) {
            "frequency" -> viewModel.updateFrequency((viewModel.frequency.value ?: 0) + delta)
            "intensity" -> viewModel.updateIntensity((viewModel.intensity.value ?: 0) + delta)
            "time"      -> viewModel.updateTimeInMinutes((viewModel.timeInMinutes.value ?: 0) + delta)
        }
        playTapSound()
    }

    // --- 其他辅助函数 ---
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(audioAttributes).build()
        tapSoundId = soundPool.load(context, R.raw.tap_sound, 1)
        soundPool.setOnLoadCompleteListener { _, _, status -> if (status == 0) isSoundPoolReady = true }
    }
    private fun playTapSound() { if (isSoundPoolReady) soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f) }

    private fun startTonePlayback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS) viewModel.forceStop() }.build()
            audioFocusRequest?.let { if (audioManager?.requestAudioFocus(it) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { viewModel.forceStop(); return } }
        } else { @Suppress("DEPRECATION") if (audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { viewModel.forceStop(); return } }

        playbackJob = lifecycleScope.launch(Dispatchers.Default) {
            val sampleRate = 44100
            val currentFrequency = viewModel.frequency.value ?: 0
            val currentIntensity = viewModel.intensity.value ?: 0
            val totalSeconds = (viewModel.timeInMinutes.value ?: 0) * 60
            val volume = if (currentIntensity > 100) 1f else currentIntensity / 100f

            // AudioTrack 初始化
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(minBufferSize) // 使用最小缓冲区大小
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            // --- 【优化核心】 ---
            // 1. 预计算一个周期的波形
            val samplesPerCycle = sampleRate.toDouble() / currentFrequency
            val waveBuffer = ShortArray(samplesPerCycle.toInt())
            for (i in waveBuffer.indices) {
                waveBuffer[i] = (sin(2 * Math.PI * i / samplesPerCycle) * Short.MAX_VALUE * volume).toInt().toShort()
            }

            var samplesGenerated = 0L
            val totalSamples = (sampleRate * totalSeconds).toLong()

            // 2. 在循环中重复写入预计算的波形
            while (isActive && samplesGenerated < totalSamples) {
                if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val remainingSamples = totalSamples - samplesGenerated
                    val samplesToWrite = minOf(waveBuffer.size.toLong(), remainingSamples).toInt()

                    val result = audioTrack?.write(waveBuffer, 0, samplesToWrite) ?: -1
                    if (result > 0) {
                        samplesGenerated += result
                    } else {
                        break // 写入失败，跳出循环
                    }
                } else {
                    break // AudioTrack 停止播放，跳出循环
                }
            }
            // --- 【优化结束】 ---
        }
    }

    private fun stopTonePlayback() {
        playbackJob?.cancel(); playbackJob = null
        audioTrack?.stop(); audioTrack?.release(); audioTrack = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) } } else { @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(null) }
        audioFocusRequest = null
    }
}