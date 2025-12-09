package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.BuildConfig

/**
 * Centralized backend environment selector.
 *
 * Release builds always force CLOUD. Debug builds read a persisted selection.
 */
object BackendEnvironment {

    private const val PREFS_NAME = "backend_env_prefs"
    private const val KEY_ENV = "backend_env"

    private const val DEFAULT_ENV = "CLOUD"

    private const val LOCAL_FIELD_NAME = "BACKEND_BASE_URL_LOCAL"
    private const val CLOUD_FIELD_NAME = "BACKEND_BASE_URL_CLOUD"

    @Volatile
    private var appContext: Context? = null

    enum class Env {
        LOCAL,
        CLOUD,
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getCurrentEnv(context: Context? = null): Env {
        // Release 构建一律强制用云端
        if (!BuildConfig.DEBUG) return Env.CLOUD

        val ctx = context ?: appContext ?: return Env.CLOUD
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ENV, DEFAULT_ENV)
        return runCatching { Env.valueOf(stored ?: DEFAULT_ENV) }.getOrDefault(Env.CLOUD)
    }

    fun setCurrentEnv(context: Context, env: Env) {
        if (!BuildConfig.DEBUG) return // Release 构建忽略任何修改
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ENV, env.name).apply()
    }

    fun getBackendBaseUrl(context: Context? = null): String {
        // Release：只用云端
        if (!BuildConfig.DEBUG) return buildConfigCloudUrl()

        val env = getCurrentEnv(context)
        val selected = when (env) {
            Env.LOCAL -> buildConfigLocalUrl()
            Env.CLOUD -> buildConfigCloudUrl()
        }
        return selected.trimEnd('/')
    }

    private fun buildConfigLocalUrl(): String {
        // BACKEND_BASE_URL_LOCAL 只在 debug 下定义，反射避免 release 版本缺字段时编译/运行问题
        val local = runCatching {
            BuildConfig::class.java.getDeclaredField(LOCAL_FIELD_NAME).get(null) as? String
        }.getOrNull()
        return (local ?: buildConfigCloudUrl()).trimEnd('/')
    }

    private fun buildConfigCloudUrl(): String {
        val cloud = runCatching {
            BuildConfig::class.java.getDeclaredField(CLOUD_FIELD_NAME).get(null) as? String
        }.getOrNull()
        return (cloud ?: BuildConfig.BACKEND_BASE_URL).trimEnd('/')
    }
}
