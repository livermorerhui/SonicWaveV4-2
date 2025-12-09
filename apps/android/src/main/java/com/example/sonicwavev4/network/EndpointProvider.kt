package com.example.sonicwavev4.network

import com.example.sonicwavev4.BuildConfig
import com.example.sonicwavev4.util.EmulatorDetector

enum class BackendEnvOption {
    REMOTE_ALIYUN,
    LOCAL_DEBUG
}

/**
 * 统一提供后端 baseUrl 的入口。
 *
 * - Release：始终使用 SERVER_BASE_URL_RELEASE（通常指向阿里云）。
 * - Debug：
 *    - 默认：沿用原来的逻辑（LAN/EMULATOR -> 本地开发）
 *    - 允许通过客户端 UI 覆盖为：阿里云（BACKEND_BASE_URL）或本地（LAN/EMULATOR）
 */
object EndpointProvider {

    // Debug 下的默认 baseUrl（不考虑 UI 覆盖）
    private val defaultDebugBaseUrl: String by lazy {
        val raw = if (EmulatorDetector.isEmulator()) {
            BuildConfig.SERVER_BASE_URL_EMULATOR
        } else {
            BuildConfig.SERVER_BASE_URL_LAN
        }
        raw.trimEnd('/')
    }

    // Release 下的默认 baseUrl
    private val defaultReleaseBaseUrl: String by lazy {
        BuildConfig.SERVER_BASE_URL_RELEASE.trimEnd('/')
    }

    // 阿里云后端（统一用 BACKEND_BASE_URL，确保和后端约定一致）
    private val aliyunBackendBaseUrl: String by lazy {
        BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    }

    @Volatile
    private var overrideEnvOption: BackendEnvOption? = null

    /**
     * 对外暴露的 baseUrl：
     * - Release：始终使用 defaultReleaseBaseUrl
     * - Debug：如果有 overrideEnvOption，则按覆盖；否则用 defaultDebugBaseUrl
     */
    val baseUrl: String
        get() {
            if (!BuildConfig.DEBUG) {
                return defaultReleaseBaseUrl.trimEnd('/') + "/"
            }

            val env = overrideEnvOption ?: BackendEnvOption.LOCAL_DEBUG

            val raw = when (env) {
                BackendEnvOption.REMOTE_ALIYUN -> aliyunBackendBaseUrl
                BackendEnvOption.LOCAL_DEBUG -> defaultDebugBaseUrl
            }

            // Retrofit 要求以 / 结尾，这里统一补齐
            return raw.trimEnd('/') + "/"
        }

    /**
     * Debug 专用：切换到阿里云后端
     */
    fun useAliyunBackendForDebug() {
        if (BuildConfig.DEBUG) {
            overrideEnvOption = BackendEnvOption.REMOTE_ALIYUN
        }
    }

    /**
     * Debug 专用：切换到本地后端（LAN/EMULATOR）
     */
    fun useLocalBackendForDebug() {
        if (BuildConfig.DEBUG) {
            overrideEnvOption = BackendEnvOption.LOCAL_DEBUG
        }
    }

    /**
     * Debug 专用：返回当前环境的中文标签，UI 用于展示。
     */
    fun currentEnvLabelForDebug(): String {
        if (!BuildConfig.DEBUG) return "阿里云"

        val env = overrideEnvOption ?: BackendEnvOption.LOCAL_DEBUG
        return when (env) {
            BackendEnvOption.REMOTE_ALIYUN -> "阿里云"
            BackendEnvOption.LOCAL_DEBUG -> "本地"
        }
    }
}
