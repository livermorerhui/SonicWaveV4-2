package com.example.sonicwavev4.data.home

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.OperationEventRequest
import com.example.sonicwavev4.network.StartOperationRequest
import com.example.sonicwavev4.network.StopOperationRequest
import com.example.sonicwavev4.utils.SessionManager

class HomeSessionRepository(
    private val sessionManager: SessionManager,
    private val apiService: ApiService
) {

    fun fetchUserId(): String? = sessionManager.fetchUserId()
    fun fetchUserName(): String? = sessionManager.fetchUserName()
    fun fetchUserEmail(): String? = sessionManager.fetchUserEmail()
    fun hasActiveSession(): Boolean = sessionManager.hasActiveSession()

    suspend fun startOperation(
        selectedCustomer: Customer?,
        frequency: Int,
        intensity: Int,
        timeInMinutes: Int
    ): Long {
        val request = StartOperationRequest(
            userId = fetchUserId() ?: "guest",
            userName = fetchUserName(),
            user_email = fetchUserEmail(),
            customer_id = selectedCustomer?.id,
            customer_name = selectedCustomer?.name,
            frequency = frequency,
            intensity = intensity,
            operationTime = timeInMinutes
        )
        val response = apiService.startOperation(request)
        return response.operationId
    }

    suspend fun logOperationEvent(operationId: Long, request: OperationEventRequest) {
        apiService.logOperationEvent(operationId, request)
    }

    suspend fun stopOperation(operationId: Long, reason: String, detail: String?) {
        val request = StopOperationRequest(reason = reason, detail = detail)
        apiService.stopOperation(operationId, request)
    }
}
