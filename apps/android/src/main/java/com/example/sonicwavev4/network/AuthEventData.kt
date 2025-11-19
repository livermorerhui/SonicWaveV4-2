package com.example.sonicwavev4.network

/**
 * 用于记录登录事件的请求体。内容为空，
 * 因为服务器会从请求头中获取IP和User-Agent。
 */
data class LoginEventRequest(val placeholder: Boolean = true)

/**
 * 成功记录登录事件后的响应，提供会话ID。
 */
data class LoginEventResponse(
    val message: String,
    val sessionId: Long
)

/**
 * 记录登出事件的请求体。
 */
data class LogoutEventRequest(
    val sessionId: Long
)

/**
 * 发送心跳的请求体。
 */
data class HeartbeatRequest(
    val sessionId: Long,
    val deviceId: String? = null
)

data class DeviceHeartbeatRequest(
    val deviceId: String,
    val ipAddress: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null
)
