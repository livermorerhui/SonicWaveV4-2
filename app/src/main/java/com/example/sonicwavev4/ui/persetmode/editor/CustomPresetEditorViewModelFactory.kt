package com.example.sonicwavev4.ui.persetmode.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.sonicwavev4.data.custompreset.CustomPresetRepository

class CustomPresetEditorViewModelFactory(
    private val repository: CustomPresetRepository,
    private val customerId: Long?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CustomPresetEditorViewModel::class.java)) {
            return CustomPresetEditorViewModel(repository, customerId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
