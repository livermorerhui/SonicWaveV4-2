package com.example.sonicwavev4.network

data class HumedsTestLoginRequest(
    val mobile: String,
    val password: String,
    val regionCode: String,
)

data class HumedsTestLoginResult(
    val token_jwt: String?,
    val raw: Any?,
)
