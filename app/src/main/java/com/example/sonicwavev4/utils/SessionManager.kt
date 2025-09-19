package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit // 确保导入 KTX 扩展函数

class SessionManager(context: Context) {
    // 将 prefs 保持为 private，这是正确的封装
    private val prefs: SharedPreferences = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

    companion object {
        const val AUTH_TOKEN = "auth_token"
        const val USER_ID = "user_id"
        const val USER_NAME = "user_name"
        const val USER_EMAIL = "user_email"
        const val SESSION_ID = "session_id"
    }

    /**
     * 最佳实践] 提供一个统一的方法来保存整个用户会话信息。
     * LoginFragment 只需调用这一个方法。
     * 内部使用了 KTX 的 edit 函数，简洁且安全。
     */
    fun saveUserSession(token: String, userId: String, userName: String, email: String) {
        prefs.edit {
            putString(AUTH_TOKEN, token)
            putString(USER_ID, userId)
            putString(USER_NAME, userName)
            putString(USER_EMAIL, email)
        }
    }

    // 单独的 sessionId 保存函数，因为它是在另一个网络请求后才获取的
    fun saveSessionId(sessionId: Long) {
        prefs.edit {
            putLong(SESSION_ID, sessionId)
        }
    }

    // 获取函数保持不变
    fun fetchAuthToken(): String? = prefs.getString(AUTH_TOKEN, null)
    fun fetchUserId(): String? = prefs.getString(USER_ID, null)
    fun fetchUserName(): String? = prefs.getString(USER_NAME, null)
    fun fetchEmail(): String? = prefs.getString(USER_EMAIL, null)
    fun fetchSessionId(): Long = prefs.getLong(SESSION_ID, -1L)

    fun clearSession() {
        prefs.edit {
            clear()
        }
    }
}