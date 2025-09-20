package com.example.sonicwavev4.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @POST("api/v1/token/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    // --- 用户认证 ---
    @POST("api/v1/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/v1/users/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // --- 日志与操作记录 ---
    @POST("api/v1/logs")
    suspend fun createClientLogs(@Body requestBody: RequestBody): Response<Unit>

    @POST("api/v1/app/usage")
    suspend fun recordAppUsage(@Body request: AppUsageRequest): Response<Unit>

    @POST("api/v1/operations/start")
    suspend fun startOperation(@Body request: StartOperationRequest): StartOperationResponse

    @PUT("api/v1/operations/stop/{id}")
    suspend fun stopOperation(@Path("id") id: Long): Response<Unit>

    // --- 心跳与会话管理 ---
    @POST("api/v1/heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<Unit>

    @POST("api/v1/auth/login-event")
    suspend fun recordLoginEvent(@Body request: LoginEventRequest): Response<LoginEventResponse>

    @PUT("api/v1/auth/logout-event") // RESTful实践：使用PUT更新会话状态
    suspend fun recordLogoutEvent(@Body request: LogoutEventRequest): Response<Unit>
}