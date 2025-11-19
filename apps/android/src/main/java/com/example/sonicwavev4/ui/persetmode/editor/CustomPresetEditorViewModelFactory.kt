package com.example.sonicwavev4.ui.persetmode.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository
import com.example.sonicwavev4.data.home.HomeHardwareRepository

class CustomPresetEditorViewModelFactory(
    private val repository: CustomPresetRepository,
    private val customerId: Long?,
    private val hardwareRepository: HomeHardwareRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomPresetEditorViewModel::class.java)) {
            return CustomPresetEditorViewModel(repository, customerId, hardwareRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
