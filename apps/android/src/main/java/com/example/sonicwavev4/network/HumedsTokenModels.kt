package com.example.sonicwavev4.network

data class HumedsTokenRequest(
    val userId: Long,
    val mobile: String? = null,
    val password: String? = null,
    val smscode: String? = null,
    val regionCode: String? = null
)

data class HumedsTokenData(
    val token_jwt: String?,
    val raw: Any? = null
)

data class HumedsTokenResponse(
    val code: Int,
    val msg: String,
    val data: HumedsTokenData?
)
