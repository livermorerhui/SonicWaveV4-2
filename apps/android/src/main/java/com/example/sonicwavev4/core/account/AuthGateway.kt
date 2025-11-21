package com.example.sonicwavev4.core.account

import com.example.sonicwavev4.utils.LogoutReason

interface AuthGateway {
    suspend fun login(email: String, password: String): Result<AuthResult>
    suspend fun registerAndLogin(username: String, email: String, password: String): Result<AuthResult>
    suspend fun enterOfflineMode(): Result<AuthResult>
    suspend fun logout(reason: LogoutReason): Result<Unit>
}
