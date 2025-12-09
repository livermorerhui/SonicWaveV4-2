package com.example.sonicwavev4.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/v1/app/feature-flags")
    suspend fun fetchFeatureFlags(@Query("deviceId") deviceId: String?): Response<FeatureFlagsResponse>

    @POST("api/v1/operations/start")
    suspend fun startOperation(@Body request: StartOperationRequest): StartOperationResponse

    @POST("api/v1/operations/{id}/events")
    suspend fun logOperationEvent(
        @Path("id") id: Long,
        @Body request: OperationEventRequest
    ): Response<Unit>

    @PUT("api/v1/operations/stop/{id}")
    suspend fun stopOperation(
        @Path("id") id: Long,
        @Body request: StopOperationRequest
    ): Response<Unit>

    @POST("api/v1/preset-modes/start")
    suspend fun startPresetMode(@Body request: StartPresetModeRequest): StartPresetModeResponse

    @PUT("api/v1/preset-modes/stop/{id}")
    suspend fun stopPresetMode(
        @Path("id") id: Long,
        @Body request: StopPresetModeRequest
    ): Response<Unit>

    // --- 心跳与会话管理 ---
    @POST("api/v1/heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<Unit>

    @POST("api/v1/auth/login-event")
    suspend fun recordLoginEvent(@Body request: LoginEventRequest): Response<LoginEventResponse>

    @PUT("api/v1/auth/logout-event") // RESTful实践：使用PUT更新会话状态
    suspend fun recordLogoutEvent(@Body request: LogoutEventRequest): Response<Unit>

    @POST("api/v1/customers")
    suspend fun addCustomer(@Body customer: Customer): Response<CustomerCreationResponse>

    @GET("api/v1/customers")
    suspend fun getCustomers(): Response<List<Customer>>

    @PUT("api/v1/customers/{customerId}")
    suspend fun updateCustomer(@Path("customerId") customerId: Int, @Body customer: Customer): Response<Unit>

    @POST("api/v1/device/heartbeat")
    suspend fun sendDeviceHeartbeat(@Body request: DeviceHeartbeatRequest): Response<Unit>

    @POST("api/humeds/token")
    suspend fun getHumedsToken(@Body request: HumedsTokenRequest): Response<HumedsTokenResponse>
}
