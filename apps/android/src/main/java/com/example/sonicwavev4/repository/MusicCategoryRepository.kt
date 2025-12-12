package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.ErrorMessageResolver
import com.example.sonicwavev4.network.MusicCategoryResponse
import com.example.sonicwavev4.network.RetrofitClient
import java.io.IOException

sealed class MusicCategoryResult {
    data class Success(val categories: List<MusicCategory>) : MusicCategoryResult()
    data class BusinessError(val message: String) : MusicCategoryResult()
    data class NetworkError(val message: String) : MusicCategoryResult()
}

data class MusicCategory(
    val id: Long,
    val code: String,
    val name: String
)

class MusicCategoryRepository(
    private val api: ApiService = RetrofitClient.api,
) {

    suspend fun fetchCategories(): MusicCategoryResult {
        return try {
            val resp = api.getMusicCategories()
            if (resp.isSuccessful) {
                val body: MusicCategoryResponse? = resp.body()
                val list = body?.categories.orEmpty().map {
                    MusicCategory(
                        id = it.id,
                        code = it.code,
                        name = it.name
                    )
                }
                MusicCategoryResult.Success(list)
            } else {
                val msg = ErrorMessageResolver.fromResponse(resp.errorBody(), resp.code())
                MusicCategoryResult.BusinessError(msg)
            }
        } catch (e: IOException) {
            MusicCategoryResult.NetworkError(ErrorMessageResolver.networkFailure(e))
        } catch (e: Throwable) {
            MusicCategoryResult.NetworkError(ErrorMessageResolver.unexpectedFailure(e))
        }
    }
}
