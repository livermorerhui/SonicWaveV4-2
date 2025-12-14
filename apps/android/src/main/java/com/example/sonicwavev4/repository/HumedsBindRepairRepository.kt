package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.HumedsTokenRequest
import com.example.sonicwavev4.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HumedsBindRepairRepository {
    suspend fun bindWithPassword(
        userId: Long,
        mobile: String,
        humedsPassword: String,
        regionCode: String = "86",
    ): String = withContext(Dispatchers.IO) {
        val request = HumedsTokenRequest(
            userId = userId,
            mobile = mobile,
            password = humedsPassword,
            smscode = null,
            regionCode = regionCode,
        )

        val resp = RetrofitClient.api.getHumedsToken(request)
        if (!resp.isSuccessful) {
            throw IllegalStateException("Humeds 绑定失败：HTTP ${resp.code()}")
        }

        val body = resp.body() ?: throw IllegalStateException("Humeds 绑定失败：响应为空")
        if (body.code != 200) {
            val msg = body.msg.takeIf { it.isNotBlank() } ?: "Humeds 绑定失败（code=${body.code}）"
            throw IllegalStateException(msg)
        }

        val token = body.data?.token_jwt
        if (token.isNullOrBlank()) {
            throw IllegalStateException("Humeds 绑定失败：token_jwt 为空")
        }
        token
    }
}
