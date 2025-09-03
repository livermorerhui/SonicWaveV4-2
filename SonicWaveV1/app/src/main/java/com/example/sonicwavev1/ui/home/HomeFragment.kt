package com.example.sonicwavev1.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.media.SoundPool
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.util.Log
import com.example.sonicwavev1.R
import kotlin.math.sin
import kotlin.math.pow
import kotlin.math.max

class HomeFragment : Fragment() {

    private var frequency = 0
    private var intensity = 0
    private var time = 0 // Stored in seconds
    private var isStarted = false // Track Start/Stop state
    private val handler = Handler(Looper.getMainLooper())
    private var isLongPress = false
    private val longPressInterval = 150L // Interval for long press
    private val TAG = "HomeFragment" // For logging

    // SoundPool for button sounds
    private lateinit var soundPool: SoundPool
    private var tapSoundId: Int = 0
    private var fastSoundId: Int = 0
    private var isSoundPoolReady = false

    // AudioTrack for tone generation
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var stopPlayback = false // Flag to stop tone playback

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Initialize SoundPool and AudioManager
        initializeSoundPool()
        audioManager = context?.getSystemService(AudioManager::class.java)

        // Frequency controls
        val btnFrequencyUp: Button = view.findViewById(R.id.btn_frequency_up)
        val btnFrequencyDown: Button = view.findViewById(R.id.btn_frequency_down)
        val tvFrequency: TextView = view.findViewById(R.id.tv_frequency_value)

        // Intensity controls
        val btnIntensityUp: Button = view.findViewById(R.id.btn_intensity_up)
        val btnIntensityDown: Button = view.findViewById(R.id.btn_intensity_down)
        val tvIntensity: TextView = view.findViewById(R.id.tv_intensity_value)

        // Time controls
        val btnTimeUp: Button = view.findViewById(R.id.btn_time_up)
        val btnTimeDown: Button = view.findViewById(R.id.btn_time_down)
        val tvTime: TextView = view.findViewById(R.id.tv_time_value)

        // Start/Stop and Clear controls
        val btnStartStop: Button = view.findViewById(R.id.btn_start_stop)
        val btnClear: Button = view.findViewById(R.id.btn_clear)

        // Frequency button listeners
        btnFrequencyUp.setOnClickListener {
            frequency += 1
            updateFrequencyDisplay(tvFrequency)
            playTapSound()
        }
        btnFrequencyDown.setOnClickListener {
            if (frequency > 0) frequency -= 1
            updateFrequencyDisplay(tvFrequency)
            playTapSound()
        }
        setupLongPress(btnFrequencyUp, { deltaT -> frequency += (deltaT * deltaT).toInt() }, tvFrequency, ::updateFrequencyDisplay)
        setupLongPress(btnFrequencyDown, { deltaT -> if (frequency > 0) frequency = max(0, frequency - (deltaT * deltaT).toInt()) }, tvFrequency, ::updateFrequencyDisplay)

        // Intensity button listeners
        btnIntensityUp.setOnClickListener {
            intensity += 1
            updateIntensityDisplay(tvIntensity)
            playTapSound()
        }
        btnIntensityDown.setOnClickListener {
            if (intensity > 0) intensity -= 1
            updateIntensityDisplay(tvIntensity)
            playTapSound()
        }
        setupLongPress(btnIntensityUp, { deltaT -> intensity += (deltaT * deltaT).toInt() }, tvIntensity, ::updateIntensityDisplay)
        setupLongPress(btnIntensityDown, { deltaT -> if (intensity > 0) intensity = max(0, intensity - (deltaT * deltaT).toInt()) }, tvIntensity, ::updateIntensityDisplay)

        // Time button listeners
        btnTimeUp.setOnClickListener {
            time += 1
            updateTimeDisplay(tvTime)
            playTapSound()
        }
        btnTimeDown.setOnClickListener {
            if (time > 0) time -= 1
            updateTimeDisplay(tvTime)
            playTapSound()
        }
        setupLongPress(btnTimeUp, { deltaT -> time += (deltaT * deltaT).toInt() }, tvTime, ::updateTimeDisplay)
        setupLongPress(btnTimeDown, { deltaT -> if (time > 0) time = max(0, time - (deltaT * deltaT).toInt()) }, tvTime, ::updateTimeDisplay)

        // Start/Stop button listener
        btnStartStop.setOnClickListener {
            isStarted = !isStarted
            btnStartStop.text = if (isStarted) "停止" else "开始"
            playTapSound()
            if (isStarted) {
                startTonePlayback()
            } else {
                stopTonePlayback()
            }
        }

        // Clear button listener
        btnClear.setOnClickListener {
            frequency = 0
            intensity = 0
            time = 0
            updateFrequencyDisplay(tvFrequency)
            updateIntensityDisplay(tvIntensity)
            updateTimeDisplay(tvTime)
            if (isStarted) {
                stopTonePlayback()
                isStarted = false
                btnStartStop.text = "开始"
            }
            playTapSound()
            Log.d(TAG, "Clear button pressed: Reset frequency, intensity, time to 0")
        }

        return view
    }

    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        tapSoundId = soundPool.load(context, R.raw.tap_sound, 1)
        fastSoundId = soundPool.load(context, R.raw.fast_sound, 1)

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                Log.d(TAG, "Sound loaded successfully: sampleId=$sampleId")
                isSoundPoolReady = true
            } else {
                Log.e(TAG, "Failed to load sound: sampleId=$sampleId, status=$status")
            }
        }
    }

    private fun playTapSound() {
        if (isSoundPoolReady && tapSoundId != 0) {
            soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f)
            Log.d(TAG, "Playing tap sound")
        } else {
            Log.w(TAG, "Tap sound not ready: isSoundPoolReady=$isSoundPoolReady, tapSoundId=$tapSoundId")
        }
    }

    private fun playFastSound() {
        if (isSoundPoolReady && fastSoundId != 0) {
            soundPool.play(fastSoundId, 1f, 1f, 1, 0, 1f)
            Log.d(TAG, "Playing fast sound")
        } else {
            Log.w(TAG, "Fast sound not ready: isSoundPoolReady=$isSoundPoolReady, fastSoundId=$fastSoundId")
        }
    }

    private fun updateFrequencyDisplay(textView: TextView) {
        textView.text = "$frequency 赫兹"
    }

    private fun updateIntensityDisplay(textView: TextView) {
        textView.text = intensity.toString()
    }

    private fun updateTimeDisplay(textView: TextView) {
        val minutes = time / 60.0
        textView.text = String.format("%.2f 分钟", minutes) // Display as "分钟 xx.xx"
    }

    private fun setupLongPress(button: Button, action: (Float) -> Unit, textView: TextView, updateDisplay: (TextView) -> Unit) {
        var startTime: Long = 0 // Track start of long press
        val longPressRunnable = object : Runnable {
            override fun run() {
                if (isLongPress) {
                    val deltaT = (System.currentTimeMillis() - startTime) / 1000f // Time in seconds
                    action(deltaT) // Apply non-linear increment based on deltaT
                    updateDisplay(textView)
                    playFastSound()
                    Log.d(TAG, "Long press: Updated value (deltaT=$deltaT) and played sound")
                    handler.postDelayed(this, longPressInterval)
                }
            }
        }

        button.setOnLongClickListener {
            isLongPress = true
            startTime = System.currentTimeMillis() // Record start time
            handler.post(longPressRunnable)
            true
        }

        button.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {
                isLongPress = false
                handler.removeCallbacks(longPressRunnable)
            }
            false
        }
    }

    private fun startTonePlayback() {
        if (frequency <= 0 || time <= 0) {
            Log.w(TAG, "Invalid parameters: frequency=$frequency, time=$time")
            isStarted = false
            view?.findViewById<Button>(R.id.btn_start_stop)?.text = "开始"
            return
        }

        // Request audio focus
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    stopTonePlayback()
                }
            }
            .build()
        audioFocusRequest = focusRequest
        if (audioManager?.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "Audio focus request denied")
            isStarted = false
            view?.findViewById<Button>(R.id.btn_start_stop)?.text = "停止"
            return
        }

        stopPlayback = false
        val sampleRate = 44100
        val volume = if (intensity > 100) 1f else intensity / 100f
        val durationMs = time * 1000L // time in seconds

        // Setup AudioTrack
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            android.media.AudioFormat.CHANNEL_OUT_MONO,
            android.media.AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        Log.d(TAG, "Streaming tone at $frequency Hz")

        Thread {
            val buffer = ShortArray(bufferSize)
            val freqHz = frequency.toDouble()
            val twoPi = 2 * Math.PI
            val phaseIncrement = twoPi * freqHz / sampleRate
            var phase = 0.0
            var samplesGenerated = 0L
            val totalSamples = (sampleRate * time).toLong()

            while (!stopPlayback && samplesGenerated < totalSamples) {
                for (i in buffer.indices) {
                    val sample = (sin(phase) * Short.MAX_VALUE * volume)
                    buffer[i] = sample.toInt().toShort()
                    phase += phaseIncrement
                    if (phase >= twoPi) phase -= twoPi
                }
                audioTrack?.write(buffer, 0, buffer.size)
                samplesGenerated += buffer.size
            }

            handler.post {
                stopTonePlayback()
                isStarted = false
                view?.findViewById<Button>(R.id.btn_start_stop)?.text = "开始"
            }
        }.start()
    }

    private fun stopTonePlayback() {
        stopPlayback = true
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        audioFocusRequest?.let { focusRequest ->
            audioManager?.abandonAudioFocusRequest(focusRequest)
        }
        audioFocusRequest = null
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Tone playback stopped")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        soundPool.release()
        stopTonePlayback()
        Log.d(TAG, "SoundPool and AudioTrack released")
    }
}
