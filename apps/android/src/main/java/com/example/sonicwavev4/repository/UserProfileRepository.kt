package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.ChangePasswordRequest
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.network.RetrofitClient
import com.example.sonicwavev4.network.UpdateUserProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserProfileRepository(
    private val apiService: ApiService = RetrofitClient.api,
) {

    suspend fun updateProfile(username: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.updateCurrentUser(
                    UpdateUserProfileRequest(username = username)
                )
                if (!response.isSuccessful) {
                    val message = ErrorMessageResolver.fromResponse(
                        response.errorBody(),
                        response.code()
                    )
                    throw Exception(message)
                }
            }
        }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = apiService.changeMyPassword(
                    ChangePasswordRequest(
                        oldPassword = oldPassword,
                        newPassword = newPassword
                    )
                )
                if (!response.isSuccessful) {
                    val message = ErrorMessageResolver.fromResponse(
                        response.errorBody(),
                        response.code()
                    )
                    throw Exception(message)
                }
            }
        }
    }
}
