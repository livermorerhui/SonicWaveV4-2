package com.example.sonicwavev4.network

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.GzipSink
import okio.buffer

class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalBody = originalRequest.body ?: return chain.proceed(originalRequest)

        if (originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest)
        }

        val compressedBody = gzip(originalBody)
        val requestBuilder = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, compressedBody)

        return chain.proceed(requestBuilder.build())
    }

    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? = body.contentType()

            override fun contentLength(): Long = -1

            override fun writeTo(sink: okio.BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }
}
