package com.example.sonicwavev4.network

import com.google.gson.Gson
import okhttp3.ResponseBody
import java.io.IOException

object ErrorMessageResolver {
    private val gson = Gson()

    private data class ServerError(val message: String?)

    fun fromResponse(errorBody: ResponseBody?, statusCode: Int? = null): String {
        when (statusCode) {
            401 -> return "邮箱或密码错误"
            409 -> return "账号或邮箱已存在"
        }

        if (errorBody == null) return "操作失败，请稍后再试"
        return try {
            val serverError = gson.fromJson(errorBody.charStream(), ServerError::class.java)
            serverError?.message?.let { mapToChinese(it) } ?: "操作失败，请稍后再试"
        } catch (_: Exception) {
            "操作失败，请稍后再试"
        }
    }

    fun networkFailure(exception: IOException): String {
        return "网络异常，请检查连接"
    }

    fun unexpectedFailure(exception: Throwable): String {
        return exception.message ?: "操作失败，请稍后再试"
    }

    private fun mapToChinese(message: String): String {
        return when (message) {
            "Username, email, and password are required." -> "请填写完整的用户名、邮箱和密码"
            "Username or email already exists." -> "账号或邮箱已存在"
            "Email and password are required." -> "请填写邮箱和密码"
            "Invalid credentials." -> "邮箱或密码错误"
            "Internal server error." -> "服务器开小差了，请稍后再试"
            else -> message
        }
    }
}
