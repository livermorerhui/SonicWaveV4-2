package com.example.sonicwavev4.network

data class AppUsageRequest(
    val launchTime: Long,
    val userId: String? = null,
    val deviceId: String? = null,
    val ipAddress: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null
)
