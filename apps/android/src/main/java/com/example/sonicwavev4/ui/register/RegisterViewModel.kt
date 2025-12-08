package com.example.sonicwavev4.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.RegisterRepository
import com.example.sonicwavev4.repository.RegisterResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RegisterViewModel keeps registration state and delegates to RegisterRepository.
 */
class RegisterViewModel(
    private val registerRepository: RegisterRepository = RegisterRepository(),
) : ViewModel() {

    data class RegisterUiState(
        val isLoading: Boolean = false,
        val success: Boolean = false,
        val errorMessage: String? = null,
        val statusMessage: String? = null,
        val codeSent: Boolean = false,
    )

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    private val _birthday = MutableStateFlow<String?>(null)
    val birthday: StateFlow<String?> = _birthday.asStateFlow()

    fun onBirthdaySelected(birthday: String) {
        _birthday.value = birthday
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun sendCode(mobile: String, accountType: String) {
        if (mobile.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "手机号不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null, codeSent = false) }
            when (val result = registerRepository.sendCode(mobile.trim(), accountType)) {
                RegisterResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = null,
                            statusMessage = "验证码已发送",
                            codeSent = true,
                        )
                    }
                }
                is RegisterResult.BusinessError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = result.message,
                            statusMessage = null,
                            codeSent = false,
                        )
                    }
                }
                is RegisterResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = result.message,
                            statusMessage = null,
                            codeSent = false,
                        )
                    }
                }
            }
        }
    }

    fun register(
        mobile: String,
        code: String,
        password: String,
        accountType: String,
        orgName: String?,
    ) {
        val birthday = _birthday.value
        if (mobile.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "手机号不能为空")
            return
        }
        if (password.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "密码不能为空")
            return
        }
        if (accountType == "personal" && birthday.isNullOrBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "请选择出生日期")
            return
        }
        if (accountType == "org" && orgName.isNullOrBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "请输入机构名称")
            return
        }

        // 当前版本不强制验证码，保留参数以便未来重新启用短信验证
        // TODO: 接入真实短信验证码后恢复对 code 的校验
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            when (
                val result = registerRepository.register(
                    mobile = mobile.trim(),
                    code = code.trim(),
                    password = password,
                    accountType = accountType,
                    birthday = birthday,
                    orgName = orgName,
                )
            ) {
                RegisterResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = true,
                            errorMessage = null,
                            statusMessage = "注册成功",
                        )
                    }
                }
                is RegisterResult.BusinessError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = result.message,
                            statusMessage = null,
                        )
                    }
                }
                is RegisterResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = result.message,
                            statusMessage = null,
                        )
                    }
                }
            }
        }
    }
}
