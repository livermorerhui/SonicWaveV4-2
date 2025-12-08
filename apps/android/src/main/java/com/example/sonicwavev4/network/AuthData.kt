package com.example.sonicwavev4.network

// 登录只使用手机号和密码
data class LoginRequest(
    val mobile: String,
    val password: String
)



data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class RegisterResponse(
    val message: String,
    val userId: Int
)
