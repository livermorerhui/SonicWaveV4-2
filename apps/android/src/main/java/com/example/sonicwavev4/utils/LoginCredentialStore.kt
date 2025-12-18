package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

interface LoginCredentialStore {
    fun isSupported(): Boolean
    fun loadRememberedAccount(): String?
    fun loadRememberedPasswordForAccount(account: String): String?
    fun saveAccountOnly(account: String)
    fun savePasswordForAccount(account: String, password: String)
    fun save(account: String, password: String)
    fun clear()
}

class UnsupportedLoginCredentialStore : LoginCredentialStore {
    override fun isSupported() = false
    override fun loadRememberedAccount(): String? = null
    override fun loadRememberedPasswordForAccount(account: String): String? = null
    override fun saveAccountOnly(account: String) = Unit
    override fun savePasswordForAccount(account: String, password: String) = Unit
    override fun save(account: String, password: String) = Unit
    override fun clear() = Unit
}

class EncryptedLoginCredentialStore(context: Context) : LoginCredentialStore {
    companion object {
        private const val PREFS_NAME = "SecureLoginPrefs"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_PASSWORD = "password"
        private const val TAG = "EncryptedLoginCredentialStore"
    }

    private val prefs: SharedPreferences? = try {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to init EncryptedSharedPreferences. Remember-password disabled.", e)
        null
    }

    private fun passwordKeyFor(account: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "pwd_" + hex.substring(0, 32)
    }

    override fun isSupported(): Boolean = prefs != null

    override fun loadRememberedAccount(): String? = prefs?.getString(KEY_ACCOUNT, null)

    override fun loadRememberedPasswordForAccount(account: String): String? {
        if (!isSupported() || account.isBlank()) return null
        val p = prefs ?: return null
        val pwdKey = passwordKeyFor(account)
        val fromMulti = p.getString(pwdKey, null)
        if (!fromMulti.isNullOrBlank()) return fromMulti

        val legacyAccount = p.getString(KEY_ACCOUNT, null)
        val legacyPwd = p.getString(KEY_PASSWORD, null)
        if (legacyAccount == account && !legacyPwd.isNullOrBlank()) {
            p.edit().putString(pwdKey, legacyPwd).remove(KEY_PASSWORD).apply()
            return legacyPwd
        }
        return null
    }

    override fun saveAccountOnly(account: String) {
        if (!isSupported() || account.isBlank()) return
        prefs?.edit()?.putString(KEY_ACCOUNT, account)?.apply()
    }

    override fun savePasswordForAccount(account: String, password: String) {
        val p = prefs ?: return
        if (account.isBlank() || password.isBlank()) return
        if (password == com.example.sonicwavev4.ui.login.MASK_TOKEN) {
            Log.w(TAG, "Attempted to save MASK_TOKEN, ignoring")
            return
        }
        val pwdKey = passwordKeyFor(account)
        p.edit()
            .putString(KEY_ACCOUNT, account)
            .putString(pwdKey, password)
            .apply()
    }

    override fun save(account: String, password: String) {
        savePasswordForAccount(account, password)
    }

    override fun clear() {
        val p = prefs ?: return
        val editor = p.edit()
        editor.remove(KEY_ACCOUNT)
        editor.remove(KEY_PASSWORD)
        p.all.keys.filter { it.startsWith("pwd_") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
