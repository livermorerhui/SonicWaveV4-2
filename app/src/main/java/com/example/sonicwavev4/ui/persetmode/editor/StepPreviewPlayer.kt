package com.example.sonicwavev4.ui.persetmode.editor

import android.util.Log
import com.example.sonicwavev4.data.custompreset.model.CustomPresetStep

/**
 * Lightweight placeholder player for single-step preview.
 * TODO: Wire to real hardware playback APIs when available.
 */
class StepPreviewPlayer {
    private var isPlaying = false
    private var currentStep: CustomPresetStep? = null

    fun start(step: CustomPresetStep) {
        currentStep = step
        isPlaying = true
        Log.d("StepPreviewPlayer", "Start step freq=${step.frequencyHz} intensity=${step.intensity01V} duration=${step.durationSec}")
    }

    fun stop() {
        if (isPlaying) {
            Log.d("StepPreviewPlayer", "Stop step")
        }
        isPlaying = false
        currentStep = null
    }

    fun updateFrequency(frequencyHz: Int) {
        if (!isPlaying) return
        Log.d("StepPreviewPlayer", "Update frequency to $frequencyHz")
    }

    fun updateIntensity(intensity: Int) {
        if (!isPlaying) return
        Log.d("StepPreviewPlayer", "Update intensity to $intensity")
    }
}
