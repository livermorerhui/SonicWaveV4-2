package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.BuildConfig
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

                    val okHttpClient = OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val requestBuilder = chain.request().newBuilder()
                            authToken?.let {
                                requestBuilder.addHeader("Authorization", "Bearer $it")
                            }
                            chain.proceed(requestBuilder.build())
                        }
                        .addInterceptor(NetworkLoggingInterceptor(appContext))
                        .addInterceptor(HttpLoggingInterceptor().apply {
                            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
                        })
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build()

                    val retrofit = Retrofit.Builder()
                        .baseUrl(BuildConfig.SERVER_BASE_URL)
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