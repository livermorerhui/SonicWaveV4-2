package com.example.sonicwavev4.core.account

import com.example.sonicwavev4.utils.LogoutReason

/**
 * Represents user-driven actions for authentication flows.
 */
sealed class AuthIntent {
    data class Login(val email: String, val password: String) : AuthIntent()
    data class Register(
        val username: String,
        val email: String,
        val password: String,
        val confirmPassword: String
    ) : AuthIntent()

    object EnterOfflineMode : AuthIntent()
    data class Logout(val reason: LogoutReason = LogoutReason.UserInitiated) : AuthIntent()
    object ClearError : AuthIntent()
}

/**
 * Snapshot for authentication related UI.
 */
data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isOfflineMode: Boolean = false,
    val accountType: String? = null,
    val username: String? = null,
    val offlineModeAllowed: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One-off events for navigation and messaging.
 */
sealed class AuthEvent {
    data class ShowToast(val message: String) : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
    object NavigateToUser : AuthEvent()
    object NavigateToLogin : AuthEvent()
}

/**
 * Result returned from the authentication gateway.
 */
data class AuthResult(
    val username: String?,
    val accountType: String?,
    val isOfflineMode: Boolean
)
