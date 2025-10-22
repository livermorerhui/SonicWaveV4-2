package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile
    private var authToken: String? = null

    @Volatile
    private var _apiService: ApiService? = null

    val api: ApiService
        get() = _apiService ?: throw IllegalStateException("RetrofitClient must be initialized before use.")

    fun initialize(context: Context) {
        if (_apiService == null) {
            synchronized(this) {
                if (_apiService == null) {
                    val appContext = context.applicationContext

                    // 1. Create the Authenticator
                    val tokenAuthenticator = TokenAuthenticator(appContext)

                    // 2. Create the OkHttp Client with all interceptors and the authenticator
                    val okHttpClient = OkHttpClient.Builder()
                        // Interceptor to add the Access Token to every request
                        .addInterceptor { chain ->
                            val requestBuilder = chain.request().newBuilder()
                            authToken?.let {
                                requestBuilder.addHeader("Authorization", "Bearer $it")
                            }
                            chain.proceed(requestBuilder.build())
                        }
                        // Interceptor to add the client version header
                        .addInterceptor { chain ->
                            val requestBuilder = chain.request().newBuilder()
                            requestBuilder.addHeader("X-Client-Version", BuildConfig.VERSION_NAME)
                            chain.proceed(requestBuilder.build())
                        }
                        .addInterceptor(NetworkLoggingInterceptor(appContext))
                        .addInterceptor(HttpLoggingInterceptor().apply {
                            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                        })
                        .authenticator(tokenAuthenticator) // Add the authenticator
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()

                    // 3. Create the Retrofit instance
                    val retrofit = Retrofit.Builder()
                        .baseUrl("${EndpointProvider.baseUrl}/")
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()

                    _apiService = retrofit.create(ApiService::class.java)
                }
            }
        }
    }

    fun updateToken(newToken: String?) {
        this.authToken = newToken
    }
}
