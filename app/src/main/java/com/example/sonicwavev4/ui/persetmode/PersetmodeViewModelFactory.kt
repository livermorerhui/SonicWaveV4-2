package com.example.sonicwavev4.ui.persetmode

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository
import com.example.sonicwavev4.data.home.HomeHardwareRepository
import com.example.sonicwavev4.data.home.HomeSessionRepository

class PersetmodeViewModelFactory(
    private val application: Application,
    private val hardwareRepository: HomeHardwareRepository,
    private val sessionRepository: HomeSessionRepository,
    private val customPresetRepository: CustomPresetRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersetmodeViewModel::class.java)) {
            return PersetmodeViewModel(
                application,
                hardwareRepository,
                sessionRepository,
                customPresetRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
