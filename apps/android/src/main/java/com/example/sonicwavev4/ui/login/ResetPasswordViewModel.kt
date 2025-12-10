package com.example.sonicwavev4.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.PasswordResetRepository
import com.example.sonicwavev4.repository.PasswordResetResult
import com.example.sonicwavev4.utils.PasswordValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    private val repository: PasswordResetRepository = PasswordResetRepository(),
) : ViewModel() {

    data class ResetPasswordUiState(
        val isLoading: Boolean = false,
        val codeSent: Boolean = false,
        val statusMessage: String? = null,
        val errorMessage: String? = null,
        val resetSuccess: Boolean = false,
    )

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun sendCode(mobile: String) {
        if (mobile.isBlank()) {
            _uiState.update { it.copy(errorMessage = "手机号不能为空", statusMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    codeSent = false,
                    statusMessage = null,
                    errorMessage = null,
                    resetSuccess = false,
                )
            }
            when (val result = repository.sendCode(mobile.trim())) {
                PasswordResetResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            codeSent = true,
                            statusMessage = "验证码已发送，请查看服务器日志",
                            errorMessage = null,
                        )
                    }
                }
                is PasswordResetResult.BusinessError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            codeSent = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
                is PasswordResetResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            codeSent = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    fun resetPassword(mobile: String, code: String, newPassword: String) {
        if (mobile.isBlank()) {
            _uiState.update { it.copy(errorMessage = "手机号不能为空", statusMessage = null) }
            return
        }
        if (code.isBlank()) {
            _uiState.update { it.copy(errorMessage = "验证码不能为空", statusMessage = null) }
            return
        }
        if (newPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "新密码不能为空", statusMessage = null) }
            return
        }
        val validation = PasswordValidator.validate(newPassword)
        if (!validation.isValid) {
            _uiState.update {
                it.copy(
                    errorMessage = validation.message ?: "密码至少 6 位，且需包含数字、字母、符号中至少两种",
                    statusMessage = null
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    resetSuccess = false,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
            when (val result = repository.resetPassword(mobile.trim(), code.trim(), newPassword)) {
                PasswordResetResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetSuccess = true,
                            statusMessage = "密码重置成功，请用新密码登录",
                            errorMessage = null,
                        )
                    }
                }
                is PasswordResetResult.BusinessError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetSuccess = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
                is PasswordResetResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetSuccess = false,
                            statusMessage = null,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }
}
