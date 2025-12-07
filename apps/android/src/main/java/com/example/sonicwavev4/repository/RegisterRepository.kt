package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.MyBackendApiService
import com.example.sonicwavev4.network.NetworkErrorParser
import com.example.sonicwavev4.network.RegisterSubmitRequest
import com.example.sonicwavev4.network.SendCodeRequest
import java.io.IOException
import retrofit2.HttpException

sealed class RegisterResult {
    object Success : RegisterResult()
    data class BusinessError(val code: Int?, val message: String) : RegisterResult()
    data class NetworkError(val message: String) : RegisterResult()
}

class RegisterRepository(
    private val api: MyBackendApiService = MyBackendApiService.create(),
) {

    suspend fun sendCode(mobile: String, accountType: String): RegisterResult {
        return try {
            val resp = api.sendRegisterCode(SendCodeRequest(mobile, accountType))
            if (resp.code == 200) {
                RegisterResult.Success
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
            val resp = api.submitRegister(
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
                RegisterResult.Success
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
