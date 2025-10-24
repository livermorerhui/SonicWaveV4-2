package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    fun fetchAccessToken(): String? = prefs.getString(ACCESS_TOKEN, null)
    fun fetchRefreshToken(): String? = prefs.getString(REFRESH_TOKEN, null)
    fun fetchUserId(): String? = prefs.getString(USER_ID, null)
    fun fetchUserName(): String? = prefs.getString(USER_NAME, null)
    fun fetchUserEmail(): String? = prefs.getString(USER_EMAIL, null)
    fun fetchSessionId(): Long = prefs.getLong(SESSION_ID, -1L)

    fun hasActiveSession(): Boolean {
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
        prefs.edit().clear().apply()
        RetrofitClient.updateToken(null)
        // Launch a coroutine on a global scope to call the suspend function
        CoroutineScope(Dispatchers.IO).launch {
            GlobalLogoutManager.logout()
        }
    }
}
