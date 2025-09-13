package com.example.sonicwavev4.network

import android.content.Context
import com.example.sonicwavev4.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient private constructor(context: Context) {

    val instance: ApiService

    init {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor(context))
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        instance = retrofit.create(ApiService::class.java)
    }

    companion object {
        @Volatile private var INSTANCE: RetrofitClient? = null

        fun getInstance(context: Context): RetrofitClient = INSTANCE ?: synchronized(this) {
            INSTANCE ?: RetrofitClient(context).also { INSTANCE = it }
        }

        val api: ApiService
            get() = INSTANCE!!.instance
    }
}