package com.example.sonicwavev4.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import com.example.sonicwavev4.BuildConfig
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.UUID

data class DeviceProfile(
    val deviceId: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val localIpAddress: String?
)

object DeviceIdentityProvider {

    private const val PREFS_NAME = "device_identity"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var initialized = false
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            initialized = true
        }
    }

    private fun ensureInitialized() {
        if (!initialized) {
            throw IllegalStateException("DeviceIdentityProvider must be initialized in Application#onCreate")
        }
    }

    fun getDeviceId(): String {
        ensureInitialized()
        val cached = prefs.getString(KEY_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        val fresh = buildStableDeviceId()
        prefs.edit { putString(KEY_DEVICE_ID, fresh) }
        return fresh
    }

    fun buildProfile(): DeviceProfile {
        ensureInitialized()
        return DeviceProfile(
            deviceId = getDeviceId(),
            deviceModel = buildModelName(),
            osVersion = Build.VERSION.RELEASE ?: "unknown",
            appVersion = BuildConfig.VERSION_NAME,
            localIpAddress = resolveCurrentIp()
        )
    }

    private fun buildModelName(): String {
        val brand = Build.BRAND?.takeIf { it.isNotBlank() } ?: ""
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: ""
        return listOf(brand, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android" }
    }

    private fun buildStableDeviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val seed = if (androidId.isNotBlank()) {
            androidId.lowercase(Locale.US)
        } else {
            UUID.randomUUID().toString()
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return "sw-$hex"
    }

    private fun resolveCurrentIp(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            Collections.list(interfaces)
                .filter { iface ->
                    iface.isUp &&
                        !iface.isLoopback &&
                        !iface.displayName.lowercase(Locale.US).contains("rmnet")
                }
                .flatMap { iface -> Collections.list(iface.inetAddresses) }
                .firstOrNull { address -> !address.isLoopbackAddress && address is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
