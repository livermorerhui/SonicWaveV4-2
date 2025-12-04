package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.MyBackendApiService
import com.example.sonicwavev4.network.RegisterRequest
import com.example.sonicwavev4.network.SendCodeRequest

class RegisterRepository(
    private val api: MyBackendApiService = MyBackendApiService.create()
) {

    suspend fun sendCode(mobile: String, accountType: String): Result<Unit> {
        return try {
            val resp = api.sendRegisterCode(SendCodeRequest(mobile, accountType))
            if (resp.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Throwable(resp.msg ?: "发送验证码失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        mobile: String,
        code: String,
        password: String,
        accountType: String,
        birthday: String?,
        orgName: String?
    ): Result<Unit> {
        return try {
            val resp = api.submitRegister(
                RegisterRequest(
                    mobile = mobile,
                    code = code,
                    password = password,
                    accountType = accountType,
                    birthday = birthday,
                    orgName = orgName
                )
            )
            if (resp.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Throwable(resp.msg ?: "注册失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
