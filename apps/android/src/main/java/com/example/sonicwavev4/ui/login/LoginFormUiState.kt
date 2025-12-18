package com.example.sonicwavev4.ui.login

enum class PasswordSource {
    INPUT,
    STORED
}

const val MASK_TOKEN = "••••••••"

data class LoginFormUiState(
    val accountText: String = "",
    val isRememberChecked: Boolean = false,
    val rememberSupported: Boolean = false,
    val passwordSource: PasswordSource = PasswordSource.INPUT,
    val rememberedAccount: String? = null,
    val isStoredAccountMatched: Boolean = true,
    val isPasswordVisible: Boolean = false
)
