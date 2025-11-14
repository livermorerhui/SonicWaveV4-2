package com.example.sonicwavev4.network

data class FeatureFlagsResponse(
    val offlineModeEnabled: Boolean,
    val updatedAt: String?,
    val deviceOfflineAllowed: Boolean? = null,
    val deviceRegistered: Boolean? = null,
    val deviceId: String? = null
)
