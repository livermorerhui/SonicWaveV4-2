package com.example.sonicwavev4.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonicwavev4.network.Customer
import com.example.sonicwavev4.repository.CustomerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {

    private val customerRepository = CustomerRepository()

    private val _addCustomerResult = MutableStateFlow<Result<Unit>?>(null)
    val addCustomerResult: StateFlow<Result<Unit>?> = _addCustomerResult

    fun addCustomer(customer: Customer) {
        viewModelScope.launch {
            try {
                val response = customerRepository.addCustomer(customer)
                if (response.isSuccessful) {
                    _addCustomerResult.value = Result.success(Unit)
                } else {
                    _addCustomerResult.value = Result.failure(Exception(response.errorBody()?.string()))
                }
            } catch (e: Exception) {
                _addCustomerResult.value = Result.failure(e)
            }
        }
    }

    fun resetAddCustomerResult() {
        _addCustomerResult.value = null
    }
}
