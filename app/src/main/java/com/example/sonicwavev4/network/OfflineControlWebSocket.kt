package com.example.sonicwavev4.network

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.sonicwavev4.utils.OfflineControlMessageHandler
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Lightweight WebSocket client that listens for backend control messages
 * (enable/disable/force-exit offline mode) without requiring user authentication.
 */
object OfflineControlWebSocket : WebSocketListener() {

    private const val TAG = "OfflineControlWS"
    private const val RECONNECT_DELAY_MS = 10_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    fun initialize(context: Context) {
        if (started) return
        started = true
        OfflineControlMessageHandler.initialize(context.applicationContext)
        connect()
    }

    private fun connect() {
        val wsUrl = buildWebSocketUrl() ?: run {
            Log.w(TAG, "Failed to build control channel URL.")
            scheduleReconnect()
            return
        }
        val request = Request.Builder().url(wsUrl).build()
        client.newWebSocket(request, this)
    }

    private fun buildWebSocketUrl(): String? {
        return try {
            val httpBase = EndpointProvider.baseUrl
            val uri = URI(httpBase)
            val scheme = if (uri.scheme.equals("https", true)) "wss" else "ws"
            val host = uri.host ?: Uri.parse(httpBase).host ?: return null
            val portPart = if (uri.port == -1) "" else ":${uri.port}"
            "$scheme://$host$portPart/ws?channel=control"
        } catch (e: Exception) {
            Log.e(TAG, "Invalid base URL for control WS", e)
            null
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "Offline control channel connected.")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        OfflineControlMessageHandler.handleRawMessage(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        onMessage(webSocket, bytes.utf8())
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "Control channel closed: $code $reason")
        scheduleReconnect()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.w(TAG, "Control channel failure: ${t.message}")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ connect() }, RECONNECT_DELAY_MS)
    }
}
