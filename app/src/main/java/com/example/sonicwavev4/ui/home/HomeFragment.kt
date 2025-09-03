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
        // 在访问 lateinit 变量前，先检查它是否已经被初始化
        if (::soundPool.isInitialized) {
            soundPool.release()
        }
    }
    private fun setupObservers() {
        viewModel.frequency.observe(viewLifecycleOwner) { updateFrequencyDisplay() }
        viewModel.intensity.observe(viewLifecycleOwner) { updateIntensityDisplay() }
        // 【改动】现在观察的是分钟
        viewModel.timeInMinutes.observe(viewLifecycleOwner) { updateTimeDisplay() }

        viewModel.currentInputType.observe(viewLifecycleOwner) { type ->
            updateHighlights(type)
            updateAllDisplays()
        }
        viewModel.inputBuffer.observe(viewLifecycleOwner) { updateAllDisplays() }

        // 【改动】运行时观察倒计时，否则显示设置的时间
        viewModel.isStarted.observe(viewLifecycleOwner) { isPlaying ->
            binding.btnStartStop.text = if (isPlaying) getString(R.string.button_stop) else getString(R.string.button_start)
            updateTimeDisplay() // 切换播放状态时也更新时间显示
            if (isPlaying) startTonePlayback() else stopTonePlayback()
        }

        // 【新增】观察倒计时秒数
        viewModel.countdownSeconds.observe(viewLifecycleOwner) { seconds ->
            if (viewModel.isStarted.value == true) {
                // 只有在运行时才显示倒计时
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


    // 【改动】时间显示逻辑
    private fun updateTimeDisplay() {
         //如果正在运行，则不处理，让countdown的观察者来处理
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
        // 【改动】时间按钮现在操作分钟
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

        // --- 这是唯一的改动区域 ---
        // 短按：删除最后一个字符
        binding.btnKeyClear.setOnClickListener { viewModel.deleteLastFromInputBuffer(); playTapSound() }
        // 长按：清除当前高亮参数的已存值和缓冲值
        binding.btnKeyClear.setOnLongClickListener { viewModel.clearCurrentParameter(); playTapSound(); true }

        binding.btnKeyEnter.setOnClickListener { viewModel.commitAndCycleInputType(); playTapSound() }

    }

    // 【改动】 +/- 按钮逻辑
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
            // 【改动】音频播放时使用分钟*60来获取总秒数
            val totalSeconds = (viewModel.timeInMinutes.value ?: 0) * 60
            val volume = if (currentIntensity > 100) 1f else currentIntensity / 100f
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)

            audioTrack = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(bufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()
            audioTrack?.play()

            val buffer = ShortArray(bufferSize)
            val freqHz = currentFrequency.toDouble()
            val phaseIncrement = 2 * Math.PI * freqHz / sampleRate
            var phase = 0.0
            var samplesGenerated = 0L
            val totalSamples = (sampleRate * totalSeconds).toLong()

            while (isActive && samplesGenerated < totalSamples) {
                for (i in buffer.indices) { buffer[i] = (sin(phase) * Short.MAX_VALUE * volume).toInt().toShort(); phase += phaseIncrement; if (phase >= 2 * Math.PI) phase -= 2 * Math.PI }
                audioTrack?.write(buffer, 0, buffer.size); samplesGenerated += buffer.size
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