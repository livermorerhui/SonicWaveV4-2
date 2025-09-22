package com.example.sonicwavev4.network

// 发送到 /start 接口的数据
data class StartOperationRequest(
    val userId: String,
    val userName: String?,
    val email: String?,
    val customer_id: Int?,
    val customer_name: String?,
    val frequency: Int,
    val intensity: Int,
    val operationTime: Int
)

// 从 /start 接口收到的响应
data class StartOperationResponse(
    val message: String,
    val operationId: Long
)

// 发送到 /stop/:id 接口的数据
data class StopOperationRequest(
    val stopTime: String // ISO 8601 format, e.g., "2025-09-17T10:00:00Z"
)
