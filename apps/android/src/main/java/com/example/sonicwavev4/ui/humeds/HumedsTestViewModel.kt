package com.example.sonicwavev4.ui.humeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.HumedsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HumedsTestUiState(
    val mobile: String = "",
    val password: String = "",
    val regionCode: String = "86",
    val isLoading: Boolean = false,
    val token: String? = null,
    val rawText: String? = null,
    val errorMessage: String? = null,
)

class HumedsTestViewModel(
    private val repository: HumedsRepository = HumedsRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HumedsTestUiState())
    val uiState: StateFlow<HumedsTestUiState> = _uiState.asStateFlow()

    fun updateMobile(value: String) {
        _uiState.value = _uiState.value.copy(mobile = value)
    }

    fun updatePassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun updateRegionCode(value: String) {
        _uiState.value = _uiState.value.copy(regionCode = value)
    }

    fun submit() {
        val current = _uiState.value
        if (current.mobile.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(
                errorMessage = "手机号和密码不能为空",
            )
            return
        }

        _uiState.value = current.copy(
            isLoading = true,
            errorMessage = null,
            token = null,
            rawText = null,
        )

        viewModelScope.launch {
            val result = repository.testLogin(
                mobile = current.mobile.trim(),
                password = current.password,
                regionCode = current.regionCode.trim().ifBlank { "86" },
            )

            result
                .onSuccess { data ->
                    val rawText = data.raw?.toString()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        token = data.token_jwt,
                        rawText = rawText,
                        errorMessage = null,
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "请求失败",
                        token = null,
                        rawText = null,
                    )
                }
        }
    }
}
