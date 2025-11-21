package com.example.sonicwavev4.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.core.account.AuthEvent
import com.example.sonicwavev4.core.account.AuthGateway
import com.example.sonicwavev4.core.account.AuthIntent
import com.example.sonicwavev4.core.account.AuthResult
import com.example.sonicwavev4.core.account.AuthUiState
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.repository.AuthRepository
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.OfflineCapabilityManager
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

    init {
        observeOfflineCapability()
        observeGlobalLogout()
    }

    fun handleIntent(intent: AuthIntent) {
        when (intent) {
            is AuthIntent.Login -> performLogin(intent.email.trim(), intent.password.trim())
            is AuthIntent.Register -> performRegister(intent)
            AuthIntent.EnterOfflineMode -> enterOfflineMode()
            is AuthIntent.Logout -> performLogout(intent.reason)
            AuthIntent.ClearError -> clearError()
        }
    }

    private fun performLogin(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            emitToast("请输入邮箱和密码")
            return
        }
        if (AuthRepository.isOfflineTestCredential(email, password)) {
            enterOfflineMode()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authGateway.login(email, password)
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
            _events.emit(AuthEvent.NavigateToUser)
        }.onFailure { throwable ->
            val message = resolveErrorMessage(throwable)
            _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            _events.emit(AuthEvent.ShowError(message))
        }
    }

    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
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
}
