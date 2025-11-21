package com.example.sonicwavev4.network

import android.content.Context
import android.util.Log
import com.example.sonicwavev4.BuildConfig
import com.example.sonicwavev4.utils.GlobalLogoutManager
import com.example.sonicwavev4.utils.LogoutReason
import com.example.sonicwavev4.utils.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TokenAuthenticator(private val context: Context) : Authenticator {

    private val sessionManager = SessionManager(context)

    override fun authenticate(route: Route?, response: Response): Request? {
        val currentAccessToken = sessionManager.fetchAccessToken()

        // If the request that failed did not have our current access token,
        // it means the token was already refreshed by another concurrent request.
        if (response.request.header("Authorization") != "Bearer $currentAccessToken") {
            return response.request.newBuilder()
                .header("Authorization", "Bearer $currentAccessToken")
                .build()
        }

        // If we don't have a refresh token, we can't do anything. Trigger logout.
        val currentRefreshToken = sessionManager.fetchRefreshToken() ?: run {
            triggerHardLogout()
            return null
        }

        // Synchronize to prevent multiple threads from trying to refresh the token at the same time.
        synchronized(this) {
            // Double-check if the token was refreshed while we were waiting for the lock.
            val newAccessToken = sessionManager.fetchAccessToken()
            if (currentAccessToken != newAccessToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccessToken")
                    .build()
            }

            // We are the first thread, perform the token refresh.
            Log.d("TokenAuthenticator", "Access token expired. Attempting to refresh...")
            val refreshResult: retrofit2.Response<RefreshTokenResponse>? = runBlocking {
                try {
                    getRefreshApiService().refreshToken(RefreshTokenRequest(currentRefreshToken))
                } catch (e: Exception) {
                    Log.e("TokenAuthenticator", "Exception during token refresh call", e)
                    null
                }
            }

            if (refreshResult != null && refreshResult.isSuccessful) {
                val newTokens = refreshResult.body()!!
                sessionManager.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                RetrofitClient.updateToken(newTokens.accessToken)

                Log.d("TokenAuthenticator", "Token refresh successful. Retrying original request.")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${newTokens.accessToken}")
                    .build()
            } else {
                Log.e("TokenAuthenticator", "Token refresh failed. Triggering hard logout.")
                triggerHardLogout()
                return null
            }
        }
    }

    private fun triggerHardLogout() {
        sessionManager.initiateLogout(LogoutReason.HardLogout)
    }

    private fun getRefreshApiService(): ApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("${EndpointProvider.baseUrl}/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
