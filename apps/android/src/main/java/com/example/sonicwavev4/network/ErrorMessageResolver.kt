package com.example.sonicwavev4.network

import com.google.gson.Gson
import okhttp3.ResponseBody
import java.io.IOException

object ErrorMessageResolver {
    private val gson = Gson()

    private data class ServerError(val message: String?)

    fun fromResponse(errorBody: ResponseBody?, statusCode: Int? = null): String {
        when (statusCode) {
            401 -> return "账号或密码错误"
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
        val normalized = message.trim()
        if (
            normalized.contains("账号或密码错误") ||
            normalized.contains("邮箱或密码错误") ||
            normalized.equals("Invalid credentials.", ignoreCase = true)
        ) {
            return "账号或密码错误"
        }
        if (
            normalized.contains("账号和密码为必填项") ||
            normalized.contains("Email and password are required", ignoreCase = true)
        ) {
            return "请输入账号和密码"
        }
        return when (normalized) {
            "Username, email, and password are required." -> "请填写完整的用户名、邮箱和密码"
            "Username or email already exists." -> "账号或邮箱已存在"
            "Internal server error." -> "服务器开小差了，请稍后再试"
            else -> message
        }
    }
}
