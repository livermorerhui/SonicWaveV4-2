package com.example.sonicwavev4.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    @POST("api/users/register")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("api/users/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/logs")
    suspend fun createClientLogs(@Body requestBody: RequestBody): Response<Unit>

    @POST("api/app/usage")
    suspend fun recordAppUsage(@Body request: AppUsageRequest): Response<Unit>

    // --- 以下是新增的方法 ---
    @POST("api/operations/start")
    suspend fun startOperation(@Body request: StartOperationRequest): StartOperationResponse

    @PUT("api/operations/stop/{id}")
    suspend fun stopOperation(@Path("id") id: Long): Response<Unit>
}
