package com.example.sonicwavev4.ui.common

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class ShowError(val throwable: Throwable) : UiEvent()
}
