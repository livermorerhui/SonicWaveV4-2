package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.HumedsTestLoginRequest
import com.example.sonicwavev4.network.HumedsTestLoginResult
import com.example.sonicwavev4.network.MyBackendApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HumedsRepository(
    private val apiService: MyBackendApiService = MyBackendApiService.create(),
) {
    suspend fun testLogin(
        mobile: String,
        password: String,
        regionCode: String,
    ): Result<HumedsTestLoginResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = HumedsTestLoginRequest(
                    mobile = mobile,
                    password = password,
                    regionCode = regionCode,
                )
                val response = apiService.humedsTestLogin(request)
                if (response.code == 200 && response.data != null) {
                    response.data
                } else {
                    val errorMessage = response.msg?.takeIf { it.isNotBlank() }
                        ?: "Humeds 登录失败（code=${'$'}{response.code}）"
                    throw Exception(errorMessage)
                }
            }
        }
    }
}
