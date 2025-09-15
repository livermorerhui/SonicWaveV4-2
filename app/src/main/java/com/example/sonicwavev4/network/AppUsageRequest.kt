package com.example.sonicwavev4.network

data class AppUsageRequest(
    val launchTime: Long,
    val userId: String? = null // Optional, if you want to associate usage with a user
)