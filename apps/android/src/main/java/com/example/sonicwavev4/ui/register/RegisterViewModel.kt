package com.example.sonicwavev4.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.RegisterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * RegisterViewModel keeps registration state and delegates to RegisterRepository.
 */
class RegisterViewModel(
    private val registerRepository: RegisterRepository = RegisterRepository()
) : ViewModel() {

    data class RegisterUiState(
        val isLoading: Boolean = false,
        val success: Boolean = false,
        val errorMessage: String? = null,
        val statusMessage: String? = null
    )

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun sendCode(mobile: String, accountType: String) {
        if (mobile.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "手机号不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, success = false)
            val result = registerRepository.sendCode(mobile.trim(), accountType)
            _uiState.value = result.fold(
                onSuccess = {
                    RegisterUiState(
                        isLoading = false,
                        success = false,
                        errorMessage = null,
                        statusMessage = "验证码已发送"
                    )
                },
                onFailure = {
                    RegisterUiState(
                        isLoading = false,
                        success = false,
                        errorMessage = it.message,
                        statusMessage = null
                    )
                }
            )
        }
    }

    fun register(
        mobile: String,
        code: String,
        password: String,
        accountType: String,
        birthday: String?,
        orgName: String?
    ) {
        if (mobile.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "手机号不能为空")
            return
        }
        if (code.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "验证码不能为空")
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

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, success = false)
            val result = registerRepository.register(
                mobile = mobile.trim(),
                code = code.trim(),
                password = password,
                accountType = accountType,
                birthday = birthday,
                orgName = orgName
            )
            _uiState.value = result.fold(
                onSuccess = {
                    RegisterUiState(
                        isLoading = false,
                        success = true,
                        errorMessage = null,
                        statusMessage = "注册成功"
                    )
                },
                onFailure = {
                    RegisterUiState(
                        isLoading = false,
                        success = false,
                        errorMessage = it.message,
                        statusMessage = null
                    )
                }
            )
        }
    }
}
