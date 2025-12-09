package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.HumedsTokenRequest
import com.example.sonicwavev4.utils.SessionManager

class HumedsLaunchRepository(
    private val sessionManager: SessionManager,
    private val apiService: ApiService,
) {
    suspend fun getHumedsTokenForCurrentUser(): String {
        val userIdString = sessionManager.fetchUserId()
            ?: throw IllegalStateException("用户未登录，无法获取 Humeds token")

        val userId = userIdString.toLongOrNull()
            ?: throw IllegalStateException("用户信息异常，请重新登录")

        val request = HumedsTokenRequest(userId = userId)
        val response = apiService.getHumedsToken(request)

        if (!response.isSuccessful) {
            throw IllegalStateException("获取 Humeds token 失败：HTTP ${response.code()}")
        }

        val body = response.body()
            ?: throw IllegalStateException("获取 Humeds token 失败：响应为空")

        if (body.code != 200) {
            throw IllegalStateException(body.msg.ifBlank { "获取 Humeds token 失败，code=${body.code}" })
        }

        val token = body.data?.token_jwt
            ?: throw IllegalStateException("获取 Humeds token 失败：token_jwt 为空")

        return token
    }
}
