package com.example.sonicwavev4.util

import android.os.Build

object EmulatorDetector {
    fun isEmulator(): Boolean {
        val product = Build.PRODUCT
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER

        return fingerprint.startsWith("generic")
            || fingerprint.startsWith("unknown")
            || model.contains("google_sdk", ignoreCase = true)
            || model.contains("emulator", ignoreCase = true)
            || model.contains("android sdk built for x86", ignoreCase = true)
            || manufacturer.contains("genymotion", ignoreCase = true)
            || product.contains("sdk_gphone", ignoreCase = true)
            || product.contains("sdk", ignoreCase = true)
    }
}
