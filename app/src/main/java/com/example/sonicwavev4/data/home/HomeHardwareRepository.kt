package com.example.sonicwavev4.data.home

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.*
import android.os.Build
import android.util.Log
import cn.wch.ch341lib.CH341Manager
import cn.wch.ch341lib.exception.CH341LibException
import cn.wch.ch347lib.callback.IUsbStateChange
import cn.wch.ch347lib.exception.ChipException
import cn.wch.ch347lib.exception.NoPermissionException
import com.example.sonicwavev4.R
import com.example.sonicwavev4.harddriver.Ad9833Controller
import com.example.sonicwavev4.harddriver.Mcp41010Controller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sin

data class HardwareState(
    val isDeviceOpen: Boolean = false,
    val isAdReady: Boolean = false,
    val isMcpReady: Boolean = false,
    val isHardwareReady: Boolean = false,
    val isTonePlaying: Boolean = false
)

sealed class HardwareEvent {
    data class Toast(val message: String) : HardwareEvent()
    data class Error(val throwable: Throwable) : HardwareEvent()
}

private data class DesiredHardwareState(
    val frequency: Int = 0,
    val intensity: Int = 0,
    val isOutputEnabled: Boolean = false,
    val playTone: Boolean = false
)

class HomeHardwareRepository(
    private val application: Application,
    private val ch341Manager: CH341Manager = CH341Manager.getInstance(),
    private val ad9833Controller: Ad9833Controller = Ad9833Controller(),
    private val mcp41010Controller: Mcp41010Controller = Mcp41010Controller(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val enableTapSound = false
    private val _state = MutableStateFlow(HardwareState())
    val state: StateFlow<HardwareState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HardwareEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<HardwareEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var desiredState = DesiredHardwareState()

    private var usbDevice: UsbDevice? = null
    private var lastAppliedFrequency = Double.NaN
    private var lastAppliedIntensity = -1
    private var lastMode = Ad9833Controller.MODE_BITS_OFF

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioTrack: AudioTrack? = null
    private var toneJob: Job? = null

    private lateinit var soundPool: SoundPool
    private var tapSoundId: Int = 0
    private var isSoundPoolReady = false

    private val usbStateListener = object : IUsbStateChange {
        override fun usbDeviceDetach(device: UsbDevice?) {
            if (device != null && device == usbDevice) {
                scope.launch { emitToast("CH341 已断开"); releaseHardware() }
            }
        }

        override fun usbDeviceAttach(device: UsbDevice?) {
            scope.launch { openDeviceIfNeeded() }
        }

        override fun usbDevicePermission(usbDevice: UsbDevice?, granted: Boolean) {
            if (granted) {
                scope.launch { openDeviceIfNeeded() }
            } else {
                scope.launch { emitToast("USB 权限被拒绝") }
            }
        }
    }

    fun start() {
        initializeAudioResources()
        ch341Manager.setUsbStateListener(usbStateListener)
        scope.launch { openDeviceIfNeeded() }
    }

    suspend fun stop() {
        ch341Manager.setUsbStateListener(emptyUsbStateListener)
        mutex.withLock {
            desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            setOutputModeInternal(false)
            releaseHardware()
            releaseAudioResources()
            ch341Manager.close(application)
        }
        scope.coroutineContext.cancelChildren()
    }

    suspend fun applyFrequency(freq: Int) = mutex.withLock {
        val clamped = freq.coerceAtLeast(0)
        desiredState = desiredState.copy(frequency = clamped)
        applyFrequencyInternal(clamped, force = true)
    }

    suspend fun applyIntensity(intensity: Int) = mutex.withLock {
        val clamped = intensity.coerceIn(0, 255)
        desiredState = desiredState.copy(intensity = clamped)
        applyIntensityInternal(clamped, force = true)
    }

    suspend fun startOutput(
        targetFrequency: Int,
        targetIntensity: Int,
        playTone: Boolean = true
    ): Boolean =
        mutex.withLock {
            desiredState = desiredState.copy(
                frequency = targetFrequency.coerceAtLeast(0),
                intensity = targetIntensity.coerceIn(0, 255)
            )
            if (!state.value.isHardwareReady) {
                emitToast("硬件初始化中，请稍候")
                desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
                return@withLock false
            }
            desiredState = desiredState.copy(isOutputEnabled = true, playTone = playTone)
            applyFrequencyInternal(desiredState.frequency, force = false)
            applyIntensityInternal(desiredState.intensity, force = false)
            val success = setOutputModeInternal(true)
            if (!success) {
                desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            }
            success
        }

    suspend fun stopOutput() = mutex.withLock {
        desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
        setOutputModeInternal(false)
    }

    suspend fun playStandaloneTone(frequency: Int, intensity: Int): Boolean = mutex.withLock {
        val clampedFrequency = frequency.coerceAtLeast(0)
        val clampedIntensity = intensity.coerceIn(0, 255)
        desiredState = desiredState.copy(
            frequency = clampedFrequency,
            intensity = clampedIntensity,
            isOutputEnabled = false,
            playTone = true
        )
        return if (state.value.isHardwareReady) {
            desiredState = desiredState.copy(isOutputEnabled = true)
            applyFrequencyInternal(clampedFrequency, force = false)
            applyIntensityInternal(clampedIntensity, force = false)
            val success = setOutputModeInternal(true)
            if (!success) {
                desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            }
            success
        } else {
            startTonePlayback(clampedFrequency, clampedIntensity)
            true
        }
    }

    suspend fun stopStandaloneTone() = mutex.withLock {
        desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
        if (state.value.isHardwareReady) {
            setOutputModeInternal(false)
        } else {
            stopTonePlayback()
        }
    }

    fun playTapSound() {
        if (enableTapSound && isSoundPoolReady) {
            soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private suspend fun openDeviceIfNeeded() = mutex.withLock {
        if (state.value.isDeviceOpen) return@withLock
        val devices = try {
            ch341Manager.enumDevice()
        } catch (e: CH341LibException) {
            emitToast("枚举设备失败: ${e.message}")
            emitError(e)
            return@withLock
        }

        if (devices.isEmpty()) {
            emitToast("未找到 CH341 设备")
            return@withLock
        }
        if (devices.size > 1) {
            emitToast("只支持连接一个 CH341 设备")
            return@withLock
        }
        val target = devices[0]
        try {
            if (ch341Manager.hasPermission(target)) {
                if (!ch341Manager.openDevice(target)) {
                    emitToast("打开 CH341 设备失败")
                    return@withLock
                }
                onDeviceOpened(target)
            } else {
                ch341Manager.requestPermission(application, target)
            }
        } catch (e: CH341LibException) {
            emitToast("操作 CH341 异常: ${e.message}")
            emitError(e)
        } catch (e: NoPermissionException) {
            emitToast("无 USB 权限")
            emitError(e)
        } catch (e: ChipException) {
            emitToast("芯片错误: ${e.message}")
            emitError(e)
        }
    }

    private suspend fun onDeviceOpened(device: UsbDevice) {
        usbDevice = device
        _state.update { it.copy(isDeviceOpen = true) }
        initializeMcp41010Hardware()
        initializeAd9833Hardware()
    }

    private suspend fun initializeAd9833Hardware() {
        try {
            val device = usbDevice ?: return
            ad9833Controller.attachDevice(device)
            ad9833Controller.setCsChannel(0)
            ad9833Controller.initializeIdleState()
            lastAppliedFrequency = Double.NaN
            lastMode = Ad9833Controller.MODE_BITS_OFF
            var becameReady = false
            _state.update { current ->
                val newAdReady = true
                val newMcpReady = current.isMcpReady
                val newReady = newAdReady && newMcpReady
                if (!current.isHardwareReady && newReady) {
                    becameReady = true
                }
                current.copy(
                    isAdReady = newAdReady,
                    isHardwareReady = newReady
                )
            }
            if (becameReady) {
                desiredState = DesiredHardwareState()
            }
            emitToast("AD9833 已初始化")
        } catch (e: CH341LibException) {
            _state.update { it.copy(isAdReady = false, isHardwareReady = false) }
            emitToast("初始化 AD9833 失败: ${e.message}")
            emitError(e)
        }
    }

    private suspend fun initializeMcp41010Hardware() {
        try {
            val device = usbDevice ?: return
            mcp41010Controller.attachDevice(device)
            mcp41010Controller.setCsChannel(1)
            mcp41010Controller.writeValue(0)
            lastAppliedIntensity = 0
            var becameReady = false
            _state.update { current ->
                val newMcpReady = true
                val newAdReady = current.isAdReady
                val newReady = newAdReady && newMcpReady
                if (!current.isHardwareReady && newReady) {
                    becameReady = true
                }
                current.copy(
                    isMcpReady = newMcpReady,
                    isHardwareReady = newReady
                )
            }
            if (becameReady) {
                desiredState = DesiredHardwareState()
            }
            emitToast("MCP41010 已初始化")
        } catch (e: CH341LibException) {
            _state.update { it.copy(isMcpReady = false, isHardwareReady = false) }
            emitToast("初始化 MCP41010 失败: ${e.message}")
            emitError(e)
        }
    }

    private suspend fun releaseHardware() {
        val device = usbDevice
        if (device != null && state.value.isDeviceOpen) {
            try {
                ch341Manager.closeDevice(device)
            } catch (ignored: Exception) {
                Log.w("HomeHardwareRepo", "closeDevice failed", ignored)
            }
        }
        usbDevice = null
        try {
            ad9833Controller.detach()
        } catch (ignored: Exception) {
        }
        try {
            mcp41010Controller.detach()
        } catch (ignored: Exception) {
        }
        lastAppliedFrequency = Double.NaN
        lastAppliedIntensity = -1
        lastMode = Ad9833Controller.MODE_BITS_OFF
        stopTonePlayback()
        _state.update { HardwareState() }
        desiredState = DesiredHardwareState()
    }

    private fun startTonePlayback(frequency: Int, intensity: Int) {
        stopTonePlayback()
        val currentVolume = if (intensity > 100) 1f else intensity / 100f
        toneJob = scope.launch(Dispatchers.Default) {
            ensureAudioFocus()
            val sampleRate = 44100
            val totalSeconds = 60 // tone runs until stopOutput is invoked
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()

            val samplesPerCycle = if (frequency <= 0) 1.0 else sampleRate.toDouble() / frequency
            val waveBuffer = ShortArray(samplesPerCycle.toInt().coerceAtLeast(1))
            for (i in waveBuffer.indices) {
                waveBuffer[i] =
                    (sin(2 * Math.PI * i / samplesPerCycle) * Short.MAX_VALUE * currentVolume).toInt().toShort()
            }

            _state.update { it.copy(isTonePlaying = true) }
            var isPlaying = true
            while (isActive && isPlaying) {
                val result = audioTrack?.write(waveBuffer, 0, waveBuffer.size) ?: -1
                if (result <= 0) {
                    isPlaying = false
                }
            }
            _state.update { it.copy(isTonePlaying = false) }
        }
    }

    private fun stopTonePlayback() {
        toneJob?.cancel()
        toneJob = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
        audioFocusRequest = null
        _state.update { it.copy(isTonePlaying = false) }
    }

    private suspend fun applyFrequencyInternal(value: Int, force: Boolean) {
        if (!state.value.isHardwareReady) return
        val freqDouble = value.toDouble()
        if (!force && freqDouble == lastAppliedFrequency) return
        try {
            ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, freqDouble)
            ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0)
            lastAppliedFrequency = freqDouble
        } catch (e: CH341LibException) {
            emitToast("设置频率失败: ${e.message}")
            emitError(e)
        }
        if (!desiredState.isOutputEnabled) {
            forceModeOff()
        }
    }

    private suspend fun applyIntensityInternal(value: Int, force: Boolean) {
        if (!state.value.isHardwareReady) return
        if (!force && value == lastAppliedIntensity) return
        try {
            mcp41010Controller.writeValue(value)
            lastAppliedIntensity = value
        } catch (e: CH341LibException) {
            emitToast("设置幅度失败: ${e.message}")
            emitError(e)
        }
    }

    private suspend fun setOutputModeInternal(enableSine: Boolean): Boolean {
        if (!state.value.isHardwareReady) return false
        return if (enableSine) {
            try {
                if (lastMode != Ad9833Controller.MODE_BITS_OFF) {
                    ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
                    lastMode = Ad9833Controller.MODE_BITS_OFF
                    delay(5)
                }
                ad9833Controller.setMode(Ad9833Controller.MODE_BITS_SINE)
                lastMode = Ad9833Controller.MODE_BITS_SINE
                if (desiredState.playTone) {
                    startTonePlayback(desiredState.frequency, desiredState.intensity)
                } else {
                    stopTonePlayback()
                }
                true
            } catch (e: CH341LibException) {
                emitToast("启动输出失败: ${e.message}")
                emitError(e)
                false
            }
        } else {
            try {
                if (lastMode != Ad9833Controller.MODE_BITS_OFF) {
                    ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
                    lastMode = Ad9833Controller.MODE_BITS_OFF
                }
                ad9833Controller.setMode(Ad9833Controller.MODE_BITS_SINE)
                ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
                lastMode = Ad9833Controller.MODE_BITS_OFF
                stopTonePlayback()
                true
            } catch (e: CH341LibException) {
                emitToast("停止输出失败: ${e.message}")
                emitError(e)
                false
            }
        }
    }

    private suspend fun forceModeOff() {
        if (!state.value.isHardwareReady) return
        try {
            ad9833Controller.setMode(Ad9833Controller.MODE_BITS_OFF)
            lastMode = Ad9833Controller.MODE_BITS_OFF
        } catch (e: CH341LibException) {
            emitToast("设置停机模式失败: ${e.message}")
            emitError(e)
        }
    }

    private fun initializeAudioResources() {
        audioManager = application.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!enableTapSound) {
            isSoundPoolReady = false
            return
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build()
        tapSoundId = soundPool.load(application, R.raw.tap_sound, 1)
        soundPool.setOnLoadCompleteListener { _, _, status ->
            isSoundPoolReady = status == 0
        }
    }

    private fun releaseAudioResources() {
        stopTonePlayback()
        if (enableTapSound && ::soundPool.isInitialized) {
            soundPool.release()
        }
        isSoundPoolReady = false
    }

    private fun ensureAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        scope.launch { stopOutput() }
                    }
                }
                .build()
            val requestResult = audioManager?.requestAudioFocus(audioFocusRequest!!)
            if (requestResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w("HomeHardwareRepo", "Audio focus request failed, continuing playback.")
            }
        } else {
            @Suppress("DEPRECATION")
            if (audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                Log.w("HomeHardwareRepo", "Legacy audio focus request failed, continuing playback.")
            }
        }
    }

    private suspend fun emitToast(message: String) {
        _events.emit(HardwareEvent.Toast(message))
    }

    private suspend fun emitError(throwable: Throwable) {
        _events.emit(HardwareEvent.Error(throwable))
    }

    private val emptyUsbStateListener = object : IUsbStateChange {
        override fun usbDeviceDetach(device: UsbDevice?) {}
        override fun usbDeviceAttach(device: UsbDevice?) {}
        override fun usbDevicePermission(usbDevice: UsbDevice?, granted: Boolean) {}
    }
}
