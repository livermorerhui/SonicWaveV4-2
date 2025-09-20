package com.example.sonicwavev4.network

data class LoginRequest(
    val email: String,
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

data class ApiError(
    val error: String
)
