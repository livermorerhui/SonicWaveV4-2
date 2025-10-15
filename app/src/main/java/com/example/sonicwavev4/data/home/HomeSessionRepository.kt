package com.example.sonicwavev4.data.home

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.StartOperationRequest
import com.example.sonicwavev4.utils.SessionManager

class HomeSessionRepository(
    private val sessionManager: SessionManager,
    private val apiService: ApiService
) {

    fun fetchUserId(): String? = sessionManager.fetchUserId()
    fun fetchUserName(): String? = sessionManager.fetchUserName()
    fun fetchUserEmail(): String? = sessionManager.fetchUserEmail()

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

    suspend fun stopOperation(operationId: Long) {
        apiService.stopOperation(operationId)
    }
}
