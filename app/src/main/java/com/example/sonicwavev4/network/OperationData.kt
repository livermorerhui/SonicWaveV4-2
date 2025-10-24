package com.example.sonicwavev4.network

// 发送到 /start 接口的数据
data class StartOperationRequest(
    val userId: String,
    val userName: String?,
    val user_email: String?,
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

data class OperationEventRequest(
    val eventType: String,
    val frequency: Int? = null,
    val intensity: Int? = null,
    val timeRemaining: Int? = null,
    val extraDetail: String? = null
)

data class StopOperationRequest(
    val reason: String,
    val detail: String? = null
)
