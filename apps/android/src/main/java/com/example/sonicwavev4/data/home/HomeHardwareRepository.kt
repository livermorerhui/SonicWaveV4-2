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
import com.example.sonicwavev4.core.vibration.ParameterRampPlanner
import com.example.sonicwavev4.core.vibration.ParameterTransitionSpec
import com.example.sonicwavev4.harddriver.Ad9833Controller
import com.example.sonicwavev4.harddriver.Mcp41010Controller
import com.example.sonicwavev4.core.vibration.VibrationHardwareGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
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
) : VibrationHardwareGateway {

    companion object {
        @Volatile
        private var instance: HomeHardwareRepository? = null

        fun getInstance(application: Application): HomeHardwareRepository {
            return instance ?: synchronized(this) {
                instance ?: HomeHardwareRepository(application).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val enableTapSound = false
    private val _state = MutableStateFlow(HardwareState())
    override val state: StateFlow<HardwareState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HardwareEvent>(extraBufferCapacity = 16)
    override val events: SharedFlow<HardwareEvent> = _events.asSharedFlow()

    private val mutex = Mutex()
    private var desiredState = DesiredHardwareState()
    private var transitionJob: Job? = null

    private var usbDevice: UsbDevice? = null
    private var lastAppliedFrequency = Double.NaN
    private var lastAppliedIntensity = -1
    private var lastAppliedHardwareIntensity = -1
    private var lastMode = Ad9833Controller.MODE_BITS_OFF

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioTrack: AudioTrack? = null
    private var toneJob: Job? = null
    @Volatile
    private var softwareToneFrequency = 0
    @Volatile
    private var softwareToneIntensity = 0
    private var hasAudioFocus = false

    private lateinit var soundPool: SoundPool
    private var tapSoundId: Int = 0
    private var isSoundPoolReady = false

    private enum class WriteResult {
        SKIPPED_NO_CHANGE,
        SUCCESS,
        FAILURE
    }

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

    override fun start() {
        initializeAudioResources()
        ch341Manager.setUsbStateListener(usbStateListener)
        scope.launch { openDeviceIfNeeded() }
    }

    override suspend fun stop() {
        transitionJob?.cancel()
        transitionJob = null
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

    override suspend fun applyFrequency(freq: Int) = mutex.withLock {
        val clamped = freq.coerceAtLeast(0)
        desiredState = desiredState.copy(frequency = clamped)
        applyFrequencyInternal(clamped, force = false)
        logOutputState("applyFrequency($clamped)")
    }

    override suspend fun applyIntensity(intensity: Int) = mutex.withLock {
        val clamped = intensity.coerceIn(0, 255)
        desiredState = desiredState.copy(intensity = clamped)
        applyIntensityInternal(clamped, force = false)
        logOutputState("applyIntensity($clamped)")
    }

    override suspend fun startOutput(
        targetFrequency: Int,
        targetIntensity: Int,
        playTone: Boolean
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
            logOutputState(if (success) "startOutput-success" else "startOutput-failure")
            if (!success) {
                desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            }
            success
        }

    override suspend fun stopOutput() {
        transitionJob?.cancel()
        transitionJob = null
        mutex.withLock {
            desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            setOutputModeInternal(false)
            logOutputState("stopOutput")
        }
    }

    override suspend fun playStandaloneTone(frequency: Int, intensity: Int): Boolean = mutex.withLock {
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
            logOutputState(if (success) "playStandaloneTone-success" else "playStandaloneTone-failure")
            if (!success) {
                desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            }
            success
        } else {
            startTonePlayback(clampedFrequency, clampedIntensity)
            logOutputState("playStandaloneTone-software")
            true
        }
    }

    override suspend fun stopStandaloneTone() {
        transitionJob?.cancel()
        transitionJob = null
        mutex.withLock {
            desiredState = desiredState.copy(isOutputEnabled = false, playTone = false)
            if (state.value.isHardwareReady) {
                setOutputModeInternal(false)
            } else {
                stopTonePlayback()
            }
            logOutputState("stopStandaloneTone")
        }
    }

    override fun playTapSound() {
        if (enableTapSound && isSoundPoolReady) {
            soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    override suspend fun transitionTo(
        targetFrequency: Int,
        targetIntensity: Int,
        spec: ParameterTransitionSpec
    ) {
        transitionJob?.cancelAndJoin()

        val (startFreq, startIntensity) = mutex.withLock {
            val sf = if (!lastAppliedFrequency.isNaN()) lastAppliedFrequency.toInt() else desiredState.frequency
            val si = if (lastAppliedIntensity >= 0) lastAppliedIntensity else desiredState.intensity
            sf to si
        }

        val plan = ParameterRampPlanner.plan(startFreq, startIntensity, targetFrequency, targetIntensity, spec)

        transitionJob = scope.launch {
            var lastSentPoint: Pair<Int, Int>? = null
            var consecutiveFailures = 0

            for (point in plan.points) {
                if (!isActive) break

                if (lastSentPoint != null && point == lastSentPoint) {
                    delay(plan.tickMs.toLong())
                    continue
                }

                val tickStartNs = System.nanoTime()

                val (resultF, resultI, shouldStop) = mutex.withLock {
                    if (!desiredState.isOutputEnabled && !desiredState.playTone) {
                        Triple(WriteResult.SKIPPED_NO_CHANGE, WriteResult.SKIPPED_NO_CHANGE, true)
                    } else {
                        desiredState = desiredState.copy(frequency = point.first, intensity = point.second)
                        val rf = applyFrequencyInternal(point.first, force = false)
                        val ri = applyIntensityInternal(point.second, force = false)
                        Triple(rf, ri, false)
                    }
                }

                if (shouldStop) break

                lastSentPoint = point

                val attemptedAnyWrite =
                    (resultF != WriteResult.SKIPPED_NO_CHANGE) || (resultI != WriteResult.SKIPPED_NO_CHANGE)
                val anyFailure = (resultF == WriteResult.FAILURE) || (resultI == WriteResult.FAILURE)

                if (anyFailure) {
                    consecutiveFailures++
                    if (consecutiveFailures > 3) {
                        Log.w("HomeHardwareRepository", "Ramp aborted after $consecutiveFailures consecutive failures")
                        break
                    }
                } else if (attemptedAnyWrite) {
                    consecutiveFailures = 0
                }

                val elapsedMs = (System.nanoTime() - tickStartNs) / 1_000_000
                if (elapsedMs > plan.tickMs * 0.8) {
                    Log.w(
                        "HomeHardwareRepository",
                        "Ramp tick took ${elapsedMs}ms (>80% of ${plan.tickMs}ms), consider increasing tickMs"
                    )
                }

                delay(plan.tickMs.toLong())
            }
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

    // ----  AD9833初始化  ----
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

    // ----  MCP41010初始化  ----
    private suspend fun initializeMcp41010Hardware() {
        try {
            val device = usbDevice ?: return
            mcp41010Controller.attachDevice(device)
            mcp41010Controller.setCsChannel(1)
            mcp41010Controller.writeValue(0)
            lastAppliedIntensity = 0
            lastAppliedHardwareIntensity = 0
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
        lastAppliedHardwareIntensity = -1
        lastMode = Ad9833Controller.MODE_BITS_OFF
        stopTonePlayback()
        _state.update { HardwareState() }
        desiredState = DesiredHardwareState()
    }

    private fun startTonePlayback(frequency: Int, intensity: Int) {
        softwareToneFrequency = frequency.coerceAtLeast(0)
        softwareToneIntensity = intensity.coerceIn(0, 255)
        if (toneJob?.isActive == true && audioTrack != null) {
            return
        }
        ensureAudioFocus()
        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        if (audioTrack == null) {
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
        }
        if (toneJob?.isActive == true) return
        toneJob = scope.launch(Dispatchers.Default) {
            val buffer = ShortArray(minBufferSize)
            var phase = 0.0
            val twoPi = 2 * Math.PI
            _state.update { it.copy(isTonePlaying = true) }
            try {
                while (isActive) {
                    val freq = softwareToneFrequency
                    val intensityLocal = softwareToneIntensity
                    val phaseIncrement = if (freq <= 0) 0.0 else twoPi * freq / sampleRate
                    val volume = if (intensityLocal > 100) 1f else intensityLocal / 100f
                    if (phaseIncrement == 0.0 || volume == 0f) {
                        buffer.fill(0)
                    } else {
                        for (i in buffer.indices) {
                            val sample = (sin(phase) * Short.MAX_VALUE * volume)
                            buffer[i] = sample.toInt().toShort()
                            phase += phaseIncrement
                            if (phase >= twoPi) {
                                phase -= twoPi
                            }
                        }
                    }
                    val track = audioTrack
                    val result = try {
                        track?.write(buffer, 0, buffer.size) ?: -1
                    } catch (e: IllegalStateException) {
                        break
                    }
                    if (result <= 0) break
                }
            } finally {
                _state.update { it.copy(isTonePlaying = false) }
            }
        }
    }

    private fun stopTonePlayback(releaseAudioFocus: Boolean = true) {
        val job = toneJob
        toneJob = null
        scope.launch(Dispatchers.Default) {
            job?.cancelAndJoin()
            val track = audioTrack
            audioTrack = null
            if (track != null) {
                try {
                    track.stop()
                } catch (_: IllegalStateException) {
                    // Ignored: already stopped/released.
                }
                try {
                    track.release()
                } catch (_: IllegalStateException) {
                    // Ignored: already released.
                }
            }
            if (releaseAudioFocus && hasAudioFocus) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager?.abandonAudioFocus(null)
                }
                audioFocusRequest = null
                hasAudioFocus = false
            }
            _state.update { it.copy(isTonePlaying = false) }
        }
    }

    private suspend fun applyFrequencyInternal(value: Int, force: Boolean): WriteResult {
        val hardwareReady = state.value.isHardwareReady
        val freqDouble = value.toDouble()
        val shouldRefreshTone = desiredState.playTone && (!hardwareReady || force || freqDouble != lastAppliedFrequency)
        var result = WriteResult.SKIPPED_NO_CHANGE

        if (hardwareReady && (force || freqDouble != lastAppliedFrequency)) {
            result = try {
                ad9833Controller.setFrequency(Ad9833Controller.CHANNEL_0, freqDouble)
                ad9833Controller.setActiveFrequency(Ad9833Controller.CHANNEL_0)
                lastAppliedFrequency = freqDouble
                if (!desiredState.isOutputEnabled) {
                    forceModeOff()
                }
                WriteResult.SUCCESS
            } catch (e: CH341LibException) {
                emitToast("设置频率失败: ${e.message}")
                emitError(e)
                WriteResult.FAILURE
            }
        }

        if (!desiredState.playTone) {
            stopTonePlayback()
        } else if (shouldRefreshTone) {
            startTonePlayback(desiredState.frequency, desiredState.intensity)
        }

        return result
    }

    private suspend fun applyIntensityInternal(value: Int, force: Boolean): WriteResult {
        val hardwareReady = state.value.isHardwareReady
        val hardwareValue = (value / 1).coerceIn(0, 255) // UI 显示值为发送值的 1 倍
        val shouldRefreshTone = desiredState.playTone && (!hardwareReady || force || value != lastAppliedIntensity)
        var result = WriteResult.SKIPPED_NO_CHANGE

        if (hardwareReady && (force || hardwareValue != lastAppliedHardwareIntensity)) {
            result = try {
                mcp41010Controller.writeValue(hardwareValue)
                lastAppliedHardwareIntensity = hardwareValue
                WriteResult.SUCCESS
            } catch (e: CH341LibException) {
                emitToast("设置幅度失败: ${e.message}")
                emitError(e)
                WriteResult.FAILURE
            }
        }

        if (force || value != lastAppliedIntensity) {
            lastAppliedIntensity = value
        }

        if (!desiredState.playTone) {
            stopTonePlayback()
        } else if (shouldRefreshTone) {
            startTonePlayback(desiredState.frequency, desiredState.intensity)
        }

        return result
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

    private fun logOutputState(action: String) {
        Log.d(
            "HomeHardwareRepo",
            "[$action] desired={enabled=${desiredState.isOutputEnabled}, playTone=${desiredState.playTone}, freq=${desiredState.frequency}, intensity=${desiredState.intensity}} " +
                "hardwareReady=${state.value.isHardwareReady} tonePlaying=${state.value.isTonePlaying} lastMode=$lastMode"
        )
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
        if (hasAudioFocus) return
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
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
            }
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = granted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocus) {
            Log.w("HomeHardwareRepo", "Audio focus request failed, continuing playback.")
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
