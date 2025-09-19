package com.example.sonicwavev4.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    // --- 用户认证 ---
    @POST("api/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // --- 日志与操作记录 ---
    @POST("api/logs")
    suspend fun createClientLogs(@Body requestBody: RequestBody): Response<Unit>

    @POST("api/app/usage")
    suspend fun recordAppUsage(@Body request: AppUsageRequest): Response<Unit>

    @POST("api/operations/start")
    suspend fun startOperation(@Body request: StartOperationRequest): StartOperationResponse

    @PUT("api/operations/stop/{id}")
    suspend fun stopOperation(@Path("id") id: Long): Response<Unit>

    // --- 心跳与会话管理 ---
    @POST("api/heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<Unit>

    @POST("api/auth/login-event")
    suspend fun recordLoginEvent(@Body request: LoginEventRequest): Response<LoginEventResponse>

    @PUT("api/auth/logout-event") // RESTful实践：使用PUT更新会话状态
    suspend fun recordLogoutEvent(@Body request: LogoutEventRequest): Response<Unit>
}