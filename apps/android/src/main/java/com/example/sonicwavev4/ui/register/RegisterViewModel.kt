package com.example.sonicwavev4.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.RegisterRepository
import com.example.sonicwavev4.repository.RegisterResult
import com.example.sonicwavev4.utils.PasswordValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        val needSmsInput: Boolean = true,
        val registrationMode: String? = null,
        val partnerRegistered: Boolean? = null,
        val selfRegistered: Boolean? = null,
        val selfBound: Boolean? = null,
        val humedsBindStatus: String? = null,
        val humedsErrorCode: String? = null,
        val humedsErrorMessage: String? = null,
        val sendCodeCooldownSeconds: Int = 0,
        val flowHint: String? = null,
    )

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    private val _birthday = MutableStateFlow<String?>(null)
    val birthday: StateFlow<String?> = _birthday.asStateFlow()
    private var sendCodeCooldownJob: Job? = null

    fun onBirthdaySelected(birthday: String) {
        _birthday.value = birthday
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    private fun startSendCodeCooldown(totalSeconds: Int = 60) {
        sendCodeCooldownJob?.cancel()
        _uiState.update { it.copy(sendCodeCooldownSeconds = totalSeconds) }

        sendCodeCooldownJob = viewModelScope.launch {
            for (sec in totalSeconds - 1 downTo 1) {
                delay(1000)
                _uiState.update { it.copy(sendCodeCooldownSeconds = sec) }
            }
            delay(1000)
            _uiState.update { it.copy(sendCodeCooldownSeconds = 0) }
        }
    }

    fun sendCode(mobile: String, accountType: String) {
        if (mobile.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "手机号不能为空")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null, codeSent = false) }
            when (val result = registerRepository.sendCode(mobile.trim(), accountType)) {
                is RegisterResult.Success -> {
                    val status = result.sendCodeStatus
                    val needSms = status?.needSmsInput ?: true
                    val mode = status?.registrationMode
                    val flowHint = when {
                        !needSms -> "当前无需短信验证码，可直接注册"
                        mode?.contains("LOCAL_ONLY") == true -> "将仅注册本应用账号（不绑定 Humeds）"
                        mode?.contains("EXISTING") == true -> "检测到 Humeds 已存在账号，注册后将尝试自动绑定"
                        mode?.contains("NEW") == true -> "Humeds 未检测到账号（仅供参考），注册后将尝试绑定"
                        else -> "注册后将尝试绑定 Humeds（如失败可登录后重试）"
                    }
                    val smsMessage = if (needSms) "验证码已发送" else "当前无需验证码，可直接注册"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = null,
                            statusMessage = smsMessage,
                            codeSent = true,
                            needSmsInput = needSms,
                            registrationMode = status?.registrationMode,
                            partnerRegistered = status?.partnerRegistered,
                            selfRegistered = status?.selfRegistered,
                            selfBound = status?.selfBound,
                            flowHint = flowHint,
                        )
                    }
                    if (needSms) {
                        startSendCodeCooldown(60)
                    } else {
                        sendCodeCooldownJob?.cancel()
                        _uiState.update { it.copy(sendCodeCooldownSeconds = 0) }
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
                            sendCodeCooldownSeconds = 0,
                            flowHint = null,
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
                            sendCodeCooldownSeconds = 0,
                            flowHint = null,
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

        val validation = PasswordValidator.validate(password)
        if (!validation.isValid) {
            _uiState.value = RegisterUiState(
                errorMessage = validation.message ?: "密码至少 6 位，且需包含数字、字母、符号中至少两种"
            )
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

        if (_uiState.value.needSmsInput && code.isBlank()) {
            _uiState.value = RegisterUiState(errorMessage = "验证码不能为空，请先获取验证码")
            return
        }

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
                is RegisterResult.Success -> {
                    val submit = result.submitStatus
                    val humedsStatus = submit?.humedsBindStatus
                    val statusMessage = when (humedsStatus) {
                        "success" -> "注册成功，Humeds 已绑定"
                        "failed" -> "注册成功，Humeds 绑定失败：" + (submit?.humedsErrorMessage ?: submit?.humedsErrorCode
                            ?: "unknown")
                        "skipped" -> "注册成功，已跳过 Humeds 绑定"
                        else -> "注册成功"
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = true,
                            errorMessage = null,
                            statusMessage = statusMessage,
                            humedsBindStatus = humedsStatus,
                            humedsErrorCode = submit?.humedsErrorCode,
                            humedsErrorMessage = submit?.humedsErrorMessage,
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

    override fun onCleared() {
        super.onCleared()
        sendCodeCooldownJob?.cancel()
    }
}
