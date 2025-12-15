package com.example.sonicwavev4.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.repository.HumedsBindRepairRepository
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
        val registeredUserId: Long? = null,
        val registeredMobile: String? = null,
    )

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    private val _birthday = MutableStateFlow<String?>(null)
    val birthday: StateFlow<String?> = _birthday.asStateFlow()
    private var sendCodeCooldownJob: Job? = null
    private val humedsBindRepairRepository: HumedsBindRepairRepository = HumedsBindRepairRepository()

    fun onBirthdaySelected(birthday: String) {
        _birthday.value = birthday
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun onMobileChanged() {
        sendCodeCooldownJob?.cancel()
        _uiState.update {
            it.copy(
                // 清理 send_code 相关状态
                codeSent = false,
                needSmsInput = true,
                sendCodeCooldownSeconds = 0,
                registrationMode = null,
                partnerRegistered = null,
                selfRegistered = null,
                selfBound = null,
                flowHint = null,
                // 同时清理提示，避免频繁 toast
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    private fun buildFlowHintForSendCode(
        registrationMode: String?,
        partnerRegistered: Boolean?,
        needSmsInput: Boolean,
    ): String? {
        if (!needSmsInput) return null
        // 统一提示：本应用验证码与 Humeds 验证码是两套体系
        val smsLine = if (needSmsInput) {
            "验证码用于本应用注册（不等同于 Humeds 验证码）。"
        } else {
            "当前无需验证码，可直接注册。"
        }

        val mode = (registrationMode ?: "").uppercase()
        val modeLine = when {
            mode.contains("LOCAL_ONLY") -> "本次仅注册本应用账号，不会绑定 Humeds。"
            mode.contains("HUMEDS_EXISTING") -> "检测到 Humeds 已存在账号：注册后将尝试自动绑定。若密码不一致，可在后续弹窗中输入 Humeds 密码进行绑定。"
            mode.contains("HUMEDS_NEW") -> "Humeds 未检测到账号：注册可先完成本应用账号创建；后续上线 Humeds 验证码流程后可自动开通并绑定。"
            mode.contains("HUMEDS_UNKNOWN") -> "暂时无法确认 Humeds 账号状态：注册后将尝试绑定；如失败可跳过或在弹窗中输入 Humeds 密码修复绑定。"
            else -> {
                when (partnerRegistered) {
                    true -> "检测到 Humeds 已存在账号：注册后将尝试自动绑定。"
                    false -> "Humeds 未检测到账号：注册后将提示下一步绑定方式。"
                    null -> "暂时无法确认 Humeds 账号状态：注册后将提示下一步绑定方式。"
                }
            }
        }

        return listOf(modeLine, smsLine).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun buildFlowHintForSubmit(
        humedsBindStatus: String?,
        humedsErrorCode: String?,
        humedsErrorMessage: String?,
    ): String? {
        val st = (humedsBindStatus ?: "").lowercase()
        return when (st) {
            "success" -> "Humeds 已绑定成功。"
            "skipped" -> "已完成本应用注册。\nHumeds 绑定已跳过（可稍后在弹窗中选择绑定）。"
            "failed" -> {
                val reason = humedsErrorMessage ?: humedsErrorCode ?: "未知原因"
                "已完成本应用注册。\nHumeds 绑定失败：$reason\n可在弹窗中输入 Humeds 密码进行绑定。"
            }
            else -> null
        }
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
                is RegisterResult.SendCodeSuccess -> {
                    val resp = result.resp
                    val data = resp.data
                    val needSms = data?.needSmsInput ?: true
                    val flowHint = buildFlowHintForSendCode(
                        registrationMode = data?.registrationMode,
                        partnerRegistered = data?.partnerRegistered,
                        needSmsInput = needSms
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = false,
                            errorMessage = null,
                            statusMessage = if (needSms) "验证码已发送" else "无需验证码，可直接注册",
                            codeSent = true,
                            needSmsInput = needSms,
                            registrationMode = data?.registrationMode,
                            partnerRegistered = data?.partnerRegistered,
                            selfRegistered = data?.selfRegistered,
                            selfBound = data?.selfBound,
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
                        )
                    }
                }
                else -> {
                    // no-op for other result types
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
                is RegisterResult.SubmitSuccess -> {
                    val resp = result.resp
                    val data = resp.data
                    val current = _uiState.value
                    val mergedPartnerRegistered = data?.partnerRegistered ?: current.partnerRegistered
                    val mergedNeedSmsInput = data?.needSmsInput ?: current.needSmsInput
                    val mergedRegistrationMode = data?.registrationMode ?: current.registrationMode

                    val mergedHumedsBindStatus = data?.humedsBindStatus
                    val mergedHumedsErrorCode = data?.humedsErrorCode
                    val mergedHumedsErrorMessage = data?.humedsErrorMessage

                    val humedsSuffix =
                        if (mergedHumedsBindStatus.isNullOrBlank()) {
                            ""
                        } else if (mergedHumedsBindStatus == "success") {
                            "（已绑定 Humeds）"
                        } else if (mergedHumedsBindStatus == "skipped") {
                            "（已跳过 Humeds 绑定）"
                        } else {
                            val detail = listOfNotNull(mergedHumedsErrorMessage, mergedHumedsErrorCode).firstOrNull()
                            if (detail.isNullOrBlank()) "（Humeds 绑定失败）" else "（Humeds 绑定失败：$detail）"
                        }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            success = true,
                            errorMessage = null,
                            statusMessage = "注册成功$humedsSuffix",
                            partnerRegistered = mergedPartnerRegistered,
                            needSmsInput = mergedNeedSmsInput,
                            registrationMode = mergedRegistrationMode,
                            humedsBindStatus = mergedHumedsBindStatus,
                            humedsErrorCode = mergedHumedsErrorCode,
                            humedsErrorMessage = mergedHumedsErrorMessage,
                            registeredUserId = data?.userId?.toLongOrNull(),
                            registeredMobile = mobile.trim(),
                            flowHint = buildFlowHintForSubmit(
                                humedsBindStatus = mergedHumedsBindStatus,
                                humedsErrorCode = mergedHumedsErrorCode,
                                humedsErrorMessage = mergedHumedsErrorMessage
                            ),
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
                else -> {
                    // no-op for other result types
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sendCodeCooldownJob?.cancel()
    }

    fun repairHumedsBindingByPassword(humedsPassword: String, regionCode: String = "86") {
        val current = _uiState.value
        val userId = current.registeredUserId
        val mobile = current.registeredMobile

        if (userId == null || mobile.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "注册信息缺失，请返回重试",
                    statusMessage = null,
                    humedsBindStatus = current.humedsBindStatus,
                )
            }
            return
        }

        if (humedsPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请输入 Humeds 密码", statusMessage = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    humedsErrorCode = null,
                    humedsErrorMessage = null,
                )
            }

            runCatching {
                humedsBindRepairRepository.bindWithPassword(
                    userId = userId,
                    mobile = mobile,
                    humedsPassword = humedsPassword,
                    regionCode = regionCode,
                )
            }.onSuccess { _ ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        humedsBindStatus = "success",
                        humedsErrorCode = null,
                        humedsErrorMessage = null,
                        statusMessage = "Humeds 绑定成功",
                        errorMessage = null,
                    )
                }
            }.onFailure { e ->
                val msg = e.message ?: "Humeds 绑定失败"
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        humedsBindStatus = "failed",
                        humedsErrorCode = "HUMEDS_BIND_REPAIR_FAILED",
                        humedsErrorMessage = msg,
                        errorMessage = msg,
                        statusMessage = null,
                    )
                }
            }
        }
    }
}
