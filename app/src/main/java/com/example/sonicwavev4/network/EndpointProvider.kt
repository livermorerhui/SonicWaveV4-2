package com.example.sonicwavev4.network

import com.example.sonicwavev4.BuildConfig
import com.example.sonicwavev4.util.EmulatorDetector

object EndpointProvider {
    val baseUrl: String by lazy {
        when {
            !BuildConfig.DEBUG -> BuildConfig.SERVER_BASE_URL_RELEASE.trimEnd('/')
            EmulatorDetector.isEmulator() -> BuildConfig.SERVER_BASE_URL_EMULATOR.trimEnd('/')
            else -> BuildConfig.SERVER_BASE_URL_LAN.trimEnd('/')
        }
    }
}
