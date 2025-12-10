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
    val userId: Int,

    @SerializedName("accountType")
    val accountType: String?,

    // 新增：Humeds 自动绑定结果（可选）
    @SerializedName("humedsBindStatus")
    val humedsBindStatus: String? = null,

    @SerializedName("humedsErrorCode")
    val humedsErrorCode: String? = null,

    @SerializedName("humedsErrorMessage")
    val humedsErrorMessage: String? = null,
)
