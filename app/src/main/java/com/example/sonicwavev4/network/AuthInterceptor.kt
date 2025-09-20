package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(context: Context) : Interceptor {

    private val sessionManager = SessionManager(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()

        // 从 SessionManager 获取 Token
        sessionManager.fetchAccessToken()?.let {
            // 如果 Token 存在，则将其添加到请求头中
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }

        return chain.proceed(requestBuilder.build())
    }
}