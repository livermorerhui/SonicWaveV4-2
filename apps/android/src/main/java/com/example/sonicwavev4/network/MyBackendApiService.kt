package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.BuildConfig
import com.example.sonicwavev4.network.EndpointProvider
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface MyBackendApiService {

    @POST("/api/register/send_code")
    suspend fun sendRegisterCode(
        @Body body: SendCodeRequest
    ): RegisterSendCodeResponse

    @POST("/api/register/submit")
    suspend fun submitRegister(
        @Body body: RegisterSubmitRequest
    ): RegisterSubmitResponse

    @POST("/api/humeds/test/login")
    suspend fun humedsTestLogin(
        @Body body: HumedsTestLoginRequest,
    ): ApiResponse<HumedsTestLoginResult>

    @POST("/api/humeds/token")
    suspend fun getHumedsToken(@Body body: HumedsTokenRequest): ApiResponse<HumedsTokenResult>

    companion object {
        fun create(context: Context? = null): MyBackendApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val baseUrl = EndpointProvider.baseUrl
            val retrofit = Retrofit.Builder()
                .baseUrl("${baseUrl.trimEnd('/')}/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(MyBackendApiService::class.java)
        }
    }
}

/**
 * Basic API response wrapper aligned with backend contract.
 */
data class ApiResponse<T>(
    val code: Int,
    val msg: String?,
    val data: T?
)

data class SendCodeRequest(
    val mobile: String,
    val accountType: String
)

data class RegisterSubmitRequest(
    val mobile: String,
    val code: String,
    val password: String,
    val accountType: String,
    val birthday: String?,
    val orgName: String?
)

data class RegisterSubmitData(
    val userId: String?
)

data class RegisterSendCodeResponse(
    val code: Int,
    val msg: String?,
    val data: Any?,
    val selfRegistered: Boolean?,
    val selfBound: Boolean?,
    val partnerRegistered: Boolean?,
    val needSmsInput: Boolean?,
    val registrationMode: String?,
    val mobile: String?,
    val accountType: String?
)

data class RegisterSubmitResponse(
    val code: Int,
    val msg: String?,
    val data: RegisterSubmitData?,
    val humedsBindStatus: String?,
    val humedsErrorCode: String?,
    val humedsErrorMessage: String?
)

/*
服务端后续改造建议（不在 Android 仓库实现）：

1）验证码服务抽象接口示例：
    interface SmsCodeService {
        fun sendCode(mobile: String, scene: String): Result<Unit>
        fun verifyCode(mobile: String, scene: String, code: String): Result<Unit>
    }

2）提供两种实现：
    - LocalSmsCodeService（本地/测试环境）：生成 6 位验证码，缓存到 Redis/内存，sendCode 打印日志，verifyCode 做本地比对。
    - HumedsSmsCodeService（生产环境）：sendCode 调 Humeds /apply_captcha + /smscode；verifyCode 在 /api/register/submit 中把 code 透传给 Humeds /signup 或 /login，由 Humeds 判定。

3）/api/register/send_code 与 /api/register/submit：
    - send_code 按环境选择 SmsCodeService 实现调用 sendCode(mobile, "register")。
    - submit：
        * 测试环境：verifyCode 成功后创建本地用户。
        * 生产环境：调用 Humeds /userexist 决定 signup/login，成功后创建/更新本地用户并标记手机号已验证。

服务端用户表建议新增字段：
- mobile: String
- mobile_verified: Boolean
- account_type: String // "personal" or "org"
- birthday: Date?
- org_name: String?
- humeds_region: String?
- humeds_status: String? // "unbound" / "registered" / "bound_existing"
- humeds_user_id: String?
- humeds_token_jwt: String?
*/
