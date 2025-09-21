package com.example.sonicwavev4.network

import com.google.gson.annotations.SerializedName

// Response body for the /login endpoint
data class LoginResponse(
    @SerializedName("message")
    val message: String,

    @SerializedName("accessToken")
    val accessToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String,

    @SerializedName("username")
    val username: String,

    @SerializedName("userId")
    val userId: Int
)
