package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.MyBackendApiService
import com.example.sonicwavev4.network.NetworkErrorParser
import com.example.sonicwavev4.network.RegisterSendCodeResponse
import com.example.sonicwavev4.network.RegisterSubmitRequest
import com.example.sonicwavev4.network.RegisterSubmitResponse
import com.example.sonicwavev4.network.SendCodeRequest
import java.io.IOException
import retrofit2.HttpException

sealed class RegisterResult {
    data class Success(
        val sendCodeStatus: SendCodeStatus? = null,
        val submitStatus: SubmitStatus? = null,
    ) : RegisterResult()

    data class BusinessError(val code: Int?, val message: String) : RegisterResult()
    data class NetworkError(val message: String) : RegisterResult()
}

data class SendCodeStatus(
    val selfRegistered: Boolean?,
    val selfBound: Boolean?,
    val partnerRegistered: Boolean?,
    val needSmsInput: Boolean?,
    val registrationMode: String?,
)

data class SubmitStatus(
    val userId: String?,
    val humedsBindStatus: String?,
    val humedsErrorCode: String?,
    val humedsErrorMessage: String?,
)

class RegisterRepository(
    private val api: MyBackendApiService = MyBackendApiService.create(),
) {

    suspend fun sendCode(mobile: String, accountType: String): RegisterResult {
        return try {
            val resp: RegisterSendCodeResponse = api.sendRegisterCode(SendCodeRequest(mobile, accountType))
            if (resp.code == 200) {
                val status = SendCodeStatus(
                    selfRegistered = resp.selfRegistered,
                    selfBound = resp.selfBound,
                    partnerRegistered = resp.partnerRegistered,
                    needSmsInput = resp.needSmsInput,
                    registrationMode = resp.registrationMode,
                )
                RegisterResult.Success(sendCodeStatus = status)
            } else {
                RegisterResult.BusinessError(resp.code, resp.msg ?: "发送验证码失败")
            }
        } catch (e: HttpException) {
            val apiError = NetworkErrorParser.parseApiError(e)
            if (apiError != null && !apiError.msg.isNullOrBlank()) {
                RegisterResult.BusinessError(apiError.code, apiError.msg!!)
            } else {
                RegisterResult.NetworkError("发送验证码失败，请稍后重试")
            }
        } catch (e: IOException) {
            RegisterResult.NetworkError("网络连接异常，请检查网络后重试")
        } catch (_: Exception) {
            RegisterResult.NetworkError("发送验证码失败，请稍后重试")
        }
    }

    suspend fun register(
        mobile: String,
        code: String,
        password: String,
        accountType: String,
        birthday: String?,
        orgName: String?,
    ): RegisterResult {
        return try {
            val resp: RegisterSubmitResponse = api.submitRegister(
                RegisterSubmitRequest(
                    mobile = mobile,
                    code = code,
                    password = password,
                    accountType = accountType,
                    birthday = birthday,
                    orgName = orgName,
                ),
            )
            if (resp.code == 200) {
                val status = SubmitStatus(
                    userId = resp.data?.userId,
                    humedsBindStatus = resp.humedsBindStatus,
                    humedsErrorCode = resp.humedsErrorCode,
                    humedsErrorMessage = resp.humedsErrorMessage,
                )
                RegisterResult.Success(submitStatus = status)
            } else {
                RegisterResult.BusinessError(resp.code, resp.msg ?: "注册失败")
            }
        } catch (e: HttpException) {
            val apiError = NetworkErrorParser.parseApiError(e)
            if (apiError != null && !apiError.msg.isNullOrBlank()) {
                RegisterResult.BusinessError(apiError.code, apiError.msg!!)
            } else {
                RegisterResult.NetworkError("注册失败，请稍后重试")
            }
        } catch (e: IOException) {
            RegisterResult.NetworkError("网络连接异常，请检查网络后重试")
        } catch (_: Exception) {
            RegisterResult.NetworkError("注册失败，请稍后重试")
        }
    }
}
