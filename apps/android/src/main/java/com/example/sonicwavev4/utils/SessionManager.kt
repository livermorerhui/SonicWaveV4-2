package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.runBlocking

// Sealed class for defining logout reasons, allowing for future expansion.
sealed class LogoutReason {
    object HardLogout : LogoutReason() // e.g., Refresh token failed
    object UserInitiated : LogoutReason() // e.g., User clicked logout button
    object ReAuthenticationRequired : LogoutReason() // Future use case
}

class SessionManager(context: Context) {

    private lateinit var prefs: SharedPreferences

    init {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                "SecureAppPrefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to create EncryptedSharedPreferences, falling back to standard SharedPreferences.", e)
            prefs = context.getSharedPreferences("AppPrefs_Fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val USER_ID = "user_id"
        const val USER_NAME = "user_name"
        const val USER_EMAIL = "user_email"
        const val SESSION_ID = "session_id"
        const val OFFLINE_TEST_MODE = "offline_test_mode"
        const val OFFLINE_MODE_ALLOWED = "offline_mode_allowed"
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit().apply {
            putString(ACCESS_TOKEN, accessToken)
            putString(REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    fun saveUserSession(
        userId: String,
        userName: String,
        email: String
    ) {
        prefs.edit().apply {
            putString(USER_ID, userId)
            putString(USER_NAME, userName)
            putString(USER_EMAIL, email)
            apply()
        }
    }

    fun saveSessionId(sessionId: Long) {
        prefs.edit().apply {
            putLong(SESSION_ID, sessionId)
            apply()
        }
    }

    fun setOfflineTestMode(enabled: Boolean) {
        prefs.edit().putBoolean(OFFLINE_TEST_MODE, enabled).apply()
    }

    fun isOfflineTestMode(): Boolean = prefs.getBoolean(OFFLINE_TEST_MODE, false)

    fun setOfflineModeAllowed(allowed: Boolean) {
        prefs.edit().putBoolean(OFFLINE_MODE_ALLOWED, allowed).apply()
    }

    fun isOfflineModeAllowed(): Boolean = prefs.getBoolean(OFFLINE_MODE_ALLOWED, true)

    fun fetchAccessToken(): String? = prefs.getString(ACCESS_TOKEN, null)
    fun fetchRefreshToken(): String? = prefs.getString(REFRESH_TOKEN, null)
    fun fetchUserId(): String? = prefs.getString(USER_ID, null)
    fun fetchUserName(): String? = prefs.getString(USER_NAME, null)
    fun fetchUserEmail(): String? = prefs.getString(USER_EMAIL, null)
    fun fetchSessionId(): Long = prefs.getLong(SESSION_ID, -1L)

    fun hasActiveSession(): Boolean {
        if (isOfflineTestMode()) {
            return true
        }
        val accessToken = fetchAccessToken()
        val userId = fetchUserId()
        return !accessToken.isNullOrBlank() && !userId.isNullOrBlank()
    }

    fun initiateLogout(reason: LogoutReason) {
        Log.w("SessionManager", "Logout initiated with reason: ${reason::class.simpleName}")
        when (reason) {
            is LogoutReason.HardLogout, is LogoutReason.UserInitiated -> {
                clearSessionAndNotify()
            }
            is LogoutReason.ReAuthenticationRequired -> {
                clearSessionAndNotify()
            }
        }
    }

    private fun clearSessionAndNotify() {
        runBlocking {
            GlobalLogoutManager.logout()
        }
        val offlineAllowed = isOfflineModeAllowed()
        prefs.edit().clear().apply()
        prefs.edit().putBoolean(OFFLINE_MODE_ALLOWED, offlineAllowed).apply()
        OfflineTestModeManager.setOfflineTestMode(false)
        OfflineCapabilityManager.setOfflineAllowed(offlineAllowed)
        OfflineForceExitManager.cancelCountdown()
        RetrofitClient.updateToken(null)
    }
}
