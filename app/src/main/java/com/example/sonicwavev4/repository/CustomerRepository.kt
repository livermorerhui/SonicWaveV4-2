package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.CustomerCreationResponse
import com.example.sonicwavev4.network.RetrofitClient
import retrofit2.Response

class CustomerRepository {
    private val apiService: ApiService = RetrofitClient.api

    suspend fun addCustomer(customer: Customer): Response<CustomerCreationResponse> = apiService.addCustomer(customer)

    suspend fun getCustomers(): Response<List<Customer>> = apiService.getCustomers()

    suspend fun updateCustomer(customerId: Int, customer: Customer): Response<Unit> = apiService.updateCustomer(customerId, customer)
}
