package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.network.PasswordResetSendCodeRequest
import com.example.sonicwavev4.network.PasswordResetSubmitRequest
import com.example.sonicwavev4.network.RetrofitClient
import java.io.IOException

sealed class PasswordResetResult {
    object Success : PasswordResetResult()
    data class BusinessError(val message: String) : PasswordResetResult()
    data class NetworkError(val message: String) : PasswordResetResult()
}

class PasswordResetRepository(
    private val api: ApiService = RetrofitClient.api,
) {

    suspend fun sendCode(mobile: String): PasswordResetResult {
        return try {
            val resp = api.sendPasswordResetCode(
                PasswordResetSendCodeRequest(mobile = mobile)
            )
            if (resp.isSuccessful) {
                PasswordResetResult.Success
            } else {
                val msg = ErrorMessageResolver.fromResponse(resp.errorBody(), resp.code())
                PasswordResetResult.BusinessError(msg)
            }
        } catch (e: IOException) {
            PasswordResetResult.NetworkError(ErrorMessageResolver.networkFailure(e))
        } catch (e: Throwable) {
            PasswordResetResult.NetworkError(ErrorMessageResolver.unexpectedFailure(e))
        }
    }

    suspend fun resetPassword(
        mobile: String,
        code: String,
        newPassword: String,
    ): PasswordResetResult {
        return try {
            val resp = api.resetPassword(
                PasswordResetSubmitRequest(
                    mobile = mobile,
                    code = code,
                    newPassword = newPassword,
                )
            )
            if (resp.isSuccessful) {
                PasswordResetResult.Success
            } else {
                val msg = ErrorMessageResolver.fromResponse(resp.errorBody(), resp.code())
                PasswordResetResult.BusinessError(msg)
            }
        } catch (e: IOException) {
            PasswordResetResult.NetworkError(ErrorMessageResolver.networkFailure(e))
        } catch (e: Throwable) {
            PasswordResetResult.NetworkError(ErrorMessageResolver.unexpectedFailure(e))
        }
    }
}
