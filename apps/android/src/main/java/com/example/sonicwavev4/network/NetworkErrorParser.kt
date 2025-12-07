package com.example.sonicwavev4.network

import com.google.gson.Gson
import retrofit2.HttpException

object NetworkErrorParser {
    private val gson = Gson()

    fun parseApiError(e: HttpException): ApiError? {
        val errorBody = e.response()?.errorBody()?.string()
        if (errorBody.isNullOrBlank()) return null
        return try {
            gson.fromJson(errorBody, ApiError::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
