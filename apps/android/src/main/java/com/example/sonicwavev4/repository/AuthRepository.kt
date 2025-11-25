package com.example.sonicwavev4.repository

import android.app.Application
import android.util.Log
import com.example.sonicwavev4.core.account.AuthGateway
import com.example.sonicwavev4.core.account.AuthResult
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.network.LoginRequest
import com.example.sonicwavev4.network.LoginResponse
import com.example.sonicwavev4.network.LoginEventRequest
import com.example.sonicwavev4.network.RegisterRequest
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.network.LogoutEventRequest
import com.example.sonicwavev4.utils.HeartbeatManager
import com.example.sonicwavev4.utils.HeartbeatOrchestrator
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.OfflineTestModeManager
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(application: Application) : AuthGateway {

    private val appContext = application.applicationContext
    private val sessionManager = SessionManager(appContext)

    override suspend fun login(email: String, password: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val loginResponse = performLoginRequest(email, password)
            recordLoginEvent()
            HeartbeatOrchestrator.onLogin(appContext)
            buildAuthResult(loginResponse, isOffline = false)
        }
    }

    override suspend fun registerAndLogin(
        username: String,
        email: String,
        password: String
    ): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            val registerResponse = RetrofitClient.api.register(RegisterRequest(username, email, password))
            if (!registerResponse.isSuccessful) {
                val message = ErrorMessageResolver.fromResponse(registerResponse.errorBody(), registerResponse.code())
                throw Exception(message)
            }
            val loginResponse = performLoginRequest(email, password)
            recordLoginEvent()
            HeartbeatOrchestrator.onLogin(appContext)
            buildAuthResult(loginResponse, isOffline = false)
        }
    }

    override suspend fun enterOfflineMode(): Result<AuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            sessionManager.setOfflineTestMode(true)
            OfflineTestModeManager.setOfflineTestMode(true)
            sessionManager.saveUserSession(OFFLINE_USER_ID, OFFLINE_USERNAME_DISPLAY, OFFLINE_EMAIL)
            sessionManager.saveSessionId(-1L)
            RetrofitClient.updateToken(null)
            HeartbeatManager.stop()
            HeartbeatOrchestrator.refreshHeartbeats()
            val offlineResponse = LoginResponse(
                message = "Offline login success",
                accessToken = "",
                refreshToken = "",
                username = OFFLINE_USERNAME_DISPLAY,
                userId = -1,
                accountType = "test"
            )
            buildAuthResult(offlineResponse, isOffline = true)
        }
    }

    override suspend fun logout(reason: LogoutReason): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sessionId = sessionManager.fetchSessionId()
            if (sessionId != -1L) {
                try {
                    RetrofitClient.api.recordLogoutEvent(LogoutEventRequest(sessionId))
                } catch (e: Exception) {
                    Log.e("AuthRepository", "Failed to record logout event", e)
                }
            }
            HeartbeatManager.stop()
            sessionManager.initiateLogout(reason)
            OfflineTestModeManager.setOfflineTestMode(false)
            RetrofitClient.updateToken(null)
            HeartbeatOrchestrator.onLogout(appContext)
        }
    }

    private suspend fun performLoginRequest(email: String, password: String): LoginResponse {
        val loginApiResponse = RetrofitClient.api.login(LoginRequest(email, password))
        if (loginApiResponse.isSuccessful && loginApiResponse.body() != null) {
            val loginResponse = loginApiResponse.body()!!
            sessionManager.saveTokens(loginResponse.accessToken, loginResponse.refreshToken)
            sessionManager.saveUserSession(
                loginResponse.userId.toString(),
                loginResponse.username,
                email
            )
            sessionManager.setOfflineTestMode(false)
            OfflineTestModeManager.setOfflineTestMode(false)
            RetrofitClient.updateToken(loginResponse.accessToken)
            return loginResponse
        } else {
            val message = ErrorMessageResolver.fromResponse(loginApiResponse.errorBody(), loginApiResponse.code())
            throw Exception(message)
        }
    }

    private suspend fun recordLoginEvent() {
        val loginEventResponse = RetrofitClient.api.recordLoginEvent(LoginEventRequest())
        if (loginEventResponse.isSuccessful && loginEventResponse.body() != null) {
            val sessionId = loginEventResponse.body()!!.sessionId
            sessionManager.saveSessionId(sessionId)
        } else {
            throw Exception("Failed to record login event after a successful login. Code: ${loginEventResponse.code()}")
        }
    }

    private fun buildAuthResult(loginResponse: LoginResponse, isOffline: Boolean): AuthResult {
        return AuthResult(
            username = loginResponse.username,
            accountType = loginResponse.accountType,
            isOfflineMode = isOffline
        )
    }

    companion object {
        private const val OFFLINE_USERNAME = "test"
        private const val OFFLINE_PASSWORD = "test123"
        private const val OFFLINE_USER_ID = "offline-test"
        private const val OFFLINE_EMAIL = "test@test.local"
        private const val OFFLINE_USERNAME_DISPLAY = "测试账号"

        fun isOfflineTestCredential(email: String, password: String): Boolean {
            return email.equals(OFFLINE_USERNAME, ignoreCase = true) && password == OFFLINE_PASSWORD
        }
    }
}
