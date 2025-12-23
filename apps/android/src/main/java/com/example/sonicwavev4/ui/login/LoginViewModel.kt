package com.example.sonicwavev4.ui.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthGateway
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.core.account.AuthResult
import com.example.sonicwavev4.core.account.AuthUiState
import com.example.sonicwavev4.core.account.HumedsBindInfo
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.repository.AuthRepository
import com.example.sonicwavev4.utils.EncryptedLoginCredentialStore
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.LoginCredentialStore
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.OfflineCapabilityManager
import com.example.sonicwavev4.utils.UnsupportedLoginCredentialStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    internal var authGateway: AuthGateway = AuthRepository(application)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private val _loginFormState = MutableStateFlow(LoginFormUiState())
    val loginFormState: StateFlow<LoginFormUiState> = _loginFormState.asStateFlow()

    private var credentialStore: LoginCredentialStore = UnsupportedLoginCredentialStore()

    init {
        observeOfflineCapability()
        observeGlobalLogout()
    }

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.Login -> {
                val mobile = intent.email.trim()
                performLogin(mobile, intent.password.trim())
            }
            is AuthIntent.Register -> performRegister(intent)
            AuthIntent.EnterOfflineMode -> enterOfflineMode()
            AuthIntent.EnterOfflineModeSilently -> enterOfflineModeSilently()
            is AuthIntent.Logout -> performLogout(intent.reason)
            AuthIntent.ClearError -> clearError()
        }
    }

    fun ensureCredentialStoreInitialized(appContext: Context) {
        credentialStore = EncryptedLoginCredentialStore(appContext.applicationContext)

        val supported = credentialStore.isSupported()
        val rememberedAccount = if (supported) credentialStore.loadRememberedAccount() else null

        _loginFormState.update { cur ->
            if (!supported || rememberedAccount.isNullOrBlank()) {
                cur.copy(
                    rememberSupported = supported,
                    isRememberChecked = false,
                    rememberedAccount = null,
                    passwordSource = PasswordSource.INPUT,
                    isStoredAccountMatched = true,
                    isPasswordVisible = false,
                    accountText = cur.accountText
                )
            } else {
                val rememberedPassword = credentialStore.loadRememberedPasswordForAccount(rememberedAccount)
                cur.copy(
                    rememberSupported = true,
                    isRememberChecked = true,
                    rememberedAccount = rememberedAccount,
                    accountText = rememberedAccount,
                    passwordSource = if (rememberedPassword.isNullOrBlank()) {
                        PasswordSource.INPUT
                    } else {
                        PasswordSource.STORED
                    },
                    isStoredAccountMatched = true,
                    isPasswordVisible = false
                )
            }
        }
    }

    fun onRememberCheckedChanged(checked: Boolean) {
        _loginFormState.update { it.copy(isRememberChecked = checked) }
        if (!checked && credentialStore.isSupported()) {
            credentialStore.clear()
            _loginFormState.update {
                it.copy(
                    rememberedAccount = null,
                    passwordSource = PasswordSource.INPUT,
                    isStoredAccountMatched = true,
                    isPasswordVisible = false
                )
            }
        }
    }

    fun onAccountChanged(newAccount: String) {
        _loginFormState.update { cur ->
            val matched = if (cur.passwordSource == PasswordSource.STORED) {
                newAccount == cur.rememberedAccount
            } else {
                true
            }
            cur.copy(accountText = newAccount, isStoredAccountMatched = matched)
        }
    }

    fun onPasswordChanged(@Suppress("UNUSED_PARAMETER") newText: String) {
        _loginFormState.update { cur ->
            cur.copy(
                passwordSource = PasswordSource.INPUT,
                isStoredAccountMatched = true
            )
        }
    }

    fun onTogglePasswordVisibility() {
        val cur = _loginFormState.value
        if (cur.passwordSource == PasswordSource.STORED) {
            emitToast("为安全起见，已保存密码不支持明文显示，请重新输入")
            _loginFormState.update { it.copy(isPasswordVisible = false) }
            return
        }
        _loginFormState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    private fun performLogin(mobile: String, passwordInput: String) {
        if (mobile.isBlank()) {
            emitToast("请输入账号")
            return
        }

        val form = _loginFormState.value
        val resolvedPassword: String = when (form.passwordSource) {
            PasswordSource.INPUT -> passwordInput
            PasswordSource.STORED -> {
                if (!form.isStoredAccountMatched) {
                    emitToast("账号已变更，请重新输入密码")
                    return
                }
                if (passwordInput != MASK_TOKEN) {
                    passwordInput
                } else {
                    val stored = if (credentialStore.isSupported()) {
                        credentialStore.loadRememberedPasswordForAccount(mobile)
                    } else {
                        null
                    }
                    if (stored.isNullOrBlank()) {
                        emitToast("未找到已保存的密码，请重新输入")
                        return
                    }
                    stored
                }
            }
        }

        if (resolvedPassword.isBlank()) {
            emitToast("请输入密码")
            return
        }

        if (form.isRememberChecked && credentialStore.isSupported()) {
            credentialStore.saveAccountOnly(mobile)
        }

        if (AuthRepository.isOfflineTestCredential(mobile, resolvedPassword)) {
            enterOfflineMode()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authGateway.login(mobile, resolvedPassword)

            result.onSuccess { authResult ->
                val shouldRemember = _loginFormState.value.isRememberChecked
                if (!authResult.isOfflineMode && credentialStore.isSupported()) {
                    if (shouldRemember) {
                        credentialStore.savePasswordForAccount(mobile, resolvedPassword)
                    } else {
                        credentialStore.clear()
                    }
                }
            }

            handleAuthResult(result, successMessage = "登录成功！")
        }
    }

    private fun performRegister(intent: AuthIntent.Register) {
        if (intent.username.isBlank() || intent.email.isBlank() || intent.password.isBlank()) {
            emitToast("请填写完整的注册信息")
            return
        }
        if (intent.password != intent.confirmPassword) {
            emitToast("两次输入的密码不一致")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            emitToast("注册成功，正在登录...")
            val result = authGateway.registerAndLogin(intent.username, intent.email, intent.password)
            handleAuthResult(result, successMessage = "登录成功！")
        }
    }

    private fun enterOfflineMode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authGateway.enterOfflineMode()
            handleAuthResult(result, successMessage = "登录成功！")
        }
    }

    private fun performLogout(reason: LogoutReason) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            authGateway.logout(reason)
            _uiState.value = AuthUiState(offlineModeAllowed = _uiState.value.offlineModeAllowed)
            _events.emit(AuthEvent.NavigateToLogin)
        }
    }

    private fun enterOfflineModeSilently() {
        if (_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authGateway.enterOfflineMode()
            handleAuthResultSilently(result)
        }
    }

    private suspend fun handleAuthResult(result: Result<AuthResult>, successMessage: String) {
        result.onSuccess { authResult ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    isOfflineMode = authResult.isOfflineMode,
                    accountType = authResult.accountType,
                    username = authResult.username,
                    errorMessage = null
                )
            }
            emitToast(successMessage)
            authResult.humedsBindInfo?.let { humedsInfo ->
                val hint = buildHumedsHintMessage(humedsInfo)
                if (hint != null) {
                    _events.emit(AuthEvent.ShowHumedsHint(hint))
                }
            }
            _events.emit(AuthEvent.NavigateToUser)
        }.onFailure { throwable ->
            val message = resolveErrorMessage(throwable)
            _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            _events.emit(AuthEvent.ShowError(message))
        }
    }

    private suspend fun handleAuthResultSilently(result: Result<AuthResult>) {
        result.onSuccess { authResult ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoggedIn = true,
                    isOfflineMode = authResult.isOfflineMode,
                    accountType = authResult.accountType ?: "test",
                    username = authResult.username,
                    errorMessage = null
                )
            }
        }.onFailure { throwable ->
            val message = resolveErrorMessage(throwable)
            _uiState.update { it.copy(isLoading = false, errorMessage = message) }
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun overrideUsername(newUsername: String) {
        _uiState.update { current ->
            current.copy(username = newUsername)
        }
    }

    private fun emitToast(message: String) {
        viewModelScope.launch { _events.emit(AuthEvent.ShowToast(message)) }
    }

    private fun observeOfflineCapability() {
        viewModelScope.launch {
            OfflineCapabilityManager.isOfflineAllowed.collect { allowed ->
                _uiState.update { it.copy(offlineModeAllowed = allowed) }
            }
        }
    }

    private fun observeGlobalLogout() {
        viewModelScope.launch {
            GlobalLogoutManager.logoutEvent.collect {
                _uiState.value = AuthUiState(offlineModeAllowed = _uiState.value.offlineModeAllowed)
                _events.emit(AuthEvent.NavigateToLogin)
            }
        }
    }

    private fun resolveErrorMessage(throwable: Throwable): String {
        return if (throwable is java.io.IOException) {
            ErrorMessageResolver.networkFailure(throwable)
        } else {
            throwable.message ?: "登录失败，请稍后再试"
        }
    }

    private fun buildHumedsHintMessage(info: HumedsBindInfo): String? {
        val status = info.status?.lowercase()
        if (status == null || status == "success") {
            return null
        }

        return when (info.errorCode) {
            "HUMEDS_LOGIN_FAILED" ->
                "已登录本应用，但 Humeds 自动登录失败，可能账号或密码不同。稍后可通过客户详情页下方的 Humeds 按钮重试或重新绑定。"
            "HUMEDS_ACCOUNT_NOT_FOUND" ->
                "已登录本应用，但未找到对应的 Humeds 账号。如需在 Humeds 中查看数据，请联系管理员或在 Humeds 应用中注册。"
            else ->
                "已登录本应用，但暂时无法自动连接 Humeds（${info.errorCode ?: "原因未知"}）。不影响本应用的正常使用。"
        }
    }
}
