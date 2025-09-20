package com.example.sonicwavev4.network

import com.google.gson.annotations.SerializedName

// Request body for the /token/refresh endpoint
data class RefreshTokenRequest(
    val refreshToken: String
)

// Response body for the /token/refresh endpoint
data class RefreshTokenResponse(
    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String
)
