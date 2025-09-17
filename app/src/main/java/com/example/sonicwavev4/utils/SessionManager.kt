package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun fetchUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun saveUserName(userName: String) {
        prefs.edit().putString(KEY_USER_NAME, userName).apply()
    }

    fun fetchUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun saveEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun fetchEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "user_session"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_EMAIL = "email"
    }
}