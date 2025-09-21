package com.example.sonicwavev4.repository

import com.example.sonicwavev4.network.ApiService
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.network.RetrofitClient

class CustomerRepository {
    private val apiService: ApiService = RetrofitClient.api

    suspend fun addCustomer(customer: Customer) = apiService.addCustomer(customer)
}
